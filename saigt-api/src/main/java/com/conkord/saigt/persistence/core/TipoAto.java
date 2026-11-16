package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/** Tipos de ato do DR. Os identificadores INCM por série são comparados com `atos_autorizados` do token. */
@Entity
@Table(name = "tipo_ato")
@Check(constraints = "incm_act_type_id_serie_1 IS NOT NULL OR incm_act_type_id_serie_2 IS NOT NULL")
@Getter
@NoArgsConstructor
public class TipoAto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 10)
    private String codigo;

    @Setter
    @Column(nullable = false, length = 200)
    private String nome;

    // ActTypeId da INCM na 1.ª série (preenchido = aplica-se à 1.ª série); nunca igual ao da 2.ª.
    @Setter
    @Column(name = "incm_act_type_id_serie_1")
    private Long incmActTypeIdSerie1;

    @Setter
    @Column(name = "incm_act_type_id_serie_2")
    private Long incmActTypeIdSerie2;

    @Setter
    @Column(nullable = false)
    private int ordem;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    public TipoAto(String codigo, String nome, int ordem) {
        this.codigo = codigo;
        this.nome = nome;
        this.ordem = ordem;
    }
}
