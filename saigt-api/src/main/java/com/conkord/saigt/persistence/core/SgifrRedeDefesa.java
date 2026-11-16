package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Redes de defesa do SGIFR. As que valem para cada programa leem-se dos indicadores, nunca do código da rede. Sem
 * `ativo`: retira-se uma rede da oferta pondo os três indicadores a falso.
 */
@Entity
@Table(name = "sgifr_rede_defesa",
        uniqueConstraints = @UniqueConstraint(name = "uq_sgifr_rede_defesa_codigo", columnNames = "codigo"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SgifrRedeDefesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(name = "aplica_pra", nullable = false)
    private boolean aplicaPra;

    @Column(name = "aplica_psa", nullable = false)
    private boolean aplicaPsa;

    @Column(nullable = false)
    private boolean publicavel;

    public SgifrRedeDefesa(String codigo, String nome, boolean aplicaPra, boolean aplicaPsa, boolean publicavel) {
        this.codigo = codigo;
        this.nome = nome;
        this.aplicaPra = aplicaPra;
        this.aplicaPsa = aplicaPsa;
        this.publicavel = publicavel;
    }
}
