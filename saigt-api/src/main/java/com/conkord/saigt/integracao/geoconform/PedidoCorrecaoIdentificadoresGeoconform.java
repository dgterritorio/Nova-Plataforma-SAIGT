package com.conkord.saigt.integracao.geoconform;

import java.util.List;

/**
 * Corpo do `PUT /api/geoconform/v1/tentativas/{idusDinamica}/identificadores` (I-03). O `motivo` `reinicio` existe no
 * contrato, mas a SAIGT só usa `publicacao_final`.
 */
public record PedidoCorrecaoIdentificadoresGeoconform(
        String idusDinamica,
        String motivo,
        List<CorrespondenciaIdusGeoconform> correspondencias
) {

    public static final String MOTIVO_PUBLICACAO_FINAL = "publicacao_final";

    public PedidoCorrecaoIdentificadoresGeoconform {
        correspondencias = correspondencias == null ? List.of() : List.copyOf(correspondencias);
    }

    public static PedidoCorrecaoIdentificadoresGeoconform publicacaoFinal(String idusDinamica,
                                                                         List<CorrespondenciaIdusGeoconform> correspondencias) {
        return new PedidoCorrecaoIdentificadoresGeoconform(idusDinamica, MOTIVO_PUBLICACAO_FINAL, correspondencias);
    }
}
