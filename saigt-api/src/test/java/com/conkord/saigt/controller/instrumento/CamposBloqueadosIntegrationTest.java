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
class CamposBloqueadosIntegrationTest {

    private static final String TEXTO_PUBLICAR = "Texto a publicar.";

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

    private record Cenario(Processo processo, Utilizador autor, Utilizador outro, Entidade entidade) {
    }

    /** Só um tipo do catálogo de formulários grava e submete; o `codigo` é único na BD partilhada pelos testes. */
    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    /** Valores gravados: os do pedido de {@link #corpo}, com a informação base e os elementos afetados preenchidos. */
    private Cenario criarCenario(String codigo, boolean submetido, List<String> camposDesbloqueados) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("BLOQ-" + codigo, "Instrumento " + codigo, "SRUP"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-BLOQ-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoComFormulario();
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        DocumentoConfig itemDeliberacao = new DocumentoConfig(procedimento, "DELIBERACAO_PUB", "Deliberação", "Deliberação",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        itemDeliberacao.setElementoInformacaoBase(ElementoInformacaoBase.DELIBERACAO);
        documentoConfigRepository.saveAndFlush(itemDeliberacao);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5093" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-bloq.pt", "INCM"));
        Utilizador outro = utilizadorRepository.saveAndFlush(
                new Utilizador("Outro " + codigo, "outro" + codigo + "@cm-bloq.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/BLOQ/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"" + TEXTO_PUBLICAR + "\"}}}");
        processo.setCamposDesbloqueados(camposDesbloqueados);
        if (submetido) {
            processo.setDataSubmissao(Instant.now());
        }
        return new Cenario(processoRepository.saveAndFlush(processo), autor, outro, entidade);
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

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    private String corpo(Cenario cenario, String sumario, String dataAssinatura, String normaLegal, String elementosAfetados,
                         String textoPublicar) {
        return "{\"versao\":" + versao(cenario) + ",\"sumario\":\"" + sumario + "\",\"dataAssinatura\":\"" + dataAssinatura
                + "\",\"normaLegal\":\"" + normaLegal + "\",\"tipoInformacao\":\"TEXTO_INTEGRAL\",\"elementosAfetados\":"
                + elementosAfetados + ",\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"" + textoPublicar + "\"}}}";
    }

    private ResultActions gravar(Cenario cenario, String sumario, String elementosAfetados, String textoPublicar) throws Exception {
        return gravar(cenario, jwtDoAutor(cenario), sumario, "Norma Legal X", elementosAfetados, textoPublicar);
    }

    private ResultActions gravar(Cenario cenario, RequestPostProcessor autenticacao, String sumario, String normaLegal,
                                 String elementosAfetados, String textoPublicar) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(autenticacao)
                .contentType("application/json")
                .content(corpo(cenario, sumario, LocalDate.now().toString(), normaLegal, elementosAfetados, textoPublicar)));
    }

    private ResultActions submeter(Cenario cenario, LocalDate dataAssinatura) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\"" + dataAssinatura
                + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    @Test
    void processoNuncaSubmetido_aceitaQualquerAlteracaoEnaoTemCamposBloqueados() throws Exception {
        Cenario cenario = criarCenario("2001", false, null);

        gravar(cenario, "Outro resumo.", "[\"OUTROS\"]", "Outro texto.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados.length()").value(0));

        assertThat(gravado(cenario).getSumario()).isEqualTo("Outro resumo.");
    }

    @Test
    void processoCorrigido_recusaMudarOSumarioENadaEGravado() throws Exception {
        Cenario cenario = criarCenario("2002", true, null);

        gravar(cenario, "Outro resumo.", "[\"PECAS_ESCRITAS\"]", TEXTO_PUBLICAR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.mensagem").value("Desbloqueie o campo antes de o alterar."))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("sumario"))
                .andExpect(jsonPath("$.campos[0].erro").value("Campo bloqueado depois da primeira submissão."));

        Processo depois = gravado(cenario);
        assertThat(depois.getSumario()).isEqualTo("Resumo do ato.");
        assertThat(depois.getVersao()).isEqualTo(cenario.processo().getVersao());
    }

    @Test
    void processoCorrigido_aceitaOsMesmosValoresGravados() throws Exception {
        Cenario cenario = criarCenario("2003", true, null);

        gravar(cenario, "Resumo do ato.", "[\"PECAS_ESCRITAS\"]", TEXTO_PUBLICAR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados.length()").value(31));
    }

    @Test
    void processoCorrigido_recusaMudarATextoPublicarEOsElementosAfetados() throws Exception {
        Cenario cenario = criarCenario("2004", true, null);

        gravar(cenario, "Resumo do ato.", "[\"PECAS_GRAFICAS\"]", "Outro texto.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[0].path").value("elementosAfetados"))
                .andExpect(jsonPath("$.campos[1].path").value("informacaoBase.deliberacao.textoPublicar"));
    }

    @Test
    void submeter_recusaOutraDataDeAssinaturaEAceitaAMesma() throws Exception {
        Cenario cenario = criarCenario("2005", true, null);

        submeter(cenario, LocalDate.now().minusDays(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[0].path").value("dataAssinatura"));
        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        submeter(cenario, LocalDate.now())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void processoResgatado_recusaMudarUmCampo() throws Exception {
        Cenario cenario = criarCenario("2006", true, null);
        Processo processo = gravado(cenario);
        processo.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(processo);

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/resgatar")
                        .with(jwtDoAutor(cenario))
                        .contentType("application/json")
                        .content("{\"versao\":" + versao(cenario) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.camposBloqueados.length()").value(31));

        gravar(cenario, "Outro resumo.", "[\"PECAS_ESCRITAS\"]", TEXTO_PUBLICAR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"));
    }

    @Test
    void campoDesbloqueado_podeMudarVoltaABloquearEOsRestantesContinuamBloqueados() throws Exception {
        Cenario cenario = criarCenario("2007", true, List.of("sumario"));
        assertThat(gravado(cenario).getCamposDesbloqueados()).containsExactly("sumario");

        gravar(cenario, "Outro resumo.", "[\"PECAS_ESCRITAS\"]", TEXTO_PUBLICAR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados.length()").value(31));
        assertThat(gravado(cenario).getSumario()).isEqualTo("Outro resumo.");
        assertThat(gravado(cenario).getCamposDesbloqueados()).isEmpty();

        gravar(cenario, jwtDoAutor(cenario), "Outro resumo.", "Outra norma.", "[\"PECAS_ESCRITAS\"]", TEXTO_PUBLICAR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("normaLegal"));
    }

    @Test
    void detalheDeUmProcessoSubmetido_listaAs22Chaves() throws Exception {
        Cenario cenario = criarCenario("2008", true, null);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados.length()").value(31))
                .andExpect(jsonPath("$.camposBloqueados[0]").value("sumario"))
                .andExpect(jsonPath("$.camposBloqueados[20]").value("informacaoBase.textoAto.avaliacaoAmbiental"))
                .andExpect(jsonPath("$.camposBloqueados[21]").value("introduzPlantaNova"));
    }

    @Test
    void quemNaoEAutorRecebe403AntesDaRegraENaoAutenticadoRecebe401() throws Exception {
        Cenario cenario = criarCenario("2009", true, null);

        gravar(cenario, jwtDe(cenario.outro(), cenario.entidade()), "Outro resumo.", "Norma Legal X",
                "[\"PECAS_ESCRITAS\"]", TEXTO_PUBLICAR)
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                        .contentType("application/json")
                        .content(corpo(cenario, "Outro resumo.", LocalDate.now().toString(), "Norma Legal X",
                                "[\"PECAS_ESCRITAS\"]", TEXTO_PUBLICAR)))
                .andExpect(status().isUnauthorized());
    }
}
