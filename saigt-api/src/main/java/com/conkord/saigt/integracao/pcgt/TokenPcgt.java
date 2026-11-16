package com.conkord.saigt.integracao.pcgt;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * JWT da última autenticação na PCGT, só em memória. Um login de cada vez: quem espera reutiliza o token que o outro
 * obteve, em vez de autenticar de novo.
 */
final class TokenPcgt {

    /** `token` nulo quando o login falhou; `falha` traz então o resultado do login. */
    record Autenticacao<F>(String token, F falha) {
    }

    private final ReentrantLock bloqueio = new ReentrantLock();
    private volatile String token;

    /**
     * Devolve o token em memória, a menos que seja `recusado` (o que a PCGT acabou de responder com 401); nesse caso,
     * ou sem token, faz o login pelo `login`.
     */
    <F> Autenticacao<F> obter(String recusado, Supplier<Autenticacao<F>> login) {
        String atual = token;
        if (atual != null && !atual.equals(recusado)) {
            return new Autenticacao<>(atual, null);
        }
        bloqueio.lock();
        try {
            atual = token;
            if (atual != null && !atual.equals(recusado)) {
                return new Autenticacao<>(atual, null);
            }
            token = null;
            Autenticacao<F> autenticacao = login.get();
            token = autenticacao.token();
            return autenticacao;
        } finally {
            bloqueio.unlock();
        }
    }
}
