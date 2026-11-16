package com.conkord.saigt.persistence.configuracao;

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
import com.conkord.saigt.persistence.core.TipoEntidade;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Por instrumento_procedimento_config, não por tipo_procedimento: a mesma ação pode ser permitida num
 * Instrumento e não noutro. É por tipo de entidade: os utilizadores internos não têm linhas próprias.
 */
@Entity
@Table(name = "permissao_acao",
        uniqueConstraints = @UniqueConstraint(name = "uq_permissao_acao",
                columnNames = {"instrumento_procedimento_config_id", "tipo_entidade"}))
@Check(constraints = "tipo_entidade IN ('CM', 'CCDR_DR', 'AM', 'CIM', 'PCM', 'SEC_ESTADO', 'DGT', 'ICNF', 'APA', 'DGPC', 'IP')")
@Getter
@NoArgsConstructor
public class PermissaoAcao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entidade", nullable = false, length = 30)
    private TipoEntidade tipoEntidade;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_procedimento_config_id", nullable = false)
    private InstrumentoProcedimentoConfig instrumentoProcedimentoConfig;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    public PermissaoAcao(TipoEntidade tipoEntidade, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        this.tipoEntidade = tipoEntidade;
        this.instrumentoProcedimentoConfig = instrumentoProcedimentoConfig;
    }
}
