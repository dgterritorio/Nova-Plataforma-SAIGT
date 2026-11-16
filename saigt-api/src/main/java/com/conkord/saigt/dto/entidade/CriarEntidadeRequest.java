package com.conkord.saigt.dto.entidade;

/**
 * `tipoEntidade` chega como texto para o serviço responder 400 no campo quando está fora da lista.
 * `nipc` e `dico` são opcionais; o `dico` só nas câmaras municipais.
 */
public record CriarEntidadeRequest(String codigo, String descricaoIncm, String descricaoSaigt, String tipoEntidade,
                                   String nipc, String dico) {
}
