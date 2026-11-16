package com.conkord.saigt.controller.sessao;

import com.conkord.saigt.dto.sessao.PermissoesResponse;
import com.conkord.saigt.service.sessao.SessaoPermissoesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** `origem`/`ator`/`perfilInterno` vêm sempre do JWT, nunca do pedido. */
@RestController
public class SessaoPermissoesController {

    private final SessaoPermissoesService sessaoPermissoesService;

    public SessaoPermissoesController(SessaoPermissoesService sessaoPermissoesService) {
        this.sessaoPermissoesService = sessaoPermissoesService;
    }

    @GetMapping("/api/v1/sessao/me/permissoes")
    public PermissoesResponse permissoes() {
        return sessaoPermissoesService.obterPermissoes();
    }
}
