package com.conkord.saigt.controller.utilizador;

import com.conkord.saigt.dto.utilizador.AtualizarPerfilRequest;
import com.conkord.saigt.dto.utilizador.UtilizadorMeResponse;
import com.conkord.saigt.service.utilizador.UtilizadorMeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Só o próprio utilizador da sessão, nunca terceiros. */
@RestController
public class UtilizadorMeController {

    private final UtilizadorMeService utilizadorMeService;

    public UtilizadorMeController(UtilizadorMeService utilizadorMeService) {
        this.utilizadorMeService = utilizadorMeService;
    }

    @GetMapping("/api/v1/utilizadores/me")
    public UtilizadorMeResponse obterMe() {
        return utilizadorMeService.obterMe();
    }

    @PatchMapping("/api/v1/utilizadores/me")
    public UtilizadorMeResponse atualizar(@RequestBody AtualizarPerfilRequest request) {
        return utilizadorMeService.atualizar(request);
    }
}
