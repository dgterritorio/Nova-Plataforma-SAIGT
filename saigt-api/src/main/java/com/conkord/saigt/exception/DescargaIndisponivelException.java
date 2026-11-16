package com.conkord.saigt.exception;

import java.time.Duration;

/** Storage temporariamente indisponível antes de começar o envio. Falha → 503 com `Retry-After`. */
public class DescargaIndisponivelException extends ConkordException {
    private static final long serialVersionUID = 1L;

    private final transient Duration repetirApos;

    public DescargaIndisponivelException(Duration repetirApos, Throwable causa) {
        super("DESCARGA_INDISPONIVEL", "Descarga temporariamente indisponível. Tente novamente.", causa);
        this.repetirApos = repetirApos;
    }

    public Duration getRepetirApos() { return repetirApos; }
}
