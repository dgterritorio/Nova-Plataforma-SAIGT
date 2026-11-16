package com.conkord.saigt.controller.utilizador;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.utilizador.AlterarPerfilInternoRequest;
import com.conkord.saigt.dto.utilizador.RevogarSessoesResponse;
import com.conkord.saigt.dto.utilizador.UtilizadorFiltro;
import com.conkord.saigt.dto.utilizador.UtilizadorResponse;
import com.conkord.saigt.service.utilizador.UtilizadorAdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Utilizadores. Só MASTER. Sem criação, remoção nem desativação: a linha nasce no primeiro login. */
@RestController
public class UtilizadorController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final UtilizadorAdminService utilizadorAdminService;

    public UtilizadorController(UtilizadorAdminService utilizadorAdminService) {
        this.utilizadorAdminService = utilizadorAdminService;
    }

    @GetMapping("/api/v1/utilizadores")
    public PageResponse<UtilizadorResponse> listar(
            @RequestParam(required = false) String origemAutenticacao,
            @RequestParam(required = false) String perfilInterno,
            @RequestParam(required = false) Long entidadeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate atuouDesde,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        int tamanhoAplicado = Math.min(size, TAMANHO_PAGINA_MAXIMO);
        UtilizadorFiltro filtro = new UtilizadorFiltro(origemAutenticacao, perfilInterno, entidadeId, atuouDesde);
        Page<UtilizadorResponse> resultado = utilizadorAdminService.listar(filtro,
                PageRequest.of(page, tamanhoAplicado, Sort.by("criadoEm").descending()));

        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/api/v1/utilizadores/{id}")
    public UtilizadorResponse obter(@PathVariable UUID id) {
        return utilizadorAdminService.obter(id);
    }

    @PatchMapping("/api/v1/utilizadores/{id}/perfil-interno")
    public UtilizadorResponse alterarPerfilInterno(@PathVariable UUID id, @RequestBody AlterarPerfilInternoRequest request) {
        return utilizadorAdminService.alterarPerfilInterno(id, request);
    }

    /** Sem corpo; um corpo enviado é ignorado. */
    @PatchMapping("/api/v1/utilizadores/{id}/sessoes/revogar")
    public RevogarSessoesResponse revogarSessoes(@PathVariable UUID id) {
        return utilizadorAdminService.revogarSessoes(id);
    }
}
