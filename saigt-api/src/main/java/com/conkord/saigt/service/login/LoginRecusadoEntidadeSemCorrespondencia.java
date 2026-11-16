package com.conkord.saigt.service.login;

import java.util.UUID;

/**
 * Login INCM recusado porque a entidade escolhida não existe na SAIGT. Consumido pelo aviso aos Master (pedido de
 * apoio gerado pela plataforma); `nipc` é o da adesão, nulo quando a INCM não o devolve.
 */
public record LoginRecusadoEntidadeSemCorrespondencia(UUID utilizadorId, String descricaoIncm, String nipc) {
}
