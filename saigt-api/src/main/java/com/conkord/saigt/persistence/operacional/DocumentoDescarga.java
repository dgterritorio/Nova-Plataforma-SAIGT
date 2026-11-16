package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Uma linha por descarga servida. Nunca guarda o ticket. */
@Entity
@Table(name = "documento_descarga")
@Getter
@NoArgsConstructor
public class DocumentoDescarga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ConsumidorDescarga consumidor;

    @Column(name = "processo_id", updatable = false)
    private UUID processoId;

    @Column(name = "processo_documento_id", updatable = false)
    private UUID processoDocumentoId;

    @Column(name = "notificacao_documento_id", updatable = false)
    private UUID notificacaoDocumentoId;

    @Column(name = "manifesto_id", updatable = false)
    private UUID manifestoId;

    @Column(name = "utilizador_id", updatable = false)
    private UUID utilizadorId;

    @Column(name = "documento_link_publico_id", updatable = false)
    private UUID documentoLinkPublicoId;

    @Setter
    @Column(length = 45)
    private String ip;

    @Column(name = "iniciada_em", nullable = false, updatable = false)
    private Instant iniciadaEm;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoDescarga resultado;

    @Setter
    @Column(name = "bytes_enviados", nullable = false)
    private long bytesEnviados;

    public DocumentoDescarga(ConsumidorDescarga consumidor, UUID processoId, UUID processoDocumentoId,
                             UUID utilizadorId, String ip, Instant iniciadaEm, ResultadoDescarga resultado) {
        this.consumidor = consumidor;
        this.processoId = processoId;
        this.processoDocumentoId = processoDocumentoId;
        this.utilizadorId = utilizadorId;
        this.ip = ip;
        this.iniciadaEm = iniciadaEm;
        this.resultado = resultado;
    }

    /** Descarga do SNIT: `processoDocumentoId` é nulo no ZIP da pasta de depósito; fica o manifesto pelo qual leu. */
    public static DocumentoDescarga porSnit(UUID processoId, UUID processoDocumentoId, UUID manifestoId, String ip,
                                            Instant iniciadaEm, ResultadoDescarga resultado) {
        DocumentoDescarga descarga = new DocumentoDescarga(ConsumidorDescarga.SNIT, processoId, processoDocumentoId,
                null, ip, iniciadaEm, resultado);
        descarga.manifestoId = manifestoId;
        return descarga;
    }

    /** Anexo de notificação descarregado por ticket: não tem processo nem `processo_documento`. */
    public static DocumentoDescarga porAnexoNotificacao(UUID notificacaoDocumentoId, UUID utilizadorId, String ip,
                                                        Instant iniciadaEm, ResultadoDescarga resultado) {
        DocumentoDescarga descarga = new DocumentoDescarga(ConsumidorDescarga.UTILIZADOR, null, null, utilizadorId, ip,
                iniciadaEm, resultado);
        descarga.notificacaoDocumentoId = notificacaoDocumentoId;
        return descarga;
    }

    /** Descarga por link público (INCM ou PCGT): fica o link usado, nunca o token. */
    public static DocumentoDescarga porLinkPublico(ConsumidorDescarga consumidor, UUID processoId,
                                                   UUID processoDocumentoId, UUID documentoLinkPublicoId, String ip,
                                                   Instant iniciadaEm, ResultadoDescarga resultado) {
        DocumentoDescarga descarga = new DocumentoDescarga(consumidor, processoId, processoDocumentoId, null, ip,
                iniciadaEm, resultado);
        descarga.documentoLinkPublicoId = documentoLinkPublicoId;
        return descarga;
    }
}
