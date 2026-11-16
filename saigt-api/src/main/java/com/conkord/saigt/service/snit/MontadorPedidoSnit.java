package com.conkord.saigt.service.snit;

import com.conkord.saigt.integracao.snit.DepositoSnit;
import com.conkord.saigt.integracao.snit.FicheiroDepositoSnit;
import com.conkord.saigt.integracao.snit.PublicacaoDrSnit;
import com.conkord.saigt.integracao.snit.PublicacaoSnit;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Monta os pedidos ao SNIT do procedimento final publicado (BL-INT-004). Lê associações preguiçosas do processo: tem
 * de ser chamado dentro de uma transação. Um dado obrigatório em falta devolve vazio e fica um WARN que o nomeia.
 */
@Slf4j
@Component
public class MontadorPedidoSnit {

    private static final String ESTADO_PUBLICACAO = "00";
    private static final String SERIE_PRIMEIRA = "PRIMEIRA";
    private static final String EXTENSAO_ZIP = "zip";
    private static final String PASTA_SUBSTITUIDOS = "/SUBSTITUIDOS/";
    private static final String CAMINHO_DEPOSITOS = "/api/v1/integracoes/snit/depositos/";
    // `summary` tem no máximo 300 caracteres no contrato do SNIT; `processo.sumario` aceita 2000.
    private static final int MAXIMO_SUMARIO = 300;

    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final ReferenciaSnitConsultaService referenciaSnitConsultaService;
    private final IdusProcessoRepository idusProcessoRepository;
    private final ProcessoDocumentoRepository documentoRepository;
    private final DepositoSnitProperties propriedadesDeposito;
    private final String urlBase;

    public MontadorPedidoSnit(InstrumentoConcelhoRepository instrumentoConcelhoRepository,
                              ReferenciaSnitConsultaService referenciaSnitConsultaService,
                              IdusProcessoRepository idusProcessoRepository,
                              ProcessoDocumentoRepository documentoRepository,
                              DepositoSnitProperties propriedadesDeposito,
                              @Value("${app.publico.base-url}") String urlBase) {
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
        this.referenciaSnitConsultaService = referenciaSnitConsultaService;
        this.idusProcessoRepository = idusProcessoRepository;
        this.documentoRepository = documentoRepository;
        this.propriedadesDeposito = propriedadesDeposito;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    public Optional<PublicacaoSnit> montarPublicacao(Processo processo) {
        Instrumento instrumento = processo.getDinamica().getInstrumento();
        if (instrumento == null) {
            // O registo no SNIT é por Instrumento: o de uma Dinâmica iniciada sem IGT não está definido.
            return emFalta(processo, "dinamica.instrumento_id (Dinâmica iniciada sem IGT)");
        }
        Long ssaigtId = ssaigtId(processo);
        if (ssaigtId == null) {
            return emFalta(processo, "processo.numero_processo");
        }
        Integer typeId = inteiro(instrumento.getTipoInstrumento().getSnitCodigo());
        if (typeId == null) {
            return emFalta(processo, "tipo_instrumento.snit_codigo");
        }
        Integer procedureId = inteiro(processo.getDinamica().getInstrumentoDinamicaConfig().getTipoDinamica()
                .getSnitCodigo());
        if (procedureId == null) {
            return emFalta(processo, "tipo_dinamica.snit_codigo");
        }
        Optional<Integer> classId = referenciaSnitConsultaService.classIdDoTipo(instrumento.getTipoInstrumento());
        if (classId.isEmpty()) {
            return emFalta(processo, "classId (snit_referencia INSTRUMENT_TYPES " + typeId + ")");
        }
        List<String> dtccs = instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(
                instrumento.getId()).stream().map(InstrumentoConcelho::getMunicipioCodigo).toList();
        if (dtccs.size() > 1) {
            // O DTCC de um Instrumento intermunicipal não está definido para o SNIT.
            log.warn("Registo no SNIT por fazer, Instrumento com {} concelhos — processoId={} numeroProcesso={}",
                    dtccs.size(), processo.getId(), processo.getNumeroProcesso());
            return Optional.empty();
        }
        if (dtccs.isEmpty()) {
            return emFalta(processo, "instrumento_concelho.municipio_codigo");
        }
        String dtcc = dtccs.get(0);
        Optional<String> nut2Code = referenciaSnitConsultaService.nut2DoMunicipio(dtcc);
        if (nut2Code.isEmpty()) {
            return emFalta(processo, "nut2Code (snit_referencia MUNICIPALITIES " + dtcc + ")");
        }
        String designacao = instrumento.getDesignacao();
        if (designacao == null || designacao.isBlank()) {
            return emFalta(processo, "instrumento.designacao");
        }
        TipoAto tipoAto = processo.getTipoAto();
        if (tipoAto == null) {
            return emFalta(processo, "processo.tipo_ato");
        }
        if (processo.getDataPublicacaoDr() == null) {
            return emFalta(processo, "processo.data_publicacao_dr");
        }
        PublicacaoDrSnit publicacao = new PublicacaoDrSnit(tipoAto.getNome(),
                serie(processo.serieDrEfetiva()), processo.getNumeroDr(),
                processo.getDataPublicacaoDr(), processo.getLinkDr());
        return Optional.of(new PublicacaoSnit(ssaigtId, typeId, procedureId, classId.get(), designacao,
                nut2Code.get(), dtcc, sumario(processo), ESTADO_PUBLICACAO, publicacao, null));
    }

    private static String sumario(Processo processo) {
        String sumario = processo.getSumario();
        if (sumario == null || sumario.isBlank()) {
            return null;
        }
        sumario = sumario.strip();
        if (sumario.codePointCount(0, sumario.length()) <= MAXIMO_SUMARIO) {
            return sumario;
        }
        log.info("Sumário cortado a {} caracteres para o SNIT — processoId={} numeroProcesso={}", MAXIMO_SUMARIO,
                processo.getId(), processo.getNumeroProcesso());
        return sumario.substring(0, sumario.offsetByCodePoints(0, MAXIMO_SUMARIO)).stripTrailing();
    }

    public Optional<DepositoSnit> montarDeposito(Processo processo, Instant depositadoEm) {
        Long ssaigtId = ssaigtId(processo);
        if (ssaigtId == null) {
            return emFalta(processo, "processo.numero_processo");
        }
        if (processo.getNumeroDeposito() == null) {
            return emFalta(processo, "processo.numero_deposito");
        }
        String idus = idusProcessoRepository.findById(processo.getId()).map(IdusProcesso::getIdus).orElse(null);
        return Optional.of(new DepositoSnit(ssaigtId, processo.getNumeroDeposito(), depositadoEm, idus,
                urlPasta(processo), null));
    }

    /** Os ZIP de peças gráficas atuais das caixas do depósito, comunicados um a um em `deposits/files`. */
    public List<FicheiroDepositoSnit> montarFicheiros(Processo processo) {
        String urlPasta = urlPasta(processo);
        return documentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId()).stream()
                .filter(documento -> propriedadesDeposito.caixas().contains(documento.getCategoria()))
                .filter(documento -> !documento.getCaminhoFicheiro().contains(PASTA_SUBSTITUIDOS))
                .filter(MontadorPedidoSnit::zipDePecaGrafica)
                .map(documento -> new FicheiroDepositoSnit(documento.getId().toString(), documento.getNomeFicheiro(),
                        null, urlPasta + "/ficheiros/" + documento.getId(), true, documento.getCriadoEm()))
                .toList();
    }

    private String urlPasta(Processo processo) {
        return urlBase + CAMINHO_DEPOSITOS + processo.getNumeroProcesso();
    }

    private static boolean zipDePecaGrafica(ProcessoDocumento documento) {
        DocumentoConfig item = documento.getDocumentoConfig();
        return item != null && item.getTipoElemento() == TipoElemento.PECA_GRAFICA
                && EXTENSAO_ZIP.equalsIgnoreCase(documento.getExtensao());
    }

    private static String serie(String serieDr) {
        if (serieDr == null) {
            return null;
        }
        return SERIE_PRIMEIRA.equals(serieDr) ? "1" : "2";
    }

    private static Long ssaigtId(Processo processo) {
        try {
            return Long.valueOf(processo.getNumeroProcesso());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer inteiro(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Integer.valueOf(valor.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <T> Optional<T> emFalta(Processo processo, String dado) {
        log.warn("Registo no SNIT por fazer, falta {} — processoId={} numeroProcesso={}", dado, processo.getId(),
                processo.getNumeroProcesso());
        return Optional.empty();
    }
}
