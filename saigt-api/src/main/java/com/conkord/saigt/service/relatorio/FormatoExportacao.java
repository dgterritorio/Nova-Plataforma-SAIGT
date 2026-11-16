package com.conkord.saigt.service.relatorio;

import java.util.Arrays;
import java.util.Optional;

public enum FormatoExportacao {
    PDF("application/pdf", "pdf"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String tipoConteudo;
    private final String extensao;

    FormatoExportacao(String tipoConteudo, String extensao) {
        this.tipoConteudo = tipoConteudo;
        this.extensao = extensao;
    }

    public String tipoConteudo() {
        return tipoConteudo;
    }

    public String extensao() {
        return extensao;
    }

    /** Só o nome exato: `pdf` ou `Excel` não são aceites, como nos restantes enums da API. */
    public static Optional<FormatoExportacao> de(String valor) {
        return Arrays.stream(values()).filter(formato -> formato.name().equals(valor)).findFirst();
    }
}
