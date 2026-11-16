package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** O IDUS da Dinâmica, gerado uma só vez na entrada em vigor. */
@Entity
@Table(name = "idus_dinamica",
        uniqueConstraints = @UniqueConstraint(name = "uq_idus_dinamica_idus", columnNames = "idus"))
@Getter
@NoArgsConstructor
public class IdusDinamica {

    @Id
    @Column(name = "dinamica_id", nullable = false, updatable = false)
    private UUID dinamicaId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "dinamica_id")
    private Dinamica dinamica;

    @Column(nullable = false, updatable = false, length = 50)
    private String idus;

    @Column(name = "id_igt_snit", nullable = false, updatable = false, length = 20)
    private String idIgtSnit;

    @Column(name = "ordem_snit", nullable = false, updatable = false, length = 10)
    private String ordemSnit;

    @Column(name = "gerado_em", nullable = false, updatable = false)
    private Instant geradoEm;

    @Builder
    private IdusDinamica(Dinamica dinamica, String idus, String idIgtSnit, String ordemSnit, Instant geradoEm) {
        this.dinamica = dinamica;
        this.idus = idus;
        this.idIgtSnit = idIgtSnit;
        this.ordemSnit = ordemSnit;
        this.geradoEm = geradoEm;
    }
}
