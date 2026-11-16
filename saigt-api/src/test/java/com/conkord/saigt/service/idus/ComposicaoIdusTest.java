package com.conkord.saigt.service.idus;

import com.conkord.saigt.exception.IdusInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComposicaoIdusTest {

    private static final SegmentosIdus CRIACAO = new SegmentosIdus("01", "1199", "01", "900001", "02", "90001", "01",
            "031", "00", null);

    @Test
    void deveComporAChaveDaCriacaoSemPubIncm() {
        assertThat(ComposicaoIdus.compor(CRIACAO)).isEqualTo("01.1199.01.900001.02.90001.01.031.00");
    }

    @Test
    void deveComporAChaveDepoisDaPublicacaoComPubIncm() {
        SegmentosIdus publicado = new SegmentosIdus("01", "1199", "01", "900001", "02", "90001", "01", "031", "01",
                "20191210");

        assertThat(ComposicaoIdus.compor(publicado)).isEqualTo("01.1199.01.900001.02.90001.01.031.01.20191210");
    }

    @Test
    void deveManterAChaveMaisLongaDentroDosCinquentaCaracteresDaColuna() {
        SegmentosIdus maximo = new SegmentosIdus("99", "9999", "99", "999999", "99", "99999", "99", "999", "99",
                "99999999");

        assertThat(ComposicaoIdus.compor(maximo)).hasSize(45);
    }

    @ParameterizedTest(name = "{0} com ''{1}''")
    @CsvSource({
            "Sistema,011",
            "Sistema,1",
            "DICO,11999",
            "DICO,119",
            "IGT,001",
            "ID_IGT,9000001",
            "ID_IGT,90001",
            "Dinâmica,2",
            "Ordem_Dinâmica,910000",
            "Ordem_Dinâmica,9001",
            "Tentativa,100",
            "Procedimento,1060",
            "Procedimento,31",
            "Submissão,0",
    })
    void deveRejeitarUmSegmentoComAlgarismosAMaisOuAMenosIndicandoOSegmento(String segmento, String valor) {
        assertThatThrownBy(() -> ComposicaoIdus.compor(comSegmento(segmento, valor)))
                .isInstanceOf(IdusInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "IDUS_INVALIDO")
                .hasMessageContaining("Segmento " + segmento + " do IDUS");
    }

    @ParameterizedTest(name = "{0} com ''{1}''")
    @CsvSource({
            "Sistema,0A",
            "DICO,11A9",
            "IGT,0x",
            "ID_IGT,9000O1",
            "Dinâmica,-2",
            "Ordem_Dinâmica,9000x",
            "Tentativa,٠١",
            "Procedimento,03.",
            "Submissão,O0",
    })
    void deveRejeitarUmSegmentoComLetrasOuSimbolosIndicandoOSegmento(String segmento, String valor) {
        assertThatThrownBy(() -> ComposicaoIdus.compor(comSegmento(segmento, valor)))
                .isInstanceOf(IdusInvalidoException.class)
                .hasMessageContaining("Segmento " + segmento + " do IDUS");
    }

    @Test
    void deveRejeitarUmSegmentoEmFalta() {
        SegmentosIdus semIgt = new SegmentosIdus("01", "1199", null, "900001", "02", "90001", "01", "031", "00", null);

        assertThatThrownBy(() -> ComposicaoIdus.compor(semIgt))
                .isInstanceOf(IdusInvalidoException.class)
                .hasMessageContaining("Segmento IGT do IDUS");
    }

    @Test
    void deveRejeitarUmPubIncmQueNaoTemOitoAlgarismos() {
        SegmentosIdus pubIncmCurto = new SegmentosIdus("01", "1199", "01", "900001", "02", "90001", "01", "031", "01",
                "2019121");

        assertThatThrownBy(() -> ComposicaoIdus.compor(pubIncmCurto))
                .isInstanceOf(IdusInvalidoException.class)
                .hasMessageContaining("Segmento pubINCM do IDUS");
    }

    @Test
    void deveDecomporAsDuasChavesDeExemploEVoltarAComporAMesmaCadeia() {
        SegmentosIdus publicado = new SegmentosIdus("01", "1199", "01", "900001", "02", "90001", "01", "031", "01",
                "20191210");

        assertThat(ComposicaoIdus.decompor("01.1199.01.900001.02.90001.01.031.00")).isEqualTo(CRIACAO);
        assertThat(ComposicaoIdus.decompor("01.1199.01.900001.02.90001.01.031.01.20191210")).isEqualTo(publicado);
    }

    @Test
    void deveMudarSoOSegmentoPedidoSemMexerNosOutros() {
        assertThat(ComposicaoIdus.compor(CRIACAO.comSubmissao("01")))
                .isEqualTo("01.1199.01.900001.02.90001.01.031.01");
        assertThat(ComposicaoIdus.compor(CRIACAO.comSubmissao("01").comPubIncm("20191210")))
                .isEqualTo("01.1199.01.900001.02.90001.01.031.01.20191210");
    }

    @ParameterizedTest(name = "''{0}''")
    @CsvSource({
            "01.1199.01.900001.02.90001.01.031",
            "01.1199.01.900001.02.90001.01.031.00.20191210.99",
            "01.1199.01.900001.02.90001.01.031.0A",
            "01.1199.01.900001.02.90001.01.031.001",
            "01..01.900001.02.90001.01.031.00",
    })
    void deveRejeitarAoDecomporUmaCadeiaComSegmentosAMaisAMenosOuInvalidos(String idus) {
        assertThatThrownBy(() -> ComposicaoIdus.decompor(idus))
                .isInstanceOf(IdusInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "IDUS_INVALIDO");
    }

    @Test
    void deveRejeitarAoDecomporUmaCadeiaNula() {
        assertThatThrownBy(() -> ComposicaoIdus.decompor(null)).isInstanceOf(IdusInvalidoException.class);
    }

    private static SegmentosIdus comSegmento(String segmento, String valor) {
        SegmentosIdus s = CRIACAO;
        return new SegmentosIdus(
                "Sistema".equals(segmento) ? valor : s.sistema(),
                "DICO".equals(segmento) ? valor : s.dico(),
                "IGT".equals(segmento) ? valor : s.igt(),
                "ID_IGT".equals(segmento) ? valor : s.idIgt(),
                "Dinâmica".equals(segmento) ? valor : s.dinamica(),
                "Ordem_Dinâmica".equals(segmento) ? valor : s.ordemDinamica(),
                "Tentativa".equals(segmento) ? valor : s.tentativa(),
                "Procedimento".equals(segmento) ? valor : s.procedimento(),
                "Submissão".equals(segmento) ? valor : s.submissao(),
                s.pubIncm());
    }
}
