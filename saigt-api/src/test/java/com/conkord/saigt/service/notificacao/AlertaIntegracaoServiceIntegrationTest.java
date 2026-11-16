package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.core.CircuitoAbertoEvento;
import com.conkord.saigt.integracao.core.SistemaExterno;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class AlertaIntegracaoServiceIntegrationTest {

    private static final Instant MOMENTO = Instant.parse("2026-11-12T15:30:00Z");
    private static final String TITULO_INCM = "Integração indisponível: INCM";

    @Autowired
    private AlertaIntegracaoService servico;
    @Autowired
    private ApplicationEventPublisher publicador;
    @Autowired
    private ApplicationEvents eventos;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertaIntegracaoService.class);

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notificacao WHERE tipo = 'ALERTA' AND titulo LIKE 'Integração indisponível:%'");
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        circuitBreakerRegistry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO).reset();
    }

    private static CircuitoAbertoEvento evento(SistemaExterno sistema, String circuito) {
        return new CircuitoAbertoEvento(sistema, circuito, MOMENTO);
    }

    private List<Map<String, Object>> alertas(String titulo) {
        return jdbcTemplate.queryForList("SELECT * FROM notificacao WHERE tipo = 'ALERTA' AND titulo = ?", titulo);
    }

    @Test
    void deveCriarUmaLinhaAlertaDaPlataformaSoParaOsMasterSemContextoNemEmail() {
        servico.criarAlertaCircuitoAberto(evento(SistemaExterno.INCM, ClienteIncm.CIRCUITO_SUBMISSAO));

        assertThat(alertas(TITULO_INCM)).singleElement().satisfies(linha -> {
            assertThat(linha.get("perfil_destino")).isEqualTo("MASTER");
            assertThat(linha.get("estado")).isEqualTo("ATIVA");
            assertThat(linha.get("canal_email")).isEqualTo(false);
            assertThat(linha.get("email_enviado_em")).isNull();
            assertThat(linha.get("criado_por")).isNull();
            assertThat(linha.get("processo_id")).isNull();
            assertThat(linha.get("utilizador_id")).isNull();
            assertThat(linha.get("entidade_id")).isNull();
            assertThat(linha.get("lote_id")).isNull();
            assertThat(linha.get("texto")).isEqualTo("A comunicação da SAIGT com INCM foi suspensa a 12/11/2026 "
                    + "15:30 depois de falhas sucessivas (circuito incm-submissao). As chamadas voltam a ser "
                    + "tentadas automaticamente.");
        });
        assertThat(eventos.stream(NotificacaoCriadaEvento.class)).isEmpty();
        assertThat(logs.list).singleElement().satisfies(linha -> {
            assertThat(linha.getLevel()).isEqualTo(Level.INFO);
            assertThat(linha.getFormattedMessage()).matches(
                    "Alerta de integração criado \\| sistema=INCM circuito=incm-submissao notificacaoId=[0-9a-f-]{36}");
        });
    }

    @ParameterizedTest
    @CsvSource({"SNIT, snit-consulta, SNIT", "SNIT, snit-registo, SNIT", "PCGT, pcgt, PCGT",
            "GEOCONFORM, geoconform, GeoConform"})
    void oTituloNomeiaOSistemaDoCircuito(SistemaExterno sistema, String circuito, String nome) {
        servico.criarAlertaCircuitoAberto(evento(sistema, circuito));

        assertThat(alertas("Integração indisponível: " + nome)).singleElement()
                .satisfies(linha -> assertThat((String) linha.get("texto"))
                        .startsWith("A comunicação da SAIGT com " + nome + " foi suspensa")
                        .contains("(circuito " + circuito + ")"));
    }

    @Test
    void naoCriaOutraLinhaEnquantoHouverUmAlertaAtivoDoMesmoSistema() {
        servico.criarAlertaCircuitoAberto(evento(SistemaExterno.INCM, ClienteIncm.CIRCUITO_SUBMISSAO));
        logs.list.clear();

        servico.criarAlertaCircuitoAberto(evento(SistemaExterno.INCM, ClienteIncm.CIRCUITO_AUTENTICACAO));

        assertThat(alertas(TITULO_INCM)).hasSize(1);
        assertThat(logs.list).singleElement().satisfies(linha -> {
            assertThat(linha.getLevel()).isEqualTo(Level.INFO);
            assertThat(linha.getFormattedMessage())
                    .isEqualTo("Alerta de integração já ativo | sistema=INCM circuito=incm-autenticacao");
        });
    }

    @Test
    void criaUmaNovaLinhaDepoisDeOAlertaAnteriorSerLidoOuDescartado() {
        for (String estado : List.of("LIDA", "REMOVIDA")) {
            servico.criarAlertaCircuitoAberto(evento(SistemaExterno.INCM, ClienteIncm.CIRCUITO_SUBMISSAO));
            jdbcTemplate.update("UPDATE notificacao SET estado = ? WHERE tipo = 'ALERTA' AND titulo = ? "
                    + "AND estado = 'ATIVA'", estado, TITULO_INCM);
        }
        servico.criarAlertaCircuitoAberto(evento(SistemaExterno.INCM, ClienteIncm.CIRCUITO_SUBMISSAO));

        assertThat(alertas(TITULO_INCM)).extracting(linha -> linha.get("estado"))
                .containsExactlyInAnyOrder("LIDA", "REMOVIDA", "ATIVA");
    }

    @Test
    void oAlertaFicaGravadoQuandoATransacaoDeQuemChamouReverte() {
        String marcaExterior = "Integração indisponível: escrita da transação exterior";
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            jdbcTemplate.update("INSERT INTO notificacao (id, tipo, titulo, texto, estado, canal_email, criado_em, "
                    + "atualizado_em) VALUES (?, 'ALERTA', ?, 'x', 'ATIVA', false, CURRENT_TIMESTAMP, "
                    + "CURRENT_TIMESTAMP)", UUID.randomUUID(), marcaExterior);
            publicador.publishEvent(evento(SistemaExterno.INCM, ClienteIncm.CIRCUITO_SUBMISSAO));
            estado.setRollbackOnly();
        });

        assertThat(alertas(marcaExterior)).isEmpty();
        assertThat(alertas(TITULO_INCM)).hasSize(1);
    }

    @Test
    void aAberturaDoCircuitoCriaUmSoAlertaMesmoComReaberturaDepoisDoHalfOpen() {
        CircuitBreaker circuito = circuitBreakerRegistry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO);

        circuito.transitionToOpenState();
        circuito.transitionToHalfOpenState();
        circuito.transitionToOpenState();

        assertThat(eventos.stream(CircuitoAbertoEvento.class)).singleElement()
                .satisfies(evento -> assertThat(evento.sistema()).isEqualTo(SistemaExterno.INCM));
        assertThat(alertas(TITULO_INCM)).singleElement()
                .satisfies(linha -> assertThat(linha.get("perfil_destino")).isEqualTo("MASTER"));
    }
}
