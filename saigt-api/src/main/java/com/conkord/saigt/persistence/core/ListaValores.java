package com.conkord.saigt.persistence.core;

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

@Entity
@Table(name = "lista_valores",
        uniqueConstraints = @UniqueConstraint(name = "uq_lista_valores_categoria_codigo",
                columnNames = {"tipo", "codigo"}))
@Getter
@NoArgsConstructor
public class ListaValores {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    // Ex: MOTIVO_REJEICAO_DGT, SERIE_DR.
    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String codigo;

    // Valor do nível acima (Classe → Domínio, Tipologia → Classe); identidade do valor, sem setter.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pai_id")
    private ListaValores pai;

    @Setter
    @Column(nullable = false, length = 300)
    private String descricao;

    @Setter
    @Column(nullable = false)
    private int ordem;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    public ListaValores(String tipo, String codigo, String descricao, int ordem) {
        this(tipo, codigo, descricao, ordem, null);
    }

    public ListaValores(String tipo, String codigo, String descricao, int ordem, ListaValores pai) {
        this.tipo = tipo;
        this.codigo = codigo;
        this.descricao = descricao;
        this.ordem = ordem;
        this.pai = pai;
    }
}
