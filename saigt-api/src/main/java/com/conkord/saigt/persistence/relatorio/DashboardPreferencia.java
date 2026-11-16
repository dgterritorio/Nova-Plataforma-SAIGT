package com.conkord.saigt.persistence.relatorio;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** PK composta, por isso não estende `BaseEntity`. */
@Entity
@Table(name = "dashboard_preferencia", uniqueConstraints = @UniqueConstraint(name = "uq_dashboard_preferencia_ordem",
        columnNames = {"utilizador_id", "ordem"}))
@Getter
@NoArgsConstructor
public class DashboardPreferencia {

    @EmbeddedId
    private DashboardPreferenciaId id;

    @Column(name = "visivel", nullable = false)
    private boolean visivel;

    @Column(name = "ordem", nullable = false)
    private short ordem;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public DashboardPreferencia(UUID utilizadorId, String kpiCodigo, boolean visivel, short ordem, Instant atualizadoEm) {
        this.id = new DashboardPreferenciaId(utilizadorId, kpiCodigo);
        this.visivel = visivel;
        this.ordem = ordem;
        this.atualizadoEm = atualizadoEm;
    }

    public String getKpiCodigo() {
        return id.getKpiCodigo();
    }
}
