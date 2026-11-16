package com.conkord.saigt.controller.referencia;

import com.conkord.saigt.dto.referencia.ReferenciaSnitResponse;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Listas de referência do SNIT para os ecrãs, lidas da réplica. Qualquer utilizador autenticado. */
@RestController
public class ReferenciaSnitController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1));

    private final ReferenciaSnitConsultaService referenciaSnitConsultaService;

    public ReferenciaSnitController(ReferenciaSnitConsultaService referenciaSnitConsultaService) {
        this.referenciaSnitConsultaService = referenciaSnitConsultaService;
    }

    @GetMapping("/api/v1/referencias-snit/{lista}")
    public ResponseEntity<List<? extends ReferenciaSnitResponse>> listar(
            @PathVariable String lista, @RequestParam(required = false) String nut2) {
        return ResponseEntity.ok().cacheControl(CACHE).body(referenciaSnitConsultaService.listar(lista, nut2));
    }
}
