package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.documento.MetadadosMotuResponse;
import com.conkord.saigt.service.documento.MetadadosMotuService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Ficha MOTU do procedimento final: a Série e, mais tarde, os Conjuntos de Dados. */
@RestController
public class MetadadosMotuController {

    private final MetadadosMotuService metadadosMotuService;

    public MetadadosMotuController(MetadadosMotuService metadadosMotuService) {
        this.metadadosMotuService = metadadosMotuService;
    }

    @GetMapping("/api/v1/processos/{id}/metadados-motu")
    public MetadadosMotuResponse obter(@PathVariable UUID id) {
        return metadadosMotuService.obter(id);
    }

    /** O corpo depende do `tipo` do registo, por isso é lido e validado no serviço. */
    @PutMapping("/api/v1/processos/{id}/metadados-motu/{motuId}")
    public MetadadosMotuResponse.Registo gravar(@PathVariable UUID id, @PathVariable UUID motuId,
                                                @RequestBody JsonNode corpo) {
        return metadadosMotuService.gravar(id, motuId, corpo);
    }

    @PostMapping("/api/v1/processos/{id}/metadados-motu")
    @ResponseStatus(HttpStatus.CREATED)
    public MetadadosMotuResponse.Registo criarOpcional(@PathVariable UUID id,
                                                       @RequestBody(required = false) CriarFichaOpcionalRequest corpo) {
        return metadadosMotuService.criarOpcional(id, corpo != null && Boolean.TRUE.equals(corpo.reproduzirUltima()));
    }

    @DeleteMapping("/api/v1/processos/{id}/metadados-motu/{motuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerOpcional(@PathVariable UUID id, @PathVariable UUID motuId) {
        metadadosMotuService.removerOpcional(id, motuId);
    }

    public record CriarFichaOpcionalRequest(Boolean reproduzirUltima) {
    }
}
