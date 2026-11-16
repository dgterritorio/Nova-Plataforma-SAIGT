package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pode haver várias do mesmo tipo no mesmo Instrumento (ex: duas Alterações). Referencia
 * instrumento_dinamica_config, não tipo_dinamica: é daí que o motor de elegibilidade navega.
 * O cascade de `estado` nunca é automático.
 */
@Entity
@Table(name = "dinamica")
@Getter
@NoArgsConstructor
public class Dinamica extends BaseEntity {

    /**
     * O Instrumento a partir do qual a Dinâmica foi iniciada: dá os dados do Instrumento ao processo (designação,
     * SNIT, PCGT) e as `dinamicasEmCurso` do `/disponivel`. Nulo numa Dinâmica iniciada sem IGT de partida; quando
     * existe, pertence a {@link #instrumentos}. Nunca decide visibilidade: essa é a da entidade do processo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrumento_id")
    private Instrumento instrumento;

    /**
     * Os Instrumentos que a Dinâmica abrange (`dinamica_instrumento`, DB-PROC-018). As regras de elegibilidade,
     * a `ultimaDinamicaPublicada` e o painel do Instrumento leem este conjunto.
     */
    // A listagem de processos lê os Instrumentos abrangidos de cada linha: em lotes, sem uma consulta por linha.
    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "dinamica_instrumento",
            joinColumns = @JoinColumn(name = "dinamica_id"),
            inverseJoinColumns = @JoinColumn(name = "instrumento_id"))
    private Set<Instrumento> instrumentos = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_dinamica_config_id", nullable = false)
    private InstrumentoDinamicaConfig instrumentoDinamicaConfig;

    @ContadorIdus(nome = "NUMERO_DINAMICA")
    @Column(name = "numero_dinamica", nullable = false, unique = true, length = 20, updatable = false)
    private String numeroDinamica;

    @Column(nullable = false, length = 50)
    private String descritivo;

    @Column(nullable = false)
    private int tentativa = 1;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDinamica estado = EstadoDinamica.EM_CURSO;

    // NATIVO | ESPELHO_ABERTO | ESPELHO_FECHADO.
    @Setter
    @Column(name = "origem_dados", nullable = false, length = 20)
    private String origemDados = "NATIVO";

    @CreationTimestamp
    @Column(name = "data_inicio", nullable = false, updatable = false)
    private Instant dataInicio;

    @Setter
    @Column(name = "data_conclusao")
    private Instant dataConclusao;

    // Vazia enquanto nenhum Procedimento da Dinâmica estiver publicado.
    @Setter
    @Column(name = "data_caducidade")
    private LocalDate dataCaducidade;

    // Limiares de caducidade já notificados, ex: ["UM_MES","QUINZE_DIAS"].
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alertas_enviados", columnDefinition = "jsonb")
    private List<String> alertasEnviados;

    // Nula até a submissão do Procedimento que pergunta responder, e nas Dinâmicas que não perguntam.
    @Setter
    @Column(name = "com_geoconform")
    private Boolean comGeoconform;

    // Devolvido pela criação do processo no GeoConform; nulo até lá e nas Dinâmicas sem GeoConform.
    @Setter
    @Column(name = "url_geoconform", columnDefinition = "text")
    private String urlGeoconform;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Utilizador criadoPor;

    public Dinamica(Instrumento instrumento, InstrumentoDinamicaConfig instrumentoDinamicaConfig, String descritivo,
                    Utilizador criadoPor) {
        this.instrumento = instrumento;
        this.instrumentos.add(instrumento);
        this.instrumentoDinamicaConfig = instrumentoDinamicaConfig;
        this.descritivo = descritivo;
        this.criadoPor = criadoPor;
    }

    /** Sem IGT de partida: os Instrumentos abrangidos associam-se depois do formulário inicial. */
    public Dinamica(InstrumentoDinamicaConfig instrumentoDinamicaConfig, String descritivo, Utilizador criadoPor) {
        this.instrumentoDinamicaConfig = instrumentoDinamicaConfig;
        this.descritivo = descritivo;
        this.criadoPor = criadoPor;
    }

    /** Passa a abranger só os Instrumentos dados: os que já abrangia ficam, os outros saem ou entram. */
    public void substituirInstrumentos(Collection<Instrumento> novos) {
        instrumentos.retainAll(novos);
        instrumentos.addAll(novos);
    }

    public Optional<Instrumento> instrumentoDeInicio() {
        return Optional.ofNullable(instrumento);
    }

    /** A designação do Instrumento de início ou, sem ele, o `descritivo` da Dinâmica. */
    public String designacao() {
        return instrumento == null ? descritivo : instrumento.getDesignacao();
    }
}
