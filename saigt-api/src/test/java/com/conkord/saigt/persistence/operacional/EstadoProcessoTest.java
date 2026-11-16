package com.conkord.saigt.persistence.operacional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoProcessoTest {

    @ParameterizedTest
    @CsvSource({
            "EM_SUBMISSAO, ENVIADO_DGT",
            "EM_SUBMISSAO, ANULADO",
            "ENVIADO_DGT, EM_SUBMISSAO",
            "ENVIADO_DGT, EM_VALIDACAO_DGT",
            "ENVIADO_DGT, ANULADO",
            "EM_VALIDACAO_DGT, VALIDADO_DGT",
            "EM_VALIDACAO_DGT, REJEITADO_DGT",
            "EM_VALIDACAO_DGT, ANULADO",
            "REJEITADO_DGT, EM_SUBMISSAO",
            "VALIDADO_DGT, ENVIADO_INCM",
            "VALIDADO_DGT, ANULADO",
            "ENVIADO_INCM, EM_VALIDACAO_INCM",
            "ENVIADO_INCM, ANULADO_INCM",
            "EM_VALIDACAO_INCM, PUBLICADO",
            "EM_VALIDACAO_INCM, ANULADO_INCM",
            "ANULADO_INCM, EM_SUBMISSAO"
    })
    void transicaoDoModeloDeveSerPermitida(EstadoProcesso origem, EstadoProcesso destino) {
        assertThat(origem.podeTransitarPara(destino)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "EM_SUBMISSAO, EM_VALIDACAO_DGT",
            "ENVIADO_DGT, VALIDADO_DGT",
            "ENVIADO_INCM, ANULADO",
            "EM_VALIDACAO_INCM, ANULADO",
            "REJEITADO_DGT, ENVIADO_DGT",
            "REJEITADO_DGT, EM_VALIDACAO_DGT",
            "ANULADO_INCM, ENVIADO_DGT",
            "ANULADO, EM_SUBMISSAO",
            "VALIDADO_DGT, PUBLICADO",
            "ENVIADO_INCM, PUBLICADO",
            "EM_VALIDACAO_DGT, ENVIADO_INCM"
    })
    void transicaoForaDoModeloDeveSerRecusada(EstadoProcesso origem, EstadoProcesso destino) {
        assertThat(origem.podeTransitarPara(destino)).isFalse();
    }

    @Test
    void totalDeTransicoesDeveCoincidirComATabela() {
        long total = Arrays.stream(EstadoProcesso.values())
                .mapToLong(origem -> Arrays.stream(EstadoProcesso.values()).filter(origem::podeTransitarPara).count())
                .sum();

        assertThat(total).isEqualTo(16);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"PUBLICADO", "ANULADO"})
    void terminalNaoDeveTerSaida(EstadoProcesso terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(EstadoProcesso.values()).noneMatch(terminal::podeTransitarPara);
    }

    @Test
    void terminaisDevemSerSoPublicadoEAnulado() {
        assertThat(EstadoProcesso.TERMINAIS)
                .containsExactlyInAnyOrder(EstadoProcesso.PUBLICADO, EstadoProcesso.ANULADO);
        assertThat(EstadoProcesso.REJEITADO_DGT.isTerminal()).isFalse();
        assertThat(EstadoProcesso.ANULADO_INCM.isTerminal()).isFalse();
    }

    @Test
    void novaTentativaSoDepoisDeAnulado() {
        assertThat(EstadoProcesso.PERMITEM_NOVA_TENTATIVA)
                .containsExactly(EstadoProcesso.ANULADO)
                .isSubsetOf(EstadoProcesso.TERMINAIS);
    }

    @Test
    void editaveisDevemSerAsOrigensDeSubmissao() {
        assertThat(EstadoProcesso.EDITAVEIS)
                .containsExactly(EstadoProcesso.EM_SUBMISSAO)
                .isEqualTo(Arrays.stream(EstadoProcesso.values())
                        .filter(origem -> origem.podeTransitarPara(EstadoProcesso.ENVIADO_DGT))
                        .collect(Collectors.toSet()));
    }

    @Test
    void anulaveisDevemSerAsOrigensDeAnuladoAntesDaIncm() {
        assertThat(EstadoProcesso.ANULAVEIS)
                .containsExactlyInAnyOrder(EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT,
                        EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.VALIDADO_DGT)
                .isEqualTo(Arrays.stream(EstadoProcesso.values())
                        .filter(origem -> origem.podeTransitarPara(EstadoProcesso.ANULADO))
                        .collect(Collectors.toSet()));
    }

    @Test
    void enumNaoDeveTerNomesAntigos() {
        assertThat(Arrays.stream(EstadoProcesso.values()).map(Enum::name))
                .doesNotContain("SUBMETIDO", "REJEITADO_INCM")
                .contains("ENVIADO_DGT", "ANULADO_INCM");
    }
}
