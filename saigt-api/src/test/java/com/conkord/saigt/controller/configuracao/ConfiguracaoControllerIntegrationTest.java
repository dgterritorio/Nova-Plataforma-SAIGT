package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Na base real porque a verificação de `EM_USO` consulta o `jsonb` das condições. A base é partilhada, sem
 * rollback, por toda a suite: cada teste usa códigos únicos.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracaoControllerIntegrationTest extends PostgresTestcontainersBase {

    private static final String BASE = "/api/v1/configuracao/";
    private static final List<String> RECURSOS = List.of("tipos-instrumento", "tipos-dinamica", "tipos-procedimento");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired
    private InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    @Autowired
    private InstrumentoRepository instrumentoRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private RegraRepository regraRepository;
    @Autowired
    private CondicaoRepository condicaoRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private static String codigoUnico() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static RequestPostProcessor comoMaster() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private static RequestPostProcessor comoPerfil(String perfil) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil));
    }

    private static RequestPostProcessor comoExterno() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("nif", "999888777", "nome", "Entidade Teste", "pcm", false)));
    }

    private String corpo(String codigo, String nome, boolean ativo) throws Exception {
        return objectMapper.writeValueAsString(Map.of("codigo", codigo, "nome", nome, "ativo", ativo));
    }

    private String corpoInstrumento(String codigo, String nome, String categoria, boolean ativo) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("codigo", codigo, "nome", nome, "categoria", categoria, "ativo", ativo));
    }

    private TipoProcedimento garantirTipoProcedimento(String codigo, String nome) {
        return tipoProcedimentoRepository.findAll().stream().filter(t -> t.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, nome)));
    }

    @Test
    void masterDeveCriarTipoDinamicaEListaloComAtivo() throws Exception {
        String codigo = codigoUnico();

        mockMvc.perform(post(BASE + "tipos-dinamica").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(codigo, "Dinâmica de teste", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.ativo").value(true));

        mockMvc.perform(get(BASE + "tipos-dinamica").param("ativo", "true").param("size", "100").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.codigo=='" + codigo + "')]").isNotEmpty());
    }

    @Test
    void codigoJaExistenteDeveDar400ComCaminhoCodigo() throws Exception {
        for (String recurso : RECURSOS) {
            String codigo = codigoUnico();
            String corpoCriacao = recurso.equals("tipos-instrumento")
                    ? corpoInstrumento(codigo, "Tipo", "PLANO", true) : corpo(codigo, "Tipo", true);
            mockMvc.perform(post(BASE + recurso).with(comoMaster()).contentType(APPLICATION_JSON).content(corpoCriacao))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(BASE + recurso).with(comoMaster()).contentType(APPLICATION_JSON).content(corpoCriacao))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("codigo"));
        }
    }

    @Test
    void putComCodigoDeOutroRegistoDeveDar400() throws Exception {
        TipoDinamica a = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "A"));
        TipoDinamica b = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "B"));

        mockMvc.perform(put(BASE + "tipos-dinamica/" + b.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(a.getCodigo(), "B", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
    }

    @Test
    void pedidoInvalidoDeveDar400ComOCampoAssinalado() throws Exception {
        mockMvc.perform(post(BASE + "tipos-dinamica").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo("minusculas", "Nome", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
        mockMvc.perform(post(BASE + "tipos-dinamica").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo("COM ESPACOS", "Nome", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
        mockMvc.perform(post(BASE + "tipos-dinamica").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo("A".repeat(41), "Nome", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
        mockMvc.perform(post(BASE + "tipos-dinamica").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(codigoUnico(), "", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("nome"));
        mockMvc.perform(post(BASE + "tipos-dinamica").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(codigoUnico(), "N".repeat(201), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("nome"));
        mockMvc.perform(post(BASE + "tipos-instrumento").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpoInstrumento(codigoUnico(), "Nome", "ARU", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("categoria"));
        mockMvc.perform(post(BASE + "tipos-instrumento").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpoInstrumento("A".repeat(21), "Nome", "PLANO", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("codigo"));
    }

    @Test
    void perfisQueNaoSaoMasterDevemDar403EmTodasAsOperacoes() throws Exception {
        TipoDinamica existente = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Existente"));

        for (RequestPostProcessor ator : List.of(comoPerfil("TECNICO"), comoPerfil("VISITANTE"), comoExterno())) {
            for (String recurso : RECURSOS) {
                mockMvc.perform(get(BASE + recurso).with(ator))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
            }
            mockMvc.perform(post(BASE + "tipos-dinamica").with(ator).contentType(APPLICATION_JSON)
                            .content(corpo(codigoUnico(), "Nome", true)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
            mockMvc.perform(put(BASE + "tipos-dinamica/" + existente.getId()).with(ator).contentType(APPLICATION_JSON)
                            .content(corpo(existente.getCodigo(), "Outro", true)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }
    }

    @Test
    void pedidoSemSessaoDeveDar401() throws Exception {
        for (String recurso : RECURSOS) {
            mockMvc.perform(get(BASE + recurso)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post(BASE + "tipos-dinamica").contentType(APPLICATION_JSON)
                        .content(corpo(codigoUnico(), "Nome", true)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tipoDeInstrumentoComConfiguracoesNaoMudaDeCodigo() throws Exception {
        TipoInstrumento tipo = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigoUnico(), "Tipo em uso", "PLANO"));
        TipoDinamica dinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Dinâmica"));
        instrumentoDinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipo, dinamica));

        mockMvc.perform(put(BASE + "tipos-instrumento/" + tipo.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpoInstrumento(codigoUnico(), "Tipo em uso", "PLANO", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));

        assertThat(tipoInstrumentoRepository.findById(tipo.getId()).orElseThrow().getCodigo()).isEqualTo(tipo.getCodigo());
    }

    @Test
    void tipoDeInstrumentoComInstrumentosNaoMudaDeCategoria() throws Exception {
        TipoInstrumento tipo = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigoUnico(), "Tipo com instrumentos", "PLANO"));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor", "autor" + UUID.randomUUID() + "@empresa.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(new Instrumento(tipo, "1",
                "2026/CFG/" + codigoUnico(), autor, autor));

        mockMvc.perform(put(BASE + "tipos-instrumento/" + tipo.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpoInstrumento(tipo.getCodigo(), "Tipo com instrumentos", "PROGRAMA", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));

        assertThat(tipoInstrumentoRepository.findById(tipo.getId()).orElseThrow().getCategoria()).isEqualTo("PLANO");
        assertThat(instrumento.getTipoInstrumento().getId()).isEqualTo(tipo.getId());
    }

    @Test
    void tipoDeInstrumentoSemUsoMudaCodigoCategoriaENome() throws Exception {
        TipoInstrumento tipo = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigoUnico(), "Novo", "PLANO"));
        String novoCodigo = codigoUnico();

        mockMvc.perform(put(BASE + "tipos-instrumento/" + tipo.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpoInstrumento(novoCodigo, "Novo nome", "SRUP", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(novoCodigo))
                .andExpect(jsonPath("$.nome").value("Novo nome"))
                .andExpect(jsonPath("$.categoria").value("SRUP"))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void tipoDeDinamicaCitadoEmCondicaoNaoMudaDeCodigo() throws Exception {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigoUnico(), "Tipo", "PLANO"));
        TipoDinamica dinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Citada"));
        InstrumentoDinamicaConfig outraConfig = instrumentoDinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento,
                        tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Outra"))));
        Regra regra = regraRepository.saveAndFlush(Regra.paraDinamica(outraConfig));
        condicaoRepository.saveAndFlush(new Condicao(regra, "OCORRENCIA_EM_ESTADO",
                "{\"codigo\":\"" + dinamica.getCodigo() + "\"}"));

        mockMvc.perform(put(BASE + "tipos-dinamica/" + dinamica.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(codigoUnico(), "Citada", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EM_USO"));
    }

    @Test
    void tipoDeProcedimentoNovoSemConfiguracoesMudaCodigoENome() throws Exception {
        TipoProcedimento tipo = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigoUnico(), "Antigo"));
        String novoCodigo = codigoUnico();

        mockMvc.perform(put(BASE + "tipos-procedimento/" + tipo.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(novoCodigo, "Novo nome", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(novoCodigo))
                .andExpect(jsonPath("$.nome").value("Novo nome"))
                .andExpect(jsonPath("$.formulario").doesNotExist());
    }

    @Test
    void desativarTipoDeProcedimentoDeveListaloEmAtivoFalseEIndicarOFormulario() throws Exception {
        TipoProcedimento decisao = garantirTipoProcedimento("DECISAO_INICIO", "Decisão de Início");
        String novoCodigo = codigoUnico();
        mockMvc.perform(post(BASE + "tipos-procedimento").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(novoCodigo, "Sem formulário", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formulario").doesNotExist());

        mockMvc.perform(put(BASE + "tipos-procedimento/" + decisao.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo("DECISAO_INICIO", decisao.getNome(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.formulario").value("INFORMACAO_GERAL"));

        mockMvc.perform(get(BASE + "tipos-procedimento").param("ativo", "false").param("size", "100").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.codigo=='DECISAO_INICIO')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.codigo=='DECISAO_INICIO')].formulario").value("INFORMACAO_GERAL"));

        String pagina = mockMvc.perform(get(BASE + "tipos-procedimento").param("size", "100").with(comoMaster()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode novo = StreamSupport.stream(objectMapper.readTree(pagina).get("content").spliterator(), false)
                .filter(no -> novoCodigo.equals(no.get("codigo").asText())).findFirst().orElseThrow();
        assertThat(novo.path("formulario").isMissingNode() || novo.get("formulario").isNull()).isTrue();
    }

    @Test
    void tamanhoDePaginaDeveSerLimitadoACem() throws Exception {
        mockMvc.perform(get(BASE + "tipos-dinamica").param("size", "500").with(comoMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void putDeIdInexistenteDeveDar404() throws Exception {
        mockMvc.perform(put(BASE + "tipos-dinamica/999999999").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(codigoUnico(), "Nome", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void postEPutDevemFicarAuditadosSemDadosPessoais() throws Exception {
        String codigo = codigoUnico();
        String resposta = mockMvc.perform(post(BASE + "tipos-dinamica").with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(codigo, "Auditada", true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(put(BASE + "tipos-dinamica/" + id).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(codigo, "Auditada e renomeada", true)))
                .andExpect(status().isOk());

        List<Auditoria> linhas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "tipos-dinamica:" + id);
        assertThat(linhas).extracting(Auditoria::getAcao).containsExactlyInAnyOrder("CRIAR", "ATUALIZAR");
        assertThat(linhas).allSatisfy(linha -> assertThat(linha.getUtilizadorId()).isNotNull());
        Auditoria atualizacao = linhas.stream().filter(l -> l.getAcao().equals("ATUALIZAR")).findFirst().orElseThrow();
        assertThat(atualizacao.getDadosAlteracao()).contains("nome").doesNotContain("codigo");
    }

    @Test
    void putSemAlteracoesNaoDeveAuditar() throws Exception {
        TipoDinamica tipo = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Igual"));

        mockMvc.perform(put(BASE + "tipos-dinamica/" + tipo.getId()).with(comoMaster()).contentType(APPLICATION_JSON)
                        .content(corpo(tipo.getCodigo(), "Igual", true)))
                .andExpect(status().isOk());

        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "tipos-dinamica:" + tipo.getId())).isEmpty();
    }
}
