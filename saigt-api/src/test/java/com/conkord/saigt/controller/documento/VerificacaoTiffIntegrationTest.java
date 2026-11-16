package com.conkord.saigt.controller.documento;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
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
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CaracteristicasTiff;
import com.conkord.saigt.service.documento.CaracteristicasTiff.BandaTiff;
import com.conkord.saigt.service.documento.InspecaoTiffIndisponivelException;
import com.conkord.saigt.service.documento.InspetorTiff;
import com.conkord.saigt.service.documento.TiffIlegivelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inspeção dos GeoTIFF no carregamento, pelo endpoint real e sobre o RustFS (bucket com versionamento e Object Lock),
 * com um {@link InspetorTiff} falso: o GDAL não corre aqui; a leitura das suas saídas está no
 * {@code GdalinfoInspetorTiffTest} e as regras no {@code VerificadorTiffTest}.
 */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "saigt.teste.bucket=" + VerificacaoTiffIntegrationTest.BUCKET})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacaoTiffIntegrationTest extends StorageTestcontainersBase {

    static final String BUCKET = "saigt-documentos-tiff-teste";
    private static final String MARCA = "TF";
    private static final String SERIE_NIPC = "5183021";
    private static final String PREFIXO_ERRO = "A peça gráfica não cumpre a especificação TIFF: ";
    private static final CaracteristicasTiff AMOSTRA = new CaracteristicasTiff("GTiff", 3763, "LZW",
            List.of(new BandaTiff("Byte", "Red"), new BandaTiff("Byte", "Green"), new BandaTiff("Byte", "Blue"),
                    new BandaTiff("Byte", "Alpha")), 300.0, 300.0, 2, true);

    static {
        MinioClient cliente = MinioClient.builder()
                .endpoint("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3))
                .credentials(ACCESS_KEY, SECRET_KEY).build();
        try {
            if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
                cliente.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).objectLock(true).build());
            }
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

    @SpyBean
    private ArmazenamentoDocumentos armazenamento;

    @MockBean
    private InspetorTiff inspetor;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    /** Itens: peças gráficas de depósito com as regras iniciais gerais e do SGIFR, de publicação e peças escritas. */
    private record Cenario(String codigo, Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig geral,
                           DocumentoConfig sgifr, DocumentoConfig publicacao, DocumentoConfig pecaEscrita) {

        String nomeZip() {
            return "plantas_" + codigo + ".zip";
        }
    }

    @BeforeEach
    void capturarLogs() {
        assertThat(storageProperties.bucket()).isEqualTo(BUCKET);
        logs.start();
        raiz.addAppender(logs);
    }

    @AfterEach
    void largarLogs() {
        raiz.detachAppender(logs);
    }

    @Test
    void deveAceitarAsAmostrasNumItemComAConfiguracaoInicialGeralInspecionandoSoOsTiffDoObjetoGravado()
            throws Exception {
        Cenario cenario = criarCenario("01");
        when(inspetor.inspecionar(anyString(), anyString())).thenReturn(AMOSTRA);

        ProcessoDocumento documento = carregarComSucesso(cenario, cenario.geral(), "DEPOSITO", cenario.nomeZip(),
                ZipsDeTeste.comEntradas("1303_PO_I_0993.tif", "1303_PO_I_0993.tfw", "1303_PO_I_0994.TIFF"));

        verify(inspetor).inspecionar(documento.getCaminhoFicheiro(), "1303_PO_I_0993.tif");
        verify(inspetor).inspecionar(documento.getCaminhoFicheiro(), "1303_PO_I_0994.TIFF");
        verify(inspetor, never()).inspecionar(anyString(), eq("1303_PO_I_0993.tfw"));
        assertThat(objetosCom(cenario, documento.getId().toString()))
                .containsExactly(documento.getCaminhoFicheiro() + "@" + documento.getVersaoObjeto());
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .filteredOn(mensagem -> mensagem.startsWith("Inspeção de TIFF")).hasSize(2)
                .allSatisfy(mensagem -> assertThat(mensagem).contains("resultado=ACEITE")
                        .contains("processoId=" + cenario.processo().getId()));
        assertSemDadosSensiveisNosLogs(cenario);
    }

    @Test
    void deveRecusarAsAmostrasNumItemDoSgifrComOsDoisMotivosSemDeixarLinhaNemObjeto() throws Exception {
        Cenario cenario = criarCenario("02");
        when(inspetor.inspecionar(anyString(), anyString())).thenReturn(AMOSTRA);

        carregar(cenario, cenario.sgifr(), "DEPOSITO", cenario.nomeZip(),
                ZipsDeTeste.comEntradas("1303_PO_I_0993.tif", "1303_PO_I_0993.tfw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TIFF_INVALIDO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"))
                .andExpect(jsonPath("$.campos[0].erro").value(
                        PREFIXO_ERRO + "compressão LZW não aceite; bandas adicionais não aceites."));

        assertGravadoEApagado(cenario);
        assertSemDocumentosNemObjetos(cenario);
        assertSemDadosSensiveisNosLogs(cenario);
    }

    @Test
    void deveRecusarNumSo400OsMotivosDeTodosOsTiffDoZip() throws Exception {
        Cenario cenario = criarCenario("03");
        when(inspetor.inspecionar(anyString(), eq("folha_1.tif"))).thenReturn(new CaracteristicasTiff("GTiff", 4326,
                "DEFLATE", List.of(new BandaTiff("Byte", "Red"), new BandaTiff("Byte", "Green"),
                new BandaTiff("Byte", "Blue")), null, null, null, true));
        when(inspetor.inspecionar(anyString(), eq("folha_2.tif"))).thenThrow(new TiffIlegivelException());

        carregar(cenario, cenario.geral(), "DEPOSITO", cenario.nomeZip(),
                ZipsDeTeste.comEntradas("folha_1.tif", "folha_2.tif"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TIFF_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value(PREFIXO_ERRO
                        + "sistema de referência EPSG:4326 não aceite; compressão DEFLATE não aceite; "
                        + "ficheiro TIFF ilegível."));

        assertGravadoEApagado(cenario);
        assertSemDocumentosNemObjetos(cenario);
    }

    @Test
    void deveAceitarUmTifSimplesComTfwERecusarSemGeorreferenciacaoOQueNaoTrazTfw() throws Exception {
        Cenario cenario = criarCenario("07");
        List<BandaTiff> rgb = List.of(new BandaTiff("Byte", "Red"), new BandaTiff("Byte", "Green"),
                new BandaTiff("Byte", "Blue"));
        when(inspetor.inspecionar(anyString(), eq("folha_antiga.tif")))
                .thenReturn(new CaracteristicasTiff("GTiff", null, "NONE", rgb, 300.0, 300.0, 2, true));
        when(inspetor.inspecionar(anyString(), eq("folha_sem_tfw.tif")))
                .thenReturn(new CaracteristicasTiff("GTiff", null, "NONE", rgb, 300.0, 300.0, 2, false));

        carregar(cenario, cenario.geral(), "DEPOSITO", "sem_tfw.zip", ZipsDeTeste.comEntradas("folha_sem_tfw.tif"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TIFF_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value(PREFIXO_ERRO
                        + "sem georreferenciação: falta o sistema de referência no TIFF ou o ficheiro TFW."));
        ProcessoDocumento aceite = carregarComSucesso(cenario, cenario.geral(), "DEPOSITO", "antigas.zip",
                ZipsDeTeste.comEntradas("folha_antiga.tif", "folha_antiga.tfw"));

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .extracting(ProcessoDocumento::getId).containsExactly(aceite.getId());
    }

    @Test
    void deveResponder500SemDetalheEApagarOObjetoQuandoAInspecaoFalha() throws Exception {
        Cenario cenario = criarCenario("04");
        when(inspetor.inspecionar(anyString(), anyString()))
                .thenThrow(new InspecaoTiffIndisponivelException("gdalinfo excedeu o tempo máximo de PT60S"));

        carregar(cenario, cenario.geral(), "DEPOSITO", cenario.nomeZip(), ZipsDeTeste.comEntradas("folha_1.tif"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.mensagem").value("Ocorreu um erro interno."))
                .andExpect(jsonPath("$.campos").doesNotExist());

        assertGravadoEApagado(cenario);
        assertSemDocumentosNemObjetos(cenario);
        assertThat(logs.list).filteredOn(evento -> evento.getLevel().toString().equals("ERROR"))
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(mensagem -> assertThat(mensagem).contains("Inspeção de TIFF indisponível")
                        .contains("processoId=" + cenario.processo().getId()).contains("extensao=tif"));
        assertSemDadosSensiveisNosLogs(cenario);
    }

    @Test
    void naoInspecionaNaPublicacaoNasPecasEscritasNemNoBaldeOutros() throws Exception {
        Cenario cenario = criarCenario("05");
        byte[] zip = ZipsDeTeste.comEntradas("folha_1.tif", "folha_1.tfw");

        carregarComSucesso(cenario, cenario.publicacao(), "PUBLICACAO", "publicacao.zip", zip);
        carregarComSucesso(cenario, cenario.pecaEscrita(), "DEPOSITO", "escrita.zip", zip);
        carregarComSucesso(cenario, null, "DEPOSITO", "outros.zip", zip);

        verify(inspetor, never()).inspecionar(any(), any());
    }

    @Test
    void deveManterOFicheiroAnteriorQuandoASubstituicaoTrazUmTiffQueNaoCumpre() throws Exception {
        Cenario cenario = criarCenario("06");
        when(inspetor.inspecionar(anyString(), anyString())).thenReturn(new CaracteristicasTiff("GTiff", 3763, "NONE",
                List.of(new BandaTiff("Byte", "Red"), new BandaTiff("Byte", "Green"), new BandaTiff("Byte", "Blue")),
                300.0, 300.0, 2, true));
        ProcessoDocumento original = carregarComSucesso(cenario, cenario.sgifr(), "DEPOSITO", "plantas.zip",
                ZipsDeTeste.comEntradas("folha_1.tif"));
        when(inspetor.inspecionar(anyString(), anyString())).thenReturn(AMOSTRA);

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + original.getId())
                        .file(ficheiro(cenario.nomeZip(), ZipsDeTeste.comEntradas("folha_2.tif")))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TIFF_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value(
                        PREFIXO_ERRO + "compressão LZW não aceite; bandas adicionais não aceites."));

        ProcessoDocumento atual = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(original.getCaminhoFicheiro());
        assertThat(atual.getHashSha256()).isEqualTo(original.getHashSha256());
        assertThat(atual.getVersaoObjeto()).isEqualTo(original.getVersaoObjeto());
        assertThat(objetosCom(cenario, original.getId().toString()))
                .containsExactly(original.getCaminhoFicheiro() + "@" + original.getVersaoObjeto());
        assertGravadoEApagado(cenario);
    }

    private void assertSemDocumentosNemObjetos(Cenario cenario) throws Exception {
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .isEmpty();
        assertThat(objetosCom(cenario, cenario.nomeZip())).isEmpty();
    }

    private void assertGravadoEApagado(Cenario cenario) {
        verify(armazenamento).guardar(contains(cenario.nomeZip()), any(), anyString());
        verify(armazenamento).removerTodasVersoes(contains(cenario.nomeZip()));
    }

    /** Nem a chave do objeto, nem o nome dos TIFF, nem as credenciais do storage. */
    private void assertSemDadosSensiveisNosLogs(Cenario cenario) {
        assertThat(logs.list).allSatisfy(evento -> {
            String texto = evento.getFormattedMessage()
                    + (evento.getThrowableProxy() == null ? "" : evento.getThrowableProxy().getMessage());
            assertThat(texto).doesNotContain(prefixo(cenario)).doesNotContain(cenario.nomeZip())
                    .doesNotContain("1303_PO_I").doesNotContain("folha_").doesNotContain(SECRET_KEY)
                    .doesNotContain("vsis3");
        });
    }

    private List<String> objetosCom(Cenario cenario, String marcador) throws Exception {
        List<String> resultado = new ArrayList<>();
        for (Result<Item> resposta : minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(storageProperties.bucket()).prefix(prefixo(cenario)).recursive(true).includeVersions(true)
                .build())) {
            Item item = resposta.get();
            if (item.objectName().contains(marcador)) {
                resultado.add(item.objectName() + "@" + item.versionId());
            }
        }
        return resultado;
    }

    private Cenario criarCenario(String numero) {
        String codigo = MARCA + numero;
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DOCT-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(
                new TipoDinamica("ELAB-DOCT-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCT-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig geral = item(procedimentoConfig, "PLANTAS_GERAL", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_GRAFICA, 1);
        DocumentoConfig sgifr = new DocumentoConfig(procedimentoConfig, "PLANTAS_SGIFR", "Plantas SGIFR",
                "Plantas", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                "[\"zip\"]", 2);
        sgifr.setRegrasTiff(RegrasTiff.inicialSgifr());
        sgifr = documentoConfigRepository.saveAndFlush(sgifr);
        DocumentoConfig publicacao = item(procedimentoConfig, "PLANTAS_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, 3);
        DocumentoConfig pecaEscrita = item(procedimentoConfig, "RELATORIO", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, 4);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(SERIE_NIPC + numero, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo.toLowerCase() + "@cm-tiff.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOCT/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(
                new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        return new Cenario(codigo, processo, autor, entidade, geral, sgifr, publicacao, pecaEscrita);
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimentoConfig, String codigo,
                                 CategoriaDocumento categoria, TipoElemento tipoElemento, int ordem) {
        return documentoConfigRepository.saveAndFlush(new DocumentoConfig(procedimentoConfig, codigo, codigo, codigo,
                categoria, tipoElemento, FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", ordem));
    }

    private ResultActions carregar(Cenario cenario, DocumentoConfig item, String categoria, String nome,
                                   byte[] conteudo) throws Exception {
        var pedido = multipart(url(cenario.processo())).file(ficheiro(nome, conteudo)).param("categoria", categoria);
        if (item != null) {
            pedido.param("documentoConfigId", item.getId().toString());
        }
        return mockMvc.perform(pedido.with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    private ProcessoDocumento carregarComSucesso(Cenario cenario, DocumentoConfig item, String categoria, String nome,
                                                 byte[] conteudo) throws Exception {
        String resposta = carregar(cenario, item, categoria, nome, conteudo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return processoDocumentoRepository.findById(UUID.fromString(objectMapper.readTree(resposta).get("id").asText()))
                .orElseThrow();
    }

    private static MockMultipartFile ficheiro(String nome, byte[] conteudo) {
        return new MockMultipartFile("ficheiro", nome, "application/zip", conteudo);
    }

    private RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private static String prefixo(Cenario cenario) {
        return "processos/" + cenario.processo().getNumeroProcesso() + "/";
    }
}
