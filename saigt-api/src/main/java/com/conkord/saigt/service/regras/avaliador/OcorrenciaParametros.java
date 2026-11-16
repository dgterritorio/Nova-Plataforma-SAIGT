package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** {@code condicao.parametros} é preenchido por SQL/backoffice: os nomes JSON têm de coincidir à letra. */
record OcorrenciaParametros(@JsonProperty("tipo_ocorrencia") String tipoOcorrencia, String codigo, String estado) {

    static final String DINAMICA = "DINAMICA";
    static final String PROCESSO = "PROCESSO";

    private static final String CHAVE_TIPO_OCORRENCIA = "tipo_ocorrencia";
    private static final String CHAVE_CODIGO = "codigo";
    private static final String CHAVE_ESTADO = "estado";
    private static final Set<String> CHAVES = Set.of(CHAVE_TIPO_OCORRENCIA, CHAVE_CODIGO, CHAVE_ESTADO);

    /** Um `estado` fora do enum é erro de configuração da regra, não um "não existe" silencioso. */
    EstadoDinamica estadoDinamica() {
        return EstadoDinamica.valueOf(estado);
    }

    EstadoProcesso estadoProcesso() {
        return EstadoProcesso.valueOf(estado);
    }

    /**
     * Os estados válidos vêm dos mesmos enums que {@link #estadoDinamica()} e {@link #estadoProcesso()}. Com
     * {@code codigoOpcionalEmDinamica}, uma {@code DINAMICA} sem {@code codigo} abrange qualquer tipo de Dinâmica;
     * num {@code PROCESSO} o {@code codigo} é sempre obrigatório.
     */
    static List<String> validar(ObjectMapper objectMapper, CatalogoTipos catalogo, String parametrosJson,
                                boolean estadoObrigatorio, boolean codigoOpcionalEmDinamica) {
        List<String> erros = new ArrayList<>();
        JsonNode parametros = ValidacaoParametros.lerObjeto(objectMapper, parametrosJson, erros);
        if (parametros == null) {
            return erros;
        }
        ValidacaoParametros.rejeitarChavesDesconhecidas(parametros, CHAVES, erros);
        String tipoOcorrencia = ValidacaoParametros.lerTexto(parametros, CHAVE_TIPO_OCORRENCIA, true, erros);
        boolean codigoObrigatorio = !(codigoOpcionalEmDinamica && DINAMICA.equals(tipoOcorrencia));
        String codigo = ValidacaoParametros.lerTexto(parametros, CHAVE_CODIGO, codigoObrigatorio, erros);
        String estado = ValidacaoParametros.lerTexto(parametros, CHAVE_ESTADO, estadoObrigatorio, erros);
        if (tipoOcorrencia == null) {
            return erros;
        }
        switch (tipoOcorrencia) {
            case DINAMICA -> {
                ValidacaoParametros.exigirTipoDinamica(catalogo, codigo, erros);
                exigirEstado(estado, EstadoDinamica.values(), "dinamica", erros);
            }
            case PROCESSO -> {
                if (codigo != null && !catalogo.existeTipoProcedimento(codigo)) {
                    erros.add("codigo inexistente em tipo_procedimento: " + codigo);
                }
                exigirEstado(estado, EstadoProcesso.values(), "processo", erros);
            }
            default -> erros.add("tipo_ocorrencia tem de ser " + DINAMICA + " ou " + PROCESSO + ": " + tipoOcorrencia);
        }
        return erros;
    }

    private static void exigirEstado(String estado, Enum<?>[] estados, String ocorrencia, List<String> erros) {
        if (estado != null && Arrays.stream(estados).noneMatch(valido -> valido.name().equals(estado))) {
            erros.add("estado " + estado + " não pertence a " + ocorrencia + " " + Arrays.toString(estados));
        }
    }
}
