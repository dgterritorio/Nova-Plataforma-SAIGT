package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Ficheiro fixado no manifesto: lê-se sempre por `versaoObjeto`, nunca pelo estado atual do documento. */
@Entity
@Table(name = "deposito_snit_manifesto_linha")
@Getter
@NoArgsConstructor
public class DepositoSnitManifestoLinha {

    @EmbeddedId
    private Id id;

    @Column(name = "chave_objeto", nullable = false, updatable = false, length = 500)
    private String chaveObjeto;

    @Column(name = "versao_objeto", nullable = false, updatable = false, length = 100)
    private String versaoObjeto;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private long tamanhoBytes;

    @Column(name = "hash_sha256", nullable = false, updatable = false, length = 64)
    private String hashSha256;

    @Column(name = "comunicado_um_a_um", nullable = false, updatable = false)
    private boolean comunicadoUmAUm;

    public DepositoSnitManifestoLinha(UUID manifestoId, ProcessoDocumento documento, boolean comunicadoUmAUm) {
        this.id = new Id(manifestoId, documento.getId());
        this.chaveObjeto = documento.getCaminhoFicheiro();
        this.versaoObjeto = documento.getVersaoObjeto();
        this.tamanhoBytes = documento.getTamanhoBytes();
        this.hashSha256 = documento.getHashSha256();
        this.comunicadoUmAUm = comunicadoUmAUm;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "manifesto_id", nullable = false)
        private UUID manifestoId;

        @Column(name = "processo_documento_id", nullable = false)
        private UUID processoDocumentoId;

        public Id(UUID manifestoId, UUID processoDocumentoId) {
            this.manifestoId = manifestoId;
            this.processoDocumentoId = processoDocumentoId;
        }
    }
}
