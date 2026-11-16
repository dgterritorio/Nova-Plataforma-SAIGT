package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
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
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
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
class FundamentacaoRjigtIntegrationTest {

    private static final String TIPO_LISTA = "FUNDAMENTACAO_RJIGT";
    private static final String CODIGO_1 = "RJIGT-T1";
    private static final String CODIGO_2 = "RJIGT-T2";
    private static final String CODIGO_INATIVO = "RJIGT-T3";

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

    @Autowired
    private ListaValoresRepository listaValoresRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    private final List<ListaValores> itensDoTeste = new ArrayList<>();

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    /** Semeados pela ordem inversa, para o detalhe ter de os ordenar por `ordem`; removidos no fim para não afetar outras classes. */
    @BeforeEach
    void semearLista() {
        ListaValores inativo = new ListaValores(TIPO_LISTA, CODIGO_INATIVO, "Opção desativada", 3);
        inativo.setAtivo(false);
        itensDoTeste.add(listaValoresRepository.saveAndFlush(inativo));
        itensDoTeste.add(listaValoresRepository.saveAndFlush(new ListaValores(TIPO_LISTA, CODIGO_2, "Segunda opção", 2)));
        itensDoTeste.add(listaValoresRepository.saveAndFlush(new ListaValores(TIPO_LISTA, CODIGO_1, "Primeira opção", 1)));
    }

    @AfterEach
    void limparLista() {
        listaValoresRepository.deleteAll(itensDoTeste);
        itensDoTeste.clear();
    }

    /** O `codigo` do tipo é único na BD partilhada pelos testes. */
    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Sem itens documentais e com o resto completo: só a fundamentação RJIGT pode travar a submissão. */
    private Cenario criarCenario(String codigo, String tipoProcedimento, boolean pedeFundamentacao, String dadosFormulario,
                                 boolean submetido) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("RJIGT-" + codigo, "Instrumento " + codigo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-RJIGT-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimento(tipoProcedimento), 1, "SEGUNDA_H");
        procedimento.setPedeFundamentacaoRjigt(pedeFundamentacao);
        procedimento = procedimentoConfigRepository.saveAndFlush(procedimento);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5096" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-rjigt.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/RJIGT/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Camara Municipal de Teste");
        processo.setDadosFormulario(dadosFormulario);
        if (submetido) {
            processo.setDataSubmissao(Instant.now());
        }
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private long versao(Cenario cenario) {
        return gravado(cenario).getVersao();
    }

    private ResultActions gravar(Cenario cenario, Utilizador quem, String camposExtra) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"entidadeEmitente\":\"Camara Municipal de Teste\",\"elementosAfetados\":[\"PECAS_ESCRITAS\"]"
                + (camposExtra.isEmpty() ? "" : "," + camposExtra) + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDe(quem, cenario.entidade()))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions gravar(Cenario cenario, String camposExtra) throws Exception {
        return gravar(cenario, cenario.autor(), camposExtra);
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDe(cenario.autor(), cenario.entidade()))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId())
                .with(jwtDe(cenario.autor(), cenario.entidade())));
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    /** Item não obrigatório: só torna a Informação Base da Deliberação prevista, sem pendências documentais. */
    private void preverDeliberacao(Cenario cenario) {
        DocumentoConfig item = new DocumentoConfig(cenario.processo().getInstrumentoProcedimentoConfig(), "DELIB_RJIGT",
                "Deliberação", "Deliberação", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA,
                FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        item.setElementoInformacaoBase(ElementoInformacaoBase.DELIBERACAO);
        documentoConfigRepository.saveAndFlush(item);
    }

    private JsonNode dadosFormulario(Cenario cenario) throws Exception {
        String dados = gravado(cenario).getDadosFormulario();
        return dados == null ? objectMapper.missingNode() : objectMapper.readTree(dados);
    }

    @Test
    void deveDevolverNoDetalheAsOpcoesAtivasPorOrdemQuandoOProcedimentoPedeOCampo() throws Exception {
        Cenario cenario = criarCenario("4001", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true, null, false);

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedeFundamentacaoRjigt").value(true))
                .andExpect(jsonPath("$.fundamentacaoRjigt.length()").value(0))
                .andExpect(jsonPath("$.opcoesFundamentacaoRjigt.length()").value(2))
                .andExpect(jsonPath("$.opcoesFundamentacaoRjigt[0].codigo").value(CODIGO_1))
                .andExpect(jsonPath("$.opcoesFundamentacaoRjigt[0].descricao").value("Primeira opção"))
                .andExpect(jsonPath("$.opcoesFundamentacaoRjigt[1].codigo").value(CODIGO_2));
    }

    @Test
    void naoDeveDevolverOpcoesQuandoOProcedimentoNaoPedeOCampo() throws Exception {
        Cenario cenario = criarCenario("4002", "ALTERACAO_SIMPLIFICADA_DL10", false, null, false);

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedeFundamentacaoRjigt").value(false))
                .andExpect(jsonPath("$.opcoesFundamentacaoRjigt.length()").value(0));
    }

    @Test
    void deveGravarOCodigoSemPerderAInformacaoBaseEDevolveLoNoDetalhe() throws Exception {
        Cenario cenario = criarCenario("4003", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true, null, false);
        preverDeliberacao(cenario);

        gravar(cenario, "\"fundamentacaoRjigt\":[\"" + CODIGO_2 + "\",\"" + CODIGO_1 + "\",\"" + CODIGO_2 + "\"],"
                + "\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2}}")
                .andExpect(status().isOk());

        JsonNode dados = dadosFormulario(cenario);
        assertThat(dados.path("fundamentacaoRjigt").toString()).isEqualTo("[\"" + CODIGO_2 + "\",\"" + CODIGO_1 + "\"]");
        assertThat(dados.path("informacaoBase").path("deliberacao").path("prazoAnos").asInt()).isEqualTo(2);
        detalhe(cenario)
                .andExpect(jsonPath("$.fundamentacaoRjigt.length()").value(2))
                .andExpect(jsonPath("$.fundamentacaoRjigt[0]").value(CODIGO_2));
    }

    @Test
    void deveRecusarUmCodigoInexistenteSemMudarNada() throws Exception {
        String anterior = "{\"fundamentacaoRjigt\":[\"" + CODIGO_1 + "\"]}";
        Cenario cenario = criarCenario("4004", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true, anterior, false);

        gravar(cenario, "\"fundamentacaoRjigt\":[\"NAO-EXISTE\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("fundamentacaoRjigt"))
                .andExpect(jsonPath("$.campos[0].erro").value("Fundamentação RJIGT inválida."));

        assertThat(gravado(cenario).getDadosFormulario()).isEqualTo(anterior);
    }

    @Test
    void deveRecusarUmCodigoInativo() throws Exception {
        Cenario cenario = criarCenario("4005", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true, null, false);

        gravar(cenario, "\"fundamentacaoRjigt\":[\"" + CODIGO_INATIVO + "\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("fundamentacaoRjigt"));

        assertThat(gravado(cenario).getDadosFormulario()).isNull();
    }

    @Test
    void deveRecusarOCampoNumProcedimentoQueNaoOPedeEAceitarSemOCampo() throws Exception {
        Cenario cenario = criarCenario("4006", "ALTERACAO_SIMPLIFICADA_DL10", false, null, false);

        gravar(cenario, "\"fundamentacaoRjigt\":[\"" + CODIGO_1 + "\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("fundamentacaoRjigt"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este procedimento não pede a fundamentação RJIGT."));
        gravar(cenario, "").andExpect(status().isOk());
        gravar(cenario, "\"fundamentacaoRjigt\":[]").andExpect(status().isOk());

        assertThat(gravado(cenario).getDadosFormulario()).isNull();
    }

    @Test
    void deveRecusarUmCodigoComMaisDe20Caracteres() throws Exception {
        Cenario cenario = criarCenario("4007", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true, null, false);

        gravar(cenario, "\"fundamentacaoRjigt\":[\"" + "x".repeat(21) + "\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveSubmeterSemFundamentacaoPorqueOCampoEOpcional() throws Exception {
        Cenario cenario = criarCenario("4008", "ALTERACAO_SIMPLIFICADA_DL10", true, null, false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveSubmeterComUmCodigoAtivo() throws Exception {
        Cenario cenario = criarCenario("4009", "ALTERACAO_SIMPLIFICADA_DL10", true,
                "{\"fundamentacaoRjigt\":[\"" + CODIGO_1 + "\"]}", false);

        submeter(cenario).andExpect(status().isOk());
    }

    @Test
    void deveRecusarASubmissaoComUmCodigoGravadoQueODepoisDesativou() throws Exception {
        Cenario cenario = criarCenario("4010", "ALTERACAO_SIMPLIFICADA_DL10", true,
                "{\"fundamentacaoRjigt\":[\"" + CODIGO_INATIVO + "\"]}", false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("fundamentacaoRjigt"));

        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveRecusarComo403QuemNaoEOAutor() throws Exception {
        Cenario cenario = criarCenario("4011", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true, null, false);
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outra 4011", "outra4011@cm-rjigt.pt", "INCM"));

        gravar(cenario, outro, "\"fundamentacaoRjigt\":[\"" + CODIGO_1 + "\"]").andExpect(status().isForbidden());

        assertThat(gravado(cenario).getDadosFormulario()).isNull();
    }

    @Test
    void deveRecusarMudarOCampoDepoisDaPrimeiraSubmissaoEAceitarOMesmoValor() throws Exception {
        Cenario cenario = criarCenario("4012", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true,
                "{\"fundamentacaoRjigt\":[\"" + CODIGO_1 + "\"]}", true);

        gravar(cenario, "\"fundamentacaoRjigt\":[\"" + CODIGO_2 + "\"]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("fundamentacaoRjigt"));
        gravar(cenario, "\"fundamentacaoRjigt\":[\"" + CODIGO_1 + "\"]").andExpect(status().isOk());
        detalhe(cenario).andExpect(jsonPath("$.camposBloqueados[?(@ == 'fundamentacaoRjigt')]").exists());
    }

    @Test
    void depoisDaPrimeiraSubmissaoUmCodigoDesativadoNaoImpedeGravarNemVoltarASubmeter() throws Exception {
        Cenario cenario = criarCenario("4013", "ABERTURA_DISCUSSAO_PUBLICA_DL10", true,
                "{\"fundamentacaoRjigt\":[\"" + CODIGO_INATIVO + "\"]}", true);

        gravar(cenario, "\"fundamentacaoRjigt\":[\"" + CODIGO_INATIVO + "\"]").andExpect(status().isOk());
        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(dadosFormulario(cenario).path("fundamentacaoRjigt").toString()).isEqualTo("[\"" + CODIGO_INATIVO + "\"]");
    }
}
