package com.conkord.saigt.controller.documento;

import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
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
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Corrigir Processo» pela PCM (TR-104, TR-106): no `VALIDADO_DGT` que lhe foi atribuído, com a linha `PCM`, grava o
 * formulário e acrescenta documentos, sem autoria; fora disso, os erros de sempre. O autor e a DGT não mudam.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrecaoPelaPcmIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 texto final da RCM".getBytes(StandardCharsets.UTF_8);
    private static final String TEXTO_PUBLICAR = "Texto a publicar.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private UtilizadorEntidadeRepository utilizadorEntidadeRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private record SessaoPcm(Entidade entidade, Utilizador utilizador) {
    }

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig textoRcm,
                           SessaoPcm pcm) {
    }

    /** Só um tipo do catálogo de formulários grava; o `codigo` é único na BD partilhada pelos testes. */
    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC"))
                .findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    private SessaoPcm novaSessaoPcm(String nif, String sufixo) {
        Entidade pcm = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "PCM " + nif, TipoEntidade.PCM));
        Utilizador utilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Utilizador PCM " + sufixo, "pcm" + sufixo + "@pcm.gov.pt", "INCM"));
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(utilizador, pcm, Instant.now()));
        return new SessaoPcm(pcm, utilizador);
    }

    /**
     * Processo de 1.ª série já submetido e validado pela DGT, atribuído à PCM (ou ao autor em `EM_SUBMISSAO`, com
     * `atribuido` falso), com o item do texto final da RCM e, com `linhaPcm`, a linha `permissao_acao` do tipo `PCM`.
     */
    private Cenario criarCenario(String codigo, EstadoProcesso estado, boolean atribuido, boolean linhaPcm,
                                 List<String> camposDesbloqueados) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PCMC-" + codigo, "Instrumento " + codigo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-PCMC-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoComFormulario(), 1, "PRIMEIRA"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        if (linhaPcm) {
            permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.PCM, procedimento));
        }
        DocumentoConfig itemDeliberacao = new DocumentoConfig(procedimento, "DELIBERACAO_PUB", "Deliberação", "Deliberação",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        itemDeliberacao.setElementoInformacaoBase(ElementoInformacaoBase.DELIBERACAO);
        documentoConfigRepository.saveAndFlush(itemDeliberacao);
        DocumentoConfig textoRcm = new DocumentoConfig(procedimento, "TEXTO_RCM", "Texto final da RCM", "Peças Escritas",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 2);
        textoRcm.setIndependenteElementosAfetados(true);
        textoRcm = documentoConfigRepository.saveAndFlush(textoRcm);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5238000" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-pcmc.pt", "INCM"));
        SessaoPcm pcm = novaSessaoPcm("5239000" + codigo, "238" + codigo);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PCMC/" + codigo, autor, autor));
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
        processo.setDataSubmissao(Instant.now());
        processo.setEstado(estado);
        if (atribuido) {
            processo.setPcmAtribuidoA(pcm.utilizador());
        }
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade, textoRcm, pcm);
    }

    private Cenario atribuidoAPcm(String codigo) {
        return criarCenario(codigo, EstadoProcesso.VALIDADO_DGT, true, true, List.of());
    }

    private static RequestPostProcessor jwtPcm(SessaoPcm sessao) {
        return jwt().jwt(builder -> builder.claim(SUB, sessao.utilizador().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "PCM", "id", sessao.entidade().getId(),
                        "nif", sessao.entidade().getNipc(), "nome", "PCM", "pcm", true)));
    }

    private static RequestPostProcessor jwtAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(),
                        "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(), "pcm", false,
                        "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtMaster() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "MASTER"));
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private String corpo(Cenario cenario, String sumario, String textoPublicar) {
        return "{\"versao\":" + gravado(cenario).getVersao() + ",\"sumario\":\"" + sumario + "\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"],\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\""
                + textoPublicar + "\"}}}";
    }

    private ResultActions gravar(Cenario cenario, RequestPostProcessor autenticacao, String sumario) throws Exception {
        return gravar(cenario, autenticacao, sumario, TEXTO_PUBLICAR);
    }

    private ResultActions gravar(Cenario cenario, RequestPostProcessor autenticacao, String sumario, String textoPublicar)
            throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(autenticacao).contentType("application/json").content(corpo(cenario, sumario, textoPublicar)));
    }

    private ResultActions carregar(Cenario cenario, RequestPostProcessor autenticacao) throws Exception {
        return mockMvc.perform(multipart("/api/v1/processos/" + cenario.processo().getId() + "/documentos")
                .file(new MockMultipartFile("ficheiro", "Texto_RCM.pdf", "application/pdf", CONTEUDO))
                .param("categoria", "PUBLICACAO")
                .param("documentoConfigId", cenario.textoRcm().getId().toString())
                .with(autenticacao));
    }

    private List<ProcessoDocumento> documentos(Cenario cenario) {
        return processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId());
    }

    private List<String> acoesAuditadas(Cenario cenario) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", cenario.processo().getId().toString())
                .stream().map(Auditoria::getAcao).toList();
    }

    /** O pedido recusado não grava nada: a mesma `versao`, estado, atribuição e documentos. */
    private void nadaGravado(Cenario cenario, Processo antes) {
        Processo depois = gravado(cenario);
        assertThat(depois.getVersao()).isEqualTo(antes.getVersao());
        assertThat(depois.getEstado()).isEqualTo(antes.getEstado());
        assertThat(depois.getSumario()).isEqualTo(antes.getSumario());
        assertThat(documentos(cenario)).isEmpty();
    }

    @Test
    void aPcmAtribuidaVeODetalheEditavelComDevolverDgtESemSubmeter() throws Exception {
        Cenario cenario = atribuidoAPcm("01");

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtPcm(cenario.pcm())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.destinoEditavel").value(false))
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.acoesDisponiveis", hasItem("devolver-dgt")))
                .andExpect(jsonPath("$.acoesDisponiveis", not(hasItem("submeter"))));
    }

    @Test
    void aPcmAtribuidaGravaOFormularioComOs200MantendoOEstadoEAAtribuicao() throws Exception {
        Cenario cenario = atribuidoAPcm("02");
        long versaoAntes = gravado(cenario).getVersao();

        gravar(cenario, jwtPcm(cenario.pcm()), "Resumo do ato.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"))
                .andExpect(jsonPath("$.pcmAtribuidoAId").value(cenario.pcm().utilizador().getId().toString()));

        Processo depois = gravado(cenario);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(depois.getPcmAtribuidoA().getId()).isEqualTo(cenario.pcm().utilizador().getId());
        assertThat(depois.getVersao()).isEqualTo(versaoAntes + 1);
    }

    @Test
    void aPcmAlteraUmCampoEditavelSegundoAsRegrasDeBloqueioDeHoje() throws Exception {
        Cenario cenario = criarCenario("03", EstadoProcesso.VALIDADO_DGT, true, true,
                List.of("informacaoBase.deliberacao.textoPublicar"));

        gravar(cenario, jwtPcm(cenario.pcm()), "Resumo do ato.", "Texto confirmado pela PCM.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));

        assertThat(gravado(cenario).getDadosFormulario()).contains("Texto confirmado pela PCM.");
        assertThat(gravado(cenario).getPcmAtribuidoA().getId()).isEqualTo(cenario.pcm().utilizador().getId());
    }

    @Test
    void aPcmQueAlteraUmCampoBloqueadoRecebeCampoBloqueadoComoOAutor() throws Exception {
        Cenario cenario = atribuidoAPcm("04");
        Processo antes = gravado(cenario);

        gravar(cenario, jwtPcm(cenario.pcm()), "Resumo alterado pela PCM.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[0].path").value("sumario"));

        nadaGravado(cenario, antes);
    }

    @Test
    void aPcmAtribuidaAcrescentaOTextoDaRcmCom201PorValidarEAuditado() throws Exception {
        Cenario cenario = atribuidoAPcm("05");

        carregar(cenario, jwtPcm(cenario.pcm()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoValidacao").value("POR_VALIDAR"))
                .andExpect(jsonPath("$.documentoConfigId").value(cenario.textoRcm().getId()))
                .andExpect(jsonPath("$.criadoPorId").value(cenario.pcm().utilizador().getId().toString()))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty());

        assertThat(documentos(cenario)).singleElement().satisfies(documento -> {
            assertThat(documento.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
            assertThat(documento.getCriadoPor().getId()).isEqualTo(cenario.pcm().utilizador().getId());
        });
        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(acoesAuditadas(cenario)).containsExactly("CARREGAR_DOCUMENTO");
    }

    @Test
    void aPcmNaoSubstituiNemRemoveOsFicheirosExistentes() throws Exception {
        Cenario cenario = atribuidoAPcm("06");
        carregar(cenario, jwtPcm(cenario.pcm())).andExpect(status().isCreated());
        UUID documentoId = documentos(cenario).get(0).getId();
        String url = "/api/v1/processos/" + cenario.processo().getId() + "/documentos/" + documentoId;

        mockMvc.perform(multipart(HttpMethod.PUT, url)
                        .file(new MockMultipartFile("ficheiro", "Outro.pdf", "application/pdf", CONTEUDO))
                        .with(jwtPcm(cenario.pcm())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));
        mockMvc.perform(delete(url).with(jwtPcm(cenario.pcm())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));

        assertThat(documentos(cenario)).singleElement()
                .satisfies(documento -> assertThat(documento.getNomeFicheiro()).isEqualTo("Texto_RCM.pdf"));
    }

    @Test
    void aPcmComOProcessoAtribuidoNoutroEstadoRecebe409ENadaEGravado() throws Exception {
        Cenario cenario = criarCenario("07", EstadoProcesso.EM_VALIDACAO_INCM, true, true, List.of());
        Processo antes = gravado(cenario);

        gravar(cenario, jwtPcm(cenario.pcm()), "Resumo do ato.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        carregar(cenario, jwtPcm(cenario.pcm()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));

        nadaGravado(cenario, antes);
    }

    @Test
    void aPcmComOProcessoAtribuidoAOutroUtilizadorRecebe404PelaVisibilidadeENadaEGravado() throws Exception {
        Cenario cenario = atribuidoAPcm("08");
        SessaoPcm colega = novaSessaoPcm("523990008", "238c08");
        Processo antes = gravado(cenario);

        gravar(cenario, jwtPcm(colega), "Resumo do ato.").andExpect(status().isNotFound());
        carregar(cenario, jwtPcm(colega)).andExpect(status().isNotFound());

        nadaGravado(cenario, antes);
    }

    @Test
    void semALinhaPcmNaConfiguracaoAPcmAtribuidaRecebe403ENadaEGravado() throws Exception {
        Cenario cenario = criarCenario("09", EstadoProcesso.VALIDADO_DGT, true, false, List.of());
        Processo antes = gravado(cenario);

        gravar(cenario, jwtPcm(cenario.pcm()), "Resumo do ato.")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        carregar(cenario, jwtPcm(cenario.pcm()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtPcm(cenario.pcm())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(false));

        nadaGravado(cenario, antes);
    }

    @Test
    void oAutorEmSubmissaoContinuaAGravarECarregar() throws Exception {
        Cenario cenario = criarCenario("10", EstadoProcesso.EM_SUBMISSAO, false, true, List.of());

        gravar(cenario, jwtAutor(cenario), "Resumo do ato.").andExpect(status().isOk());
        carregar(cenario, jwtAutor(cenario)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(true));
    }

    @Test
    void oAutorEADgtNaoGravamNemCarregamNoValidadoDgtAtribuidoAPcm() throws Exception {
        Cenario cenario = atribuidoAPcm("11");
        Processo antes = gravado(cenario);

        gravar(cenario, jwtAutor(cenario), "Resumo do ato.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        carregar(cenario, jwtAutor(cenario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));
        gravar(cenario, jwtMaster(), "Resumo do ato.").andExpect(status().isForbidden());
        carregar(cenario, jwtMaster()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtMaster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(false));

        nadaGravado(cenario, antes);
    }
}
