package com.conkord.saigt.controller.snit;

import com.conkord.saigt.dto.snit.ConsultaPlantasResponse;
import com.conkord.saigt.service.snit.ConsultaPlantasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada do SNIT. Só o cliente OAuth `snit` da #393 entra (token client credentials do `/oauth2/token`, regra de `/api/v1/integracoes/snit/**` em `SecurityConfig`). Os
 * parâmetros chegam como texto para um valor inválido dar o erro do contrato e não o da API interna.
 */
@RestController
public class ConsultaPlantasController {

    private final ConsultaPlantasService consultaPlantasService;

    public ConsultaPlantasController(ConsultaPlantasService consultaPlantasService) {
        this.consultaPlantasService = consultaPlantasService;
    }

    @GetMapping("/api/v1/integracoes/snit/plantas")
    public ConsultaPlantasResponse consultar(@RequestParam(required = false) String numeroDeposito,
                                             @RequestParam(required = false) String saigtId,
                                             @RequestParam(required = false) String emVigor) {
        return consultaPlantasService.consultar(numeroDeposito, saigtId, emVigor);
    }
}
