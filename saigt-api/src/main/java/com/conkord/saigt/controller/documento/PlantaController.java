package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.documento.AtualizarPlantaRequest;
import com.conkord.saigt.dto.documento.PlantaFiltro;
import com.conkord.saigt.dto.documento.PlantaResponse;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.service.documento.PlantaConsultaService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Plantas SAIGT/SNIT do BackOffice. A autorização (`MASTER`/`TECNICO`) está no serviço. */
@RestController
public class PlantaController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final PlantaConsultaService plantaConsultaService;

    public PlantaController(PlantaConsultaService plantaConsultaService) {
        this.plantaConsultaService = plantaConsultaService;
    }

    @GetMapping("/api/v1/plantas")
    public PageResponse<PlantaResponse> listar(
            @RequestParam(required = false) String numeroProcesso,
            @RequestParam(required = false) String numeroDeposito,
            @RequestParam(required = false) Boolean emVigor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        validarPagina(page, size);
        Page<PlantaResponse> resultado = plantaConsultaService.listar(
                new PlantaFiltro(numeroProcesso, numeroDeposito, emVigor), page, size);
        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @PatchMapping("/api/v1/plantas/{plantaId}")
    public PlantaResponse atualizar(@PathVariable UUID plantaId, @RequestBody(required = false) AtualizarPlantaRequest pedido) {
        return plantaConsultaService.atualizar(plantaId, pedido);
    }

    private static void validarPagina(int page, int size) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        if (page < 0) {
            erros.add(new ValidacaoException.CampoErro("page", "Não pode ser negativo."));
        }
        if (size < 1 || size > TAMANHO_PAGINA_MAXIMO) {
            erros.add(new ValidacaoException.CampoErro("size", "Tem de estar entre 1 e " + TAMANHO_PAGINA_MAXIMO + "."));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Parâmetros de paginação inválidos.", erros);
        }
    }
}
