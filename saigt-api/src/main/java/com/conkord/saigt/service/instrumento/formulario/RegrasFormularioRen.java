package com.conkord.saigt.service.instrumento.formulario;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regras do formulário `REN` (BL-PROC-042). O que muda por Dinâmica lê-se da configuração
 * (`pede_diploma_corrigido`, `tipo_informacao_fixo`), nunca de códigos de Instrumento ou de Dinâmica escritos aqui.
 * Os campos guardam-se em `dados_formulario`, ao lado das chaves da Informação Geral.
 */
@Component
public class RegrasFormularioRen {

    public static final String CAMPO_MOTIVO = "motivoProcedimentoId";
    public static final String CAMPO_DIPLOMA = "diplomaCorrigido";
    public static final String CAMPO_REGIAO = "regiaoCodigo";
    public static final String CAMPO_MUNICIPIO = "municipioCodigo";
    public static final List<String> CHAVES = List.of(CAMPO_MOTIVO, CAMPO_DIPLOMA, CAMPO_REGIAO, CAMPO_MUNICIPIO);
    private static final String CAMPO_TIPO_INFORMACAO = "tipoInformacao";

    public static final String MSG_MOTIVO_EM_FALTA = "Indique o motivo do procedimento.";
    static final String MSG_MOTIVO_INVALIDO = "Motivo do procedimento inválido.";
    static final String MSG_NAO_APLICAVEL = "Campo não aplicável a este procedimento.";
    static final String MSG_REGIAO_INVALIDA = "Região inválida.";
    static final String MSG_MUNICIPIO_INVALIDO = "Município inválido para a região escolhida.";
    static final String MSG_TIPO_INFORMACAO = "O tipo de informação desta dinâmica é fixo.";
    private static final String MSG_VARIOS = "O formulário tem campos em falta ou inválidos.";
    private static final String TIPO_MOTIVO = TipoListaValores.MOTIVO_PROCEDIMENTO_REN.name();

    /** Os campos do pedido; um texto em branco apaga o gravado, um campo nulo deixa-o como está. */
    public record CamposRen(Long motivoProcedimentoId, String diplomaCorrigido, String regiaoCodigo, String municipioCodigo) {

        boolean algumPreenchido() {
            return motivoProcedimentoId != null || preenchido(diplomaCorrigido) || preenchido(regiaoCodigo)
                    || preenchido(municipioCodigo);
        }
    }

    /** O que está gravado em `dados_formulario`. */
    public record Gravados(Long motivoProcedimentoId, String diplomaCorrigido, String regiaoCodigo, String municipioCodigo) {
    }

    private final ListaValoresRepository listaValoresRepository;
    private final SnitReferenciaRepository snitReferenciaRepository;
    private final ObjectMapper objectMapper;

    public RegrasFormularioRen(ListaValoresRepository listaValoresRepository,
                               SnitReferenciaRepository snitReferenciaRepository, ObjectMapper objectMapper) {
        this.listaValoresRepository = listaValoresRepository;
        this.snitReferenciaRepository = snitReferenciaRepository;
        this.objectMapper = objectMapper;
    }

    /** Fora do formulário `REN` os campos não existem: um campo preenchido recusa o pedido. */
    public static void verificarNaoAplicavel(CamposRen campos) {
        if (campos == null || !campos.algumPreenchido()) {
            return;
        }
        List<CampoErro> erros = new ArrayList<>();
        if (campos.motivoProcedimentoId() != null) {
            erros.add(new CampoErro(CAMPO_MOTIVO, MSG_NAO_APLICAVEL));
        }
        if (preenchido(campos.diplomaCorrigido())) {
            erros.add(new CampoErro(CAMPO_DIPLOMA, MSG_NAO_APLICAVEL));
        }
        if (preenchido(campos.regiaoCodigo())) {
            erros.add(new CampoErro(CAMPO_REGIAO, MSG_NAO_APLICAVEL));
        }
        if (preenchido(campos.municipioCodigo())) {
            erros.add(new CampoErro(CAMPO_MUNICIPIO, MSG_NAO_APLICAVEL));
        }
        throw new ValidacaoException(MSG_NAO_APLICAVEL, erros);
    }

    /** Com `tipo_informacao_fixo` na Dinâmica, outro valor é recusado e sem valor fica o fixo; sem ele, fica o pedido. */
    public String tipoInformacao(Processo processo, String pedido) {
        String fixo = configDinamica(processo).getTipoInformacaoFixo();
        if (fixo == null) {
            return pedido;
        }
        if (pedido != null && !fixo.equals(pedido)) {
            throw new ValidacaoException(MSG_TIPO_INFORMACAO, List.of(new CampoErro(CAMPO_TIPO_INFORMACAO, MSG_TIPO_INFORMACAO)));
        }
        return fixo;
    }

    public boolean temTipoInformacaoFixo(Processo processo) {
        return configDinamica(processo).getTipoInformacaoFixo() != null;
    }

    /**
     * Valida o pedido contra o gravado e devolve as chaves a guardar em `dados_formulario`. Corre antes de qualquer
     * escrita: um pedido recusado não grava nada.
     */
    public Map<String, Object> chavesAGravar(Processo processo, CamposRen pedido) {
        Gravados gravados = gravados(processo.getDadosFormulario());
        CamposRen campos = pedido == null ? new CamposRen(null, null, null, null) : pedido;
        List<CampoErro> erros = new ArrayList<>();

        Long motivo = gravados.motivoProcedimentoId();
        if (campos.motivoProcedimentoId() != null) {
            if (motivoAtivo(campos.motivoProcedimentoId()).isEmpty()) {
                erros.add(new CampoErro(CAMPO_MOTIVO, MSG_MOTIVO_INVALIDO));
            }
            motivo = campos.motivoProcedimentoId();
        }

        String diploma = escolher(campos.diplomaCorrigido(), gravados.diplomaCorrigido());
        if (preenchido(campos.diplomaCorrigido()) && !configDinamica(processo).isPedeDiplomaCorrigido()) {
            erros.add(new CampoErro(CAMPO_DIPLOMA, MSG_NAO_APLICAVEL));
        }

        String regiao = escolher(campos.regiaoCodigo(), gravados.regiaoCodigo());
        String municipio = escolher(campos.municipioCodigo(), gravados.municipioCodigo());
        erros.addAll(errosTerritorio(regiao, municipio));

        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros.size() == 1 ? erros.get(0).erro() : MSG_VARIOS, erros);
        }
        Map<String, Object> chaves = new LinkedHashMap<>();
        chaves.put(CAMPO_MOTIVO, motivo);
        chaves.put(CAMPO_DIPLOMA, diploma);
        chaves.put(CAMPO_REGIAO, regiao);
        chaves.put(CAMPO_MUNICIPIO, municipio);
        return chaves;
    }

    /** Sobre o gravado: o motivo só se exige com a lista preenchida (OI-246) e tem de continuar ativo. */
    public void verificarNaSubmissao(Processo processo) {
        List<CampoErro> pendencias = pendencias(processo);
        if (!pendencias.isEmpty()) {
            throw new ValidacaoException(pendencias.size() == 1 ? pendencias.get(0).erro() : MSG_VARIOS, pendencias);
        }
    }

    public List<CampoErro> pendencias(Processo processo) {
        Gravados gravados = gravados(processo.getDadosFormulario());
        List<CampoErro> erros = new ArrayList<>();
        boolean motivoValido = gravados.motivoProcedimentoId() != null
                && motivoAtivo(gravados.motivoProcedimentoId()).isPresent();
        if (!motivoValido && listaValoresRepository.existsByTipoAndAtivoTrue(TIPO_MOTIVO)) {
            erros.add(new CampoErro(CAMPO_MOTIVO, MSG_MOTIVO_EM_FALTA));
        }
        erros.addAll(errosTerritorio(gravados.regiaoCodigo(), gravados.municipioCodigo()));
        return erros;
    }

    public List<ListaValores> motivosDisponiveis() {
        return listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc(TIPO_MOTIVO);
    }

    /** O motivo gravado, mesmo que entretanto desativado, para o ecrã o mostrar. */
    public Optional<ListaValores> motivoGravado(Processo processo) {
        Long id = gravados(processo.getDadosFormulario()).motivoProcedimentoId();
        return id == null ? Optional.empty()
                : listaValoresRepository.findById(id).filter(valor -> TIPO_MOTIVO.equals(valor.getTipo()));
    }

    public Gravados gravados(String dadosFormulario) {
        if (dadosFormulario == null || dadosFormulario.isBlank()) {
            return new Gravados(null, null, null, null);
        }
        try {
            JsonNode raiz = objectMapper.readTree(dadosFormulario);
            JsonNode motivo = raiz.get(CAMPO_MOTIVO);
            return new Gravados(motivo == null || !motivo.canConvertToLong() ? null : motivo.asLong(),
                    texto(raiz, CAMPO_DIPLOMA), texto(raiz, CAMPO_REGIAO), texto(raiz, CAMPO_MUNICIPIO));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("dados_formulario ilegível.", e);
        }
    }

    public boolean pedeDiplomaCorrigido(Processo processo) {
        return configDinamica(processo).isPedeDiplomaCorrigido();
    }

    public String tipoInformacaoFixo(Processo processo) {
        return configDinamica(processo).getTipoInformacaoFixo();
    }

    private Optional<ListaValores> motivoAtivo(Long id) {
        return listaValoresRepository.findById(id)
                .filter(valor -> TIPO_MOTIVO.equals(valor.getTipo()) && valor.isAtivo());
    }

    /** O município pertence à região pelo `nut2Code` da réplica do SNIT (DB-INT-007). */
    private List<CampoErro> errosTerritorio(String regiao, String municipio) {
        List<CampoErro> erros = new ArrayList<>();
        boolean regiaoValida = regiao != null
                && snitReferenciaRepository.existsByListaAndCodigoAndAtivoTrue(ListaReferenciaSnit.NUT2, regiao);
        if (regiao != null && !regiaoValida) {
            erros.add(new CampoErro(CAMPO_REGIAO, MSG_REGIAO_INVALIDA));
        }
        if (municipio != null && (!regiaoValida || !municipioDaRegiao(municipio, regiao))) {
            erros.add(new CampoErro(CAMPO_MUNICIPIO, MSG_MUNICIPIO_INVALIDO));
        }
        return erros;
    }

    private boolean municipioDaRegiao(String municipio, String regiao) {
        return snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, municipio)
                .filter(SnitReferencia::isAtivo)
                .map(concelho -> regiao.equals(nut2Do(concelho)))
                .orElse(false);
    }

    private String nut2Do(SnitReferencia concelho) {
        try {
            return texto(objectMapper.readTree(concelho.getDadosOrigem()), ListaReferenciaSnit.CHAVE_NUT2_DO_MUNICIPIO);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static InstrumentoDinamicaConfig configDinamica(Processo processo) {
        return processo.getDinamica().getInstrumentoDinamicaConfig();
    }

    /** Nulo mantém o gravado; em branco apaga-o. */
    private static String escolher(String pedido, String gravado) {
        if (pedido == null) {
            return gravado;
        }
        return pedido.isBlank() ? null : pedido.strip();
    }

    private static String texto(JsonNode raiz, String chave) {
        JsonNode no = raiz.get(chave);
        return no == null || no.isNull() ? null : no.asText();
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
