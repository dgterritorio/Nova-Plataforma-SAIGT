package com.conkord.saigt.controller.instrumento;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class InformacaoBasePrevistaIntegrationTest {

    private static final String DELIBERACAO = "{\"prazoAnos\":1,\"prazoMeses\":2,\"prazoDias\":3,"
            + "\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2026-09-01\",\"textoPublicar\":\"%s\"}";
    private static final String TEXTO_ATO = "{\"prazoAnos\":4,\"prazoMeses\":5,\"prazoDias\":6,\"avaliacaoAmbiental\":false}";
    private static final String TEXTO_RESERVADO = "Texto-reservado-que-nao-pode-ir-para-o-log";

    @Autowired
    private MockMvc mockMvc;

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
    private DocumentoConfigRepository documentoConfigRepository;

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

    /** O `codigo` é único na BD partilhada pelos testes: um tipo de Procedimento do seed é reutilizado. */
    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Itens não obrigatórios: só marcam o elemento como previsto, sem criar pendências documentais. */
    private void preverInformacaoBase(InstrumentoProcedimentoConfig procedimento, ElementoInformacaoBase... elementos) {
        int ordem = 1;
        for (ElementoInformacaoBase elemento : elementos) {
            DocumentoConfig item = new DocumentoConfig(procedimento, elemento.name() + "_PUB", elemento.name(),
                    elemento.name(), CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                    "[\"pdf\"]", ordem++);
            item.setElementoInformacaoBase(elemento);
            documentoConfigRepository.saveAndFlush(item);
        }
    }

    private Cenario criarCenario(String sufixo, ElementoInformacaoBase... previstos) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("INFB-" + sufixo, "Instrumento " + sufixo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-INFB-" + sufixo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento("ALTERACAO"), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        preverInformacaoBase(procedimento, previstos);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5095" + sufixo, "Câmara " + sufixo));
        Utilizador autor = criarUtilizador("autora" + sufixo);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/INFB/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setSumario("Sumário original.");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private Utilizador criarUtilizador(String prefixo) {
        return utilizadorRepository.saveAndFlush(new Utilizador("Utilizador " + prefixo, prefixo + "@cm-infb.pt", "INCM"));
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

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private ResultActions gravar(Cenario cenario, RequestPostProcessor jwt, String sumario, String informacaoBase) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"" + sumario + "\","
                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"],\"entidadeEmitente\":\"Camara Municipal de Teste\""
                + (informacaoBase == null ? "" : ",\"informacaoBase\":" + informacaoBase) + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwt).contentType("application/json").content(corpo));
    }

    private static String informacaoBase(boolean comDeliberacao, boolean comTextoAto) {
        List<String> elementos = new ArrayList<>();
        if (comDeliberacao) {
            elementos.add("\"deliberacao\":" + DELIBERACAO.formatted("Texto a publicar."));
        }
        if (comTextoAto) {
            elementos.add("\"textoAto\":" + TEXTO_ATO);
        }
        return "{" + String.join(",", elementos) + "}";
    }

    @Test
    void deveGravarOsDoisElementosQuandoOProcedimentoOsPrev() throws Exception {
        Cenario cenario = criarCenario("00001", ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);

        gravar(cenario, jwtDe(cenario.autor(), cenario.entidade()), "Novo sumário.", informacaoBase(true, true))
                .andExpect(status().isOk());

        Processo processo = gravado(cenario);
        assertThat(processo.getSumario()).isEqualTo("Novo sumário.");
        assertThat(processo.getDadosFormulario()).contains("\"deliberacao\":{").contains("\"textoAto\":{");
    }

    @Test
    void deveRecusarComOsDoisCamposESemGravarNadaQuandoOProcedimentoNaoPrevElementos() throws Exception {
        Cenario cenario = criarCenario("00002");
        long versaoAntes = versao(cenario);

        gravar(cenario, jwtDe(cenario.autor(), cenario.entidade()), "Novo sumário.", informacaoBase(true, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("Informação Base não prevista neste procedimento."))
                .andExpect(jsonPath("$.campos", hasSize(2)))
                .andExpect(jsonPath("$.campos[0].path").value("informacaoBase.deliberacao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este procedimento não prevê Informação Base na Deliberação."))
                .andExpect(jsonPath("$.campos[1].path").value("informacaoBase.textoAto"))
                .andExpect(jsonPath("$.campos[1].erro").value("Este procedimento não prevê Informação Base no Texto do ato."));

        Processo processo = gravado(cenario);
        assertThat(processo.getSumario()).isEqualTo("Sumário original.");
        assertThat(processo.getVersao()).isEqualTo(versaoAntes);
        assertThat(processo.getDadosFormulario()).isNull();
    }

    @Test
    void deveRecusarSoADeliberacaoQuandoSoPrevTextoAto() throws Exception {
        Cenario cenario = criarCenario("00003", ElementoInformacaoBase.TEXTO_ATO);
        RequestPostProcessor jwt = jwtDe(cenario.autor(), cenario.entidade());

        gravar(cenario, jwt, "Só texto do ato.", informacaoBase(false, true)).andExpect(status().isOk());

        gravar(cenario, jwt, "Com deliberação.", informacaoBase(true, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("informacaoBase.deliberacao"));
        assertThat(gravado(cenario).getSumario()).isEqualTo("Só texto do ato.");
    }

    @Test
    void deveAceitarInformacaoBaseNulaOuSemElementosNumProcedimentoSemElementosPrevistos() throws Exception {
        Cenario cenario = criarCenario("00004");
        RequestPostProcessor jwt = jwtDe(cenario.autor(), cenario.entidade());

        gravar(cenario, jwt, "Sem informação base.", null).andExpect(status().isOk());
        assertThat(gravado(cenario).getDadosFormulario()).isNull();

        gravar(cenario, jwt, "Elementos vazios.", informacaoBase(false, false)).andExpect(status().isOk());
        assertThat(gravado(cenario).getDadosFormulario()).doesNotContain("\"deliberacao\":{").doesNotContain("\"textoAto\":{");
    }

    @Test
    void deveDevolver403AoQueNaoEAutorAntesDeVerificarAInformacaoBase() throws Exception {
        Cenario cenario = criarCenario("00005");
        Utilizador outro = criarUtilizador("outra00005");

        gravar(cenario, jwtDe(outro, cenario.entidade()), "Novo sumário.", informacaoBase(true, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolver409QuandoOProcessoJaFoiEnviadoParaADgt() throws Exception {
        Cenario cenario = criarCenario("00006");
        Processo processo = gravado(cenario);
        processo.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(processo);

        gravar(cenario, jwtDe(cenario.autor(), cenario.entidade()), "Novo sumário.", informacaoBase(true, true))
                .andExpect(status().isConflict());
    }

    @Test
    void naoDeveRegistarNoLogOConteudoDaInformacaoBaseRecusada(CapturedOutput saida) throws Exception {
        Cenario cenario = criarCenario("00007");
        String informacaoBase = "{\"deliberacao\":" + DELIBERACAO.formatted(TEXTO_RESERVADO) + "}";

        gravar(cenario, jwtDe(cenario.autor(), cenario.entidade()), "Sumário reservado.", informacaoBase)
                .andExpect(status().isBadRequest());

        assertThat(saida.getAll())
                .contains("processoId=" + cenario.processo().getId())
                .contains("informacaoBase.deliberacao")
                .doesNotContain(TEXTO_RESERVADO);
    }
}
