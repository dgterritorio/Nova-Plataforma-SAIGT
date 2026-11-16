package com.conkord.saigt.service.documento;

import java.util.UUID;

/**
 * O que a descarga precisa do documento, lido numa transação curta para o streaming não ter nenhuma aberta. Num anexo
 * de notificação, `documentoId` é o do anexo e `processoId` e `hashSha256` são nulos.
 */
public record AlvoDescarga(UUID documentoId, UUID processoId, UUID utilizadorId, String caminho, String versaoObjeto,
                           String nomeFicheiro, long tamanhoBytes, String hashSha256, TipoTicketDescarga tipo) {

    public String etag() {
        return hashSha256 != null ? hashSha256 : documentoId.toString();
    }
}
