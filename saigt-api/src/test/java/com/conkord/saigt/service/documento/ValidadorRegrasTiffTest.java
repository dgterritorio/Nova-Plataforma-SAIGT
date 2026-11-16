package com.conkord.saigt.service.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.CompressaoTiff;
import com.conkord.saigt.persistence.configuracao.ModeloCorTiff;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorRegrasTiffTest {

    private final ValidadorRegrasTiff validador = new ValidadorRegrasTiff();

    @Test
    void deveAceitarAsConfiguracoesIniciaisESemRegrasNumaPecaGrafica() {
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, RegrasTiff.inicialGeral())).isEmpty();
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, RegrasTiff.inicialSgifr())).isEmpty();
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, null)).isEmpty();
        assertThatCode(() -> validador.validar(TipoElemento.PECA_GRAFICA, RegrasTiff.inicialGeral()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = TipoElemento.class, names = {"PECA_ESCRITA", "OUTROS"})
    void deveRecusarRegrasNumItemQueNaoEPecaGrafica(TipoElemento tipoElemento) {
        assertThat(validador.erros(tipoElemento, RegrasTiff.inicialGeral()))
                .containsExactly(new CampoErro("regrasTiff", ValidadorRegrasTiff.MSG_SO_PECAS_GRAFICAS));
        assertThat(validador.erros(tipoElemento, null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"3763", "EPSG:376", "EPSG:376300", "epsg:3763", "EPSG: 3763", "EPSG:3763 "})
    void deveRecusarSistemaDeReferenciaForaDoFormatoEpsg(String sistema) {
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comSistemas(List.of("EPSG:3763", sistema))))
                .containsExactly(new CampoErro("regrasTiff.sistemasReferencia",
                        ValidadorRegrasTiff.MSG_SISTEMAS_REFERENCIA));
    }

    @Test
    void deveRecusarSistemasDeReferenciaRepetidosOuNulosEAceitarListaVaziaECincoAlgarismos() {
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comSistemas(List.of("EPSG:3763", "EPSG:3763"))))
                .extracting(CampoErro::path).containsExactly("regrasTiff.sistemasReferencia");
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comSistemas(Arrays.asList("EPSG:3763", null))))
                .extracting(CampoErro::path).containsExactly("regrasTiff.sistemasReferencia");
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comSistemas(List.of()))).isEmpty();
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comSistemas(List.of("EPSG:3763", "EPSG:32628"))))
                .isEmpty();
    }

    @Test
    void deveRecusarCompressoesVaziasOuComValorForaDoEnum() {
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comCompressoes(List.of())))
                .containsExactly(new CampoErro("regrasTiff.compressoesAceites", ValidadorRegrasTiff.MSG_COMPRESSOES));
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comCompressoes(null)))
                .extracting(CampoErro::path).containsExactly("regrasTiff.compressoesAceites");
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comCompressoes(Arrays.asList(CompressaoTiff.NONE, null))))
                .extracting(CampoErro::path).containsExactly("regrasTiff.compressoesAceites");
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comCompressoes(List.of(CompressaoTiff.LZW)))).isEmpty();
    }

    @Test
    void deveRecusarBitsPorBandaEResolucaoNaoPositivosEAceitarVazios() {
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comNumeros(0, -1))).containsExactly(
                new CampoErro("regrasTiff.bitsPorBanda", ValidadorRegrasTiff.MSG_BITS_POR_BANDA),
                new CampoErro("regrasTiff.resolucaoDpi", ValidadorRegrasTiff.MSG_RESOLUCAO));
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comNumeros(null, null))).isEmpty();
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comNumeros(1, 1))).isEmpty();
    }

    @Test
    void deveRecusarElementoDaLegendaVazioOuComMaisDeCemCaracteres() {
        for (List<String> legenda : List.of(List.of("ID do plano", " "), List.of("x".repeat(101)),
                Arrays.asList("ID do plano", (String) null))) {
            assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comLegenda(legenda))).containsExactly(
                    new CampoErro("regrasTiff.elementosLegenda", ValidadorRegrasTiff.MSG_ELEMENTOS_LEGENDA));
        }
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comLegenda(List.of("x".repeat(100))))).isEmpty();
        assertThat(validador.erros(TipoElemento.PECA_GRAFICA, comLegenda(List.of()))).isEmpty();
    }

    @Test
    void deveJuntarTodosOsErrosNumSoPedidoInvalido() {
        RegrasTiff regras = new RegrasTiff(List.of("3763"), List.of(), true, null, null, 0, List.of());

        assertThatThrownBy(() -> validador.validar(TipoElemento.PECA_GRAFICA, regras))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo(ValidacaoException.CODIGO);
                    assertThat(erro.getCampos()).extracting(CampoErro::path).containsExactly(
                            "regrasTiff.sistemasReferencia", "regrasTiff.compressoesAceites", "regrasTiff.resolucaoDpi");
                });
    }

    private static RegrasTiff comSistemas(List<String> sistemas) {
        RegrasTiff base = RegrasTiff.inicialGeral();
        return new RegrasTiff(sistemas, base.compressoesAceites(), true, null, null, null, base.elementosLegenda());
    }

    private static RegrasTiff comCompressoes(List<CompressaoTiff> compressoes) {
        RegrasTiff base = RegrasTiff.inicialGeral();
        return new RegrasTiff(base.sistemasReferencia(), compressoes, true, null, null, null, base.elementosLegenda());
    }

    private static RegrasTiff comNumeros(Integer bitsPorBanda, Integer resolucaoDpi) {
        RegrasTiff base = RegrasTiff.inicialSgifr();
        return new RegrasTiff(base.sistemasReferencia(), base.compressoesAceites(), false, ModeloCorTiff.RGB,
                bitsPorBanda, resolucaoDpi, base.elementosLegenda());
    }

    private static RegrasTiff comLegenda(List<String> legenda) {
        RegrasTiff base = RegrasTiff.inicialGeral();
        return new RegrasTiff(base.sistemasReferencia(), base.compressoesAceites(), true, null, null, null, legenda);
    }
}
