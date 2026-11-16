package com.conkord.saigt.controller.documento;

import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.exception.StorageIndisponivelException;
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
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublicoRepository;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.LinkPublicoService;
import com.conkord.saigt.service.documento.LinkPublicoService.LinkPublico;
import com.conkord.saigt.service.documento.SubstituicaoDocumentosService;
import com.conkord.saigt.service.documento.VersaoObjeto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectRetentionArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.Item;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "saigt.teste.bucket=" + ProcessoDocumentoBucketVersionadoIntegrationTest.BUCKET_VERSIONADO})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessoDocumentoBucketVersionadoIntegrationTest extends StorageTestcontainersBase {

    static final String BUCKET_VERSIONADO = "saigt-documentos-versionado-teste";
    private static final byte[] CONTEUDO = "%PDF-1.4 versão original".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTEUDO_NOVO = "%PDF-1.4 versão corrigida".getBytes(StandardCharsets.UTF_8);

    static {
        MinioClient cliente = MinioClient.builder()
                .endpoint("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3))
                .credentials(ACCESS_KEY, SECRET_KEY).build();
        try {
            if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_VERSIONADO).build())) {
                cliente.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_VERSIONADO).objectLock(true).build());
            }
            cliente.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(BUCKET_VERSIONADO)
                    .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StorageProperties storageProperties;

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
    private ArmazenamentoIntencaoRepository intencaoRepository;

    @SpyBean
    private ArmazenamentoDocumentos armazenamento;

    @Autowired
    private SubstituicaoDocumentosService substituicaoDocumentosService;

    @Autowired
    private LinkPublicoService linkPublicoService;

    @Autowired
    private DocumentoLinkPublicoRepository linkRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @SpyBean
    private AuditoriaService auditoriaService;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig documentoConfig) {
    }

    @BeforeEach
    void confirmarQueAAplicacaoUsaOBucketVersionado() {
        assertThat(storageProperties.bucket()).isEqualTo(BUCKET_VERSIONADO);
    }

    @Test
    void naoDeveFicarNenhumaVersaoDepoisDeSubstituirDuasVezesERemover() throws Exception {
        Cenario cenario = criarCenario("V01");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");
        String chaveOriginal = original.getCaminhoFicheiro();
        assertThat(versoes(chaveOriginal)).singleElement().isEqualTo(original.getVersaoObjeto());

        String chaveSegunda = substituir(cenario, original.getId(), "regulamento.pdf");
        assertThat(versoes(chaveOriginal)).isEmpty();
        String chaveTerceira = substituir(cenario, original.getId(), "regulamento_v3.pdf");
        assertThat(versoes(chaveSegunda)).isEmpty();
        assertThat(List.of(chaveOriginal, chaveSegunda, chaveTerceira)).doesNotHaveDuplicates();

        mockMvc.perform(delete(url(cenario.processo(), original.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(processoDocumentoRepository.findById(original.getId())).isEmpty();
        assertThat(versoesSob(prefixo(cenario))).isEmpty();
        for (String chave : List.of(chaveOriginal, chaveSegunda, chaveTerceira)) {
            assertThat(operacoes(chave)).as(chave)
                    .containsExactlyInAnyOrder(OperacaoArmazenamento.GRAVACAO, OperacaoArmazenamento.APAGAMENTO);
        }
    }

    @Test
    void deveGravarOVersionIdRealDoObjetoNovoNaSubstituicao() throws Exception {
        Cenario cenario = criarCenario("V02");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");

        String chaveNova = substituir(cenario, original.getId(), "regulamento.pdf");

        ProcessoDocumento substituido = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(chaveNova).matches("processos/" + cenario.processo().getNumeroProcesso() + "/PUBLICACAO/"
                + original.getId() + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(substituido.getVersaoObjeto()).isNotEqualTo("null");
        assertThat(versoes(chaveNova)).containsExactly(substituido.getVersaoObjeto());
        assertThat(substituido.getDocumentoConfig().getId()).isEqualTo(cenario.documentoConfig().getId());
        assertThat(substituido.getCategoria()).isEqualTo(CategoriaDocumento.PUBLICACAO);
    }

    @Test
    void deveApagarAsVersoesDoObjetoNovoEManterOAnteriorQuandoASubstituicaoReverte() throws Exception {
        Cenario cenario = criarCenario("V03");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");
        doThrow(new IllegalStateException("falha simulada")).when(auditoriaService).registar(eq("PROCESSO"),
                anyString(), eq("SUBSTITUIR_DOCUMENTO"), any(), any(), any(), any());

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), original.getId()))
                        .file(ficheiro("regulamento_v2.pdf", CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isInternalServerError());

        ProcessoDocumento atual = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(original.getCaminhoFicheiro());
        assertThat(atual.getVersaoObjeto()).isEqualTo(original.getVersaoObjeto());
        assertThat(versoesSob(prefixo(cenario)))
                .containsExactly(original.getCaminhoFicheiro() + "@" + original.getVersaoObjeto());
        assertThat(intencoesSob(prefixo(cenario))).filteredOn(i -> !i.getChaveObjeto().equals(original.getCaminhoFicheiro()))
                .singleElement().extracting(ArmazenamentoIntencao::getOperacao).isEqualTo(OperacaoArmazenamento.GRAVACAO);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deveResponder204EDeixarAIntencaoParaALimpezaQuandoOStorageFalhaDepoisDoCommit(CapturedOutput saida)
            throws Exception {
        Cenario cenario = criarCenario("V04");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        String chave = documento.getCaminhoFicheiro();
        doThrow(new StorageIndisponivelException("indisponível", new RuntimeException()))
                .when(armazenamento).removerTodasVersoes(chave);

        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(processoDocumentoRepository.findById(documento.getId())).isEmpty();
        assertThat(operacoes(chave)).contains(OperacaoArmazenamento.APAGAMENTO);
        assertThat(versoes(chave)).containsExactly(documento.getVersaoObjeto());
        assertThat(saida).contains("WARN").contains("Objeto órfão não removido após commit — caminho=" + chave);
    }

    @Test
    void naoDeveApagarVersoesNemRegistarIntencaoQuandoODocumentoEstaBloqueado() throws Exception {
        Cenario cenario = criarCenario("V05");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        documento.setBloqueadoEdicao(true);
        processoDocumentoRepository.saveAndFlush(documento);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documento.getId()))
                        .file(ficheiro("regulamento.pdf", CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));
        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));

        assertNadaMudou(cenario, documento);
    }

    @Test
    void naoDeveApagarVersoesNemRegistarIntencaoQuandoQuemAlteraNaoEOAutor() throws Exception {
        Cenario cenario = criarCenario("V06");
        ProcessoDocumento documento = carregar(cenario, "regulamento.pdf");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro V06", "outrov06@cm-doc.pt", "INCM"));

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documento.getId()))
                        .file(ficheiro("regulamento.pdf", CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(url(cenario.processo(), documento.getId()))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());

        assertNadaMudou(cenario, documento);
    }

    /** Só a intenção e a versão do carregamento inicial. */
    @Test
    void deveMoverParaSubstituidosComACopiaDaVersaoEApagarTodasAsVersoesDoOriginal() throws Exception {
        Cenario cenario = criarCenario("V21");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");
        String chaveOriginal = original.getCaminhoFicheiro();

        moverParaSubstituidos(original.getId());

        ProcessoDocumento movido = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(movido.getCaminhoFicheiro()).matches(prefixo(cenario) + "SUBSTITUIDOS/" + original.getId()
                + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(versoes(movido.getCaminhoFicheiro())).containsExactly(movido.getVersaoObjeto());
        assertThat(versoes(chaveOriginal)).isEmpty();
        try (InputStream copia = armazenamento.abrir(movido.getCaminhoFicheiro(), movido.getVersaoObjeto(), 0, 0)) {
            assertThat(copia.readAllBytes()).isEqualTo(CONTEUDO);
        }
        assertThat(operacoes(movido.getCaminhoFicheiro())).containsExactly(OperacaoArmazenamento.COPIA);
        assertThat(operacoes(chaveOriginal))
                .containsExactlyInAnyOrder(OperacaoArmazenamento.GRAVACAO, OperacaoArmazenamento.APAGAMENTO);
        assertThat(armazenamento.retidoAte(movido.getCaminhoFicheiro(), movido.getVersaoObjeto())).isEmpty();
    }

    @Test
    void deveFicarSubstituidoRecusarPutEDeleteEContinuarADescarregarPorTicket() throws Exception {
        Cenario cenario = criarCenario("V25");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");

        moverParaSubstituidos(original.getId());

        ProcessoDocumento movido = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(movido.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), original.getId()))
                        .file(ficheiro("regulamento.pdf", CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));
        mockMvc.perform(delete(url(cenario.processo(), original.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));

        String resposta = mockMvc.perform(post(url(cenario.processo(), original.getId()) + "/descarga")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String descarga = URI.create(objectMapper.readTree(resposta).get("url").asText()).getRawPath();
        assertThat(mockMvc.perform(get(descarga)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).isEqualTo(CONTEUDO);
        assertThat(processoDocumentoRepository.findById(original.getId()).orElseThrow().getCaminhoFicheiro())
                .isEqualTo(movido.getCaminhoFicheiro());
    }

    @Test
    void deveReterACopiaAteADataDoOriginalRetidoEDeixarSoUmMarcadorDeEliminacaoNoOriginal() throws Exception {
        Cenario cenario = criarCenario("V26");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");
        String chaveOriginal = original.getCaminhoFicheiro();
        Instant ate = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        armazenamento.reter(chaveOriginal, original.getVersaoObjeto(), ate);

        moverParaSubstituidos(original.getId());

        ProcessoDocumento movido = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(movido.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        assertThat(versoes(chaveOriginal)).hasSize(2).contains(original.getVersaoObjeto());
        assertThat(armazenamento.versoes(chaveOriginal)).filteredOn(VersaoObjeto::retida)
                .extracting(VersaoObjeto::versionId).containsExactly(original.getVersaoObjeto());
        Retention retencaoCopia = minioClient.getObjectRetention(GetObjectRetentionArgs.builder()
                .bucket(BUCKET_VERSIONADO).object(movido.getCaminhoFicheiro()).versionId(movido.getVersaoObjeto()).build());
        assertThat(retencaoCopia.mode()).isEqualTo(RetentionMode.GOVERNANCE);
        assertThat(retencaoCopia.retainUntilDate().toInstant()).isEqualTo(ate);
        try (InputStream copia = armazenamento.abrir(movido.getCaminhoFicheiro(), movido.getVersaoObjeto(), 0, 0)) {
            assertThat(copia.readAllBytes()).isEqualTo(CONTEUDO);
        }
    }

    @Test
    void deveRevogarOLinkIncmDoDocumentoMovidoEDevolver404NoUrlPublico() throws Exception {
        Cenario cenario = criarCenario("V22");
        cenario.documentoConfig().setLinkPublicoPermitido(true);
        documentoConfigRepository.saveAndFlush(cenario.documentoConfig());
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");
        LinkPublico link = linkPublicoService.obterOuCriar(original.getId(), DestinoLinkPublico.INCM);
        String caminho = URI.create(link.url()).getRawPath();
        mockMvc.perform(get(caminho)).andExpect(status().isOk());

        moverParaSubstituidos(original.getId());

        mockMvc.perform(get(caminho)).andExpect(status().isNotFound());
        assertThat(linkRepository.findByProcessoDocumento_Id(original.getId())).singleElement()
                .satisfies(revogado -> {
                    assertThat(revogado.getRevogadoEm()).isNotNull();
                    assertThat(revogado.getMotivoRevogacao()).isEqualTo(MotivoRevogacaoLink.SUBSTITUICAO);
                });
    }

    @Test
    void deveReverterEDeixarODocumentoEOOriginalComoEstavamQuandoACopiaFalha() throws Exception {
        Cenario cenario = criarCenario("V23");
        ProcessoDocumento original = carregar(cenario, "regulamento.pdf");
        doThrow(new StorageIndisponivelException("Falha simulada da cópia.", null))
                .when(armazenamento).copiar(anyString(), any(), anyString());

        assertThatThrownBy(() -> moverParaSubstituidos(original.getId()))
                .isInstanceOf(StorageIndisponivelException.class);

        ProcessoDocumento depois = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(depois.getCaminhoFicheiro()).isEqualTo(original.getCaminhoFicheiro());
        assertThat(depois.getVersaoObjeto()).isEqualTo(original.getVersaoObjeto());
        assertThat(versoes(original.getCaminhoFicheiro())).containsExactly(original.getVersaoObjeto());
        assertThat(operacoes(original.getCaminhoFicheiro())).containsExactly(OperacaoArmazenamento.GRAVACAO);
    }

    @Test
    void deveGuardarOOutputDoGeoconformEmSubstituidosGeoconform() throws Exception {
        Cenario cenario = criarCenario("V24");
        ProcessoDocumento carregado = carregar(cenario, "fde.pdf");
        String output = prefixo(cenario) + "DEPOSITO/GEOCONFORM/" + carregado.getId() + "-fde.pdf";
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            ProcessoDocumento documento = processoDocumentoRepository.findById(carregado.getId()).orElseThrow();
            String versao = armazenamento.copiar(documento.getCaminhoFicheiro(), documento.getVersaoObjeto(), output);
            armazenamento.removerTodasVersoes(documento.getCaminhoFicheiro());
            documento.setCaminhoFicheiro(output);
            documento.setVersaoObjeto(versao);
            documento.setOrigem(OrigemDocumento.GEOCONFORM);
        });

        moverParaSubstituidos(carregado.getId());

        ProcessoDocumento movido = processoDocumentoRepository.findById(carregado.getId()).orElseThrow();
        assertThat(movido.getCaminhoFicheiro()).matches(prefixo(cenario) + "SUBSTITUIDOS/GEOCONFORM/" + carregado.getId()
                + "-[0-9a-f]{8}-fde\\.pdf");
        assertThat(movido.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        assertThat(versoes(movido.getCaminhoFicheiro())).containsExactly(movido.getVersaoObjeto());
        assertThat(versoes(output)).isEmpty();
    }

    private void moverParaSubstituidos(UUID documentoId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> substituicaoDocumentosService
                .moverParaSubstituidos(processoDocumentoRepository.findById(documentoId).orElseThrow()));
    }

    private void assertNadaMudou(Cenario cenario, ProcessoDocumento documento) throws Exception {
        assertThat(processoDocumentoRepository.findById(documento.getId())).isPresent();
        assertThat(versoesSob(prefixo(cenario)))
                .containsExactly(documento.getCaminhoFicheiro() + "@" + documento.getVersaoObjeto());
        assertThat(intencoesSob(prefixo(cenario))).singleElement()
                .extracting(ArmazenamentoIntencao::getOperacao).isEqualTo(OperacaoArmazenamento.GRAVACAO);
    }

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DOCV-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DOCV-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCV-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig documentoConfig = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"pdf\"]", 1));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5080000" + codigo.substring(1),
                "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo.toLowerCase() + "@cm-doc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOCV/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        return new Cenario(processo, autor, entidade, documentoConfig);
    }

    private ProcessoDocumento carregar(Cenario cenario, String nome) throws Exception {
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro(nome, CONTEUDO))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return processoDocumentoRepository.findById(UUID.fromString(objectMapper.readTree(resposta).get("id").asText()))
                .orElseThrow();
    }

    private String substituir(Cenario cenario, UUID documentoId, String nome) throws Exception {
        String resposta = mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documentoId))
                        .file(ficheiro(nome, CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode corpo = objectMapper.readTree(resposta);
        assertThat(corpo.get("id").asText()).isEqualTo(documentoId.toString());
        return processoDocumentoRepository.findById(documentoId).orElseThrow().getCaminhoFicheiro();
    }

    private static MockMultipartFile ficheiro(String nome, byte[] conteudo) {
        return new MockMultipartFile("ficheiro", nome, "application/pdf", conteudo);
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

    private static String prefixo(Cenario cenario) {
        return "processos/" + cenario.processo().getNumeroProcesso() + "/";
    }

    private List<OperacaoArmazenamento> operacoes(String chave) {
        return intencaoRepository.findByChaveObjeto(chave).stream().map(ArmazenamentoIntencao::getOperacao).toList();
    }

    private List<ArmazenamentoIntencao> intencoesSob(String prefixo) {
        return intencaoRepository.findAll().stream().filter(i -> i.getChaveObjeto().startsWith(prefixo)).toList();
    }

    /** `versionId` das versões e marcadores de eliminação da chave exata. */
    private List<String> versoes(String chave) throws Exception {
        List<String> resultado = new ArrayList<>();
        for (String versao : versoesSob(chave)) {
            if (versao.startsWith(chave + "@")) {
                resultado.add(versao.substring(chave.length() + 1));
            }
        }
        return resultado;
    }

    private List<String> versoesSob(String prefixo) throws Exception {
        List<String> resultado = new ArrayList<>();
        for (Result<Item> resposta : minioClient.listObjects(ListObjectsArgs.builder().bucket(BUCKET_VERSIONADO)
                .prefix(prefixo).recursive(true).includeVersions(true).build())) {
            Item item = resposta.get();
            resultado.add(item.objectName() + "@" + item.versionId());
        }
        return resultado;
    }
}
