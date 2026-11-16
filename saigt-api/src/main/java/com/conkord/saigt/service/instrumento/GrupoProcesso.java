package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;

import java.util.Optional;
import java.util.Set;

/**
 * Grupos de estados das listagens e dos painéis (BL-PROC-050). O critério depende de quem pergunta:
 * a DGT só tem Publicados e Em validação (FR-158), pelo que os outros dois não se aplicam ao perfil interno.
 */
public enum GrupoProcesso {
    PUBLICADOS,
    EM_CURSO,
    PARA_CORRECAO,
    EM_VALIDACAO;

    /**
     * @param apenasProprios `processo.criado_por` tem de ser o utilizador da sessão
     * @param incluiValidadosAtribuidos soma, em OR, os `VALIDADO_DGT` com `processo.pcm_atribuido_a` igual ao utilizador
     * @param excluiValidadosComPcm tira os `VALIDADO_DGT` com `processo.pcm_atribuido_a` preenchido (estão com a PCM)
     */
    public record Criterio(Set<EstadoProcesso> estados, boolean apenasProprios, boolean incluiValidadosAtribuidos,
                           boolean excluiValidadosComPcm) {

        public Criterio(Set<EstadoProcesso> estados, boolean apenasProprios, boolean incluiValidadosAtribuidos) {
            this(estados, apenasProprios, incluiValidadosAtribuidos, false);
        }
    }

    /** Vazio quando o grupo não se aplica ao contexto. */
    public Optional<Criterio> criterio(ContextoUtilizador contexto) {
        return contexto.perfilInterno() == null ? criterioExterno(contexto) : criterioInterno();
    }

    private Optional<Criterio> criterioExterno(ContextoUtilizador contexto) {
        return Optional.of(switch (this) {
            case PUBLICADOS -> new Criterio(Set.of(EstadoProcesso.PUBLICADO), false, false);
            case EM_CURSO -> new Criterio(Set.of(EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT), true, false);
            case PARA_CORRECAO -> new Criterio(Set.of(EstadoProcesso.REJEITADO_DGT, EstadoProcesso.ANULADO_INCM),
                    true, contexto.pcm());
            case EM_VALIDACAO -> new Criterio(Set.of(EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.VALIDADO_DGT,
                    EstadoProcesso.ENVIADO_INCM, EstadoProcesso.EM_VALIDACAO_INCM), false, false);
        });
    }

    private Optional<Criterio> criterioInterno() {
        return switch (this) {
            case PUBLICADOS -> Optional.of(new Criterio(Set.of(EstadoProcesso.PUBLICADO), false, false));
            // Fila da DGT: sem EM_SUBMISSAO, que ainda não chegou à DGT, nem os validados que estão com a PCM.
            case EM_VALIDACAO -> Optional.of(new Criterio(Set.of(EstadoProcesso.ENVIADO_DGT,
                    EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.VALIDADO_DGT), false, false, true));
            case EM_CURSO, PARA_CORRECAO -> Optional.empty();
        };
    }
}
