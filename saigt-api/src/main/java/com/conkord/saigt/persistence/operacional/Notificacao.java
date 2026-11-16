package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.BaseEntity;
import com.conkord.saigt.persistence.core.Entidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notificacao")
@Getter
@NoArgsConstructor
public class Notificacao extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_id")
    private Processo processo;

    @Setter
    @Column(name = "id_igt_snit", columnDefinition = "TEXT")
    private String idIgtSnit;

    @Setter
    @Column(name = "descricao_igt", columnDefinition = "TEXT")
    private String descricaoIgt;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizador_id")
    private Utilizador utilizador;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entidade_id")
    private Entidade entidade;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil_destino", length = 20)
    private PerfilInterno perfilDestino;

    @Setter
    @Column(name = "lote_id")
    private UUID loteId;

    @Setter
    private Instant prazo;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNotificacao estado = EstadoNotificacao.ATIVA;

    @Setter
    @Column(name = "canal_email", nullable = false)
    private boolean canalEmail = false;

    @Setter
    @Column(name = "email_enviado_em")
    private Instant emailEnviadoEm;

    /** Vazio quando a notificação é gerada pela plataforma. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por", updatable = false)
    private Utilizador criadoPor;

    @Setter
    @Column(name = "lido_em")
    private Instant lidoEm;

    @Setter
    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    public Notificacao(TipoNotificacao tipo, String titulo, String texto, Utilizador criadoPor) {
        this.tipo = tipo;
        this.titulo = titulo;
        this.texto = texto;
        this.criadoPor = criadoPor;
    }

    /** Fecha a notificação: a resposta do Master pela mensagem e a transferência de autoria a pedido resolvem-na. */
    public void resolver(Instant agora) {
        this.estado = EstadoNotificacao.RESOLVIDA;
        this.resolvidoEm = agora;
    }

    /** O autor de um pedido de apoio resolvido sem resposta suficiente volta a abri-lo. */
    public void reabrir() {
        this.estado = EstadoNotificacao.ATIVA;
        this.resolvidoEm = null;
    }
}
