package com.conkord.saigt.integracao.core;

public record ResultadoChamadaExterna<T>(Integer httpStatus, T corpo, FalhaTecnicaExterna falhaTecnica,
                                         long duracaoMs) {

    static <T> ResultadoChamadaExterna<T> sucesso(int httpStatus, T corpo, long duracaoMs) {
        return new ResultadoChamadaExterna<>(httpStatus, corpo, null, duracaoMs);
    }

    static <T> ResultadoChamadaExterna<T> falha(FalhaTecnicaExterna falhaTecnica, Integer httpStatus, long duracaoMs) {
        return new ResultadoChamadaExterna<>(httpStatus, null, falhaTecnica, duracaoMs);
    }

    public boolean temFalhaTecnica() {
        return falhaTecnica != null;
    }
}
