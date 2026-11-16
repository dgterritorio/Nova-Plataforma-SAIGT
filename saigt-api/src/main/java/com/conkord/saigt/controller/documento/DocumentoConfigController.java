package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.documento.AtualizarDocumentoConfigRequest;
import com.conkord.saigt.dto.documento.CriarDocumentoConfigRequest;
import com.conkord.saigt.dto.documento.DocumentoConfigResponse;
import com.conkord.saigt.service.documento.DocumentoConfigService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Leitura: qualquer sessão autenticada. Escrita: só MASTER. */
@RestController
public class DocumentoConfigController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final DocumentoConfigService documentoConfigService;

    public DocumentoConfigController(DocumentoConfigService documentoConfigService) {
        this.documentoConfigService = documentoConfigService;
    }

    @GetMapping("/api/v1/documento-config")
    public PageResponse<DocumentoConfigResponse> listar(
            @RequestParam(required = false) Long instrumentoProcedimentoConfigId,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        Pageable pagina = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, TAMANHO_PAGINA_MAXIMO)),
                Sort.by("ordem", "id"));
        Page<DocumentoConfigResponse> resultado =
                documentoConfigService.listar(instrumentoProcedimentoConfigId, ativo, pagina);
        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @PostMapping("/api/v1/documento-config")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoConfigResponse criar(@Valid @RequestBody CriarDocumentoConfigRequest request) {
        return documentoConfigService.criar(request);
    }

    @PutMapping("/api/v1/documento-config/{id}")
    public DocumentoConfigResponse atualizar(@PathVariable Long id,
                                             @Valid @RequestBody AtualizarDocumentoConfigRequest request) {
        return documentoConfigService.atualizar(id, request);
    }
}
