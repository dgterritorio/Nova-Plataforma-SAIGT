package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.TamanhoExcedidoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.service.documento.LimitesDocumento.Limite;
import com.conkord.saigt.service.documento.LimitesDocumento.Motivo;
import com.conkord.saigt.service.documento.LimitesDocumento.Ocupacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitesDocumentoTest {

    private static final long MB = 1024L * 1024;
    private static final long GB = MB * 1024;
    private static final UUID PROCESSO = UUID.randomUUID();
    private static final Ocupacao VAZIO = new Ocupacao(0, 0, 0);

    private static DocumentoConfig item(Long porFicheiro, Long total, Integer numero) {
        DocumentoConfig item = new DocumentoConfig(null, "ITEM", "Item", "Separador", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.NAO_EDITAVEL, "[\"pdf\"]", 1);
        item.setTamanhoMaximoFicheiro(porFicheiro);
        item.setTamanhoMaximoTotal(total);
        item.setNumeroMaximoFicheiros(numero);
        return item;
    }

    private static LimitesDocumento limites(Long porFicheiro, Long total, Integer numero, Long processo) {
        return new LimitesDocumento(PROCESSO, item(porFicheiro, total, numero), processo);
    }

    private static String erroDe(Throwable e) {
        return ((ValidacaoException) e).getCampos().get(0).erro();
    }

    @Test
    void semNenhumLimiteDeveDeixarLerSemTeto() {
        Limite limite = limites(null, null, null, null).limiteDaLeitura(new Ocupacao(10 * GB, 500, 50 * GB));

        assertThat(limite.bytes()).isEqualTo(Long.MAX_VALUE);
        assertThat(limite.motivo()).isNull();
    }

    @Test
    void deveEscolherOLimitePorFicheiroQuandoEOMenor() {
        Limite limite = limites(MB, 3 * MB, 10, 5 * GB).limiteDaLeitura(VAZIO);

        assertThat(limite).isEqualTo(new Limite(MB, Motivo.FICHEIRO, MB));
    }

    @Test
    void deveEscolherOQueSobraNoItemQuandoEOMenor() {
        Limite limite = limites(500 * MB, 3 * MB, 10, 5 * GB).limiteDaLeitura(new Ocupacao(2 * MB, 1, 2 * MB));

        assertThat(limite).isEqualTo(new Limite(MB, Motivo.TOTAL_ITEM, 3 * MB));
    }

    @Test
    void deveEscolherOQueSobraNoProcessoQuandoEOMenor() {
        Limite limite = limites(500 * MB, 2 * GB, 10, 5 * MB).limiteDaLeitura(new Ocupacao(0, 0, 4 * MB));

        assertThat(limite).isEqualTo(new Limite(MB, Motivo.TOTAL_PROCESSO, 5 * MB));
    }

    @Test
    void deveIgnorarOsLimitesVaziosDoItemEFicarSoComODoProcesso() {
        Limite limite = limites(null, null, null, 5 * GB).limiteDaLeitura(new Ocupacao(9 * GB, 99, GB));

        assertThat(limite).isEqualTo(new Limite(4 * GB, Motivo.TOTAL_PROCESSO, 5 * GB));
    }

    @Test
    void noBaldeOutrosSoDeveContarOProcesso() {
        Limite limite = new LimitesDocumento(PROCESSO, null, 5 * MB).limiteDaLeitura(new Ocupacao(0, 0, 4 * MB));

        assertThat(limite).isEqualTo(new Limite(MB, Motivo.TOTAL_PROCESSO, 5 * MB));
    }

    @Test
    void emEmpateDeveFicarOLimitePorFicheiro() {
        Limite limite = limites(MB, 2 * MB, null, null).limiteDaLeitura(new Ocupacao(MB, 1, MB));

        assertThat(limite.motivo()).isEqualTo(Motivo.FICHEIRO);
    }

    @Test
    void deveRecusarAntesDeLerQuandoOItemJaTemONumeroMaximoDeFicheiros() {
        assertThatThrownBy(() -> limites(null, null, 2, null).limiteDaLeitura(new Ocupacao(0, 2, 0)))
                .isInstanceOf(TamanhoExcedidoException.class)
                .satisfies(e -> assertThat(erroDe(e)).isEqualTo("O item já tem o número máximo de ficheiros (2)."));
    }

    @Test
    void deveDeixarLerComUmFicheiroAbaixoDoNumeroMaximo() {
        assertThatCode(() -> limites(null, null, 2, null).limiteDaLeitura(new Ocupacao(0, 1, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void deveRecusarAntesDeLerQuandoOTotalDoItemJaEstaCheio() {
        assertThatThrownBy(() -> limites(null, 3 * MB, null, null).limiteDaLeitura(new Ocupacao(3 * MB, 1, 3 * MB)))
                .isInstanceOf(TamanhoExcedidoException.class)
                .satisfies(e -> assertThat(erroDe(e)).isEqualTo("Limite total do item: 3 MB."));
    }

    @Test
    void deveRecusarAntesDeLerQuandoOProcessoJaEstaCheio() {
        assertThatThrownBy(() -> limites(null, null, null, 5 * GB).limiteDaLeitura(new Ocupacao(0, 0, 5 * GB)))
                .isInstanceOf(TamanhoExcedidoException.class)
                .satisfies(e -> assertThat(erroDe(e)).isEqualTo("Limite total do processo: 5 GB."));
    }

    @Test
    void comOsValoresPorOmissaoDeveAceitarUmFicheiroDe100Mb() {
        LimitesDocumento limites = limites(500 * MB, 2 * GB, 10, 5 * GB);

        assertThat(limites.limiteDaLeitura(VAZIO).bytes()).isGreaterThan(100 * MB);
        assertThatCode(() -> limites.verificarAposGravar(VAZIO, 100 * MB)).doesNotThrowAnyException();
    }

    @Test
    void depoisDeGravarDeveRecusarOQueOutroUploadJaOcupouNoProcesso() {
        LimitesDocumento limites = limites(null, null, null, 5 * MB);

        assertThatThrownBy(() -> limites.verificarAposGravar(new Ocupacao(0, 0, 3 * MB), 3 * MB))
                .isInstanceOf(TamanhoExcedidoException.class)
                .satisfies(e -> assertThat(erroDe(e)).isEqualTo("Limite total do processo: 5 MB."));
        assertThatCode(() -> limites.verificarAposGravar(new Ocupacao(0, 0, 2 * MB), 3 * MB))
                .doesNotThrowAnyException();
    }

    @Test
    void depoisDeGravarDeveRecusarOQueOutroUploadJaOcupouNoItem() {
        LimitesDocumento limites = limites(null, 3 * MB, 2, null);

        assertThatThrownBy(() -> limites.verificarAposGravar(new Ocupacao(2 * MB, 1, 2 * MB), 2 * MB))
                .satisfies(e -> assertThat(erroDe(e)).isEqualTo("Limite total do item: 3 MB."));
        assertThatThrownBy(() -> limites.verificarAposGravar(new Ocupacao(MB, 2, MB), MB))
                .satisfies(e -> assertThat(erroDe(e)).isEqualTo("O item já tem o número máximo de ficheiros (2)."));
    }

    @Test
    void deveDevolverACodigoEAMensagemDoBlDoc017() {
        TamanhoExcedidoException erro = limites(MB, null, null, null).excedido(new Limite(MB, Motivo.FICHEIRO, MB));

        assertThat(erro.getCodigo()).isEqualTo("DOC_TAMANHO_EXCEDIDO");
        assertThat(erro.getMessage()).isEqualTo("O ficheiro excede o tamanho máximo permitido.");
        assertThat(erro.getCampos()).singleElement()
                .isEqualTo(new ValidacaoException.CampoErro("ficheiro", "Limite por ficheiro: 1 MB."));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "524288000  | 500 MB",
            "2147483648 | 2 GB",
            "5368709120 | 5 GB",
            "1048576    | 1 MB",
            "1610612736 | 1,5 GB",
            "1073741823 | 1023,99 MB",
            "2048       | 2 KB",
            "100        | 100 bytes"})
    void deveFormatarOLimiteNaMaiorUnidadeSemArredondarParaCima(long bytes, String esperado) {
        assertThat(LimitesDocumento.formatar(bytes)).isEqualTo(esperado);
    }
}
