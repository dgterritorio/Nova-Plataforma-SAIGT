package com.conkord.saigt.integracao.pcgt;

import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;

/** `erro` é o corpo de uma resposta de erro, ou nulo quando não veio ou não se conseguiu ler. */
public record ResultadoPcgt<T>(ResultadoChamadaExterna<T> chamada, ErroPcgt erro) {

    public boolean processoNaoExiste() {
        return chamada.falhaTecnica() == FalhaTecnicaExterna.ERRO_HTTP && Integer.valueOf(404).equals(chamada.httpStatus());
    }

    public boolean sucesso() {
        Integer status = chamada.httpStatus();
        return !chamada.temFalhaTecnica() && status != null && status >= 200 && status < 300;
    }

    public T corpo() {
        return chamada.corpo();
    }
}
