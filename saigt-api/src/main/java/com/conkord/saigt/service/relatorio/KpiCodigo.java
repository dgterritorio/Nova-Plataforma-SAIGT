package com.conkord.saigt.service.relatorio;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo de KPIs do dashboard (BL-REP-010), pela ordem da EF v1.5. Retirar um valor não pede migração: as
 * preferências gravadas com esse código passam a ser ignoradas.
 */
public enum KpiCodigo {
    TOTAL_POR_TIPOLOGIA("Total de processos por tipologia, com os atos prévios e os publicados"),
    PROCESSOS_POR_ESTADO("Processos nos principais estados, por IGT"),
    A_CADUCAR("Processos a caducar"),
    A_INATIVAR("Processos a inativar"),
    GRAFICO_ESTADO("Gráfico de processos por estado"),
    GRAFICO_MES("Gráfico de processos por mês"),
    TEMPO_MEDIO_INSTRUCAO("Tempos médios de instrução"),
    PADROES_TAMANHO_FICHEIROS("Padrões de tamanho de ficheiros"),
    TAXA_REJEICAO("Taxa de rejeição por entidade, tipologia, dinâmica e motivo"),
    ATIVIDADE_RECENTE("Atividade recente");

    private final String designacao;

    KpiCodigo(String designacao) {
        this.designacao = designacao;
    }

    public String getDesignacao() {
        return designacao;
    }

    public static Optional<KpiCodigo> deCodigo(String codigo) {
        return Arrays.stream(values()).filter(kpi -> kpi.name().equals(codigo)).findFirst();
    }
}
