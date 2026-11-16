package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Ficha de metadados (MOTU) do processo, gravada como rascunho e exigida na submissão (EF v1.5 §3.6). */
@Entity
@Table(name = "processo_metadados")
@Getter
@NoArgsConstructor
public class ProcessoMetadados {

    @Id
    @Column(name = "processo_id", nullable = false, updatable = false)
    private UUID processoId;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String dados;

    @Setter
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public ProcessoMetadados(UUID processoId, String dados, Instant atualizadoEm) {
        this.processoId = processoId;
        this.dados = dados;
        this.atualizadoEm = atualizadoEm;
    }
}
