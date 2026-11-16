package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Relay SMTP numa porta sem servidor: a criação responde como sempre e a falha fica só no log. */
@SpringBootTest(properties = {"app.security.auth-disabled=false", "app.notificacoes.email.ativo=true",
        "app.notificacoes.email.remetente=saigt@saigt-teste.invalid", "spring.mail.host=127.0.0.1",
        "spring.mail.port=9", "spring.mail.properties.mail.smtp.connectiontimeout=2000",
        "management.endpoint.health.show-components=always"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CopiaEmailRelayIndisponivelIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private EntidadeContactoRepository contactoRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CopiaEmailNotificacaoListener.class);

    private Utilizador master;
    private Entidade entidade;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        int n = SEQUENCIA.incrementAndGet();
        Utilizador novoMaster = new Utilizador("Marta Master", "marta" + n + "@dgt.saigt-teste.invalid");
        novoMaster.setPerfilInterno(PerfilInterno.MASTER);
        master = utilizadorRepository.saveAndFlush(novoMaster);
        entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5213%05d".formatted(n), "Câmara " + n));
        EntidadeContacto principal = new EntidadeContacto(entidade, "geral" + n + "@cm.saigt-teste.invalid", master);
        principal.setPrincipal(true);
        contactoRepository.saveAndFlush(principal);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void comORelayIndisponivelOPostResponde201FicaWarnSemEnderecoEATentativaRegistada() throws Exception {
        String titulo = "Aviso " + UUID.randomUUID();
        MvcResult resultado = mockMvc.perform(post("/api/v1/notificacoes").with(masterJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"ESTATICA","titulo":"%s","texto":"Texto do aviso.",
                                 "destinatarios":[{"entidadeId":%d}]}""".formatted(titulo, entidade.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String notificacaoId = new ObjectMapper().readTree(resultado.getResponse().getContentAsString())
                .path("notificacoes").path(0).path("id").asText();
        assertThat(notificacaoId).isNotBlank();

        ILoggingEvent aviso = esperarLog("Falha no envio da cópia por email");
        assertThat(aviso.getLevel()).isEqualTo(Level.WARN);
        assertThat(aviso.getFormattedMessage()).contains("notificacaoId=" + notificacaoId).doesNotContain("@");
        assertThat(aviso.getThreadName()).startsWith("notif-email-");
        assertThat(aviso.getThrowableProxy()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT canal_email FROM notificacao WHERE id = ?", Boolean.class,
                UUID.fromString(notificacaoId))).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT email_enviado_em IS NOT NULL FROM notificacao WHERE id = ?",
                Boolean.class, UUID.fromString(notificacaoId))).isTrue();
        assertThat(logs()).allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain("@")
                .doesNotContain(titulo).doesNotContain("Texto do aviso"));
    }

    @Test
    void oHealthNaoTemIndicadorDeEmailNemFicaDownPeloRelay() throws Exception {
        String corpo = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getContentAsString();
        JsonNode health = new ObjectMapper().readTree(corpo);

        assertThat(health.path("components").has("mail")).isFalse();
        assertThat(health.path("components").size()).as("componentes visíveis: " + corpo).isPositive();
    }

    private List<ILoggingEvent> logs() {
        synchronized (logs) {
            return new ArrayList<>(logs.list);
        }
    }

    private ILoggingEvent esperarLog(String inicio) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < limite) {
            for (ILoggingEvent evento : logs()) {
                if (evento.getFormattedMessage().startsWith(inicio)) {
                    return evento;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Sem o log «" + inicio + "» em 20 s");
    }

    private RequestPostProcessor masterJwt() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, master.getId().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", PerfilInterno.MASTER.name()));
    }
}
