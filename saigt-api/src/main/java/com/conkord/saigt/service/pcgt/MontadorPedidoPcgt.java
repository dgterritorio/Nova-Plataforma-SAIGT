package com.conkord.saigt.service.pcgt;

import com.conkord.saigt.integracao.pcgt.BlocoAprovacaoPcgt;
import com.conkord.saigt.integracao.pcgt.BlocoComissaoConsultivaPcgt;
import com.conkord.saigt.integracao.pcgt.BlocoDepositoPcgt;
import com.conkord.saigt.integracao.pcgt.EntidadePcgt;
import com.conkord.saigt.integracao.pcgt.FicheiroReferenciaPcgt;
import com.conkord.saigt.integracao.pcgt.PedidoAtualizacaoProcessoPcgt;
import com.conkord.saigt.integracao.pcgt.PedidoCriacaoProcessoPcgt;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Monta os pedidos à PCGT (`contracts/pcgt-processos.pdf`, 04/11) de um processo publicado. `preparar` lê
 * associações preguiçosas e corre dentro de uma transação; os pedidos montam-se depois, sem base de dados. Um dado em
 * falta é omitido com um WARN que o nomeia; o POST não sai sem os tipos e a entidade.
 */
@Slf4j
@Component
public class MontadorPedidoPcgt {

    static final String CONSTITUICAO_CC = "CONSTITUICAO_CC";
    static final String DECISAO_INICIO = "DECISAO_INICIO";
    static final String DESPACHO_CCDR_DEP = "DESPACHO_CCDR_DEP";
    static final String DELIBERACAO_CM_DEP = "DELIBERACAO_CM_DEP";
    static final String DELIBERACAO_AM_DEP = "DELIBERACAO_AM_DEP";
    static final String DELIBERACAO_DEP = "DELIBERACAO_DEP";
    private static final Pattern FORMATO_IDUS =
            Pattern.compile("^\\d{2}\\.\\d{4}\\.\\d{2}\\.\\d{6}\\.\\d{2}\\.\\d{5}\\.\\d{2}\\.\\d{3}\\.\\d{2}\\.\\d{8}$");

    private final IdusProcessoRepository idusProcessoRepository;
    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final ProcessoDocumentoRepository documentoRepository;
    private final ProcessoRepository processoRepository;
    private final NumeroDepositoService numeroDepositoService;
    private final ObjectMapper objectMapper;

    public MontadorPedidoPcgt(IdusProcessoRepository idusProcessoRepository,
                              InstrumentoConcelhoRepository instrumentoConcelhoRepository,
                              ProcessoDocumentoRepository documentoRepository, ProcessoRepository processoRepository,
                              NumeroDepositoService numeroDepositoService, ObjectMapper objectMapper) {
        this.idusProcessoRepository = idusProcessoRepository;
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
        this.documentoRepository = documentoRepository;
        this.processoRepository = processoRepository;
        this.numeroDepositoService = numeroDepositoService;
        this.objectMapper = objectMapper;
    }

    DadosPedidoPcgt preparar(Processo processo) {
        Dinamica dinamica = processo.getDinamica();
        TipoInstrumento tipoInstrumento = dinamica.getInstrumentoDinamicaConfig().getTipoInstrumento();
        TipoDinamica tipoDinamica = processo.getDinamica().getInstrumentoDinamicaConfig().getTipoDinamica();
        Entidade entidade = processo.getEntidade();
        String idus = idusProcessoRepository.findById(processo.getId()).map(IdusProcesso::getIdus).orElse(null);
        boolean procedimentoFinal = numeroDepositoService.doProcedimentoFinal(processo);

        List<ProcessoDocumento> documentos = documentoRepository.findAtuaisDoProcessoPorCodigos(processo.getId(),
                Set.of(DESPACHO_CCDR_DEP, DELIBERACAO_CM_DEP, DELIBERACAO_AM_DEP, DELIBERACAO_DEP));
        return new DadosPedidoPcgt(
                processo.getId(),
                processo.getNumeroProcesso(),
                processo.getDinamica().getNumeroDinamica(),
                idus,
                codigoIdus(processo, idus),
                municipio(processo, dinamica),
                processo.getTipoAto() == null ? null : processo.getTipoAto().getNome(),
                processo.getNumeroDr(),
                processo.getDataPublicacaoDr(),
                processo.getLinkDr(),
                tipoDinamica.getNome() + " — " + dinamica.designacao(),
                tipoInstrumento.getPcgtCodigo(),
                tipoDinamica.getPcgtCodigo(),
                entidade == null ? null : entidade.getCodigo(),
                comissaoConsultiva(processo, documentos),
                aprovacaoCmEim(processo, documentos),
                aprovacaoAm(processo, documentos, procedimentoFinal),
                processo.getNumeroDeposito() == null ? null
                        : new DadosPedidoPcgt.Deposito(processo.getNumeroDeposito(), processo.getDataPublicacaoDr()));
    }

    /** `PUT`: substitui os campos simples na PCGT, por isso vai sempre completo. */
    PedidoAtualizacaoProcessoPcgt montarAtualizacao(DadosPedidoPcgt dados, Map<UUID, String> urls) {
        return new PedidoAtualizacaoProcessoPcgt(dados.codigoProcesso(), dados.codigoIdus(), dados.municipioToken(),
                dados.tipoAto(), dados.numeroAto(), dados.dataAto(), dados.linkAto(),
                comissaoConsultiva(dados, urls), null, aprovacao(dados, urls), deposito(dados));
    }

    /** Vazio, com um WARN que nomeia o campo, sem os tipos ou a entidade que a criação exige. */
    Optional<PedidoCriacaoProcessoPcgt> montarCriacao(DadosPedidoPcgt dados, Map<UUID, String> urls) {
        if (dados.tipoInstrumentoCodigo() == null) {
            return emFalta(dados, "tipo_instrumento.pcgt_codigo");
        }
        if (dados.tipoProcedimentoCodigo() == null) {
            return emFalta(dados, "tipo_dinamica.pcgt_codigo");
        }
        if (dados.codigoEntidade() == null || dados.codigoEntidade().isBlank()) {
            return emFalta(dados, "entidade.codigo");
        }
        return Optional.of(new PedidoCriacaoProcessoPcgt(dados.codigoProcesso(), null, null, dados.designacao(),
                dados.tipoInstrumentoCodigo(), dados.tipoProcedimentoCodigo(), dados.municipioToken(),
                dados.codigoIdus(), new EntidadePcgt(dados.codigoEntidade()), dados.tipoAto(), dados.numeroAto(),
                dados.dataAto(), dados.linkAto(), comissaoConsultiva(dados, urls), null, aprovacao(dados, urls),
                deposito(dados)));
    }

    private static String codigoIdus(Processo processo, String idus) {
        if (idus != null && FORMATO_IDUS.matcher(idus).matches()) {
            return idus;
        }
        // Fora do formato a PCGT recusaria o pedido inteiro com 400: vai sem ele.
        log.warn("Comunicação à PCGT sem codigoIdus, {} — processoId={} numeroProcesso={}",
                idus == null ? "processo sem IDUS" : "IDUS fora do formato de 10 grupos", processo.getId(),
                processo.getNumeroProcesso());
        return null;
    }

    /** Vários concelhos: o de DTCC mais baixo. */
    /** O primeiro concelho do Instrumento de início ou, numa Dinâmica iniciada sem IGT, dos Instrumentos que abrange. */
    private String municipio(Processo processo, Dinamica dinamica) {
        List<UUID> instrumentoIds = dinamica.instrumentoDeInicio().map(instrumento -> List.of(instrumento.getId()))
                .orElseGet(() -> dinamica.getInstrumentos().stream().map(Instrumento::getId).sorted().toList());
        List<InstrumentoConcelho> concelhos = instrumentoIds.stream()
                .flatMap(id -> instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(id).stream())
                .sorted(Comparator.comparing(InstrumentoConcelho::getMunicipioCodigo))
                .toList();
        if (concelhos.isEmpty()) {
            log.warn("Comunicação à PCGT sem municipioToken, Instrumento sem concelhos — processoId={} numeroProcesso={}",
                    processo.getId(), processo.getNumeroProcesso());
            return null;
        }
        return concelhos.get(0).getMunicipioCodigo();
    }

    private static DadosPedidoPcgt.ComissaoConsultiva comissaoConsultiva(Processo processo,
                                                                         List<ProcessoDocumento> documentos) {
        if (!CONSTITUICAO_CC.equals(codigoProcedimento(processo))) {
            return null;
        }
        String tipoAto = processo.getTipoAto() == null ? "Ato" : processo.getTipoAto().getNome();
        String titulo = processo.getNumeroDr() == null ? tipoAto : tipoAto + " n.º " + processo.getNumeroDr();
        return new DadosPedidoPcgt.ComissaoConsultiva(titulo, processo.getLinkDr(),
                documentosDe(documentos, DESPACHO_CCDR_DEP));
    }

    /** A deliberação da CM é a que determinou o procedimento: a data vem da Decisão de início publicada. */
    private DadosPedidoPcgt.Deliberacao aprovacaoCmEim(Processo processo, List<ProcessoDocumento> documentos) {
        List<DadosPedidoPcgt.Documento> ficheiros = documentosDe(documentos, DELIBERACAO_CM_DEP);
        if (ficheiros.isEmpty()) {
            return null;
        }
        LocalDate data = processoRepository
                .findFirstByDinamicaAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByCriadoEmDesc(
                        processo.getDinamica(), DECISAO_INICIO, EstadoProcesso.PUBLICADO)
                .map(decisao -> dataDeliberacao(decisao.getDadosFormulario()))
                .orElse(null);
        if (data == null) {
            log.warn("Comunicação à PCGT sem a parte CM/EIM da aprovação, sem data da deliberação da Decisão de início "
                    + "publicada — processoId={} numeroProcesso={}", processo.getId(), processo.getNumeroProcesso());
            return null;
        }
        return new DadosPedidoPcgt.Deliberacao(data, ficheiros);
    }

    private DadosPedidoPcgt.Deliberacao aprovacaoAm(Processo processo, List<ProcessoDocumento> documentos,
                                                    boolean procedimentoFinal) {
        List<DadosPedidoPcgt.Documento> ficheiros = new ArrayList<>(documentosDe(documentos, DELIBERACAO_AM_DEP));
        if (procedimentoFinal) {
            ficheiros.addAll(documentosDe(documentos, DELIBERACAO_DEP));
        }
        if (ficheiros.isEmpty()) {
            return null;
        }
        LocalDate data = dataDeliberacao(processo.getDadosFormulario());
        if (data == null) {
            log.warn("Comunicação à PCGT sem a parte AM da aprovação, sem data da deliberação no formulário — "
                    + "processoId={} numeroProcesso={}", processo.getId(), processo.getNumeroProcesso());
            return null;
        }
        return new DadosPedidoPcgt.Deliberacao(data, List.copyOf(ficheiros));
    }

    private LocalDate dataDeliberacao(String dadosFormulario) {
        if (dadosFormulario == null || dadosFormulario.isBlank()) {
            return null;
        }
        try {
            JsonNode data = objectMapper.readTree(dadosFormulario).path("informacaoBase").path("deliberacao")
                    .path("dataDeliberacao");
            return data.isTextual() ? LocalDate.parse(data.asText()) : null;
        } catch (JsonProcessingException | DateTimeParseException e) {
            return null;
        }
    }

    private static String codigoProcedimento(Processo processo) {
        return processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo();
    }

    private static List<DadosPedidoPcgt.Documento> documentosDe(List<ProcessoDocumento> documentos, String codigo) {
        return documentos.stream().filter(d -> codigo.equals(d.getDocumentoConfig().getCodigo()))
                .map(d -> new DadosPedidoPcgt.Documento(d.getId(), d.getNomeFicheiro())).toList();
    }

    private static BlocoComissaoConsultivaPcgt comissaoConsultiva(DadosPedidoPcgt dados, Map<UUID, String> urls) {
        DadosPedidoPcgt.ComissaoConsultiva cc = dados.comissaoConsultiva();
        return cc == null ? null : new BlocoComissaoConsultivaPcgt(cc.titulo(), cc.url(), ficheiros(cc.ficheiros(), urls));
    }

    private static BlocoAprovacaoPcgt aprovacao(DadosPedidoPcgt dados, Map<UUID, String> urls) {
        DadosPedidoPcgt.Deliberacao cm = dados.aprovacaoCmEim();
        DadosPedidoPcgt.Deliberacao am = dados.aprovacaoAm();
        if (cm == null && am == null) {
            return null;
        }
        return new BlocoAprovacaoPcgt(cm == null ? null : cm.data(), cm == null ? null : ficheiros(cm.ficheiros(), urls),
                am == null ? null : am.data(), am == null ? null : ficheiros(am.ficheiros(), urls));
    }

    private static BlocoDepositoPcgt deposito(DadosPedidoPcgt dados) {
        return dados.deposito() == null ? null
                : new BlocoDepositoPcgt(dados.deposito().numero(), dados.deposito().data());
    }

    private static List<FicheiroReferenciaPcgt> ficheiros(List<DadosPedidoPcgt.Documento> documentos,
                                                          Map<UUID, String> urls) {
        return documentos.stream().map(d -> new FicheiroReferenciaPcgt(d.nome(), urls.get(d.id()))).toList();
    }

    private static <T> Optional<T> emFalta(DadosPedidoPcgt dados, String campo) {
        log.warn("Criação do processo na PCGT por fazer, falta {} — processoId={} numeroProcesso={} numeroDinamica={}",
                campo, dados.processoId(), dados.numeroProcesso(), dados.codigoProcesso());
        return Optional.empty();
    }
}
