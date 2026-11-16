package com.conkord.saigt.dto.snit;

public record InfoEntradaVigorResponse(String numeroDeposito, String estado) {

    public static final String RECEBIDO = "RECEBIDO";

    public static InfoEntradaVigorResponse recebido(String numeroDeposito) {
        return new InfoEntradaVigorResponse(numeroDeposito, RECEBIDO);
    }
}
