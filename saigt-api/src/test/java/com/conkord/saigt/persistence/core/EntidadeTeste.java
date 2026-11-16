package com.conkord.saigt.persistence.core;

import java.util.concurrent.atomic.AtomicLong;

/** Fábrica de entidades de teste: `codigo` e `tipo_entidade` são obrigatórios e o código é único. */
public final class EntidadeTeste {

    // As suites partilham a mesma base H2, por isso o código tem de ser único em toda a JVM.
    private static final AtomicLong SEQUENCIA = new AtomicLong(900_000);

    private EntidadeTeste() {
    }

    public static Entidade nova(String nipc, String descricao) {
        return nova(nipc, descricao, TipoEntidade.CM);
    }

    public static Entidade nova(String nipc, String descricao, TipoEntidade tipoEntidade) {
        return new Entidade("T" + SEQUENCIA.incrementAndGet(), tipoEntidade, nipc, descricao, descricao);
    }
}
