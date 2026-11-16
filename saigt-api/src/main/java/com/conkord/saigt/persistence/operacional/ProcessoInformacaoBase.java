package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/** Um elemento da Informação Base do Processo que a DGT tem de rever antes de validar. */
@Entity
@Table(name = "processo_informacao_base",
        uniqueConstraints = @UniqueConstraint(name = "uq_processo_informacao_base_processo_elemento",
                columnNames = {"processo_id", "elemento"}))
@Check(constraints = "elemento IN ('DELIBERACAO', 'TEXTO_ATO') "
        + "AND estado_validacao IN ('POR_VALIDAR', 'VALIDADO', 'REJEITADO')")
@Getter
@NoArgsConstructor
public class ProcessoInformacaoBase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ElementoInformacaoBase elemento;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacao", nullable = false, length = 20)
    private EstadoValidacao estadoValidacao = EstadoValidacao.POR_VALIDAR;

    public ProcessoInformacaoBase(Processo processo, ElementoInformacaoBase elemento) {
        this.processo = processo;
        this.elemento = elemento;
    }
}
