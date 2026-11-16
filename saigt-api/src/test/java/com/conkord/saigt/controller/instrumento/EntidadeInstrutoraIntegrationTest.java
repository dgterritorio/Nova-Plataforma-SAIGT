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
import com.conkord.saigt.persistence.core.TipoAtoTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntidadeInstrutoraIntegrationTest {

    private static final String MENSAGEM_EM_FALTA = "Indique a entidade instrutora.";
    private static final String INSTRUTORA = "Comissão de Coordenação e Desenvolvimento Regional de Teste";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
    private PermissaoAcaoRepository permissaoAcaoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    /** Sem itens documentais, com elementos afetados e entidade emitente: só a entidade instrutora pode travar a submissão. */
    private Cenario criarCenario(String codigo, String categoria, String entidadeInstrutora, boolean submetido) {
        return criarCenario(codigo, categoria, entidadeInstrutora, submetido, true);
    }

    private Cenario criarCenario(String codigo, String categoria, String entidadeInstrutora, boolean submetido,
                                 boolean entidadeInstrutoraObrigatoria) {
        TipoInstrumento novoTipo = new TipoInstrumento("INSTR-" + codigo, "Instrumento " + codigo, categoria);
        novoTipo.setEntidadeInstrutoraObrigatoria(entidadeInstrutoraObrigatoria);
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(novoTipo);
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-INSTR-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoComFormulario(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5095" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-instr.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/INSTR/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Entidade emitente de teste");
        processo.setEntidadeInstrutora(entidadeInstrutora);
        if (submetido) {
            processo.setDataSubmissao(Instant.now());
        }
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor(), cenario.entidade());
    }

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    private ResultActions gravar(Cenario cenario, String campoInstrutora, RequestPostProcessor sessao) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"],\"entidadeEmitente\":\"Entidade emitente de teste\","
                + "\"entidadeInstrutora\":" + campoInstrutora + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(sessao)
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions gravar(Cenario cenario, String campoInstrutora) throws Exception {
        return gravar(cenario, campoInstrutora, jwtDoAutor(cenario));
    }

    private ResultActions submeter(Cenario cenario, RequestPostProcessor sessao) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(sessao)
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)));
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private static String texto(String valor) {
        return "\"" + valor + "\"";
    }

    @Test
    void deveGravarAEntidadeInstrutoraDeUmProgramaSemEspacosNasPontasEDevolveLaComACategoria() throws Exception {
        Cenario cenario = criarCenario("4001", "PROGRAMA", null, false);

        gravar(cenario, texto("  " + INSTRUTORA + "  ")).andExpect(status().isOk());

        assertThat(gravado(cenario).getEntidadeInstrutora()).isEqualTo(INSTRUTORA);
        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidadeInstrutora").value(INSTRUTORA))
                .andExpect(jsonPath("$.categoriaInstrumento").value("PROGRAMA"));
    }

    @Test
    void deveGravarAEntidadeInstrutoraEmBrancoComoNula() throws Exception {
        Cenario cenario = criarCenario("4002", "PROGRAMA", INSTRUTORA, false);

        gravar(cenario, texto("   ")).andExpect(status().isOk());

        assertThat(gravado(cenario).getEntidadeInstrutora()).isNull();
        detalhe(cenario).andExpect(jsonPath("$.entidadeInstrutora").doesNotExist());
    }

    @Test
    void naoDeveGravarAEntidadeInstrutoraEnviadaParaUmPlano() throws Exception {
        Cenario cenario = criarCenario("4003", "PLANO", null, false);

        gravar(cenario, texto(INSTRUTORA)).andExpect(status().isOk());

        assertThat(gravado(cenario).getEntidadeInstrutora()).isNull();
        detalhe(cenario)
                .andExpect(jsonPath("$.entidadeInstrutora").doesNotExist())
                .andExpect(jsonPath("$.categoriaInstrumento").value("PLANO"));
    }

    @Test
    void deveRecusarASubmissaoDeUmProgramaSemEntidadeInstrutoraSemMudarOEstado() throws Exception {
        Cenario cenario = criarCenario("4004", "PROGRAMA", null, false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario, jwtDoAutor(cenario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeInstrutora"))
                .andExpect(jsonPath("$.campos[0].erro").value(MENSAGEM_EM_FALTA));

        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveIndicarAsDuasEntidadesEmFaltaNaMesmaResposta() throws Exception {
        Cenario cenario = criarCenario("4005", "PROGRAMA", null, false);
        Processo processo = gravado(cenario);
        processo.setEntidadeEmitente(null);
        processoRepository.saveAndFlush(processo);

        submeter(cenario, jwtDoAutor(cenario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(org.hamcrest.Matchers.contains("entidadeInstrutora", "entidadeEmitente")));
    }

    @Test
    void deveSubmeterUmProgramaComEntidadeInstrutora() throws Exception {
        Cenario cenario = criarCenario("4006", "PROGRAMA", INSTRUTORA, false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveSubmeterSemEntidadeInstrutoraUmProgramaEmQueElaNaoEObrigatoria() throws Exception {
        Cenario cenario = criarCenario("4014", "PROGRAMA", null, false, false);

        detalhe(cenario)
                .andExpect(jsonPath("$.entidadeInstrutoraObrigatoria").value(false))
                .andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void naoDeveExigirAEntidadeInstrutoraNumPlano() throws Exception {
        Cenario cenario = criarCenario("4007", "PLANO", null, false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarUmaEntidadeInstrutoraComMaisDe500CaracteresEAceitar500() throws Exception {
        Cenario cenario = criarCenario("4008", "PROGRAMA", INSTRUTORA, false);

        gravar(cenario, texto("x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeInstrutora"));
        assertThat(gravado(cenario).getEntidadeInstrutora()).isEqualTo(INSTRUTORA);

        gravar(cenario, texto("i".repeat(500))).andExpect(status().isOk());
        assertThat(gravado(cenario).getEntidadeInstrutora()).hasSize(500);
    }

    @Test
    void deveResponder404AUmUtilizadorDeOutraEntidadeAntesDeValidarAGravacaoOuASubmissao() throws Exception {
        Cenario cenario = criarCenario("4009", "PROGRAMA", null, false);
        Entidade outra = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5096" + "4009", "Câmara alheia"));
        Utilizador intruso = utilizadorRepository.saveAndFlush(new Utilizador("Intruso", "intruso4009@cm-alheia.pt", "INCM"));

        gravar(cenario, texto(INSTRUTORA), jwtDe(intruso, outra)).andExpect(status().isNotFound());
        submeter(cenario, jwtDe(intruso, outra)).andExpect(status().isNotFound());

        Processo depois = gravado(cenario);
        assertThat(depois.getEntidadeInstrutora()).isNull();
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveRecusarMudarAEntidadeInstrutoraDeUmProgramaDepoisDaPrimeiraSubmissao() throws Exception {
        Cenario cenario = criarCenario("4010", "PROGRAMA", INSTRUTORA, true);

        gravar(cenario, texto("Outra entidade"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeInstrutora"));
        gravar(cenario, texto(" " + INSTRUTORA)).andExpect(status().isOk());

        assertThat(gravado(cenario).getEntidadeInstrutora()).isEqualTo(INSTRUTORA);
    }

    @Test
    void deveIgnorarAEntidadeInstrutoraEnviadaParaUmPlanoJaSubmetido() throws Exception {
        Cenario cenario = criarCenario("4011", "PLANO", null, true);

        gravar(cenario, texto(INSTRUTORA)).andExpect(status().isOk());

        assertThat(gravado(cenario).getEntidadeInstrutora()).isNull();
    }

    @Test
    void cadaTipoDeInstrumentoDeveTrazerACategoria() throws Exception {
        Cenario programa = criarCenario("4012", "PROGRAMA", null, false);
        criarCenario("4013", "PLANO", null, false);

        String corpo = mockMvc.perform(get("/api/v1/tipos-instrumento").with(jwtDoAutor(programa)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode tipos = objectMapper.readTree(corpo);
        assertThat(tipos).isNotEmpty();
        assertThat(tipos).allSatisfy(tipo -> assertThat(tipo.path("categoria").asText()).isIn("PLANO", "PROGRAMA", "SRUP"));
        assertThat(tipos).filteredOn(tipo -> tipo.path("codigo").asText().equals("INSTR-4012"))
                .singleElement().satisfies(tipo -> {
                    assertThat(tipo.path("categoria").asText()).isEqualTo("PROGRAMA");
                    assertThat(tipo.path("entidadeInstrutoraObrigatoria").asBoolean()).isTrue();
                });
        assertThat(tipos).filteredOn(tipo -> tipo.path("codigo").asText().equals("INSTR-4013"))
                .singleElement().satisfies(tipo -> assertThat(tipo.path("categoria").asText()).isEqualTo("PLANO"));
    }
}
