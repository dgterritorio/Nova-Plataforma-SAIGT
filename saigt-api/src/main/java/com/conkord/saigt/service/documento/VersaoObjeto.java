package com.conkord.saigt.service.documento;

/** Uma versão de um objeto no storage, incluindo os marcadores de remoção, que nunca têm retenção. */
public record VersaoObjeto(String versionId, boolean retida) {
}
