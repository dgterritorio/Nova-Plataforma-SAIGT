package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
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
import java.util.UUID;

/** Observações de um item documental do Processo (DB-DOC-004): justificam um item sem ficheiro. */
@Entity
@Table(name = "processo_documento_observacao")
@Getter
@NoArgsConstructor
public class ProcessoDocumentoObservacao {

    @EmbeddedId
    private Id id;

    @MapsId("processoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    @MapsId("documentoConfigId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_config_id", nullable = false)
    private DocumentoConfig documentoConfig;

    @Setter
    @Column(name = "observacoes", nullable = false, columnDefinition = "TEXT")
    private String observacoes;

    public ProcessoDocumentoObservacao(Processo processo, DocumentoConfig documentoConfig, String observacoes) {
        this.id = new Id(processo.getId(), documentoConfig.getId());
        this.processo = processo;
        this.documentoConfig = documentoConfig;
        this.observacoes = observacoes;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "processo_id", nullable = false)
        private UUID processoId;

        @Column(name = "documento_config_id", nullable = false)
        private Long documentoConfigId;

        public Id(UUID processoId, Long documentoConfigId) {
            this.processoId = processoId;
            this.documentoConfigId = documentoConfigId;
        }
    }
}
