package com.conkord.saigt.dto.documento;

import com.conkord.saigt.dto.documento.MetadadosResponse.Responsavel;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;

import java.util.List;
import java.util.UUID;

/**
 * Ficha MOTU do processo. O responsável vem da entidade, igual em todos os registos e só de leitura; cada registo
 * traz em `dados` os campos do seu `tipo`.
 */
public record MetadadosMotuResponse(
        boolean aplicaFichaMotu,
        Responsavel responsavel,
        List<Registo> registos
) {

    /** `zip` e `plantas` só vêm nos Conjuntos; o `zip` falta nas fichas opcionais, que não têm documento. */
    public record Registo(UUID id, TipoMetadadosMotu tipo, boolean opcional, boolean completo, String titulo,
                          ZipConjunto zip, DadosMotu dados, List<FolhaConjunto> plantas) {
    }

    public record ZipConjunto(UUID id, String nome) {
    }

    public record FolhaConjunto(UUID id, String nomeRegulamento, String numeroFolha, String nomeTiff) {
    }
}
