package com.conkord.saigt.service.instrumento;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/** Consulta na BD e ciclo completo do job, com o listener `AFTER_COMMIT` de um consumidor dos eventos. */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class InatividadeProcessoJobIntegrationTest {

    private static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    /** O estado que um listener `AFTER_COMMIT` vê na BD quando recebe o evento. */
    static final Map<UUID, String> ESTADO_VISTO_NO_COMMIT = new ConcurrentHashMap<>();

    @TestConfiguration
    static class ListenerAposCommit {

        @Bean
        ConsumidorDosEventos consumidorDosEventosDeInatividade(JdbcTemplate jdbcTemplate) {
            return new ConsumidorDosEventos(jdbcTemplate);
        }
    }

    static class ConsumidorDosEventos {

        private final JdbcTemplate jdbcTemplate;

        ConsumidorDosEventos(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void aoCommit(ProcessoAnuladoPorInatividadeEvento evento) {
            ESTADO_VISTO_NO_COMMIT.put(evento.processoId(), jdbcTemplate.queryForObject(
                    "SELECT estado FROM processo WHERE id = ?", String.class, evento.processoId()));
        }
    }

    @Autowired
    private InatividadeProcessoJob job;

    @SpyBean
    private InatividadeProcessoService servico;

    @Autowired
    private ApplicationEvents eventos;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private ArmazenamentoIntencaoRepository intencaoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(InatividadeProcessoJob.class);

    private Instrumento instrumento;
    private InstrumentoDinamicaConfig dinamicaConfig;
    private InstrumentoProcedimentoConfig procedimentoConfig;
    private Utilizador autor;
    private Entidade entidade;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);

        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("INATIV" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("INATIV-D" + n, "Alteração"));
        dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("INATIV-P" + n, "Procedimento " + n));
        procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5073%05d".formatted(n), "Câmara " + n));
        autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor " + n, "autor" + n + "@cm-inativ.pt", "INCM"));
        instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, String.valueOf(n), "2026/INATIV/" + n, autor, autor));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static Instant ha(Period periodo) {
        return Instant.now().atZone(LISBOA).minus(periodo).toInstant();
    }

    /** Cada processo na sua Dinâmica; `atualizado_em` só se muda por SQL, porque cada gravação o repõe. */
    private Processo criarProcesso(EstadoProcesso estado, Instant atualizadoEm) {
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimentoConfig, autor, entidade);
        processo.setEstado(estado);
        processo = processoRepository.saveAndFlush(processo);
        jdbcTemplate.update("UPDATE processo SET atualizado_em = ? WHERE id = ?", Timestamp.from(atualizadoEm),
                processo.getId());
        return processo;
    }

    private List<String> guardarFicheiros(Processo processo, int quantidade) {
        List<String> caminhos = new java.util.ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            String caminho = "processos/" + processo.getNumeroProcesso() + "/" + UUID.randomUUID();
            processoDocumentoRepository.save(new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                    "f" + i + ".pdf", caminho, "c".repeat(64), "v1", 10L, autor));
            caminhos.add(caminho);
        }
        processoDocumentoRepository.flush();
        return caminhos;
    }

    private Processo recarregar(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow();
    }

    private List<Object> eventosDe(Processo... processos) {
        Set<UUID> ids = Set.of(java.util.Arrays.stream(processos).map(Processo::getId).toArray(UUID[]::new));
        return java.util.stream.Stream.<Object>concat(
                eventos.stream(AvisoInatividadeProcessoEvento.class).filter(e -> ids.contains(e.processoId())),
                eventos.stream(ProcessoAnuladoPorInatividadeEvento.class).filter(e -> ids.contains(e.processoId())))
                .toList();
    }

    private List<OperacaoArmazenamento> intencoesDe(List<String> caminhos) {
        return caminhos.stream().flatMap(c -> intencaoRepository.findByChaveObjeto(c).stream())
                .map(ArmazenamentoIntencao::getOperacao).toList();
    }

    @Test
    void deveAvisarEAnularConformeOTempoParadoDeCadaProcesso() {
        Processo aDoisMeses = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 2, 29)));
        Processo aTresMeses = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(3)));
        Processo aTresMesesEMeio = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 3, 16)));
        Instant atualizadoEmAnulado = ha(Period.of(0, 4, 1));
        Processo aQuatroMeses = criarProcesso(EstadoProcesso.EM_SUBMISSAO, atualizadoEmAnulado);
        List<String> ficheiros = guardarFicheiros(aQuatroMeses, 3);
        long versaoAntes = recarregar(aQuatroMeses).getVersao();
        Processo enviado = criarProcesso(EstadoProcesso.ENVIADO_DGT, ha(Period.ofMonths(5)));
        Processo rejeitado = criarProcesso(EstadoProcesso.REJEITADO_DGT, ha(Period.ofMonths(5)));
        Processo anuladoIncm = criarProcesso(EstadoProcesso.ANULADO_INCM, ha(Period.ofMonths(5)));
        List<String> ficheirosDoEnviado = guardarFicheiros(enviado, 1);

        job.executar();

        assertThat(eventosDe(aDoisMeses, enviado, rejeitado, anuladoIncm)).isEmpty();
        assertThat(recarregar(aDoisMeses).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        assertThat(eventosDe(aTresMeses)).singleElement().isInstanceOfSatisfying(AvisoInatividadeProcessoEvento.class,
                aviso -> assertThat(aviso.limiar()).isEqualTo(LimiarInatividade.PRIMEIRO_AVISO));
        assertThat(recarregar(aTresMeses).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(eventosDe(aTresMesesEMeio)).singleElement().isInstanceOfSatisfying(
                AvisoInatividadeProcessoEvento.class,
                aviso -> assertThat(aviso.limiar()).isEqualTo(LimiarInatividade.SEGUNDO_AVISO));

        Processo anulado = recarregar(aQuatroMeses);
        assertThat(anulado.getEstado()).isEqualTo(EstadoProcesso.ANULADO);
        assertThat(anulado.getVersao()).isGreaterThan(versaoAntes);
        assertThat(intencoesDe(ficheiros)).containsExactly(OperacaoArmazenamento.APAGAMENTO,
                OperacaoArmazenamento.APAGAMENTO, OperacaoArmazenamento.APAGAMENTO);
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(anulado.getId())).hasSize(3);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", anulado.getId().toString()))
                .extracting("acao", "estadoAntes", "estadoDepois", "utilizadorId")
                .containsExactly(tuple("ANULACAO_INATIVIDADE", "EM_SUBMISSAO", "ANULADO", null));
        assertThat(eventosDe(aQuatroMeses)).containsExactly(new ProcessoAnuladoPorInatividadeEvento(anulado.getId()));
        assertThat(ESTADO_VISTO_NO_COMMIT.get(anulado.getId())).isEqualTo("ANULADO");
        assertThat(dinamicaRepository.findById(anulado.getDinamica().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDinamica.EM_CURSO);

        assertThat(recarregar(enviado).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(recarregar(rejeitado).getEstado()).isEqualTo(EstadoProcesso.REJEITADO_DGT);
        assertThat(recarregar(anuladoIncm).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(intencoesDe(ficheirosDoEnviado)).isEmpty();
    }

    @Test
    void deveDevolverSoOsProcessosEmSubmissaoAlemDoLimiteDosMaisAntigosParaOsMaisRecentes() {
        Processo aDoisMeses = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(2)));
        Processo aTresMeses = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 3, 1)));
        Processo aSeisMeses = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(6)));
        Processo enviado = criarProcesso(EstadoProcesso.ENVIADO_DGT, ha(Period.ofMonths(5)));
        Set<UUID> deste = Set.of(aDoisMeses.getId(), aTresMeses.getId(), aSeisMeses.getId(), enviado.getId());

        List<UUID> ids = processoRepository.findIdsEmSubmissaoSemAtualizacaoDesde(
                servico.limiteDaConsulta(Instant.now()));

        assertThat(ids).filteredOn(deste::contains).containsExactly(aSeisMeses.getId(), aTresMeses.getId());
    }

    @Test
    void deveTratarOsOutrosQuandoUmProcessoFalhaERegistarAFalhaComOProcessoId() {
        Processo primeiro = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(6)));
        Processo segundo = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(5)));
        Processo terceiro = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 4, 2)));
        doThrow(new IllegalStateException("falha de teste")).when(servico).avaliar(eq(segundo.getId()), any());

        job.executar();

        assertThat(recarregar(primeiro).getEstado()).isEqualTo(EstadoProcesso.ANULADO);
        assertThat(recarregar(segundo).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(recarregar(terceiro).getEstado()).isEqualTo(EstadoProcesso.ANULADO);
        ILoggingEvent erro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(erro.getFormattedMessage()).contains("processoId=" + segundo.getId());
        assertThat(resumo()).contains("job=inatividade-processos").contains("falhas=1");
    }

    @Test
    void naoDeveDeixarIntencoesNemEntregarOEventoQuandoAAnulacaoERevertida() {
        Processo processo = criarProcesso(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 4, 1)));
        List<String> ficheiros = guardarFicheiros(processo, 2);

        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            assertThat(servico.avaliar(processo.getId(), Instant.now())).isEqualTo(DesfechoInatividade.ANULADO);
            estado.setRollbackOnly();
        });

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(intencoesDe(ficheiros)).isEmpty();
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processo.getId().toString()))
                .isEmpty();
        assertThat(ESTADO_VISTO_NO_COMMIT).doesNotContainKey(processo.getId());
    }

    private String resumo() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("job=inatividade-processos"))
                .findFirst().orElseThrow();
    }
}
