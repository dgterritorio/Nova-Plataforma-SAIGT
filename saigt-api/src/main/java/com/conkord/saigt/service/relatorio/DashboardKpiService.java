package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PreferenciaKpiResponse;
import com.conkord.saigt.dto.relatorio.ValorKpiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Valores dos KPIs visíveis do chamador, pela ordem das preferências, calculados a pedido sobre os dados
 * confirmados. Só `MASTER` (verificado ao ler as preferências). Um KPI sem calculador não entra na resposta.
 */
@Service
public class DashboardKpiService {

    private static final Logger log = LoggerFactory.getLogger(DashboardKpiService.class);

    private final DashboardPreferenciaService dashboardPreferenciaService;
    private final Map<KpiCodigo, CalculadorKpi> calculadores;
    private final Clock relogio;

    public DashboardKpiService(DashboardPreferenciaService dashboardPreferenciaService,
                               List<CalculadorKpi> calculadores, Clock relogio) {
        this.dashboardPreferenciaService = dashboardPreferenciaService;
        this.calculadores = new EnumMap<>(KpiCodigo.class);
        for (CalculadorKpi calculador : calculadores) {
            if (this.calculadores.putIfAbsent(calculador.codigo(), calculador) != null) {
                throw new IllegalStateException("Dois calculadores para o KPI " + calculador.codigo() + ".");
            }
        }
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<ValorKpiResponse> calcular() {
        List<PreferenciaKpiResponse> visiveis = dashboardPreferenciaService.obter().stream()
                .filter(PreferenciaKpiResponse::visivel)
                .sorted(Comparator.comparingInt(PreferenciaKpiResponse::ordem))
                .toList();
        Instant calculadoEm = relogio.instant();
        List<ValorKpiResponse> valores = new ArrayList<>(visiveis.size());
        for (PreferenciaKpiResponse preferencia : visiveis) {
            Optional<KpiCodigo> kpi = KpiCodigo.deCodigo(preferencia.kpiCodigo());
            CalculadorKpi calculador = kpi.map(calculadores::get).orElse(null);
            if (calculador == null) {
                continue;
            }
            long inicio = System.nanoTime();
            ValorKpi valor = calculador.calcular();
            log.debug("KPI calculado | codigo={} ms={}", kpi.get(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
            valores.add(new ValorKpiResponse(kpi.get().name(), kpi.get().getDesignacao(), valor.valor(), valor.serie(),
                    calculadoEm));
        }
        return valores;
    }
}
