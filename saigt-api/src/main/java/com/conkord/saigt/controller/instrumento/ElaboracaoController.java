package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.CriarElaboracaoRequest;
import com.conkord.saigt.dto.instrumento.ElaboracaoCriadaResponse;
import com.conkord.saigt.dto.instrumento.ElaboracaoNovaResponse;
import com.conkord.saigt.dto.instrumento.ProcessoResponse;
import com.conkord.saigt.service.instrumento.ElaboracaoService;
import com.conkord.saigt.service.instrumento.ElaboracaoService.ElaboracaoCriada;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ElaboracaoController {

    private final ElaboracaoService elaboracaoService;

    public ElaboracaoController(ElaboracaoService elaboracaoService) {
        this.elaboracaoService = elaboracaoService;
    }

    @GetMapping("/api/v1/tipos-instrumento/{id}/elaboracao")
    public ElaboracaoNovaResponse elaboracaoNova(@PathVariable Long id) {
        return elaboracaoService.elaboracaoNova(id);
    }

    @PostMapping("/api/v1/instrumentos/elaboracao")
    public ResponseEntity<ElaboracaoCriadaResponse> criar(@Valid @RequestBody CriarElaboracaoRequest request) {
        ElaboracaoCriada criada = elaboracaoService.criar(request);
        return ResponseEntity.status(201).body(new ElaboracaoCriadaResponse(criada.instrumento().getId(),
                criada.dinamica().getId(), ProcessoResponse.de(criada.processo())));
    }
}
