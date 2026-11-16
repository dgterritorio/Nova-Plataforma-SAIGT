package com.conkord.saigt.dto.instrumento;

import java.util.List;

/**
 * Já passou as Camadas 2, 3 e 4: um Procedimento sem permissão ou indisponível não aparece, sem flag de erro.
 * {@code tentativaEmCurso} não nulo significa "retoma, não cria": a última tentativa está em `EM_SUBMISSAO`.
 */
public record ProcedimentoDisponivelResponse(
        Long instrumentoProcedimentoConfigId,
        String tipoProcedimento,
        String nomeProcedimento,
        int ordem,
        boolean obrigatorio,
        boolean atoPontual,
        Integer prazoDias,
        String serieDr,
        String formulario,
        List<DocumentoExigidoResponse> documentosExigidos,
        List<TipoAtoDisponivelResponse> tiposAtoDisponiveis,
        TentativaEmCursoResponse tentativaEmCurso,
        /** Nos atos pontuais, os atos publicados que podem visar (nunca vazia, senão o ato não aparece); vazia nos outros. */
        List<AtoReferivelResponse> atosReferiveis
) {
}
