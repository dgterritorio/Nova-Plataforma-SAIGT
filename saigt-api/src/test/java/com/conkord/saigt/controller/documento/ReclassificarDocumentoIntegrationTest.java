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
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
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
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CaracteristicasTiff;
import com.conkord.saigt.service.documento.CaracteristicasTiff.BandaTiff;
import com.conkord.saigt.service.documento.InspetorTiff;
import com.conkord.saigt.service.documento.ProcessoAlteravel;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.Item;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API-DOC-004: reclassificar um ficheiro gravado entre itens e caixas e alterar as tags, com RustFS versionado. */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "saigt.teste.bucket=" + ReclassificarDocumentoIntegrationTest.BUCKET_RECLASSIFICAR})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReclassificarDocumentoIntegrationTest extends StorageTestcontainersBase {

    static final String BUCKET_RECLASSIFICAR = "saigt-documentos-reclassificar-teste";
    private static final byte[] PDF = "%PDF-1.4 ficheiro a reclassificar".getBytes(StandardCharsets.UTF_8);
    private static final String MSG_TAGS = "Só é possível associar o ficheiro a itens de depósito obrigatório.";
    private static final String MSG_TAGS_ORIGEM =
            "Só um ficheiro de depósito obrigatório pode ser associado a outros itens.";
    private static final CaracteristicasTiff TIFF_COM_ALFA_E_LZW = new CaracteristicasTiff("GTiff", 3763, "LZW",
            List.of(new BandaTiff("Byte", "Red"), new BandaTiff("Byte", "Green"), new BandaTiff("Byte", "Blue"),
                    new BandaTiff("Byte", "Alpha")), 300.0, 300.0, 2, true);

    static {
        MinioClient cliente = MinioClient.builder()
                .endpoint("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3))
                .credentials(ACCESS_KEY, SECRET_KEY).build();
        try {
            if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_RECLASSIFICAR).build())) {
                cliente.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_RECLASSIFICAR).objectLock(true).build());
            }
            cliente.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(BUCKET_RECLASSIFICAR)
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
    private JdbcTemplate jdbcTemplate;
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
    @Autowired
    private PlantaRepository plantaRepository;
    @Autowired
    private MetadadosMotuRepository metadadosMotuRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @SpyBean
    private ArmazenamentoDocumentos armazenamento;
    @SpyBean
    private ProcessoAlteravel processoAlteravel;
    @MockBean
    private InspetorTiff inspetor;

    /**
     * delib e textoAto: depósito obrigatório (pdf); opcional: depósito não obrigatório; relatorioPub: peça escrita de
     * publicação (pdf); plantasPub e plantasPub2: peças gráficas de publicação (zip); anexosPub: «outros» de
     * publicação (zip); plantasDep: peça gráfica de depósito com as regras TIFF do SGIFR.
     */
    private record Cenario(Processo processo, Dinamica dinamica, Utilizador autor, Entidade entidade,
                           InstrumentoProcedimentoConfig procedimento, DocumentoConfig delib, DocumentoConfig textoAto,
                           DocumentoConfig opcional, DocumentoConfig relatorioPub, DocumentoConfig plantasPub,
                           DocumentoConfig plantasPub2, DocumentoConfig anexosPub, DocumentoConfig plantasDep) {

        String prefixo() {
            return "processos/" + processo.getNumeroProcesso() + "/";
        }
    }

    @BeforeEach
    void confirmarOBucket() {
        assertThat(storageProperties.bucket()).isEqualTo(BUCKET_RECLASSIFICAR);
    }

    // --- Critérios 1, 2 e 16 ---

    @Test
    void deveMudarDeItemNaMesmaCaixaSemMudarAChaveNemAVersaoEMostrarONaEstrutura() throws Exception {
        Cenario cenario = criarCenario("01");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.textoAto())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentoConfigId").value(cenario.textoAto().getId()))
                .andExpect(jsonPath("$.categoria").value("DEPOSITO"))
                .andExpect(jsonPath("$.tags.length()").value(0));

        ProcessoDocumento depois = relido(documento);
        assertThat(depois.getDocumentoConfig().getId()).isEqualTo(cenario.textoAto().getId());
        assertThat(depois.getCaminhoFicheiro()).isEqualTo(documento.getCaminhoFicheiro());
        assertThat(depois.getVersaoObjeto()).isEqualTo(documento.getVersaoObjeto());
        assertThat(depois.getHashSha256()).isEqualTo(documento.getHashSha256());
        assertThat(versoesSob(cenario.prefixo()))
                .containsExactly(documento.getCaminhoFicheiro() + "@" + documento.getVersaoObjeto());
        JsonNode estrutura = estrutura(cenario);
        assertThat(idsDosFicheiros(itemDaEstrutura(estrutura, cenario.textoAto()))).containsExactly(documento.getId().toString());
        assertThat(idsDosFicheiros(itemDaEstrutura(estrutura, cenario.delib()))).isEmpty();
    }

    @Test
    void devePassarParaOsOutrosNoDepositoENaPublicacao() throws Exception {
        Cenario cenario = criarCenario("02");
        ProcessoDocumento deposito = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);
        ProcessoDocumento publicacao = carregar(cenario, cenario.relatorioPub(), "relatorio.pdf", PDF);

        reclassificar(cenario, deposito.getId(), "DEPOSITO", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentoConfigId").doesNotExist());
        assertThat(estrutura(cenario).get("outros").get(0).get("id").asText()).isEqualTo(deposito.getId().toString());

        reclassificar(cenario, publicacao.getId(), "PUBLICACAO", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("PUBLICACAO"))
                .andExpect(jsonPath("$.documentoConfigId").doesNotExist());
        assertThat(relido(publicacao).getDocumentoConfig()).isNull();
    }

    @Test
    void deveRecusarComValidacaoUmCorpoSemCategoriaOuSemTags() throws Exception {
        Cenario cenario = criarCenario("03");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);

        patchCru(cenario, documento.getId(), "{\"documentoConfigId\":" + cenario.textoAto().getId() + ",\"tags\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("categoria"));
        patchCru(cenario, documento.getId(), "{\"categoria\":\"DEPOSITO\",\"documentoConfigId\":"
                + cenario.textoAto().getId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("tags"));

        assertThat(relido(documento).getDocumentoConfig().getId()).isEqualTo(cenario.delib().getId());
    }

    // --- Critérios 3, 4 e 5 ---

    @Test
    void deveCopiarParaAPublicacaoComNovaVersaoEApagarAsVersoesDoOriginalDepoisDoCommit() throws Exception {
        Cenario cenario = criarCenario("04");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);
        String chaveOriginal = documento.getCaminhoFicheiro();

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.relatorioPub())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("PUBLICACAO"))
                .andExpect(jsonPath("$.documentoConfigId").value(cenario.relatorioPub().getId()))
                .andExpect(jsonPath("$.hashSha256").value(documento.getHashSha256()));

        ProcessoDocumento depois = relido(documento);
        assertThat(depois.getCaminhoFicheiro()).matches(cenario.prefixo() + "PUBLICACAO/" + documento.getId()
                + "-[0-9a-f]{8}-deliberacao\\.pdf");
        assertThat(depois.getVersaoObjeto()).isNotEqualTo(documento.getVersaoObjeto()).isNotEqualTo("null");
        assertThat(depois.getHashSha256()).isEqualTo(documento.getHashSha256());
        assertThat(versoes(depois.getCaminhoFicheiro())).containsExactly(depois.getVersaoObjeto());
        assertThat(versoes(chaveOriginal)).isEmpty();
        assertThat(operacoes(depois.getCaminhoFicheiro())).containsExactly(OperacaoArmazenamento.COPIA);
        assertThat(operacoes(chaveOriginal))
                .containsExactlyInAnyOrder(OperacaoArmazenamento.GRAVACAO, OperacaoArmazenamento.APAGAMENTO);
    }

    @Test
    void deveRecusarAMudancaDeCaixaDeUmObjetoRetidoSemMudarNada() throws Exception {
        Cenario cenario = criarCenario("05");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);
        armazenamento.reter(documento.getCaminhoFicheiro(), documento.getVersaoObjeto(),
                Instant.now().plus(1, ChronoUnit.MINUTES));

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.relatorioPub())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_RETIDO"))
                .andExpect(jsonPath("$.mensagem").value("Este ficheiro está retido e não pode mudar de caixa."));

        assertNadaMudou(cenario, documento);
        verify(armazenamento, never()).copiar(anyString(), any(), anyString());
    }

    @Test
    void deveDeixarALinhaAsTagsEOOriginalComoEstavamQuandoACopiaFalha() throws Exception {
        Cenario cenario = criarCenario("06");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF, cenario.textoAto());
        doThrow(new StorageIndisponivelException("Falha simulada da cópia.", null))
                .when(armazenamento).copiar(anyString(), any(), anyString());

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.relatorioPub())
                .andExpect(status().isInternalServerError());

        assertNadaMudou(cenario, documento);
        assertThat(tagsGravadas(documento.getId())).containsExactly(cenario.textoAto().getId());
    }

    // --- Critério 6 ---

    @Test
    void deveDevolverConflitoDeVersaoQuandoOutraReclassificacaoGravaPrimeiro() throws Exception {
        Cenario cenario = criarCenario("07");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);
        // A primeira reclassificação grava enquanto a segunda já leu o documento.
        doAnswer(invocacao -> {
            CompletableFuture.runAsync(() -> jdbcTemplate.update(
                    "UPDATE processo_documento SET documento_config_id = ?, versao = versao + 1 WHERE id = ?",
                    cenario.textoAto().getId(), documento.getId())).join();
            return invocacao.callRealMethod();
        }).when(processoAlteravel).obterComBloqueio(eq(cenario.processo().getId()), any(ContextoUtilizador.class));

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.opcional())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_VERSAO_DESATUALIZADA"));

        assertThat(relido(documento).getDocumentoConfig().getId()).isEqualTo(cenario.textoAto().getId());
    }

    // --- Critérios 7, 8, 9 e 10 ---

    @Test
    void deveRecusarAExtensaoQueOItemDeDestinoNaoAceita() throws Exception {
        Cenario cenario = criarCenario("08");
        ProcessoDocumento documento = carregar(cenario, null, "memoria.docx", PDF);

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.delib())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"));

        assertThat(relido(documento).getDocumentoConfig()).isNull();
    }

    @Test
    void deveRecusarQuandoOItemDeDestinoJaTemONumeroMaximoDeFicheiros() throws Exception {
        Cenario cenario = criarCenario("09");
        cenario.textoAto().setNumeroMaximoFicheiros(1);
        documentoConfigRepository.saveAndFlush(cenario.textoAto());
        carregar(cenario, cenario.textoAto(), "texto.pdf", PDF);
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.textoAto())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TAMANHO_EXCEDIDO"));

        assertThat(relido(documento).getDocumentoConfig().getId()).isEqualTo(cenario.delib().getId());
    }

    @Test
    void deveRecusarOsGeoTiffQueNaoCumpremAsRegrasDoDestinoSemRemoverOObjeto() throws Exception {
        Cenario cenario = criarCenario("10");
        ProcessoDocumento documento = carregar(cenario, null, "plantas.zip", ZipsDeTeste.comEntradas("folha1.tif"));
        when(inspetor.inspecionar(anyString(), eq("folha1.tif"))).thenReturn(TIFF_COM_ALFA_E_LZW);

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.plantasDep())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TIFF_INVALIDO"));

        verify(inspetor).inspecionar(documento.getCaminhoFicheiro(), "folha1.tif");
        assertNadaMudou(cenario, documento);
    }

    @Test
    void deveRecusarComOCanalGeoconformAMudancaParaUmaPecaGrafica() throws Exception {
        Cenario cenario = criarCenario("11");
        ProcessoDocumento documento = carregar(cenario, null, "plantas.zip", ZipsDeTeste.comEntradas("folha1.tif"));
        cenario.dinamica().setComGeoconform(true);
        dinamicaRepository.saveAndFlush(cenario.dinamica());

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.plantasDep())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_CANAL_GEOCONFORM"));

        assertThat(relido(documento).getDocumentoConfig()).isNull();
    }

    // --- Critério 11 ---

    @Test
    void deveRecusarTagsInvalidasNoCampoTagsSemMudarNada() throws Exception {
        Cenario cenario = criarCenario("12");
        Cenario outro = criarCenario("13");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF, cenario.textoAto());

        for (Long tag : List.of(cenario.relatorioPub().getId(), cenario.opcional().getId(), outro.textoAto().getId(),
                999_999L)) {
            reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.textoAto(), tag)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                    .andExpect(jsonPath("$.campos[0].path").value("tags"))
                    .andExpect(jsonPath("$.campos[0].erro").value(MSG_TAGS));
        }

        assertThat(relido(documento).getDocumentoConfig().getId()).isEqualTo(cenario.delib().getId());
        assertThat(tagsGravadas(documento.getId())).containsExactly(cenario.textoAto().getId());
    }

    @Test
    void deveSubstituirAsTagsEDeixarDePendenteOItemSatisfeitoSoPorTag() throws Exception {
        Cenario cenario = criarCenario("14");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);
        assertThat(itemDaEstrutura(estrutura(cenario), cenario.textoAto()).get("pendente").asBoolean()).isTrue();

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.delib(), cenario.textoAto().getId(),
                cenario.textoAto().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andExpect(jsonPath("$.tags[0]").value(cenario.textoAto().getId()));
        assertThat(tagsGravadas(documento.getId())).containsExactly(cenario.textoAto().getId());
        assertThat(itemDaEstrutura(estrutura(cenario), cenario.textoAto()).get("pendente").asBoolean()).isFalse();

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.textoAto(), cenario.delib().getId())
                .andExpect(status().isOk());
        assertThat(tagsGravadas(documento.getId())).containsExactly(cenario.delib().getId());
        JsonNode estrutura = estrutura(cenario);
        assertThat(itemDaEstrutura(estrutura, cenario.textoAto()).get("pendente").asBoolean()).isFalse();
        assertThat(itemDaEstrutura(estrutura, cenario.delib()).get("pendente").asBoolean()).isFalse();

        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.textoAto())
                .andExpect(status().isOk());
        assertThat(tagsGravadas(documento.getId())).isEmpty();
        assertThat(itemDaEstrutura(estrutura(cenario), cenario.delib()).get("pendente").asBoolean()).isTrue();
    }

    // EF v1.5, Recolha de ficheiros: só os «ficheiros para depósito obrigatório» têm mais do que um tag.
    @Test
    void deveRecusarTagsQuandoODestinoNaoEDeDepositoObrigatorioSemMudarNada() throws Exception {
        Cenario cenario = criarCenario("23");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF, cenario.textoAto());

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.relatorioPub(), cenario.textoAto().getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("tags"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_TAGS_ORIGEM));
        reclassificar(cenario, documento.getId(), "DEPOSITO", cenario.opcional(), cenario.textoAto().getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_TAGS_ORIGEM));

        assertThat(relido(documento).getDocumentoConfig().getId()).isEqualTo(cenario.delib().getId());
        assertThat(tagsGravadas(documento.getId())).containsExactly(cenario.textoAto().getId());
    }

    // --- Critérios 12 e 17 ---

    @Test
    void deveApagarAPlantaEAFichaDoConjuntoEManterASerieAoSairDasPecasGraficasDePublicacao() throws Exception {
        Cenario cenario = criarCenario("15");
        ProcessoDocumento documento = carregar(cenario, cenario.plantasPub(), "plantas.zip",
                ZipsDeTeste.comEntradas("folha1.tif"));
        assertThat(plantaRepository.findByProcessoDocumento_Id(documento.getId())).hasSize(1);
        assertThat(tiposMotu(cenario)).containsExactlyInAnyOrder(TipoMetadadosMotu.SERIE, TipoMetadadosMotu.CDG);

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.anexosPub())
                .andExpect(status().isOk());

        assertThat(plantaRepository.findByProcessoDocumento_Id(documento.getId())).isEmpty();
        assertThat(tiposMotu(cenario)).containsExactly(TipoMetadadosMotu.SERIE);
    }

    @Test
    void deveManterALinhaDaPlantaComOsMesmosDadosEntreDuasPecasGraficas() throws Exception {
        Cenario cenario = criarCenario("16");
        ProcessoDocumento documento = carregar(cenario, cenario.plantasPub(), "plantas.zip",
                ZipsDeTeste.comEntradas("folha1.tif"));
        Planta planta = plantaRepository.findByProcessoDocumento_Id(documento.getId()).get(0);
        planta.setNomeRegulamento("Planta de Ordenamento");
        planta.setNumeroFolha("3");
        plantaRepository.saveAndFlush(planta);

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.plantasPub2())
                .andExpect(status().isOk());

        assertThat(plantaRepository.findByProcessoDocumento_Id(documento.getId())).singleElement().satisfies(depois -> {
            assertThat(depois.getId()).isEqualTo(planta.getId());
            assertThat(depois.getNomeRegulamento()).isEqualTo("Planta de Ordenamento");
            assertThat(depois.getNumeroFolha()).isEqualTo("3");
        });
        assertThat(tiposMotu(cenario)).containsExactlyInAnyOrder(TipoMetadadosMotu.SERIE, TipoMetadadosMotu.CDG);
    }

    @Test
    void deveCriarAPlantaEAFichaDoConjuntoAoEntrarNumaPecaGraficaDePublicacao() throws Exception {
        Cenario cenario = criarCenario("17");
        ProcessoDocumento documento = carregar(cenario, cenario.anexosPub(), "plantas.zip",
                ZipsDeTeste.comEntradas("folha1.tif"));
        assertThat(plantaRepository.findByProcessoDocumento_Id(documento.getId())).isEmpty();

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.plantasPub())
                .andExpect(status().isOk());

        assertThat(plantaRepository.findByProcessoDocumento_Id(documento.getId())).hasSize(1);
        assertThat(tiposMotu(cenario)).containsExactlyInAnyOrder(TipoMetadadosMotu.SERIE, TipoMetadadosMotu.CDG);
    }

    @Test
    void naoDeveMexerNaPlantaNemNaFichaQuandoAReclassificacaoFalha() throws Exception {
        Cenario cenario = criarCenario("18");
        ProcessoDocumento documento = carregar(cenario, cenario.plantasPub(), "plantas.zip",
                ZipsDeTeste.comEntradas("folha1.tif"));
        doThrow(new StorageIndisponivelException("Falha simulada da cópia.", null))
                .when(armazenamento).copiar(anyString(), any(), anyString());

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.anexosPub(), cenario.opcional().getId())
                .andExpect(status().isBadRequest());
        reclassificar(cenario, documento.getId(), "DEPOSITO", null)
                .andExpect(status().isInternalServerError());

        assertThat(plantaRepository.findByProcessoDocumento_Id(documento.getId())).hasSize(1);
        assertThat(tiposMotu(cenario)).containsExactlyInAnyOrder(TipoMetadadosMotu.SERIE, TipoMetadadosMotu.CDG);
        assertNadaMudou(cenario, documento);
    }

    // --- Critérios 13 e 14 ---

    @Test
    void deveRecusarUmFicheiroBloqueadoEUmProcessoForaDeEmSubmissao() throws Exception {
        Cenario cenario = criarCenario("19");
        ProcessoDocumento bloqueado = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);
        ProcessoDocumento livre = carregar(cenario, cenario.delib(), "outro.pdf", PDF);
        jdbcTemplate.update("UPDATE processo_documento SET bloqueado_edicao = true WHERE id = ?", bloqueado.getId());

        reclassificar(cenario, bloqueado.getId(), "DEPOSITO", cenario.textoAto())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_BLOQUEADO"));

        Processo processo = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        processo.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(processo);
        reclassificar(cenario, livre.getId(), "DEPOSITO", cenario.textoAto())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));

        assertThat(relido(bloqueado).getDocumentoConfig().getId()).isEqualTo(cenario.delib().getId());
        assertThat(relido(livre).getDocumentoConfig().getId()).isEqualTo(cenario.delib().getId());
    }

    @Test
    void deveRecusarQuemNaoEOAutorEQuemEDeOutraEntidadeSemMudarNada() throws Exception {
        Cenario cenario = criarCenario("20");
        Cenario outra = criarCenario("21");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "deliberacao.pdf", PDF);
        Utilizador colega = utilizadorRepository.saveAndFlush(new Utilizador("Colega 20", "colega20@cm-rcl.pt", "INCM"));

        mockMvc.perform(pedido(cenario, documento.getId(), corpo("PUBLICACAO", cenario.relatorioPub()))
                        .with(jwtDe(cenario.entidade(), colega.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(pedido(cenario, documento.getId(), corpo("PUBLICACAO", cenario.relatorioPub()))
                        .with(jwtDe(outra.entidade(), outra.autor().getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(pedido(outra, documento.getId(), corpo("DEPOSITO", outra.textoAto()))
                        .with(jwtDe(outra.entidade(), outra.autor().getId())))
                .andExpect(status().isNotFound());

        assertNadaMudou(cenario, documento);
    }

    // --- Critério 15 ---

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deveAuditarAsCaixasOsItensEAsTagsAnterioresENovasSemNomesDeFicheiroNoLog(CapturedOutput saida)
            throws Exception {
        Cenario cenario = criarCenario("22");
        ProcessoDocumento documento = carregar(cenario, cenario.delib(), "anexo_secreto.pdf", PDF,
                cenario.textoAto());
        int inicio = saida.getAll().length();

        reclassificar(cenario, documento.getId(), "PUBLICACAO", cenario.relatorioPub())
                .andExpect(status().isOk());

        List<Auditoria> registos = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "PROCESSO", cenario.processo().getId().toString(), "RECLASSIFICAR_DOCUMENTO");
        assertThat(registos).hasSize(1);
        JsonNode dados = objectMapper.readTree(registos.get(0).getDadosAlteracao());
        assertThat(dados.get("processoDocumentoId").asText()).isEqualTo(documento.getId().toString());
        assertThat(dados.get("categoriaAnterior").asText()).isEqualTo("DEPOSITO");
        assertThat(dados.get("categoria").asText()).isEqualTo("PUBLICACAO");
        assertThat(dados.get("documentoConfigIdAnterior").asLong()).isEqualTo(cenario.delib().getId());
        assertThat(dados.get("documentoConfigId").asLong()).isEqualTo(cenario.relatorioPub().getId());
        assertThat(dados.get("tagsAnteriores").get(0).asLong()).isEqualTo(cenario.textoAto().getId());
        assertThat(dados.get("tags")).isEmpty();
        assertThat(dados.has("nomeFicheiro")).isFalse();
        String log = saida.getAll().substring(inicio);
        assertThat(log).contains("Documento reclassificado").doesNotContain("anexo_secreto");
    }

    // --- Cenário e auxiliares ---

    private Cenario criarCenario(String numero) {
        String codigo = "RCL" + numero;
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        DocumentoConfig delib = item(procedimento, "DELIBERACAO_DEP", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, "pdf", true, 1);
        DocumentoConfig textoAto = item(procedimento, "TEXTO_ATO_DEP", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, "pdf", true, 2);
        DocumentoConfig opcional = item(procedimento, "ANEXO_DEP", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, "pdf", false, 3);
        DocumentoConfig relatorioPub = item(procedimento, "RELATORIO_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, "pdf", false, 4);
        DocumentoConfig plantasPub = item(procedimento, "PLANTAS_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, "zip", false, 5);
        DocumentoConfig plantasPub2 = item(procedimento, "PLANTA_ORD_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, "zip", false, 6);
        DocumentoConfig anexosPub = item(procedimento, "ANEXOS_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.OUTROS, "zip", false, 7);
        DocumentoConfig plantasDep = new DocumentoConfig(procedimento, "PLANTAS_DEP", "Plantas", "Plantas",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", 8);
        plantasDep.setObrigatorio(false);
        plantasDep.setRegrasTiff(RegrasTiff.inicialSgifr());
        plantasDep = documentoConfigRepository.saveAndFlush(plantasDep);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5092880" + numero, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo.toLowerCase() + "@cm-rcl.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/RCL/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo novo = new Processo(dinamica, procedimento, autor, entidade);
        // Num PLANO a estrutura só mostra os itens dos elementos afetados (FR-168).
        novo.setElementosAfetados(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS", "OUTROS"));
        Processo processo = processoRepository.saveAndFlush(novo);
        return new Cenario(processo, dinamica, autor, entidade, procedimento, delib, textoAto, opcional, relatorioPub,
                plantasPub, plantasPub2, anexosPub, plantasDep);
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, CategoriaDocumento categoria,
                                 TipoElemento tipoElemento, String extensao, boolean obrigatorio, int ordem) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, "Item " + codigo, "Separador " + ordem,
                categoria, tipoElemento, FormatoDocumento.EDITAVEL, "[\"" + extensao + "\"]", ordem);
        item.setObrigatorio(obrigatorio);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private static RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private ProcessoDocumento carregar(Cenario cenario, DocumentoConfig item, String nome, byte[] conteudo,
                                       DocumentoConfig... tags) throws Exception {
        var pedido = multipart(url(cenario.processo()))
                .file(new MockMultipartFile("ficheiro", nome, "application/octet-stream", conteudo));
        pedido.param("categoria", item == null ? "DEPOSITO" : item.getCategoria().name());
        if (item != null) {
            pedido.param("documentoConfigId", item.getId().toString());
        }
        if (tags.length > 0) {
            pedido.param("tags", Arrays.stream(tags).map(tag -> tag.getId().toString()).toArray(String[]::new));
        }
        String resposta = mockMvc.perform(pedido.with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return processoDocumentoRepository.findById(UUID.fromString(objectMapper.readTree(resposta).get("id").asText()))
                .orElseThrow();
    }

    private String corpo(String categoria, DocumentoConfig item, Long... tags) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("categoria", categoria);
        corpo.put("documentoConfigId", item == null ? null : item.getId());
        corpo.put("tags", List.of(tags));
        return objectMapper.writeValueAsString(corpo);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido(
            Cenario cenario, UUID documentoId, String corpo) {
        return patch(url(cenario.processo()) + "/" + documentoId + "/classificacao")
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private ResultActions reclassificar(Cenario cenario, UUID documentoId, String categoria, DocumentoConfig item,
                                        Long... tags) throws Exception {
        return patchCru(cenario, documentoId, corpo(categoria, item, tags));
    }

    private ResultActions patchCru(Cenario cenario, UUID documentoId, String corpo) throws Exception {
        return mockMvc.perform(pedido(cenario, documentoId, corpo)
                .with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    private ProcessoDocumento relido(ProcessoDocumento documento) {
        return processoDocumentoRepository.findById(documento.getId()).orElseThrow();
    }

    private List<Long> tagsGravadas(UUID documentoId) {
        return jdbcTemplate.queryForList("SELECT documento_config_id FROM processo_documento_tag "
                + "WHERE processo_documento_id = ? ORDER BY documento_config_id", Long.class, documentoId);
    }

    private List<TipoMetadadosMotu> tiposMotu(Cenario cenario) {
        return metadadosMotuRepository.findByProcesso_Id(cenario.processo().getId()).stream()
                .map(MetadadosMotu::getTipo).toList();
    }

    /** A linha, a chave, a versão e o único objeto no bucket ficam como no carregamento. */
    private void assertNadaMudou(Cenario cenario, ProcessoDocumento documento) throws Exception {
        ProcessoDocumento depois = relido(documento);
        assertThat(depois.getCategoria()).isEqualTo(documento.getCategoria());
        assertThat(depois.getDocumentoConfig() == null ? null : depois.getDocumentoConfig().getId())
                .isEqualTo(documento.getDocumentoConfig() == null ? null : documento.getDocumentoConfig().getId());
        assertThat(depois.getCaminhoFicheiro()).isEqualTo(documento.getCaminhoFicheiro());
        assertThat(depois.getVersaoObjeto()).isEqualTo(documento.getVersaoObjeto());
        assertThat(versoesSob(cenario.prefixo()).stream()
                .filter(versao -> versao.contains(documento.getId().toString())).toList())
                .containsExactly(documento.getCaminhoFicheiro() + "@" + documento.getVersaoObjeto());
        assertThat(intencoesSob(cenario.prefixo()).stream()
                .filter(intencao -> intencao.getChaveObjeto().contains(documento.getId().toString()))
                .map(ArmazenamentoIntencao::getOperacao).filter(operacao -> operacao != OperacaoArmazenamento.COPIA)
                .toList()).containsExactly(OperacaoArmazenamento.GRAVACAO);
    }

    private JsonNode estrutura(Cenario cenario) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()
                        + "/estrutura-documental").with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static JsonNode itemDaEstrutura(JsonNode estrutura, DocumentoConfig item) {
        for (JsonNode separador : estrutura.get("separadores")) {
            for (String caixa : List.of("publicacao", "deposito")) {
                for (JsonNode candidato : separador.get(caixa)) {
                    if (candidato.get("documentoConfigId").asLong() == item.getId()) {
                        return candidato;
                    }
                }
            }
        }
        throw new AssertionError("Item não encontrado na estrutura: " + item.getCodigo());
    }

    private static List<String> idsDosFicheiros(JsonNode item) {
        List<String> ids = new ArrayList<>();
        item.get("ficheiros").forEach(ficheiro -> ids.add(ficheiro.get("id").asText()));
        return ids;
    }

    private List<OperacaoArmazenamento> operacoes(String chave) {
        return intencaoRepository.findByChaveObjeto(chave).stream().map(ArmazenamentoIntencao::getOperacao).toList();
    }

    private List<ArmazenamentoIntencao> intencoesSob(String prefixo) {
        return intencaoRepository.findAll().stream().filter(i -> i.getChaveObjeto().startsWith(prefixo)).toList();
    }

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
        for (Result<Item> resposta : minioClient.listObjects(ListObjectsArgs.builder().bucket(BUCKET_RECLASSIFICAR)
                .prefix(prefixo).recursive(true).includeVersions(true).build())) {
            Item item = resposta.get();
            resultado.add(item.objectName() + "@" + item.versionId());
        }
        return resultado;
    }
}
