package com.conkord.saigt.dto.instrumento;

/** Tipo de Dinâmica elegível agora pelo motor de regras, ainda não iniciada. */
public record DinamicaDisponivelResponse(Long instrumentoDinamicaConfigId, String tipoDinamica, String nomeDinamica) {
}
