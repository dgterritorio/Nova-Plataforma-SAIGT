package com.conkord.saigt.controller.instrumento;

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
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfirmacoesSubmissaoIntegrationTest {

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    private TipoProcedimento decisaoInicio() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("DECISAO_INICIO")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão de início")));
    }

    /** Sem itens documentais e com os dados INCM completos: só as confirmações podem travar a submissão. */
    private Cenario criarCenario() {
        // A BD H2 é partilhada entre classes de teste: códigos aleatórios evitam colisões entre execuções.
        String sufixo = String.format("%05d", ThreadLocalRandom.current().nextInt(100_000));
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("CNF-" + sufixo, "Instrumento " + sufixo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-CNF-" + sufixo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisaoInicio(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5778" + sufixo, "Câmara " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cm-confirma.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/CNF/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private static RequestPostProcessor jwtDe(UUID utilizadorId, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor().getId(), cenario.entidade());
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    /** `confirmacoes` é o fragmento JSON do valor, ou nulo para o omitir. */
    private ResultActions submeter(Cenario cenario, RequestPostProcessor sessao, String confirmacoes) throws Exception {
        String corpo = "{\"versao\":" + gravado(cenario).getVersao() + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\""
                + (confirmacoes == null ? "" : ",\"confirmacoes\":" + confirmacoes) + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(sessao).contentType("application/json").content(corpo));
    }

    private Auditoria auditoriaSubmeter(Cenario cenario) {
        return auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "PROCESSO", cenario.processo().getId().toString(), "SUBMETER").orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<omitido>", "null", "[]"})
    void deveRecusarASubmissaoSemAConfirmacaoDoConteudoEManterOProcesso(String confirmacoes) throws Exception {
        Cenario cenario = criarCenario();
        long versaoAntes = gravado(cenario).getVersao();

        submeter(cenario, jwtDoAutor(cenario), "<omitido>".equals(confirmacoes) ? null : confirmacoes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACAO_EM_FALTA"))
                .andExpect(jsonPath("$.mensagem").value("Confirme os campos assinalados."))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("confirmacoes.CONTEUDO_PROCESSO"))
                .andExpect(jsonPath("$.campos[0].erro").value("Confirmação obrigatória."));

        Processo depois = gravado(cenario);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
        assertThat(depois.getDataSubmissao()).isNull();
    }

    @Test
    void deveSubmeterComAConfirmacaoEGuardaLaNaAuditoria() throws Exception {
        Cenario cenario = criarCenario();

        submeter(cenario, jwtDoAutor(cenario), "[\"CONTEUDO_PROCESSO\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(auditoriaSubmeter(cenario).getDadosAlteracao())
                .contains("\"confirmacoes\"").contains("CONTEUDO_PROCESSO");
    }

    @Test
    void deveIgnorarUmaChaveQueOFormularioNaoExige() throws Exception {
        Cenario cenario = criarCenario();

        submeter(cenario, jwtDoAutor(cenario), "[\"CONTEUDO_PROCESSO\",\"OUTRA\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(auditoriaSubmeter(cenario).getDadosAlteracao()).doesNotContain("OUTRA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"\"]", "[\"   \"]", "[\"CONTEUDO_PROCESSO\",\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"]"})
    void deveRecusarComValidacaoUmaChaveEmBrancoOuComMaisDe50Caracteres(String confirmacoes) throws Exception {
        Cenario cenario = criarCenario();

        submeter(cenario, jwtDoAutor(cenario), confirmacoes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));

        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveRecusarCom403QuemNaoEOAutorAntesDaRegraDasConfirmacoes() throws Exception {
        Cenario cenario = criarCenario();
        Utilizador outro = utilizadorRepository.saveAndFlush(
                new Utilizador("Outro", "outro" + UUID.randomUUID().toString().substring(0, 8) + "@cm-confirma.pt", "INCM"));

        submeter(cenario, jwtDe(outro.getId(), cenario.entidade()), null)
                .andExpect(status().isForbidden());

        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }
}
