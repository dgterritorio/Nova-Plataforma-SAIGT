package com.conkord.saigt.persistence.relatorio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class DashboardPreferenciaId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "utilizador_id", nullable = false)
    private UUID utilizadorId;

    @Column(name = "kpi_codigo", nullable = false, length = 60)
    private String kpiCodigo;

    public DashboardPreferenciaId(UUID utilizadorId, String kpiCodigo) {
        this.utilizadorId = utilizadorId;
        this.kpiCodigo = kpiCodigo;
    }
}
