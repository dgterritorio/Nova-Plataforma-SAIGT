package com.conkord.saigt.service.idus;

import com.conkord.saigt.exception.IdusInvalidoException;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.ContadoresIdus;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistorico;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistoricoRepository;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.SegmentoIdus;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ciclo de vida do IDUS do Processo (BL-INT-007): cria-o quando o Processo é gravado pela primeira vez e atualiza a
 * Submissão e o pubINCM. Corre na transação de quem grava o Processo: se o IDUS falhar, o Processo também não fica
 * gravado.
 */
@Service
public class IdusProcessoService {

    private static final Logger log = LoggerFactory.getLogger(IdusProcessoService.class);

    private static final String CATEGORIA_PLANO = "PLANO";
    private static final String CONTADOR_ORDEM_DINAMICA = "ORDEM_DINAMICA_PROVISORIA";
    private static final String MDC_TRACE_ID = "apiTraceId";
    private static final String SISTEMA_NOVA_SAIGT = "01";
    private static final String DICO_VARIOS_CONCELHOS = "0000";
    private static final String SUBMISSAO_INICIAL = "00";
    private static final String PREFIXO_PROVISORIO = "9";
    private static final String PREFIXO_DEFINITIVO = "0";
    private static final int ALGARISMOS_ID_IGT_SNIT = 5;
    private static final int PASSO_POR_OCORRENCIA = 100;
    private static final int SUBMISSAO_MAXIMA = 99;
    private static final DateTimeFormatter FORMATO_PUB_INCM = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> DINAMICAS_QUE_CRIAM_O_IGT = Set.of("ELABORACAO", "REVISAO");

    private final IdusProcessoRepository idusProcessoRepository;
    private final IdusProcessoHistoricoRepository historicoRepository;
    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final ProcessoRejeicaoRepository rejeicaoRepository;
    private final EntityManager entityManager;

    public IdusProcessoService(IdusProcessoRepository idusProcessoRepository,
                               IdusProcessoHistoricoRepository historicoRepository,
                               InstrumentoConcelhoRepository instrumentoConcelhoRepository,
                               ProcessoRejeicaoRepository rejeicaoRepository,
                               EntityManager entityManager) {
        this.idusProcessoRepository = idusProcessoRepository;
        this.historicoRepository = historicoRepository;
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
        this.rejeicaoRepository = rejeicaoRepository;
        this.entityManager = entityManager;
    }

    /**
     * Vazio quando o Processo não é de um Plano ou falta um código (fica um `WARN`, sem valores por omissão: um IDUS
     * enviado a sistemas externos é difícil de corrigir). Um segmento que não cabe nos seus algarismos lança
     * {@link com.conkord.saigt.exception.IdusInvalidoException}. Chamar depois do `saveAndFlush` do Processo, que é
     * quando o `numero_processo` fica atribuído.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdusProcesso> criar(Processo processo) {
        Dinamica dinamica = processo.getDinamica();
        if (dinamica.instrumentoDeInicio().isEmpty()) {
            // EF §2.2.8: o IDUS não está definido para os procedimentos não associados a um IGT.
            log.info("IDUS não aplicável, Dinâmica iniciada sem IGT — processoId={}", processo.getId());
            return Optional.empty();
        }
        Instrumento instrumento = dinamica.getInstrumento();
        TipoInstrumento tipoInstrumento = instrumento.getTipoInstrumento();
        if (!CATEGORIA_PLANO.equals(tipoInstrumento.getCategoria())) {
            return Optional.empty();
        }

        TipoDinamica tipoDinamica = dinamica.getInstrumentoDinamicaConfig().getTipoDinamica();
        String codigoProcedimento = processo.getInstrumentoProcedimentoConfig().getCodigoIdus();
        List<InstrumentoConcelho> concelhos =
                instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumento.getId());

        List<String> emFalta = segmentosEmFalta(concelhos, tipoInstrumento, tipoDinamica, codigoProcedimento);
        if (!emFalta.isEmpty()) {
            log.warn("IDUS não criado, faltam dados — processoId={}, segmentosEmFalta={}", processo.getId(), emFalta);
            return Optional.empty();
        }

        // O insert do Processo já tomou o lock de NUMERO_PROCESSO. A ordem é sempre essa e depois esta, por isso
        // duas transações nunca se bloqueiam uma à outra.
        bloquearOrdemDinamica();
        Optional<IdusProcesso> outroDaDinamica = idusProcessoRepository.findFirstByProcessoDinamicaId(dinamica.getId());

        IgtDoIdus igt = outroDaDinamica
                .map(outro -> new IgtDoIdus(outro.getIdIgt(), outro.isIdIgtDefinitivo()))
                .orElseGet(() -> igtNovo(processo, instrumento, tipoDinamica));
        String ordemDinamica = outroDaDinamica.map(IdusProcesso::getOrdemDinamica)
                .orElseGet(() -> PREFIXO_PROVISORIO + String.format("%04d", proximaOrdemDinamica()));

        SegmentosIdus segmentos = new SegmentosIdus(
                SISTEMA_NOVA_SAIGT,
                concelhos.size() == 1 ? concelhos.get(0).getMunicipioCodigo() : DICO_VARIOS_CONCELHOS,
                tipoInstrumento.getCodigoIdus(),
                igt.id(),
                tipoDinamica.getCodigoIdus(),
                ordemDinamica,
                String.format("%02d", dinamica.getTentativa()),
                String.format("%03d", Integer.parseInt(codigoProcedimento)
                        + PASSO_POR_OCORRENCIA * (processo.getOcorrencia() - 1)),
                SUBMISSAO_INICIAL,
                null);
        String idus = ComposicaoIdus.compor(segmentos);

        Instant agora = Instant.now();
        IdusProcesso criado = idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                .processo(processo)
                .idus(idus)
                .sistemaOrigem(segmentos.sistema())
                .idIgt(igt.id())
                .idIgtDefinitivo(igt.definitivo())
                .ordemDinamica(ordemDinamica)
                .ordemDefinitiva(false)
                .tentativa((short) dinamica.getTentativa())
                .procedimento(segmentos.procedimento())
                .submissao((short) 0)
                .atualizadoEm(agora)
                .build());
        historicoRepository.save(IdusProcessoHistorico.builder()
                .processoId(processo.getId())
                .segmento(SegmentoIdus.CRIACAO)
                .idusNovo(idus)
                .evento(EventoIdus.PROCESSO_CRIADO)
                .registadoEm(agora)
                .correlationId(correlationId())
                .build());
        log.info("IDUS criado — processoId={}, idus={}", processo.getId(), idus);
        return Optional.of(criado);
    }

    /**
     * Submissão = 1 + as recusas (DGT e INCM) do Processo, por isso resgatar e voltar a submeter não mexe no contador
     * e só a primeira submissão e as que seguem uma recusa o aumentam (BL-INT-007 passo 2, BL-INT-019). Vazio quando o
     * Processo não tem IDUS ou nada mudou. Chamar depois de gravar a submissão.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdusProcesso> registarSubmissao(Processo processo) {
        Optional<IdusProcesso> encontrado = idusProcessoRepository.findById(processo.getId());
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        IdusProcesso idusProcesso = encontrado.get();
        int submissaoEsperada = 1 + rejeicaoRepository.maiorSequencia(processo.getId()).orElse(0);
        if (idusProcesso.getSubmissao() >= submissaoEsperada) {
            return Optional.empty();
        }
        int nova = idusProcesso.getSubmissao() + 1;
        if (nova > SUBMISSAO_MAXIMA) {
            throw new IdusInvalidoException("Sub-contador de submissão inválido: o máximo é " + SUBMISSAO_MAXIMA
                    + " e o Processo " + processo.getId() + " ia para " + nova + ".");
        }
        String anterior = idusProcesso.getIdus();
        String idus = ComposicaoIdus.compor(ComposicaoIdus.decompor(anterior).comSubmissao(String.format("%02d", nova)));
        Instant agora = Instant.now();
        idusProcesso.setSubmissao((short) nova);
        return Optional.of(gravarMudanca(idusProcesso, idus, anterior, SegmentoIdus.SUBMISSAO, EventoIdus.SUBMISSAO,
                agora));
    }

    /**
     * Acrescenta ao IDUS a data de publicação do Processo (BL-INT-007 passo 3): a que a INCM devolveu ou, no registo
     * externo, a que o `MASTER` indicou. Sem data a publicação segue e o IDUS fica como estava, com um `WARN`: o ato já
     * está publicado e não se desfaz por causa do IDUS. Vazio quando o Processo não tem IDUS, já tem pubINCM ou falta a
     * data.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdusProcesso> registarPublicacaoIncm(Processo processo) {
        Optional<IdusProcesso> encontrado = idusProcessoRepository.findById(processo.getId());
        if (encontrado.isEmpty() || encontrado.get().getPubIncm() != null) {
            return Optional.empty();
        }
        LocalDate dataPublicacao = processo.getDataPublicacaoDr();
        if (dataPublicacao == null) {
            log.warn("pubINCM não registado, a INCM não devolveu a data de publicação — processoId={}",
                    processo.getId());
            return Optional.empty();
        }
        IdusProcesso idusProcesso = encontrado.get();
        String pubIncm = FORMATO_PUB_INCM.format(dataPublicacao);
        String anterior = idusProcesso.getIdus();
        String idus = ComposicaoIdus.compor(ComposicaoIdus.decompor(anterior).comPubIncm(pubIncm));
        Instant agora = Instant.now();
        idusProcesso.setPubIncm(pubIncm);
        return Optional.of(gravarMudanca(idusProcesso, idus, anterior, SegmentoIdus.PUB_INCM,
                EventoIdus.PUBLICACAO_INCM, agora));
    }

    /**
     * Entrada em vigor (BL-INT-008 passo 1): em todos os processos da Dinâmica, o ID_IGT provisório passa ao do SNIT
     * ("0" + 5 algarismos) e a Ordem_Dinâmica a "0" + a ordem do SNIT em 4 algarismos (OI-410), ambos definitivos, com
     * uma linha de histórico por segmento que mudou. Um ID_IGT já definitivo não muda. Corre na transação de quem recebe
     * a entrada em vigor, para a atualização ser atómica para toda a Dinâmica.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<IdusProcesso> registarEntradaEmVigor(UUID dinamicaId, long idIgtSnit, int ordemSnit) {
        String idIgt = PREFIXO_DEFINITIVO + String.format("%0" + ALGARISMOS_ID_IGT_SNIT + "d", idIgtSnit);
        String ordem = PREFIXO_DEFINITIVO + String.format("%04d", ordemSnit);
        Instant agora = Instant.now();
        List<IdusProcesso> atualizados = new ArrayList<>();
        for (IdusProcesso idusProcesso : idusProcessoRepository.findByProcessoDinamicaId(dinamicaId)) {
            String atual = idusProcesso.getIdus();
            if (!idusProcesso.isIdIgtDefinitivo()) {
                String novo = ComposicaoIdus.compor(ComposicaoIdus.decompor(atual)
                        .comIgtEOrdem(idIgt, idusProcesso.getOrdemDinamica()));
                idusProcesso.setIdIgt(idIgt);
                idusProcesso.setIdIgtDefinitivo(true);
                gravarMudanca(idusProcesso, novo, atual, SegmentoIdus.ID_IGT, EventoIdus.ENTRADA_EM_VIGOR, agora);
                atual = novo;
            } else if (!idIgt.equals(idusProcesso.getIdIgt())) {
                log.warn("Entrada em vigor com outro ID_IGT do que o definitivo do processo, fica o do processo — "
                        + "processoId={} idIgtProcesso={} idIgtSnit={}", idusProcesso.getProcessoId(),
                        idusProcesso.getIdIgt(), idIgt);
            }
            if (!idusProcesso.isOrdemDefinitiva() || !ordem.equals(idusProcesso.getOrdemDinamica())) {
                SegmentosIdus segmentos = ComposicaoIdus.decompor(atual);
                String novo = ComposicaoIdus.compor(segmentos.comIgtEOrdem(segmentos.idIgt(), ordem));
                idusProcesso.setOrdemDinamica(ordem);
                idusProcesso.setOrdemDefinitiva(true);
                gravarMudanca(idusProcesso, novo, atual, SegmentoIdus.ORDEM_DINAMICA, EventoIdus.ENTRADA_EM_VIGOR, agora);
            }
            atualizados.add(idusProcesso);
        }
        return atualizados;
    }

    private IdusProcesso gravarMudanca(IdusProcesso idusProcesso, String idusNovo, String idusAnterior,
                                       SegmentoIdus segmento, EventoIdus evento, Instant agora) {
        idusProcesso.setIdus(idusNovo);
        idusProcesso.setAtualizadoEm(agora);
        IdusProcesso gravado = idusProcessoRepository.saveAndFlush(idusProcesso);
        historicoRepository.save(IdusProcessoHistorico.builder()
                .processoId(idusProcesso.getProcessoId())
                .segmento(segmento)
                .idusAnterior(idusAnterior)
                .idusNovo(idusNovo)
                .evento(evento)
                .registadoEm(agora)
                .correlationId(correlationId())
                .build());
        log.info("IDUS atualizado — processoId={}, evento={}, idus={}", idusProcesso.getProcessoId(), evento, idusNovo);
        return gravado;
    }

    private static List<String> segmentosEmFalta(List<InstrumentoConcelho> concelhos, TipoInstrumento tipoInstrumento,
                                                 TipoDinamica tipoDinamica, String codigoProcedimento) {
        List<String> emFalta = new ArrayList<>();
        if (concelhos.isEmpty()) {
            emFalta.add("DICO");
        }
        if (tipoInstrumento.getCodigoIdus() == null) {
            emFalta.add("IGT");
        }
        if (tipoDinamica.getCodigoIdus() == null) {
            emFalta.add("Dinâmica");
        }
        if (codigoProcedimento == null) {
            emFalta.add("Procedimento");
        }
        return emFalta;
    }

    /**
     * Elaboração e Revisão criam o IGT, por isso o primeiro Processo dá o seu número ao ID_IGT provisório e os
     * seguintes da Dinâmica copiam-no. Nas outras Dinâmicas o IGT já existe: vale o ID do SNIT, ou o provisório
     * enquanto o Instrumento ainda não o tem (FR-191).
     */
    private static IgtDoIdus igtNovo(Processo processo, Instrumento instrumento, TipoDinamica tipoDinamica) {
        String provisorio = PREFIXO_PROVISORIO + processo.getNumeroProcesso();
        String idIgtSnit = instrumento.getIdIgtSnit();
        if (DINAMICAS_QUE_CRIAM_O_IGT.contains(tipoDinamica.getCodigo()) || idIgtSnit == null || idIgtSnit.isBlank()) {
            return new IgtDoIdus(provisorio, false);
        }
        return new IgtDoIdus(PREFIXO_DEFINITIVO + "0".repeat(Math.max(0, ALGARISMOS_ID_IGT_SNIT - idIgtSnit.length()))
                + idIgtSnit, true);
    }

    private void bloquearOrdemDinamica() {
        entityManager.unwrap(Session.class).doWork(ligacao -> ContadoresIdus.bloquear(ligacao, CONTADOR_ORDEM_DINAMICA));
    }

    private int proximaOrdemDinamica() {
        int[] valor = new int[1];
        entityManager.unwrap(Session.class)
                .doWork(ligacao -> valor[0] = ContadoresIdus.proximo(ligacao, CONTADOR_ORDEM_DINAMICA));
        return valor[0];
    }

    private static String correlationId() {
        String id = MDC.get(MDC_TRACE_ID);
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }

    private record IgtDoIdus(String id, boolean definitivo) {
    }
}
