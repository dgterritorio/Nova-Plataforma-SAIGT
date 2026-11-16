package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * `perfilInterno` só se aplica a internos (AD_DGT): nasce `VISITANTE` no 1.º login e só o backoffice o muda;
 * nos externos (INCM) o perfil resolve-se da entidade escolhida e não é persistido.
 * Uma conta por pessoa e por origem: a mesma pessoa com acesso INCM e AD tem duas linhas. Sem desativação nem remoção:
 * o acesso retira-se na INCM ou no AD e pela revogação das sessões.
 */
@Entity
@Table(name = "utilizador", uniqueConstraints = @UniqueConstraint(name = "uq_utilizador_email_origem",
        columnNames = {"email", "origem_autenticacao"}))
@Check(constraints = "(origem_autenticacao = 'AD_DGT' AND perfil_interno IS NOT NULL) "
        + "OR (origem_autenticacao = 'INCM' AND perfil_interno IS NULL)")
@Check(name = "ck_utilizador_perfil_interno_valor",
        constraints = "perfil_interno IS NULL OR perfil_interno IN ('MASTER', 'TECNICO', 'VISITANTE')")
@Getter
@NoArgsConstructor
public class Utilizador extends BaseEntity {

    // INCM: o nome devolvido pela INCM, refrescado a cada login. AD_DGT: o nome de autenticação no AD, nunca alterado.
    @Setter
    @Column(length = 200)
    private String nome;

    // INCM: obrigatório, refrescado a cada login. AD_DGT: opcional, o AD não o devolve.
    @Setter
    @Column(length = 200)
    private String email;

    // Só para utilizadores externos — a INCM devolve-o, o AD da DGT não.
    @Setter
    @Column(length = 200)
    private String cargo;

    // INCM | AD_DGT — fixado no primeiro login. Default AD_DGT (LDAP); o login INCM passa-o explicitamente.
    @Column(name = "origem_autenticacao", nullable = false, length = 10)
    private String origemAutenticacao = "AD_DGT";

    // Obrigatório para AD_DGT, vazio para INCM (ver @Check). VISITANTE na criação; depois só o backoffice o altera.
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil_interno", length = 20)
    private PerfilInterno perfilInterno;

    // Só AD_DGT: o telefone e a morada são do próprio, editados no Perfil; o login nunca os toca (o AD não os devolve).
    @Setter
    @Column(length = 30)
    private String telefone;

    @Setter
    @Column(length = 300)
    private String morada;

    public Utilizador(String nome, String email) {
        this.nome = nome;
        this.email = email;
    }

    /** Primeiro login via INCM. */
    public Utilizador(String nome, String email, String origemAutenticacao) {
        this(nome, email);
        this.origemAutenticacao = origemAutenticacao;
    }
}
