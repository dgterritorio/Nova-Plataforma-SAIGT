package com.conkord.saigt.controller.documento;

import com.conkord.saigt.configuration.StorageProperties;
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
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
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
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Um ficheiro validado e desbloqueado na correção substitui-se como um recusado: documento novo por validar e o
 * anterior SUBSTITUIDO em `SUBSTITUIDOS/`, com a justificação; não se remove.
 */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "saigt.teste.bucket=" + SubstituicaoDesbloqueadoIntegrationTest.BUCKET_VERSIONADO})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubstituicaoDesbloqueadoIntegrationTest extends StorageTestcontainersBase {

    static final String BUCKET_VERSIONADO = "saigt-documentos-desbloqueado-teste";
    private static final byte[] CONTEUDO = "%PDF-1.4 versão validada".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTEUDO_NOVO = "%PDF-1.4 versão corrigida".getBytes(StandardCharsets.UTF_8);
    private static final String JUSTIFICACAO = "A DGT pediu a correção do regulamento depois da validação.";

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
    private ArmazenamentoDocumentos armazenamento;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig documentoConfig) {
    }

    @BeforeEach
    void confirmarQueAAplicacaoUsaOBucketVersionado() {
        assertThat(storageProperties.bucket()).isEqualTo(BUCKET_VERSIONADO);
    }

    @Test
    void deveSubstituirODesbloqueadoPorUmDocumentoNovoPorValidarEGuardarOAnteriorEmSubstituidosComAJustificacao()
            throws Exception {
        Cenario cenario = criarCenario("01");
        ProcessoDocumento desbloqueado = desbloqueado(cenario, carregar(cenario, "regulamento.pdf"));
        String chaveOriginal = desbloqueado.getCaminhoFicheiro();

        JsonNode corpo = substituir(cenario, desbloqueado.getId(), "regulamento_v2.pdf");

        UUID novoId = UUID.fromString(corpo.get("id").asText());
        assertThat(novoId).isNotEqualTo(desbloqueado.getId());
        assertThat(corpo.get("estadoValidacao").asText()).isEqualTo("POR_VALIDAR");
        assertThat(corpo.get("justificacaoDesbloqueio").isNull()).isTrue();
        ProcessoDocumento novo = processoDocumentoRepository.findById(novoId).orElseThrow();
        assertThat(novo.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(novo.getJustificacaoDesbloqueio()).isNull();
        assertThat(novo.isBloqueadoEdicao()).isFalse();
        assertThat(novo.getDocumentoConfig().getId()).isEqualTo(cenario.documentoConfig().getId());
        assertThat(novo.getCaminhoFicheiro())
                .isEqualTo(prefixo(cenario) + "PUBLICACAO/" + novoId + "-regulamento_v2.pdf");

        ProcessoDocumento anterior = processoDocumentoRepository.findById(desbloqueado.getId()).orElseThrow();
        assertThat(anterior.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        assertThat(anterior.getJustificacaoDesbloqueio()).isEqualTo(JUSTIFICACAO);
        assertThat(anterior.getCaminhoFicheiro()).matches(prefixo(cenario) + "SUBSTITUIDOS/" + desbloqueado.getId()
                + "-[0-9a-f]{8}-regulamento\\.pdf");
        assertThat(versoes(chaveOriginal)).isEmpty();
        assertThat(versoes(anterior.getCaminhoFicheiro())).containsExactly(anterior.getVersaoObjeto());
        try (InputStream copia = armazenamento.abrir(anterior.getCaminhoFicheiro(), anterior.getVersaoObjeto(), 0, 0)) {
            assertThat(copia.readAllBytes()).isEqualTo(CONTEUDO);
        }
    }

    @Test
    void deveAuditarASubstituicaoDoDesbloqueadoComOsDoisIdsSemAJustificacao() throws Exception {
        Cenario cenario = criarCenario("02");
        ProcessoDocumento desbloqueado = desbloqueado(cenario, carregar(cenario, "regulamento.pdf"));

        JsonNode corpo = substituir(cenario, desbloqueado.getId(), "regulamento_v2.pdf");

        assertThat(auditorias(cenario, "SUBSTITUIR_DOCUMENTO_RECUSADO")).isEmpty();
        assertThat(auditorias(cenario, "SUBSTITUIR_DOCUMENTO_DESBLOQUEADO")).singleElement().satisfies(registo -> {
            assertThat(registo.getDadosAlteracao()).doesNotContain(JUSTIFICACAO);
            JsonNode dados = objectMapper.readTree(registo.getDadosAlteracao());
            assertThat(dados.get("processoDocumentoId").asText()).isEqualTo(desbloqueado.getId().toString());
            assertThat(dados.get("processoDocumentoNovoId").asText()).isEqualTo(corpo.get("id").asText());
            assertThat(dados.get("hashSha256Anterior").asText()).isEqualTo(desbloqueado.getHashSha256());
        });
    }

    @Test
    void deveRecusarARemocaoDoDesbloqueadoSemMudarNada() throws Exception {
        Cenario cenario = criarCenario("03");
        ProcessoDocumento desbloqueado = desbloqueado(cenario, carregar(cenario, "regulamento.pdf"));
        String chave = desbloqueado.getCaminhoFicheiro();

        mockMvc.perform(delete(url(cenario.processo(), desbloqueado.getId()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem")
                        .value("Na correção, um ficheiro desbloqueado substitui-se; não se remove."));

        ProcessoDocumento depois = processoDocumentoRepository.findById(desbloqueado.getId()).orElseThrow();
        assertThat(depois.getEstadoValidacao()).isEqualTo(EstadoValidacao.VALIDADO);
        assertThat(depois.getCaminhoFicheiro()).isEqualTo(chave);
        assertThat(versoes(chave)).containsExactly(desbloqueado.getVersaoObjeto());
    }

    /** Desbloqueado depois de ANULADO_INCM: a versão validada já tem retenção. */
    @Test
    void deveDeixarSoUmMarcadorNoOriginalRetidoEReterACopiaEmSubstituidos() throws Exception {
        Cenario cenario = criarCenario("04");
        ProcessoDocumento desbloqueado = desbloqueado(cenario, carregar(cenario, "regulamento.pdf"));
        String chaveOriginal = desbloqueado.getCaminhoFicheiro();
        Instant ate = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        armazenamento.reter(chaveOriginal, desbloqueado.getVersaoObjeto(), ate);

        substituir(cenario, desbloqueado.getId(), "regulamento_v2.pdf");

        ProcessoDocumento anterior = processoDocumentoRepository.findById(desbloqueado.getId()).orElseThrow();
        assertThat(anterior.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        // a versão retida continua no bucket e o marcador de eliminação é a segunda entrada
        assertThat(versoes(chaveOriginal)).hasSize(2).contains(desbloqueado.getVersaoObjeto());
        assertThat(armazenamento.versoes(chaveOriginal)).filteredOn(VersaoObjeto::retida)
                .extracting(VersaoObjeto::versionId).containsExactly(desbloqueado.getVersaoObjeto());
        Retention retencaoCopia = minioClient.getObjectRetention(GetObjectRetentionArgs.builder()
                .bucket(BUCKET_VERSIONADO).object(anterior.getCaminhoFicheiro()).versionId(anterior.getVersaoObjeto())
                .build());
        assertThat(retencaoCopia.retainUntilDate().toInstant()).isEqualTo(ate);
    }

    // ---------- auxiliares ----------

    /** Validado pela DGT e bloqueado pela correção, depois desbloqueado pelo endpoint. */
    private ProcessoDocumento desbloqueado(Cenario cenario, ProcessoDocumento documento) throws Exception {
        documento.setEstadoValidacao(EstadoValidacao.VALIDADO);
        documento.setBloqueadoEdicao(true);
        processoDocumentoRepository.saveAndFlush(documento);
        mockMvc.perform(patch(url(cenario.processo(), documento.getId()) + "/desbloquear")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("justificacao", JUSTIFICACAO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueadoEdicao").value(false));
        return processoDocumentoRepository.findById(documento.getId()).orElseThrow();
    }

    private JsonNode substituir(Cenario cenario, UUID documentoId, String nome) throws Exception {
        String resposta = mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documentoId))
                        .file(ficheiro(nome, CONTEUDO_NOVO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta);
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

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DESB-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DESB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DESB-" + codigo, "Procedimento D" + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig documentoConfig = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"pdf\"]", 1));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5097281" + codigo, "Câmara D" + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor D" + codigo, "autor.d" + codigo + "@cm-desb.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DESB/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        return new Cenario(processo, autor, entidade, documentoConfig);
    }

    private List<Auditoria> auditorias(Cenario cenario, String acao) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc("PROCESSO",
                cenario.processo().getId().toString(), acao);
    }

    private static MockMultipartFile ficheiro(String nome, byte[] conteudo) {
        return new MockMultipartFile("ficheiro", nome, "application/pdf", conteudo);
    }

    private RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
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

    /** `versionId` das versões e marcadores de eliminação da chave exata. */
    private List<String> versoes(String chave) throws Exception {
        List<String> resultado = new ArrayList<>();
        for (Result<Item> resposta : minioClient.listObjects(ListObjectsArgs.builder().bucket(BUCKET_VERSIONADO)
                .prefix(chave).recursive(true).includeVersions(true).build())) {
            Item item = resposta.get();
            if (item.objectName().equals(chave)) {
                resultado.add(item.versionId());
            }
        }
        return resultado;
    }
}
