package com.conkord.saigt.service.core;

import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** `documento_config.extensoes_aceites` (array JSON) normalizado: sem ponto inicial, em minúsculas. */
public final class ExtensoesAceites {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExtensoesAceites() {
    }

    public static List<String> ler(DocumentoConfig documentoConfig) {
        try {
            return Arrays.stream(MAPPER.readValue(documentoConfig.getExtensoesAceites(), String[].class))
                    .map(extensao -> extensao.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("extensoes_aceites inválido no documento_config " + documentoConfig.getId(), e);
        }
    }

    /** Normaliza (sem ponto, minúsculas, sem repetidos, pela ordem recebida) e serializa para a coluna. */
    public static String escrever(List<String> extensoes) {
        try {
            return MAPPER.writeValueAsString(extensoes.stream()
                    .map(extensao -> extensao.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("extensoes_aceites não serializável", e);
        }
    }
}
