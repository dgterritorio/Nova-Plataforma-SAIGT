package com.conkord.saigt.persistence.regras;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
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
import org.hibernate.annotations.Check;

/**
 * Regras de Planos, Programas e SRUP (TR-083, OI-417). O motor orquestra, não decide: as regras são dados. Aplica-se a uma dinâmica
 * ou a um procedimento, exatamente um dos dois; no máximo uma por Procedimento.
 */
@Entity
@Table(name = "regra",
        uniqueConstraints = @UniqueConstraint(name = "uq_regra_procedimento_config",
                columnNames = {"instrumento_procedimento_config_id"}))
@Check(constraints = "(instrumento_dinamica_config_id IS NOT NULL) != (instrumento_procedimento_config_id IS NOT NULL)")
@Getter
@NoArgsConstructor
public class Regra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrumento_dinamica_config_id")
    private InstrumentoDinamicaConfig instrumentoDinamicaConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrumento_procedimento_config_id")
    private InstrumentoProcedimentoConfig instrumentoProcedimentoConfig;

    // AND | OR — combina os grupos de condicao entre si.
    @Setter
    @Column(nullable = false, length = 3)
    private String operador = "AND";

    // false = aditiva ao sequenciamento por omissão, true = substitutiva.
    @Setter
    @Column(nullable = false)
    private boolean substituiSequenciamento = false;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    @Setter
    @Column(length = 300)
    private String descricao;

    public static Regra paraDinamica(InstrumentoDinamicaConfig instrumentoDinamicaConfig) {
        Regra regra = new Regra();
        regra.instrumentoDinamicaConfig = instrumentoDinamicaConfig;
        return regra;
    }

    public static Regra paraProcedimento(InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        Regra regra = new Regra();
        regra.instrumentoProcedimentoConfig = instrumentoProcedimentoConfig;
        return regra;
    }
}
