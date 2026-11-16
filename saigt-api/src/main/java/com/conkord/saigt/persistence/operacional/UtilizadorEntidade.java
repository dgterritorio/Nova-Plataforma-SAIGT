package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.Entidade;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Projeção só de leitura da última `UserEnrollmentAgreementList` da INCM, refeita a cada login da própria
 * pessoa. Serve para listar utilizadores por entidade e validar owner — nunca para autorização, que continua
 * a vir só do token. PK composta, por isso não estende `BaseEntity` (como `Sessao`).
 */
@Entity
@Table(name = "utilizador_entidade")
@Getter
@NoArgsConstructor
public class UtilizadorEntidade {

    @EmbeddedId
    private Id id;

    @MapsId("utilizadorId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilizador_id", nullable = false)
    private Utilizador utilizador;

    @MapsId("entidadeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entidade_id", nullable = false)
    private Entidade entidade;

    // Último `ValidateUser` que confirmou o vínculo.
    @Setter
    @Column(name = "sincronizado_em", nullable = false)
    private Instant sincronizadoEm;

    public UtilizadorEntidade(Utilizador utilizador, Entidade entidade, Instant sincronizadoEm) {
        this.id = new Id(utilizador.getId(), entidade.getId());
        this.utilizador = utilizador;
        this.entidade = entidade;
        this.sincronizadoEm = sincronizadoEm;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "utilizador_id", nullable = false)
        private UUID utilizadorId;

        @Column(name = "entidade_id", nullable = false)
        private Long entidadeId;

        public Id(UUID utilizadorId, Long entidadeId) {
            this.utilizadorId = utilizadorId;
            this.entidadeId = entidadeId;
        }
    }
}
