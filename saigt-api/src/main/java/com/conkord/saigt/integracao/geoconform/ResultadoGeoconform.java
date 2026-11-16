package com.conkord.saigt.integracao.geoconform;

import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;

/**
 * `erro` é o corpo de uma resposta de erro, ou nulo quando não veio ou não se conseguiu ler; `location` é o cabeçalho
 * `Location` de uma resposta 2xx.
 */
public record ResultadoGeoconform<T>(ResultadoChamadaExterna<T> chamada, ErroGeoconform erro, String location) {

    static final String CODIGO_DUPLICADO = "idu_duplicado";

    public boolean sucesso() {
        Integer status = chamada.httpStatus();
        return !chamada.temFalhaTecnica() && status != null && status >= 200 && status < 300;
    }

    /** 409 `idu_duplicado`: o processo já existe no GeoConform. */
    public boolean duplicado() {
        return chamada.falhaTecnica() == FalhaTecnicaExterna.ERRO_HTTP && Integer.valueOf(409).equals(chamada.httpStatus())
                && erro != null && CODIGO_DUPLICADO.equals(erro.codigo());
    }

    /** Criado agora ou já existente. */
    public boolean criado() {
        return sucesso() || duplicado();
    }

    public T corpo() {
        return chamada.corpo();
    }

    public String codigoErro() {
        return erro == null ? null : erro.codigo();
    }
}
