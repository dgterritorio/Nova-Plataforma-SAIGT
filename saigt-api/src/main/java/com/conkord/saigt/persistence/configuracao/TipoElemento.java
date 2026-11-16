package com.conkord.saigt.persistence.configuracao;

public enum TipoElemento {
    PECA_ESCRITA("PECAS_ESCRITAS"),
    PECA_GRAFICA("PECAS_GRAFICAS"),
    OUTROS("OUTROS");

    private final String codigoElementoAfetado;

    TipoElemento(String codigoElementoAfetado) {
        this.codigoElementoAfetado = codigoElementoAfetado;
    }

    /** Valor deste tipo em `processo.elementos_afetados`. */
    public String codigoElementoAfetado() {
        return codigoElementoAfetado;
    }
}
