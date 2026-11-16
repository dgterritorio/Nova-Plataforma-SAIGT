package com.conkord.saigt.dto.documento;

/** Campos do perfil MOTU de um registo; o tipo do registo decide qual é (Série ou Conjunto de Dados). */
public sealed interface DadosMotu permits SerieMotuDados, ConjuntoMotuDados {
}
