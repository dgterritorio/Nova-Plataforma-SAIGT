package com.conkord.saigt.integracao.core;

import org.springframework.http.HttpHeaders;

public enum SistemaExterno {
    INCM("ServiceKey", "INCM"),
    SNIT("X-Saigt-Api-Key", "SNIT"),
    PCGT(HttpHeaders.AUTHORIZATION, "PCGT"),
    GEOCONFORM(HttpHeaders.AUTHORIZATION, "GeoConform");

    private final String cabecalhoCredencial;
    private final String nomeApresentacao;

    SistemaExterno(String cabecalhoCredencial, String nomeApresentacao) {
        this.cabecalhoCredencial = cabecalhoCredencial;
        this.nomeApresentacao = nomeApresentacao;
    }

    public String cabecalhoCredencial() {
        return cabecalhoCredencial;
    }

    public String nomeApresentacao() {
        return nomeApresentacao;
    }
}
