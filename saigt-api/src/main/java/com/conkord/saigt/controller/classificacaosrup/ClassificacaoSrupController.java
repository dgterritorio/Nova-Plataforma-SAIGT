package com.conkord.saigt.controller.classificacaosrup;

import com.conkord.saigt.dto.classificacaosrup.ClassificacaoSrupDominioResponse;
import com.conkord.saigt.service.classificacaosrup.ClassificacaoSrupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Classificação das servidões para os ecrãs; qualquer utilizador autenticado, por ser catálogo. */
@RestController
public class ClassificacaoSrupController {

    private final ClassificacaoSrupService classificacaoSrupService;

    public ClassificacaoSrupController(ClassificacaoSrupService classificacaoSrupService) {
        this.classificacaoSrupService = classificacaoSrupService;
    }

    @GetMapping("/api/v1/classificacao-srup")
    public List<ClassificacaoSrupDominioResponse> listar() {
        return classificacaoSrupService.listar();
    }
}
