package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "metadados_motu")
@Check(constraints = "(tipo = 'SERIE' AND processo_documento_id IS NULL AND opcional = FALSE) "
        + "OR (tipo = 'CDG' AND (opcional = TRUE OR processo_documento_id IS NOT NULL))")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetadadosMotu extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false, updatable = false)
    private Processo processo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private TipoMetadadosMotu tipo;

    @Column(nullable = false, updatable = false)
    private boolean opcional = false;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_documento_id")
    private ProcessoDocumento processoDocumento;

    @Setter
    @Column(nullable = false)
    private boolean completo = false;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String dados = "{}";

    private MetadadosMotu(Processo processo, TipoMetadadosMotu tipo, ProcessoDocumento processoDocumento) {
        this(processo, tipo, processoDocumento, false);
    }

    private MetadadosMotu(Processo processo, TipoMetadadosMotu tipo, ProcessoDocumento processoDocumento,
                          boolean opcional) {
        this.opcional = opcional;
        this.processo = processo;
        this.tipo = tipo;
        this.processoDocumento = processoDocumento;
    }

    public static MetadadosMotu serie(Processo processo) {
        return new MetadadosMotu(processo, TipoMetadadosMotu.SERIE, null);
    }

    public static MetadadosMotu conjunto(Processo processo, ProcessoDocumento documento) {
        return new MetadadosMotu(processo, TipoMetadadosMotu.CDG, documento);
    }

    /** Ficha de Conjunto opcional: nasce sem ZIP e incompleta, e liga-se a uma peça gráfica mais tarde. */
    public static MetadadosMotu conjuntoOpcional(Processo processo, String dados) {
        MetadadosMotu ficha = new MetadadosMotu(processo, TipoMetadadosMotu.CDG, null, true);
        ficha.dados = dados;
        return ficha;
    }
}
