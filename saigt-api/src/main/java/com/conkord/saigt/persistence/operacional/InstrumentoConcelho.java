package com.conkord.saigt.persistence.operacional;

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

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "instrumento_concelho")
@Getter
@NoArgsConstructor
public class InstrumentoConcelho {

    @EmbeddedId
    private Id id;

    @MapsId("instrumentoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_id", nullable = false)
    private Instrumento instrumento;

    public InstrumentoConcelho(Instrumento instrumento, String municipioCodigo) {
        this.id = new Id(instrumento.getId(), municipioCodigo);
        this.instrumento = instrumento;
    }

    public String getMunicipioCodigo() {
        return id.getMunicipioCodigo();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "instrumento_id", nullable = false)
        private UUID instrumentoId;

        @Column(name = "municipio_codigo", nullable = false, length = 50)
        private String municipioCodigo;

        public Id(UUID instrumentoId, String municipioCodigo) {
            this.instrumentoId = instrumentoId;
            this.municipioCodigo = municipioCodigo;
        }
    }
}
