package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasNomeFicheiroTest {

    @ParameterizedTest
    @ValueSource(strings = {"regulamento2026.pdf", "relatorio_2026.pdf", "1234567890123456.pdf", "semextensao", "A_b_9.PDF"})
    void deveAceitarNomesDentroDasRegras(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome)).isEmpty();
    }

    @Test
    void deveContarAExtensaoNosVinteCaracteres() {
        assertThat("12345678901234567.pdf".length()).isEqualTo(21);
        assertThat(RegrasNomeFicheiro.primeiroErro("1234567890123456.pdf")).isEmpty();
        assertThat(RegrasNomeFicheiro.primeiroErro("12345678901234567.pdf"))
                .contains(RegrasNomeFicheiro.MSG_CARACTERES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plano final.pdf", "plano-final.pdf", "alteração.pdf", "alteracao\u0301.pdf", "façade.pdf",
            ".pdf", "C:\\pasta\\a.pdf", "plano@.pdf", "plano.pd f", "semextensao123456789012"})
    void deveRecusarPelaRegraDosCaracteres(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome)).contains(RegrasNomeFicheiro.MSG_CARACTERES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plano.v2.pdf", "a..pdf", "plano.", "a.b.c.d"})
    void deveRecusarPelaRegraDoPonto(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome)).contains(RegrasNomeFicheiro.MSG_PONTO);
    }

    @Test
    void deveAplicarARegraDoPontoAntesDaDosCaracteres() {
        assertThat(RegrasNomeFicheiro.primeiroErro("plano final.v2.pdf")).contains(RegrasNomeFicheiro.MSG_PONTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Plantas de ordenamento finais.zip", "plano-final.ZIP", "a.b.c.Zip"})
    void naoDeveValidarONomeDeUmZip(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"regulamento2026.pdf", "plano final.pdf", "alteração.pdf", "12345678901234567.pdf",
            "plano.v2.pdf", "plano final.v2.pdf", "Plantas finais.zip", "PSA-AML-RVF.tif", "PSA_AML_RVF.tif"})
    void aRegraGeralExplicitaDeveDarOMesmoResultadoQueAOmissao(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome, RegraNomenclatura.GERAL))
                .isEqualTo(RegrasNomeFicheiro.primeiroErro(nome));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PSA_AML_RVF.tif", "PRA_LVT_RPFGC.TIF", "PSA_AML_RVF", "P1_A2_3.pdf", "PSA_AML_RVFGC123.tif"})
    void aRegraSgifrDeveAceitarONomeNoFormatoNomeRegiaoTematica(String nome) {
        assertThat(nome.length()).isLessThanOrEqualTo(20);
        assertThat(RegrasNomeFicheiro.primeiroErro(nome, RegraNomenclatura.SGIFR)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PSA-AML-RVF.tif", "PSAAMLRVF.tif", "PSA__RVF.tif", "Região_AML_RVF.tif", "PSA_AML_RVFGC1234.tif",
            "_AML_RVF.tif", "PSA_AML_.tif", "PSA_AML.tif", "PSA_AML_RVF_X.tif", "PSA AML_RVF.tif", "PSA-AML.tif"})
    void aRegraSgifrDeveRecusarComAMensagemDoFormato(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome, RegraNomenclatura.SGIFR)).contains(RegrasNomeFicheiro.MSG_SGIFR);
    }

    @Test
    void aRegraSgifrDeveContarAExtensaoNosVinteCaracteres() {
        assertThat("PSA_AML_RVFGC1234.tif").hasSize(21);
        assertThat(RegrasNomeFicheiro.primeiroErro("PSA_AML_RVFGC123.tif", RegraNomenclatura.SGIFR)).isEmpty();
        assertThat(RegrasNomeFicheiro.primeiroErro("PSA_AML_RVFGC1234.tif", RegraNomenclatura.SGIFR))
                .contains(RegrasNomeFicheiro.MSG_SGIFR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PSA.AML_RVF.tif", "PSA_AML_RVF.", "PSA_AML.RVF.tif", "Regiao.AML.tif"})
    void aRegraSgifrDeveAplicarPrimeiroARegraDoPonto(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome, RegraNomenclatura.SGIFR)).contains(RegrasNomeFicheiro.MSG_PONTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Plantas do PSA.zip", "PSA-AML-RVF.zip", "a.b.c.ZIP"})
    void aRegraSgifrNaoDeveValidarONomeDeUmZip(String nome) {
        assertThat(RegrasNomeFicheiro.primeiroErro(nome, RegraNomenclatura.SGIFR)).isEmpty();
    }

    @Test
    void deveValidarONomeDeUmZipQuandoEUmaEntradaDeOutroZip() {
        assertThat(RegrasNomeFicheiro.erroNome("plantas finais.zip")).contains(RegrasNomeFicheiro.MSG_CARACTERES);
        assertThat(RegrasNomeFicheiro.erroNome("anexo.v2.zip")).contains(RegrasNomeFicheiro.MSG_PONTO);
        assertThat(RegrasNomeFicheiro.erroNome("anexos.zip")).isEmpty();
    }
}
