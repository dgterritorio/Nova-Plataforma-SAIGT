package com.conkord.saigt.service.documento;

import java.util.UUID;

/**
 * Uma linha do manifesto, como foi comunicada: `caminhoNoZip` é a caixa, a subpasta `GEOCONFORM/` se houver, e o
 * nome do objeto (id do documento à frente); `nomeFicheiro` é só esse último troço.
 */
public record EntradaDepositoSnit(UUID documentoId, String caminhoNoZip, String nomeFicheiro, String chave,
                                  String versao, long tamanhoBytes, String hashSha256) {
}
