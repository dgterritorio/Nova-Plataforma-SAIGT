package com.conkord.saigt.persistence.core;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "snit_referencia",
        uniqueConstraints = @UniqueConstraint(name = "uq_snit_referencia_lista_codigo",
                columnNames = {"lista", "codigo"}))
@Check(name = "ck_snit_referencia_lista", constraints = "lista IN ('INSTRUMENT_TYPES', 'PROCEDURES', "
        + "'PROCEDURE_TYPES', 'CLASSES', 'NUT2', 'MUNICIPALITIES')")
@Getter
@NoArgsConstructor
public class SnitReferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private ListaReferenciaSnit lista;

    @Column(nullable = false, updatable = false, length = 50)
    private String codigo;

    @Setter
    @Column(nullable = false, length = 500)
    private String designacao;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false, columnDefinition = "jsonb")
    private String dadosOrigem;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    @Setter
    @Column(name = "sincronizado_em", nullable = false)
    private Instant sincronizadoEm;

    public SnitReferencia(ListaReferenciaSnit lista, String codigo, String designacao, String dadosOrigem,
                          Instant sincronizadoEm) {
        this.lista = lista;
        this.codigo = codigo;
        this.designacao = designacao;
        this.dadosOrigem = dadosOrigem;
        this.sincronizadoEm = sincronizadoEm;
    }
}
