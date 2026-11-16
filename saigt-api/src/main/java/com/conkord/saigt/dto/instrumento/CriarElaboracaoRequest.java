package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * IGT novo, Elaboração e Decisão de elaborar num só pedido; a entidade é sempre a da sessão. `concelhos` segue as
 * regras de {@link CriarInstrumentoRequest}. `concelho` (um só) é o formato anterior, aceite enquanto o ecrã não
 * passa a enviar a lista; `concelhos` tem precedência. `nutIICodigo` é opcional: quando vem, tem de ser uma NUT II
 * que a Elaboração oferece.
 */
public record CriarElaboracaoRequest(
        @NotNull Long tipoInstrumentoId,
        @NotBlank @Size(max = 255) String designacao,
        @Size(max = 50) String nutIICodigo,
        @Size(max = 50) List<@NotNull @Pattern(regexp = "\\d{4}", message = "Código de concelho inválido.") String> concelhos,
        @Pattern(regexp = "\\d{4}", message = "Código de concelho inválido.") String concelho,
        @NotNull Long tipoAtoId
) {

    public CriarElaboracaoRequest(Long tipoInstrumentoId, String designacao, String nutIICodigo, List<String> concelhos,
                                  Long tipoAtoId) {
        this(tipoInstrumentoId, designacao, nutIICodigo, concelhos, null, tipoAtoId);
    }

    /** Os concelhos pedidos, da lista ou, na sua falta, do campo `concelho`; nulo quando nenhum vem. */
    public List<String> concelhosPedidos() {
        if (concelhos != null) {
            return concelhos;
        }
        return concelho == null ? null : List.of(concelho);
    }
}
