package com.conkord.saigt.service.instrumento;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
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
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
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

/** Seleção das Dinâmicas na BD e ciclo completo do job, com o listener `AFTER_COMMIT` de um consumidor do evento. */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class CaducidadeDinamicaJobIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    /** O que um listener `AFTER_COMMIT` vê na BD quando recebe o evento. */
    static final Map<UUID, String> ESTADO_VISTO_NO_COMMIT = new ConcurrentHashMap<>();

    @TestConfiguration
    static class ListenerAposCommit {

        @Bean
        ConsumidorDoEvento consumidorDoEvento(JdbcTemplate jdbcTemplate) {
            return new ConsumidorDoEvento(jdbcTemplate);
        }
    }

    static class ConsumidorDoEvento {

        private final JdbcTemplate jdbcTemplate;

        ConsumidorDoEvento(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void aoCommit(AlertaCaducidadeDinamicaEvento evento) {
            ESTADO_VISTO_NO_COMMIT.put(evento.dinamicaId(), jdbcTemplate.queryForObject(
                    "SELECT estado FROM dinamica WHERE id = ?", String.class, evento.dinamicaId()));
        }
    }

    @Autowired
    private CaducidadeDinamicaJob job;

    @SpyBean
    private CaducidadeDinamicaService servico;

    @Autowired
    private ApplicationEvents eventos;

    @Autowired
    private Environment ambiente;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

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
    private final Logger logger = (Logger) LoggerFactory.getLogger(CaducidadeDinamicaJob.class);

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
                new TipoInstrumento("CADUC" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("CADUC-D" + n, "Alteração"));
        dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("CADUC-P" + n, "Procedimento " + n));
        procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5072%05d".formatted(n), "Câmara " + n));
        autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor " + n, "autor" + n + "@cm-caduc.pt", "INCM"));
        instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, String.valueOf(n), "2026/CADUC/" + n, autor, autor));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("Europe/Lisbon"));
    }

    private Dinamica criarDinamica(EstadoDinamica estado, LocalDate dataCaducidade, List<String> alertas) {
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor);
        dinamica.setEstado(estado);
        dinamica.setDataCaducidade(dataCaducidade);
        dinamica.setAlertasEnviados(alertas);
        return dinamicaRepository.saveAndFlush(dinamica);
    }

    private Dinamica recarregar(Dinamica dinamica) {
        return dinamicaRepository.findById(dinamica.getId()).orElseThrow();
    }

    private List<AlertaCaducidadeDinamicaEvento> eventosDe(Dinamica... dinamicas) {
        Set<UUID> ids = Set.of(java.util.Arrays.stream(dinamicas).map(Dinamica::getId).toArray(UUID[]::new));
        return eventos.stream(AlertaCaducidadeDinamicaEvento.class).filter(e -> ids.contains(e.dinamicaId())).toList();
    }

    @Test
    void deveRegistarLimiaresECaducarConformeADataDeCadaDinamica() {
        LocalDate hoje = hoje();
        Dinamica aUmMes = criarDinamica(EstadoDinamica.EM_CURSO, hoje.plusMonths(1), null);
        Dinamica alemDeUmMes = criarDinamica(EstadoDinamica.EM_CURSO, hoje.plusMonths(1).plusDays(10), null);
        Dinamica aQuinzeDias = criarDinamica(EstadoDinamica.EM_CURSO, hoje.plusDays(15), List.of("UM_MES"));
        Dinamica naData = criarDinamica(EstadoDinamica.EM_CURSO, hoje, List.of("UM_MES", "QUINZE_DIAS"));
        Dinamica terminada = criarDinamica(EstadoDinamica.TERMINADA, hoje.minusDays(5), null);
        Dinamica semData = criarDinamica(EstadoDinamica.EM_CURSO, null, null);
        Processo processo = processoRepository.saveAndFlush(new Processo(naData, procedimentoConfig, autor, entidade));

        job.executar();

        Dinamica umMes = recarregar(aUmMes);
        assertThat(umMes.getAlertasEnviados()).containsExactly("UM_MES");
        assertThat(umMes.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(eventosDe(aUmMes)).containsExactly(
                new AlertaCaducidadeDinamicaEvento(aUmMes.getId(), LimiarCaducidade.UM_MES, hoje.plusMonths(1)));

        assertThat(recarregar(alemDeUmMes).getAlertasEnviados()).isNull();
        assertThat(eventosDe(alemDeUmMes)).isEmpty();

        Dinamica quinze = recarregar(aQuinzeDias);
        assertThat(quinze.getAlertasEnviados()).containsExactly("UM_MES", "QUINZE_DIAS");
        assertThat(quinze.getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(eventosDe(aQuinzeDias)).extracting(AlertaCaducidadeDinamicaEvento::limiar)
                .containsExactly(LimiarCaducidade.QUINZE_DIAS);

        Dinamica caducada = recarregar(naData);
        assertThat(caducada.getEstado()).isEqualTo(EstadoDinamica.CADUCADA);
        assertThat(caducada.getAlertasEnviados()).containsExactly("UM_MES", "QUINZE_DIAS", "DATA");
        assertThat(eventosDe(naData)).extracting(AlertaCaducidadeDinamicaEvento::limiar)
                .containsExactly(LimiarCaducidade.DATA);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("DINAMICA", naData.getId().toString()))
                .extracting("acao", "estadoAntes", "estadoDepois", "utilizadorId")
                .containsExactly(tuple("CADUCIDADE", "EM_CURSO", "CADUCADA", null));
        assertThat(processoRepository.findById(processo.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        assertThat(recarregar(terminada).getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(recarregar(terminada).getAlertasEnviados()).isNull();
        assertThat(eventosDe(terminada, semData)).isEmpty();
        assertThat(recarregar(semData).getAlertasEnviados()).isNull();
    }

    @Test
    void naoDeveAlterarNemPublicarNadaNumaSegundaExecucaoNoMesmoDia() {
        LocalDate hoje = hoje();
        Dinamica aUmMes = criarDinamica(EstadoDinamica.EM_CURSO, hoje.plusMonths(1), null);
        Dinamica naData = criarDinamica(EstadoDinamica.EM_CURSO, hoje, null);

        job.executar();
        long eventosDaPrimeira = eventosDe(aUmMes, naData).size();
        job.executar();

        assertThat(eventosDaPrimeira).isEqualTo(2);
        assertThat(eventosDe(aUmMes, naData)).hasSize(2);
        assertThat(recarregar(aUmMes).getAlertasEnviados()).containsExactly("UM_MES");
        assertThat(recarregar(naData).getAlertasEnviados()).containsExactly("UM_MES", "QUINZE_DIAS", "DATA");
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("DINAMICA", naData.getId().toString())).hasSize(1);
    }

    @Test
    void deveTratarAsOutrasQuandoUmaDinamicaFalhaERegistarAFalhaComODinamicaId() {
        LocalDate hoje = hoje();
        Dinamica primeira = criarDinamica(EstadoDinamica.EM_CURSO, hoje.minusDays(3), null);
        Dinamica segunda = criarDinamica(EstadoDinamica.EM_CURSO, hoje.minusDays(2), null);
        Dinamica terceira = criarDinamica(EstadoDinamica.EM_CURSO, hoje.minusDays(1), null);
        doThrow(new IllegalStateException("falha de teste")).when(servico).avaliar(eq(segunda.getId()), any());

        job.executar();

        assertThat(recarregar(primeira).getEstado()).isEqualTo(EstadoDinamica.CADUCADA);
        assertThat(recarregar(segunda).getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(recarregar(terceira).getEstado()).isEqualTo(EstadoDinamica.CADUCADA);
        ILoggingEvent linhaErro = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(linhaErro.getFormattedMessage()).contains("dinamicaId=" + segunda.getId());
        assertThat(resumo()).contains("falhas=1");
    }

    @Test
    void deveEntregarOEventoAoListenerAfterCommitSoDepoisDeCaducarADinamicaNaBd() {
        Dinamica naData = criarDinamica(EstadoDinamica.EM_CURSO, hoje(), null);
        Dinamica aUmMes = criarDinamica(EstadoDinamica.EM_CURSO, hoje().plusMonths(1), null);

        job.executar();

        assertThat(ESTADO_VISTO_NO_COMMIT.get(naData.getId())).isEqualTo("CADUCADA");
        assertThat(ESTADO_VISTO_NO_COMMIT.get(aUmMes.getId())).isEqualTo("EM_CURSO");
    }

    @Test
    void deveAgendarOJobPorOmissaoAsCincoDaManhaTodosOsDiasComLockDeTrintaMinutos() {
        assertThat(ambiente.getProperty("app.dinamicas.caducidade.cron")).isEqualTo("0 0 5 * * *");
        assertThat(ambiente.getProperty("app.dinamicas.caducidade.lock-maximo")).isEqualTo("30m");
    }

    private String resumo() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("job=caducidade-dinamicas"))
                .findFirst().orElseThrow();
    }
}
