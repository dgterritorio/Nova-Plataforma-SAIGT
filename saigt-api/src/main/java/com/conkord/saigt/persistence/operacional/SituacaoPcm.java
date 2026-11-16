package com.conkord.saigt.persistence.operacional;

/**
 * Situação do Processo face à PCM, derivada de `pcm_atribuido_a` e `pcm_devolvido_em`, sem coluna própria:
 * `COM_PCM` com a atribuição preenchida; `DEVOLVIDO` sem atribuição e com a data da devolução; `SEM_PCM` sem nenhuma.
 */
public enum SituacaoPcm {
    SEM_PCM,
    COM_PCM,
    DEVOLVIDO
}
