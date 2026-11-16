package com.conkord.saigt.service.relatorio;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KpiCodigoTest {

    @Test
    void temOsKpisDaEfPelaOrdemDoCatalogo() {
        assertThat(Arrays.stream(KpiCodigo.values()).map(Enum::name)).containsExactly(
                "TOTAL_POR_TIPOLOGIA", "PROCESSOS_POR_ESTADO", "A_CADUCAR", "A_INATIVAR", "GRAFICO_ESTADO",
                "GRAFICO_MES", "TEMPO_MEDIO_INSTRUCAO", "PADROES_TAMANHO_FICHEIROS", "TAXA_REJEICAO",
                "ATIVIDADE_RECENTE");
    }

    @Test
    void cadaKpiTemDesignacaoECabeNaColunaDaTabela() {
        assertThat(KpiCodigo.values()).allSatisfy(kpi -> {
            assertThat(kpi.getDesignacao()).isNotBlank();
            assertThat(kpi.name()).hasSizeLessThanOrEqualTo(60);
        });
        assertThat(KpiCodigo.TAXA_REJEICAO.getDesignacao())
                .isEqualTo("Taxa de rejeição por entidade, tipologia, dinâmica e motivo");
    }

    @Test
    void deCodigoDevolveOKpiOuVazio() {
        assertThat(KpiCodigo.deCodigo("GRAFICO_MES")).contains(KpiCodigo.GRAFICO_MES);
        assertThat(KpiCodigo.deCodigo("XPTO")).isEmpty();
        assertThat(KpiCodigo.deCodigo("PROCESSOS_POR_IGT")).isEmpty();
        assertThat(KpiCodigo.deCodigo("grafico_mes")).isEmpty();
        assertThat(KpiCodigo.deCodigo(null)).isEmpty();
    }
}
