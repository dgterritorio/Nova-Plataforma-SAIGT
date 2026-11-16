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
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class FormularioSgifrIntegrationTest {

    private static final String CAMPO_NAO_APLICAVEL = "Campo não aplicável a este procedimento.";
    private static final List<String> REDES_PSA = List.of("AEMGC", "APPS", "RPA", "RSFGC", "RVDI", "RVF");

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
    private SgifrRedeDefesaRepository redeDefesaRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    @BeforeEach
    void catalogo() {
        redeSeNaoExiste("RPFGC", true, false, true);
        redeSeNaoExiste("RTFGC", false, false, false);
        REDES_PSA.forEach(codigo -> redeSeNaoExiste(codigo, false, true, true));
    }

    private void redeSeNaoExiste(String codigo, boolean pra, boolean psa, boolean publicavel) {
        if (!redeDefesaRepository.existsByCodigo(codigo)) {
            redeDefesaRepository.saveAndFlush(new SgifrRedeDefesa(codigo, "Rede " + codigo, pra, psa, publicavel));
        }
    }

    private TipoInstrumento tipoInstrumento(String codigo, String categoria) {
        return tipoInstrumentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigo, codigo, categoria)));
    }

    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Dados INCM completos e sem itens documentais: só o formulário pode travar a gravação ou a submissão. */
    private Cenario criarCenario(String sufixo, String programa, String categoria, String codigoProcedimento) {
        TipoInstrumento tipoInstrumento = tipoInstrumento(programa, categoria);
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("DELIM-SGIFR-" + sufixo, "Delimitação"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento(codigoProcedimento), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5094" + sufixo, "Entidade " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cm-sgifr.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/SGIFR/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Delimitação", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setEntidadeEmitente("Entidade de Teste");
        processo.setEntidadeInstrutora("Entidade instrutora de teste");
        // O tipo de ato escolhe-se no cabeçalho e é obrigatório para submeter (US-368).
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private Cenario psa(String sufixo) {
        return criarCenario(sufixo, "PSA", "PROGRAMA", "PUBLICACAO_SGIFR");
    }

    private Cenario pra(String sufixo) {
        return criarCenario(sufixo, "PRA", "PROGRAMA", "PUBLICACAO_SGIFR");
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

    private String corpo(Cenario cenario, String extras) {
        return "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Sumário alterado.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"entidadeEmitente\":\"Entidade de Teste\","
                + "\"entidadeInstrutora\":\"Entidade instrutora de teste\""
                + (extras.isEmpty() ? "" : "," + extras) + "}";
    }

    private ResultActions gravar(Cenario cenario, String extras) throws Exception {
        return gravar(cenario, extras, jwtDoAutor(cenario));
    }

    private ResultActions gravar(Cenario cenario, String extras, RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwt).contentType("application/json").content(corpo(cenario, extras)));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario)).contentType("application/json")
                .content(corpo(cenario, PedidosSubmissao.JSON_CONFIRMACOES)));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)));
    }

    private static String redes(String... codigos) {
        return "\"redesDefesa\":[" + java.util.Arrays.stream(codigos).map(c -> "\"" + c + "\"").collect(Collectors.joining(",")) + "]";
    }

    private Set<String> redesGravadas(Cenario cenario) {
        return transactionTemplate.execute(estado -> processoRepository.findById(cenario.processo().getId()).orElseThrow()
                .getRedesDefesa().stream().map(SgifrRedeDefesa::getCodigo).collect(Collectors.toSet()));
    }

    @Test
    void deveDevolverOFormularioSgifrComAsRedesDisponiveisDoPsa() throws Exception {
        Cenario cenario = psa("10001");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("SGIFR"))
                .andExpect(jsonPath("$.redesDefesa", hasSize(0)))
                .andExpect(jsonPath("$.redesDefesaDisponiveis[*].codigo", hasItems(REDES_PSA.toArray(String[]::new))))
                .andExpect(jsonPath("$.redesDefesaDisponiveis[?(@.codigo=='RPFGC')]").isEmpty())
                .andExpect(jsonPath("$.redesDefesaDisponiveis[?(@.codigo=='RTFGC')]").isEmpty())
                .andExpect(jsonPath("$.redesDefesaDisponiveis[0].nome").isNotEmpty());
    }

    @Test
    void deveGravarAsRedesDoPrimeiroPedidoSemRepetidosNoPsa() throws Exception {
        Cenario cenario = psa("10002");

        gravar(cenario, redes("APPS", "RVF", "APPS")).andExpect(status().isOk());

        assertThat(redesGravadas(cenario)).containsExactlyInAnyOrder("APPS", "RVF");
        detalhe(cenario)
                .andExpect(jsonPath("$.redesDefesa[*].codigo", contains("APPS", "RVF")));
    }

    @Test
    void deveGravarAsSeisRedesDoPsa() throws Exception {
        Cenario cenario = psa("10003");

        gravar(cenario, redes(REDES_PSA.toArray(String[]::new))).andExpect(status().isOk());

        assertThat(redesGravadas(cenario)).containsExactlyInAnyOrderElementsOf(REDES_PSA);
    }

    @Test
    void deveDeixarOPsaSemRedesComListaVaziaEAceitarASubmissao() throws Exception {
        Cenario cenario = psa("10004");
        gravar(cenario, redes("APPS")).andExpect(status().isOk());

        gravar(cenario, redes()).andExpect(status().isOk());
        assertThat(redesGravadas(cenario)).isEmpty();

        submeter(cenario).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveManterAsRedesDoPsaQuandoOCampoVemOmitido() throws Exception {
        Cenario cenario = psa("10005");
        gravar(cenario, redes("APPS")).andExpect(status().isOk());

        gravar(cenario, "").andExpect(status().isOk());

        assertThat(redesGravadas(cenario)).containsExactly("APPS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RTFGC", "RPFGC", "XYZ"})
    void deveRecusarNoPsaUmaRedeQueOProgramaNaoAceita(String codigo) throws Exception {
        Cenario cenario = psa(switch (codigo) {
            case "RTFGC" -> "10010";
            case "RPFGC" -> "10011";
            default -> "10012";
        });

        gravar(cenario, redes(codigo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REDES_DEFESA_INVALIDAS"))
                .andExpect(jsonPath("$.mensagem").value("Redes de defesa inválidas para este programa."))
                .andExpect(jsonPath("$.campos[0].path").value("redesDefesa"));

        assertThat(redesGravadas(cenario)).isEmpty();
    }

    @Test
    void deveGravarARedePrimariaNoPraSemAEscolher() throws Exception {
        Cenario cenario = pra("10020");

        gravar(cenario, "").andExpect(status().isOk());

        assertThat(redesGravadas(cenario)).contains("RPFGC").doesNotContain("RTFGC", "RSFGC");
        detalhe(cenario)
                .andExpect(jsonPath("$.redesDefesa[*].codigo", hasItems("RPFGC")))
                .andExpect(jsonPath("$.redesDefesaDisponiveis[*].codigo", hasItems("RPFGC")));
    }

    @Test
    void deveRecusarNoPraUmaRedeDoPsa() throws Exception {
        Cenario cenario = pra("10021");

        gravar(cenario, redes("RSFGC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REDES_DEFESA_INVALIDAS"))
                .andExpect(jsonPath("$.campos[0].path").value("redesDefesa"));
    }

    @Test
    void deveGravarARedePrimariaNaSubmissaoDeUmPraSemRascunho() throws Exception {
        Cenario cenario = pra("10022");

        submeter(cenario).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(redesGravadas(cenario)).contains("RPFGC");
    }

    @Test
    void deveRecusarOExtratoEGravarSempreOTextoIntegral() throws Exception {
        Cenario cenario = psa("10030");

        gravar(cenario, "\"tipoInformacao\":\"EXTRATO\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));
        submeter(cenario).andExpect(status().isOk());

        assertThat(processoRepository.findById(cenario.processo().getId()).orElseThrow().getTipoInformacao())
                .isEqualTo("TEXTO_INTEGRAL");
    }

    @Test
    void deveRecusarOExtratoNaSubmissao() throws Exception {
        Cenario cenario = psa("10031");

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                        .with(jwtDoAutor(cenario)).contentType("application/json")
                        .content(corpo(cenario, "\"tipoInformacao\":\"EXTRATO\"," + PedidosSubmissao.JSON_CONFIRMACOES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));
    }

    @Test
    void deveRecusarAsRedesNumProcessoDeOutroFormulario() throws Exception {
        Cenario cenario = criarCenario("10040", "PDM-SGIFR", "PLANO", "ALTERACAO");

        gravar(cenario, redes("APPS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos[0].path").value("redesDefesa"));
    }

    @Test
    void deveDevolverAsRedesNulasNumProcessoDeOutroFormulario() throws Exception {
        Cenario cenario = criarCenario("10041", "PDM-SGIFR", "PLANO", "ALTERACAO");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("PUBLICACAO_ALTERACAO"))
                .andExpect(jsonPath("$.redesDefesa").value(nullValue()))
                .andExpect(jsonPath("$.redesDefesaDisponiveis").value(nullValue()));
    }

    @Test
    void deveRecusarComForbiddenQuemNaoEOAutorSemAlterarAsRedes() throws Exception {
        Cenario cenario = psa("10050");
        Utilizador outro = utilizadorRepository.saveAndFlush(
                new Utilizador("Outra pessoa", "outra10050@cm-sgifr.pt", "INCM"));

        gravar(cenario, redes("APPS"), jwtDe(outro, cenario.entidade())).andExpect(status().isForbidden());

        assertThat(redesGravadas(cenario)).isEmpty();
    }
}
