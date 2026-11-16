package com.conkord.saigt.controller.relatorio;

import com.conkord.saigt.dto.relatorio.PreferenciaKpiRequest;
import com.conkord.saigt.dto.relatorio.PreferenciaKpiResponse;
import com.conkord.saigt.dto.relatorio.ValorKpiResponse;
import com.conkord.saigt.service.relatorio.DashboardKpiService;
import com.conkord.saigt.service.relatorio.DashboardPreferenciaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardPreferenciaService dashboardPreferenciaService;
    private final DashboardKpiService dashboardKpiService;

    public DashboardController(DashboardPreferenciaService dashboardPreferenciaService,
                               DashboardKpiService dashboardKpiService) {
        this.dashboardPreferenciaService = dashboardPreferenciaService;
        this.dashboardKpiService = dashboardKpiService;
    }

    @GetMapping("/kpis")
    public List<ValorKpiResponse> obterKpis() {
        return dashboardKpiService.calcular();
    }

    @GetMapping("/preferencias")
    public List<PreferenciaKpiResponse> obterPreferencias() {
        return dashboardPreferenciaService.obter();
    }

    /**
     * Sem `@Valid`: o serviço valida o formato e as regras de uma só vez, para devolver todas as falhas num só
     * envelope `VALIDACAO` com os caminhos `[i].campo`. O utilizador vem sempre da sessão, nunca do pedido.
     */
    @PutMapping("/preferencias")
    public List<PreferenciaKpiResponse> substituirPreferencias(@RequestBody List<PreferenciaKpiRequest> pedido) {
        return dashboardPreferenciaService.substituir(pedido);
    }
}
