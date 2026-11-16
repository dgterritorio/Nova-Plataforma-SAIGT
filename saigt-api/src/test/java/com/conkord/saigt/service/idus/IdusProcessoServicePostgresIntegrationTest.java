package com.conkord.saigt.service.idus;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.exception.IdusInvalidoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistorico;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistoricoRepository;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.SegmentoIdus;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.RejeicaoProcessoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cria e atualiza o IDUS num PostgreSQL real, com os catálogos que o Liquibase semeia: o lock do contador e as
 * restrições da BD não existem em H2. O Processo é gravado e o IDUS criado na mesma transação, como em
 * `ProcessoService`.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("docker")
class IdusProcessoServicePostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CONTADOR_ORDEM = "ORDEM_DINAMICA_PROVISORIA";
    private static final String CONTADOR_NUMERO_PROCESSO = "NUMERO_PROCESSO";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(1000);

    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:15-alpine")
            .withEnv("POSTGRES_PASSWORD", "teste")
            .withEnv("POSTGRES_DB", "saigt")
            .withCopyToContainer(Transferable.of("CREATE ROLE saigt_app LOGIN PASSWORD 'teste';"),
                    "/docker-entrypoint-initdb.d/01-saigt-app.sql")
            .withExposedPorts(PORTA_POSTGRES)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\n", 2));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PORTA_POSTGRES) + "/saigt";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "saigt_app");
        registry.add("spring.datasource.password", () -> "teste");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.url", () -> url);
        registry.add("spring.liquibase.user", () -> "postgres");
        registry.add("spring.liquibase.password", () -> "teste");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private IdusProcessoService idusProcessoService;

    @Autowired
    private RejeicaoProcessoService rejeicaoProcessoService;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

    @Autowired
    private IdusProcessoHistoricoRepository historicoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    private ListAppender<ILoggingEvent> registosDoServico;

    @Autowired
    private EntidadeRepository entidadeRepository;

    private record Base(UUID utilizadorId, UUID instrumentoId, String nipc) {
    }

    @BeforeEach
    void capturarLogDoServico() {
        registosDoServico = new ListAppender<>();
        registosDoServico.start();
        logDoServico().addAppender(registosDoServico);
    }

    @AfterEach
    void libertarLogDoServico() {
        logDoServico().detachAppender(registosDoServico);
    }

    // Corre primeiro: o contador de NUMERO_PROCESSO ainda não passou do 28, por isso o primeiro Processo recebe o 00029.
    @Test
    @Order(1)
    void deveDarAoIdIgtProvisorioDeUmaElaboracaoDePpONumeroDoPrimeiroProcesso() {
        definirContador(CONTADOR_NUMERO_PROCESSO, 28);
        Base base = novoInstrumento("PP", null, "1111");
        UUID dinamica = novaDinamica(base, "ELABORACAO", 1);

        UUID decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);
        UUID abertura = gravarProcesso(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 1);

        assertThat(processoRepository.findById(decisao).orElseThrow().getNumeroProcesso()).isEqualTo("00029");
        IdusProcesso daDecisao = idusDe(decisao);
        IdusProcesso daAbertura = idusDe(abertura);
        assertThat(daDecisao.getIdIgt()).isEqualTo("900029");
        assertThat(daAbertura.getIdIgt()).isEqualTo("900029");
        assertThat(daDecisao.isIdIgtDefinitivo()).isFalse();
        assertThat(daAbertura.isIdIgtDefinitivo()).isFalse();
        assertThat(daDecisao.getIdus()).matches("^01\\.1111\\.06\\.900029\\.01\\.9\\d{4}\\.01\\.029\\.00$");
        assertThat(daAbertura.getIdus()).matches("^01\\.1111\\.06\\.900029\\.01\\.9\\d{4}\\.01\\.033\\.00$");
    }

    @Test
    void deveCriarOIdusEOPrimeiroRegistoDoHistoricoNaDecisaoDeInicioDeUmaAlteracaoDePdm() {
        Base base = novoInstrumento("PDM", "123", "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);

        UUID decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);

        IdusProcesso idus = idusDe(decisao);
        assertThat(idus.getIdus()).matches("^01\\.1111\\.01\\.000123\\.02\\.9\\d{4}\\.01\\.031\\.00$");
        assertThat(idus.getSistemaOrigem()).isEqualTo("01");
        assertThat(idus.getIdIgt()).isEqualTo("000123");
        assertThat(idus.isIdIgtDefinitivo()).isTrue();
        assertThat(idus.getOrdemDinamica()).matches("^9\\d{4}$");
        assertThat(idus.isOrdemDefinitiva()).isFalse();
        assertThat(idus.getTentativa()).isEqualTo((short) 1);
        assertThat(idus.getProcedimento()).isEqualTo("031");
        assertThat(idus.getSubmissao()).isZero();
        assertThat(idus.getPubIncm()).isNull();
        assertThat(idus.getAtualizadoEm()).isNotNull();

        List<IdusProcessoHistorico> historico = historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao);
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).getSegmento()).isEqualTo(SegmentoIdus.CRIACAO);
        assertThat(historico.get(0).getEvento()).isEqualTo(EventoIdus.PROCESSO_CRIADO);
        assertThat(historico.get(0).getIdusAnterior()).isNull();
        assertThat(historico.get(0).getIdusNovo()).isEqualTo(idus.getIdus());
        assertThat(historico.get(0).getRegistadoEm()).isNotNull();
        assertThat(UUID.fromString(historico.get(0).getCorrelationId())).isNotNull();
    }

    @Test
    void deveGravarNoHistoricoOCorrelationIdDoPedido() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);
        MDC.put("apiTraceId", "trace-do-pedido-1");
        UUID decisao;
        try {
            decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);
        } finally {
            MDC.remove("apiTraceId");
        }

        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao))
                .extracting(IdusProcessoHistorico::getCorrelationId)
                .containsExactly("trace-do-pedido-1");
    }

    @Test
    void deveDarAAberturaDaDiscussaoPublicaOMesmoIdIgtEAMesmaOrdemSemMexerNoContador() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);
        int contadorDepoisDaDecisao = valorDoContador(CONTADOR_ORDEM);

        UUID abertura = gravarProcesso(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 1);

        IdusProcesso daDecisao = idusDe(decisao);
        IdusProcesso daAbertura = idusDe(abertura);
        assertThat(daAbertura.getIdIgt()).isEqualTo(daDecisao.getIdIgt());
        assertThat(daAbertura.isIdIgtDefinitivo()).isEqualTo(daDecisao.isIdIgtDefinitivo());
        assertThat(daAbertura.getOrdemDinamica()).isEqualTo(daDecisao.getOrdemDinamica());
        assertThat(daAbertura.isOrdemDefinitiva()).isFalse();
        assertThat(daAbertura.getProcedimento()).isEqualTo("034");
        assertThat(daAbertura.getIdus()).isEqualTo(daDecisao.getIdus().replace(".031.00", ".034.00"));
        assertThat(valorDoContador(CONTADOR_ORDEM)).isEqualTo(contadorDepoisDaDecisao);
    }

    @Test
    void deveSomarCemAoProcedimentoPorCadaOcorrenciaAnterior() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);

        UUID primeira = gravarProcesso(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 1);
        UUID segunda = gravarProcesso(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 2);

        assertThat(idusDe(primeira).getProcedimento()).isEqualTo("034");
        assertThat(idusDe(segunda).getProcedimento()).isEqualTo("134");
        assertThat(idusDe(segunda).getIdus()).endsWith(".134.00");
    }

    @Test
    void deveAceitarAOcorrenciaDezDeUmProcedimentoSessentaEFalharNaOnze() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "REVISAO", 1);

        UUID decima = gravarProcesso(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 10);

        assertThat(idusDe(decima).getProcedimento()).isEqualTo("960");
        assertThatThrownBy(() -> gravarProcesso(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 11))
                .isInstanceOf(IdusInvalidoException.class)
                .hasMessageContaining("Segmento Procedimento");
        assertThat(contarProcessos(dinamica)).isEqualTo(1);
    }

    @Test
    void deveUsarOIdIgtProvisorioQuandoOInstrumentoAindaNaoTemIdNoSnit() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);

        UUID decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);

        IdusProcesso idus = idusDe(decisao);
        String numero = processoRepository.findById(decisao).orElseThrow().getNumeroProcesso();
        assertThat(idus.getIdIgt()).isEqualTo("9" + numero);
        assertThat(idus.isIdIgtDefinitivo()).isFalse();
    }

    @Test
    void deveManterAOrdemDaDinamicaQuandoATentativaMuda() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID daTentativaUm = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);
        jdbcTemplate.update("UPDATE dinamica SET tentativa = 2 WHERE id = ?", dinamica);

        UUID daTentativaDois = gravarProcesso(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 1);

        IdusProcesso idus = idusDe(daTentativaDois);
        assertThat(idus.getTentativa()).isEqualTo((short) 2);
        assertThat(idus.getOrdemDinamica()).isEqualTo(idusDe(daTentativaUm).getOrdemDinamica());
        assertThat(idus.getIdus()).endsWith("." + idus.getOrdemDinamica() + ".02.034.00");
    }

    @Test
    void deveDarANovaDecisaoDeInicioDaTentativaDoisOMesmoIdIgtEAMesmaOrdemComATentativa02() {
        Base base = novoInstrumento("PDM", "337", "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID daTentativaUm = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);
        jdbcTemplate.update("UPDATE dinamica SET tentativa = 2 WHERE id = ?", dinamica);

        UUID daTentativaDois = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);

        IdusProcesso primeiro = idusDe(daTentativaUm);
        IdusProcesso novo = idusDe(daTentativaDois);
        assertThat(processoRepository.findById(daTentativaDois).orElseThrow().getTentativa()).isEqualTo(2);
        assertThat(processoRepository.findById(daTentativaUm).orElseThrow().getTentativa()).isEqualTo(1);
        assertThat(novo.getTentativa()).isEqualTo((short) 2);
        assertThat(novo.getIdIgt()).isEqualTo(primeiro.getIdIgt()).isEqualTo("000337");
        assertThat(novo.getOrdemDinamica()).isEqualTo(primeiro.getOrdemDinamica());
        assertThat(novo.getProcedimento()).isEqualTo(primeiro.getProcedimento()).isEqualTo("031");
        assertThat(novo.getIdus()).isEqualTo(primeiro.getIdus().replace("." + primeiro.getOrdemDinamica() + ".01.",
                "." + primeiro.getOrdemDinamica() + ".02."));
        assertThat(idusDe(daTentativaUm).getIdus()).isEqualTo(primeiro.getIdus());
    }

    @Test
    void deveRecusarNaBaseOutroProcessoComAMesmaOcorrenciaDoProcedimentoNaMesmaTentativa() {
        Base base = novoInstrumento("PDM", "338", "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 2);
        gravarProcessoSemIdus(base, dinamica, "DECISAO_INICIO", 1);

        assertThatThrownBy(() -> gravarProcessoSemIdus(base, dinamica, "DECISAO_INICIO", 1))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("uq_processo_ocorrencia");
        assertThat(contarProcessos(dinamica)).isEqualTo(1);
    }

    @Test
    void deveComecarOContadorEm90001QuandoNaoExisteEDarAOrdemSeguinteACadaDinamica() {
        apagarContador(CONTADOR_ORDEM);
        Base base = novoInstrumento("PDM", null, "1111");
        UUID primeiraDinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID segundaDinamica = novaDinamica(base, "ALTERACAO", 1);

        UUID daPrimeira = gravarProcesso(base, primeiraDinamica, "DECISAO_INICIO", 1);
        UUID daSegunda = gravarProcesso(base, segundaDinamica, "DECISAO_INICIO", 1);

        assertThat(idusDe(daPrimeira).getOrdemDinamica()).isEqualTo("90001");
        assertThat(idusDe(daSegunda).getOrdemDinamica()).isEqualTo("90002");
        assertThat(valorDoContador(CONTADOR_ORDEM)).isEqualTo(2);
    }

    @Test
    void deveDarAMesmaOrdemAProcessosDaMesmaDinamicaGravadosEmSimultaneo() throws Exception {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID primeiro = gravarProcessoSemIdus(base, dinamica, "DECISAO_INICIO", 1);
        UUID segundo = gravarProcessoSemIdus(base, dinamica, "ABERTURA_DISCUSSAO_PUBLICA", 1);

        criarIdusEmSimultaneo(primeiro, segundo);

        assertThat(idusDe(segundo).getOrdemDinamica()).isEqualTo(idusDe(primeiro).getOrdemDinamica());
        assertThat(idusDe(segundo).getIdIgt()).isEqualTo(idusDe(primeiro).getIdIgt());
    }

    @Test
    void deveDarOrdensDiferentesADinamicasDiferentesGravadasEmSimultaneo() throws Exception {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID primeiraDinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID segundaDinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID primeiro = gravarProcessoSemIdus(base, primeiraDinamica, "DECISAO_INICIO", 1);
        UUID segundo = gravarProcessoSemIdus(base, segundaDinamica, "DECISAO_INICIO", 1);

        criarIdusEmSimultaneo(primeiro, segundo);

        assertThat(idusDe(segundo).getOrdemDinamica()).isNotEqualTo(idusDe(primeiro).getOrdemDinamica());
    }

    @Test
    void deveDarODicoZeroQuandoOInstrumentoTemMaisDeUmConcelho() {
        Base base = novoInstrumento("PDM", null, "1111", "1112");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);

        UUID decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);

        assertThat(idusDe(decisao).getIdus()).startsWith("01.0000.01.");
    }

    @Test
    void deveGravarOProcessoSemIdusEComAvisoQuandoOInstrumentoNaoTemConcelhos() {
        Base base = novoInstrumento("PDM", null);
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);

        UUID decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);

        assertThat(processoRepository.existsById(decisao)).isTrue();
        assertSemIdus(decisao);
        assertThat(avisos()).anySatisfy(aviso -> assertThat(aviso)
                .contains(decisao.toString()).contains("DICO"));
    }

    @Test
    void deveGravarOProcessoSemIdusEComAvisoQuandoADinamicaNaoTemCodigoNoIdus() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "SUSPENSAO", 1);

        UUID abertura = gravarProcesso(base, dinamica, "PUBLICACAO_SUSPENSAO", 1);

        assertThat(processoRepository.existsById(abertura)).isTrue();
        assertSemIdus(abertura);
        assertThat(avisos()).anySatisfy(aviso -> assertThat(aviso)
                .contains(abertura.toString()).contains("Dinâmica").contains("Procedimento"));
    }

    @Test
    void naoDeveCriarIdusNemAvisoParaOProcessoDeUmPrograma() {
        Base base = novoInstrumento("PROT", null, "1111");
        UUID dinamica = novaDinamica(base, "ELABORACAO", 1);

        UUID decisao = gravarProcesso(base, dinamica, "DECISAO_INICIO", 1);

        assertThat(processoRepository.existsById(decisao)).isTrue();
        assertSemIdus(decisao);
        assertThat(avisos()).noneSatisfy(aviso -> assertThat(aviso).contains(decisao.toString()));
    }

    @Test
    void deveFalharENaoGravarOProcessoQuandoOContadorDaOrdemEsgota() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);
        int contadorAnterior = valorDoContador(CONTADOR_ORDEM);
        definirContador(CONTADOR_ORDEM, 9999);

        try {
            assertThatThrownBy(() -> gravarProcesso(base, dinamica, "DECISAO_INICIO", 1))
                    .isInstanceOf(IdusInvalidoException.class)
                    .hasMessageContaining("Segmento Ordem_Dinâmica");

            assertThat(contarProcessos(dinamica)).isZero();
            assertThat(valorDoContador(CONTADOR_ORDEM)).isEqualTo(9999);
        } finally {
            definirContador(CONTADOR_ORDEM, contadorAnterior);
        }
    }

    @Test
    void deveFalharENaoGravarOProcessoQuandoATentativaTemTresAlgarismos() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 100);

        assertThatThrownBy(() -> gravarProcesso(base, dinamica, "DECISAO_INICIO", 1))
                .isInstanceOf(IdusInvalidoException.class)
                .hasMessageContaining("Segmento Tentativa");

        assertThat(contarProcessos(dinamica)).isZero();
    }

    @Test
    void deveFalharENaoGravarOProcessoQuandoOIdDoSnitTemMaisDeCincoAlgarismos() {
        Base base = novoInstrumento("PDM", "123456", "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);

        assertThatThrownBy(() -> gravarProcesso(base, dinamica, "DECISAO_INICIO", 1))
                .isInstanceOf(IdusInvalidoException.class)
                .hasMessageContaining("Segmento ID_IGT");

        assertThat(contarProcessos(dinamica)).isZero();
    }

    @Test
    void deveDesfazerATransacaoQuandoOIdusJaExiste() {
        Base base = novoInstrumento("PDM", "555", "1111");
        UUID primeiraDinamica = novaDinamica(base, "ALTERACAO", 1);
        UUID segundaDinamica = novaDinamica(base, "ALTERACAO", 1);
        gravarProcesso(base, primeiraDinamica, "DECISAO_INICIO", 1);
        int contadorDepoisDaPrimeira = valorDoContador(CONTADOR_ORDEM);
        // A segunda Dinâmica volta a receber a Ordem que a primeira já tem: o resto dos segmentos é igual.
        definirContador(CONTADOR_ORDEM, contadorDepoisDaPrimeira - 1);

        try {
            assertThatThrownBy(() -> gravarProcesso(base, segundaDinamica, "DECISAO_INICIO", 1))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasStackTraceContaining("uq_idus_processo_idus");

            assertThat(contarProcessos(segundaDinamica)).isZero();
        } finally {
            definirContador(CONTADOR_ORDEM, contadorDepoisDaPrimeira);
        }
    }

    @Test
    void deveCriarOIdusDeUmProcessoDeRegistoExterno() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID dinamica = novaDinamica(base, "ALTERACAO", 1);

        UUID registado = transacao.execute(status -> {
            Processo processo = novoProcesso(base, dinamica, "DECISAO_INICIO", 1);
            processo.setEstado(EstadoProcesso.PUBLICADO);
            processo.setOrigemDados("REGISTO_EXTERNO");
            processo.setNumeroDr("12345");
            processo.setDataPublicacaoDr(LocalDate.of(2026, 1, 15));
            processoRepository.saveAndFlush(processo);
            idusProcessoService.criar(processo);
            idusProcessoService.registarPublicacaoIncm(processo);
            return processo.getId();
        });

        IdusProcesso idus = idusDe(registado);
        assertThat(idus.getIdus()).matches("^01\\.1111\\.01\\.9\\d{5}\\.02\\.9\\d{4}\\.01\\.031\\.00\\.20260115$");
        assertThat(idus.getSubmissao()).isZero();
        assertThat(idus.getPubIncm()).isEqualTo("20260115");
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(registado))
                .extracting(IdusProcessoHistorico::getEvento)
                .containsExactly(EventoIdus.PROCESSO_CRIADO, EventoIdus.PUBLICACAO_INCM);
    }

    @Test
    void devePassarASubmissaoA01NaPrimeiraSubmissaoERegistarNoHistorico() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID decisao = gravarProcesso(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);
        String idusNaCriacao = idusDe(decisao).getIdus();

        submeter(decisao);

        IdusProcesso idus = idusDe(decisao);
        assertThat(idus.getSubmissao()).isEqualTo((short) 1);
        assertThat(idus.getIdus()).isEqualTo(idusNaCriacao.replaceAll("\\.00$", ".01"));
        assertThat(idus.getPubIncm()).isNull();
        List<IdusProcessoHistorico> historico = historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao);
        assertThat(historico).hasSize(2);
        assertThat(historico.get(1).getSegmento()).isEqualTo(SegmentoIdus.SUBMISSAO);
        assertThat(historico.get(1).getEvento()).isEqualTo(EventoIdus.SUBMISSAO);
        assertThat(historico.get(1).getIdusAnterior()).isEqualTo(idusNaCriacao);
        assertThat(historico.get(1).getIdusNovo()).isEqualTo(idus.getIdus());
        assertThat(historico.get(1).getCorrelationId()).isNotBlank();
    }

    @Test
    void naoDeveMexerNaSubmissaoNemNoHistoricoQuandoSeResgataEVoltaASubmeter() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID decisao = gravarProcesso(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);
        submeter(decisao);
        String idusDepoisDaPrimeira = idusDe(decisao).getIdus();

        assertThat(submeter(decisao)).isEmpty();

        assertThat(idusDe(decisao).getSubmissao()).isEqualTo((short) 1);
        assertThat(idusDe(decisao).getIdus()).isEqualTo(idusDepoisDaPrimeira);
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao)).hasSize(2);
    }

    @Test
    void deveIncrementarASubmissaoDepoisDeUmaRecusaDaDgtEDeUmaRecusaDaIncm() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID decisao = gravarProcesso(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);
        submeter(decisao);

        recusarPelaDgt(base, decisao);
        submeter(decisao);
        assertThat(idusDe(decisao).getSubmissao()).isEqualTo((short) 2);
        assertThat(idusDe(decisao).getIdus()).endsWith(".031.02");

        recusarPelaIncm(decisao);
        submeter(decisao);
        assertThat(idusDe(decisao).getSubmissao()).isEqualTo((short) 3);
        assertThat(idusDe(decisao).getIdus()).endsWith(".031.03");
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao))
                .extracting(IdusProcessoHistorico::getEvento)
                .containsExactly(EventoIdus.PROCESSO_CRIADO, EventoIdus.SUBMISSAO, EventoIdus.SUBMISSAO,
                        EventoIdus.SUBMISSAO);
    }

    @Test
    void deveFalharSemMudarNadaQuandoASubmissaoPassariaDe99Para100() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID decisao = gravarProcesso(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);
        jdbcTemplate.update("UPDATE idus_processo SET submissao = 99, idus = regexp_replace(idus, '\\.00$', '.99') "
                + "WHERE processo_id = ?", decisao);
        // A Submissão esperada é 1 + as recusas: 99 recusas pedem a 100.ª.
        for (int i = 0; i < 99; i++) {
            recusarPelaIncm(decisao);
        }

        assertThatThrownBy(() -> submeter(decisao))
                .isInstanceOf(IdusInvalidoException.class)
                .hasMessageContaining("Sub-contador de submissão inválido");

        assertThat(idusDe(decisao).getSubmissao()).isEqualTo((short) 99);
        assertThat(idusDe(decisao).getIdus()).endsWith(".031.99");
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao)).hasSize(1);
    }

    @Test
    void naoDeveFazerNadaNemAvisarQuandoOProcessoNaoTemIdus() {
        Base base = novoInstrumento("PDM", null);
        UUID decisao = gravarProcessoSemIdus(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);

        assertThat(submeter(decisao)).isEmpty();
        assertThat(publicar(decisao, LocalDate.of(2019, 12, 10))).isEmpty();

        assertSemIdus(decisao);
        assertThat(avisos()).noneSatisfy(aviso -> assertThat(aviso).contains(decisao.toString()));
    }

    @Test
    void deveAcrescentarOPubIncmNaPublicacaoERegistarNoHistorico() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID decisao = gravarProcesso(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);
        submeter(decisao);
        String idusSubmetido = idusDe(decisao).getIdus();

        publicar(decisao, LocalDate.of(2019, 12, 10));

        IdusProcesso idus = idusDe(decisao);
        assertThat(idus.getPubIncm()).isEqualTo("20191210");
        assertThat(idus.getSubmissao()).isEqualTo((short) 1);
        assertThat(idus.getIdus()).isEqualTo(idusSubmetido + ".20191210");
        List<IdusProcessoHistorico> historico = historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao);
        assertThat(historico).hasSize(3);
        assertThat(historico.get(2).getSegmento()).isEqualTo(SegmentoIdus.PUB_INCM);
        assertThat(historico.get(2).getEvento()).isEqualTo(EventoIdus.PUBLICACAO_INCM);
        assertThat(historico.get(2).getIdusAnterior()).isEqualTo(idusSubmetido);
        assertThat(historico.get(2).getIdusNovo()).isEqualTo(idus.getIdus());
    }

    @Test
    void naoDeveRepetirOPubIncmNemOHistoricoSeAPublicacaoChegaDuasVezes() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID decisao = gravarProcesso(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);
        publicar(decisao, LocalDate.of(2019, 12, 10));

        assertThat(publicar(decisao, LocalDate.of(2020, 1, 2))).isEmpty();

        assertThat(idusDe(decisao).getPubIncm()).isEqualTo("20191210");
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao)).hasSize(2);
    }

    @Test
    void deveAvisarESeguirSemPubIncmQuandoAIncmNaoDevolveADataDePublicacao() {
        Base base = novoInstrumento("PDM", null, "1111");
        UUID decisao = gravarProcesso(base, novaDinamica(base, "ALTERACAO", 1), "DECISAO_INICIO", 1);
        String idusNaCriacao = idusDe(decisao).getIdus();

        assertThat(publicar(decisao, null)).isEmpty();

        assertThat(idusDe(decisao).getPubIncm()).isNull();
        assertThat(idusDe(decisao).getIdus()).isEqualTo(idusNaCriacao);
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(decisao)).hasSize(1);
        assertThat(avisos()).anySatisfy(aviso -> assertThat(aviso).contains(decisao.toString()).contains("pubINCM"));
    }

    private Optional<IdusProcesso> submeter(UUID processoId) {
        return transacao.execute(status ->
                idusProcessoService.registarSubmissao(processoRepository.findById(processoId).orElseThrow()));
    }

    private Optional<IdusProcesso> publicar(UUID processoId, LocalDate dataPublicacao) {
        return transacao.execute(status -> {
            Processo processo = processoRepository.findById(processoId).orElseThrow();
            processo.setDataPublicacaoDr(dataPublicacao);
            processoRepository.saveAndFlush(processo);
            return idusProcessoService.registarPublicacaoIncm(processo);
        });
    }

    private void recusarPelaDgt(Base base, UUID processoId) {
        transacao.executeWithoutResult(status -> rejeicaoProcessoService.registarRecusaDgt(
                processoRepository.findById(processoId).orElseThrow(),
                utilizadorRepository.findById(base.utilizadorId()).orElseThrow(), null, List.of()));
    }

    private void recusarPelaIncm(UUID processoId) {
        transacao.executeWithoutResult(status -> rejeicaoProcessoService.registarRecusaIncm(
                processoRepository.findById(processoId).orElseThrow(), List.of("Ato recusado pela INCM.")));
    }

    private void criarIdusEmSimultaneo(UUID primeiroProcesso, UUID segundoProcesso) throws Exception {
        CountDownLatch primeiroCriou = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // O primeiro mantém a transação (e o lock da Ordem) aberta enquanto o segundo tenta criar o seu.
            Callable<Void> primeiro = () -> {
                transacao.executeWithoutResult(status -> {
                    idusProcessoService.criar(processoRepository.findById(primeiroProcesso).orElseThrow());
                    primeiroCriou.countDown();
                    dormir(600);
                });
                return null;
            };
            Callable<Void> segundo = () -> {
                primeiroCriou.await();
                transacao.executeWithoutResult(status ->
                        idusProcessoService.criar(processoRepository.findById(segundoProcesso).orElseThrow()));
                return null;
            };
            Future<Void> aPrimeira = executor.submit(primeiro);
            Future<Void> aSegunda = executor.submit(segundo);
            aPrimeira.get(30, TimeUnit.SECONDS);
            aSegunda.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Base novoInstrumento(String tipoInstrumento, String idIgtSnit, String... concelhos) {
        int n = SEQUENCIA.incrementAndGet();
        UUID utilizador = UUID.randomUUID();
        UUID instrumento = UUID.randomUUID();
        String nif = String.format("5%08d", n);
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizador, "autor-" + n + "@cm-idus.pt");
        jdbcTemplate.update("INSERT INTO entidade (codigo, descricao_incm, descricao_saigt, tipo_entidade, nipc) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "T" + nif, "Câmara Municipal " + n, "Câmara Municipal " + n, "CM", nif);
        jdbcTemplate.update("INSERT INTO instrumento (id, tipo_instrumento_id, numero, designacao, "
                        + "responsavel_id, criado_por, id_igt_snit) VALUES (?, "
                        + "(SELECT id FROM tipo_instrumento WHERE codigo = ?), ?, ?, ?, ?, ?)",
                instrumento, tipoInstrumento, "IDUS-" + n, tipoInstrumento + " " + n, utilizador, utilizador,
                idIgtSnit);
        for (String concelho : concelhos) {
            jdbcTemplate.update("INSERT INTO instrumento_concelho (instrumento_id, municipio_codigo) VALUES (?, ?)",
                    instrumento, concelho);
        }
        return new Base(utilizador, instrumento, nif);
    }

    private UUID novaDinamica(Base base, String tipoDinamica, int tentativa) {
        Long configId = jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica t ON t.id = c.tipo_dinamica_id "
                + "WHERE t.codigo = ? AND c.tipo_instrumento_id = "
                + "(SELECT tipo_instrumento_id FROM instrumento WHERE id = ?)", Long.class, tipoDinamica,
                base.instrumentoId());
        return gravarDinamica(base, dinamicaConfigRepository.findById(configId).orElseThrow(), tentativa);
    }

    private UUID gravarDinamica(Base base, InstrumentoDinamicaConfig config, int tentativa) {
        return transacao.execute(status -> {
            Dinamica dinamica = new Dinamica(instrumentoRepository.findById(base.instrumentoId()).orElseThrow(),
                    config, "Dinâmica de teste do IDUS",
                    utilizadorRepository.findById(base.utilizadorId()).orElseThrow());
            ReflectionTestUtils.setField(dinamica, "tentativa", tentativa);
            return dinamicaRepository.saveAndFlush(dinamica).getId();
        });
    }

    private Processo novoProcesso(Base base, UUID dinamicaId, String procedimento, int ocorrencia) {
        Long configId = jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_procedimento_config c "
                + "JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
                + "WHERE t.codigo = ? AND c.instrumento_dinamica_config_id = "
                + "(SELECT instrumento_dinamica_config_id FROM dinamica WHERE id = ?)", Long.class, procedimento,
                dinamicaId);
        Processo processo = new Processo(dinamicaRepository.findById(dinamicaId).orElseThrow(),
                procedimentoConfigRepository.findById(configId).orElseThrow(),
                utilizadorRepository.findById(base.utilizadorId()).orElseThrow(),
                entidadeRepository.findAllByNipc(base.nipc()).get(0));
        processo.setOcorrencia(ocorrencia);
        return processo;
    }

    private UUID gravarProcesso(Base base, UUID dinamicaId, String procedimento, int ocorrencia) {
        return transacao.execute(status -> {
            Processo processo = processoRepository.saveAndFlush(novoProcesso(base, dinamicaId, procedimento, ocorrencia));
            idusProcessoService.criar(processo);
            return processo.getId();
        });
    }

    private UUID gravarProcessoSemIdus(Base base, UUID dinamicaId, String procedimento, int ocorrencia) {
        return transacao.execute(status ->
                processoRepository.saveAndFlush(novoProcesso(base, dinamicaId, procedimento, ocorrencia)).getId());
    }

    private IdusProcesso idusDe(UUID processoId) {
        return idusProcessoRepository.findById(processoId).orElseThrow();
    }

    private void assertSemIdus(UUID processoId) {
        assertThat(idusProcessoRepository.findById(processoId)).isEmpty();
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(processoId)).isEmpty();
    }

    private List<String> avisos() {
        return registosDoServico.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static ch.qos.logback.classic.Logger logDoServico() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(IdusProcessoService.class);
    }

    private Long idDe(String tabela, String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM " + tabela + " WHERE codigo = ?", Long.class, codigo);
    }

    private int contarProcessos(UUID dinamicaId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processo WHERE dinamica_id = ?", Integer.class,
                dinamicaId);
    }

    private int valorDoContador(String nome) {
        Optional<Integer> valor = jdbcTemplate.queryForList("SELECT valor_atual FROM idus_contador WHERE nome = ?",
                Integer.class, nome).stream().findFirst();
        return valor.orElse(0);
    }

    private void definirContador(String nome, int valor) {
        jdbcTemplate.update("INSERT INTO idus_contador (nome, valor_atual) VALUES (?, ?) "
                + "ON CONFLICT (nome) DO UPDATE SET valor_atual = EXCLUDED.valor_atual", nome, valor);
    }

    private void apagarContador(String nome) {
        jdbcTemplate.update("DELETE FROM idus_contador WHERE nome = ?", nome);
    }
}
