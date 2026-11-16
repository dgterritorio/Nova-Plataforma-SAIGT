package com.conkord.saigt.persistence.regras;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * `avaliador` é vocabulário fixo implementado em código: OCORRENCIA_EM_ESTADO, OCORRENCIA_NAO_EXISTE,
 * DINAMICA_BLOQUEADA_LEGADO.
 */
@Entity
@Table(name = "condicao")
@Getter
@NoArgsConstructor
public class Condicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regra_id", nullable = false)
    private Regra regra;

    @Setter
    @Column(nullable = false, length = 40)
    private String avaliador;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String parametros;

    // Condições do mesmo grupo combinam com AND; os grupos entre si com regra.operador.
    @Setter
    @Column(nullable = false)
    private short grupo = 1;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    public Condicao(Regra regra, String avaliador, String parametros) {
        this.regra = regra;
        this.avaliador = avaliador;
        this.parametros = parametros;
    }
}
