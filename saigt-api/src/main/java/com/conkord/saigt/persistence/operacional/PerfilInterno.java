package com.conkord.saigt.persistence.operacional;

import java.util.Arrays;
import java.util.Optional;

/** Perfil de um utilizador AD_DGT. Mesmos valores do CHECK `ck_utilizador_perfil_interno_valor`. */
public enum PerfilInterno {
    MASTER,
    TECNICO,
    VISITANTE;

    public static Optional<PerfilInterno> deTexto(String valor) {
        return Arrays.stream(values()).filter(perfil -> perfil.name().equals(valor)).findFirst();
    }
}
