package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tipo_procedimento")
@Getter
@NoArgsConstructor
public class TipoProcedimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    // Um código do seed do PDM (ABERTURA_DISCUSSAO_PUBLICA_DL10) não cabe em 30.
    @Setter
    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Setter
    @Column(nullable = false, length = 200)
    private String nome;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "snit_codigo", updatable = false, length = 20)
    private String snitCodigo;

    public TipoProcedimento(String codigo, String nome) {
        this.codigo = codigo;
        this.nome = nome;
    }
}
