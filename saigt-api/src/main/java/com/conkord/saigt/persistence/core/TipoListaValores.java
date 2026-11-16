package com.conkord.saigt.persistence.core;


public enum TipoListaValores {
    MOTIVO_PROCEDIMENTO_REN,
    CATEGORIA_REJEICAO_DGT,
    MOTIVO_REJEICAO_DGT,
    ALTERACAO_PEOT,
    DOMINIO_SRUP,
    CLASSE_SRUP,
    TIPOLOGIA_SRUP,
    FUNDAMENTACAO_RJIGT,
    FREGUESIA,
    TOPONIMO,
    MOTIVO_ALTERACAO_LEI,
    MOTIVO_ALTERACAO_PROGRAMA,
    MOTIVO_ALTERACAO_PLANO,
    /** Região do formulário SGIFR (EF l.1527). Gerida pelo Master, arranca vazia (OI-246). */
    REGIAO,
    /** CIM / AM do formulário SGIFR (EF l.1528); cada valor depende de uma {@link #REGIAO} por `pai_id`. */
    CIM_AM
}
