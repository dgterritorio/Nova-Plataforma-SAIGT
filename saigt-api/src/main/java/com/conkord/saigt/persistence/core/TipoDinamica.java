package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tipo_dinamica")
@Getter
@NoArgsConstructor
public class TipoDinamica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Setter
    @Column(nullable = false, length = 200)
    private String nome;

    // Segmento Dinâmica do IDUS (2 algarismos): o CodProcedimento do SNIT, não o ID. Só o seed o preenche.
    @Column(name = "codigo_idus", updatable = false, length = 2)
    private String codigoIdus;

    // ID (não o CodProcedimento) do SNIT, enviado no procedureId do registo da publicação.
    @Column(name = "snit_codigo", updatable = false, length = 20)
    private String snitCodigo;

    // Código da PCGT (GET /api/tipos-*), enviado como texto com o zero à esquerda no pedido de criação do processo.
    @Column(name = "pcgt_codigo", updatable = false, length = 20)
    private String pcgtCodigo;

    // Inicia-se sem escolher primeiro um IGT. Só o seed o preenche; a edição é do backoffice.
    @ColumnDefault("false")
    @Column(name = "inicio_sem_igt", nullable = false, updatable = false)
    private boolean inicioSemIgt;

    // O `descritivo` das Dinâmicas deste tipo é a Designação e não se repete no tipo. Só o seed o preenche.
    @ColumnDefault("false")
    @Column(name = "designacao_unica", nullable = false, updatable = false)
    private boolean designacaoUnica;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    public TipoDinamica(String codigo, String nome) {
        this.codigo = codigo;
        this.nome = nome;
    }
}
