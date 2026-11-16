package com.conkord.saigt.service.relatorio;

/**
 * Cálculo de um KPI do dashboard, com uma só consulta agregada sobre todos os processos (universo do `MASTER`).
 * Um pré-cálculo pode substituir a implementação sem mexer no endpoint.
 */
public interface CalculadorKpi {

    KpiCodigo codigo();

    ValorKpi calcular();
}
