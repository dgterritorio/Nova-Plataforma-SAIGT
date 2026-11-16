package com.conkord.saigt.db;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.regras.AvaliadorRegistoValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O seed dos Programas contra o PostgreSQL migrado pelo {@code master.xml}: a configuração semeada e o que uma CIM, uma
 * Câmara e a DGT (sessões de entidade com o tipo na claim) veem dela pelos endpoints.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("docker")
class SeedProgramasPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String URL_INSTRUMENTOS = "/api/v1/instrumentos";
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final String CODIGO_CCDR_LVT = "8001";
    private static final List<String> CONCELHOS_SINTRA_E_CASCAIS = List.of("1111", "1105");
    private static final List<String> PROGRAMAS_COM_ELABORACAO = List.of("PS", "PROT", "PEOT", "PIMOT");
    private static final List<String> DINAMICAS_SEM_ELABORACAO = List.of("REVISAO", "ALTERACAO", "ALTERACAO_ADAPTACAO",
            "CORRECAO_MATERIAL", "REVOGACAO");
    /** As Dinâmicas que se iniciam sem IGT abrangem também os Programas, mas não são deste seed. */
    /** Os atos pontuais vêm de um seed posterior, por tipo de Procedimento. */
    private static final String SEM_OS_ATOS_PONTUAIS = " AND NOT ipc.ato_pontual";
    private static final String SEM_AS_SEM_IGT = " AND idc.tipo_dinamica_id NOT IN (SELECT id FROM tipo_dinamica WHERE inicio_sem_igt)";
    private static final String DOS_PROGRAMAS = "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo IN ('PNPOT', 'PS', 'PROT', 'PEOT', 'PIMOT')"
            + SEM_AS_SEM_IGT + SEM_OS_ATOS_PONTUAIS;

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
    private TransactionTemplate transacao;

    @Autowired
    private AvaliadorRegistoValidator avaliadorRegistoValidator;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Test
    void deveSemearPnpotPsEPimotComoProgramasEManterOIdEONomeDoProtEDoPeot() {
        assertThat(linhas("SELECT codigo || ':' || nome || ':' || categoria || ':' || ativo || ':' || coalesce(snit_codigo, '-') "
                + "|| ':' || coalesce(ambito_territorial, '-') FROM tipo_instrumento "
                + "WHERE codigo IN ('PNPOT', 'PS', 'PIMOT') ORDER BY codigo"))
                .containsExactly("PIMOT:Programa Intermunicipal de Ordenamento do Território:PROGRAMA:true:52:INTERMUNICIPAL",
                        "PNPOT:Programa Nacional da Política de Ordenamento do Território:PROGRAMA:true:17:-",
                        "PS:Programa Setorial:PROGRAMA:true:46:-");
        assertThat(linhas("SELECT id || ':' || codigo || ':' || nome || ':' || categoria FROM tipo_instrumento "
                + "WHERE codigo IN ('PROT', 'PEOT') ORDER BY id"))
                .containsExactly("4:PROT:Programa Regional de Ordenamento do Território:PROGRAMA",
                        "5:PEOT:Programa Especial de Ordenamento do Território:PROGRAMA");
    }

    @Test
    void oPnpotDeveTerCincoDinamicasSemElaboracaoEOsOutrosProgramasSeis() {
        assertThat(dinamicas("PNPOT")).containsExactlyElementsOf(DINAMICAS_SEM_ELABORACAO);
        List<String> comElaboracao = new ArrayList<>(List.of("ELABORACAO"));
        comElaboracao.addAll(DINAMICAS_SEM_ELABORACAO);
        for (String programa : PROGRAMAS_COM_ELABORACAO) {
            assertThat(dinamicas(programa)).as(programa).containsExactlyElementsOf(comElaboracao);
        }
        assertThat(contar("SELECT count(*) FROM instrumento_dinamica_config idc "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "WHERE ti.codigo IN ('PNPOT', 'PS', 'PROT', 'PEOT', 'PIMOT') AND NOT idc.ativo")).isZero();
    }

    @Test
    void osProcedimentosDevemSeguirAOrdemEASerieDaTabelaPorPrograma() {
        List<String> comuns = List.of(
                "REVISAO:1:DECISAO_INICIO", "REVISAO:2:ABERTURA_DISCUSSAO_PUBLICA", "REVISAO:3:REVISAO",
                "ALTERACAO:1:DECISAO_INICIO", "ALTERACAO:2:ABERTURA_DISCUSSAO_PUBLICA", "ALTERACAO:3:ALTERACAO",
                "ALTERACAO_ADAPTACAO:1:ALTERACAO_ADAPTACAO", "CORRECAO_MATERIAL:1:CORRECAO_MATERIAL", "REVOGACAO:1:REVOGACAO");
        List<String> elaboracaoDoGoverno = List.of("ELABORACAO:1:DECISAO_INICIO", "ELABORACAO:2:ABERTURA_DISCUSSAO_PUBLICA",
                "ELABORACAO:3:ELABORACAO");
        List<String> elaboracaoDoPimot = List.of("ELABORACAO:1:DECISAO_INICIO", "ELABORACAO:2:CONSTITUICAO_CC",
                "ELABORACAO:3:ABERTURA_DISCUSSAO_PUBLICA", "ELABORACAO:4:ELABORACAO");

        assertThat(procedimentos("PNPOT")).hasSize(9).containsExactlyElementsOf(comSerie(comuns, "PNPOT"));
        for (String programa : List.of("PS", "PROT", "PEOT")) {
            List<String> esperados = new ArrayList<>(elaboracaoDoGoverno);
            esperados.addAll(comuns);
            assertThat(procedimentos(programa)).as(programa).hasSize(12).containsExactlyElementsOf(comSerie(esperados, programa));
        }
        List<String> pimot = new ArrayList<>(elaboracaoDoPimot);
        pimot.addAll(comuns);
        assertThat(procedimentos("PIMOT")).hasSize(13).containsExactlyElementsOf(comSerie(pimot, "PIMOT"));

        assertThat(linhas("SELECT DISTINCT ipc.obrigatorio || ':' || ipc.ativo || ':' || ipc.ato_pontual || ':' "
                + "|| ipc.tamanho_maximo_processo FROM instrumento_procedimento_config ipc " + DOS_PROGRAMAS))
                .containsExactly("true:true:false:5368709120");
        assertThat(linhas("SELECT DISTINCT tp.codigo FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id " + DOS_PROGRAMAS + " AND ipc.repetivel"))
                .containsExactly("ABERTURA_DISCUSSAO_PUBLICA");
        assertThat(linhas("SELECT ti.codigo || ':' || tp.codigo FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id " + DOS_PROGRAMAS + " AND ipc.visivel_na_dinamica"))
                .containsExactly("PIMOT:CONSTITUICAO_CC");
        assertThat(contar("SELECT count(*) FROM instrumento_procedimento_config ipc " + DOS_PROGRAMAS)).isEqualTo(58);
    }

    @Test
    void asPermissoesDevemSerAsDaTabelaPorProgramaComAPcmSoNosProcedimentosDaPrimeiraSerie() {
        Map<String, String> entidades = Map.of("PNPOT", "DGT/true,SEC_ESTADO/true", "PS", "APA/true,DGT/true,ICNF/true,SEC_ESTADO/true",
                "PROT", "CCDR_DR/true,DGT/true,SEC_ESTADO/false", "PEOT", "APA/true,DGPC/true,DGT/true,ICNF/true,SEC_ESTADO/true",
                "PIMOT", "CIM/true,DGT/true");
        List<String> porProcedimento = linhas("SELECT ti.codigo || ':' || tp.codigo || ':' "
                + "|| string_agg(pa.tipo_entidade || '/' || pa.ativo, ',' ORDER BY pa.tipo_entidade) "
                + "FROM instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id " + DOS_PROGRAMAS
                + " AND pa.tipo_entidade <> 'PCM' GROUP BY ipc.id, ti.codigo, tp.codigo ORDER BY ipc.id");

        assertThat(porProcedimento).hasSize(58).allSatisfy(linha -> {
            String[] partes = linha.split(":");
            String esperado = "PIMOT".equals(partes[0]) && "CONSTITUICAO_CC".equals(partes[1])
                    ? "CCDR_DR/true,DGT/true" : entidades.get(partes[0]);
            assertThat(partes[2]).as(linha).isEqualTo(esperado);
        });
        assertThat(contar("SELECT count(*) FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = pa.instrumento_procedimento_config_id " + DOS_PROGRAMAS + " AND pa.tipo_entidade <> 'PCM'"))
                .isEqualTo(188);
        // A linha PCM vem do seed posterior da 1.ª série: uma por Procedimento de 1.ª série, nenhuma nos outros.
        assertThat(contar("SELECT count(*) FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = pa.instrumento_procedimento_config_id " + DOS_PROGRAMAS + " AND pa.tipo_entidade = 'PCM'"))
                .isEqualTo(contar("SELECT count(*) FROM instrumento_procedimento_config ipc " + DOS_PROGRAMAS
                        + " AND ipc.serie_dr = 'PRIMEIRA'"));
        assertThat(contar("SELECT count(*) FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = pa.instrumento_procedimento_config_id " + DOS_PROGRAMAS + " AND pa.tipo_entidade = 'PCM' "
                + "AND ipc.serie_dr <> 'PRIMEIRA'")).isZero();
    }

    @Test
    void asRegrasECondicoesDosProgramasDevemSerValidasParaOArranque() {
        List<String> semElaboracao = List.of(
                "REVISAO:AND:false:true:Requer programa em vigor; só 1 Revisão em curso:INSTRUMENTO_EM_VIGOR{}1true;"
                        + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"REVISAO\", \"estado\": \"EM_CURSO\", \"tipo_ocorrencia\": \"DINAMICA\"}1true",
                "ALTERACAO:AND:false:true:Requer programa em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                "ALTERACAO_ADAPTACAO:AND:false:true:Requer programa em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                "CORRECAO_MATERIAL:AND:false:true:Requer programa em vigor:INSTRUMENTO_EM_VIGOR{}1true",
                "REVOGACAO:AND:false:true:Requer programa em vigor; Revogação é única:INSTRUMENTO_EM_VIGOR{}1true;"
                        + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"REVOGACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}1true");
        List<String> comElaboracao = new ArrayList<>(List.of("ELABORACAO:AND:false:true:Elaboração é única:"
                + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"ELABORACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}1true"));
        comElaboracao.addAll(semElaboracao);

        assertThat(regras("PNPOT")).containsExactlyElementsOf(semElaboracao);
        for (String programa : PROGRAMAS_COM_ELABORACAO) {
            assertThat(regras(programa)).as(programa).containsExactlyElementsOf(comElaboracao);
        }
        assertThatCode(() -> avaliadorRegistoValidator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void aCimVeEPodeCriarOPimotENaoOPnpotEACamaraRecebe403NoProt() throws Exception {
        Sessao cim = sessao(CODIGO_CAMARA_DE_SINTRA, "CIM");
        Sessao camara = sessao(CODIGO_CAMARA_DE_SINTRA, "CM");

        mockMvc.perform(get("/api/v1/tipos-instrumento").with(cim.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo").value(hasItem("PIMOT")));

        JsonNode permissoes = objectMapper.readTree(mockMvc.perform(get("/api/v1/sessao/me/permissoes").with(cim.jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<Long> permitidos = StreamSupport.stream(permissoes.get("procedimentosPermitidos").spliterator(), false)
                .map(JsonNode::asLong).toList();
        assertThat(permitidos).containsAll(idsDosProcedimentos("PIMOT", "tp.codigo <> 'CONSTITUICAO_CC'"))
                .doesNotContainAnyElementsOf(idsDosProcedimentos("PNPOT", "true"));

        int instrumentosAntes = contar("SELECT count(*) FROM instrumento");
        mockMvc.perform(post(URL_INSTRUMENTOS).with(cim.jwt()).contentType("application/json")
                        .content(pedido("PNPOT", List.of("1111"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(post(URL_INSTRUMENTOS).with(camara.jwt()).contentType("application/json")
                        .content(pedido("PROT", List.of("1111"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        assertThat(contar("SELECT count(*) FROM instrumento")).isEqualTo(instrumentosAntes);

        criar(cim, "PIMOT", CONCELHOS_SINTRA_E_CASCAIS);
        assertThat(contar("SELECT count(*) FROM instrumento")).isEqualTo(instrumentosAntes + 1);
    }

    @Test
    void umPnpotNuncaOfereceAElaboracaoEEmVigorOfereceAsOutrasCinco() throws Exception {
        Sessao dgt = sessao(CODIGO_CCDR_LVT, "DGT");
        UUID pnpot = criar(dgt, "PNPOT", List.of("1111"));
        String disponivel = URL_INSTRUMENTOS + "/" + pnpot + "/disponivel";

        mockMvc.perform(get(disponivel).with(dgt.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis").isEmpty());

        jdbcTemplate.update("UPDATE instrumento SET em_vigor = true WHERE id = ?", pnpot);

        // A DGT também pode iniciar as Dinâmicas do Governo, que abrangem qualquer plano ou programa em vigor.
        List<String> emVigor = new ArrayList<>(DINAMICAS_SEM_ELABORACAO);
        emVigor.addAll(List.of("SUSPENSAO_GOVERNO", "MEDIDAS_PREVENTIVAS_GOVERNO"));
        mockMvc.perform(get(disponivel).with(dgt.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(containsInAnyOrder(emVigor.toArray())));
    }

    @Test
    void umPimotNovoSoOfereceAElaboracaoQueComecaPelaDecisaoDeInicio() throws Exception {
        Sessao cim = sessao(CODIGO_CAMARA_DE_SINTRA, "CIM");
        UUID pimot = criar(cim, "PIMOT", CONCELHOS_SINTRA_E_CASCAIS);
        String disponivel = URL_INSTRUMENTOS + "/" + pimot + "/disponivel";

        mockMvc.perform(get(disponivel).with(cim.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis[*].tipoDinamica").value(contains("ELABORACAO")));

        transacao.executeWithoutResult(estado -> dinamicaRepository.saveAndFlush(new Dinamica(
                instrumentoRepository.findById(pimot).orElseThrow(),
                dinamicaConfigRepository.findById(dinamicaConfigId("PIMOT", "ELABORACAO")).orElseThrow(),
                "Elaboração do PIMOT", utilizadorRepository.findById(cim.utilizadorId()).orElseThrow())));

        mockMvc.perform(get(disponivel).with(cim.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasDisponiveis").isEmpty())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].tipoDinamica").value("ELABORACAO"))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[*].tipoProcedimento")
                        .value(contains("DECISAO_INICIO")))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].serieDr").value("SEGUNDA_H"));
        assertThat(linhas("SELECT tp.codigo FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.instrumento_dinamica_config_id = ? "
                + SEM_OS_ATOS_PONTUAIS + " ORDER BY ipc.ordem", dinamicaConfigId("PIMOT", "ELABORACAO")))
                .containsExactly("DECISAO_INICIO", "CONSTITUICAO_CC", "ABERTURA_DISCUSSAO_PUBLICA", "ELABORACAO");
    }

    @Test
    void aDecisaoDeInicioDaElaboracaoDeUmPsTemOTextoDoAtoNasDuasCaixasEDuasPendencias() throws Exception {
        Sessao dgt = sessao(CODIGO_CCDR_LVT, "DGT");
        long tipoAto = jdbcTemplate.queryForObject("SELECT min(id) FROM tipo_ato", Long.class);
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS + "/elaboracao").with(dgt.jwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId("PS"),
                                "designacao", "PS de teste " + UUID.randomUUID(), "concelhos", List.of("1111"),
                                "tipoAtoId", tipoAto))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String processo = objectMapper.readTree(resposta).get("processo").get("id").asText();

        mockMvc.perform(get("/api/v1/processos/" + processo + "/estrutura-documental").with(dgt.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[*].separador").value(contains("Texto do Ato")))
                .andExpect(jsonPath("$.separadores[*].publicacao[*].codigo").value(contains("TEXTO_ATO_PUB")))
                .andExpect(jsonPath("$.separadores[*].deposito[*].codigo").value(contains("TEXTO_ATO_DEP")))
                .andExpect(jsonPath("$.separadores..obrigatorio").value(everyItem(is(true))))
                .andExpect(jsonPath("$.pendencias", hasSize(2)));
    }

    private record Sessao(RequestPostProcessor jwt, UUID utilizadorId) {
    }

    private Sessao sessao(String codigoEntidade, String tipoEntidade) {
        Long entidadeId = jdbcTemplate.queryForObject("SELECT id FROM entidade WHERE codigo = ?", Long.class, codigoEntidade);
        String nipc = jdbcTemplate.queryForObject("SELECT nipc FROM entidade WHERE id = ?", String.class, entidadeId);
        UUID utilizadorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO utilizador (id, email, origem_autenticacao) VALUES (?, ?, 'INCM')",
                utilizadorId, "utilizador-" + utilizadorId + "@teste-programas.pt");
        Map<String, Object> entidade = Map.of("id", entidadeId, "tipo_entidade", tipoEntidade, "nif", nipc,
                "nome", "Entidade de teste " + tipoEntidade, "pcm", false, "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 7_000_000_000_000_001L, "letra", "H", "descricao", "Parte H"));
        return new Sessao(jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString())
                .claim("origem", "INCM").claim("entidade", entidade)), utilizadorId);
    }

    private String pedido(String tipo, List<String> concelhos) throws Exception {
        return objectMapper.writeValueAsString(Map.of("tipoInstrumentoId", tipoInstrumentoId(tipo),
                "designacao", tipo + " de teste " + UUID.randomUUID(), "concelhos", concelhos));
    }

    private UUID criar(Sessao quem, String tipo, List<String> concelhos) throws Exception {
        String resposta = mockMvc.perform(post(URL_INSTRUMENTOS).with(quem.jwt()).contentType("application/json")
                        .content(pedido(tipo, concelhos)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private List<String> dinamicas(String programa) {
        return linhas("SELECT td.codigo FROM instrumento_dinamica_config idc "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "WHERE ti.codigo = ? AND idc.ativo AND idc.associacao_instrumentos = 'UM' ORDER BY idc.id", programa);
    }

    private List<String> procedimentos(String programa) {
        return linhas("SELECT td.codigo || ':' || ipc.ordem || ':' || tp.codigo || ':' || ipc.serie_dr "
                + "FROM instrumento_procedimento_config ipc "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ti.codigo = ?" + SEM_AS_SEM_IGT + SEM_OS_ATOS_PONTUAIS
                + " ORDER BY idc.id, ipc.ordem", programa);
    }

    /** Programas do Governo: 1.ª série, exceto a Adaptação (2.ª C). PIMOT: 2.ª série H, exceto a CC (2.ª C). */
    private static List<String> comSerie(List<String> procedimentos, String programa) {
        return procedimentos.stream().map(procedimento -> {
            String serie;
            if ("PIMOT".equals(programa)) {
                serie = procedimento.endsWith(":CONSTITUICAO_CC") ? "SEGUNDA_C" : "SEGUNDA_H";
            } else {
                serie = procedimento.endsWith(":ALTERACAO_ADAPTACAO") ? "SEGUNDA_C" : "PRIMEIRA";
            }
            return procedimento + ":" + serie;
        }).toList();
    }

    private List<String> regras(String programa) {
        return linhas("SELECT td.codigo || ':' || r.operador || ':' || r.substitui_sequenciamento || ':' || r.ativo || ':' "
                + "|| r.descricao || ':' || (SELECT string_agg(c.avaliador || c.parametros::text || c.grupo || c.ativo, ';' ORDER BY c.id) "
                + "FROM condicao c WHERE c.regra_id = r.id) FROM regra r "
                + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "WHERE ti.codigo = ?" + SEM_AS_SEM_IGT + " ORDER BY r.id", programa);
    }

    private List<Long> idsDosProcedimentos(String programa, String filtro) {
        return jdbcTemplate.queryForList("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = ?" + SEM_AS_SEM_IGT + " AND " + filtro, Long.class, programa);
    }

    private long tipoInstrumentoId(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM tipo_instrumento WHERE codigo = ?", Long.class, codigo);
    }

    private long dinamicaConfigId(String tipoInstrumento, String tipoDinamica) {
        return jdbcTemplate.queryForObject("SELECT c.id FROM instrumento_dinamica_config c "
                + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                + "WHERE t.codigo = ? AND d.codigo = ?", Long.class, tipoInstrumento, tipoDinamica);
    }

    private List<String> linhas(String consulta, Object... argumentos) {
        return jdbcTemplate.queryForList(consulta, String.class, argumentos);
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
