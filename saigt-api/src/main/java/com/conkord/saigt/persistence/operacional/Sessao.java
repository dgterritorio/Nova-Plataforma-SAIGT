package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * PK natural `jti`: o do token de pré-autenticação em `PRE_AUTENTICACAO`, o do refresh token em `ATIVA`.
 * Rotação e escolha de entidade revogam a linha antiga e criam outra, nunca a sobrescrevem.
 * Implementa Persistable porque a PK é atribuída: sem isso, Spring Data usaria merge() e um jti
 * duplicado passaria por update silencioso.
 */
@Entity
@Table(name = "sessao")
@Check(constraints = "tipo <> 'PRE_AUTENTICACAO' OR contexto_sessao IS NOT NULL")
@Getter
@NoArgsConstructor
public class Sessao implements Persistable<String> {

    @Transient
    private boolean novo = true;

    @Id
    @Column(length = 100, updatable = false)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilizador_id", nullable = false)
    private Utilizador utilizador;

    // PRE_AUTENTICACAO (login validado, entidade por escolher) | ATIVA. PRE_AUTENTICACAO só via
    // paraPreAutenticacao.
    @Column(nullable = false, length = 20)
    private String tipo = "ATIVA";

    // Contexto de adesões INCM do login — obrigatório em PRE_AUTENTICACAO, vazio para a DGT (ver @Check).
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto_sessao", columnDefinition = "jsonb")
    private String contextoSessao;

    @CreationTimestamp
    @Column(name = "emitido_em", nullable = false, updatable = false)
    private Instant emitidoEm;

    @Setter
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    // Vazio = em vigor. Preenchido = logout (próprio ou forçado) ou escolha de entidade concluída.
    @Setter
    @Column(name = "revogado_em")
    private Instant revogadoEm;

    public Sessao(String jti, Utilizador utilizador, Instant expiraEm) {
        this.jti = jti;
        this.utilizador = utilizador;
        this.expiraEm = expiraEm;
    }

    public static Sessao paraPreAutenticacao(String jti, Utilizador utilizador, Instant expiraEm, String contextoSessao) {
        Sessao sessao = new Sessao(jti, utilizador, expiraEm);
        sessao.tipo = "PRE_AUTENTICACAO";
        sessao.contextoSessao = contextoSessao;
        return sessao;
    }

    @Override
    public String getId() {
        return jti;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostPersist
    @PostLoad
    void marcarComoExistente() {
        novo = false;
    }
}
