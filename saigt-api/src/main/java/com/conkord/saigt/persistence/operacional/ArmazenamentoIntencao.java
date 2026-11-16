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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Só inserção e apagamento: a linha existe até a limpeza noturna reconciliar a chave com a base de dados. */
@Entity
@Table(name = "armazenamento_intencao")
@Check(constraints = "operacao IN ('GRAVACAO', 'COPIA', 'APAGAMENTO')")
@Getter
@NoArgsConstructor
public class ArmazenamentoIntencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "chave_objeto", nullable = false, updatable = false, length = 500)
    private String chaveObjeto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private OperacaoArmazenamento operacao;

    @CreationTimestamp
    @Column(name = "registada_em", nullable = false, updatable = false)
    private Instant registadaEm;

    public ArmazenamentoIntencao(String chaveObjeto, OperacaoArmazenamento operacao) {
        this.chaveObjeto = chaveObjeto;
        this.operacao = operacao;
    }
}
