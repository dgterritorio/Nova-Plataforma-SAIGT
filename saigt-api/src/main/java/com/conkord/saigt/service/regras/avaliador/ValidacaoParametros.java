package com.conkord.saigt.service.regras.avaliador;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

/**
 * Lê {@code condicao.parametros} como árvore, não como record: o {@code ObjectMapper} do Spring ignora
 * chaves desconhecidas. Acumula motivos em vez de lançar, para o arranque listar todos.
 */
final class ValidacaoParametros {

    private ValidacaoParametros() {
    }

    /** @return o objeto, ou {@code null} com o motivo já registado. */
    static JsonNode lerObjeto(ObjectMapper objectMapper, String parametrosJson, List<String> erros) {
        try {
            JsonNode parametros = objectMapper.readTree(parametrosJson);
            if (parametros == null || !parametros.isObject()) {
                erros.add("parametros não é um objeto JSON: " + parametrosJson);
                return null;
            }
            return parametros;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            erros.add("JSON inválido: " + parametrosJson);
            return null;
        }
    }

    static void rejeitarChavesDesconhecidas(JsonNode parametros, Set<String> permitidas, List<String> erros) {
        parametros.fieldNames().forEachRemaining(chave -> {
            if (!permitidas.contains(chave)) {
                erros.add("chave desconhecida: " + chave);
            }
        });
    }

    /** Um {@code null} explícito conta como omitido, tal como na leitura para avaliação. */
    static String lerTexto(JsonNode parametros, String chave, boolean obrigatorio, List<String> erros) {
        JsonNode valor = parametros.get(chave);
        if (valor == null || valor.isNull()) {
            if (obrigatorio) {
                erros.add(chave + " obrigatório");
            }
            return null;
        }
        if (!valor.isTextual() || valor.asText().isBlank()) {
            erros.add(chave + " tem de ser texto não vazio: " + valor);
            return null;
        }
        return valor.asText();
    }

    static void exigirTipoDinamica(CatalogoTipos catalogo, String codigo, List<String> erros) {
        if (codigo != null && !catalogo.existeTipoDinamica(codigo)) {
            erros.add("codigo inexistente em tipo_dinamica: " + codigo);
        }
    }
}
