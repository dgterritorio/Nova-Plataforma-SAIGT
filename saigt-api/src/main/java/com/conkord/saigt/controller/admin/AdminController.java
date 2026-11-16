package com.conkord.saigt.controller.admin;

import com.conkord.saigt.dto.admin.EstatisticasAdminResponse;
import com.conkord.saigt.service.admin.AdminEstatisticasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Só MASTER. */
@RestController
public class AdminController {

    private final AdminEstatisticasService adminEstatisticasService;

    public AdminController(AdminEstatisticasService adminEstatisticasService) {
        this.adminEstatisticasService = adminEstatisticasService;
    }

    @GetMapping("/api/v1/admin/estatisticas")
    public EstatisticasAdminResponse estatisticas() {
        return adminEstatisticasService.obter();
    }
}
