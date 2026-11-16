package com.conkord.saigt.service.documento;

import java.io.InputStream;
import java.util.List;

/**
 * Pedido de upload já separado em campos. `conteudo` é o corpo da parte `ficheiro` ainda por ler, ligado ao pedido
 * HTTP: só se consome uma vez e só enquanto o pedido durar. É nulo quando o pedido não traz a parte.
 * `verificarFim` confirma, depois de o ficheiro ser lido, que não vêm mais partes.
 */
public record PedidoUpload(String categoria, Long documentoConfigId, List<String> tags, String nomeFicheiro,
                           String contentType, InputStream conteudo, Runnable verificarFim) {
}
