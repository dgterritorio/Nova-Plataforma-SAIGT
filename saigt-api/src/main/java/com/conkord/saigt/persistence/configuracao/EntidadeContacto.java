package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.Utilizador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Contactos institucionais curados pela DGT, distintos do responsável do termo de adesão (vindo da INCM):
 * fora do login e da autorização, e nenhum refresh da INCM os escreve.
 * No máximo um `principal` ativo por entidade: verificado em {@code EntidadeContactoService} e garantido pelo índice
 * único parcial `uq_entidade_contacto_principal_ativo` (só na migração; o H2 dos testes não o tem).
 */
@Entity
@Table(name = "entidade_contacto",
        uniqueConstraints = @UniqueConstraint(name = "uq_entidade_contacto", columnNames = {"entidade_id", "email"}))
@Getter
@NoArgsConstructor
public class EntidadeContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entidade_id", nullable = false)
    private Entidade entidade;

    @Setter
    @Column(nullable = false, length = 200)
    private String email;

    @Setter
    @Column(length = 200)
    private String descricao;

    @Setter
    @Column(length = 20)
    private String telefone;

    @Setter
    @Column(nullable = false)
    private boolean principal = false;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Utilizador criadoPor;

    public EntidadeContacto(Entidade entidade, String email, Utilizador criadoPor) {
        this.entidade = entidade;
        this.email = email;
        this.criadoPor = criadoPor;
    }
}
