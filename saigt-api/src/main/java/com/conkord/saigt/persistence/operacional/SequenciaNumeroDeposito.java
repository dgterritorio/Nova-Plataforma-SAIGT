package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.SerieNumeroDeposito;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Último sequencial do n.º de depósito atribuído por série e ano; só se altera por {@link SequenciaNumeroDepositoRepository}. */
@Entity
@Table(name = "sequencia_numero_deposito")
@Getter
@NoArgsConstructor
public class SequenciaNumeroDeposito {

    @EmbeddedId
    private Id id;

    @Column(name = "ultimo_sequencial", nullable = false)
    private int ultimoSequencial;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Enumerated(EnumType.STRING)
        @Column(name = "serie", nullable = false, length = 10)
        private SerieNumeroDeposito serie;

        @Column(name = "ano", nullable = false)
        private int ano;

        public Id(SerieNumeroDeposito serie, int ano) {
            this.serie = serie;
            this.ano = ano;
        }
    }
}
