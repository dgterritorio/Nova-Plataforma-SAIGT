package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.ColumnDefault;

/** Dinâmicas suportadas por cada tipo de instrumento. */
@Entity
@Table(name = "instrumento_dinamica_config",
        uniqueConstraints = @UniqueConstraint(name = "uq_instrumento_dinamica_config",
                columnNames = {"tipo_instrumento_id", "tipo_dinamica_id"}))
@Check(constraints = "associacao_instrumentos IN ('UM', 'UM_OU_MAIS', 'OPCIONAL')")@Getter
@NoArgsConstructor
public class InstrumentoDinamicaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_instrumento_id", nullable = false)
    private TipoInstrumento tipoInstrumento;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_dinamica_id", nullable = false)
    private TipoDinamica tipoDinamica;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    @Setter
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'UM'")
    @Column(name = "associacao_instrumentos", nullable = false, length = 12)
    private AssociacaoInstrumentos associacaoInstrumentos = AssociacaoInstrumentos.UM;

    /** O formulário pede o «Diploma que corrige ou retifica». */
    @Setter
    @ColumnDefault("false")
    @Column(name = "pede_diploma_corrigido", nullable = false)
    private boolean pedeDiplomaCorrigido;

    /** `TEXTO_INTEGRAL` ou `EXTRATO` imposto ao ato; nulo deixa a escolha ao formulário. */
    @Setter
    @Column(name = "tipo_informacao_fixo", length = 20)
    private String tipoInformacaoFixo;

    // Só a configuração (seed ou backoffice) o liga: a Dinâmica admite associar o processo a outra Dinâmica.
    @ColumnDefault("false")
    @Column(name = "admite_associacao_igt", nullable = false)
    private boolean admiteAssociacaoIgt;

    public InstrumentoDinamicaConfig(TipoInstrumento tipoInstrumento, TipoDinamica tipoDinamica) {
        this.tipoInstrumento = tipoInstrumento;
        this.tipoDinamica = tipoDinamica;
    }
}
