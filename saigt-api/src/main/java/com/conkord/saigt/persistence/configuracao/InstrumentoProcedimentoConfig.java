package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoProcedimento;
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

/**
 * Sequenciamento dos Procedimentos dentro de uma Dinâmica configurada.
 * `serieDr` pertence ao Procedimento, não ao Instrumento: a Ratificação de um PDM é `PRIMEIRA`,
 * os restantes Procedimentos do mesmo PDM são `SEGUNDA_H`.
 */
@Entity
@Table(name = "instrumento_procedimento_config",
        uniqueConstraints = @UniqueConstraint(name = "uq_instrumento_procedimento_config",
                columnNames = {"instrumento_dinamica_config_id", "tipo_procedimento_id"}))
@Check(constraints = "serie_dr IN ('PRIMEIRA', 'SEGUNDA_H', 'SEGUNDA_C') "
        + "AND (tamanho_maximo_processo IS NULL OR tamanho_maximo_processo > 0) "
        + "AND pergunta_geoconform IN ('NAO_APLICAVEL', 'OBRIGATORIA', 'SE_PECAS_GRAFICAS') "
        + "AND pede_localizacao IN ('NAO', 'OPCIONAL', 'OBRIGATORIA') "
        + "AND pede_motivo_alteracao IN ('NAO', 'COM_PERGUNTA', 'OBRIGATORIO')")
@Getter
@NoArgsConstructor
public class InstrumentoProcedimentoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_dinamica_config_id", nullable = false)
    private InstrumentoDinamicaConfig instrumentoDinamicaConfig;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_procedimento_id", nullable = false)
    private TipoProcedimento tipoProcedimento;

    @Setter
    @Column(nullable = false)
    private int ordem;

    @Setter
    @Column(nullable = false)
    private boolean obrigatorio = true;

    // Vazio = sem prazo configurado.
    @Setter
    @Column(name = "prazo_dias")
    private Integer prazoDias;

    // LEGAL | INTERNO — só relevante quando prazoDias está preenchido.
    @Setter
    @Column(name = "tipo_prazo", length = 10)
    private String tipoPrazo;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    // PRIMEIRA | SEGUNDA_H | SEGUNDA_C — nunca derivado de tipo_instrumento.categoria; configurável em backoffice.
    // Nulo só nas restantes servidões: a série escolhe-se no processo.
    @Setter
    @Column(name = "serie_dr", length = 10)
    private String serieDr;

    // Vazio = sem limite agregado por processo, em bytes.
    @Setter
    @Column(name = "tamanho_maximo_processo")
    private Long tamanhoMaximoProcesso;

    @Setter
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean repetivel = false;

    @Setter
    @ColumnDefault("false")
    @Column(name = "ato_pontual", nullable = false)
    private boolean atoPontual = false;

    // Pergunta-se na submissão de um Processo deste Procedimento, não do Instrumento: o core não testa o tipo.
    @Setter
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'NAO_APLICAVEL'")
    @Column(name = "pergunta_geoconform", nullable = false, length = 20)
    private PerguntaGeoconform perguntaGeoconform = PerguntaGeoconform.NAO_APLICAVEL;

    // Segmento Procedimento do IDUS (3 algarismos); depende da Dinâmica, por isso não está em tipo_procedimento.
    // Só o seed o preenche.
    @Column(name = "codigo_idus", updatable = false, length = 3)
    private String codigoIdus;

    @Setter
    @ColumnDefault("false")
    @Column(name = "pede_fundamentacao_rjigt", nullable = false)
    private boolean pedeFundamentacaoRjigt = false;

    @Setter
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'NAO'")
    @Column(name = "pede_localizacao", nullable = false, length = 20)
    private PedeLocalizacao pedeLocalizacao = PedeLocalizacao.NAO;

    @Setter
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'NAO'")
    @Column(name = "pede_motivo_alteracao", nullable = false, length = 20)
    private PedeMotivoAlteracao pedeMotivoAlteracao = PedeMotivoAlteracao.NAO;

    // Os processos do Procedimento são vistos, em leitura, pelas outras entidades da mesma Dinâmica.
    @Setter
    @ColumnDefault("false")
    @Column(name = "visivel_na_dinamica", nullable = false)
    private boolean visivelNaDinamica = false;

    public InstrumentoProcedimentoConfig(InstrumentoDinamicaConfig instrumentoDinamicaConfig,
                                          TipoProcedimento tipoProcedimento, int ordem, String serieDr) {
        this.instrumentoDinamicaConfig = instrumentoDinamicaConfig;
        this.tipoProcedimento = tipoProcedimento;
        this.ordem = ordem;
        this.serieDr = serieDr;
    }

    /** Um novo processo depois do anterior `PUBLICADO`: no `repetivel` e no ato pontual («Opcional, repetível», EF v1.5). */
    public boolean repeteDepoisDePublicado() {
        return repetivel || atoPontual;
    }
}
