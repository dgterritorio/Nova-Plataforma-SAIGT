package com.conkord.saigt.controller.entidade;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.entidade.AtualizarEntidadeRequest;
import com.conkord.saigt.dto.entidade.CriarEntidadeRequest;
import com.conkord.saigt.dto.entidade.EntidadeEmitenteResponse;
import com.conkord.saigt.dto.entidade.EntidadeFiltro;
import com.conkord.saigt.dto.entidade.EntidadeResponse;
import com.conkord.saigt.service.entidade.EntidadeAdminService;
import com.conkord.saigt.service.entidade.EntidadeEmitenteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Só MASTER, menos a lista das entidades emitentes (qualquer sessão com entidade). */
@RestController
public class EntidadeController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final EntidadeAdminService entidadeAdminService;
    private final EntidadeEmitenteService entidadeEmitenteService;

    public EntidadeController(EntidadeAdminService entidadeAdminService, EntidadeEmitenteService entidadeEmitenteService) {
        this.entidadeAdminService = entidadeAdminService;
        this.entidadeEmitenteService = entidadeEmitenteService;
    }

    /** Só `id` e nome das entidades ativas do tipo, por ordem do nome; não é a listagem de administração. */
    @GetMapping("/api/v1/entidades/emitentes")
    public List<EntidadeEmitenteResponse> emitentes(@RequestParam(required = false) String tipoEntidade) {
        return entidadeEmitenteService.listar(tipoEntidade);
    }

    @PostMapping("/api/v1/entidades")
    @ResponseStatus(HttpStatus.CREATED)
    public EntidadeResponse criar(@RequestBody CriarEntidadeRequest request) {
        return entidadeAdminService.criar(request);
    }

    @PatchMapping("/api/v1/entidades/{id}")
    public EntidadeResponse atualizar(@PathVariable Long id, @RequestBody AtualizarEntidadeRequest request) {
        return entidadeAdminService.atualizar(id, request);
    }

    @DeleteMapping("/api/v1/entidades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        entidadeAdminService.eliminar(id);
    }

    @GetMapping("/api/v1/entidades")
    public PageResponse<EntidadeResponse> listar(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String tipoEntidade,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        int tamanhoAplicado = Math.min(size, TAMANHO_PAGINA_MAXIMO);
        EntidadeFiltro filtro = new EntidadeFiltro(ativo, nome, entidadeAdminService.converterTipoFiltro(tipoEntidade));
        Page<EntidadeResponse> resultado = entidadeAdminService.listar(filtro,
                PageRequest.of(page, tamanhoAplicado, Sort.by("descricaoSaigt", "id")));

        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }
}
