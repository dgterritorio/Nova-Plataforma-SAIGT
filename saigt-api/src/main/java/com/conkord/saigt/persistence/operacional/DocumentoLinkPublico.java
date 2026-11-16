package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.UUID;

/**
 * Link público de um documento para a INCM ou a PCGT: só uma linha, sem cópia do objeto. Sem validade; só deixa de
 * servir quando é revogado, na substituição ou na remoção do documento.
 */
@Entity
@Table(name = "documento_link_publico")
@Check(constraints = "destino IN ('INCM', 'PCGT')")
@Getter
@NoArgsConstructor
public class DocumentoLinkPublico {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_documento_id", nullable = false, updatable = false)
    private ProcessoDocumento processoDocumento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private DestinoLinkPublico destino;

    @Column(nullable = false, updatable = false, unique = true)
    private UUID token;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_revogacao", length = 20)
    private MotivoRevogacaoLink motivoRevogacao;

    public DocumentoLinkPublico(ProcessoDocumento processoDocumento, DestinoLinkPublico destino, UUID token,
                                Instant criadoEm) {
        this.id = UUID.randomUUID();
        this.processoDocumento = processoDocumento;
        this.destino = destino;
        this.token = token;
        this.criadoEm = criadoEm;
    }

    public void revogar(MotivoRevogacaoLink motivo, Instant em) {
        this.revogadoEm = em;
        this.motivoRevogacao = motivo;
    }

    public boolean isAtivo() {
        return revogadoEm == null;
    }
}
