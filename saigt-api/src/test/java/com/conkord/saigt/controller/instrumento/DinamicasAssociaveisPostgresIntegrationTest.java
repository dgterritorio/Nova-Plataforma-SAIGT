package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaAssociavel;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A associação de um processo à Dinâmica de um IGT (BL-PROC-062) lê `tipo_instrumento_associavel` e
 * `admite_associacao_igt` do seed Liquibase, que os testes em H2 não aplicam: arranca a aplicação num PostgreSQL real
 * com o {@code master.xml}. A REN ainda não tem Procedimentos no seed; o teste cria-os, como a configuração da REN fará.
 * Cada teste usa concelhos próprios, para as listas não verem as Dinâmicas dos outros.
 */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class DinamicasAssociaveisPostgresIntegrationTest {

    private static final String NIF_CCDR = "600000284";
    private static final String NIF_OUTRA_CCDR = "600001284";
    private static final String NIF_CAMARA = "500000284";
    private static final int PORTA_POSTGRES = 5432;
    private static final AtomicInteger NUMERO = new AtomicInteger();

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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transacao;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired private TipoAtoRepository tipoAtoRepository;
    @Autowired private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired private PermissaoAcaoRepository permissaoAcaoRepository;
    @Autowired private EntidadeRepository entidadeRepository;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private InstrumentoRepository instrumentoRepository;
    @Autowired private InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    @Autowired private DinamicaRepository dinamicaRepository;
    @Autowired private ProcessoRepository processoRepository;

    /** Por chave `TIPO:DINAMICA` (ex.: `REN:DELIMITACAO`). */
    private static Map<String, InstrumentoDinamicaConfig> configuracoes;
    /** Procedimentos com formulário (Decisão de início) das configurações onde o teste cria processos. */
    private static Map<String, InstrumentoProcedimentoConfig> procedimentos;
    private static Map<String, Long> entidades;
    private static Map<String, UUID> utilizadores;

    @BeforeEach
    void prepararUmaVez() {
        if (procedimentos != null) {
            return;
        }
        entidades = new HashMap<>();
        entidades.put("CCDR", entidadeRepository.saveAndFlush(
                EntidadeTeste.nova(NIF_CCDR, "CCDR de Teste", TipoEntidade.CCDR_DR)).getId());
        entidades.put("OUTRA_CCDR", entidadeRepository.saveAndFlush(
                EntidadeTeste.nova(NIF_OUTRA_CCDR, "Outra CCDR de Teste", TipoEntidade.CCDR_DR)).getId());
        entidades.put("CAMARA", entidadeRepository.saveAndFlush(
                EntidadeTeste.nova(NIF_CAMARA, "Câmara Municipal de Teste", TipoEntidade.CM)).getId());
        utilizadores = new HashMap<>();
        for (String nome : List.of("AUTOR", "COLEGA", "OUTRA_CCDR", "CAMARA")) {
            utilizadores.put(nome, utilizadorRepository.saveAndFlush(
                    new Utilizador("Utilizador " + nome, nome.toLowerCase() + "@teste-284.pt", "INCM")).getId());
        }
        configuracoes = new HashMap<>();
        for (String chave : List.of("REN:DELIMITACAO", "REN:ALTERACAO", "REN:CORRECAO_MATERIAL_RETIFICACAO",
                "PDM:ALTERACAO", "PU:ALTERACAO", "PRA:DELIMITACAO")) {
            configuracoes.put(chave, configuracao(chave));
        }
        procedimentos = new HashMap<>();
        TipoProcedimento decisaoInicio = tipoProcedimentoRepository.findAll().stream()
                .filter(tipo -> "DECISAO_INICIO".equals(tipo.getCodigo())).findFirst().orElseThrow();
        for (String chave : List.of("REN:DELIMITACAO", "REN:ALTERACAO", "REN:CORRECAO_MATERIAL_RETIFICACAO")) {
            InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                    new InstrumentoProcedimentoConfig(configuracoes.get(chave), decisaoInicio, 1, "SEGUNDA_C"));
            permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CCDR_DR, procedimento));
            procedimentos.put(chave, procedimento);
        }
        Long decisaoInicioDoPdm = jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE tp.codigo = 'DECISAO_INICIO' "
                + "AND ipc.instrumento_dinamica_config_id = ?", Long.class, configuracoes.get("PDM:ALTERACAO").getId());
        InstrumentoProcedimentoConfig procedimentoDoPdm = procedimentoConfigRepository.findById(decisaoInicioDoPdm).orElseThrow();
        if (!permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM,
                procedimentoDoPdm)) {
            permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoDoPdm));
        }
        procedimentos.put("PDM:ALTERACAO", procedimentoDoPdm);
    }

    @Test
    void deveTerOsTiposAssociaveisDaRenEAAssociacaoSoNaDelimitacaoENaAlteracao() {
        assertThat(jdbcTemplate.queryForList("SELECT o.codigo || '>' || d.codigo FROM tipo_instrumento_associavel a "
                + "JOIN tipo_instrumento o ON o.id = a.tipo_instrumento_id "
                + "JOIN tipo_instrumento d ON d.id = a.tipo_instrumento_associavel_id ORDER BY d.codigo", String.class))
                .containsExactly("REN>PDM", "REN>PEOT", "REN>PP", "REN>PU");
        assertThat(jdbcTemplate.queryForList("SELECT ti.codigo || ':' || td.codigo FROM instrumento_dinamica_config idc "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE idc.admite_associacao_igt "
                + "ORDER BY td.codigo", String.class))
                .containsExactly("REN:ALTERACAO", "REN:DELIMITACAO");
    }

    @Test
    void deveListarSoAsDinamicasNaoTerminadasDosIgtAssociaveisComConcelhoComum() throws Exception {
        UUID pdm = instrumento("PDM", "PDM de Teste 1111", "1111");
        UUID terminada = dinamica(pdm, "PDM:ALTERACAO", "Alteração terminada", EstadoDinamica.TERMINADA);
        UUID caducada = dinamica(pdm, "PDM:ALTERACAO", "Alteração caducada", EstadoDinamica.CADUCADA);
        UUID emCurso = dinamica(pdm, "PDM:ALTERACAO", "Alteração em curso", EstadoDinamica.EM_CURSO);
        dinamica(instrumento("PDM", "PDM de Teste 1105", "1105"), "PDM:ALTERACAO", "Alteração noutro concelho",
                EstadoDinamica.EM_CURSO);
        dinamica(instrumento("PRA", "PRA de Teste 1111", "1111"), "PRA:DELIMITACAO", "Delimitação do PRA",
                EstadoDinamica.EM_CURSO);
        UUID delimitacao = processoDaRen("1111", "REN:DELIMITACAO", "AUTOR", "CCDR");
        UUID alteracao = processoDaRen("1111", "REN:ALTERACAO", "AUTOR", "CCDR");

        String numero = transacao.execute(status -> dinamicaRepository.findById(emCurso).orElseThrow().getNumeroDinamica());
        for (UUID processo : List.of(delimitacao, alteracao)) {
            mockMvc.perform(get(associaveis(processo)).with(jwtDe("AUTOR", "CCDR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].dinamicaId").value(contains(emCurso.toString(), caducada.toString())))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].numeroDinamica").value(numero))
                    .andExpect(jsonPath("$.content[0].descritivo").value("Alteração em curso"))
                    .andExpect(jsonPath("$.content[0].tipoDinamica").value("ALTERACAO"))
                    .andExpect(jsonPath("$.content[0].estado").value("EM_CURSO"))
                    .andExpect(jsonPath("$.content[0].instrumento.id").value(pdm.toString()))
                    .andExpect(jsonPath("$.content[0].instrumento.designacao").value("PDM de Teste 1111"))
                    .andExpect(jsonPath("$.content[0].instrumento.tipoInstrumento").value("PDM"))
                    .andExpect(jsonPath("$.content[1].estado").value("CADUCADA"));
        }
        assertThat(terminada).isNotNull();
    }

    @Test
    void deveDevolverAPaginaCertaNumaSoConsultaComTresCandidatas() {
        UUID pdm = instrumento("PDM", "PDM de Teste 1121", "1121");
        UUID pu = instrumento("PU", "PU de Teste 1121", "1121");
        UUID primeira = dinamica(pu, "PU:ALTERACAO", "Alteração do PU", EstadoDinamica.EM_CURSO);
        UUID segunda = dinamica(pdm, "PDM:ALTERACAO", "Primeira alteração do PDM", EstadoDinamica.CADUCADA);
        UUID terceira = dinamica(pdm, "PDM:ALTERACAO", "Segunda alteração do PDM", EstadoDinamica.EM_CURSO);
        UUID instrumentoRen = instrumento("REN", "REN de Teste 1121", "1121");
        List<Long> tipos = jdbcTemplate.queryForList("SELECT tipo_instrumento_associavel_id FROM tipo_instrumento_associavel",
                Long.class);
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        List<String> todas = transacao.execute(status -> {
            estatisticas.clear();
            Page<DinamicaAssociavel> pagina = dinamicaRepository.listarAssociaveis(instrumentoRen, tipos, PageRequest.of(0, 10));
            List<String> linhas = pagina.getContent().stream().map(linha -> linha.getDinamica().getId() + ":"
                    + linha.getDinamica().getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo() + ":"
                    + linha.getInstrumento().getTipoInstrumento().getCodigo()).toList();
            assertThat(estatisticas.getPrepareStatementCount()).as("uma só consulta, sem contagem nem N+1").isEqualTo(1);
            assertThat(pagina.getTotalElements()).isEqualTo(3);
            return linhas;
        });
        assertThat(todas).containsExactly(terceira + ":ALTERACAO:PDM", segunda + ":ALTERACAO:PDM", primeira + ":ALTERACAO:PU");

        transacao.executeWithoutResult(status -> {
            estatisticas.clear();
            Page<DinamicaAssociavel> pagina = dinamicaRepository.listarAssociaveis(instrumentoRen, tipos, PageRequest.of(1, 2));
            assertThat(pagina.getContent()).extracting(linha -> linha.getDinamica().getId()).containsExactly(primeira);
            pagina.getContent().forEach(linha -> linha.getInstrumento().getTipoInstrumento().getCodigo());
            assertThat(pagina.getTotalElements()).isEqualTo(3);
            assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
            assertThat(dinamicaRepository.existeAssociavel(instrumentoRen, tipos, segunda)).isTrue();
        });
    }

    @Test
    void deveRecusarCom409NumProcessoDeAlteracaoDePdm() throws Exception {
        UUID processo = processo(instrumento("PDM", "PDM de Teste 1131", "1131"), "PDM:ALTERACAO", "CAMARA", "CAMARA");

        mockMvc.perform(get(associaveis(processo)).with(jwtDe("CAMARA", "CAMARA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Este processo não admite associação a uma dinâmica."));
    }

    @Test
    void deveRecusarAListaEAGravacaoNaCorrecaoMaterialDaRenComAceitaFalsoNoDetalhe() throws Exception {
        UUID emCurso = dinamica(instrumento("PDM", "PDM de Teste 1141", "1141"), "PDM:ALTERACAO", "Alteração",
                EstadoDinamica.EM_CURSO);
        UUID correcao = processoDaRen("1141", "REN:CORRECAO_MATERIAL_RETIFICACAO", "AUTOR", "CCDR");

        mockMvc.perform(get(associaveis(correcao)).with(jwtDe("AUTOR", "CCDR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        gravar(correcao, emCurso, "AUTOR", "CCDR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_ASSOCIADA_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("dinamicaAssociadaId"));
        mockMvc.perform(get("/api/v1/processos/" + correcao).with(jwtDe("AUTOR", "CCDR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceitaDinamicaAssociada").value(false))
                .andExpect(jsonPath("$.dinamicaAssociadaId").doesNotExist());
    }

    @Test
    void deveAplicarAsCamadas2E3bEAAutenticacao() throws Exception {
        UUID processo = processoDaRen("1151", "REN:DELIMITACAO", "AUTOR", "CCDR");

        mockMvc.perform(get(associaveis(processo)).with(jwtDe("COLEGA", "CCDR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(associaveis(processo)).with(jwtDe("OUTRA_CCDR", "OUTRA_CCDR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        mockMvc.perform(get(associaveis(processo)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRecusarUmTamanhoDePaginaAcimaDe100() throws Exception {
        UUID processo = processoDaRen("1161", "REN:DELIMITACAO", "AUTOR", "CCDR");

        mockMvc.perform(get(associaveis(processo)).param("size", "101").with(jwtDe("AUTOR", "CCDR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("size"));
    }

    @Test
    void deveGravarUmaDinamicaDaListaEDevolveLaNoDetalheEAuditar() throws Exception {
        UUID pdm = instrumento("PDM", "PDM de Teste 1171", "1171");
        UUID emCurso = dinamica(pdm, "PDM:ALTERACAO", "Alteração para associar", EstadoDinamica.EM_CURSO);
        UUID processo = processoDaRen("1171", "REN:DELIMITACAO", "AUTOR", "CCDR");

        gravar(processo, emCurso, "AUTOR", "CCDR").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + processo).with(jwtDe("AUTOR", "CCDR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicaAssociadaId").value(emCurso.toString()))
                .andExpect(jsonPath("$.aceitaDinamicaAssociada").value(true))
                .andExpect(jsonPath("$.dinamicaAssociada.dinamicaId").value(emCurso.toString()))
                .andExpect(jsonPath("$.dinamicaAssociada.descritivo").value("Alteração para associar"))
                .andExpect(jsonPath("$.dinamicaAssociada.instrumento.id").value(pdm.toString()))
                .andExpect(jsonPath("$.dinamicaAssociada.instrumento.tipoInstrumento").value("PDM"));
        assertThat(jdbcTemplate.queryForObject("SELECT dinamica_associada_id FROM processo WHERE id = ?", UUID.class, processo))
                .isEqualTo(emCurso);
        assertThat(jdbcTemplate.queryForObject("SELECT dados_alteracao::text FROM auditoria WHERE id_ocorrencia = ? "
                + "AND acao = 'ASSOCIAR_DINAMICA'", String.class, processo.toString()))
                .contains("\"dinamicaAssociadaId\": \"" + emCurso + "\"").contains("\"dinamicaAssociadaIdAnterior\": null");
    }

    @Test
    void deveRecusarDinamicasForaDaListaInexistentesOuDeOutraEntidadeSemMudarNada() throws Exception {
        UUID pdm = instrumento("PDM", "PDM de Teste 1181", "1181");
        UUID emCurso = dinamica(pdm, "PDM:ALTERACAO", "Alteração associada", EstadoDinamica.EM_CURSO);
        UUID terminada = dinamica(pdm, "PDM:ALTERACAO", "Alteração terminada", EstadoDinamica.TERMINADA);
        UUID noutroConcelho = dinamica(instrumento("PDM", "PDM de Teste 1182", "1182"), "PDM:ALTERACAO", "Alteração",
                EstadoDinamica.EM_CURSO);
        UUID processo = processoDaRen("1181", "REN:DELIMITACAO", "AUTOR", "CCDR");
        UUID processoDeOutraEntidade = processoDaRen("1181", "REN:ALTERACAO", "OUTRA_CCDR", "OUTRA_CCDR");
        UUID dinamicaDeOutraEntidade = transacao.execute(status ->
                processoRepository.findById(processoDeOutraEntidade).orElseThrow().getDinamica().getId());
        gravar(processo, emCurso, "AUTOR", "CCDR").andExpect(status().isOk());
        long versao = versao(processo);

        for (UUID recusada : List.of(terminada, noutroConcelho, UUID.randomUUID(), dinamicaDeOutraEntidade)) {
            gravar(processo, recusada, "AUTOR", "CCDR")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DINAMICA_ASSOCIADA_INVALIDA"))
                    .andExpect(jsonPath("$.mensagem").value("A dinâmica indicada não está disponível para associação."))
                    .andExpect(jsonPath("$.campos[0].path").value("dinamicaAssociadaId"));
        }

        assertThat(versao(processo)).isEqualTo(versao);
        assertThat(jdbcTemplate.queryForObject("SELECT dinamica_associada_id FROM processo WHERE id = ?", UUID.class, processo))
                .isEqualTo(emCurso);
    }

    @Test
    void deveRecusarAAssociacaoNumProcessoDePdmEGravarSemOCampoComoHoje() throws Exception {
        UUID pdm = instrumento("PDM", "PDM de Teste 1191", "1191");
        UUID outraAlteracao = dinamica(pdm, "PDM:ALTERACAO", "Outra alteração", EstadoDinamica.EM_CURSO);
        UUID processo = processo(pdm, "PDM:ALTERACAO", "CAMARA", "CAMARA");

        gravar(processo, outraAlteracao, "CAMARA", "CAMARA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_ASSOCIADA_INVALIDA"));
        mockMvc.perform(put("/api/v1/processos/" + processo + "/dados-formulario").with(jwtDe("CAMARA", "CAMARA"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("versao", versao(processo), "sumario", "Rascunho"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/" + processo).with(jwtDe("CAMARA", "CAMARA")))
                .andExpect(jsonPath("$.aceitaDinamicaAssociada").value(false))
                .andExpect(jsonPath("$.sumario").value("Rascunho"));
    }

    @Test
    void deveRecusarASubmissaoComAAssociacaoEntretantoTerminadaENaoTravarASemAssociacao() throws Exception {
        UUID emCurso = dinamica(instrumento("PDM", "PDM de Teste 1201", "1201"), "PDM:ALTERACAO", "Alteração",
                EstadoDinamica.EM_CURSO);
        UUID associado = processoDaRen("1201", "REN:DELIMITACAO", "AUTOR", "CCDR");
        UUID semAssociacao = processoDaRen("1201", "REN:ALTERACAO", "AUTOR", "CCDR");
        gravar(associado, emCurso, "AUTOR", "CCDR").andExpect(status().isOk());
        terminar(emCurso);

        submeter(associado)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DINAMICA_ASSOCIADA_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("dinamicaAssociadaId"));
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM processo WHERE id = ?", String.class, associado))
                .isEqualTo(EstadoProcesso.EM_SUBMISSAO.name());

        submeter(semAssociacao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveGravarARespostaSimSemDinamicaEDevolveLaNoDetalhe() throws Exception {
        UUID processo = processoDaRen("1211", "REN:DELIMITACAO", "AUTOR", "CCDR");

        gravar(processo, null, "SIM", "AUTOR", "CCDR").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + processo).with(jwtDe("AUTOR", "CCDR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceitaDinamicaAssociada").value(true))
                .andExpect(jsonPath("$.dinamicaAssociadaId").doesNotExist())
                .andExpect(jsonPath("$.dadosFormulario",
                        org.hamcrest.Matchers.matchesPattern("\\{\"simultaneoComPmot\": ?\"SIM\"}")));
        assertThat(jdbcTemplate.queryForObject("SELECT dados_formulario ->> 'simultaneoComPmot' FROM processo WHERE id = ?",
                String.class, processo)).isEqualTo("SIM");

        gravar(processo, null, "NAO", "AUTOR", "CCDR").andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT dados_formulario ->> 'simultaneoComPmot' FROM processo WHERE id = ?",
                String.class, processo)).isEqualTo("NAO");
    }

    @Test
    void deveRecusarADinamicaComARespostaNaoOuSemRespostaSemMudarNada() throws Exception {
        UUID emCurso = dinamica(instrumento("PDM", "PDM de Teste 1221", "1221"), "PDM:ALTERACAO", "Alteração",
                EstadoDinamica.EM_CURSO);
        UUID processo = processoDaRen("1221", "REN:ALTERACAO", "AUTOR", "CCDR");
        long versao = versao(processo);

        for (String resposta : java.util.Arrays.asList("NAO", null)) {
            gravar(processo, emCurso, resposta, "AUTOR", "CCDR")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DINAMICA_ASSOCIADA_INVALIDA"))
                    .andExpect(jsonPath("$.campos[0].path").value("dinamicaAssociadaId"));
        }

        assertThat(versao(processo)).isEqualTo(versao);
        assertThat(jdbcTemplate.queryForObject("SELECT dinamica_associada_id FROM processo WHERE id = ?", UUID.class, processo))
                .isNull();
    }

    @Test
    void deveRecusarARespostaNumProcessoQueNaoPerguntaENumValorForaDeSimENao() throws Exception {
        UUID correcao = processoDaRen("1231", "REN:CORRECAO_MATERIAL_RETIFICACAO", "AUTOR", "CCDR");
        UUID delimitacao = processoDaRen("1231", "REN:DELIMITACAO", "AUTOR", "CCDR");

        gravar(correcao, null, "SIM", "AUTOR", "CCDR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("dadosFormulario.simultaneoComPmot"));
        gravar(delimitacao, null, "TALVEZ", "AUTOR", "CCDR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("dadosFormulario.simultaneoComPmot"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processo WHERE id IN (?, ?) AND dados_formulario IS NOT NULL",
                Long.class, correcao, delimitacao)).isZero();
    }

    @Test
    void deveBloquearARespostaDepoisDaPrimeiraSubmissao() throws Exception {
        UUID processo = processoDaRen("1241", "REN:DELIMITACAO", "AUTOR", "CCDR");
        gravar(processo, null, "SIM", "AUTOR", "CCDR").andExpect(status().isOk());
        jdbcTemplate.update("UPDATE processo SET data_submissao = now() WHERE id = ?", processo);

        mockMvc.perform(get("/api/v1/processos/" + processo).with(jwtDe("AUTOR", "CCDR")))
                .andExpect(jsonPath("$.camposBloqueados", org.hamcrest.Matchers.hasItem("dadosFormulario.simultaneoComPmot")));
        gravar(processo, null, "NAO", "AUTOR", "CCDR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[0].path").value("dadosFormulario.simultaneoComPmot"));
        gravar(processo, null, "SIM", "AUTOR", "CCDR").andExpect(status().isOk());
    }

    private ResultActions submeter(UUID processo) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processo + "/submeter").with(jwtDe("AUTOR", "CCDR"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(PedidosSubmissao.confirmado(versao(processo), "Resumo do ato.",
                        LocalDate.of(2026, 11, 10), "Norma legal", "TEXTO_INTEGRAL", null, null))));
    }

    /** Com uma Dinâmica, responde «SIM» à pergunta do PMOT, como o formulário. */
    private ResultActions gravar(UUID processo, UUID dinamicaAssociadaId, String utilizador, String entidade) throws Exception {
        return gravar(processo, dinamicaAssociadaId, dinamicaAssociadaId == null ? null : "SIM", utilizador, entidade);
    }

    private ResultActions gravar(UUID processo, UUID dinamicaAssociadaId, String simultaneoComPmot, String utilizador,
                                 String entidade) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", versao(processo));
        corpo.put("dinamicaAssociadaId", dinamicaAssociadaId);
        if (simultaneoComPmot != null) {
            corpo.put("dadosFormulario", Map.of("simultaneoComPmot", simultaneoComPmot));
        }
        return mockMvc.perform(put("/api/v1/processos/" + processo + "/dados-formulario").with(jwtDe(utilizador, entidade))
                .contentType("application/json").content(objectMapper.writeValueAsString(corpo)));
    }

    private long versao(UUID processo) {
        return jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, processo);
    }

    private static String associaveis(UUID processo) {
        return "/api/v1/processos/" + processo + "/dinamicas-associaveis";
    }

    private InstrumentoDinamicaConfig configuracao(String chave) {
        String[] partes = chave.split(":");
        return transacao.execute(status -> dinamicaConfigRepository.findAll().stream()
                .filter(c -> partes[0].equals(c.getTipoInstrumento().getCodigo())
                        && partes[1].equals(c.getTipoDinamica().getCodigo()))
                .findFirst().orElseThrow());
    }

    private UUID instrumento(String tipo, String designacao, String concelho) {
        return transacao.execute(status -> {
            TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.findAll().stream()
                    .filter(t -> tipo.equals(t.getCodigo())).findFirst().orElseThrow();
            Utilizador autor = utilizadorRepository.findById(utilizadores.get("AUTOR")).orElseThrow();
            Instrumento instrumento = instrumentoRepository.saveAndFlush(new Instrumento(tipoInstrumento,
                    "T284-" + NUMERO.incrementAndGet(), designacao, autor, autor));
            instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumento, concelho));
            return instrumento.getId();
        });
    }

    private UUID dinamica(UUID instrumento, String chave, String descritivo, EstadoDinamica estado) {
        return transacao.execute(status -> {
            Dinamica dinamica = new Dinamica(instrumentoRepository.findById(instrumento).orElseThrow(),
                    configuracoes.get(chave), descritivo,
                    utilizadorRepository.findById(utilizadores.get("AUTOR")).orElseThrow());
            dinamica.setEstado(estado);
            if (estado == EstadoDinamica.TERMINADA) {
                dinamica.setDataConclusao(Instant.parse("2026-11-10T10:00:00Z"));
            }
            return dinamicaRepository.saveAndFlush(dinamica).getId();
        });
    }

    private void terminar(UUID dinamica) {
        transacao.executeWithoutResult(status -> {
            Dinamica terminada = dinamicaRepository.findById(dinamica).orElseThrow();
            terminada.setEstado(EstadoDinamica.TERMINADA);
            terminada.setDataConclusao(Instant.parse("2026-11-12T10:00:00Z"));
        });
    }

    private UUID processoDaRen(String concelho, String chave, String utilizador, String entidade) {
        return processo(instrumento("REN", "REN de Teste " + NUMERO.get(), concelho), chave, utilizador, entidade);
    }

    private UUID processo(UUID instrumento, String chave, String utilizador, String entidade) {
        UUID dinamica = dinamica(instrumento, chave, "Dinâmica do processo", EstadoDinamica.EM_CURSO);
        return transacao.execute(status -> {
            Processo processo = new Processo(dinamicaRepository.findById(dinamica).orElseThrow(), procedimentos.get(chave),
                    utilizadorRepository.findById(utilizadores.get(utilizador)).orElseThrow(),
                    entidadeRepository.findById(entidades.get(entidade)).orElseThrow());
            processo.setTipoAto(tipoAtoRepository.findAll().get(0));
            return processoRepository.saveAndFlush(processo).getId();
        });
    }

    private RequestPostProcessor jwtDe(String utilizador, String entidade) {
        Entidade sessao = entidadeRepository.findById(entidades.get(entidade)).orElseThrow();
        return jwt().jwt(builder -> builder.claim(SUB, utilizadores.get(utilizador).toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("id", sessao.getId(), "tipo_entidade", sessao.getTipoEntidade().name(),
                        "nif", sessao.getNipc(), "nome", sessao.getDescricaoIncm(), "pcm", false,
                        "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "C", "descricao", "Parte C"))));
    }
}
