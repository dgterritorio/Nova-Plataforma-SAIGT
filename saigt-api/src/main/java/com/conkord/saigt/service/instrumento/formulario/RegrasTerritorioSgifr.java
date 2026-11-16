package com.conkord.saigt.service.instrumento.formulario;

import com.conkord.saigt.dto.instrumento.OpcaoCimAmResponse;
import com.conkord.saigt.dto.instrumento.OpcaoListaResponse;
import com.conkord.saigt.dto.instrumento.TerritorioSgifrResponse;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
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
 * Região e CIM / AM do formulário `SGIFR` (EF v1.5 l.1527-1528). Quando cada campo se pede é a tradução programa ×
 * Dinâmica, própria do formulário e fora do motor e da autorização: a Região só na Delimitação; a CIM / AM só no PSA,
 * na Delimitação em função da Região escolhida e na Alteração escolhida diretamente. Uma lista sem valores ativos não
 * pede o campo (OI-246, TR-256). Os valores gravam-se em `dados_formulario` pelo `codigo`, como a Fundamentação RJIGT.
 */
@Component
public class RegrasTerritorioSgifr {

    public static final String CAMPO_REGIAO = "regiao";
    public static final String CAMPO_CIM_AM = "cimAm";
    private static final String MSG_CAMPO_NAO_APLICAVEL = "Campo não aplicável a este procedimento.";
    private static final String MSG_REGIAO_INVALIDA = "Região inválida.";
    private static final String MSG_CIM_AM_INVALIDA = "CIM / AM inválida.";
    private static final String MSG_CIM_AM_FORA_DA_REGIAO = "A CIM / AM não pertence à Região escolhida.";
    private static final String PROGRAMA_PRA = "PRA";
    private static final String PROGRAMA_PSA = "PSA";
    private static final String DINAMICA_DELIMITACAO = "DELIMITACAO";
    private static final String DINAMICA_ALTERACAO = "ALTERACAO";

    private final ListaValoresRepository listaValoresRepository;
    private final ObjectMapper objectMapper;

    public RegrasTerritorioSgifr(ListaValoresRepository listaValoresRepository, ObjectMapper objectMapper) {
        this.listaValoresRepository = listaValoresRepository;
        this.objectMapper = objectMapper;
    }

    /** PRA ou PSA, só na Delimitação. */
    public boolean aplicaRegiao(FormularioFixo formulario, Processo processo) {
        String programa = programa(processo);
        return formulario == FormularioFixo.SGIFR && DINAMICA_DELIMITACAO.equals(dinamica(processo))
                && (PROGRAMA_PRA.equals(programa) || PROGRAMA_PSA.equals(programa));
    }

    /** Só no PSA, na Delimitação ou na Alteração; nunca no PRA. */
    public boolean aplicaCimAm(FormularioFixo formulario, Processo processo) {
        String dinamica = dinamica(processo);
        return formulario == FormularioFixo.SGIFR && PROGRAMA_PSA.equals(programa(processo))
                && (DINAMICA_DELIMITACAO.equals(dinamica) || DINAMICA_ALTERACAO.equals(dinamica));
    }

    /** Na Delimitação a CIM / AM depende da Região escolhida; na Alteração a lista é a completa. */
    private boolean cimAmPelaRegiao(Processo processo) {
        return DINAMICA_DELIMITACAO.equals(dinamica(processo));
    }

    /** As Regiões ativas por `ordem`; vazia quando o campo não se aplica. */
    public List<ListaValores> opcoesRegiao(FormularioFixo formulario, Processo processo) {
        if (!aplicaRegiao(formulario, processo)) {
            return List.of();
        }
        return listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc(TipoListaValores.REGIAO.name());
    }

    /**
     * As CIM / AM ativas por `ordem`; na Delimitação só as que têm uma Região ativa, porque só essas se podem escolher
     * a partir de uma Região do formulário. Vazia quando o campo não se aplica.
     */
    public List<ListaValores> opcoesCimAm(FormularioFixo formulario, Processo processo) {
        if (!aplicaCimAm(formulario, processo)) {
            return List.of();
        }
        List<ListaValores> ativas = listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc(TipoListaValores.CIM_AM.name());
        if (!cimAmPelaRegiao(processo)) {
            return ativas;
        }
        return ativas.stream().filter(item -> item.getPai() != null && item.getPai().isAtivo()).toList();
    }

    /**
     * Corre antes de qualquer escrita no processo. Texto em branco vale omitido. Um campo enviado onde não se aplica
     * (Região na Alteração, CIM / AM no PRA, qualquer dos dois fora do `SGIFR`) recusa o pedido com a mensagem da
     * #249; um valor que não é opção ativa, ou uma CIM / AM de outra Região na Delimitação, recusa-o com o campo.
     *
     * @return as chaves a gravar em `dados_formulario` (vazia fora do `SGIFR`)
     */
    public Map<String, Object> verificar(FormularioFixo formulario, Processo processo, String regiaoPedida, String cimAmPedida) {
        String regiao = emBrancoComoNulo(regiaoPedida);
        String cimAm = emBrancoComoNulo(cimAmPedida);
        List<CampoErro> erros = new ArrayList<>();
        boolean aplicaRegiao = aplicaRegiao(formulario, processo);
        boolean aplicaCimAm = aplicaCimAm(formulario, processo);
        if (regiao != null && !aplicaRegiao) {
            erros.add(new CampoErro(CAMPO_REGIAO, MSG_CAMPO_NAO_APLICAVEL));
        }
        if (cimAm != null && !aplicaCimAm) {
            erros.add(new CampoErro(CAMPO_CIM_AM, MSG_CAMPO_NAO_APLICAVEL));
        }
        if (erros.isEmpty()) {
            erros.addAll(errosDeValor(formulario, processo, regiao, cimAm));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros.get(0).erro(), erros);
        }
        Map<String, Object> chaves = new LinkedHashMap<>();
        if (formulario == FormularioFixo.SGIFR) {
            chaves.put(CAMPO_REGIAO, regiao);
            chaves.put(CAMPO_CIM_AM, cimAm);
        }
        return chaves;
    }

    private List<CampoErro> errosDeValor(FormularioFixo formulario, Processo processo, String regiao, String cimAm) {
        List<CampoErro> erros = new ArrayList<>();
        boolean regiaoValida = regiao == null
                || opcoesRegiao(formulario, processo).stream().anyMatch(item -> item.getCodigo().equals(regiao));
        if (!regiaoValida) {
            erros.add(new CampoErro(CAMPO_REGIAO, MSG_REGIAO_INVALIDA));
        }
        if (cimAm == null) {
            return erros;
        }
        Optional<ListaValores> escolhida = opcoesCimAm(formulario, processo).stream()
                .filter(item -> item.getCodigo().equals(cimAm)).findFirst();
        if (escolhida.isEmpty()) {
            erros.add(new CampoErro(CAMPO_CIM_AM, MSG_CIM_AM_INVALIDA));
        } else if (regiaoValida && cimAmPelaRegiao(processo)
                && !escolhida.get().getPai().getCodigo().equals(regiao)) {
            erros.add(new CampoErro(CAMPO_CIM_AM, MSG_CIM_AM_FORA_DA_REGIAO));
        }
        return erros;
    }

    /** Para o detalhe: fora do `SGIFR` nada se pede e as listas e os valores ficam nulos. */
    public TerritorioSgifrResponse territorio(FormularioFixo formulario, Processo processo) {
        if (formulario != FormularioFixo.SGIFR) {
            return TerritorioSgifrResponse.NAO_APLICAVEL;
        }
        List<ListaValores> regioes = opcoesRegiao(formulario, processo);
        List<ListaValores> cimAm = opcoesCimAm(formulario, processo);
        String regiaoGravada = lerCodigo(processo.getDadosFormulario(), CAMPO_REGIAO);
        String cimAmGravada = lerCodigo(processo.getDadosFormulario(), CAMPO_CIM_AM);
        return new TerritorioSgifrResponse(
                !regioes.isEmpty(),
                regioes.stream().map(OpcaoListaResponse::de).toList(),
                regiaoGravada,
                descricao(TipoListaValores.REGIAO, regiaoGravada),
                !cimAm.isEmpty(),
                cimAm.stream().map(OpcaoCimAmResponse::de).toList(),
                cimAmGravada,
                descricao(TipoListaValores.CIM_AM, cimAmGravada));
    }

    /** A descrição atual, mesmo de um valor que o Master desativou depois da gravação; nula sem valor. */
    private String descricao(TipoListaValores tipo, String codigo) {
        if (codigo == null) {
            return null;
        }
        return listaValoresRepository.findByTipoInAndCodigoIn(List.of(tipo.name()), List.of(codigo)).stream()
                .findFirst().map(ListaValores::getDescricao).orElse(null);
    }

    /** JSON ilegível ou valor que não é texto vale nulo. */
    private String lerCodigo(String dadosFormulario, String chave) {
        if (dadosFormulario == null || dadosFormulario.isBlank()) {
            return null;
        }
        try {
            JsonNode valor = objectMapper.readTree(dadosFormulario).path(chave);
            return valor.isTextual() ? valor.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String emBrancoComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String programa(Processo processo) {
        return processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().getCodigo();
    }

    private static String dinamica(Processo processo) {
        return processo.getDinamica().getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo();
    }
}
