package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Convivência com o legado: existe Dinâmica homónima `ESPELHO_ABERTO` que abrange o mesmo Instrumento. */
@Component
public class DinamicaBloqueadaLegadoAvaliador implements Avaliador {

    public static final String CODIGO_AVALIADOR = "DINAMICA_BLOQUEADA_LEGADO";
    private static final String ORIGEM_ESPELHO_ABERTO = "ESPELHO_ABERTO";
    private static final String CHAVE_CODIGO = "codigo";

    private final DinamicaRepository dinamicaRepository;
    private final CatalogoTipos catalogoTipos;
    private final ObjectMapper objectMapper;

    public DinamicaBloqueadaLegadoAvaliador(DinamicaRepository dinamicaRepository, CatalogoTipos catalogoTipos,
                                            ObjectMapper objectMapper) {
        this.dinamicaRepository = dinamicaRepository;
        this.catalogoTipos = catalogoTipos;
        this.objectMapper = objectMapper;
    }

    @Override
    public String codigo() {
        return CODIGO_AVALIADOR;
    }

    @Override
    public boolean avaliar(UUID instrumentoId, String parametrosJson) {
        CodigoParametro parametros = ler(parametrosJson);
        return dinamicaRepository.existeAbrangendoComOrigemETipo(instrumentoId, ORIGEM_ESPELHO_ABERTO, parametros.codigo());
    }

    @Override
    public List<String> validarParametros(String parametrosJson) {
        List<String> erros = new ArrayList<>();
        JsonNode parametros = ValidacaoParametros.lerObjeto(objectMapper, parametrosJson, erros);
        if (parametros != null) {
            ValidacaoParametros.rejeitarChavesDesconhecidas(parametros, Set.of(CHAVE_CODIGO), erros);
            String codigo = ValidacaoParametros.lerTexto(parametros, CHAVE_CODIGO, true, erros);
            ValidacaoParametros.exigirTipoDinamica(catalogoTipos, codigo, erros);
        }
        return erros;
    }

    private CodigoParametro ler(String parametrosJson) {
        try {
            return objectMapper.readValue(parametrosJson, CodigoParametro.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("condicao.parametros inválido para " + CODIGO_AVALIADOR + ": " + parametrosJson, e);
        }
    }
}
