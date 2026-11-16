package com.conkord.saigt.controller.listavalores;

import com.conkord.saigt.dto.instrumento.OpcaoListaResponse;
import com.conkord.saigt.dto.listavalores.AtualizarListaValoresRequest;
import com.conkord.saigt.dto.listavalores.CriarListaValoresRequest;
import com.conkord.saigt.dto.listavalores.ListaValoresResponse;
import com.conkord.saigt.service.listavalores.ListaValoresService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogos de `lista_valores`. Só `MASTER` escreve; MASTER/TECNICO leem; as opções dos formulários são de todos. */
@RestController
public class ListaValoresController {

    private final ListaValoresService listaValoresService;

    public ListaValoresController(ListaValoresService listaValoresService) {
        this.listaValoresService = listaValoresService;
    }

    @GetMapping("/api/v1/lista-valores")
    public List<ListaValoresResponse> listar(@RequestParam(required = false) String tipo) {
        return listaValoresService.listar(tipo);
    }

    @GetMapping("/api/v1/lista-valores/opcoes")
    public List<OpcaoListaResponse> opcoes(@RequestParam(required = false) String tipo) {
        return listaValoresService.opcoesFormulario(tipo);
    }

    @PostMapping("/api/v1/lista-valores")
    @ResponseStatus(HttpStatus.CREATED)
    public ListaValoresResponse criar(@Valid @RequestBody CriarListaValoresRequest request) {
        return listaValoresService.criar(request);
    }

    @PatchMapping("/api/v1/lista-valores/{id}")
    public ListaValoresResponse atualizar(@PathVariable Long id, @RequestBody AtualizarListaValoresRequest request) {
        return listaValoresService.atualizar(id, request);
    }
}
