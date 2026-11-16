package com.conkord.saigt.dto.configuracao;

import com.fasterxml.jackson.databind.JsonNode;

public record CondicaoConfigResponse(Long id, String avaliador, JsonNode parametros, short grupo, boolean ativo) {}
