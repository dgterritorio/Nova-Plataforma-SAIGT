package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Associação dos Instrumentos à Suspensão e às Medidas Preventivas ({@code PUT /api/v1/dinamicas/{id}/instrumentos}),
 * num PostgreSQL migrado pelo {@code master.xml}, com os tipos admitidos pelas configurações semeadas.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class AssociacaoInstrumentosPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_SEC_ESTADO = "3001";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final String DINAMICAS = "/api/v1/dinamicas";
    private static final String ASSOCIACAO_INVALIDA = "ASSOCIACAO_INVALIDA";

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;

    @SpyBean
    private CatalogoFormularios catalogoFormularios;

    // --- substituição do conjunto ---

    @Test
    void aSuspensaoDoGovernoAssociaDoisInstrumentosEmVigorEOConjuntoEnviadoSubstituiOAnterior() throws Exception {
        Sessao governo = governo();
        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        UUID processo = criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        Instrumento pdm = novoInstrumento("PDM", true);
        Instrumento pu = novoInstrumento("PU", true);

        associar(suspensao, governo, pdm, pu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentos", hasSize(2)))
                .andExpect(jsonPath("$.instrumentos[*].id").value(containsInAnyOrder(id(pdm), id(pu))))
                .andExpect(jsonPath("$.instrumentos[*].tipoInstrumento").value(containsInAnyOrder("PDM", "PU")))
                .andExpect(jsonPath("$.instrumentos[*].designacao")
                        .value(containsInAnyOrder(pdm.getDesignacao(), pu.getDesignacao())));
        assertThat(associados(suspensao)).containsExactlyInAnyOrder(pdm.getId(), pu.getId());
        mockMvc.perform(get("/api/v1/processos/" + processo).with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentosAbrangidos[*].id").value(containsInAnyOrder(id(pdm), id(pu))));

        associar(suspensao, governo, pu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentos[*].id").value(containsInAnyOrder(id(pu))));
        assertThat(associados(suspensao)).containsExactly(pu.getId());
        mockMvc.perform(get("/api/v1/processos/" + processo).with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentosAbrangidos[*].id").value(containsInAnyOrder(id(pu))));

        associar(suspensao, governo).andExpect(status().isOk()).andExpect(jsonPath("$.instrumentos", empty()));
        assertThat(associados(suspensao)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auditoria WHERE tipo_ocorrencia = 'DINAMICA' "
                + "AND id_ocorrencia = ? AND acao = 'ASSOCIACAO_INSTRUMENTOS'", Integer.class, suspensao.toString()))
                .isEqualTo(3);
    }

    // --- regras dos Instrumentos ---

    @Test
    void umInstrumentoQueNaoEstaEmVigorDa400ENaoMudaAAssociacao() throws Exception {
        Sessao governo = governo();
        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        Instrumento emVigor = novoInstrumento("PDM", true);
        Instrumento revogado = novoInstrumento("PDM", false);
        associar(suspensao, governo, emVigor).andExpect(status().isOk());

        associar(suspensao, governo, emVigor, revogado)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(ASSOCIACAO_INVALIDA))
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoIds[1]"))
                .andExpect(jsonPath("$.campos[0].erro").value("Só se associam instrumentos em vigor."));
        assertThat(associados(suspensao)).containsExactly(emVigor.getId());

        associar(suspensao, governo, UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(ASSOCIACAO_INVALIDA));
        assertThat(associados(suspensao)).containsExactly(emVigor.getId());
    }

    @Test
    void aSuspensaoDaCcdrSoAdmitePlanosMunicipaisEIntermunicipais() throws Exception {
        Sessao ccdr = ccdr();
        UUID suspensao = iniciar(ccdr, "SUSPENSAO_CCDR");
        criarProcesso(suspensao, "SUSPENSAO_CCDR", "SUSPENSAO_CCDR", ccdr);
        Instrumento prot = novoInstrumento("PROT", true);

        associar(suspensao, ccdr, prot)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(ASSOCIACAO_INVALIDA))
                .andExpect(jsonPath("$.campos[0].erro").value("Esta dinâmica não admite instrumentos do tipo PROT."));
        assertThat(associados(suspensao)).isEmpty();

        Instrumento pdm = novoInstrumento("PDM", true);
        associar(suspensao, ccdr, pdm).andExpect(status().isOk());
        assertThat(associados(suspensao)).containsExactly(pdm.getId());

        Instrumento[] planos = Arrays.stream(new String[] {"PDM", "PDIM", "PU", "PUIM", "PP", "PPIM"})
                .map(tipo -> novoInstrumento(tipo, true)).toArray(Instrumento[]::new);
        associar(suspensao, ccdr, planos).andExpect(status().isOk()).andExpect(jsonPath("$.instrumentos", hasSize(6)));
    }

    @Test
    void aSuspensaoEAsMedidasPreventivasDoGovernoAdmitemInstrumentosDeQualquerTipo() throws Exception {
        Sessao governo = governo();
        Instrumento prot = novoInstrumento("PROT", true);
        Instrumento pdm = novoInstrumento("PDM", true);

        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        associar(suspensao, governo, prot, pdm).andExpect(status().isOk());
        assertThat(associados(suspensao)).containsExactlyInAnyOrder(prot.getId(), pdm.getId());

        UUID medidas = iniciar(governo, "MEDIDAS_PREVENTIVAS_GOVERNO");
        criarProcesso(medidas, "MEDIDAS_PREVENTIVAS_GOVERNO", "PUBLICACAO_MP_GOVERNO", governo);
        associar(medidas, governo, prot, pdm).andExpect(status().isOk());
        assertThat(associados(medidas)).containsExactlyInAnyOrder(prot.getId(), pdm.getId());
    }

    @Test
    void oMesmoInstrumentoDuasVezesNoConjuntoDa400() throws Exception {
        Sessao governo = governo();
        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        Instrumento pdm = novoInstrumento("PDM", true);

        associar(suspensao, governo, pdm, pdm)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(ASSOCIACAO_INVALIDA))
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoIds[1]"))
                .andExpect(jsonPath("$.campos[0].erro").value("O instrumento está repetido no conjunto."));
        assertThat(associados(suspensao)).isEmpty();
    }

    @Test
    void instrumentosDeOutraEntidadeEDeOutroMunicipioAssociamSeNoGovernoENaCcdr() throws Exception {
        Instrumento doPorto = novoInstrumento("PDM", true);
        jdbcTemplate.update("INSERT INTO instrumento_concelho (instrumento_id, municipio_codigo) VALUES (?, '1312')",
                doPorto.getId());

        Sessao governo = governo();
        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        associar(suspensao, governo, doPorto).andExpect(status().isOk());

        Sessao ccdr = ccdr();
        UUID daCcdr = iniciar(ccdr, "SUSPENSAO_CCDR");
        criarProcesso(daCcdr, "SUSPENSAO_CCDR", "SUSPENSAO_CCDR", ccdr);
        associar(daCcdr, ccdr, doPorto).andExpect(status().isOk());
        assertThat(associados(daCcdr)).containsExactly(doPorto.getId());
    }

    @Test
    void oInstrumentoAPartirDoQualADinamicaFoiIniciadaNaoSeDesassocia() throws Exception {
        Sessao ccdr = ccdr();
        Instrumento inicio = novoInstrumento("PDM", true);
        Instrumento outro = novoInstrumento("PU", true);
        UUID suspensao = dinamicaRepository.saveAndFlush(new Dinamica(inicio,
                instrumentoDinamicaConfigRepository.findById(dinamicaConfigId("PDM", "SUSPENSAO_CCDR")).orElseThrow(),
                "Suspensão a partir do PDM", utilizadorRepository.findById(ccdr.utilizadorId()).orElseThrow())).getId();
        criarProcesso(suspensao, "SUSPENSAO_CCDR", "SUSPENSAO_CCDR", ccdr);

        associar(suspensao, ccdr, outro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(ASSOCIACAO_INVALIDA));
        assertThat(associados(suspensao)).containsExactly(inicio.getId());
        associar(suspensao, ccdr, inicio, outro).andExpect(status().isOk());
        assertThat(associados(suspensao)).containsExactlyInAnyOrder(inicio.getId(), outro.getId());
    }

    // --- estado, visibilidade e autor ---

    @Test
    void naRatificacaoEnumaDinamicaDeUmSoInstrumentoAOperacaoDa409() throws Exception {
        Sessao governo = governo();
        UUID ratificacao = iniciar(governo, "RATIFICACAO");
        criarProcesso(ratificacao, "RATIFICACAO", "RATIFICACAO", governo);
        Instrumento pdm = novoInstrumento("PDM", true);

        associar(ratificacao, governo, pdm)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        assertThat(associados(ratificacao)).isEmpty();
    }

    @Test
    void semProcessoEmSubmissaoDoChamadorDa409EForaDaVisibilidadeDa404() throws Exception {
        Sessao governo = governo();
        UUID semProcessos = iniciar(governo, "SUSPENSAO_GOVERNO");
        Instrumento pdm = novoInstrumento("PDM", true);
        associar(semProcessos, governo, pdm)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        UUID processo = criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        jdbcTemplate.update("UPDATE processo SET estado = 'ENVIADO_DGT' WHERE id = ?", processo);
        associar(suspensao, governo, pdm)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        assertThat(associados(suspensao)).isEmpty();

        associar(suspensao, sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H"), pdm)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        associar(UUID.randomUUID(), governo, pdm).andExpect(status().isNotFound());
    }

    @Test
    void quemNaoEOAutorRecebe403EAAssociacaoNaoMuda() throws Exception {
        Sessao governo = governo();
        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        Instrumento pdm = novoInstrumento("PDM", true);
        associar(suspensao, governo, pdm).andExpect(status().isOk());
        Instrumento pu = novoInstrumento("PU", true);

        for (PerfilInterno perfil : PerfilInterno.values()) {
            mockMvc.perform(put(DINAMICAS + "/" + suspensao + "/instrumentos").with(interno(perfil))
                            .contentType("application/json").content(corpo(pu)))
                    .andExpect(status().isForbidden());
        }
        associar(suspensao, governo(), pu)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        assertThat(associados(suspensao)).containsExactly(pdm.getId());
    }

    @Test
    void semAListaDeInstrumentosDa400DeValidacao() throws Exception {
        Sessao governo = governo();
        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        mockMvc.perform(put(DINAMICAS + "/" + suspensao + "/instrumentos").with(governo.jwt())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    // --- apoio ao bloco do ecrã dos documentos ---

    @Test
    void oDetalheDizSeOChamadorAssociaEQuaisOsTiposAdmitidos() throws Exception {
        Sessao ccdr = ccdr();
        UUID suspensao = iniciar(ccdr, "SUSPENSAO_CCDR");
        UUID processo = criarProcesso(suspensao, "SUSPENSAO_CCDR", "SUSPENSAO_CCDR", ccdr);

        mockMvc.perform(get("/api/v1/processos/" + processo).with(ccdr.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associacaoInstrumentos").value("OPCIONAL"))
                .andExpect(jsonPath("$.podeAssociarInstrumentos").value(true))
                .andExpect(jsonPath("$.tiposInstrumentoAdmitidos[*].codigo")
                        .value(containsInAnyOrder("PDM", "PDIM", "PU", "PUIM", "PP", "PPIM")));
        mockMvc.perform(get("/api/v1/processos/" + processo).with(interno(PerfilInterno.TECNICO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeAssociarInstrumentos").value(false));

        Sessao governo = governo();
        UUID ratificacao = iniciar(governo, "RATIFICACAO");
        UUID daRatificacao = criarProcesso(ratificacao, "RATIFICACAO", "RATIFICACAO", governo);
        mockMvc.perform(get("/api/v1/processos/" + daRatificacao).with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associacaoInstrumentos").value("UM"))
                .andExpect(jsonPath("$.podeAssociarInstrumentos").value(false))
                .andExpect(jsonPath("$.tiposInstrumentoAdmitidos", empty()));

        jdbcTemplate.update("UPDATE processo SET estado = 'ENVIADO_DGT' WHERE id = ?", processo);
        mockMvc.perform(get("/api/v1/processos/" + processo).with(ccdr.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeAssociarInstrumentos").value(false));
    }

    @Test
    void aPesquisaDeInstrumentosAceitaVariosTiposEDevolveOsConcelhos() throws Exception {
        String designacao = "Pesquisa " + sufixo();
        Instrumento pdm = novoInstrumento("PDM", true, designacao);
        Instrumento pu = novoInstrumento("PU", true, designacao);
        novoInstrumento("PROT", true, designacao);
        novoInstrumento("PDM", false, designacao);
        jdbcTemplate.update("INSERT INTO instrumento_concelho (instrumento_id, municipio_codigo) VALUES (?, '1111'), (?, '1105')",
                pdm.getId(), pdm.getId());

        mockMvc.perform(get("/api/v1/instrumentos").with(governo().jwt())
                        .param("tipoInstrumentoId", String.valueOf(pdm.getTipoInstrumento().getId()))
                        .param("tipoInstrumentoId", String.valueOf(pu.getTipoInstrumento().getId()))
                        .param("emVigor", "true").param("designacao", designacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(id(pdm), id(pu))))
                .andExpect(jsonPath("$.content[?(@.id == '" + pdm.getId() + "')].concelhos[*]")
                        .value(containsInAnyOrder("1105", "1111")))
                .andExpect(jsonPath("$.content[?(@.id == '" + pu.getId() + "')].concelhos[*]").value(empty()));
    }

    // --- número de Instrumentos na submissão ---

    @Test
    void aSuspensaoDoGovernoSemInstrumentosNaoSeSubmeteEComUmAvanca() throws Exception {
        comFormularioNosProcedimentosSemIgt();
        Sessao governo = governo();
        UUID suspensao = iniciar(governo, "SUSPENSAO_GOVERNO");
        UUID processo = criarProcesso(suspensao, "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", governo);
        preencherFormulario(processo, governo);
        mockMvc.perform(get("/api/v1/processos/" + processo).with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false));

        submeter(processo, governo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Associe os instrumentos que esta dinâmica exige."))
                .andExpect(jsonPath("$.campos[0].path").value("instrumentos"));
        assertThat(estado(processo)).isEqualTo("EM_SUBMISSAO");

        associar(suspensao, governo, novoInstrumento("PDM", true)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/" + processo).with(governo.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(processo, governo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void asMedidasPreventivasDoGovernoSemInstrumentosTambemNaoSeSubmetemMesmoSemFormulario() throws Exception {
        Sessao governo = governo();
        UUID medidas = iniciar(governo, "MEDIDAS_PREVENTIVAS_GOVERNO");
        UUID processo = criarProcesso(medidas, "MEDIDAS_PREVENTIVAS_GOVERNO", "PUBLICACAO_MP_GOVERNO", governo);

        submeter(processo, governo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Associe os instrumentos que esta dinâmica exige."));
        assertThat(estado(processo)).isEqualTo("EM_SUBMISSAO");
    }

    @Test
    void aSuspensaoDaCcdrSemInstrumentosSubmeteSe() throws Exception {
        comFormularioNosProcedimentosSemIgt();
        Sessao ccdr = ccdr();
        UUID suspensao = iniciar(ccdr, "SUSPENSAO_CCDR");
        UUID processo = criarProcesso(suspensao, "SUSPENSAO_CCDR", "SUSPENSAO_CCDR", ccdr);
        preencherFormulario(processo, ccdr);

        submeter(processo, ccdr)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(associados(suspensao)).isEmpty();
    }

    @Test
    void asMedidasPreventivasDoMunicipioSemInstrumentosSubmetemSe() throws Exception {
        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM", "H");
        UUID medidas = iniciar(camara, "MEDIDAS_PREVENTIVAS");
        UUID processo = criarProcesso(medidas, "MEDIDAS_PREVENTIVAS", "PUBLICACAO_MP", camara);
        preencherFormulario(processo, camara);

        submeter(processo, camara)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(associados(medidas)).isEmpty();
    }

    // --- apoio ---

    private static final String CAMPOS_DO_FORMULARIO = "\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
            + java.time.LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\",\"publicarSuplemento\":false";

    /**
     * Os formulários destes Procedimentos ainda não existem: o da Informação Geral faz as vezes deles. Os documentos
     * obrigatórios dos seeds deixam de o ser: aqui só conta o número de Instrumentos associados.
     */
    private void comFormularioNosProcedimentosSemIgt() {
        jdbcTemplate.update("UPDATE documento_config SET obrigatorio = false WHERE instrumento_procedimento_config_id IN "
                + "(SELECT c.id FROM instrumento_procedimento_config c JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
                + "WHERE t.codigo IN ('PUBLICACAO_SUSPENSAO_GOVERNO', 'SUSPENSAO_CCDR'))");
        doReturn(Optional.of(FormularioFixo.INFORMACAO_GERAL)).when(catalogoFormularios).formularioDe(
                argThat(tipo -> tipo != null && List.of("PUBLICACAO_SUSPENSAO_GOVERNO", "SUSPENSAO_CCDR")
                        .contains(tipo.getCodigo())));
    }

    private void preencherFormulario(UUID processo, Sessao sessao) throws Exception {
        mockMvc.perform(put("/api/v1/processos/" + processo + "/dados-formulario").with(sessao.jwt())
                        .contentType("application/json").content("{\"versao\":" + versao(processo) + "," + CAMPOS_DO_FORMULARIO
                                + ",\"entidadeEmitente\":\"Entidade emitente\",\"elementosAfetados\":[\"PECAS_ESCRITAS\"]"
                                + (pedeLocalizacao(processo) ? ",\"localizacao\":{\"aplicavelTodaArea\":true}}" : "}")))
                .andExpect(status().isOk());
    }

    private ResultActions submeter(UUID processo, Sessao sessao) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processo + "/submeter").with(sessao.jwt())
                .contentType("application/json").content("{\"versao\":" + versao(processo) + "," + CAMPOS_DO_FORMULARIO
                        + "," + com.conkord.saigt.dto.instrumento.PedidosSubmissao.JSON_CONFIRMACOES + "}"));
    }

    private boolean pedeLocalizacao(UUID processo) {
        return !"NAO".equals(jdbcTemplate.queryForObject("SELECT ipc.pede_localizacao FROM processo p "
                + "JOIN instrumento_procedimento_config ipc ON ipc.id = p.instrumento_procedimento_config_id WHERE p.id = ?",
                String.class, processo));
    }

    private long versao(UUID processo) {
        return jdbcTemplate.queryForObject("SELECT versao FROM processo WHERE id = ?", Long.class, processo);
    }

    private String estado(UUID processo) {
        return jdbcTemplate.queryForObject("SELECT estado FROM processo WHERE id = ?", String.class, processo);
    }

    private ResultActions associar(UUID dinamica, Sessao sessao, Instrumento... instrumentos) throws Exception {
        return mockMvc.perform(put(DINAMICAS + "/" + dinamica + "/instrumentos").with(sessao.jwt())
                .contentType("application/json").content(corpo(instrumentos)));
    }

    private ResultActions associar(UUID dinamica, Sessao sessao, UUID instrumentoId) throws Exception {
        return mockMvc.perform(put(DINAMICAS + "/" + dinamica + "/instrumentos").with(sessao.jwt())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("instrumentoIds", List.of(instrumentoId)))));
    }

    private String corpo(Instrumento... instrumentos) throws Exception {
        return objectMapper.writeValueAsString(Map.of("instrumentoIds",
                Arrays.stream(instrumentos).map(Instrumento::getId).toList()));
    }

    private List<UUID> associados(UUID dinamica) {
        return jdbcTemplate.queryForList("SELECT instrumento_id FROM dinamica_instrumento WHERE dinamica_id = ?",
                UUID.class, dinamica);
    }

    private static String id(Instrumento instrumento) {
        return instrumento.getId().toString();
    }

    private Sessao governo() {
        return sessao(CODIGO_SEC_ESTADO, "SEC_ESTADO", "H");
    }

    private Sessao ccdr() {
        return sessao(CODIGO_CCDR_LVT, "CCDR_DR", "C");
    }

    private UUID iniciar(Sessao sessao, String tipoDinamica) throws Exception {
        String resposta = mockMvc.perform(post(DINAMICAS).with(sessao.jwt()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoDinamicaConfigId", dinamicaConfigId("PDM", tipoDinamica),
                                "descritivo", tipoDinamica.substring(0, 10) + " " + sufixo()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(ler(resposta).get("id").asText());
    }

    private RequestPostProcessor interno(PerfilInterno perfil) {
        String conta = "interno-" + UUID.randomUUID();
        Utilizador utilizador = new Utilizador(conta, conta + "@dgt.pt", "AD_DGT");
        utilizador.setPerfilInterno(perfil);
        String sub = utilizadorRepository.saveAndFlush(utilizador).getId().toString();
        return jwt().jwt(builder -> builder.claim(SUB, sub).claim("origem", "AD_DGT").claim("perfil_interno", perfil.name()));
    }

    private Instrumento novoInstrumento(String tipo, boolean emVigor) {
        return novoInstrumento(tipo, emVigor, tipo);
    }

    private Instrumento novoInstrumento(String tipo, boolean emVigor, String designacao) {
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Responsável", "responsavel-" + UUID.randomUUID() + "@teste-associacao.pt", "INCM"));
        String numero = tipo + "-" + sufixo();
        Instrumento instrumento = new Instrumento(tipoInstrumentoRepository.findAll().stream()
                .filter(t -> tipo.equals(t.getCodigo())).findFirst().orElseThrow(), numero, designacao + " " + numero,
                utilizador, utilizador);
        instrumento.setEmVigor(emVigor);
        return instrumentoRepository.saveAndFlush(instrumento);
    }

    private UUID criarProcesso(UUID dinamica, String tipoDinamica, String tipoProcedimento, Sessao sessao) throws Exception {
        long tipoAto = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato", Long.class);
        String resposta = mockMvc.perform(post(DINAMICAS + "/" + dinamica + "/processos").with(sessao.jwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoProcedimentoConfigId", procedimentoConfigId("PDM", tipoDinamica, tipoProcedimento),
                                "tipoAtoId", tipoAto))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID processo = UUID.fromString(ler(resposta).get("id").asText());
        // Sem ficheiros nos testes: os itens obrigatórios levam observações para a submissão passar (FR-137).
        jdbcTemplate.update("INSERT INTO processo_documento_observacao (processo_id, documento_config_id, observacoes) "
                + "SELECT p.id, d.id, 'Sem ficheiro no teste.' FROM processo p JOIN documento_config d "
                + "ON d.instrumento_procedimento_config_id = p.instrumento_procedimento_config_id "
                + "WHERE p.id = ? AND d.obrigatorio AND d.ativo", processo);
        return processo;
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }

    private long procedimentoConfigId(String tipoInstrumento, String tipoDinamica, String tipoProcedimento) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "WHERE ipc.instrumento_dinamica_config_id = ? AND tp.codigo = ?", Long.class,
                dinamicaConfigId(tipoInstrumento, tipoDinamica), tipoProcedimento);
    }

    private JsonNode ler(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId, Long entidadeId) {
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade, String letraParte) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-associacao.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + codigoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", letraParte, "descricao", "Parte " + letraParte));
        return new Sessao(jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", entidade)), utilizadorId, entidadeId);
    }
}
