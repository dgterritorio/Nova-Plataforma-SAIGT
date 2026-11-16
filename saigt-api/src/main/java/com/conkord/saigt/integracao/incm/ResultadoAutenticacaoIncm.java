package com.conkord.saigt.integracao.incm;

import java.util.Optional;

/** `motivoRecusa` é o `ErrorMessage` da INCM quando recusa as credenciais; vazio se a INCM não o enviar. */
public record ResultadoAutenticacaoIncm(Optional<UtilizadorIncm> utilizador, String motivoRecusa) {

    public static ResultadoAutenticacaoIncm aceite(UtilizadorIncm utilizador) {
        return new ResultadoAutenticacaoIncm(Optional.of(utilizador), null);
    }

    public static ResultadoAutenticacaoIncm recusada(String motivo) {
        return new ResultadoAutenticacaoIncm(Optional.empty(), motivo);
    }
}
