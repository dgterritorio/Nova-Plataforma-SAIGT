package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.GravarValidacaoItensRequest;
import com.conkord.saigt.dto.instrumento.ValidacaoItensResponse;
import com.conkord.saigt.service.instrumento.ProcessoValidacaoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Autorização e visibilidade decididas em `ProcessoValidacaoService`, nunca aqui. */
@RestController
public class ProcessoValidacaoController {

    private final ProcessoValidacaoService processoValidacaoService;

    public ProcessoValidacaoController(ProcessoValidacaoService processoValidacaoService) {
        this.processoValidacaoService = processoValidacaoService;
    }

    @PutMapping("/api/v1/processos/{id}/validacao/itens")
    public ValidacaoItensResponse gravar(@PathVariable UUID id, @Valid @RequestBody GravarValidacaoItensRequest request) {
        return processoValidacaoService.gravar(id, request);
    }
}
