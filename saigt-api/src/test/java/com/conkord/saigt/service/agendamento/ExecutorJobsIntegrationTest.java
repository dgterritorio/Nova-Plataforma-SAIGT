package com.conkord.saigt.service.agendamento;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.configuration.AgendamentoConfig;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada {@link ExecutorJobs} criado aqui faz de uma instância do saigt-api: todos partilham a mesma tabela `shedlock`. */
@SpringBootTest
@ActiveProfiles("test")
class ExecutorJobsIntegrationTest {

    private static final String JOB = "job-de-teste";
    private static final Duration LOCK_MAXIMO = Duration.ofMinutes(1);
    private static final String MDC_EXECUCAO_JOB = "execucaoJob";
    private static final String MDC_CORRELATION_ID = "apiTraceId";

    @Autowired
    private DataSource dataSource;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExecutorJobs.class);

    @BeforeEach
    void setUp() {
        new JdbcTemplate(dataSource).update("DELETE FROM shedlock");
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logger.setLevel(null);
    }

    @Test
    void deveNaoExecutarERegistarEmDebugQuandoOutraInstanciaTemOLock() {
        ExecutorJobs instanciaA = novaInstancia(Duration.ZERO);
        ExecutorJobs instanciaB = novaInstancia(Duration.ZERO);
        AtomicInteger execucoesB = new AtomicInteger();

        instanciaA.executar(JOB, LOCK_MAXIMO, () -> instanciaB.executar(JOB, LOCK_MAXIMO, execucoesB::incrementAndGet));

        assertThat(execucoesB).hasValue(0);
        assertThat(logs.list).anySatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(evento.getFormattedMessage()).contains("lock ocupado").contains("job=" + JOB);
        });
    }

    @Test
    void deveExecutarDeNovoQuandoAExecucaoAnteriorTerminou() {
        ExecutorJobs instancia = novaInstancia(Duration.ZERO);
        AtomicInteger execucoes = new AtomicInteger();

        instancia.executar(JOB, LOCK_MAXIMO, execucoes::incrementAndGet);
        instancia.executar(JOB, LOCK_MAXIMO, execucoes::incrementAndGet);

        assertThat(execucoes).hasValue(2);
    }

    @Test
    void deveManterOLockDuranteOLockMinimoParaOutraInstanciaNaoRepetirUmJobRapido() {
        ExecutorJobs instanciaA = novaInstancia(Duration.ofMinutes(1));
        ExecutorJobs instanciaB = novaInstancia(Duration.ofMinutes(1));
        AtomicInteger execucoes = new AtomicInteger();

        instanciaA.executar(JOB, LOCK_MAXIMO, execucoes::incrementAndGet);
        instanciaB.executar(JOB, LOCK_MAXIMO, execucoes::incrementAndGet);

        assertThat(execucoes).hasValue(1);
    }

    @Test
    void deveExecutarQuandoPassouOLockMaximoDeUmaInstanciaQueMorreuAMeio() throws InterruptedException {
        LockProvider instanciaMorta = new AgendamentoConfig().lockProvider(dataSource);
        Duration lockMaximoCurto = Duration.ofMillis(500);
        assertThat(instanciaMorta.lock(new LockConfiguration(Instant.now(), JOB, lockMaximoCurto, Duration.ZERO)))
                .isPresent();
        ExecutorJobs outraInstancia = novaInstancia(Duration.ZERO);
        AtomicInteger execucoes = new AtomicInteger();

        outraInstancia.executar(JOB, LOCK_MAXIMO, execucoes::incrementAndGet);
        assertThat(execucoes).hasValue(0);

        Thread.sleep(lockMaximoCurto.plusMillis(300).toMillis());
        outraInstancia.executar(JOB, LOCK_MAXIMO, execucoes::incrementAndGet);
        assertThat(execucoes).hasValue(1);
    }

    @Test
    void deveRegistarInicioEFimComNomeDuracaoResultadoEIdProprioDeCadaExecucaoSemCorrelationId() {
        ExecutorJobs instancia = novaInstancia(Duration.ZERO);

        instancia.executar(JOB, LOCK_MAXIMO, () -> { });
        instancia.executar(JOB, LOCK_MAXIMO, () -> { });

        List<ILoggingEvent> info = logs.list.stream().filter(evento -> evento.getLevel() == Level.INFO).toList();
        assertThat(info).hasSize(4);
        assertThat(info.get(0).getFormattedMessage()).isEqualTo("Job iniciado | job=" + JOB);
        assertThat(info.get(1).getFormattedMessage())
                .matches("Job terminado \\| job=" + JOB + " duracaoMs=\\d+ resultado=OK");
        String execucaoPrimeira = info.get(0).getMDCPropertyMap().get(MDC_EXECUCAO_JOB);
        String execucaoSegunda = info.get(2).getMDCPropertyMap().get(MDC_EXECUCAO_JOB);
        assertThat(execucaoPrimeira).isNotBlank().isEqualTo(info.get(1).getMDCPropertyMap().get(MDC_EXECUCAO_JOB));
        assertThat(execucaoSegunda).isNotBlank().isEqualTo(info.get(3).getMDCPropertyMap().get(MDC_EXECUCAO_JOB))
                .isNotEqualTo(execucaoPrimeira);
        assertThat(info).allSatisfy(evento ->
                assertThat(evento.getMDCPropertyMap()).doesNotContainKey(MDC_CORRELATION_ID));
        assertThat(MDC.get(MDC_EXECUCAO_JOB)).isNull();
    }

    @Test
    void deveRegistarErroComStackTraceELibertarOLockQuandoOJobLancaExcecao() {
        ExecutorJobs instancia = novaInstancia(Duration.ZERO);
        AtomicInteger execucoes = new AtomicInteger();

        instancia.executar(JOB, LOCK_MAXIMO, () -> {
            throw new IllegalStateException("falha simulada");
        });
        instancia.executar(JOB, LOCK_MAXIMO, execucoes::incrementAndGet);

        assertThat(logs.list).anySatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
            assertThat(evento.getFormattedMessage()).matches("Job terminado \\| job=" + JOB + " duracaoMs=\\d+ resultado=ERRO");
            assertThat(evento.getThrowableProxy().getMessage()).isEqualTo("falha simulada");
            assertThat(evento.getMDCPropertyMap().get(MDC_EXECUCAO_JOB)).isNotBlank();
        });
        assertThat(execucoes).hasValue(1);
    }

    private ExecutorJobs novaInstancia(Duration lockMinimo) {
        return new ExecutorJobs(new AgendamentoConfig().lockProvider(dataSource), lockMinimo);
    }
}
