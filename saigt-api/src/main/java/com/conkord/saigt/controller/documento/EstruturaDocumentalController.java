package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.documento.EstruturaDocumentalResponse;
import com.conkord.saigt.service.documento.EstruturaDocumentalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class EstruturaDocumentalController {

    private final EstruturaDocumentalService estruturaDocumentalService;

    public EstruturaDocumentalController(EstruturaDocumentalService estruturaDocumentalService) {
        this.estruturaDocumentalService = estruturaDocumentalService;
    }

    @GetMapping("/api/v1/processos/{id}/estrutura-documental")
    public EstruturaDocumentalResponse obter(@PathVariable UUID id) {
        return estruturaDocumentalService.obter(id);
    }
}
