package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Na base real: `condicao.parametros` é `jsonb`. A base é partilhada, sem rollback, por toda a suite: cada teste cria
 * as suas configurações e filtra por elas.
 */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracaoRegrasControllerIntegrationTest extends PostgresTestcontainersBase {

    private static final String REGRAS = "/api/v1/configuracao/regras";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
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
    private RegraRepository regraRepository;
    @Autowired
    private CondicaoRepository condicaoRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private static String codigoUnico() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static RequestPostProcessor comoPerfil(String perfil) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil));
    }

    private static RequestPostProcessor comoMaster() {
        return comoPerfil("MASTER");
    }

    private void garantirTipoDinamica(String codigo) {
        if (!tipoDinamicaRepository.existsByCodigo(codigo)) {
            tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigo, codigo));
        }
    }

    private InstrumentoDinamicaConfig novaConfigDinamica() {
        garantirTipoDinamica("REVISAO");
        return dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(
                tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoUnico(), "Tipo", "PROGRAMA")),
                tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Dinâmica"))));
    }

    private InstrumentoProcedimentoConfig novoProcedimento(InstrumentoDinamicaConfig dinamica, int ordem) {
        TipoProcedimento tipo = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigoUnico(), "Proc"));
        return procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamica, tipo, ordem, "PRIMEIRA"));
    }

    private static Map<String, Object> condicao(String avaliador, Map<String, Object> parametros, int grupo, boolean ativo) {
        Map<String, Object> condicao = new HashMap<>();
        condicao.put("avaliador", avaliador);
        condicao.put("parametros", parametros);
        condicao.put("grupo", grupo);
        condicao.put("ativo", ativo);
        return condicao;
    }

    private static Map<String, Object> revisaoEmCursoNaoExiste() {
        return condicao("OCORRENCIA_NAO_EXISTE", Map.of("tipo_ocorrencia", "DINAMICA", "codigo", "REVISAO",
                "estado", "EM_CURSO"), 1, true);
    }

    private static Map<String, Object> emVigor() {
        return condicao("INSTRUMENTO_EM_VIGOR", Map.of(), 1, true);
    }

    private static Map<String, Object> regra(Long dinamicaConfigId, Long procedimentoConfigId, boolean ativo,
                                             List<Map<String, Object>> condicoes) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("instrumentoDinamicaConfigId", dinamicaConfigId);
        corpo.put("instrumentoProcedimentoConfigId", procedimentoConfigId);
        corpo.put("operador", "AND");
        corpo.put("substituiSequenciamento", false);
        corpo.put("descricao", "Regra de teste");
        corpo.put("ativo", ativo);
        corpo.put("condicoes", condicoes);
        return corpo;
    }

    private String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    private JsonNode criar(Map<String, Object> corpo) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long contarRegrasECondicoes() {
        return jdbcTemplate.queryForObject("SELECT (SELECT count(*) FROM regra) + (SELECT count(*) FROM condicao)", Long.class);
    }

    @Test
    void masterCriaUmaRegraDeElegibilidadeDeDinamicaComACondicaoEFicaAuditada() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();

        JsonNode criada = criar(regra(dinamica.getId(), null, true, List.of(revisaoEmCursoNaoExiste())));

        assertThat(criada.get("id").asLong()).isPositive();
        assertThat(criada.get("instrumentoDinamicaConfigId").asLong()).isEqualTo(dinamica.getId());
        assertThat(criada.get("instrumentoProcedimentoConfigId").isNull()).isTrue();
        assertThat(criada.get("operador").asText()).isEqualTo("AND");
        assertThat(criada.get("substituiSequenciamento").asBoolean()).isFalse();
        assertThat(criada.get("condicoes")).hasSize(1);
        JsonNode condicao = criada.get("condicoes").get(0);
        assertThat(condicao.get("avaliador").asText()).isEqualTo("OCORRENCIA_NAO_EXISTE");
        assertThat(condicao.get("parametros")).isEqualTo(objectMapper.valueToTree(
                Map.of("tipo_ocorrencia", "DINAMICA", "codigo", "REVISAO", "estado", "EM_CURSO")));
        assertThat(condicao.get("grupo").asInt()).isEqualTo(1);

        List<Auditoria> linhas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "regras:" + criada.get("id").asLong());
        assertThat(linhas).extracting(Auditoria::getAcao).containsExactly("CRIAR");
        JsonNode dados = objectMapper.readTree(linhas.get(0).getDadosAlteracao());
        assertThat(dados.get("operador").asText()).isEqualTo("AND");
        assertThat(dados.get("condicoes").get(0).get("avaliador").asText()).isEqualTo("OCORRENCIA_NAO_EXISTE");
        assertThat(dados.get("condicoes").get(0).get("parametros").get("estado").asText()).isEqualTo("EM_CURSO");
        assertThat(dados.has("descricao")).isFalse();
        assertThat(linhas.get(0).getUtilizadorId()).isNotNull();
    }

    @Test
    void comOsDoisAlvosOuNenhumDa400RegraMalConfiguradaNosDoisCampos() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        InstrumentoProcedimentoConfig procedimento = novoProcedimento(dinamica, 1);
        long antes = contarRegrasECondicoes();

        for (Map<String, Object> corpo : List.of(regra(dinamica.getId(), procedimento.getId(), true, List.of(emVigor())),
                regra(null, null, true, List.of(emVigor())))) {
            mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON).content(json(corpo)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.mensagem").value("Regra mal configurada."))
                    .andExpect(jsonPath("$.campos[*].path").value(containsInAnyOrder(
                            "instrumentoDinamicaConfigId", "instrumentoProcedimentoConfigId")));
        }
        assertThat(contarRegrasECondicoes()).isEqualTo(antes);
    }

    @Test
    void umaConfiguracaoInexistenteDa400NoCampo() throws Exception {
        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(999_999_999L, null, true, List.of(emVigor())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoDinamicaConfigId"));
        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(null, 999_999_999L, true, List.of(emVigor())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoProcedimentoConfigId"));
    }

    @Test
    void umAvaliadorDesconhecidoDa400NoCaminhoDaCondicao() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();

        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(dinamica.getId(), null, true, List.of(condicao("QUALQUER", Map.of(), 1, true))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("condicoes[0].avaliador"))
                .andExpect(jsonPath("$.campos[0].erro").value("Avaliador desconhecido."));
    }

    @Test
    void parametrosInvalidosDao400ComCadaMotivoNaCondicaoENadaEGravado() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        long antes = contarRegrasECondicoes();
        List<Map<String, Object>> condicoes = List.of(
                condicao("OCORRENCIA_EM_ESTADO", Map.of("tipo_ocorrencia", "DINAMICA", "codigo", "REVISAO"), 1, true),
                condicao("OCORRENCIA_EM_ESTADO", Map.of("tipo_ocorrencia", "DINAMICA", "codigo", "XPTO",
                        "estado", "EM_CURSO"), 1, true),
                condicao("OCORRENCIA_EM_ESTADO", Map.of("tipo_ocorrencia", "DINAMICA", "codigo", "REVISAO",
                        "estado", "EM_CURSO", "extra", "x"), 2, false));

        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(dinamica.getId(), null, true, condicoes))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos", hasSize(3)))
                .andExpect(jsonPath("$.campos[0].path").value("condicoes[0].parametros"))
                .andExpect(jsonPath("$.campos[0].erro").value("estado obrigatório"))
                .andExpect(jsonPath("$.campos[1].path").value("condicoes[1].parametros"))
                .andExpect(jsonPath("$.campos[1].erro").value("codigo inexistente em tipo_dinamica: XPTO"))
                .andExpect(jsonPath("$.campos[2].path").value("condicoes[2].parametros"))
                .andExpect(jsonPath("$.campos[2].erro").value("chave desconhecida: extra"));

        assertThat(contarRegrasECondicoes()).isEqualTo(antes);
    }

    @Test
    void substituirOSequenciamentoNumaRegraDeDinamicaDa400NoCampo() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        Map<String, Object> corpo = regra(dinamica.getId(), null, true, List.of(emVigor()));
        corpo.put("substituiSequenciamento", true);

        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON).content(json(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Regra mal configurada."))
                .andExpect(jsonPath("$.campos[0].path").value("substituiSequenciamento"));

        InstrumentoProcedimentoConfig procedimento = novoProcedimento(dinamica, 1);
        Map<String, Object> deProcedimento = regra(null, procedimento.getId(), true, List.of(emVigor()));
        deProcedimento.put("substituiSequenciamento", true);
        assertThat(criar(deProcedimento).get("substituiSequenciamento").asBoolean()).isTrue();
    }

    @Test
    void umProcedimentoComRegraMesmoInativaNaoRecebeOutra() throws Exception {
        InstrumentoProcedimentoConfig procedimento = novoProcedimento(novaConfigDinamica(), 1);
        criar(regra(null, procedimento.getId(), false, List.of()));

        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(null, procedimento.getId(), true, List.of(emVigor())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("instrumentoProcedimentoConfigId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este Procedimento já tem uma regra; edite a existente."));
    }

    @Test
    void umaDinamicaPodeTerVariasRegras() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        criar(regra(dinamica.getId(), null, true, List.of(emVigor())));
        criar(regra(dinamica.getId(), null, true, List.of(revisaoEmCursoNaoExiste())));

        mockMvc.perform(get(REGRAS).param("instrumentoDinamicaConfigId", String.valueOf(dinamica.getId())).with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void umaRegraAtivaSemCondicoesAtivasDa400() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();

        for (List<Map<String, Object>> condicoes : List.of(List.<Map<String, Object>>of(),
                List.of(condicao("INSTRUMENTO_EM_VIGOR", Map.of(), 1, false)))) {
            mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                            .content(json(regra(dinamica.getId(), null, true, condicoes))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("Indique pelo menos uma condição ativa."))
                    .andExpect(jsonPath("$.campos[0].path").value("condicoes"));
        }
    }

    @Test
    void oPutSubstituiAListaDesativaAQueNaoVemECriaANovaEOGetDevolveAsTres() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        JsonNode criada = criar(regra(dinamica.getId(), null, true, List.of(emVigor(), revisaoEmCursoNaoExiste())));
        long id = criada.get("id").asLong();
        long primeira = criada.get("condicoes").get(0).get("id").asLong();
        long segunda = criada.get("condicoes").get(1).get("id").asLong();

        Map<String, Object> mantida = emVigor();
        mantida.put("id", primeira);
        Map<String, Object> nova = condicao("OCORRENCIA_NAO_EXISTE", Map.of("tipo_ocorrencia", "DINAMICA",
                "codigo", "REVISAO"), 2, true);
        Map<String, Object> corpo = regra(dinamica.getId(), null, true, List.of(mantida, nova));
        corpo.put("operador", "OR");

        mockMvc.perform(put(REGRAS + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON).content(json(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operador").value("OR"))
                .andExpect(jsonPath("$.condicoes", hasSize(3)));

        assertThat(condicaoRepository.findById(segunda).orElseThrow().isAtivo()).isFalse();
        assertThat(condicaoRepository.findById(primeira).orElseThrow().isAtivo()).isTrue();

        JsonNode pagina = objectMapper.readTree(mockMvc.perform(get(REGRAS)
                        .param("instrumentoDinamicaConfigId", String.valueOf(dinamica.getId())).with(comoMaster()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode condicoes = pagina.get("content").get(0).get("condicoes");
        assertThat(condicoes).hasSize(3);
        List<String> resumo = new ArrayList<>();
        condicoes.forEach(c -> resumo.add(c.get("grupo").asInt() + ":" + c.get("avaliador").asText() + ":" + c.get("ativo").asBoolean()));
        assertThat(resumo).containsExactly("1:INSTRUMENTO_EM_VIGOR:true", "1:OCORRENCIA_NAO_EXISTE:false",
                "2:OCORRENCIA_NAO_EXISTE:true");

        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO", "regras:" + id))
                .extracting(Auditoria::getAcao).containsExactlyInAnyOrder("CRIAR", "ATUALIZAR");
        Auditoria atualizacao = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO", "regras:" + id)
                .stream().filter(l -> l.getAcao().equals("ATUALIZAR")).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(atualizacao.getDadosAlteracao()).get("condicoes")).hasSize(3);
    }

    @Test
    void oPutComOutroAlvoOuComACondicaoDeOutraRegraDa400ENaoMudaNada() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        InstrumentoDinamicaConfig outra = novaConfigDinamica();
        JsonNode criada = criar(regra(dinamica.getId(), null, true, List.of(emVigor())));
        JsonNode daOutra = criar(regra(outra.getId(), null, true, List.of(emVigor())));
        long id = criada.get("id").asLong();

        mockMvc.perform(put(REGRAS + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(outra.getId(), null, true, List.of(emVigor())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("O alvo da regra não pode ser alterado; desative-a e crie outra."));

        Map<String, Object> alheia = emVigor();
        alheia.put("id", daOutra.get("condicoes").get(0).get("id").asLong());
        mockMvc.perform(put(REGRAS + "/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(dinamica.getId(), null, true, List.of(alheia)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("condicoes[0].id"));

        assertThat(regraRepository.findById(id).orElseThrow().getInstrumentoDinamicaConfig().getId()).isEqualTo(dinamica.getId());
        assertThat(condicaoRepository.findById(daOutra.get("condicoes").get(0).get("id").asLong()).orElseThrow()
                .getRegra().getId()).isEqualTo(daOutra.get("id").asLong());
    }

    @Test
    void oPutDeUmaRegraInexistenteDa404() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        mockMvc.perform(put(REGRAS + "/999999999").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(dinamica.getId(), null, true, List.of(emVigor())))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void oFiltroDinamicaDosProcedimentosDevolveSoAsRegrasDosProcedimentosDessaDinamica() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        long p1 = criar(regra(null, novoProcedimento(dinamica, 1).getId(), true, List.of(emVigor()))).get("id").asLong();
        long p2 = criar(regra(null, novoProcedimento(dinamica, 2).getId(), false, List.of(emVigor()))).get("id").asLong();
        criar(regra(dinamica.getId(), null, true, List.of(emVigor())));
        criar(regra(null, novoProcedimento(novaConfigDinamica(), 1).getId(), true, List.of(emVigor())));

        mockMvc.perform(get(REGRAS).param("dinamicaDosProcedimentosId", String.valueOf(dinamica.getId())).with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(p1))
                .andExpect(jsonPath("$.content[1].id").value(p2))
                .andExpect(jsonPath("$.content[0].instrumentoDinamicaConfigId").doesNotExist());
        mockMvc.perform(get(REGRAS).param("dinamicaDosProcedimentosId", String.valueOf(dinamica.getId()))
                        .param("ativo", "false").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(p2));
    }

    @Test
    void asCondicoesDaPaginaCarregamNumaSoConsultaQualquerQueSejaONumeroDeRegras() throws Exception {
        InstrumentoDinamicaConfig uma = novaConfigDinamica();
        criar(regra(null, novoProcedimento(uma, 1).getId(), true, List.of(emVigor(), revisaoEmCursoNaoExiste())));
        InstrumentoDinamicaConfig cinco = novaConfigDinamica();
        for (int ordem = 1; ordem <= 5; ordem++) {
            criar(regra(null, novoProcedimento(cinco, ordem).getId(), true, List.of(emVigor(), revisaoEmCursoNaoExiste())));
        }

        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        mockMvc.perform(get(REGRAS).param("dinamicaDosProcedimentosId", String.valueOf(uma.getId())).with(comoMaster()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        long consultasUma = estatisticas.getPrepareStatementCount();
        estatisticas.clear();
        mockMvc.perform(get(REGRAS).param("dinamicaDosProcedimentosId", String.valueOf(cinco.getId())).with(comoMaster()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[4].condicoes", hasSize(2)));
        long consultasCinco = estatisticas.getPrepareStatementCount();
        long carregadasCinco = estatisticas.getEntityStatistics(Condicao.class.getName()).getLoadCount();

        assertThat(consultasCinco).isEqualTo(consultasUma);
        assertThat(carregadasCinco).isEqualTo(10);
    }

    @Test
    void perfisQueNaoSaoMasterRecebem403EmTodosOsEndpointsESemSessao401() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        long id = criar(regra(dinamica.getId(), null, true, List.of(emVigor()))).get("id").asLong();
        String corpo = json(regra(dinamica.getId(), null, false, List.of(emVigor())));
        long antes = contarRegrasECondicoes();

        for (RequestPostProcessor ator : List.of(comoPerfil("TECNICO"), comoPerfil("VISITANTE"))) {
            mockMvc.perform(get(REGRAS).with(ator))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
            mockMvc.perform(post(REGRAS).with(ator).contentType(APPLICATION_JSON).content(corpo))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
            mockMvc.perform(put(REGRAS + "/" + id).with(ator).contentType(APPLICATION_JSON).content(corpo))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }
        mockMvc.perform(get(REGRAS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(REGRAS).contentType(APPLICATION_JSON).content(corpo)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(REGRAS + "/" + id).contentType(APPLICATION_JSON).content(corpo)).andExpect(status().isUnauthorized());

        assertThat(contarRegrasECondicoes()).isEqualTo(antes);
        assertThat(regraRepository.findById(id).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void pedidoMalFormadoDa400NoCampo() throws Exception {
        InstrumentoDinamicaConfig dinamica = novaConfigDinamica();
        Map<String, Object> operador = regra(dinamica.getId(), null, true, List.of(emVigor()));
        operador.put("operador", "XOR");
        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON).content(json(operador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("operador"));

        mockMvc.perform(post(REGRAS).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(json(regra(dinamica.getId(), null, true, List.of(condicao("INSTRUMENTO_EM_VIGOR", Map.of(), 0, true))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("condicoes[0].grupo"));

        mockMvc.perform(get(REGRAS).param("size", "500").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }
}
