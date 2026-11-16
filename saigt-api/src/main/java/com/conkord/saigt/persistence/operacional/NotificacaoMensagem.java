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

@Entity
@Table(name = "notificacao_mensagem")
@Getter
@NoArgsConstructor
public class NotificacaoMensagem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notificacao_id", nullable = false, updatable = false)
    private Notificacao notificacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false, updatable = false)
    private Utilizador autor;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String texto;

    public NotificacaoMensagem(Notificacao notificacao, Utilizador autor, String texto) {
        this.notificacao = notificacao;
        this.autor = autor;
        this.texto = texto;
    }
}
