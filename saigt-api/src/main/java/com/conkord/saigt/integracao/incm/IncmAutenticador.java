package com.conkord.saigt.integracao.incm;

import java.util.Optional;

/** Fronteira com o `ValidateUser` da INCM; isola a mecânica REST/Circuit Breaker, para testes. */
public interface IncmAutenticador {

    /** Vazio quando a INCM recusa as credenciais — nunca se distingue email desconhecido de senha errada (anti-enumeração). */
    Optional<UtilizadorIncm> autenticar(String username, String senha);

    /** Como {@link #autenticar}, mas devolve também o motivo da recusa dado pela INCM. */
    default ResultadoAutenticacaoIncm autenticarComMotivo(String username, String senha) {
        return autenticar(username, senha).map(ResultadoAutenticacaoIncm::aceite)
                .orElseGet(() -> ResultadoAutenticacaoIncm.recusada(null));
    }
}
