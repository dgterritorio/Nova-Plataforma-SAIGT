package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sem {@code estado}, exige que não exista em nenhum estado: exprime unicidade sem enumerar estados,
 * para que um estado novo não escape às regras. Um IGT trazido do SNIT conta como já tendo a Dinâmica que o criou,
 * Elaboração ou Delimitação, que a SAIGT não regista: sem isto essa Dinâmica seria oferecida a um IGT em vigor.
 * Numa {@code DINAMICA} sem
 * {@code codigo}, a condição abrange qualquer Dinâmica do Instrumento (ex.: não haver Dinâmicas em simultâneo).
 * Conta qualquer Dinâmica que abranja o Instrumento, não só as iniciadas a partir dele.
 */
@Component
public class OcorrenciaNaoExisteAvaliador implements Avaliador {

    public static final String CODIGO_AVALIADOR = "OCORRENCIA_NAO_EXISTE";

    private static final Set<String> DINAMICAS_QUE_CRIAM_O_INSTRUMENTO = Set.of("ELABORACAO", "DELIMITACAO");

    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final CatalogoTipos catalogoTipos;
    private final ObjectMapper objectMapper;

    public OcorrenciaNaoExisteAvaliador(DinamicaRepository dinamicaRepository, ProcessoRepository processoRepository,
                                         InstrumentoRepository instrumentoRepository, CatalogoTipos catalogoTipos,
                                         ObjectMapper objectMapper) {
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.instrumentoRepository = instrumentoRepository;
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
        boolean existe;
        if (parametros.codigo() == null) {
            existe = existeAlgumaDinamica(instrumentoId, parametros);
        } else {
            existe = parametros.estado() == null ? existeEmQualquerEstado(instrumentoId, parametros)
                    : existeNoEstado(instrumentoId, parametros);
        }
        return !existe;
    }

    private boolean existeAlgumaDinamica(UUID instrumentoId, OcorrenciaParametros parametros) {
        if (!OcorrenciaParametros.DINAMICA.equals(parametros.tipoOcorrencia())) {
            throw new IllegalStateException("codigo obrigatório para tipo_ocorrencia " + parametros.tipoOcorrencia());
        }
        return parametros.estado() == null ? dinamicaRepository.existeAbrangendo(instrumentoId)
                : dinamicaRepository.existeAbrangendoComEstado(instrumentoId, parametros.estadoDinamica());
    }

    private boolean existeNoEstado(UUID instrumentoId, OcorrenciaParametros parametros) {
        return switch (parametros.tipoOcorrencia()) {
            case OcorrenciaParametros.DINAMICA -> dinamicaRepository.existeAbrangendoComTipoEEstado(
                    instrumentoId, parametros.codigo(), parametros.estadoDinamica());
            case OcorrenciaParametros.PROCESSO -> processoRepository.existeProcessoAbrangendoComTipoEEstado(
                    instrumentoId, parametros.codigo(), parametros.estadoProcesso());
            default -> throw new IllegalStateException("tipo_ocorrencia não suportado: " + parametros.tipoOcorrencia());
        };
    }

    private boolean existeEmQualquerEstado(UUID instrumentoId, OcorrenciaParametros parametros) {
        return switch (parametros.tipoOcorrencia()) {
            case OcorrenciaParametros.DINAMICA -> dinamicaRepository.existeAbrangendoComTipo(
                    instrumentoId, parametros.codigo())
                    || criadoNoSnit(instrumentoId, parametros.codigo());
            case OcorrenciaParametros.PROCESSO -> processoRepository.existeProcessoAbrangendoComTipo(
                    instrumentoId, parametros.codigo());
            default -> throw new IllegalStateException("tipo_ocorrencia não suportado: " + parametros.tipoOcorrencia());
        };
    }

    private boolean criadoNoSnit(UUID instrumentoId, String codigo) {
        return DINAMICAS_QUE_CRIAM_O_INSTRUMENTO.contains(codigo)
                && instrumentoRepository.existsByIdAndIdIgtSnitNotNull(instrumentoId);
    }

    @Override
    public List<String> validarParametros(String parametrosJson) {
        return OcorrenciaParametros.validar(objectMapper, catalogoTipos, parametrosJson, false, true);
    }

    private OcorrenciaParametros ler(String parametrosJson) {
        try {
            return objectMapper.readValue(parametrosJson, OcorrenciaParametros.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("condicao.parametros inválido para " + CODIGO_AVALIADOR + ": " + parametrosJson, e);
        }
    }
}
