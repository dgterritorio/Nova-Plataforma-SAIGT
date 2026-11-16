package com.conkord.saigt.controller.entidade;

import com.conkord.saigt.dto.entidade.AtualizarContactoRequest;
import com.conkord.saigt.dto.entidade.ContactoRequest;
import com.conkord.saigt.dto.entidade.ContactoResponse;
import com.conkord.saigt.service.entidade.EntidadeContactoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contactos institucionais da entidade. Só {@code MASTER}, verificado em {@code EntidadeContactoService}. */
@RestController
public class EntidadeContactoController {

    private final EntidadeContactoService entidadeContactoService;

    public EntidadeContactoController(EntidadeContactoService entidadeContactoService) {
        this.entidadeContactoService = entidadeContactoService;
    }

    @GetMapping("/api/v1/entidades/{entidadeId}/contactos")
    public List<ContactoResponse> listar(@PathVariable Long entidadeId) {
        return entidadeContactoService.listar(entidadeId).stream().map(ContactoResponse::de).toList();
    }

    @PostMapping("/api/v1/entidades/{entidadeId}/contactos")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactoResponse criar(@PathVariable Long entidadeId, @RequestBody ContactoRequest request) {
        return ContactoResponse.de(entidadeContactoService.criarContacto(entidadeId, request));
    }

    @PatchMapping("/api/v1/entidades/{entidadeId}/contactos/{contactoId}")
    public ContactoResponse editar(@PathVariable Long entidadeId, @PathVariable Long contactoId,
                                   @RequestBody AtualizarContactoRequest request) {
        return ContactoResponse.de(entidadeContactoService.editarContacto(entidadeId, contactoId, request));
    }
}
