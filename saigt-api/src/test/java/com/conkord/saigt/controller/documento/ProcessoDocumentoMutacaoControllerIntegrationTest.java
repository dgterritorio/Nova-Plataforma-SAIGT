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
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.documento.ProcessoDocumentoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Substituição e remoção (§14.3, §14.5), e a linha da planta e as fichas MOTU que acompanham a peça gráfica, contra
 * storage real.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessoDocumentoMutacaoControllerIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 versão original".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTEUDO_NOVO = "%PDF-1.4 versão corrigida, mais longa".getBytes(StandardCharsets.UTF_8);
    private static final long MB = 1024L * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioClient minioClient;

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
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private PlantaRepository plantaRepository;

    @Autowired
    private MetadadosMotuRepository metadadosMotuRepository;

    @Autowired
    private ProcessoDocumentoService processoDocumentoService;

    @SpyBean
    private AuditoriaService auditoriaService;

    private record Cenario(Instrumento instrumento, Processo processo, Utilizador autor, Entidade entidade,
                           DocumentoConfig documentoConfig) {
    }

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DOCM-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DOCM-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCM-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig documentoConfig = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"pdf\"]", 1));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("507000" + codigo, "Câmara M" + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor M" + codigo, "autorm" + codigo + "@cm-doc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOCM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        limparObjetos("processos/" + processo.getNumeroProcesso() + "/");
        return new Cenario(instrumento, processo, autor, entidade, documentoConfig);
    }

    private RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private static String url(Processo processo, UUID documentoId) {
        return url(processo) + "/" + documentoId;
    }

    /** Carrega pelo endpoint real e devolve a linha gravada. */
    private ProcessoDocumento carregar(Cenario cenario, String nome) throws Exception {
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", nome, "application/pdf", ZipsDeTeste.conteudo(nome, CONTEUDO)))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return processoDocumentoRepository.findById(UUID.fromString(objectMapper.readTree(resposta).get("id").asText()))
                .orElseThrow();
    }

    private static MockMultipartFile ficheiroNovo(String nome) {
        return new MockMultipartFile("ficheiro", nome, "application/pdf", ZipsDeTeste.conteudo(nome, CONTEUDO_NOVO));
    }

    private void colocarEmEstado(Processo processo, EstadoProcesso estado) {
        Processo atual = processoRepository.findById(processo.getId()).orElseThrow();
        atual.setEstado(estado);
        processoRepository.saveAndFlush(atual);
    }

    @Test
    void deveSubstituirMantendoIdESlotERecalcularHashETamanho() throws Exception {
        Cenario cenario = criarCenario("001");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), original.getId()))
                        .file(ficheiroNovo("regulamento_v2.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId().toString()))
                .andExpect(jsonPath("$.documentoConfigId").value(cenario.documentoConfig().getId()))
                .andExpect(jsonPath("$.nomeFicheiro").value("regulamento_v2.pdf"))
                .andExpect(jsonPath("$.tamanhoBytes").value(CONTEUDO_NOVO.length));

        ProcessoDocumento substituido = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(substituido.getDocumentoConfig().getId()).isEqualTo(cenario.documentoConfig().getId());
        assertThat(substituido.getCategoria()).isEqualTo(CategoriaDocumento.PUBLICACAO);
        assertThat(substituido.getExtensao()).isEqualTo("pdf");
        assertThat(substituido.getCaminhoFicheiro()).contains("/PUBLICACAO/");
        assertThat(substituido.getVersaoObjeto()).isNotBlank().isNotEqualTo("null");
        assertThat(substituido.getHashSha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTEUDO_NOVO)));
        assertThat(substituido.getTamanhoBytes()).isEqualTo(CONTEUDO_NOVO.length);
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(substituido.getCaminhoFicheiro());

        List<Auditoria> auditoria = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia(
                "PROCESSO", cenario.processo().getId().toString());
        assertThat(auditoria).extracting(Auditoria::getAcao)
                .containsExactly("CARREGAR_DOCUMENTO", "SUBSTITUIR_DOCUMENTO");
        JsonNode dados = objectMapper.readTree(auditoria.get(1).getDadosAlteracao());
        assertThat(dados.get("processoDocumentoId").asText()).isEqualTo(original.getId().toString());
        assertThat(dados.get("categoria").asText()).isEqualTo("PUBLICACAO");
        assertThat(dados.get("hashSha256Anterior").asText()).isEqualTo(original.getHashSha256());
    }

    @Test
    void deveSubstituirNumItemQueJaTemONumeroMaximoDeFicheiros() throws Exception {
        Cenario cenario = criarCenario("201");
        limitarItem(cenario.documentoConfig(), null, null, 2);
        ProcessoDocumento primeiro = carregar(cenario, "regulamento.pdf");
        carregar(cenario, "anexo.pdf");

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), primeiro.getId()))
                        .file(ficheiroNovo("regulamento_v2.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeFicheiro").value("regulamento_v2.pdf"));
    }

    @Test
    void naoDeveContarOFicheiroSubstituidoNoTotalDoItem() throws Exception {
        Cenario cenario = criarCenario("202");
        limitarItem(cenario.documentoConfig(), null, 3 * MB, null);
        ProcessoDocumento aSubstituir = jaCarregado(cenario, 2 * MB);
        jaCarregado(cenario, MB);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), aSubstituir.getId()))
                        .file(new MockMultipartFile("ficheiro", "regulamento_v2.pdf", "application/pdf",
                                new byte[(int) (2 * MB)]))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanhoBytes").value(2 * MB));
    }

    @Test
    void deveRecusarASubstituicaoQueNaoCabeNoTotalDoItemEManterOOriginal() throws Exception {
        Cenario cenario = criarCenario("203");
        limitarItem(cenario.documentoConfig(), null, 3 * MB, null);
        ProcessoDocumento aSubstituir = jaCarregado(cenario, 2 * MB);
        jaCarregado(cenario, MB);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), aSubstituir.getId()))
                        .file(new MockMultipartFile("ficheiro", "regulamento_v2.pdf", "application/pdf",
                                new byte[(int) (2 * MB + 1)]))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TAMANHO_EXCEDIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value("Limite total do item: 3 MB."));

        ProcessoDocumento mantido = processoDocumentoRepository.findById(aSubstituir.getId()).orElseThrow();
        assertThat(mantido.getCaminhoFicheiro()).isEqualTo(aSubstituir.getCaminhoFicheiro());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    private void limitarItem(DocumentoConfig item, Long porFicheiro, Long total, Integer numero) {
        DocumentoConfig atual = documentoConfigRepository.findById(item.getId()).orElseThrow();
        atual.setTamanhoMaximoFicheiro(porFicheiro);
        atual.setTamanhoMaximoTotal(total);
        atual.setNumeroMaximoFicheiros(numero);
        documentoConfigRepository.saveAndFlush(atual);
    }

    /** Só a linha: para os limites conta o que está na BD, não no bucket. */
    private ProcessoDocumento jaCarregado(Cenario cenario, long tamanhoBytes) {
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(),
                cenario.documentoConfig(), CategoriaDocumento.PUBLICACAO, "pdf", "existente.pdf",
                "processos/" + cenario.processo().getNumeroProcesso() + "/PUBLICACAO/" + UUID.randomUUID()
                        + "-existente.pdf", "a".repeat(64), "v1", tamanhoBytes, cenario.autor()));
    }

    @Test
    void deveAtualizarAExtensaoMantendoACategoriaNumDocumentoSemItem() throws Exception {
        Cenario cenario = criarCenario("015");
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", "notas.txt", "text/plain", CONTEUDO))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documentoId))
                        .file(new MockMultipartFile("ficheiro", "notas.md", "text/markdown", CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("DEPOSITO"))
                .andExpect(jsonPath("$.extensao").value("md"));

        ProcessoDocumento substituido = processoDocumentoRepository.findById(documentoId).orElseThrow();
        assertThat(substituido.getExtensao()).isEqualTo("md");
        assertThat(substituido.getCategoria()).isEqualTo(CategoriaDocumento.DEPOSITO);
        assertThat(substituido.getDocumentoConfig()).isNull();
        assertThat(substituido.getCaminhoFicheiro()).contains("/DEPOSITO/").endsWith("-notas.md");
        assertThat(substituido.getVersaoObjeto()).isNotBlank().isNotEqualTo("null");
    }

    @Test
    void deveGravarNumaChaveNovaQuandoOFicheiroNovoTemOMesmoNome() throws Exception {
        Cenario cenario = criarCenario("002");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), original.getId()))
                        .file(ficheiroNovo("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk());

        String caminho = processoDocumentoRepository.findById(original.getId()).orElseThrow().getCaminhoFicheiro();
        assertThat(caminho).isNotEqualTo(original.getCaminhoFicheiro())
                .matches(".*/PUBLICACAO/" + original.getId() + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).containsExactly(caminho);
    }

    @Test
    void deveManterOriginalQuandoASubstituicaoReverte() throws Exception {
        Cenario cenario = criarCenario("003");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");
        doThrow(new IllegalStateException("falha simulada")).when(auditoriaService).registar(eq("PROCESSO"),
                anyString(), eq("SUBSTITUIR_DOCUMENTO"), any(), any(), any(), any());

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), original.getId()))
                        .file(ficheiroNovo("regulamento_v2.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isInternalServerError());

        ProcessoDocumento atual = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(original.getCaminhoFicheiro());
        assertThat(atual.getHashSha256()).isEqualTo(original.getHashSha256());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(original.getCaminhoFicheiro());
    }

    @Test
    void deveDevolver400QuandoAExtensaoNovaNaoEAceiteNoSlot() throws Exception {
        Cenario cenario = criarCenario("004");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), original.getId()))
                        .file(ficheiroNovo("regulamento.docx"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"))
                .andExpect(jsonPath("$.mensagem").value("Extensão não aceite para este documento."))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"));

        ProcessoDocumento atual = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(atual.getNomeFicheiro()).isEqualTo("regulamento.pdf");
        assertThat(atual.getHashSha256()).isEqualTo(original.getHashSha256());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(original.getCaminhoFicheiro());
    }

    @Test
    void deveDevolver400EManterODocumentoQuandoNomeNovoEstaForaDasRegras() throws Exception {
        Cenario cenario = criarCenario("801");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), original.getId()))
                        .file(ficheiroNovo("regulamento final.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_NOME_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"));

        ProcessoDocumento atual = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(atual.getNomeFicheiro()).isEqualTo("regulamento.pdf");
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(original.getCaminhoFicheiro());
        assertThat(atual.getHashSha256()).isEqualTo(original.getHashSha256());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(original.getCaminhoFicheiro());
    }

    @Test
    void deveRemoverALinhaEOObjeto() throws Exception {
        Cenario cenario = criarCenario("005");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");

        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(processoDocumentoRepository.findById(documento.getId())).isEmpty();
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
        List<Auditoria> auditoria = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia(
                "PROCESSO", cenario.processo().getId().toString());
        assertThat(auditoria).extracting(Auditoria::getAcao).containsExactly("CARREGAR_DOCUMENTO", "REMOVER_DOCUMENTO");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = "EM_SUBMISSAO", mode = EnumSource.Mode.EXCLUDE)
    void deveDevolver409NaSubstituicaoENaRemocaoForaDeEmSubmissao(EstadoProcesso estado) throws Exception {
        Cenario cenario = criarCenario(String.format("%03d", 100 + estado.ordinal()));
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        colocarEmEstado(cenario.processo(), estado);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documento.getId()))
                        .file(ficheiroNovo("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));
        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));

        ProcessoDocumento atual = processoDocumentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(atual.getHashSha256()).isEqualTo(documento.getHashSha256());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(documento.getCaminhoFicheiro());
    }

    @Test
    void deveDevolver409DocBloqueadoNaSubstituicaoENaRemocaoDeDocumentoBloqueado() throws Exception {
        Cenario cenario = criarCenario("014");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        documento.setBloqueadoEdicao(true);
        processoDocumentoRepository.saveAndFlush(documento);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documento.getId()))
                        .file(ficheiroNovo("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));
        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));

        ProcessoDocumento atual = processoDocumentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(atual.getHashSha256()).isEqualTo(documento.getHashSha256());
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(documento.getCaminhoFicheiro());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(documento.getCaminhoFicheiro());
    }

    private ProcessoDocumento comEstadoValidacao(ProcessoDocumento documento, EstadoValidacao estado) {
        ProcessoDocumento atual = processoDocumentoRepository.findById(documento.getId()).orElseThrow();
        atual.setEstadoValidacao(estado);
        return processoDocumentoRepository.saveAndFlush(atual);
    }

    @Test
    void deveBloquearOValidadoEDeixarSubstituirORecusadoDepoisDeCorrigir() throws Exception {
        Cenario cenario = criarCenario("301");
        ProcessoDocumento validado = comEstadoValidacao(carregar(cenario, "regulamento.pdf"), EstadoValidacao.VALIDADO);
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", "anexo.pdf", "application/pdf", CONTEUDO))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID recusadoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        comEstadoValidacao(processoDocumentoRepository.findById(recusadoId).orElseThrow(), EstadoValidacao.REJEITADO);
        colocarEmEstado(cenario.processo(), EstadoProcesso.REJEITADO_DGT);
        long versao = processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/corrigir")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId()))
                        .contentType("application/json").content("{\"versao\":" + versao + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), validado.getId()))
                        .file(ficheiroNovo("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"))
                .andExpect(jsonPath("$.mensagem").value("O documento está bloqueado e já não pode ser alterado."));
        mockMvc.perform(delete(url(cenario.processo(), validado.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));

        ProcessoDocumento atual = processoDocumentoRepository.findById(validado.getId()).orElseThrow();
        assertThat(atual.isBloqueadoEdicao()).isTrue();
        assertThat(atual.getHashSha256()).isEqualTo(validado.getHashSha256());
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(validado.getCaminhoFicheiro());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .contains(validado.getCaminhoFicheiro());

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), recusadoId))
                        .file(ficheiroNovo("anexo.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk());
    }

    @Test
    void deveDevolver409DocBloqueadoNaSubstituicaoENaRemocaoDeDocumentoSubstituido() throws Exception {
        Cenario cenario = criarCenario("016");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        documento.setEstadoValidacao(EstadoValidacao.SUBSTITUIDO);
        processoDocumentoRepository.saveAndFlush(documento);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documento.getId()))
                        .file(ficheiroNovo("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"))
                .andExpect(jsonPath("$.mensagem").value("Este ficheiro já foi substituído e só pode ser consultado."));
        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));

        ProcessoDocumento atual = processoDocumentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(documento.getCaminhoFicheiro());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(documento.getCaminhoFicheiro());
    }

    @Test
    void deveRemoverODocumentoRejeitadoMovendoOParaSubstituidosSemApagarOFicheiro() throws Exception {
        Cenario cenario = criarCenario("017");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        documento.setEstadoValidacao(EstadoValidacao.REJEITADO);
        processoDocumentoRepository.saveAndFlush(documento);

        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        ProcessoDocumento atual = processoDocumentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(atual.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        assertThat(atual.getCaminhoFicheiro())
                .startsWith("processos/" + cenario.processo().getNumeroProcesso() + "/SUBSTITUIDOS/");
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(atual.getCaminhoFicheiro());
    }

    @Test
    void naoDeveContarOFicheiroSubstituidoNoNumeroMaximoDoItem() throws Exception {
        Cenario cenario = criarCenario("018");
        limitarItem(cenario.documentoConfig(), null, null, 1);
        ProcessoDocumento recusado = carregar(cenario, "regulamento.pdf");
        recusado.setEstadoValidacao(EstadoValidacao.SUBSTITUIDO);
        processoDocumentoRepository.saveAndFlush(recusado);

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", "novo.pdf", "application/pdf", CONTEUDO_NOVO))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void deveDevolver403QuandoQuemAlteraNaoEOAutor() throws Exception {
        Cenario cenario = criarCenario("006");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro M006", "outrom006@cm-doc.pt", "INCM"));

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documento.getId()))
                        .file(ficheiroNovo("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());

        assertThat(processoDocumentoRepository.findById(documento.getId())).isPresent();
    }

    @Test
    void deveDevolver404QuandoODocumentoEDeOutroProcesso() throws Exception {
        Cenario cenario = criarCenario("007");
        Cenario outro = criarCenario("008");
        ProcessoDocumento documentoDoOutro = carregar(outro, "regulamento.pdf");

        mockMvc.perform(delete(url(cenario.processo(), documentoDoOutro.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documentoDoOutro.getId()))
                        .file(ficheiroNovo("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNotFound());

        assertThat(processoDocumentoRepository.findById(documentoDoOutro.getId())).isPresent();
    }

    @Test
    void deveRemoverTodosOsDocumentosDoProcessoParaAAnulacaoPorInatividade() throws Exception {
        Cenario cenario = criarCenario("009");
        carregar(cenario, "regulamento.pdf");
        carregar(cenario, "planta.pdf");

        processoDocumentoService.removerTodosDoProcesso(cenario.processo().getId());

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).isEmpty();
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    /** O bucket é partilhado entre classes e o número do processo recomeça com cada BD de teste. */
    private void limparObjetos(String prefixo) {
        for (String objeto : objetosSob(prefixo)) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(BUCKET).object(objeto).build());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private DocumentoConfig itemDeDeposito(Cenario cenario, String codigo, TipoElemento tipoElemento) {
        return documentoConfigRepository.saveAndFlush(new DocumentoConfig(
                cenario.documentoConfig().getInstrumentoProcedimentoConfig(), codigo, "Planta de Localização",
                "Planta com área de intervenção", CategoriaDocumento.DEPOSITO, tipoElemento,
                FormatoDocumento.NAO_EDITAVEL, "[\"zip\",\"tif\",\"tiff\",\"tfw\",\"gpkg\"]", 2));
    }

    /** Carrega em depósito; sem item, vai para o balde «outros». */
    private UUID carregarEmDeposito(Cenario cenario, DocumentoConfig item, String nome) throws Exception {
        var pedido = multipart(url(cenario.processo()))
                .file(new MockMultipartFile("ficheiro", nome, "application/octet-stream", ZipsDeTeste.conteudo(nome, CONTEUDO)))
                .param("categoria", "DEPOSITO");
        if (item != null) {
            pedido.param("documentoConfigId", item.getId().toString());
        }
        String resposta = mockMvc.perform(pedido.with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    @Test
    void deveCriarAPlantaAoCarregarUmTiffNumaPecaGrafica() throws Exception {
        Cenario cenario = criarCenario("160");
        DocumentoConfig item = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);

        UUID documentoId = carregarEmDeposito(cenario, item, "planta1.tif");

        Planta planta = plantaRepository.findByProcessoDocumento_Id(documentoId).getFirst();
        assertThat(planta.getProcesso().getId()).isEqualTo(cenario.processo().getId());
        assertThat(planta.getNomeTiff()).isEqualTo("planta1.tif");
        assertThat(planta.getNomeZip()).isNull();
        assertThat(planta.isEmVigor()).isFalse();
        assertThat(planta.getLinkJpeg()).isNull();
        assertThat(planta.getAtualizadoPor()).isNull();
    }

    @Test
    void deveCriarAPlantaComONomeDoZip() throws Exception {
        Cenario cenario = criarCenario("161");
        DocumentoConfig item = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);

        UUID documentoId = carregarEmDeposito(cenario, item, "planta1.zip");

        Planta planta = plantaRepository.findByProcessoDocumento_Id(documentoId).getFirst();
        assertThat(planta.getNomeZip()).isEqualTo("planta1.zip");
        assertThat(planta.getNomeTiff()).isNull();
    }

    @Test
    void naoDeveCriarPlantaParaTfwNemGpkgDaPecaGrafica() throws Exception {
        Cenario cenario = criarCenario("162");
        DocumentoConfig item = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);

        carregarEmDeposito(cenario, item, "planta1.tfw");
        carregarEmDeposito(cenario, item, "planta1.gpkg");

        assertThat(plantaRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
    }

    @Test
    void naoDeveCriarPlantaNumaPecaEscritaNemNoBaldeOutros() throws Exception {
        Cenario cenario = criarCenario("163");
        DocumentoConfig pecaEscrita = itemDeDeposito(cenario, "RELATORIO_DEP", TipoElemento.PECA_ESCRITA);

        carregarEmDeposito(cenario, pecaEscrita, "relatorio.zip");
        carregarEmDeposito(cenario, pecaEscrita, "relatorio.tif");
        carregarEmDeposito(cenario, null, "anexo.zip");
        carregarEmDeposito(cenario, null, "anexo.tif");

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .hasSize(4);
        assertThat(plantaRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
    }

    @Test
    void deveAtualizarAMesmaPlantaNaSubstituicao() throws Exception {
        Cenario cenario = criarCenario("164");
        DocumentoConfig item = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);
        UUID documentoId = carregarEmDeposito(cenario, item, "planta1.tif");
        Planta original = plantaRepository.findByProcessoDocumento_Id(documentoId).getFirst();

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documentoId))
                        .file(new MockMultipartFile("ficheiro", "planta1b.tif", "image/tiff", CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk());

        Planta atualizada = plantaRepository.findByProcessoDocumento_Id(documentoId).getFirst();
        assertThat(atualizada.getId()).isEqualTo(original.getId());
        assertThat(atualizada.getNomeTiff()).isEqualTo("planta1b.tif");
        assertThat(atualizada.getNomeZip()).isNull();
        assertThat(atualizada.getAtualizadoEm()).isAfter(original.getAtualizadoEm());
        assertThat(plantaRepository.findByProcesso_Id(cenario.processo().getId())).hasSize(1);
    }

    @Test
    void deveApagarAPlantaAoRemoverODocumento() throws Exception {
        Cenario cenario = criarCenario("165");
        DocumentoConfig item = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);
        UUID documentoId = carregarEmDeposito(cenario, item, "planta1.zip");

        mockMvc.perform(delete(url(cenario.processo(), documentoId))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(plantaRepository.findByProcessoDocumento_Id(documentoId)).isEmpty();
        assertThat(processoDocumentoRepository.findById(documentoId)).isEmpty();
    }

    @Test
    void naoDeveFicarPlantaQuandoOCarregamentoReverte() throws Exception {
        Cenario cenario = criarCenario("166");
        DocumentoConfig item = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);
        doThrow(new IllegalStateException("falha simulada")).when(auditoriaService).registar(eq("PROCESSO"),
                anyString(), eq("CARREGAR_DOCUMENTO"), any(), any(), any(), any());

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", "planta1.zip", "application/zip", ZipsDeTeste.conteudo("planta1.zip", CONTEUDO)))
                        .param("categoria", "DEPOSITO")
                        .param("documentoConfigId", item.getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isInternalServerError());

        assertThat(plantaRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .isEmpty();
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    @Test
    void deveApagarAsPlantasAoRemoverTodosOsDocumentosDoProcesso() throws Exception {
        Cenario cenario = criarCenario("167");
        DocumentoConfig item = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);
        carregarEmDeposito(cenario, item, "planta1.zip");
        carregarEmDeposito(cenario, item, "planta2.tif");

        processoDocumentoService.removerTodosDoProcesso(cenario.processo().getId());

        assertThat(plantaRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .isEmpty();
    }

    private DocumentoConfig itemDePublicacao(Cenario cenario) {
        return documentoConfigRepository.saveAndFlush(new DocumentoConfig(
                cenario.documentoConfig().getInstrumentoProcedimentoConfig(), "PLANTA_ORDENAMENTO_PUB",
                "Planta de Ordenamento", "Planta de Ordenamento", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", 3));
    }

    private UUID carregarEmPublicacao(Cenario cenario, DocumentoConfig item, String nome) throws Exception {
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", nome, "application/zip", ZipsDeTeste.conteudo(nome, CONTEUDO)))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", item.getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private List<MetadadosMotu> fichasMotu(Cenario cenario, TipoMetadadosMotu tipo) {
        return metadadosMotuRepository.findByProcesso_Id(cenario.processo().getId()).stream()
                .filter(ficha -> ficha.getTipo() == tipo)
                .toList();
    }

    private MetadadosMotu conjuntoDe(UUID documentoId) {
        return metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(documentoId,
                TipoMetadadosMotu.CDG).orElseThrow();
    }

    @Test
    void deveCriarOConjuntoELigarAPlantaAoCarregarUmZipDePublicacaoSemCriarOutraSerie() throws Exception {
        Cenario cenario = criarCenario("242");
        DocumentoConfig item = itemDePublicacao(cenario);
        MetadadosMotu serie = metadadosMotuRepository.saveAndFlush(MetadadosMotu.serie(cenario.processo()));

        UUID documentoId = carregarEmPublicacao(cenario, item, "ordenamento.zip");

        MetadadosMotu conjunto = conjuntoDe(documentoId);
        assertThat(conjunto.getProcessoDocumento().getId()).isEqualTo(documentoId);
        assertThat(conjunto.isOpcional()).isFalse();
        assertThat(conjunto.isCompleto()).isFalse();
        assertThat(plantaRepository.findByProcessoDocumento_Id(documentoId)).singleElement()
                .satisfies(planta -> assertThat(planta.getMetadadosMotu().getId()).isEqualTo(conjunto.getId()));
        assertThat(fichasMotu(cenario, TipoMetadadosMotu.SERIE)).extracting(MetadadosMotu::getId)
                .containsExactly(serie.getId());
    }

    @Test
    void deveCriarSoMaisUmConjuntoAoCarregarUmSegundoZip() throws Exception {
        Cenario cenario = criarCenario("243");
        DocumentoConfig item = itemDePublicacao(cenario);
        metadadosMotuRepository.saveAndFlush(MetadadosMotu.serie(cenario.processo()));
        carregarEmPublicacao(cenario, item, "ordenamento.zip");

        carregarEmPublicacao(cenario, item, "condicionantes.zip");

        assertThat(fichasMotu(cenario, TipoMetadadosMotu.CDG)).hasSize(2);
        assertThat(fichasMotu(cenario, TipoMetadadosMotu.SERIE)).hasSize(1);
    }

    @Test
    void deveCriarASerieEOConjuntoNumProcessoSemSerie() throws Exception {
        Cenario cenario = criarCenario("244");
        DocumentoConfig item = itemDePublicacao(cenario);

        UUID documentoId = carregarEmPublicacao(cenario, item, "ordenamento.zip");

        assertThat(fichasMotu(cenario, TipoMetadadosMotu.SERIE)).hasSize(1);
        assertThat(fichasMotu(cenario, TipoMetadadosMotu.CDG)).extracting(ficha -> ficha.getProcessoDocumento().getId())
                .containsExactly(documentoId);
    }

    @Test
    void naoDeveCriarFichasMotuForaDoProcedimentoFinal() throws Exception {
        Cenario cenario = criarCenario("245");
        DocumentoConfig item = itemDePublicacao(cenario);
        InstrumentoProcedimentoConfig procedimentoDoCenario = cenario.documentoConfig().getInstrumentoProcedimentoConfig();
        TipoProcedimento seguinte = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCM-245-FINAL", "Publicação"));
        procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                procedimentoDoCenario.getInstrumentoDinamicaConfig(), seguinte, 2, "SEGUNDA_H"));

        carregarEmPublicacao(cenario, item, "area-intervencao.zip");

        assertThat(metadadosMotuRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
    }

    @Test
    void naoDeveCriarConjuntoParaPecaGraficaDeDepositoNemParaPecaEscrita() throws Exception {
        Cenario cenario = criarCenario("246");
        itemDePublicacao(cenario);
        DocumentoConfig deposito = itemDeDeposito(cenario, "PLANTA_DEP", TipoElemento.PECA_GRAFICA);
        metadadosMotuRepository.saveAndFlush(MetadadosMotu.serie(cenario.processo()));

        carregarEmDeposito(cenario, deposito, "planta1.tif");
        carregar(cenario, "regulamento.pdf");

        assertThat(fichasMotu(cenario, TipoMetadadosMotu.CDG)).isEmpty();
        assertThat(fichasMotu(cenario, TipoMetadadosMotu.SERIE)).hasSize(1);
    }

    @Test
    void deveManterOConjuntoEALigacaoAPlantaNaSubstituicao() throws Exception {
        Cenario cenario = criarCenario("247");
        DocumentoConfig item = itemDePublicacao(cenario);
        UUID documentoId = carregarEmPublicacao(cenario, item, "ordenamento.zip");
        MetadadosMotu conjunto = conjuntoDe(documentoId);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documentoId))
                        .file(new MockMultipartFile("ficheiro", "ordenamento-v2.zip", "application/zip", ZipsDeTeste.conteudo("ordenamento-v2.zip", CONTEUDO_NOVO)))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk());

        assertThat(conjuntoDe(documentoId).getId()).isEqualTo(conjunto.getId());
        assertThat(fichasMotu(cenario, TipoMetadadosMotu.CDG)).hasSize(1);
        assertThat(plantaRepository.findByProcessoDocumento_Id(documentoId)).singleElement()
                .satisfies(planta -> assertThat(planta.getMetadadosMotu().getId()).isEqualTo(conjunto.getId()));
    }

    @Test
    void deveApagarOConjuntoEManterASerieAoRemoverAPecaGrafica() throws Exception {
        Cenario cenario = criarCenario("248");
        DocumentoConfig item = itemDePublicacao(cenario);
        UUID documentoId = carregarEmPublicacao(cenario, item, "ordenamento.zip");

        mockMvc.perform(delete(url(cenario.processo(), documentoId))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(fichasMotu(cenario, TipoMetadadosMotu.CDG)).isEmpty();
        assertThat(fichasMotu(cenario, TipoMetadadosMotu.SERIE)).hasSize(1);
        assertThat(processoDocumentoRepository.findById(documentoId)).isEmpty();
    }

    @Test
    void naoDeveFicarConjuntoNemSerieQuandoOCarregamentoReverte() throws Exception {
        Cenario cenario = criarCenario("249");
        DocumentoConfig item = itemDePublicacao(cenario);
        doThrow(new IllegalStateException("falha simulada")).when(auditoriaService).registar(eq("PROCESSO"),
                anyString(), eq("CARREGAR_DOCUMENTO"), any(), any(), any(), any());

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", "ordenamento.zip", "application/zip", ZipsDeTeste.conteudo("ordenamento.zip", CONTEUDO)))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", item.getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isInternalServerError());

        assertThat(metadadosMotuRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .isEmpty();
    }

    @Test
    void deveApagarOsConjuntosEManterASerieAoRemoverTodosOsDocumentosDoProcesso() throws Exception {
        Cenario cenario = criarCenario("250");
        DocumentoConfig item = itemDePublicacao(cenario);
        carregarEmPublicacao(cenario, item, "ordenamento.zip");
        carregarEmPublicacao(cenario, item, "condicionantes.zip");

        processoDocumentoService.removerTodosDoProcesso(cenario.processo().getId());

        assertThat(fichasMotu(cenario, TipoMetadadosMotu.CDG)).isEmpty();
        assertThat(fichasMotu(cenario, TipoMetadadosMotu.SERIE)).hasSize(1);
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .isEmpty();
    }

    private List<String> objetosSob(String prefixo) {
        return StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(BUCKET).prefix(prefixo).recursive(true).build()).spliterator(), false)
                .map(resultado -> {
                    try {
                        return resultado.get().objectName();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }
}
