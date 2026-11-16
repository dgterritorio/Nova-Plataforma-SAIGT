package com.conkord.saigt.controller.listavalores;

import com.conkord.saigt.dto.listavalores.CatalogoResponse;
import com.conkord.saigt.service.listavalores.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogos globais para o ecrã "Listas de Valores" — só MASTER lê. */
@RestController
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/api/v1/catalogos")
    public List<CatalogoResponse> listar() {
        return catalogoService.listar();
    }
}
