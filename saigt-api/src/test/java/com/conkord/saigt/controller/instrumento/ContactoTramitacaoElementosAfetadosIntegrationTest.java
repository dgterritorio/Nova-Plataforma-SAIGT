package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
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
class ContactoTramitacaoElementosAfetadosIntegrationTest {

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    /** Só um tipo do catálogo de formulários grava e submete; o `codigo` é único na BD partilhada pelos testes. */
    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    /** Sem itens documentais: só os elementos afetados podem travar a submissão. */
    private Cenario criarCenario(String codigo, String categoria, List<String> elementosAfetados) {
        return criarCenario(codigo, categoria, tipoDinamicaAlteracao(), elementosAfetados);
    }

    /** O código do tipo de Dinâmica é único na base partilhada pelos testes: o tipo partilha-se entre cenários. */
    private TipoDinamica tipoDinamicaAlteracao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> "ALTERACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração")));
    }

    private Cenario criarCenario(String codigo, String categoria, TipoDinamica tipoDinamica, List<String> elementosAfetados) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("TRAM-" + codigo, "Instrumento " + codigo, categoria));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoComFormulario();
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5092" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-tram.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/TRAM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(elementosAfetados);
        processo.setEntidadeEmitente("Câmara " + codigo);
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    private ResultActions gravar(Cenario cenario, String camposExtra) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + camposExtra + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)));
    }

    @Test
    void deveGravarOContactoEOsElementosAfetadosEDevolveLosNoDetalhe() throws Exception {
        Cenario cenario = criarCenario("0001", "PLANO", null);

        gravar(cenario, "\"contactoTramitacao\":\"tramitacao@cm-sintra.pt\",\"elementosAfetados\":[\"PECAS_ESCRITAS\"]")
                .andExpect(status().isOk());

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactoTramitacao").value("tramitacao@cm-sintra.pt"))
                .andExpect(jsonPath("$.elementosAfetados.length()").value(1))
                .andExpect(jsonPath("$.elementosAfetados[0]").value("PECAS_ESCRITAS"));
    }

    @Test
    void deveRecusarUmContactoQueNaoEEmail() throws Exception {
        Cenario cenario = criarCenario("0002", "PLANO", null);

        gravar(cenario, "\"contactoTramitacao\":\"sem-arroba\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("contactoTramitacao"));

        assertThat(processoRepository.findById(cenario.processo().getId()).orElseThrow().getContactoTramitacao()).isNull();
    }

    @Test
    void deveRecusarUmElementoAfetadoForaDosTresNoCampoElementosAfetados() throws Exception {
        Cenario cenario = criarCenario("0003", "PLANO", List.of("OUTROS"));

        gravar(cenario, "\"elementosAfetados\":[\"OUTRO_VALOR\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("elementosAfetados"));

        assertThat(processoRepository.findById(cenario.processo().getId()).orElseThrow().getElementosAfetados())
                .containsExactly("OUTROS");
    }

    @Test
    void deveGravarOContactoEmBrancoComoNuloEOsElementosRepetidosUmaVez() throws Exception {
        Cenario cenario = criarCenario("0004", "PLANO", null);

        gravar(cenario, "\"contactoTramitacao\":\"\",\"elementosAfetados\":[\"PECAS_GRAFICAS\",\"PECAS_GRAFICAS\"]")
                .andExpect(status().isOk());

        Processo gravado = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        assertThat(gravado.getContactoTramitacao()).isNull();
        assertThat(gravado.getElementosAfetados()).containsExactly("PECAS_GRAFICAS");
    }

    @Test
    void deveRecusarASubmissaoDeUmPlanoSemElementosAfetadosEPodeSubmeterFicaFalso() throws Exception {
        Cenario cenario = criarCenario("0005", "PLANO", null);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ELEMENTOS_AFETADOS_EM_FALTA"))
                .andExpect(jsonPath("$.campos[0].path").value("elementosAfetados"));

        assertThat(processoRepository.findById(cenario.processo().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveSubmeterUmPlanoComElementosAfetadosEPodeSubmeterFicaVerdadeiro() throws Exception {
        Cenario cenario = criarCenario("0006", "PLANO", List.of("PECAS_ESCRITAS"));

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveExigirOsElementosAfetadosTambemNaElaboracaoDeUmPlano() throws Exception {
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-TRAM-0101", "Elaboração"));
        Cenario cenario = criarCenario("0101", "PLANO", elaboracao, null);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ELEMENTOS_AFETADOS_EM_FALTA"));
    }

    @Test
    void naoDeveExigirElementosAfetadosNumInstrumentoSrup() throws Exception {
        Cenario cenario = criarCenario("0007", "SRUP", null);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarPrimeiroOsDadosIncmEmFaltaQuandoFaltamTambemOsElementosAfetados() throws Exception {
        Cenario cenario = criarCenario("0008", "PLANO", null);

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                        .with(jwtDoAutor(cenario))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                                + LocalDate.now() + "\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INCM_EM_FALTA"));
    }
}
