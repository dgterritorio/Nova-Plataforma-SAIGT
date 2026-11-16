package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;

import java.util.UUID;

/** Link público resolvido e o documento que serve, lidos numa transação curta antes do envio. */
public record AlvoLinkPublico(UUID linkId, UUID token, DestinoLinkPublico destino, boolean ativo,
                              AlvoDescarga documento) {
}
