package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Sem parâmetros: lê `instrumento.em_vigor`, mantida por `InstrumentoService` quando uma Dinâmica termina. */
@Component
public class InstrumentoEmVigorAvaliador implements Avaliador {

    public static final String CODIGO_AVALIADOR = "INSTRUMENTO_EM_VIGOR";

    private final InstrumentoRepository instrumentoRepository;
    private final ObjectMapper objectMapper;

    public InstrumentoEmVigorAvaliador(InstrumentoRepository instrumentoRepository, ObjectMapper objectMapper) {
        this.instrumentoRepository = instrumentoRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String codigo() {
        return CODIGO_AVALIADOR;
    }

    @Override
    public boolean avaliar(UUID instrumentoId, String parametrosJson) {
        return instrumentoRepository.findById(instrumentoId).map(Instrumento::isEmVigor).orElse(false);
    }

    /** Uma chave aqui é sinal de que a condição queria outro avaliador. */
    @Override
    public List<String> validarParametros(String parametrosJson) {
        List<String> erros = new ArrayList<>();
        JsonNode parametros = ValidacaoParametros.lerObjeto(objectMapper, parametrosJson, erros);
        if (parametros != null) {
            ValidacaoParametros.rejeitarChavesDesconhecidas(parametros, Set.of(), erros);
        }
        return erros;
    }
}
