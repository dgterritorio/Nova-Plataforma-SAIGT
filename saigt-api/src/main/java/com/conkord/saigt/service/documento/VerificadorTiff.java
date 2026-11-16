package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.TiffInvalidoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.ModeloCorTiff;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GeoTIFF das peças gráficas de depósito contra as regras do item (`regras_tiff`), sobre o ZIP já gravado. Tudo vem
 * das regras do item: nenhum código distingue o SGIFR nem o tipo de Instrumento.
 */
@Component
public class VerificadorTiff {

    static final String MSG_ILEGIVEL = "ficheiro TIFF ilegível";
    static final String MSG_SEM_GEORREFERENCIACAO =
            "sem georreferenciação: falta o sistema de referência no TIFF ou o ficheiro TFW";
    static final String MSG_MODELO_COR = "modelo de cor diferente de RGB";
    static final String MSG_BANDAS_ADICIONAIS = "bandas adicionais não aceites";
    static final String MSG_BANDA_ALFA = "banda alfa não aceite";
    static final String MSG_RESOLUCAO_NAO_INDICADA = "resolução não indicada";

    private static final Logger log = LoggerFactory.getLogger(VerificadorTiff.class);
    private static final String FORMATO_GEOTIFF = "GTiff";
    private static final Set<String> EXTENSOES_TIFF = Set.of("tif", "tiff");
    private static final List<String> BANDAS_RGB = List.of("Red", "Green", "Blue");
    private static final String BANDA_ALFA = "Alpha";
    private static final Map<Integer, String> TIPO_POR_BITS = Map.of(8, "Byte", 16, "UInt16");
    private static final Pattern BITS_DO_TIPO = Pattern.compile("(\\d+)$");
    private static final int UNIDADE_POLEGADA = 2;
    private static final int UNIDADE_CENTIMETRO = 3;
    private static final double CENTIMETROS_POR_POLEGADA = 2.54;
    private static final double TOLERANCIA_DPI = 0.5;

    private final InspetorTiff inspetor;

    public VerificadorTiff(InspetorTiff inspetor) {
        this.inspetor = inspetor;
    }

    /**
     * Só a caixa de depósito de um item de peças gráficas: o balde «outros», as peças escritas e o ZIP de publicação
     * (só informação vetorial) não têm GeoTIFF a verificar.
     */
    public static Optional<RegrasTiff> regrasAplicaveis(CategoriaDocumento categoria, DocumentoConfig documentoConfig) {
        if (documentoConfig == null || categoria != CategoriaDocumento.DEPOSITO) {
            return Optional.empty();
        }
        return documentoConfig.regrasTiffEfetivas();
    }

    public static boolean ehTiff(String entrada) {
        return EXTENSOES_TIFF.contains(CaminhoDocumento.extensao(entrada));
    }

    /** Falha uma vez, com os motivos de todos os TIFF do ZIP. */
    public void verificar(UUID processoId, String chaveObjeto, List<String> entradasZip, RegrasTiff regras) {
        Set<String> motivos = new LinkedHashSet<>();
        for (String entrada : entradasZip) {
            if (ehTiff(entrada)) {
                motivos.addAll(inspecionar(processoId, chaveObjeto, entrada, regras));
            }
        }
        if (!motivos.isEmpty()) {
            throw new TiffInvalidoException(List.copyOf(motivos));
        }
    }

    private List<String> inspecionar(UUID processoId, String chaveObjeto, String entrada, RegrasTiff regras) {
        long inicio = System.nanoTime();
        List<String> motivos;
        try {
            motivos = motivos(inspetor.inspecionar(chaveObjeto, entrada), regras);
        } catch (TiffIlegivelException e) {
            motivos = List.of(MSG_ILEGIVEL);
        } catch (InspecaoTiffIndisponivelException e) {
            log.error("Inspeção de TIFF indisponível — processoId={} extensao={} causa={}", processoId,
                    CaminhoDocumento.extensao(entrada), e.getMessage());
            throw e;
        }
        log.info("Inspeção de TIFF — processoId={} resultado={} motivos={} duracaoMs={}", processoId,
                motivos.isEmpty() ? "ACEITE" : "RECUSADO", motivos.size(), (System.nanoTime() - inicio) / 1_000_000);
        return motivos;
    }

    static List<String> motivos(CaracteristicasTiff tiff, RegrasTiff regras) {
        if (tiff == null || !FORMATO_GEOTIFF.equals(tiff.formato())) {
            return List.of(MSG_ILEGIVEL);
        }
        List<String> motivos = new ArrayList<>();
        sistemaReferencia(tiff, regras).ifPresent(motivos::add);
        String compressao = tiff.compressao() == null ? "NONE" : tiff.compressao();
        if (regras.compressoesAceites().stream().noneMatch(aceite -> aceite.name().equalsIgnoreCase(compressao))) {
            motivos.add("compressão " + compressao + " não aceite");
        }
        motivos.addAll(bandas(tiff.bandas(), regras));
        bitsPorBanda(tiff.bandas(), regras.bitsPorBanda()).ifPresent(motivos::add);
        resolucao(tiff, regras.resolucaoDpi()).ifPresent(motivos::add);
        return motivos;
    }

    /**
     * A EF admite o GeoTIFF e o TIF simples com TFW: este traz a georreferenciação no TFW e não indica o sistema de
     * referência, que fica para a validação da DGT.
     */
    private static Optional<String> sistemaReferencia(CaracteristicasTiff tiff, RegrasTiff regras) {
        if (regras.sistemasReferencia().isEmpty()) {
            return Optional.empty();
        }
        if (!tiff.georreferenciado()) {
            return Optional.of(MSG_SEM_GEORREFERENCIACAO);
        }
        if (tiff.codigoEpsg() == null) {
            return Optional.empty();
        }
        String sistema = "EPSG:" + tiff.codigoEpsg();
        boolean aceite = regras.sistemasReferencia().stream().anyMatch(sistema::equalsIgnoreCase);
        return aceite ? Optional.empty() : Optional.of("sistema de referência " + sistema + " não aceite");
    }

    /**
     * Com RGB, as três primeiras são Red, Green e Blue e só se aceita uma quarta, Alpha, com a alfa tolerada (e então
     * ignorada). Sem modelo de cor, só a banda alfa é verificada.
     */
    private static List<String> bandas(List<CaracteristicasTiff.BandaTiff> bandas, RegrasTiff regras) {
        List<String> cores = bandas.stream().map(CaracteristicasTiff.BandaTiff::interpretacaoCor).toList();
        List<String> motivos = new ArrayList<>();
        if (regras.modeloCor() == ModeloCorTiff.RGB) {
            if (cores.size() < BANDAS_RGB.size() || !cores.subList(0, BANDAS_RGB.size()).equals(BANDAS_RGB)) {
                motivos.add(MSG_MODELO_COR);
            }
            List<String> adicionais = cores.size() > BANDAS_RGB.size()
                    ? cores.subList(BANDAS_RGB.size(), cores.size()) : List.of();
            boolean alfaTolerada = adicionais.equals(List.of(BANDA_ALFA)) && regras.bandaAlfaTolerada();
            if (!adicionais.isEmpty() && !alfaTolerada) {
                motivos.add(MSG_BANDAS_ADICIONAIS);
            }
        } else if (cores.contains(BANDA_ALFA) && !regras.bandaAlfaTolerada()) {
            motivos.add(MSG_BANDA_ALFA);
        }
        return motivos;
    }

    private static Optional<String> bitsPorBanda(List<CaracteristicasTiff.BandaTiff> bandas, Integer bitsPorBanda) {
        if (bitsPorBanda == null) {
            return Optional.empty();
        }
        String tipoEsperado = TIPO_POR_BITS.get(bitsPorBanda);
        return bandas.stream()
                .map(CaracteristicasTiff.BandaTiff::tipoDados)
                .filter(tipo -> tipoEsperado == null || !tipoEsperado.equals(tipo))
                .findFirst()
                .map(tipo -> bitsDoTipo(tipo) + " bits por banda em vez de " + bitsPorBanda);
    }

    private static String bitsDoTipo(String tipo) {
        if ("Byte".equals(tipo)) {
            return "8";
        }
        Matcher bits = tipo == null ? null : BITS_DO_TIPO.matcher(tipo);
        return bits != null && bits.find() ? bits.group(1) : "?";
    }

    private static Optional<String> resolucao(CaracteristicasTiff tiff, Integer resolucaoDpi) {
        if (resolucaoDpi == null) {
            return Optional.empty();
        }
        Double dpiX = emDpi(tiff.resolucaoX(), tiff.unidadeResolucao());
        Double dpiY = emDpi(tiff.resolucaoY(), tiff.unidadeResolucao());
        if (dpiX == null || dpiY == null) {
            return Optional.of(MSG_RESOLUCAO_NAO_INDICADA);
        }
        for (double dpi : new double[]{dpiX, dpiY}) {
            if (Math.abs(dpi - resolucaoDpi) > TOLERANCIA_DPI) {
                return Optional.of("resolução de " + formatarDpi(dpi) + " dpi em vez de " + resolucaoDpi);
            }
        }
        return Optional.empty();
    }

    /** Sem unidade absoluta (etiqueta 1 ou em falta) a resolução não diz os dpi. */
    private static Double emDpi(Double valor, Integer unidade) {
        if (valor == null || unidade == null) {
            return null;
        }
        return switch (unidade) {
            case UNIDADE_POLEGADA -> valor;
            case UNIDADE_CENTIMETRO -> valor * CENTIMETROS_POR_POLEGADA;
            default -> null;
        };
    }

    private static String formatarDpi(double dpi) {
        return new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-PT"))).format(dpi);
    }
}
