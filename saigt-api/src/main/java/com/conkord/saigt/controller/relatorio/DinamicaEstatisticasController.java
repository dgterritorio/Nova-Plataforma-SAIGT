package com.conkord.saigt.controller.relatorio;

import com.conkord.saigt.dto.relatorio.EstatisticasDinamicaResponse;
import com.conkord.saigt.service.relatorio.EstatisticasDinamicaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DinamicaEstatisticasController {

    private final EstatisticasDinamicaService estatisticasDinamicaService;

    public DinamicaEstatisticasController(EstatisticasDinamicaService estatisticasDinamicaService) {
        this.estatisticasDinamicaService = estatisticasDinamicaService;
    }

    @GetMapping("/api/v1/dinamicas/{id}/estatisticas")
    public EstatisticasDinamicaResponse obter(@PathVariable UUID id) {
        return estatisticasDinamicaService.obter(id);
    }
}
