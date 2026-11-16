package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Servidor SMTP de teste: o Mailpit, a mesma imagem e versão do `smtp-mock` do docker-compose, que guarda as
 * mensagens e as mostra pela API REST. Só endereços `.invalid`.
 */
@Tag("docker")
@SpringBootTest(properties = {"app.security.auth-disabled=false", "app.notificacoes.email.ativo=true",
        "app.notificacoes.email.remetente=saigt@saigt-teste.invalid"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CopiaEmailNotificacaoSmtpIntegrationTest {

    private static final int PORTA_SMTP = 1025;
    private static final int PORTA_API = 8025;
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.32.0")
            .withExposedPorts(PORTA_SMTP, PORTA_API)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(PORTA_API)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void propriedadesSmtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(PORTA_SMTP));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private EntidadeContactoRepository contactoRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private ApplicationEventPublisher publicador;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CopiaEmailNotificacaoListener.class);

    private Utilizador master;
    private Entidade entidade;
    private String contacto;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        int n = SEQUENCIA.incrementAndGet();
        Utilizador novoMaster = new Utilizador("Marta Master", "marta" + n + "@dgt.saigt-teste.invalid");
        novoMaster.setPerfilInterno(PerfilInterno.MASTER);
        master = utilizadorRepository.saveAndFlush(novoMaster);
        entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5212%05d".formatted(n), "Câmara " + n));
        contacto = "geral" + n + "@cm.saigt-teste.invalid";
        EntidadeContacto principal = new EntidadeContacto(entidade, contacto, master);
        principal.setPrincipal(true);
        contactoRepository.saveAndFlush(principal);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void noticiaParaEntidadeChegaAoServidorSmtpComOAssuntoEOTextoEMarcaALinhaForaDaThreadDoPedido()
            throws Exception {
        String titulo = "Manutenção programada " + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/notificacoes").with(masterJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"ESTATICA","titulo":"%s","texto":"A plataforma pára no sábado.",
                                 "destinatarios":[{"entidadeId":%d}]}""".formatted(titulo, entidade.getId())))
                .andExpect(status().isCreated());

        JsonNode resumo = esperar(() -> mensagemPara(contacto), "email no Mailpit");
        assertThat(resumo.path("Subject").asText()).isEqualTo(titulo);
        assertThat(resumo.path("From").path("Address").asText()).isEqualTo("saigt@saigt-teste.invalid");
        JsonNode completa = getJson("/api/v1/message/" + resumo.path("ID").asText());
        assertThat(completa.path("Text").asText()).contains("A plataforma pára no sábado.");

        Map<String, Object> linha = esperar(() -> {
            Map<String, Object> l = jdbcTemplate.queryForMap(
                    "SELECT canal_email, email_enviado_em FROM notificacao WHERE titulo = ?", titulo);
            return Boolean.TRUE.equals(l.get("canal_email")) ? l : null;
        }, "canal_email = true");
        assertThat(linha.get("email_enviado_em")).isNotNull();

        ILoggingEvent enviado = esperar(() -> logs().stream()
                .filter(e -> e.getFormattedMessage().startsWith("Cópia por email enviada")).findFirst().orElse(null),
                "log de envio");
        assertThat(enviado.getThreadName()).startsWith("notif-email-");
        assertThat(logs()).allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain("@")
                .doesNotContain(titulo).doesNotContain("A plataforma pára"));
    }

    @Test
    void criacaoQueReverteNaoEnviaNada() throws Exception {
        String titulo = "Revertida " + UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            Notificacao linha = new Notificacao(TipoNotificacao.ESTATICA, titulo, "Não deve sair.", master);
            linha.setEntidade(entidade);
            Notificacao gravada = notificacaoRepository.saveAndFlush(linha);
            publicador.publishEvent(new NotificacaoCriadaEvento(List.of(gravada.getId()), null, false));
            estado.setRollbackOnly();
        });

        Thread.sleep(2_000);
        assertThat(mensagemPara(contacto)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notificacao WHERE titulo = ?", Long.class,
                titulo)).isZero();
        assertThat(logs()).noneMatch(e -> e.getFormattedMessage().startsWith("Cópia por email"));
    }

    private List<ILoggingEvent> logs() {
        synchronized (logs) {
            return new ArrayList<>(logs.list);
        }
    }

    private RequestPostProcessor masterJwt() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, master.getId().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", PerfilInterno.MASTER.name()));
    }

    private static JsonNode mensagemPara(String endereco) throws Exception {
        for (JsonNode mensagem : getJson("/api/v1/messages").path("messages")) {
            for (JsonNode para : mensagem.path("To")) {
                if (endereco.equalsIgnoreCase(para.path("Address").asText())) {
                    return mensagem;
                }
            }
        }
        return null;
    }

    private static JsonNode getJson(String caminho) throws Exception {
        HttpResponse<String> resposta = HTTP.send(HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost()
                + ":" + MAILPIT.getMappedPort(PORTA_API) + caminho)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resposta.statusCode()).isEqualTo(200);
        return JSON.readTree(resposta.body());
    }

    @FunctionalInterface
    private interface Sonda<T> {
        T obter() throws Exception;
    }

    private static <T> T esperar(Sonda<T> sonda, String oQue) throws Exception {
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < limite) {
            T valor = sonda.obter();
            if (valor != null) {
                return valor;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Sem " + oQue + " em 15 s");
    }
}
