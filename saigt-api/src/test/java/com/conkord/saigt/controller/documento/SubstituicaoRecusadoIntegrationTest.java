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
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
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
        "saigt.teste.bucket=" + SubstituicaoRecusadoIntegrationTest.BUCKET_VERSIONADO})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubstituicaoRecusadoIntegrationTest extends StorageTestcontainersBase {

    static final String BUCKET_VERSIONADO = "saigt-documentos-substituicao-teste";
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

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private PlantaRepository plantaRepository;

    @Autowired
    private MetadadosMotuRepository metadadosMotuRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig documentoConfig,
                           DocumentoConfig itemPlantas) {
    }

    @BeforeEach
    void confirmarQueAAplicacaoUsaOBucketVersionado() {
        assertThat(storageProperties.bucket()).isEqualTo(BUCKET_VERSIONADO);
    }


    private static final String[] TIFFS = {"PO_01_A.tif", "PO_01_B.tif", "PO_01_C.tif"};

    // ---------- AC1: substituição de um recusado de peças escritas ----------

    @Test
    void deveSubstituirORecusadoPorUmDocumentoNovoEMoverOOriginalParaSubstituidos() throws Exception {
        Cenario cenario = criarCenario("S01");
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));
        String chaveOriginal = recusado.getCaminhoFicheiro();

        JsonNode corpo = substituirRecusado(cenario, recusado.getId(), "regulamento_v2.pdf", CONTEUDO_NOVO);

        UUID novoId = UUID.fromString(corpo.get("id").asText());
        assertThat(novoId).isNotEqualTo(recusado.getId());
        ProcessoDocumento novo = processoDocumentoRepository.findById(novoId).orElseThrow();
        assertThat(novo.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(novo.isBloqueadoEdicao()).isFalse();
        assertThat(novo.getDocumentoConfig().getId()).isEqualTo(cenario.documentoConfig().getId());
        assertThat(novo.getCategoria()).isEqualTo(CategoriaDocumento.PUBLICACAO);
        assertThat(novo.getCaminhoFicheiro())
                .isEqualTo(prefixo(cenario) + "PUBLICACAO/" + novoId + "-regulamento_v2.pdf");
        assertThat(versoes(novo.getCaminhoFicheiro())).containsExactly(novo.getVersaoObjeto());

        ProcessoDocumento antigo = processoDocumentoRepository.findById(recusado.getId()).orElseThrow();
        assertThat(antigo.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        assertThat(antigo.getCaminhoFicheiro()).matches(prefixo(cenario) + "SUBSTITUIDOS/" + recusado.getId()
                + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(versoes(antigo.getCaminhoFicheiro())).containsExactly(antigo.getVersaoObjeto());
        assertThat(versoes(chaveOriginal)).isEmpty();
        try (InputStream copia = armazenamento.abrir(antigo.getCaminhoFicheiro(), antigo.getVersaoObjeto(), 0, 0)) {
            assertThat(copia.readAllBytes()).isEqualTo(CONTEUDO);
        }
    }

    /** AC «intenções»: uma antes de cada gravação, cópia e apagamento. */
    @Test
    void deveRegistarUmaIntencaoAntesDeCadaGravacaoCopiaEApagamento() throws Exception {
        Cenario cenario = criarCenario("S02");
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));
        String chaveOriginal = recusado.getCaminhoFicheiro();

        JsonNode corpo = substituirRecusado(cenario, recusado.getId(), "regulamento_v2.pdf", CONTEUDO_NOVO);

        ProcessoDocumento novo = processoDocumentoRepository.findById(UUID.fromString(corpo.get("id").asText())).orElseThrow();
        ProcessoDocumento antigo = processoDocumentoRepository.findById(recusado.getId()).orElseThrow();
        assertThat(operacoes(novo.getCaminhoFicheiro())).containsExactly(OperacaoArmazenamento.GRAVACAO);
        assertThat(operacoes(antigo.getCaminhoFicheiro())).containsExactly(OperacaoArmazenamento.COPIA);
        assertThat(operacoes(chaveOriginal))
                .containsExactlyInAnyOrder(OperacaoArmazenamento.GRAVACAO, OperacaoArmazenamento.APAGAMENTO);
    }

    @Test
    void deveAuditarSubstituirDocumentoRecusadoComOsDoisIdsEOsHashes() throws Exception {
        Cenario cenario = criarCenario("S03");
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));

        JsonNode corpo = substituirRecusado(cenario, recusado.getId(), "regulamento_v2.pdf", CONTEUDO_NOVO);

        List<Auditoria> auditoria = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "PROCESSO", cenario.processo().getId().toString(), "SUBSTITUIR_DOCUMENTO_RECUSADO");
        assertThat(auditoria).singleElement().satisfies(registo -> {
            JsonNode dados = lerJson(registo.getDadosAlteracao());
            assertThat(dados.get("processoDocumentoId").asText()).isEqualTo(recusado.getId().toString());
            assertThat(dados.get("processoDocumentoNovoId").asText()).isEqualTo(corpo.get("id").asText());
            assertThat(dados.get("hashSha256Anterior").asText()).isEqualTo(recusado.getHashSha256());
            assertThat(dados.get("hashSha256").asText()).isNotEqualTo(recusado.getHashSha256()).hasSize(64);
            assertThat(dados.get("categoria").asText()).isEqualTo("PUBLICACAO");
        });
    }

    // ---------- retenção ----------

    @Test
    void deveDeixarSoUmMarcadorNoOriginalRetidoEReterACopiaEmSubstituidos() throws Exception {
        Cenario cenario = criarCenario("S04");
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));
        String chaveOriginal = recusado.getCaminhoFicheiro();
        Instant ate = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        armazenamento.reter(chaveOriginal, recusado.getVersaoObjeto(), ate);

        substituirRecusado(cenario, recusado.getId(), "regulamento_v2.pdf", CONTEUDO_NOVO);

        ProcessoDocumento antigo = processoDocumentoRepository.findById(recusado.getId()).orElseThrow();
        assertThat(antigo.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        // a versão retida continua no bucket e o marcador de eliminação é a segunda entrada
        assertThat(versoes(chaveOriginal)).hasSize(2).contains(recusado.getVersaoObjeto());
        assertThat(armazenamento.versoes(chaveOriginal)).filteredOn(VersaoObjeto::retida)
                .extracting(VersaoObjeto::versionId).containsExactly(recusado.getVersaoObjeto());
        Retention retencaoCopia = minioClient.getObjectRetention(GetObjectRetentionArgs.builder()
                .bucket(BUCKET_VERSIONADO).object(antigo.getCaminhoFicheiro()).versionId(antigo.getVersaoObjeto()).build());
        assertThat(retencaoCopia.retainUntilDate().toInstant()).isEqualTo(ate);
    }

    // ---------- links ----------

    @Test
    void deveRevogarOLinkPublicoDoRecusadoEDevolver404() throws Exception {
        Cenario cenario = criarCenario("S05");
        cenario.documentoConfig().setLinkPublicoPermitido(true);
        documentoConfigRepository.saveAndFlush(cenario.documentoConfig());
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));
        LinkPublico link = linkPublicoService.obterOuCriar(recusado.getId(), DestinoLinkPublico.INCM);
        String caminho = URI.create(link.url()).getRawPath();
        mockMvc.perform(get(caminho)).andExpect(status().isOk());

        substituirRecusado(cenario, recusado.getId(), "regulamento_v2.pdf", CONTEUDO_NOVO);

        mockMvc.perform(get(caminho)).andExpect(status().isNotFound());
        assertThat(linkRepository.findByProcessoDocumento_Id(recusado.getId())).singleElement().satisfies(revogado -> {
            assertThat(revogado.getRevogadoEm()).isNotNull();
            assertThat(revogado.getMotivoRevogacao()).isEqualTo(MotivoRevogacaoLink.SUBSTITUICAO);
        });
    }

    // ---------- plantas ----------

    @Test
    void deveManterNomesEPassarAsTresLinhasDaPlantaAoDocumentoNovo() throws Exception {
        Cenario cenario = criarCenario("S06");
        ProcessoDocumento recusado = plantaRecusada(cenario);
        List<Planta> antes = plantaRepository.findByProcessoDocumento_Id(recusado.getId());
        assertThat(antes).hasSize(3);
        UUID conjunto = antes.get(0).getMetadadosMotu().getId();

        JsonNode corpo = substituirRecusado(cenario, recusado.getId(), "PO_01.zip", ZipsDeTeste.comEntradas(TIFFS));

        UUID novoId = UUID.fromString(corpo.get("id").asText());
        assertThat(plantaRepository.findByProcessoDocumento_Id(recusado.getId())).isEmpty();
        List<Planta> depois = plantaRepository.findByProcessoDocumento_Id(novoId);
        assertThat(depois).hasSize(3);
        assertThat(plantaRepository.findByProcesso_Id(cenario.processo().getId())).hasSize(3);
        assertThat(depois).extracting(Planta::getNomeTiff).containsExactlyInAnyOrder(TIFFS);
        assertThat(depois).extracting(Planta::getNomeRegulamento).containsOnly("Planta de Ordenamento");
        assertThat(depois).extracting(Planta::getNumeroFolha).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(depois).extracting(p -> p.getMetadadosMotu().getId()).containsOnly(conjunto);
        assertThat(depois).extracting(Planta::getNomeZip).containsOnly("PO_01.zip");
        // a ficha do Conjunto acompanha o ZIP novo
        assertThat(metadadosMotuRepository.findByProcessoDocumento_Id(novoId)).hasSize(1);
        assertThat(metadadosMotuRepository.findByProcessoDocumento_Id(recusado.getId())).isEmpty();
    }

    @Test
    void deveRecusarComOutroNomeDePlantaSemMudarNada() throws Exception {
        Cenario cenario = criarCenario("S07");
        ProcessoDocumento recusado = plantaRecusada(cenario);
        long documentosAntes = processoDocumentoRepository.count();

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), recusado.getId()))
                        .file(zip("PO_1.zip", ZipsDeTeste.comEntradas(TIFFS)))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_NOME_SUBSTITUICAO_DIFERENTE"))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"))
                .andExpect(jsonPath("$.campos[0].erro").value(org.hamcrest.Matchers.containsString("PO_01.zip")));

        assertNadaMudouNaPlanta(cenario, recusado, documentosAntes);
    }

    @Test
    void deveRecusarQuandoFaltaUmTiffOuTemOutroNome() throws Exception {
        Cenario cenario = criarCenario("S08");
        ProcessoDocumento recusado = plantaRecusada(cenario);
        long documentosAntes = processoDocumentoRepository.count();

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), recusado.getId()))
                        .file(zip("PO_01.zip", ZipsDeTeste.comEntradas("PO_01_A.tif", "PO_01_B.tif")))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_NOME_SUBSTITUICAO_DIFERENTE"))
                .andExpect(jsonPath("$.campos[0].erro").value(org.hamcrest.Matchers.containsString("Faltam: PO_01_C.tif")));
        assertNadaMudouNaPlanta(cenario, recusado, documentosAntes);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), recusado.getId()))
                        .file(zip("PO_01.zip", ZipsDeTeste.comEntradas("PO_01_A.tif", "PO_01_B.tif", "PO_01_X.tif")))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_NOME_SUBSTITUICAO_DIFERENTE"))
                .andExpect(jsonPath("$.campos[0].erro").value(org.hamcrest.Matchers.containsString("PO_01_C.tif")));
        assertNadaMudouNaPlanta(cenario, recusado, documentosAntes);
    }

    private void assertNadaMudouNaPlanta(Cenario cenario, ProcessoDocumento recusado, long documentosAntes) throws Exception {
        ProcessoDocumento atual = processoDocumentoRepository.findById(recusado.getId()).orElseThrow();
        assertThat(atual.getEstadoValidacao()).isEqualTo(EstadoValidacao.REJEITADO);
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(recusado.getCaminhoFicheiro());
        assertThat(processoDocumentoRepository.count()).isEqualTo(documentosAntes);
        assertThat(plantaRepository.findByProcessoDocumento_Id(recusado.getId())).hasSize(3);
        assertThat(versoesSob(prefixo(cenario)))
                .containsExactly(recusado.getCaminhoFicheiro() + "@" + recusado.getVersaoObjeto());
    }

    // ---------- autorização ----------

    @Test
    void deveDevolver403ANaoAutorAntesDeQualquerRegra() throws Exception {
        Cenario cenario = criarCenario("S09");
        ProcessoDocumento recusado = plantaRecusada(cenario);
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro S09", "outros09@cm-doc.pt", "INCM"));
        long documentosAntes = processoDocumentoRepository.count();

        // nome diferente: se a regra do nome corresse primeiro, seria 400
        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), recusado.getId()))
                        .file(zip("PO_1.zip", ZipsDeTeste.comEntradas(TIFFS)))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(url(cenario.processo(), recusado.getId()))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());

        assertNadaMudouNaPlanta(cenario, recusado, documentosAntes);
    }

    // ---------- falha da cópia ----------

    @Test
    void deveReverterQuandoACopiaFalhaEDeixarAIntencaoDoObjetoNovoSemLinha() throws Exception {
        Cenario cenario = criarCenario("S10");
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));
        long documentosAntes = processoDocumentoRepository.count();
        doThrow(new StorageIndisponivelException("Falha simulada da cópia.", null))
                .when(armazenamento).copiar(anyString(), any(), anyString());

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), recusado.getId()))
                        .file(ficheiro("regulamento_v2.pdf", CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isInternalServerError());

        ProcessoDocumento atual = processoDocumentoRepository.findById(recusado.getId()).orElseThrow();
        assertThat(atual.getEstadoValidacao()).isEqualTo(EstadoValidacao.REJEITADO);
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(recusado.getCaminhoFicheiro());
        assertThat(atual.getVersaoObjeto()).isEqualTo(recusado.getVersaoObjeto());
        assertThat(processoDocumentoRepository.count()).isEqualTo(documentosAntes);
        // só o original fica no bucket: o objeto novo foi apagado no rollback, mas a intenção ficou registada
        assertThat(versoesSob(prefixo(cenario)))
                .containsExactly(recusado.getCaminhoFicheiro() + "@" + recusado.getVersaoObjeto());
        assertThat(intencoesSob(prefixo(cenario))).filteredOn(i -> !i.getChaveObjeto().equals(recusado.getCaminhoFicheiro()))
                .extracting(ArmazenamentoIntencao::getOperacao).contains(OperacaoArmazenamento.GRAVACAO);
        assertThat(intencoesSob(prefixo(cenario)))
                .filteredOn(i -> i.getChaveObjeto().contains("/PUBLICACAO/") && !i.getChaveObjeto().equals(recusado.getCaminhoFicheiro()))
                .allSatisfy(i -> assertThat(processoDocumentoRepository.findAll()
                        .stream().noneMatch(d -> d.getCaminhoFicheiro().equals(i.getChaveObjeto()))).isTrue());
    }

    // ---------- DELETE de um recusado ----------

    @Test
    void deveMoverORecusadoParaSubstituidosNoDeleteSemDocumentoNovo() throws Exception {
        Cenario cenario = criarCenario("S11");
        cenario.documentoConfig().setLinkPublicoPermitido(true);
        documentoConfigRepository.saveAndFlush(cenario.documentoConfig());
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));
        String chaveOriginal = recusado.getCaminhoFicheiro();
        LinkPublico link = linkPublicoService.obterOuCriar(recusado.getId(), DestinoLinkPublico.INCM);
        long documentosAntes = processoDocumentoRepository.count();

        mockMvc.perform(delete(url(cenario.processo(), recusado.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(processoDocumentoRepository.count()).isEqualTo(documentosAntes);
        ProcessoDocumento antigo = processoDocumentoRepository.findById(recusado.getId()).orElseThrow();
        assertThat(antigo.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        assertThat(antigo.getCaminhoFicheiro()).matches(prefixo(cenario) + "SUBSTITUIDOS/" + recusado.getId()
                + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(versoes(chaveOriginal)).isEmpty();
        assertThat(versoes(antigo.getCaminhoFicheiro())).containsExactly(antigo.getVersaoObjeto());
        mockMvc.perform(get(URI.create(link.url()).getRawPath())).andExpect(status().isNotFound());
        assertThat(operacoes(antigo.getCaminhoFicheiro())).containsExactly(OperacaoArmazenamento.COPIA);
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc("PROCESSO",
                cenario.processo().getId().toString(), "REMOVER_DOCUMENTO")).singleElement()
                .satisfies(registo -> assertThat(lerJson(registo.getDadosAlteracao()).get("processoDocumentoId").asText())
                        .isEqualTo(recusado.getId().toString()));
        // o item fica sem ficheiro: nova substituição do mesmo documento já não é possível
        mockMvc.perform(delete(url(cenario.processo(), recusado.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));
    }

    @Test
    void deveRetirarAsPlantasEOConjuntoDoRecusadoNoDelete() throws Exception {
        Cenario cenario = criarCenario("S13");
        ProcessoDocumento recusado = plantaRecusada(cenario);
        assertThat(plantaRepository.findByProcessoDocumento_Id(recusado.getId())).hasSize(3);
        assertThat(metadadosMotuRepository.findByProcessoDocumento_Id(recusado.getId())).hasSize(1);

        mockMvc.perform(delete(url(cenario.processo(), recusado.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(processoDocumentoRepository.findById(recusado.getId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.SUBSTITUIDO);
        // nada fica pendurado no SUBSTITUIDO: o consultaPlantas e o ZIP seguinte não veem folhas nem Conjunto antigos
        assertThat(plantaRepository.findByProcesso_Id(cenario.processo().getId())).isEmpty();
        assertThat(metadadosMotuRepository.findByProcessoDocumento_Id(recusado.getId())).isEmpty();
    }

    // ---------- listagem e descarga do substituído ----------

    @Test
    void deveListarOSubstituidoPeloFiltroEDescarregarACopia() throws Exception {
        Cenario cenario = criarCenario("S12");
        ProcessoDocumento recusado = recusado(cenario, carregar(cenario, "regulamento.pdf"));
        substituirRecusado(cenario, recusado.getId(), "regulamento_v2.pdf", CONTEUDO_NOVO);

        mockMvc.perform(get(url(cenario.processo()) + "?estadoValidacao=SUBSTITUIDO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(recusado.getId().toString()))
                .andExpect(jsonPath("$.content[0].estadoValidacao").value("SUBSTITUIDO"));
        String resposta = mockMvc.perform(post(url(cenario.processo(), recusado.getId()) + "/descarga")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String descarga = URI.create(objectMapper.readTree(resposta).get("url").asText()).getRawPath();
        assertThat(mockMvc.perform(get(descarga)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).isEqualTo(CONTEUDO);
    }

    // ---------- auxiliares ----------

    private ProcessoDocumento recusado(Cenario cenario, ProcessoDocumento documento) {
        documento.setEstadoValidacao(EstadoValidacao.REJEITADO);
        return processoDocumentoRepository.saveAndFlush(documento);
    }

    /** Planta `PO_01.zip` recusada, com 3 linhas `planta` (uma por folha) ligadas à ficha do Conjunto. */
    private ProcessoDocumento plantaRecusada(Cenario cenario) throws Exception {
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(zip("PO_01.zip", ZipsDeTeste.comEntradas(TIFFS)))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.itemPlantas().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        return transacao().execute(estado -> {
            ProcessoDocumento documento = processoDocumentoRepository.findById(id).orElseThrow();
            Processo processo = documento.getProcesso();
            plantaRepository.deleteByProcessoDocumento_Id(id);
            plantaRepository.flush();
            MetadadosMotu conjunto = metadadosMotuRepository.findByProcessoDocumento_Id(id).stream().findFirst()
                    .orElseGet(() -> metadadosMotuRepository.saveAndFlush(MetadadosMotu.conjunto(processo, documento)));
            for (int i = 0; i < TIFFS.length; i++) {
                Planta planta = new Planta(processo, documento);
                planta.setNomeRegulamento("Planta de Ordenamento");
                planta.setNumeroFolha(String.valueOf((char) ('A' + i)));
                planta.setNomeTiff(TIFFS[i]);
                planta.setNomeZip("PO_01.zip");
                planta.setMetadadosMotu(conjunto);
                plantaRepository.save(planta);
            }
            plantaRepository.flush();
            documento.setEstadoValidacao(EstadoValidacao.REJEITADO);
            return processoDocumentoRepository.saveAndFlush(documento);
        });
    }

    private TransactionTemplate transacao() {
        return new TransactionTemplate(transactionManager);
    }

    private JsonNode substituirRecusado(Cenario cenario, UUID documentoId, String nome, byte[] conteudo) throws Exception {
        String resposta = mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documentoId))
                        .file(nome.endsWith(".zip") ? zip(nome, conteudo) : ficheiro(nome, conteudo))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta);
    }

    private JsonNode lerJson(String texto) {
        try {
            return objectMapper.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockMultipartFile zip(String nome, byte[] conteudo) {
        return new MockMultipartFile("ficheiro", nome, "application/zip", conteudo);
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
        DocumentoConfig itemPlantas = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "PLANTA_PO", "Planta de Ordenamento", "Planta de Ordenamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                        "[\"zip\"]", 2));
        return new Cenario(processo, autor, entidade, documentoConfig, itemPlantas);
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
