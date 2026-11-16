package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * `PROCESSO` avalia processos por tipo de Procedimento: não existe tabela `procedimento`. Conta qualquer Dinâmica
 * que abranja o Instrumento, não só as iniciadas a partir dele.
 */
@Component
public class OcorrenciaEmEstadoAvaliador implements Avaliador {

    public static final String CODIGO_AVALIADOR = "OCORRENCIA_EM_ESTADO";

    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final CatalogoTipos catalogoTipos;
    private final ObjectMapper objectMapper;

    public OcorrenciaEmEstadoAvaliador(DinamicaRepository dinamicaRepository, ProcessoRepository processoRepository,
                                        CatalogoTipos catalogoTipos, ObjectMapper objectMapper) {
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.catalogoTipos = catalogoTipos;
        this.objectMapper = objectMapper;
    }

    @Override
    public String codigo() {
        return CODIGO_AVALIADOR;
    }

    @Override
    public boolean avaliar(UUID instrumentoId, String parametrosJson) {
        OcorrenciaParametros parametros = ler(parametrosJson);
        return switch (parametros.tipoOcorrencia()) {
            case OcorrenciaParametros.DINAMICA -> dinamicaRepository.existeAbrangendoComTipoEEstado(
                    instrumentoId, parametros.codigo(), parametros.estadoDinamica());
            case OcorrenciaParametros.PROCESSO -> processoRepository.existeProcessoAbrangendoComTipoEEstado(
                    instrumentoId, parametros.codigo(), parametros.estadoProcesso());
            default -> throw new IllegalStateException("tipo_ocorrencia não suportado: " + parametros.tipoOcorrencia());
        };
    }

    @Override
    public List<String> validarParametros(String parametrosJson) {
        return OcorrenciaParametros.validar(objectMapper, catalogoTipos, parametrosJson, true, false);
    }

    private OcorrenciaParametros ler(String parametrosJson) {
        try {
            return objectMapper.readValue(parametrosJson, OcorrenciaParametros.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("condicao.parametros inválido para " + CODIGO_AVALIADOR + ": " + parametrosJson, e);
        }
    }
}
