package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PerguntaGeoconform;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
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
class OpcaoGeoconformSubmissaoIntegrationTest {

    private static final String MSG_EM_FALTA = "Indique se utiliza o GeoConform.";
    private static final String MSG_SEM_PERGUNTA = "Este procedimento não pergunta pela utilização do GeoConform.";

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

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, Dinamica dinamica) {
    }

    /** Só um tipo do catálogo de formulários grava e submete; o `codigo` é único na BD partilhada pelos testes. */
    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    /** Sem itens documentais: só a opção GeoConform pode travar a submissão. */
    private Cenario criarCenario(String codigo, PerguntaGeoconform pergunta, List<String> elementosAfetados) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("GEO-" + codigo, "Instrumento " + codigo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-GEO-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = new InstrumentoProcedimentoConfig(dinamicaConfig,
                tipoProcedimentoComFormulario(), 1, "SEGUNDA_H");
        procedimento.setPerguntaGeoconform(pergunta);
        procedimento = procedimentoConfigRepository.saveAndFlush(procedimento);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5093" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-geo.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/GEO/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Elaboração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(elementosAfetados);
        processo.setEntidadeEmitente("Câmara " + codigo);
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade, dinamica);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    private ResultActions submeter(Cenario cenario, RequestPostProcessor utilizador, String comGeoconform) throws Exception {
        String opcao = comGeoconform == null ? "" : ",\"comGeoconform\":" + comGeoconform;
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"" + opcao + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(utilizador)
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions gravar(Cenario cenario, String elementosAfetados, String comGeoconform) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"entidadeEmitente\":\"Câmara\",\"elementosAfetados\":" + elementosAfetados
                + ",\"comGeoconform\":" + comGeoconform + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDe(cenario.autor(), cenario.entidade()))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions submeter(Cenario cenario, String comGeoconform) throws Exception {
        return submeter(cenario, jwtDe(cenario.autor(), cenario.entidade()), comGeoconform);
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId())
                .with(jwtDe(cenario.autor(), cenario.entidade())));
    }

    private Boolean comGeoconformDaDinamica(Cenario cenario) {
        return dinamicaRepository.findById(cenario.dinamica().getId()).orElseThrow().getComGeoconform();
    }

    private EstadoProcesso estado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getEstado();
    }

    @Test
    void deveRecusarASubmissaoSemComGeoconformQuandoOProcedimentoPergunta() throws Exception {
        Cenario cenario = criarCenario("0001", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));

        submeter(cenario, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("OPCAO_GEOCONFORM_EM_FALTA"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("comGeoconform"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_EM_FALTA));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(comGeoconformDaDinamica(cenario)).isNull();
    }

    @Test
    void deveSubmeterEGuardarNaDinamicaAOpcaoSim() throws Exception {
        Cenario cenario = criarCenario("0002", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));

        submeter(cenario, "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(comGeoconformDaDinamica(cenario)).isTrue();
    }

    @Test
    void deveGuardarNaDinamicaAOpcaoNao() throws Exception {
        Cenario cenario = criarCenario("0003", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));

        submeter(cenario, "false").andExpect(status().isOk());

        assertThat(comGeoconformDaDinamica(cenario)).isFalse();
    }

    @Test
    void naoDeveExigirAOpcaoNaAlteracaoSoComPecasEscritasENaoAlterarADinamica() throws Exception {
        Cenario cenario = criarCenario("0004", PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("PECAS_ESCRITAS"));

        submeter(cenario, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(comGeoconformDaDinamica(cenario)).isNull();
    }

    @Test
    void deveExigirAOpcaoNaAlteracaoComPecasGraficas() throws Exception {
        Cenario cenario = criarCenario("0005", PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("PECAS_GRAFICAS"));

        submeter(cenario, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("OPCAO_GEOCONFORM_EM_FALTA"))
                .andExpect(jsonPath("$.campos[0].path").value("comGeoconform"));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveRecusarOComGeoconformNumProcedimentoQueNaoPergunta() throws Exception {
        Cenario cenario = criarCenario("0006", PerguntaGeoconform.NAO_APLICAVEL, List.of("PECAS_ESCRITAS"));

        submeter(cenario, "true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("comGeoconform"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_SEM_PERGUNTA));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(comGeoconformDaDinamica(cenario)).isNull();
    }

    @Test
    void deveRecusarOComGeoconformQuandoAAlteracaoNaoIncideSobrePecasGraficas() throws Exception {
        Cenario cenario = criarCenario("0007", PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("OUTROS"));

        submeter(cenario, "false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("comGeoconform"));

        assertThat(comGeoconformDaDinamica(cenario)).isNull();
    }

    @Test
    void deveSubstituirARespostaNumaNovaSubmissaoDepoisDeCorrigir() throws Exception {
        Cenario cenario = criarCenario("0008", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));
        submeter(cenario, "true").andExpect(status().isOk());
        assertThat(comGeoconformDaDinamica(cenario)).isTrue();

        Processo rejeitado = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        rejeitado.setEstado(EstadoProcesso.REJEITADO_DGT);
        processoRepository.saveAndFlush(rejeitado);
        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/corrigir")
                        .with(jwtDe(cenario.autor(), cenario.entidade()))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(cenario) + "}"))
                .andExpect(status().isOk());

        submeter(cenario, "false").andExpect(status().isOk());

        assertThat(comGeoconformDaDinamica(cenario)).isFalse();
    }

    @Test
    void deveRecusarAQuemNaoEOAutorSemMudarADinamica() throws Exception {
        Cenario cenario = criarCenario("0009", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outra 0009", "outra0009@cm-geo.pt", "INCM"));

        submeter(cenario, jwtDe(outro, cenario.entidade()), "true").andExpect(status().isForbidden());

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(comGeoconformDaDinamica(cenario)).isNull();
    }

    @Test
    void deveRegistarAOpcaoNaAuditoriaDaSubmissaoSoQuandoFoiPerguntada() throws Exception {
        Cenario pergunta = criarCenario("0010", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));
        Cenario semPergunta = criarCenario("0011", PerguntaGeoconform.NAO_APLICAVEL, List.of("PECAS_ESCRITAS"));

        submeter(pergunta, "true").andExpect(status().isOk());
        submeter(semPergunta, null).andExpect(status().isOk());

        assertThat(auditoriaSubmeter(pergunta).getDadosAlteracao()).contains("\"comGeoconform\"").contains("true");
        assertThat(auditoriaSubmeter(semPergunta).getDadosAlteracao()).doesNotContain("comGeoconform");
    }

    private Auditoria auditoriaSubmeter(Cenario cenario) {
        return auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "PROCESSO", cenario.processo().getId().toString(), "SUBMETER").orElseThrow();
    }

    @Test
    void deveDevolverNoDetalheSeAOpcaoEExigidaEARespostaDaDinamica() throws Exception {
        Cenario obrigatoria = criarCenario("0012", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));
        Cenario naoAplicavel = criarCenario("0013", PerguntaGeoconform.NAO_APLICAVEL, List.of("PECAS_GRAFICAS"));
        Cenario comGraficas = criarCenario("0014", PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("PECAS_GRAFICAS"));
        Cenario semGraficas = criarCenario("0015", PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("PECAS_ESCRITAS"));

        detalhe(obrigatoria).andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeOpcaoGeoconform").value(true))
                .andExpect(jsonPath("$.comGeoconform").doesNotExist());
        detalhe(naoAplicavel).andExpect(jsonPath("$.exigeOpcaoGeoconform").value(false));
        detalhe(comGraficas).andExpect(jsonPath("$.exigeOpcaoGeoconform").value(true));
        detalhe(semGraficas).andExpect(jsonPath("$.exigeOpcaoGeoconform").value(false));

        submeter(obrigatoria, "false").andExpect(status().isOk());

        detalhe(obrigatoria).andExpect(jsonPath("$.exigeOpcaoGeoconform").value(true))
                .andExpect(jsonPath("$.comGeoconform").value(false));
    }

    @Test
    void deveDevolverNoDetalheAPerguntaDoProcedimentoSemOlharAoQueEstaGravado() throws Exception {
        Cenario semNadaGravado = criarCenario("0016", PerguntaGeoconform.SE_PECAS_GRAFICAS, null);
        Cenario soEscritas = criarCenario("0017", PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("PECAS_ESCRITAS"));
        Cenario obrigatoria = criarCenario("0018", PerguntaGeoconform.OBRIGATORIA, null);
        Cenario naoAplicavel = criarCenario("0019", PerguntaGeoconform.NAO_APLICAVEL, List.of("PECAS_GRAFICAS"));

        detalhe(semNadaGravado).andExpect(status().isOk())
                .andExpect(jsonPath("$.perguntaGeoconform").value("SE_PECAS_GRAFICAS"))
                .andExpect(jsonPath("$.exigeOpcaoGeoconform").value(false));
        detalhe(soEscritas).andExpect(jsonPath("$.perguntaGeoconform").value("SE_PECAS_GRAFICAS"))
                .andExpect(jsonPath("$.exigeOpcaoGeoconform").value(false));
        detalhe(obrigatoria).andExpect(jsonPath("$.perguntaGeoconform").value("OBRIGATORIA"))
                .andExpect(jsonPath("$.exigeOpcaoGeoconform").value(true));
        detalhe(naoAplicavel).andExpect(jsonPath("$.perguntaGeoconform").value("NAO_APLICAVEL"))
                .andExpect(jsonPath("$.exigeOpcaoGeoconform").value(false));
    }

    @Test
    void deveGravarAOpcaoNoFormularioESubmeterSemAEnviarDeNovo() throws Exception {
        Cenario cenario = criarCenario("0101", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));

        gravar(cenario, "[\"PECAS_ESCRITAS\"]", "false").andExpect(status().isOk());
        assertThat(comGeoconformDaDinamica(cenario)).isFalse();
        detalhe(cenario).andExpect(jsonPath("$.comGeoconform").value(false));

        submeter(cenario, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(comGeoconformDaDinamica(cenario)).isFalse();
    }

    @Test
    void naoDeveMexerNaOpcaoGravadaQuandoOFormularioNaoAEnvia() throws Exception {
        Cenario cenario = criarCenario("0102", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));
        gravar(cenario, "[\"PECAS_ESCRITAS\"]", "true").andExpect(status().isOk());

        gravar(cenario, "[\"PECAS_ESCRITAS\"]", "null").andExpect(status().isOk());

        assertThat(comGeoconformDaDinamica(cenario)).isTrue();
    }

    @Test
    void deveRecusarAOpcaoNoFormularioQuandoOProcedimentoNaoPergunta() throws Exception {
        Cenario cenario = criarCenario("0103", PerguntaGeoconform.NAO_APLICAVEL, List.of("PECAS_ESCRITAS"));

        gravar(cenario, "[\"PECAS_ESCRITAS\"]", "true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("comGeoconform"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_SEM_PERGUNTA));

        assertThat(comGeoconformDaDinamica(cenario)).isNull();
        assertThat(processoRepository.findById(cenario.processo().getId()).orElseThrow().getEntidadeEmitente())
                .isEqualTo("Câmara 0103");
    }

    @Test
    void devePerguntarNoFormularioPelasPecasGraficasDoMesmoPedido() throws Exception {
        Cenario cenario = criarCenario("0104", PerguntaGeoconform.SE_PECAS_GRAFICAS, null);

        gravar(cenario, "[\"PECAS_GRAFICAS\"]", "true").andExpect(status().isOk());

        assertThat(comGeoconformDaDinamica(cenario)).isTrue();
    }

    @Test
    void deveDarPrioridadeAOpcaoEnviadaNaSubmissao() throws Exception {
        Cenario cenario = criarCenario("0105", PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"));
        gravar(cenario, "[\"PECAS_ESCRITAS\"]", "false").andExpect(status().isOk());

        submeter(cenario, "true").andExpect(status().isOk());

        assertThat(comGeoconformDaDinamica(cenario)).isTrue();
    }
}
