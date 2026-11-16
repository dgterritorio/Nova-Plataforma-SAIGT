package com.conkord.saigt.integracao.incm;

/** Uma falha técnica vem aqui, nunca como exceção; `corpo` só vem com uma resposta 2xx lida. */
public record ResultadoChamadaIncm<T>(Integer httpStatus, T corpo, FalhaTecnicaIncm falhaTecnica, long duracaoMs) {

    static <T> ResultadoChamadaIncm<T> sucesso(int httpStatus, T corpo, long duracaoMs) {
        return new ResultadoChamadaIncm<>(httpStatus, corpo, null, duracaoMs);
    }

    static <T> ResultadoChamadaIncm<T> falha(FalhaTecnicaIncm falhaTecnica, Integer httpStatus, long duracaoMs) {
        return new ResultadoChamadaIncm<>(httpStatus, null, falhaTecnica, duracaoMs);
    }

    public boolean temFalhaTecnica() {
        return falhaTecnica != null;
    }
}
