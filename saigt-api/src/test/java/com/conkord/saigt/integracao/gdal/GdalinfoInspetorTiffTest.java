package com.conkord.saigt.integracao.gdal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CaracteristicasTiff;
import com.conkord.saigt.service.documento.CaracteristicasTiff.BandaTiff;
import com.conkord.saigt.service.documento.InspecaoTiffIndisponivelException;
import com.conkord.saigt.service.documento.TiffIlegivelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura das saídas do `gdalinfo -json` guardadas em `src/test/resources/gdal/` (geradas com GDAL 3.13.3 a partir das
 * amostras e de TIFF sintéticos), a construção do processo e o executor com um executável falso.
 */
class GdalinfoInspetorTiffTest {

    private static final String CHAVE = "processos/2026-000123/DEPOSITO/0b7d-plantas.zip";
    private static final String ENTRADA = "1303_PO_I_0993.tif";
    private static final String ENTRADA_TRUNCADA = "geotif1204_ren.tif";
    private static final String SEGREDO = "segredo-do-storage-123";
    private static final String ACESSO = "chave-de-acesso-xyz";
    private static final List<BandaTiff> RGB = List.of(new BandaTiff("Byte", "Red"), new BandaTiff("Byte", "Green"),
            new BandaTiff("Byte", "Blue"));
    private static final List<BandaTiff> RGBA = List.of(new BandaTiff("Byte", "Red"), new BandaTiff("Byte", "Green"),
            new BandaTiff("Byte", "Blue"), new BandaTiff("Byte", "Alpha"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GdalinfoInspetorTiff.class);
    private Level nivelAnterior;

    @BeforeEach
    void capturarLogs() {
        nivelAnterior = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void largarLogs() {
        logger.detachAppender(logs);
        logger.setLevel(nivelAnterior);
    }

    @Test
    void deveLerAsDuasAmostrasComoLzwRgbComAlfa300DpiEEpsg3763() throws IOException {
        for (String amostra : List.of("amostra-1303-po-i-0993.json", "amostra-1303-po-i-0994.json")) {
            assertThat(ler(amostra)).as(amostra)
                    .isEqualTo(new CaracteristicasTiff("GTiff", 3763, "LZW", RGBA, 300.0, 300.0, 2, true));
        }
    }

    @Test
    void deveLerSemCompressaoComoNoneEAsEtiquetasDeResolucao() throws IOException {
        assertThat(ler("rgb-300dpi.json"))
                .isEqualTo(new CaracteristicasTiff("GTiff", 3763, "NONE", RGB, 300.0, 300.0, 2, true));
        assertThat(ler("150dpi.json"))
                .isEqualTo(new CaracteristicasTiff("GTiff", 3763, "NONE", RGB, 150.0, 150.0, 2, true));
        assertThat(ler("resolucao-em-cm.json"))
                .isEqualTo(new CaracteristicasTiff("GTiff", 3763, "NONE", RGB, 118.11, 118.11, 3, true));
    }

    @Test
    void deveLerUmTiffSemSistemaDeReferenciaNemGeorreferenciacao() throws IOException {
        assertThat(ler("sem-crs.json"))
                .isEqualTo(new CaracteristicasTiff("GTiff", null, "NONE", RGB, null, null, null, false));
    }

    @Test
    void deveLerUmTifSimplesComTfwComoGeorreferenciadoSemSistemaDeReferencia() throws IOException {
        assertThat(ler("tif-com-tfw.json"))
                .isEqualTo(new CaracteristicasTiff("GTiff", null, "NONE", RGB, 300.0, 300.0, 2, true));
    }

    @Test
    void deveLerCompressaoDeflateEpsg4326CinzentoE16Bits() throws IOException {
        assertThat(ler("deflate.json").compressao()).isEqualTo("DEFLATE");
        assertThat(ler("epsg-4326.json").codigoEpsg()).isEqualTo(4326);
        assertThat(ler("cinzento-1-banda.json").bandas()).containsExactly(new BandaTiff("Byte", "Gray"));
        assertThat(ler("16-bits.json").bandas()).extracting(BandaTiff::tipoDados)
                .containsExactly("UInt16", "UInt16", "UInt16");
        assertThat(ler("varios-incumprimentos.json")).isEqualTo(new CaracteristicasTiff("GTiff", 4326, "DEFLATE",
                List.of(new BandaTiff("UInt16", "Red"), new BandaTiff("UInt16", "Green"),
                        new BandaTiff("UInt16", "Blue")), 150.0, 150.0, 2, true));
    }

    @Test
    void deveTirarOEpsgDoIdDeTopoDoWktQuandoNaoHaStac() throws IOException {
        ObjectNode json = (ObjectNode) objectMapper.readTree(recurso("epsg-4326.json"));
        json.remove("stac");

        assertThat(GdalinfoInspetorTiff.lerJson(objectMapper, json.toString()).codigoEpsg()).isEqualTo(4326);
    }

    @Test
    void deveTratarJsonIlegivelComoFalhaTecnica() {
        assertThatThrownBy(() -> GdalinfoInspetorTiff.lerJson(objectMapper, "isto não é JSON"))
                .isInstanceOf(InspecaoTiffIndisponivelException.class);
        assertThatThrownBy(() -> GdalinfoInspetorTiff.lerJson(objectMapper, ""))
                .isInstanceOf(InspecaoTiffIndisponivelException.class);
    }

    @Test
    void deveCorrerSoComMenosJsonEOCaminhoVsizipVsis3EAsCredenciaisSoNoAmbiente() {
        ProcessBuilder processo = inspetor("http://rustfs:9000", "gdalinfo", Duration.ofSeconds(60))
                .construirProcesso(CHAVE, ENTRADA);

        assertThat(processo.command()).containsExactly("gdalinfo", "-json",
                "/vsizip//vsis3/saigt-documentos/" + CHAVE + "/" + ENTRADA);
        assertThat(String.join(" ", processo.command())).doesNotContain(SEGREDO).doesNotContain(ACESSO)
                .doesNotContain("X-Amz").doesNotContain("http");
        assertThat(processo.environment()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "AWS_ACCESS_KEY_ID", ACESSO,
                "AWS_SECRET_ACCESS_KEY", SEGREDO,
                "AWS_S3_ENDPOINT", "rustfs:9000",
                "AWS_HTTPS", "NO",
                "AWS_VIRTUAL_HOSTING", "FALSE",
                "GDAL_DISABLE_READDIR_ON_OPEN", "FALSE",
                "GDAL_HTTP_TIMEOUT", "60"));
    }

    @Test
    void deveUsarHttpsQuandoOEndpointInternoOTem() {
        Map<String, String> ambiente = inspetor("https://storage.interno", "gdalinfo", Duration.ofSeconds(30))
                .construirProcesso(CHAVE, ENTRADA).environment();

        assertThat(ambiente).containsEntry("AWS_S3_ENDPOINT", "storage.interno").containsEntry("AWS_HTTPS", "YES")
                .containsEntry("GDAL_HTTP_TIMEOUT", "30");
    }

    /** O adaptador nem conhece o armazenamento dos documentos: não há como pedir um URL pré-assinado. */
    @Test
    void naoDependeDoArmazenamentoNemDeUrlsPreAssinados() {
        for (Constructor<?> construtor : GdalinfoInspetorTiff.class.getConstructors()) {
            assertThat(construtor.getParameterTypes()).containsExactly(StorageProperties.class, TiffProperties.class,
                    ObjectMapper.class);
        }
        assertThat(Arrays.stream(ArmazenamentoDocumentos.class.getMethods()).map(Method::getName))
                .noneMatch(nome -> nome.toLowerCase().contains("url"));
    }

    @Test
    void deveLerASaidaDeUmProcessoQueTerminaBem() {
        GdalinfoInspetorTiff.ResultadoProcesso resultado =
                GdalinfoInspetorTiff.executar(falso("json"), Duration.ofSeconds(60));

        assertThat(resultado.codigoSaida()).isZero();
        assertThat(GdalinfoInspetorTiff.interpretar(objectMapper, resultado).formato()).isEqualTo("GTiff");
    }

    @Test
    void deveMatarOProcessoPassadoOTempoMaximo() {
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> GdalinfoInspetorTiff.executar(falso("dormir"), Duration.ofSeconds(2)))
                .isInstanceOf(InspecaoTiffIndisponivelException.class)
                .hasMessageContaining("tempo máximo");

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void deveDistinguirFicheiroNaoReconhecidoDeOutraFalha() {
        GdalinfoInspetorTiff.ResultadoProcesso naoReconhecido =
                GdalinfoInspetorTiff.executar(falso("nao-reconhecido"), Duration.ofSeconds(60));
        GdalinfoInspetorTiff.ResultadoProcesso falha =
                GdalinfoInspetorTiff.executar(falso("falha"), Duration.ofSeconds(60));
        GdalinfoInspetorTiff.ResultadoProcesso lixo =
                GdalinfoInspetorTiff.executar(falso("lixo"), Duration.ofSeconds(60));

        assertThatThrownBy(() -> GdalinfoInspetorTiff.interpretar(objectMapper, naoReconhecido))
                .isInstanceOf(TiffIlegivelException.class);
        assertThatThrownBy(() -> GdalinfoInspetorTiff.interpretar(objectMapper, falha))
                .isInstanceOf(InspecaoTiffIndisponivelException.class)
                .hasMessage("gdalinfo terminou com o código 1");
        assertThatThrownBy(() -> GdalinfoInspetorTiff.interpretar(objectMapper, lixo))
                .isInstanceOf(InspecaoTiffIndisponivelException.class);
    }

    @Test
    void deveTratarTiffTruncadoOuCorrompidoComoIlegivel() throws IOException {
        for (String saida : List.of("tiff-truncado.stderr.txt", "cabecalho-tiff-ilegivel.stderr.txt",
                "nao-reconhecido.stderr.txt")) {
            assertThatThrownBy(() -> GdalinfoInspetorTiff.interpretar(objectMapper, falhou(recurso(saida))))
                    .as(saida).isInstanceOf(TiffIlegivelException.class);
        }
        for (String mensagem : List.of("ERROR 1: a.tif: TIFFReadDirectory:Failed to read directory at offset 8",
                "ERROR 1: a.tif: TIFFFetchDirectory:a.tif: Can not read TIFF directory count",
                "ERROR 1: a.tif: TIFFFetchNormalTag:Incorrect count for \"XResolution\"",
                "ERROR 1: a.tif: TIFFFillStrip:Read error on strip 3", "ERROR 1: a.tif: TIFFFillTile:Read error",
                "ERROR 1: a.tif: Not a TIFF file, bad version number 1")) {
            assertThatThrownBy(() -> GdalinfoInspetorTiff.interpretar(objectMapper, falhou(mensagem)))
                    .as(mensagem).isInstanceOf(TiffIlegivelException.class);
        }
    }

    /** Uma leitura do storage que falha a meio também deixa mensagens da libtiff; não é culpa do ficheiro. */
    @Test
    void deveTratarErroDeAcessoAoArmazenamentoComoFalhaTecnicaSemStderrNemChaveNaMensagem() throws IOException {
        for (String saida : List.of("armazenamento-403.stderr.txt", "armazenamento-inacessivel.stderr.txt",
                "armazenamento-a-meio-da-leitura.stderr.txt")) {
            assertThatThrownBy(() -> GdalinfoInspetorTiff.interpretar(objectMapper, falhou(recurso(saida))))
                    .as(saida).isInstanceOf(InspecaoTiffIndisponivelException.class)
                    .hasMessage("gdalinfo terminou com o código 1");
        }
    }

    @Test
    void naoEscreveStderrNemChaveNoLogQuandoOTiffEstaTruncado() {
        GdalinfoInspetorTiff.ResultadoProcesso truncado =
                GdalinfoInspetorTiff.executar(falso("truncado"), Duration.ofSeconds(60));

        assertThat(truncado.erros()).contains("TIFFReadDirectory");
        assertThatThrownBy(() -> GdalinfoInspetorTiff.interpretar(objectMapper, truncado))
                .isInstanceOf(TiffIlegivelException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CHAVE).doesNotContain(ENTRADA_TRUNCADA)
                        .doesNotContain("TIFF directory"));
        assertThat(logs.list).isNotEmpty().extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(mensagem -> assertThat(mensagem).doesNotContain("TIFFReadDirectory")
                        .doesNotContain("TIFFFetchDirectory").doesNotContain(ENTRADA_TRUNCADA)
                        .doesNotContain("421794118"));
        assertSemDadosSensiveisNosLogs();
    }

    @Test
    void deveTratarOExecutavelEmFaltaComoFalhaTecnicaSemCredenciaisNemChaveNaMensagemNemNoLog() {
        GdalinfoInspetorTiff inspetor = inspetor("http://rustfs:9000",
                Path.of("nao-existe", "gdalinfo-inexistente").toAbsolutePath().toString(), Duration.ofSeconds(5));

        assertThatThrownBy(() -> inspetor.inspecionar(CHAVE, ENTRADA))
                .isInstanceOf(InspecaoTiffIndisponivelException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SEGREDO).doesNotContain(ACESSO)
                        .doesNotContain(CHAVE).doesNotContain(ENTRADA).doesNotContain("vsis3"))
                .hasNoCause();
        assertSemDadosSensiveisNosLogs();
    }

    @Test
    void naoEscreveCaminhoNemStderrNoLogQuandoOGdalFalha() {
        GdalinfoInspetorTiff.executar(falso("falha"), Duration.ofSeconds(60));

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(mensagem -> assertThat(mensagem).doesNotContain("403").doesNotContain("ERROR"));
        assertSemDadosSensiveisNosLogs();
    }

    private void assertSemDadosSensiveisNosLogs() {
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).allSatisfy(mensagem ->
                assertThat(mensagem).doesNotContain(SEGREDO).doesNotContain(ACESSO).doesNotContain(CHAVE)
                        .doesNotContain(ENTRADA).doesNotContain("vsis3"));
    }

    private static GdalinfoInspetorTiff inspetor(String endpoint, String gdalinfo, Duration timeout) {
        StorageProperties storage = new StorageProperties(endpoint, ACESSO, SEGREDO, "saigt-documentos", false, false,
                Duration.ofSeconds(10), Duration.ofSeconds(60));
        return new GdalinfoInspetorTiff(storage, new TiffProperties(gdalinfo, timeout), new ObjectMapper());
    }

    /** O falso corre com a JVM dos testes; o executor não sabe que não é o GDAL. */
    private static ProcessBuilder falso(String modo) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                GdalinfoFalso.class.getName(), modo);
    }

    private static GdalinfoInspetorTiff.ResultadoProcesso falhou(String erros) {
        return new GdalinfoInspetorTiff.ResultadoProcesso(1, "", erros);
    }

    private CaracteristicasTiff ler(String ficheiro) throws IOException {
        return GdalinfoInspetorTiff.lerJson(objectMapper, recurso(ficheiro));
    }

    private static String recurso(String ficheiro) throws IOException {
        try (InputStream stream = GdalinfoInspetorTiffTest.class.getResourceAsStream("/gdal/" + ficheiro)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
