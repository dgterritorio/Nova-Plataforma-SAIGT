package com.conkord.saigt.db;

import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.IdusContador;
import com.conkord.saigt.persistence.operacional.IdusContadorRepository;
import com.conkord.saigt.persistence.operacional.IdusDinamica;
import com.conkord.saigt.persistence.operacional.IdusDinamicaRepository;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistorico;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistoricoRepository;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.SegmentoIdus;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.InstrumentoService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Arranca a aplicação como em produção: o Liquibase corre o {@code master.xml} num PostgreSQL real e o Hibernate
 * valida as entidades contra o esquema que ele criou ({@code ddl-auto=validate}). Os restantes testes de contexto
 * usam H2 com o esquema do Hibernate e não apanham uma entidade que diverge da migração.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("docker")
class EsquemaValidadoPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;

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

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

    @Autowired
    private IdusProcessoHistoricoRepository historicoRepository;

    @Autowired
    private IdusDinamicaRepository idusDinamicaRepository;

    @Autowired
    private IdusContadorRepository idusContadorRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private InstrumentoService instrumentoService;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    private record Base(UUID utilizadorId, UUID instrumentoId, String nipc) {
    }

    @Test
    void deveArrancarComAsEntidadesDoIdusValidadasContraAMigracao() {
        assertThat(ddlAuto).isEqualTo("validate");
        assertThat(entityManagerFactory.getMetamodel().getEntities()).extracting(EntityType::getName)
                .contains("IdusProcesso", "IdusProcessoHistorico", "IdusDinamica", "IdusContador");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_name IN ('idus_processo', 'idus_processo_historico', 'idus_dinamica', 'idus_contador')",
                Integer.class)).isEqualTo(4);
    }

    @Test
    void deveLerEGravarAsRegrasTiffDosItensPelaEntidadeNumPostgresReal() {
        List<DocumentoConfig> pecasGraficas = documentoConfigRepository.findAll().stream()
                .filter(item -> item.getTipoElemento() == TipoElemento.PECA_GRAFICA).toList();
        // Os itens do SGIFR trazem as regras do GeoTIFF do SGIFR; os restantes, as gerais.
        assertThat(pecasGraficas).isNotEmpty().allSatisfy(item -> {
            RegrasTiff esperadas = item.getRegraNomenclatura() == RegraNomenclatura.SGIFR
                    ? RegrasTiff.inicialSgifr() : RegrasTiff.inicialGeral();
            assertThat(item.getRegrasTiff()).isEqualTo(esperadas);
            assertThat(item.regrasTiffEfetivas()).contains(esperadas);
        });
        assertThat(pecasGraficas).filteredOn(item -> item.getRegraNomenclatura() == RegraNomenclatura.SGIFR).hasSize(28);

        Long id = pecasGraficas.stream().filter(item -> item.getRegraNomenclatura() == RegraNomenclatura.GERAL)
                .findFirst().orElseThrow().getId();
        transacao.executeWithoutResult(status -> {
            DocumentoConfig item = documentoConfigRepository.findById(id).orElseThrow();
            item.setRegrasTiff(RegrasTiff.inicialSgifr());
            documentoConfigRepository.saveAndFlush(item);
            assertThat(jdbcTemplate.queryForObject("SELECT regras_tiff ->> 'resolucaoDpi' FROM documento_config "
                    + "WHERE id = ?", String.class, id)).isEqualTo("300");
            status.setRollbackOnly();
        });
    }

    @Test
    void deveLerEGravarARegraDeNomenclaturaDosItensPelaEntidadeNumPostgresReal() {
        List<DocumentoConfig> itens = documentoConfigRepository.findAll();
        assertThat(itens).isNotEmpty().filteredOn(item -> item.getRegraNomenclatura() == RegraNomenclatura.SGIFR)
                .hasSize(44);

        Long id = itens.stream().filter(item -> item.getRegraNomenclatura() == RegraNomenclatura.GERAL)
                .findFirst().orElseThrow().getId();
        transacao.executeWithoutResult(status -> {
            DocumentoConfig item = documentoConfigRepository.findById(id).orElseThrow();
            item.setRegraNomenclatura(RegraNomenclatura.SGIFR);
            documentoConfigRepository.saveAndFlush(item);
            assertThat(jdbcTemplate.queryForObject("SELECT regra_nomenclatura FROM documento_config WHERE id = ?",
                    String.class, id)).isEqualTo("SGIFR");
            status.setRollbackOnly();
        });
    }

    @Test
    void deveLerAsColunasV15PelasEntidadesESemOutrosAtosEAnexosNasDinamicasDoInstrumento() {
        List<String> associacoes = transacao.execute(status -> dinamicaConfigRepository.findAll().stream()
                .map(config -> config.getTipoDinamica().isInicioSemIgt() + ":" + config.getAssociacaoInstrumentos())
                .distinct().sorted().toList());
        assertThat(associacoes).containsExactly("false:" + AssociacaoInstrumentos.UM, "true:" + AssociacaoInstrumentos.OPCIONAL,
                "true:" + AssociacaoInstrumentos.UM, "true:" + AssociacaoInstrumentos.UM_OU_MAIS);
        assertThat(procedimentoConfigRepository.findAll())
                .isNotEmpty()
                .allSatisfy(config -> assertThat(config.getTamanhoMaximoProcesso()).isEqualTo(5_368_709_120L));

        TipoInstrumento pdm = tipoInstrumentoRepository.findAll().stream()
                .filter(tipo -> "PDM".equals(tipo.getCodigo())).findFirst().orElseThrow();
        List<String> dinamicasDoPdm = transacao.execute(status -> dinamicaConfigRepository
                .findByTipoInstrumentoAndAtivoTrue(pdm).stream()
                .map(config -> config.getTipoDinamica().getCodigo()).toList());

        assertThat(dinamicasDoPdm).isNotEmpty().doesNotContain("OUTROS_ATOS_ANEXOS");
    }

    @Test
    void deveArrancarListandoARenEOSgifrEComAsMesmasDinamicasPorIniciarDoPdm() {
        assertThat(tipoInstrumentoRepository.findAll()).extracting(TipoInstrumento::getCodigo)
                .contains("REN", "PRA", "PSA");
        assertThat(instrumentoService.listarTipos()).extracting(TipoInstrumento::getCodigo)
                .containsExactly("PDIM", "PDM", "PEOT", "PIMOT", "PNPOT", "PP", "PPIM", "PRA", "PROT", "PS", "PSA", "PU", "PUIM",
                        "REN", "SERVIDAO");

        TipoInstrumento pdm = tipoInstrumentoRepository.findAll().stream()
                .filter(tipo -> "PDM".equals(tipo.getCodigo())).findFirst().orElseThrow();
        // A mesma leitura e ordem com que o painel do Instrumento lista as Dinâmicas por iniciar.
        List<String> dinamicasPorIniciar = transacao.execute(status -> dinamicaConfigRepository
                .findByTipoInstrumentoAndAtivoTrue(pdm).stream()
                .sorted(Comparator.comparing(config -> config.getTipoDinamica().getId()))
                .map(config -> config.getTipoDinamica().getCodigo()).toList());

        assertThat(dinamicasPorIniciar).containsExactly("ELABORACAO", "REVISAO", "ALTERACAO",
                "ALTERACAO_SIMPLIFICADA_ART123", "ALTERACAO_SIMPLIFICADA_DL10_2024", "ALTERACAO_ADAPTACAO",
                "CORRECAO_MATERIAL", "REVOGACAO", "MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS", "SUSPENSAO",
                "SUSPENSAO_CCDR", "SUSPENSAO_GOVERNO", "MEDIDAS_PREVENTIVAS_GOVERNO", "RATIFICACAO");
    }

    @Test
    void deveLerOsCodigosDoIdusPelasEntidadesComOEsquemaValidado() {
        List<TipoInstrumento> tipos = tipoInstrumentoRepository.findAll();
        TipoInstrumento pdm = tipos.stream().filter(tipo -> "PDM".equals(tipo.getCodigo())).findFirst().orElseThrow();
        assertThat(pdm.getCodigoIdus()).isEqualTo("01");
        assertThat(tipos).filteredOn(tipo -> "PROT".equals(tipo.getCodigo()) || "PEOT".equals(tipo.getCodigo()))
                .hasSize(2).allSatisfy(tipo -> assertThat(tipo.getCodigoIdus()).isNull());
        // Folha B do IDUS (IDTYPE): REN 37, PDIM 53, PUIM 54, PPIM 55 (changeset 2026-11-12-14).
        assertThat(tipos).filteredOn(tipo -> List.of("REN", "PDIM", "PUIM", "PPIM").contains(tipo.getCodigo()))
                .extracting(TipoInstrumento::getCodigo, TipoInstrumento::getCodigoIdus)
                .containsExactlyInAnyOrder(tuple("REN", "37"), tuple("PDIM", "53"), tuple("PUIM", "54"),
                        tuple("PPIM", "55"));
        // EF v1.5: a entidade instrutora não tem «X» no PS (changeset 2026-11-12-15).
        assertThat(tipos).filteredOn(tipo -> "PROGRAMA".equals(tipo.getCategoria()))
                .extracting(TipoInstrumento::getCodigo, TipoInstrumento::isEntidadeInstrutoraObrigatoria)
                .contains(tuple("PS", false), tuple("PNPOT", true), tuple("PIMOT", true));

        List<String> alteracaoEDecisaoDeInicio = transacao.execute(status -> {
            InstrumentoDinamicaConfig alteracao = dinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(pdm).stream()
                    .filter(config -> "ALTERACAO".equals(config.getTipoDinamica().getCodigo()))
                    .findFirst().orElseThrow();
            InstrumentoProcedimentoConfig decisaoDeInicio = procedimentoConfigRepository
                    .findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(alteracao).stream()
                    .filter(config -> "DECISAO_INICIO".equals(config.getTipoProcedimento().getCodigo()))
                    .findFirst().orElseThrow();
            return List.of(alteracao.getTipoDinamica().getCodigoIdus(), decisaoDeInicio.getCodigoIdus());
        });

        assertThat(alteracaoEDecisaoDeInicio).containsExactly("02", "031");
    }

    @Test
    void deveGravarELerPelasEntidadesNumPostgresRealComAsRestricoesDaBd() {
        Base base = criarBase("val", "500000002");
        Dinamica dinamica = novaDinamica(base);
        UUID processoA = novoProcesso(dinamica.getId(), base, 1).getId();
        UUID processoB = novoProcesso(dinamica.getId(), base, 2).getId();
        Instant agora = Instant.parse("2026-11-03T10:00:00Z");

        guardarIdus(processoA, "IDUS-A", (short) 1, (short) 99, agora);
        IdusProcesso lido = idusProcessoRepository.findByIdus("IDUS-A").orElseThrow();
        assertThat(lido.getProcessoId()).isEqualTo(processoA);
        assertThat(lido.getTentativa()).isEqualTo((short) 1);
        assertThat(lido.getSubmissao()).isEqualTo((short) 99);
        assertThat(lido.getAtualizadoEm()).isEqualTo(agora);
        assertThat(idusProcessoRepository.existsByIdus("IDUS-A")).isTrue();

        assertThatThrownBy(() -> guardarIdus(processoB, "IDUS-A", (short) 1, (short) 0, agora))
                .hasStackTraceContaining("uq_idus_processo_idus");
        assertThatThrownBy(() -> guardarIdus(processoB, "IDUS-B", (short) 1, (short) 100, agora))
                .hasStackTraceContaining("ck_idus_processo_submissao");
        assertThatThrownBy(() -> guardarIdus(processoB, "IDUS-B", (short) 0, (short) 0, agora))
                .hasStackTraceContaining("ck_idus_processo_tentativa");

        historicoRepository.saveAndFlush(historico(processoA, SegmentoIdus.SUBMISSAO, EventoIdus.SUBMISSAO, agora.plusSeconds(60)));
        historicoRepository.saveAndFlush(historico(processoA, SegmentoIdus.CRIACAO, EventoIdus.PROCESSO_CRIADO, agora));
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(processoA))
                .extracting(IdusProcessoHistorico::getSegmento)
                .containsExactly(SegmentoIdus.CRIACAO, SegmentoIdus.SUBMISSAO);
        assertThatThrownBy(() -> historicoRepository.deleteAll()).hasStackTraceContaining("permission denied");

        transacao.executeWithoutResult(status -> idusDinamicaRepository.saveAndFlush(IdusDinamica.builder()
                .dinamica(dinamicaRepository.findById(dinamica.getId()).orElseThrow())
                .idus("SNIT-1001-T001").idIgtSnit("1001").ordemSnit("T001").geradoEm(agora).build()));
        assertThat(idusDinamicaRepository.findById(dinamica.getId()).orElseThrow().getIdus()).isEqualTo("SNIT-1001-T001");

        idusContadorRepository.saveAndFlush(new IdusContador("CONTADOR_DE_TESTE", 41));
        assertThat(idusContadorRepository.findById("CONTADOR_DE_TESTE").orElseThrow().getValorAtual()).isEqualTo(41);
        assertThatThrownBy(() -> idusContadorRepository.saveAndFlush(new IdusContador("NEGATIVO", -1)))
                .hasStackTraceContaining("ck_idus_contador_valor_atual");
    }

    @Test
    void deveAtribuirOsNumerosDeProcessoEDeDinamicaPeloIdusContadorEmSequencia() {
        Base base = criarBase("seq", "500000003");
        int processosAntes = valorDoContador("NUMERO_PROCESSO");
        int dinamicasAntes = valorDoContador("NUMERO_DINAMICA");

        Dinamica dinamica = novaDinamica(base);
        Processo primeiro = novoProcesso(dinamica.getId(), base, 1);
        Processo segundo = novoProcesso(dinamica.getId(), base, 2);

        assertThat(dinamica.getNumeroDinamica()).isEqualTo(String.format("%05d", dinamicasAntes + 1));
        assertThat(primeiro.getNumeroProcesso()).isEqualTo(String.format("%05d", processosAntes + 1));
        assertThat(segundo.getNumeroProcesso()).isEqualTo(String.format("%05d", processosAntes + 2));
        assertThat(valorDoContador("NUMERO_PROCESSO")).isEqualTo(processosAntes + 2);
        assertThat(valorDoContador("NUMERO_DINAMICA")).isEqualTo(dinamicasAntes + 1);
        assertThat(processoRepository.findById(segundo.getId()).orElseThrow().getNumeroProcesso())
                .isEqualTo(segundo.getNumeroProcesso());
    }

    @Test
    void deveAtribuirNumerosDeProcessoDistintosESemFurosEmCriacoesConcorrentes() throws Exception {
        Base base = criarBase("con", "500000004");
        UUID dinamicaId = novaDinamica(base).getId();
        int antes = valorDoContador("NUMERO_PROCESSO");
        int fios = 8;
        int porFio = 5;

        ExecutorService executor = Executors.newFixedThreadPool(fios);
        try {
            List<Callable<List<String>>> tarefas = new ArrayList<>();
            for (int fio = 0; fio < fios; fio++) {
                int inicio = fio * porFio;
                tarefas.add(() -> {
                    List<String> numeros = new ArrayList<>();
                    for (int i = 1; i <= porFio; i++) {
                        numeros.add(novoProcesso(dinamicaId, base, inicio + i).getNumeroProcesso());
                    }
                    return numeros;
                });
            }
            List<String> todos = new ArrayList<>();
            for (Future<List<String>> resultado : executor.invokeAll(tarefas)) {
                todos.addAll(resultado.get());
            }

            assertThat(todos).hasSize(fios * porFio).doesNotHaveDuplicates();
            List<String> esperados = new ArrayList<>();
            for (int n = antes + 1; n <= antes + fios * porFio; n++) {
                esperados.add(String.format("%05d", n));
            }
            assertThat(todos).containsExactlyInAnyOrderElementsOf(esperados);
            assertThat(valorDoContador("NUMERO_PROCESSO")).isEqualTo(antes + fios * porFio);
        } finally {
            executor.shutdownNow();
        }
    }

    private Base criarBase(String sufixo, String nif) {
        UUID utilizador = UUID.randomUUID();
        UUID instrumento = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizador, "autor-" + sufixo + "@cm-val.pt");
        jdbcTemplate.update("INSERT INTO entidade (codigo, descricao_incm, descricao_saigt, tipo_entidade, nipc) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "T" + nif, "Câmara Municipal " + sufixo, "Câmara Municipal " + sufixo, "CM", nif);
        jdbcTemplate.update("INSERT INTO instrumento (id, tipo_instrumento_id, numero, designacao, "
                + "responsavel_id, criado_por) VALUES (?, (SELECT min(id) FROM tipo_instrumento), ?, 'PDM', ?, ?)",
                instrumento, sufixo, utilizador, utilizador);
        return new Base(utilizador, instrumento, nif);
    }

    private Dinamica novaDinamica(Base base) {
        return transacao.execute(status -> dinamicaRepository.saveAndFlush(new Dinamica(
                instrumentoRepository.findById(base.instrumentoId()).orElseThrow(),
                dinamicaConfigRepository.findAll().get(0), "Alteração do PDM",
                utilizadorRepository.findById(base.utilizadorId()).orElseThrow())));
    }

    private Processo novoProcesso(UUID dinamicaId, Base base, int ocorrencia) {
        return transacao.execute(status -> {
            Processo processo = new Processo(dinamicaRepository.findById(dinamicaId).orElseThrow(),
                    procedimentoConfigRepository.findAll().get(0),
                    utilizadorRepository.findById(base.utilizadorId()).orElseThrow(),
                    entidadeRepository.findAllByNipc(base.nipc()).get(0));
            processo.setOcorrencia(ocorrencia);
            return processoRepository.saveAndFlush(processo);
        });
    }

    private int valorDoContador(String nome) {
        List<Integer> valores = jdbcTemplate.queryForList(
                "SELECT valor_atual FROM idus_contador WHERE nome = ?", Integer.class, nome);
        return valores.isEmpty() ? 0 : valores.get(0);
    }

    // O Processo tem de estar gerido na mesma transação em que se grava o IDUS, como num serviço.
    private void guardarIdus(UUID processoId, String idus, short tentativa, short submissao, Instant atualizadoEm) {
        transacao.executeWithoutResult(status -> idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                .processo(processoRepository.findById(processoId).orElseThrow())
                .idus(idus).sistemaOrigem("SAIGT").idIgt("1001").ordemDinamica("T001")
                .tentativa(tentativa).procedimento("031").submissao(submissao).atualizadoEm(atualizadoEm)
                .build()));
    }

    private IdusProcessoHistorico historico(UUID processoId, SegmentoIdus segmento, EventoIdus evento, Instant registadoEm) {
        return IdusProcessoHistorico.builder()
                .processoId(processoId).segmento(segmento).idusNovo("IDUS-A").evento(evento)
                .registadoEm(registadoEm).correlationId("corr-1").build();
    }
}
