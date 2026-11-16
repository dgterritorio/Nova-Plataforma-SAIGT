package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tipo_instrumento")
@Getter
@NoArgsConstructor
public class TipoInstrumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Setter
    @Column(nullable = false, length = 200)
    private String nome;

    @Setter
    @Column(nullable = false, length = 20)
    private String categoria;

    // Segmento IGT do IDUS (2 algarismos); nulo nos Programas, que não têm IDUS. Só o seed o preenche.
    @Column(name = "codigo_idus", updatable = false, length = 2)
    private String codigoIdus;

    // IDTYPE do SNIT, enviado no typeId do registo da publicação; distinto do codigoIdus.
    @Column(name = "snit_codigo", updatable = false, length = 20)
    private String snitCodigo;

    // Código da PCGT (GET /api/tipos-*), enviado como texto com o zero à esquerda no pedido de criação do processo.
    @Column(name = "pcgt_codigo", updatable = false, length = 20)
    private String pcgtCodigo;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "serie_numero_deposito", nullable = false, length = 10)
    @ColumnDefault("'GERAL'")
    private SerieNumeroDeposito serieNumeroDeposito = SerieNumeroDeposito.GERAL;

    // Nulo nos tipos cuja regra de concelhos ainda não está definida (regionais e nacionais).
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "ambito_territorial", length = 20)
    private AmbitoTerritorial ambitoTerritorial;

    // Só conta nos Programas, os únicos que pedem a entidade instrutora.
    @Setter
    @Column(name = "entidade_instrutora_obrigatoria", nullable = false)
    @ColumnDefault("true")
    private boolean entidadeInstrutoraObrigatoria = true;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    // Tipos cujas Dinâmicas um processo deste tipo pode associar (`tipo_instrumento_associavel`); só a configuração o muda.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tipo_instrumento_associavel",
            joinColumns = @JoinColumn(name = "tipo_instrumento_id"),
            inverseJoinColumns = @JoinColumn(name = "tipo_instrumento_associavel_id"))
    private Set<TipoInstrumento> tiposAssociaveis = new HashSet<>();

    public TipoInstrumento(String codigo, String nome, String categoria) {
        this.codigo = codigo;
        this.nome = nome;
        this.categoria = categoria;
    }
}
