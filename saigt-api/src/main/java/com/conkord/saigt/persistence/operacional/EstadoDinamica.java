package com.conkord.saigt.persistence.operacional;

/**
 * `dinamica.estado`, gravado por nome. TERMINADA quando o procedimento final é publicado; CADUCADA quando atinge a
 * data de caducidade sem terminar, sem bloquear nem mudar o estado dos processos.
 */
public enum EstadoDinamica {
    EM_CURSO,
    TERMINADA,
    CADUCADA
}
