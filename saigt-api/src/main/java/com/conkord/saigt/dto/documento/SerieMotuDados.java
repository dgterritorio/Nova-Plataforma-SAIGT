package com.conkord.saigt.dto.documento;

import jakarta.validation.constraints.Size;

/**
 * Campos editáveis da Série de Dados Geográficos (MOTU, EF v1.5 §3.6.1). Grava-se como rascunho, com campos em
 * branco; os obrigatórios só contam para `completo` e para a submissão.
 */
public record SerieMotuDados(
        @Size(max = 300) String tituloAlternativo,
        @Size(max = 4000) String resumo,
        @Size(max = 500) String palavrasChave,
        @Size(max = 50) String denominadorEscala,
        @Size(max = 200) String sistemaReferencia,
        @Size(max = 50) String codigoSistema,
        @Size(max = 4000) String historico,
        @Size(max = 4000) String relatorio
) implements DadosMotu {

    public static SerieMotuDados vazia() {
        return new SerieMotuDados(null, null, null, null, null, null, null, null);
    }

    /** Todos os campos obrigatórios com texto. */
    public boolean completa() {
        return preenchido(tituloAlternativo) && preenchido(resumo) && preenchido(palavrasChave)
                && preenchido(denominadorEscala) && preenchido(sistemaReferencia) && preenchido(historico)
                && preenchido(relatorio);
    }

    private static boolean preenchido(String texto) {
        return texto != null && !texto.isBlank();
    }
}
