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
import static org.hamcrest.Matchers.containsString;
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
class PublicacaoAlteracaoIntegrationTest {

    private static final String ENTIDADE_EMITENTE = "Camara Municipal de Teste";
    private static final String PECAS_GRAFICAS = "[\"PECAS_GRAFICAS\"]";
    private static final String PECAS_ESCRITAS = "[\"PECAS_ESCRITAS\"]";
    private static final String PECAS_GRAFICAS_E_ESCRITAS = "[\"PECAS_GRAFICAS\",\"PECAS_ESCRITAS\"]";
    private static final String MSG_GEOCONFORM = "Numa dinâmica com GeoConform, a alteração afeta sempre as Peças Gráficas.";
    private static final String MSG_PLANTA_NOVA_EM_FALTA = "Indique se a alteração introduz alguma planta nova.";
    private static final String MSG_SEM_PERGUNTA = "Este procedimento não pergunta se a alteração introduz uma planta nova.";
    private static final String MSG_EXIGE_REGULAMENTO =
            "Introduzir uma planta nova exige alteração ao regulamento: assinale as Peças Escritas.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

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

    private record Cenario(Processo processo, Utilizador autor, Utilizador outro, Entidade entidade) {
    }

    private record Parametros(String codigoProcedimento, String codigoInstrumento, Boolean comGeoconform,
                              List<String> elementosAfetados, String dadosFormulario, boolean submetido) {
    }

    /** O `codigo` é único na BD partilhada pelos testes: um tipo de Procedimento do seed é reutilizado. */
    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Sem itens documentais e com os dados INCM completos: só as regras do formulário podem travar a submissão. */
    private Cenario criarCenario(String sufixo, Parametros parametros) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(parametros.codigoInstrumento() + "-PUBALT-" + sufixo, "Instrumento " + sufixo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-PUBALT-" + sufixo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento(parametros.codigoProcedimento()), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5097" + sufixo, "Câmara " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cm-pubalt.pt", "INCM"));
        Utilizador outro = utilizadorRepository.saveAndFlush(
                new Utilizador("Outro " + sufixo, "outro" + sufixo + "@cm-pubalt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PUBALT/" + sufixo, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração", autor);
        dinamica.setComGeoconform(parametros.comGeoconform());
        dinamica = dinamicaRepository.saveAndFlush(dinamica);

        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(parametros.elementosAfetados());
        processo.setEntidadeEmitente(ENTIDADE_EMITENTE);
        processo.setDadosFormulario(parametros.dadosFormulario());
        if (parametros.submetido()) {
            processo.setDataSubmissao(Instant.now());
        }
        return new Cenario(processoRepository.saveAndFlush(processo), autor, outro, entidade);
    }

    private Cenario alteracao(String sufixo, String codigoInstrumento, Boolean comGeoconform, List<String> elementosAfetados,
                              String dadosFormulario) {
        return criarCenario(sufixo, new Parametros("ALTERACAO", codigoInstrumento, comGeoconform, elementosAfetados,
                dadosFormulario, false));
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor(), cenario.entidade());
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private String corpoRascunho(Cenario cenario, String elementosAfetados, String introduzPlantaNova, String informacaoBase) {
        return "{\"versao\":" + gravado(cenario).getVersao() + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"elementosAfetados\":" + elementosAfetados + ",\"entidadeEmitente\":\"" + ENTIDADE_EMITENTE + "\""
                + (introduzPlantaNova == null ? "" : ",\"introduzPlantaNova\":" + introduzPlantaNova)
                + (informacaoBase == null ? "" : ",\"informacaoBase\":" + informacaoBase) + "}";
    }

    private ResultActions gravar(Cenario cenario, RequestPostProcessor autenticacao, String elementosAfetados,
                                 String introduzPlantaNova, String informacaoBase) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(autenticacao).contentType("application/json")
                .content(corpoRascunho(cenario, elementosAfetados, introduzPlantaNova, informacaoBase)));
    }

    private ResultActions gravar(Cenario cenario, String elementosAfetados, String introduzPlantaNova) throws Exception {
        return gravar(cenario, jwtDoAutor(cenario), elementosAfetados, introduzPlantaNova, null);
    }

    private ResultActions submeter(Cenario cenario, RequestPostProcessor autenticacao) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(autenticacao).contentType("application/json")
                .content("{\"versao\":" + gravado(cenario).getVersao() + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                        + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}"));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        return submeter(cenario, jwtDoAutor(cenario));
    }

    private void verificarQueFicouEmSubmissao(Cenario cenario) {
        Processo depois = gravado(cenario);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(depois.getDataSubmissao()).isNull();
    }

    @Test
    void deveRecusarNumaDinamicaComGeoconformQuandoOsElementosAfetadosNaoTemPecasGraficas() throws Exception {
        Cenario cenario = alteracao("10001", "PDM", true, List.of("PECAS_ESCRITAS"), null);

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("elementosAfetados"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_GEOCONFORM));

        verificarQueFicouEmSubmissao(cenario);
    }

    @Test
    void deveAceitarNumaDinamicaComGeoconformComPecasGraficasESemResposta() throws Exception {
        Cenario cenario = alteracao("10002", "PDM", true, List.of("PECAS_GRAFICAS"), null);

        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarSemRespostaQuandoHaPecasGraficasESemGeoconform() throws Exception {
        Cenario cenario = alteracao("10003", "PU", false, List.of("PECAS_GRAFICAS"), null);

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("introduzPlantaNova"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_PLANTA_NOVA_EM_FALTA));

        verificarQueFicouEmSubmissao(cenario);
    }

    @Test
    void deveRecusarPlantaNovaSemPecasEscritasEAceitarComElas() throws Exception {
        Cenario cenario = alteracao("10004", "PU", false, List.of("PECAS_GRAFICAS"), "{\"introduzPlantaNova\":true}");

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("elementosAfetados"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_EXIGE_REGULAMENTO));
        verificarQueFicouEmSubmissao(cenario);

        gravar(cenario, PECAS_GRAFICAS_E_ESCRITAS, "true").andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarUmaRespostaQuandoAPerguntaNaoSeAplica() throws Exception {
        Cenario cenario = alteracao("10005", "PDM", false, List.of("PECAS_ESCRITAS"), "{\"introduzPlantaNova\":false}");

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("introduzPlantaNova"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_SEM_PERGUNTA));
        verificarQueFicouEmSubmissao(cenario);
    }

    @Test
    void deveJuntarTodasAsFalhasNumaSoRespostaSemCodigoNovo() throws Exception {
        Cenario cenario = alteracao("10006", "PDM", true, List.of("PECAS_ESCRITAS"), "{\"introduzPlantaNova\":true}");

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[0].path").value("elementosAfetados"))
                .andExpect(jsonPath("$.campos[1].path").value("introduzPlantaNova"));
        verificarQueFicouEmSubmissao(cenario);
    }

    @Test
    void deveGravarORascunhoSemValidarENaoAplicarAsRegrasNoutroProcedimento() throws Exception {
        Cenario cenario = criarCenario("10007", new Parametros("ABERTURA_DISCUSSAO_PUBLICA", "PDM", false,
                List.of("PECAS_ESCRITAS"), null, false));

        gravar(cenario, PECAS_ESCRITAS, "true").andExpect(status().isOk());
        assertThat(gravado(cenario).getDadosFormulario()).isEqualTo("{\"introduzPlantaNova\":true}");

        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveGravarORascunhoDaPublicacaoDaAlteracaoComRespostaIncoerenteSemValidar() throws Exception {
        Cenario cenario = alteracao("10008", "PDM", true, List.of("PECAS_ESCRITAS"), null);

        gravar(cenario, PECAS_ESCRITAS, "true").andExpect(status().isOk());

        assertThat(gravado(cenario).getDadosFormulario()).isEqualTo("{\"introduzPlantaNova\":true}");
    }

    @Test
    void deveRecusarMudarARespostaDepoisDaPrimeiraSubmissao() throws Exception {
        Cenario cenario = criarCenario("10009", new Parametros("ALTERACAO", "PDM", false, List.of("PECAS_GRAFICAS", "PECAS_ESCRITAS"),
                "{\"introduzPlantaNova\":true}", true));

        gravar(cenario, PECAS_GRAFICAS_E_ESCRITAS, "false")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("introduzPlantaNova"));
        assertThat(gravado(cenario).getDadosFormulario()).isEqualTo("{\"introduzPlantaNova\":true}");

        gravar(cenario, PECAS_GRAFICAS_E_ESCRITAS, "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados[?(@ == 'introduzPlantaNova')]").exists());
    }

    @Test
    void deveDevolverNoDetalheAsDuasChavesDeDadosFormulario() throws Exception {
        Cenario cenario = alteracao("10010", "PDM", false, List.of("PECAS_GRAFICAS", "PECAS_ESCRITAS"), null);
        // O Procedimento do cenário não tem itens documentais, logo não prevê conteúdo na Informação Base: o bloco vai vazio.
        String informacaoBase = "{}";

        gravar(cenario, jwtDoAutor(cenario), PECAS_GRAFICAS_E_ESCRITAS, "true", informacaoBase).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("PUBLICACAO_ALTERACAO"))
                .andExpect(jsonPath("$.dadosFormulario").value(containsString("\"informacaoBase\"")))
                .andExpect(jsonPath("$.dadosFormulario").value(containsString("\"introduzPlantaNova\":true")));
    }

    @Test
    void quemNaoEAutorRecebe403AntesDeQualquerRegraENaoAutenticadoRecebe401() throws Exception {
        Cenario cenario = alteracao("10011", "PDM", true, List.of("PECAS_ESCRITAS"), "{\"introduzPlantaNova\":true}");

        gravar(cenario, jwtDe(cenario.outro(), cenario.entidade()), PECAS_ESCRITAS, "true", null)
                .andExpect(status().isForbidden());
        submeter(cenario, jwtDe(cenario.outro(), cenario.entidade())).andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                        .contentType("application/json").content("{\"versao\":0}"))
                .andExpect(status().isUnauthorized());
        verificarQueFicouEmSubmissao(cenario);
    }
}
