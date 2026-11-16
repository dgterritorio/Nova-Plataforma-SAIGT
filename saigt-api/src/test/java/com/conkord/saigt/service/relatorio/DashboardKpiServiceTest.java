package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.dto.relatorio.PreferenciaKpiResponse;
import com.conkord.saigt.dto.relatorio.ValorKpiResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardKpiServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");

    @Mock
    private DashboardPreferenciaService dashboardPreferenciaService;

    private CalculadorKpi porEstado;
    private CalculadorKpi graficoMes;
    private CalculadorKpi porTipologia;
    private DashboardKpiService service;

    @BeforeEach
    void preparar() {
        porEstado = calculador(KpiCodigo.PROCESSOS_POR_ESTADO, new ValorKpi(5L, List.of(new PontoSerieResponse("EM_SUBMISSAO", 5))));
        graficoMes = calculador(KpiCodigo.GRAFICO_MES, new ValorKpi(null, List.of(new PontoSerieResponse("2026-03", 2))));
        porTipologia = calculador(KpiCodigo.TOTAL_POR_TIPOLOGIA, new ValorKpi(3L, List.of(new PontoSerieResponse("PDM", 3))));
        service = new DashboardKpiService(dashboardPreferenciaService, List.of(porEstado, graficoMes, porTipologia),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static CalculadorKpi calculador(KpiCodigo codigo, ValorKpi valor) {
        CalculadorKpi calculador = mock(CalculadorKpi.class);
        when(calculador.codigo()).thenReturn(codigo);
        when(calculador.calcular()).thenReturn(valor);
        return calculador;
    }

    private static PreferenciaKpiResponse preferencia(KpiCodigo kpi, boolean visivel, int ordem) {
        return new PreferenciaKpiResponse(kpi.name(), kpi.getDesignacao(), visivel, ordem);
    }

    @Test
    void devolvePelaOrdemDasPreferenciasComOMesmoCalculadoEm() {
        when(dashboardPreferenciaService.obter()).thenReturn(List.of(
                preferencia(KpiCodigo.GRAFICO_MES, true, 0),
                preferencia(KpiCodigo.TOTAL_POR_TIPOLOGIA, true, 1),
                preferencia(KpiCodigo.PROCESSOS_POR_ESTADO, true, 2)));

        List<ValorKpiResponse> valores = service.calcular();

        assertThat(valores).extracting(ValorKpiResponse::codigo, ValorKpiResponse::designacao, ValorKpiResponse::valor,
                        ValorKpiResponse::calculadoEm)
                .containsExactly(
                        tuple("GRAFICO_MES", KpiCodigo.GRAFICO_MES.getDesignacao(), null, AGORA),
                        tuple("TOTAL_POR_TIPOLOGIA", KpiCodigo.TOTAL_POR_TIPOLOGIA.getDesignacao(), 3L, AGORA),
                        tuple("PROCESSOS_POR_ESTADO", KpiCodigo.PROCESSOS_POR_ESTADO.getDesignacao(), 5L, AGORA));
        assertThat(valores.get(0).serie()).containsExactly(new PontoSerieResponse("2026-03", 2));
    }

    @Test
    void ordenaPelaOrdemMesmoQueAListaVenhaDesordenada() {
        when(dashboardPreferenciaService.obter()).thenReturn(List.of(
                preferencia(KpiCodigo.PROCESSOS_POR_ESTADO, true, 7),
                preferencia(KpiCodigo.GRAFICO_MES, true, 2)));

        assertThat(service.calcular()).extracting(ValorKpiResponse::codigo)
                .containsExactly("GRAFICO_MES", "PROCESSOS_POR_ESTADO");
    }

    @Test
    void osOcultosNaoVemNemSaoCalculados() {
        when(dashboardPreferenciaService.obter()).thenReturn(List.of(
                preferencia(KpiCodigo.TOTAL_POR_TIPOLOGIA, true, 0),
                preferencia(KpiCodigo.GRAFICO_MES, false, 1),
                preferencia(KpiCodigo.PROCESSOS_POR_ESTADO, true, 2)));

        assertThat(service.calcular()).extracting(ValorKpiResponse::codigo)
                .containsExactly("TOTAL_POR_TIPOLOGIA", "PROCESSOS_POR_ESTADO");
        verify(graficoMes, never()).calcular();
    }

    @Test
    void osKpisSemCalculadorNaoVemESemErro() {
        when(dashboardPreferenciaService.obter()).thenReturn(List.of(
                preferencia(KpiCodigo.TAXA_REJEICAO, true, 0),
                preferencia(KpiCodigo.A_CADUCAR, true, 1),
                preferencia(KpiCodigo.PROCESSOS_POR_ESTADO, true, 2),
                preferencia(KpiCodigo.ATIVIDADE_RECENTE, true, 3)));

        assertThat(service.calcular()).extracting(ValorKpiResponse::codigo).containsExactly("PROCESSOS_POR_ESTADO");
    }

    @Test
    void semKpisVisiveisComCalculadorDevolveListaVazia() {
        when(dashboardPreferenciaService.obter()).thenReturn(List.of(
                preferencia(KpiCodigo.TAXA_REJEICAO, true, 0),
                preferencia(KpiCodigo.PROCESSOS_POR_ESTADO, false, 1)));

        assertThat(service.calcular()).isEmpty();
    }

    @Test
    void semPerfilMasterNaoCalculaNada() {
        when(dashboardPreferenciaService.obter()).thenThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação."));

        assertThatThrownBy(service::calcular).isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(porEstado, never()).calcular();
        verify(graficoMes, never()).calcular();
        verify(porTipologia, never()).calcular();
    }

    @Test
    void doisCalculadoresParaOMesmoKpiFalhamNoArranque() {
        CalculadorKpi outro = calculador(KpiCodigo.GRAFICO_MES, new ValorKpi(null, List.of()));
        Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
        List<CalculadorKpi> calculadores = List.of(graficoMes, outro);

        assertThatThrownBy(() -> new DashboardKpiService(dashboardPreferenciaService, calculadores, relogio))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GRAFICO_MES");
    }
}
