package com.conkord.saigt.dto.documento;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Campos editáveis da ficha de metadados (MOTU, EF v1.5 §3.6), como no Figma da «Decisão de elaborar sem Geoconform».
 * Grava-se como rascunho, com campos em branco; os obrigatórios só se exigem na submissão.
 */
public record MetadadosProcesso(
        @Size(max = 500) String palavrasChave,
        @Size(max = 50) String resolucaoEspacialEscala,
        @Size(max = 300) String tituloAlternativo,
        @Size(max = 4000) String resumo,
        @Size(max = 200) String sistemaReferencia,
        @Size(max = 50) String codigoSistema,
        @Size(max = 100) String cartografiaTipo,
        @Size(max = 300) String entidadeProprietaria,
        LocalDate dataEdicao,
        @Size(max = 300) String entidadeProdutora,
        LocalDate dataAtualizacao,
        @Size(max = 300) String atualizacaoComplemento,
        @Size(max = 200) String sistemaReferenciaNome,
        @Size(max = 50) String sistemaReferenciaCodigo,
        @Size(max = 100) String exatidaoPosicional,
        @Size(max = 100) String exatidaoTematica
) {

    public static MetadadosProcesso vazio() {
        return new MetadadosProcesso(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
