package com.conkord.saigt.service.regras.avaliador;

import java.util.List;
import java.util.UUID;

/** {@code codigo()} corresponde ao valor livre em {@code condicao.avaliador}, sem tabela de catálogo. */
public interface Avaliador {

    String codigo();

    /** @param parametrosJson {@code condicao.parametros}; cada avaliador interpreta o seu schema. */
    boolean avaliar(UUID instrumentoId, String parametrosJson);

    /**
     * Se o resultado depende do Instrumento: numa Dinâmica iniciada sem IGT a condição só se avalia quando os
     * Instrumentos se associam. Um avaliador que não depende é chamado com {@code instrumentoId} nulo.
     */
    default boolean dependeDeInstrumento() {
        return true;
    }

    /**
     * Chamado no arranque para cada condição ativa deste avaliador, para que um erro de configuração não
     * espere pela primeira avaliação.
     *
     * @return os motivos de invalidade, todos; vazio se os parâmetros são válidos
     */
    List<String> validarParametros(String parametrosJson);
}
