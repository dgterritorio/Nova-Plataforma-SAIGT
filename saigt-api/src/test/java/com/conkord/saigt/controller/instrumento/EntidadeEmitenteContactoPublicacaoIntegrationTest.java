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
class EntidadeEmitenteContactoPublicacaoIntegrationTest {

    private static final String MENSAGEM_ENTIDADE_EM_FALTA = "Indique a identificação da entidade emitente.";

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

    /** Sem itens documentais e com elementos afetados: só a entidade emitente pode travar a submissão. */
    private Cenario criarCenario(String codigo, String categoria, String entidadeEmitente, boolean submetido) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("EMIT-" + codigo, "Instrumento " + codigo, categoria));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-EMIT-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoComFormulario(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5094" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-emit.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/EMIT/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente(entidadeEmitente);
        if ("PROGRAMA".equals(categoria)) {
            processo.setEntidadeInstrutora("Comissão de Coordenação de Teste");
        }
        if (submetido) {
            processo.setDataSubmissao(Instant.now());
        }
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
                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"]," + camposExtra + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)));
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    @Test
    void deveGravarOsDoisCamposSemOsEspacosDasPontasEDevolveLosNoDetalhe() throws Exception {
        Cenario cenario = criarCenario("3001", "PLANO", null, false);

        gravar(cenario, "\"entidadeEmitente\":\"  Camara Municipal de Sintra  \",\"contactoPublicacao\":\"  Gabinete de Planeamento  \"")
                .andExpect(status().isOk());

        Processo depois = gravado(cenario);
        assertThat(depois.getEntidadeEmitente()).isEqualTo("Camara Municipal de Sintra");
        assertThat(depois.getContactoPublicacao()).isEqualTo("Gabinete de Planeamento");
        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidadeEmitente").value("Camara Municipal de Sintra"))
                .andExpect(jsonPath("$.contactoPublicacao").value("Gabinete de Planeamento"));
    }

    @Test
    void deveGravarOsCamposEmBrancoComoNulo() throws Exception {
        Cenario cenario = criarCenario("3002", "PLANO", "Entidade anterior", false);

        gravar(cenario, "\"entidadeEmitente\":\"   \",\"contactoPublicacao\":\"\"").andExpect(status().isOk());

        Processo depois = gravado(cenario);
        assertThat(depois.getEntidadeEmitente()).isNull();
        assertThat(depois.getContactoPublicacao()).isNull();
        detalhe(cenario)
                .andExpect(jsonPath("$.entidadeEmitente").doesNotExist())
                .andExpect(jsonPath("$.contactoPublicacao").doesNotExist());
    }

    @Test
    void deveRecusarUmaEntidadeEmitenteComMaisDe500CaracteresNoCampoRespetivo() throws Exception {
        Cenario cenario = criarCenario("3003", "PLANO", "Entidade anterior", false);

        gravar(cenario, "\"entidadeEmitente\":\"" + "x".repeat(501) + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeEmitente"));

        assertThat(gravado(cenario).getEntidadeEmitente()).isEqualTo("Entidade anterior");
    }

    @Test
    void deveRecusarUmContactoDePublicacaoComMaisDe500CaracteresNoCampoRespetivo() throws Exception {
        Cenario cenario = criarCenario("3004", "PLANO", null, false);

        gravar(cenario, "\"contactoPublicacao\":\"" + "x".repeat(501) + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("contactoPublicacao"));

        assertThat(gravado(cenario).getContactoPublicacao()).isNull();
    }

    @Test
    void deveAceitarOsDoisCamposComExatamente500Caracteres() throws Exception {
        Cenario cenario = criarCenario("3005", "PLANO", null, false);

        gravar(cenario, "\"entidadeEmitente\":\"" + "e".repeat(500) + "\",\"contactoPublicacao\":\"" + "c".repeat(500) + "\"")
                .andExpect(status().isOk());

        assertThat(gravado(cenario).getEntidadeEmitente()).hasSize(500);
        assertThat(gravado(cenario).getContactoPublicacao()).hasSize(500);
    }

    @Test
    void deveRecusarASubmissaoDeUmPlanoSemEntidadeEmitenteEPodeSubmeterFicaFalso() throws Exception {
        Cenario cenario = criarCenario("3006", "PLANO", null, false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeEmitente"))
                .andExpect(jsonPath("$.campos[0].erro").value(MENSAGEM_ENTIDADE_EM_FALTA));

        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveRecusarASubmissaoDeUmProgramaSemEntidadeEmitente() throws Exception {
        Cenario cenario = criarCenario("3007", "PROGRAMA", null, false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeEmitente"));

        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveSubmeterUmPlanoComEntidadeEmitenteSemContactoDePublicacao() throws Exception {
        Cenario cenario = criarCenario("3008", "PLANO", "Camara Municipal de Sintra", false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void naoDeveExigirAEntidadeEmitenteNumInstrumentoSrup() throws Exception {
        Cenario cenario = criarCenario("3009", "SRUP", null, false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarMudarAEntidadeEmitenteDepoisDaPrimeiraSubmissao() throws Exception {
        Cenario cenario = criarCenario("3010", "PLANO", "Camara Municipal de Sintra", true);

        gravar(cenario, "\"entidadeEmitente\":\"Outra entidade\",\"contactoPublicacao\":null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("entidadeEmitente"));

        assertThat(gravado(cenario).getEntidadeEmitente()).isEqualTo("Camara Municipal de Sintra");
    }

    @Test
    void deveRecusarMudarOContactoDePublicacaoDepoisDaPrimeiraSubmissao() throws Exception {
        Cenario cenario = criarCenario("3011", "PLANO", "Camara Municipal de Sintra", true);

        gravar(cenario, "\"entidadeEmitente\":\"Camara Municipal de Sintra\",\"contactoPublicacao\":\"Novo contacto\"")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[0].path").value("contactoPublicacao"));

        assertThat(gravado(cenario).getContactoPublicacao()).isNull();
    }

    @Test
    void deveAceitarOsMesmosValoresDepoisDaPrimeiraSubmissao() throws Exception {
        Cenario cenario = criarCenario("3012", "PLANO", "Camara Municipal de Sintra", true);

        gravar(cenario, "\"entidadeEmitente\":\"  Camara Municipal de Sintra \",\"contactoPublicacao\":\"\"")
                .andExpect(status().isOk());
    }
}
