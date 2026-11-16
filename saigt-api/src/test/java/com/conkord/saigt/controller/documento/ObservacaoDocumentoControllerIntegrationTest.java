package com.conkord.saigt.controller.documento;

import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
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
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
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
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacao;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.EstruturaDocumentalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Observações de um item sem ficheiro (API-DOC-008) e o seu efeito na estrutura e na completude (BL-DOC-003). */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservacaoDocumentoControllerIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 conteudo".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    private DocumentoConfigRepository documentoConfigRepository;

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

    @Autowired
    private EstruturaDocumentalService estruturaDocumentalService;

    @Autowired
    private CompletudeDocumentalService completudeDocumentalService;

    @SpyBean
    private ProcessoDocumentoObservacaoRepository observacaoRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig item,
                           InstrumentoProcedimentoConfig procedimento) {
    }

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DOCO-" + codigo, "Instrumento " + codigo, "OUTRO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DOCO-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCO-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        DocumentoConfig novo = new DocumentoConfig(procedimento, "DELIBERACAO_DEP", "Deliberação", "Deliberação",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, FormatoDocumento.NAO_EDITAVEL, "[\"pdf\"]", 1);
        novo.setObrigatorio(true);
        DocumentoConfig item = documentoConfigRepository.saveAndFlush(novo);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("509100" + codigo, "Câmara O" + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor O" + codigo, "autoro" + codigo + "@cm-doc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOCO/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));
        return new Cenario(processo, autor, entidade, item, procedimento);
    }

    private RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String url(Processo processo, Long documentoConfigId) {
        return "/api/v1/processos/" + processo.getId() + "/documentos/observacoes/" + documentoConfigId;
    }

    private String corpo(String observacoes) throws Exception {
        return objectMapper.writeValueAsString(Map.of("observacoes", observacoes));
    }

    private ResultActions gravar(Cenario cenario, String observacoes) throws Exception {
        return mockMvc.perform(put(url(cenario.processo(), cenario.item().getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(observacoes))
                .with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    private Processo recarregar(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow();
    }

    /** Os serviços leem associações LAZY do Processo: chamam-se dentro de uma transação, como no pedido HTTP. */
    private List<Long> pendencias(Cenario cenario) {
        return transactionTemplate.execute(estado -> completudeDocumentalService.pendencias(recarregar(cenario.processo()))
                .stream().map(item -> item.config().getId()).toList());
    }

    private void verificarCompletude(Cenario cenario) {
        transactionTemplate.executeWithoutResult(estado -> completudeDocumentalService.verificar(recarregar(cenario.processo())));
    }

    @Test
    void deveGravarAsObservacoesETirarOItemDasPendencias() throws Exception {
        Cenario cenario = criarCenario("001");

        gravar(cenario, "  NA  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentoConfigId").value(cenario.item().getId()))
                .andExpect(jsonPath("$.observacoes").value("NA"));

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].deposito[0].observacoes").value("NA"))
                .andExpect(jsonPath("$.separadores[0].deposito[0].pendente").value(false))
                .andExpect(jsonPath("$.pendencias").isEmpty());
    }

    @Test
    void semObservacoesOItemObrigatorioContinuaPendenteEASubmissaoERecusada() {
        Cenario cenario = criarCenario("002");

        assertThat(pendencias(cenario)).containsExactly(cenario.item().getId());
        assertThatThrownBy(() -> verificarCompletude(cenario))
                .hasFieldOrPropertyWithValue("codigo", "DOCUMENTOS_OBRIGATORIOS_EM_FALTA");
    }

    @Test
    void comObservacoesASubmissaoNaoERecusadaPorDocumentosEmFalta() throws Exception {
        Cenario cenario = criarCenario("003");
        gravar(cenario, "NA").andExpect(status().isOk());

        verificarCompletude(cenario);
        assertThat(pendencias(cenario)).isEmpty();
    }

    @Test
    void deveSubstituirAsObservacoesNumaSoLinha() throws Exception {
        Cenario cenario = criarCenario("004");
        gravar(cenario, "Primeira versão").andExpect(status().isOk());

        gravar(cenario, "Segunda versão").andExpect(status().isOk());

        List<ProcessoDocumentoObservacao> linhas = observacaoRepository.findByIdProcessoId(cenario.processo().getId());
        assertThat(linhas).extracting(ProcessoDocumentoObservacao::getObservacoes).containsExactly("Segunda versão");
    }

    @Test
    void deveRecusarObservacoesVaziasOuSoComEspacos() throws Exception {
        Cenario cenario = criarCenario("005");

        for (String vazio : List.of("", "   ")) {
            gravar(cenario, vazio)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DOC_OBSERVACOES_VAZIAS"))
                    .andExpect(jsonPath("$.campos[0].path").value("observacoes"));
        }
        assertThat(observacaoRepository.findByIdProcessoId(cenario.processo().getId())).isEmpty();
    }

    @Test
    void deveRecusarObservacoesComMaisDeMilCaracteres() throws Exception {
        Cenario cenario = criarCenario("006");

        gravar(cenario, "x".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveRecusarUmItemDeOutroProcedimento() throws Exception {
        Cenario cenario = criarCenario("007");
        Cenario outro = criarCenario("008");

        mockMvc.perform(put(url(cenario.processo(), outro.item().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("NA"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("documentoConfigId"));
    }

    @Test
    void deveDevolver403AQuemNaoEOAutor() throws Exception {
        Cenario cenario = criarCenario("009");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro O009", "outroo009@cm-doc.pt", "INCM"));

        mockMvc.perform(put(url(cenario.processo(), cenario.item().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("NA"))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());
        assertThat(observacaoRepository.findByIdProcessoId(cenario.processo().getId())).isEmpty();
    }

    @Test
    void deveDevolver404AOutraEntidade() throws Exception {
        Cenario cenario = criarCenario("010");
        Cenario outra = criarCenario("011");

        mockMvc.perform(put(url(cenario.processo(), cenario.item().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("NA"))
                        .with(jwtDe(outra.entidade(), outra.autor().getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveDevolver401SemToken() throws Exception {
        Cenario cenario = criarCenario("012");

        mockMvc.perform(put(url(cenario.processo(), cenario.item().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("NA")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveDevolver409ForaDeEmSubmissao() throws Exception {
        Cenario cenario = criarCenario("013");
        Processo atual = recarregar(cenario.processo());
        atual.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(atual);

        gravar(cenario, "NA").andExpect(status().isConflict());
    }

    @Test
    void comObservacoesOItemContinuaCompletoDepoisDeRemoverOFicheiro() throws Exception {
        Cenario cenario = criarCenario("014");
        String resposta = mockMvc.perform(multipart("/api/v1/processos/" + cenario.processo().getId() + "/documentos")
                        .file(new MockMultipartFile("ficheiro", "deliberacao.pdf", "application/pdf", CONTEUDO))
                        .param("categoria", "DEPOSITO")
                        .param("documentoConfigId", cenario.item().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        gravar(cenario, "Justificação").andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/processos/" + cenario.processo().getId() + "/documentos/"
                        + objectMapper.readTree(resposta).get("id").asText())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(pendencias(cenario)).isEmpty();
    }

    @Test
    void deveAuditarOItemEOTamanhoSemOTexto() throws Exception {
        Cenario cenario = criarCenario("015");
        String texto = "Texto com o nome Maria";

        gravar(cenario, texto).andExpect(status().isOk());

        List<Auditoria> auditoria = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia(
                "PROCESSO", cenario.processo().getId().toString());
        assertThat(auditoria).extracting(Auditoria::getAcao).containsExactly("GRAVAR_OBSERVACOES_DOCUMENTO");
        JsonNode dados = objectMapper.readTree(auditoria.get(0).getDadosAlteracao());
        assertThat(dados.get("documentoConfigId").asLong()).isEqualTo(cenario.item().getId());
        assertThat(dados.get("caracteres").asInt()).isEqualTo(texto.length());
        assertThat(auditoria.get(0).getDadosAlteracao()).doesNotContain("Maria");
    }

    @Test
    void aEstruturaLeAsObservacoesNumaSoConsultaComVinteItens() throws Exception {
        Cenario cenario = criarCenario("016");
        for (int ordem = 2; ordem <= 20; ordem++) {
            documentoConfigRepository.saveAndFlush(new DocumentoConfig(cenario.procedimento(), "ITEM_" + ordem,
                    "Item " + ordem, "Deliberação", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA,
                    FormatoDocumento.NAO_EDITAVEL, "[\"pdf\"]", ordem));
        }
        gravar(cenario, "NA").andExpect(status().isOk());
        clearInvocations(observacaoRepository);

        transactionTemplate.executeWithoutResult(estado -> estruturaDocumentalService.montar(recarregar(cenario.processo())));

        verify(observacaoRepository, times(1)).findByIdProcessoId(cenario.processo().getId());
        verify(observacaoRepository, never()).findById(any());
    }
}
