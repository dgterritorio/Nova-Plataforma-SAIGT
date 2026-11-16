package com.conkord.saigt.persistence.configuracao;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.List;

/**
 * Regras do GeoTIFF de um item de peças gráficas, gravadas em `documento_config.regras_tiff`. Uma lista de sistemas de
 * referência vazia e um modelo de cor, bits por banda ou resolução nulos não são verificados. A legenda é verificada
 * pela DGT na checklist, não automaticamente.
 *
 * <p>No JSON, as três listas e {@code bandaAlfaTolerada} são obrigatórias e não aceitam {@code null}: em falta, a
 * banda alfa ficaria {@code false} sem erro.
 */
public record RegrasTiff(
        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) List<String> sistemasReferencia,
        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) List<CompressaoTiff> compressoesAceites,
        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) boolean bandaAlfaTolerada,
        ModeloCorTiff modeloCor,
        Integer bitsPorBanda,
        Integer resolucaoDpi,
        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) List<String> elementosLegenda) {

    private static final List<String> SISTEMAS_REFERENCIA_INICIAIS =
            List.of("EPSG:3763", "EPSG:5014", "EPSG:5015", "EPSG:5016");
    private static final List<String> ELEMENTOS_LEGENDA_INICIAIS = List.of(
            "ID do plano", "N.º de folha", "Data de aprovação", "Identificação do procedimento");

    public RegrasTiff {
        sistemasReferencia = sistemasReferencia == null ? List.of() : sistemasReferencia;
        compressoesAceites = compressoesAceites == null ? List.of() : compressoesAceites;
        elementosLegenda = elementosLegenda == null ? List.of() : elementosLegenda;
    }

    /** Manual legado: PT-TM06/ETRS89 e PTRA08-UTM/ITRF93, sem compressão ou LZW, alfa tolerada, sem dpi/cor/bits. */
    public static RegrasTiff inicialGeral() {
        return new RegrasTiff(SISTEMAS_REFERENCIA_INICIAIS, List.of(CompressaoTiff.NONE, CompressaoTiff.LZW), true,
                null, null, null, ELEMENTOS_LEGENDA_INICIAIS);
    }

    /** SGIFR: 300 dpi, RGB, 8 bits por banda e sem compressão. */
    public static RegrasTiff inicialSgifr() {
        return new RegrasTiff(SISTEMAS_REFERENCIA_INICIAIS, List.of(CompressaoTiff.NONE), false,
                ModeloCorTiff.RGB, 8, 300, ELEMENTOS_LEGENDA_INICIAIS);
    }
}
