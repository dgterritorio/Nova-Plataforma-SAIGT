package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "notificacao_documento")
@Getter
@NoArgsConstructor
public class NotificacaoDocumento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notificacao_id", nullable = false, updatable = false)
    private Notificacao notificacao;

    @Column(name = "nome_ficheiro", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String nomeFicheiro;

    @Column(name = "caminho_ficheiro", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String caminhoFicheiro;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private Long tamanhoBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false, updatable = false)
    private Utilizador criadoPor;

    public NotificacaoDocumento(Notificacao notificacao, String nomeFicheiro, String caminhoFicheiro,
                                Long tamanhoBytes, Utilizador criadoPor) {
        this.notificacao = notificacao;
        this.nomeFicheiro = nomeFicheiro;
        this.caminhoFicheiro = caminhoFicheiro;
        this.tamanhoBytes = tamanhoBytes;
        this.criadoPor = criadoPor;
    }

    public NotificacaoDocumento(UUID id, Notificacao notificacao, String nomeFicheiro, String caminhoFicheiro,
                                Long tamanhoBytes, Utilizador criadoPor) {
        this(notificacao, nomeFicheiro, caminhoFicheiro, tamanhoBytes, criadoPor);
        atribuirId(id);
    }
}
