package com.conkord.saigt.persistence.operacional;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
/**
 * Estado de uma tentativa (`processo.estado`) e máquina de estados do BL-PROC-004 — fonte única das
 * transições permitidas e dos conjuntos derivados. Gravado por nome; sem `CHECK` na BD, a garantia é da
 * aplicação.
 */
public enum EstadoProcesso {
    EM_SUBMISSAO,
    ENVIADO_DGT,
    EM_VALIDACAO_DGT,
    VALIDADO_DGT,
    REJEITADO_DGT,
    ENVIADO_INCM,
    EM_VALIDACAO_INCM,
    PUBLICADO,
    ANULADO,
    ANULADO_INCM;

    /** Fim da linha; o Procedimento continua, se for o caso, numa nova tentativa. */
    public static final Set<EstadoProcesso> TERMINAIS = Set.copyOf(EnumSet.of(PUBLICADO, ANULADO));

    public static final Set<EstadoProcesso> ANULADOS = Set.copyOf(EnumSet.of(ANULADO, ANULADO_INCM));

    /** `REJEITADO_DGT` e `ANULADO_INCM` corrigem-se na mesma tentativa, por isso não entram. */
    public static final Set<EstadoProcesso> PERMITEM_NOVA_TENTATIVA = Set.copyOf(EnumSet.of(ANULADO));

    /** Formulário editável pela entidade e submissão permitida — a origem de `ENVIADO_DGT`. */
    public static final Set<EstadoProcesso> EDITAVEIS = Set.copyOf(EnumSet.of(EM_SUBMISSAO));

    /** Origens da anulação manual pelo `MASTER`: tudo o que ainda não foi enviado à INCM. */
    public static final Set<EstadoProcesso> ANULAVEIS = Set.copyOf(
            EnumSet.of(EM_SUBMISSAO, ENVIADO_DGT, EM_VALIDACAO_DGT, VALIDADO_DGT));

    private static final Map<EstadoProcesso, Set<EstadoProcesso>> TRANSICOES = new EnumMap<>(EstadoProcesso.class);

    static {
        TRANSICOES.put(EM_SUBMISSAO, EnumSet.of(ENVIADO_DGT, ANULADO));
        TRANSICOES.put(ENVIADO_DGT, EnumSet.of(EM_SUBMISSAO, EM_VALIDACAO_DGT, ANULADO));
        TRANSICOES.put(EM_VALIDACAO_DGT, EnumSet.of(VALIDADO_DGT, REJEITADO_DGT, ANULADO));
        TRANSICOES.put(REJEITADO_DGT, EnumSet.of(EM_SUBMISSAO));
        TRANSICOES.put(VALIDADO_DGT, EnumSet.of(ENVIADO_INCM, ANULADO));
        TRANSICOES.put(ENVIADO_INCM, EnumSet.of(EM_VALIDACAO_INCM, ANULADO_INCM));
        TRANSICOES.put(EM_VALIDACAO_INCM, EnumSet.of(PUBLICADO, ANULADO_INCM));
        TRANSICOES.put(ANULADO_INCM, EnumSet.of(EM_SUBMISSAO));
        for (EstadoProcesso terminal : TERMINAIS) {
            TRANSICOES.put(terminal, EnumSet.noneOf(EstadoProcesso.class));
        }
    }
    public boolean podeTransitarPara(EstadoProcesso destino) {
        return TRANSICOES.get(this).contains(destino);
    }
    public boolean isTerminal() {
        return TERMINAIS.contains(this);
    }
}
