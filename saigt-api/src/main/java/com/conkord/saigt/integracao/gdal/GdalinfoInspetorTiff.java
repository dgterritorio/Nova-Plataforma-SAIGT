package com.conkord.saigt.integracao.gdal;

import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.service.documento.CaracteristicasTiff;
import com.conkord.saigt.service.documento.InspecaoTiffIndisponivelException;
import com.conkord.saigt.service.documento.InspetorTiff;
import com.conkord.saigt.service.documento.TiffIlegivelException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * `gdalinfo -json` sobre `/vsizip//vsis3/`: o GDAL lê do storage só os intervalos de bytes de que precisa (cabeçalho
 * e IFD), com as credenciais internas da saigt-api — sem disco temporário e sem URL pré-assinado. Corre sem shell,
 * com as credenciais só no ambiente do processo filho (os argumentos veem-se na lista de processos), e nunca escreve
 * as credenciais, o caminho, a chave do objeto nem o stderr do GDAL em log ou exceção.
 */
@Component
@EnableConfigurationProperties(TiffProperties.class)
public class GdalinfoInspetorTiff implements InspetorTiff {

    private static final Logger log = LoggerFactory.getLogger(GdalinfoInspetorTiff.class);
    private static final int SAIDA_MAXIMA_BYTES = 4 * 1024 * 1024;
    private static final int ERROS_MAXIMO_BYTES = 64 * 1024;
    private static final Pattern NAO_RECONHECIDO = Pattern.compile("not recognized as (being in )?a supported file format");
    private static final Pattern TIFF_CORROMPIDO = Pattern.compile(
            "TIFF(ReadDirectory|FetchDirectory|FetchNormalTag|FillStrip|FillTile)"
                    + "|Cannot read TIFF header|Not a TIFF file");
    private static final Pattern FALHA_ARMAZENAMENTO = Pattern.compile(
            "HTTP response code|does not exist in the file system");
    private static final Pattern EPSG_DE_TOPO = Pattern.compile("ID\\[\"EPSG\",(\\d+)]\\s*]\\s*$");
    private static final Pattern NUMERO_INICIAL = Pattern.compile("^\\s*(\\d+)");
    private static final String SEM_COMPRESSAO = "NONE";

    private final StorageProperties storage;
    private final TiffProperties propriedades;
    private final ObjectMapper objectMapper;

    public GdalinfoInspetorTiff(StorageProperties storage, TiffProperties propriedades, ObjectMapper objectMapper) {
        this.storage = storage;
        this.propriedades = propriedades;
        this.objectMapper = objectMapper;
    }

    @Override
    public CaracteristicasTiff inspecionar(String chaveObjeto, String entradaZip) {
        return interpretar(objectMapper, executar(construirProcesso(chaveObjeto, entradaZip), propriedades.timeout()));
    }

    static CaracteristicasTiff interpretar(ObjectMapper objectMapper, ResultadoProcesso resultado) {
        if (resultado.codigoSaida() != 0) {
            if (ilegivel(resultado.erros())) {
                throw new TiffIlegivelException();
            }
            throw new InspecaoTiffIndisponivelException("gdalinfo terminou com o código " + resultado.codigoSaida());
        }
        return lerJson(objectMapper, resultado.saida());
    }

    /** Uma leitura do storage que falha a meio também deixa mensagens da libtiff: a falha de acesso prevalece. */
    private static boolean ilegivel(String erros) {
        if (FALHA_ARMAZENAMENTO.matcher(erros).find()) {
            return false;
        }
        return NAO_RECONHECIDO.matcher(erros).find() || TIFF_CORROMPIDO.matcher(erros).find();
    }

    ProcessBuilder construirProcesso(String chaveObjeto, String entradaZip) {
        String caminho = "/vsizip//vsis3/" + storage.bucket() + "/" + chaveObjeto + "/" + entradaZip;
        ProcessBuilder processo = new ProcessBuilder(List.of(propriedades.gdalinfo(), "-json", caminho));
        Map<String, String> ambiente = processo.environment();
        ambiente.clear();
        URI endpoint = URI.create(storage.endpoint());
        ambiente.put("AWS_ACCESS_KEY_ID", storage.accessKey());
        ambiente.put("AWS_SECRET_ACCESS_KEY", storage.secretKey());
        ambiente.put("AWS_S3_ENDPOINT", endpoint.getPort() < 0 ? endpoint.getHost()
                : endpoint.getHost() + ":" + endpoint.getPort());
        ambiente.put("AWS_HTTPS", "https".equalsIgnoreCase(endpoint.getScheme()) ? "YES" : "NO");
        ambiente.put("AWS_VIRTUAL_HOSTING", "FALSE");
        // O TFW com o mesmo nome dá a georreferenciação de um TIF simples: o GDAL procura-o só entre as entradas do
        // ZIP, listadas a partir do diretório central que já leu.
        ambiente.put("GDAL_DISABLE_READDIR_ON_OPEN", "FALSE");
        ambiente.put("GDAL_HTTP_TIMEOUT", String.valueOf(Math.max(1, propriedades.timeout().toSeconds())));
        return processo;
    }

    /** stdout e stderr lidos em paralelo, com limite, para o processo nunca bloquear com o pipe cheio. */
    static ResultadoProcesso executar(ProcessBuilder construtor, Duration timeout) {
        long inicio = System.nanoTime();
        Process processo;
        try {
            processo = construtor.start();
        } catch (IOException e) {
            throw new InspecaoTiffIndisponivelException("gdalinfo não arrancou (" + e.getClass().getSimpleName() + ")");
        }
        try {
            processo.getOutputStream().close();
            CompletableFuture<byte[]> saida = ler(processo.getInputStream(), SAIDA_MAXIMA_BYTES);
            CompletableFuture<byte[]> erros = ler(processo.getErrorStream(), ERROS_MAXIMO_BYTES);
            if (!processo.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new InspecaoTiffIndisponivelException("gdalinfo excedeu o tempo máximo de " + timeout);
            }
            ResultadoProcesso resultado = new ResultadoProcesso(processo.exitValue(),
                    new String(saida.get(timeout.toMillis(), TimeUnit.MILLISECONDS), StandardCharsets.UTF_8),
                    new String(erros.get(timeout.toMillis(), TimeUnit.MILLISECONDS), StandardCharsets.UTF_8));
            log.debug("gdalinfo terminou — codigoSaida={} duracaoMs={}", resultado.codigoSaida(),
                    (System.nanoTime() - inicio) / 1_000_000);
            return resultado;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InspecaoTiffIndisponivelException("inspeção interrompida");
        } catch (IOException | ExecutionException | TimeoutException e) {
            throw new InspecaoTiffIndisponivelException("saída do gdalinfo ilegível (" + e.getClass().getSimpleName() + ")");
        } finally {
            processo.descendants().forEach(ProcessHandle::destroyForcibly);
            processo.destroyForcibly();
        }
    }

    private static CompletableFuture<byte[]> ler(InputStream stream, int maximo) {
        CompletableFuture<byte[]> resultado = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (stream) {
                byte[] bytes = stream.readNBytes(maximo);
                stream.transferTo(OutputStream.nullOutputStream());
                resultado.complete(bytes);
            } catch (IOException e) {
                resultado.completeExceptionally(e);
            }
        });
        return resultado;
    }

    static CaracteristicasTiff lerJson(ObjectMapper objectMapper, String json) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InspecaoTiffIndisponivelException("JSON do gdalinfo ilegível");
        }
        if (raiz == null || !raiz.isObject()) {
            throw new InspecaoTiffIndisponivelException("JSON do gdalinfo ilegível");
        }
        JsonNode metadados = raiz.path("metadata");
        JsonNode etiquetas = metadados.path("");
        String compressao = texto(metadados.path("IMAGE_STRUCTURE").path("COMPRESSION"));
        List<CaracteristicasTiff.BandaTiff> bandas = new ArrayList<>();
        for (JsonNode banda : raiz.path("bands")) {
            bandas.add(new CaracteristicasTiff.BandaTiff(texto(banda.path("type")),
                    texto(banda.path("colorInterpretation"))));
        }
        return new CaracteristicasTiff(
                texto(raiz.path("driverShortName")),
                codigoEpsg(raiz),
                compressao == null ? SEM_COMPRESSAO : compressao.toUpperCase(Locale.ROOT),
                bandas,
                decimal(etiquetas.path("TIFFTAG_XRESOLUTION")),
                decimal(etiquetas.path("TIFFTAG_YRESOLUTION")),
                inteiroInicial(etiquetas.path("TIFFTAG_RESOLUTIONUNIT")),
                raiz.path("geoTransform").isArray() && !raiz.path("geoTransform").isEmpty());
    }

    /** O EPSG do STAC; na falta, o `ID["EPSG",n]` de topo do WKT (o último, que fecha o CRS). */
    private static Integer codigoEpsg(JsonNode raiz) {
        JsonNode stac = raiz.path("stac").path("proj:epsg");
        if (stac.canConvertToInt() && stac.isNumber()) {
            return stac.asInt();
        }
        String wkt = texto(raiz.path("coordinateSystem").path("wkt"));
        if (wkt == null) {
            return null;
        }
        Matcher id = EPSG_DE_TOPO.matcher(wkt.strip());
        return id.find() ? Integer.valueOf(id.group(1)) : null;
    }

    private static String texto(JsonNode no) {
        return no.isTextual() && !no.asText().isBlank() ? no.asText() : null;
    }

    private static Double decimal(JsonNode no) {
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        try {
            return Double.valueOf(valor.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer inteiroInicial(JsonNode no) {
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        Matcher numero = NUMERO_INICIAL.matcher(valor);
        return numero.find() ? Integer.valueOf(numero.group(1)) : null;
    }

    record ResultadoProcesso(int codigoSaida, String saida, String erros) {
    }
}
