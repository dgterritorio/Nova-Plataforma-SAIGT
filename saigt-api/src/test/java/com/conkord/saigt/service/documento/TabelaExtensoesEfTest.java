package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TabelaExtensoesEfTest {

    @Test
    void deveDevolverAsExtensoesDePublicacaoDasPecasEscritas() {
        assertThat(TabelaExtensoesEf.extensoes(TipoElemento.PECA_ESCRITA, CategoriaDocumento.PUBLICACAO)).hasValueSatisfying(
                extensoes -> assertThat(extensoes).containsExactlyInAnyOrder(
                        "doc", "docx", "rtf", "odf", "odt", "xls", "xlsx", "csv", "ods", "pdf", "txt"));
    }

    @Test
    void deveDevolverAsExtensoesDeDepositoDasPecasEscritas() {
        assertThat(TabelaExtensoesEf.extensoes(TipoElemento.PECA_ESCRITA, CategoriaDocumento.DEPOSITO)).hasValueSatisfying(
                extensoes -> assertThat(extensoes).containsExactlyInAnyOrder("pdf", "jpg", "png"));
    }

    @ParameterizedTest
    @EnumSource(CategoriaDocumento.class)
    void deveAceitarSoZipNasPecasGraficasEmQualquerCaixa(CategoriaDocumento categoria) {
        assertThat(TabelaExtensoesEf.extensoes(TipoElemento.PECA_GRAFICA, categoria)).hasValueSatisfying(
                extensoes -> assertThat(extensoes).containsExactly("zip"));
    }

    @ParameterizedTest
    @EnumSource(CategoriaDocumento.class)
    void naoDeveTerLinhaParaOutros(CategoriaDocumento categoria) {
        assertThat(TabelaExtensoesEf.extensoes(TipoElemento.OUTROS, categoria)).isEmpty();
    }
}
