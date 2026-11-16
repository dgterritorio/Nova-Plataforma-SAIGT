package com.conkord.saigt.persistence.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegrasTiffTest {

    private static final List<String> SISTEMAS = List.of("EPSG:3763", "EPSG:5014", "EPSG:5015", "EPSG:5016");
    private static final List<String> LEGENDA =
            List.of("ID do plano", "N.º de folha", "Data de aprovação", "Identificação do procedimento");

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void deveTerNaConfiguracaoInicialGeralOsValoresDoManualLegado() {
        RegrasTiff geral = RegrasTiff.inicialGeral();

        assertThat(geral.sistemasReferencia()).containsExactlyElementsOf(SISTEMAS);
        assertThat(geral.compressoesAceites()).containsExactly(CompressaoTiff.NONE, CompressaoTiff.LZW);
        assertThat(geral.bandaAlfaTolerada()).isTrue();
        assertThat(geral.modeloCor()).isNull();
        assertThat(geral.bitsPorBanda()).isNull();
        assertThat(geral.resolucaoDpi()).isNull();
        assertThat(geral.elementosLegenda()).containsExactlyElementsOf(LEGENDA);
    }

    @Test
    void deveTerNaConfiguracaoInicialDoSgifr300DpiRgb8BitsESemCompressao() {
        RegrasTiff sgifr = RegrasTiff.inicialSgifr();

        assertThat(sgifr.sistemasReferencia()).containsExactlyElementsOf(SISTEMAS);
        assertThat(sgifr.compressoesAceites()).containsExactly(CompressaoTiff.NONE);
        assertThat(sgifr.bandaAlfaTolerada()).isFalse();
        assertThat(sgifr.modeloCor()).isEqualTo(ModeloCorTiff.RGB);
        assertThat(sgifr.bitsPorBanda()).isEqualTo(8);
        assertThat(sgifr.resolucaoDpi()).isEqualTo(300);
        assertThat(sgifr.elementosLegenda()).containsExactlyElementsOf(LEGENDA);
    }

    @Test
    void devePreservarTodosOsCamposNaSerializacaoJsonDeIdaEVolta() throws Exception {
        RegrasTiff personalizada = new RegrasTiff(List.of("EPSG:3763"), List.of(CompressaoTiff.LZW), true,
                ModeloCorTiff.RGB, 16, 150, List.of("Escala"));

        for (RegrasTiff regras : List.of(RegrasTiff.inicialGeral(), RegrasTiff.inicialSgifr(), personalizada)) {
            assertThat(json.readValue(json.writeValueAsString(regras), RegrasTiff.class)).isEqualTo(regras);
        }
    }

    @Test
    void deveUsarOsNomesDosCamposDoContratoNoJson() throws Exception {
        assertThat(json.readTree(json.writeValueAsString(RegrasTiff.inicialGeral())).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("sistemasReferencia", "compressoesAceites", "bandaAlfaTolerada", "modeloCor",
                        "bitsPorBanda", "resolucaoDpi", "elementosLegenda");
    }

    @Test
    void deveRecusarCamposObrigatoriosEmFaltaOuNulos() {
        assertThatThrownBy(() -> json.readValue(
                "{\"sistemasReferencia\": [], \"compressoesAceites\": [\"NONE\"], \"elementosLegenda\": []}",
                RegrasTiff.class))
                .isInstanceOf(MismatchedInputException.class)
                .hasMessageContaining("bandaAlfaTolerada");
        assertThatThrownBy(() -> json.readValue("{\"bandaAlfaTolerada\": false}", RegrasTiff.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> json.readValue("{\"sistemasReferencia\": [], \"compressoesAceites\": [\"NONE\"], "
                + "\"bandaAlfaTolerada\": null, \"elementosLegenda\": []}", RegrasTiff.class))
                .isInstanceOf(InvalidNullException.class);
    }

    @Test
    void deveTratarListasNulasNoConstrutorComoVazias() {
        RegrasTiff regras = new RegrasTiff(null, null, false, null, null, null, null);

        assertThat(regras.sistemasReferencia()).isEmpty();
        assertThat(regras.compressoesAceites()).isEmpty();
        assertThat(regras.elementosLegenda()).isEmpty();
    }

    @Test
    void deveRecusarCompressaoOuModeloDeCorForaDosEnums() {
        assertThatThrownBy(() -> json.readValue("{\"compressoesAceites\": [\"JPEG\"]}", RegrasTiff.class))
                .isInstanceOf(InvalidFormatException.class);
        assertThatThrownBy(() -> json.readValue("{\"modeloCor\": \"CMYK\"}", RegrasTiff.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void deveDevolverAsRegrasGravadasOuAsIniciaisGeraisSoNasPecasGraficas() {
        DocumentoConfig vazia = item(TipoElemento.PECA_GRAFICA);
        DocumentoConfig gravada = item(TipoElemento.PECA_GRAFICA);
        gravada.setRegrasTiff(RegrasTiff.inicialSgifr());

        assertThat(vazia.regrasTiffEfetivas()).contains(RegrasTiff.inicialGeral());
        assertThat(gravada.regrasTiffEfetivas()).contains(RegrasTiff.inicialSgifr());
        assertThat(item(TipoElemento.PECA_ESCRITA).regrasTiffEfetivas()).isEmpty();
        assertThat(item(TipoElemento.OUTROS).regrasTiffEfetivas()).isEmpty();
    }

    private static DocumentoConfig item(TipoElemento tipoElemento) {
        return new DocumentoConfig(null, "ITEM", "Item", "Item", CategoriaDocumento.DEPOSITO, tipoElemento,
                FormatoDocumento.NAO_EDITAVEL, "[\"tif\"]", 1);
    }
}
