package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * O IDUS corrente do Processo, que muda ao longo da vida dele; cada mudança fica em `idus_processo_historico`.
 * Só os Processos de Planos e da REN têm IDUS.
 */
@Entity
@Table(name = "idus_processo",
        uniqueConstraints = @UniqueConstraint(name = "uq_idus_processo_idus", columnNames = "idus"))
@Getter
@NoArgsConstructor
public class IdusProcesso {

    @Id
    @Column(name = "processo_id", nullable = false, updatable = false)
    private UUID processoId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "processo_id")
    private Processo processo;

    @Setter
    @Column(nullable = false, length = 50)
    private String idus;

    @Column(name = "sistema_origem", nullable = false, updatable = false, length = 10)
    private String sistemaOrigem;

    @Setter
    @Column(name = "id_igt", nullable = false, length = 10)
    private String idIgt;

    @Setter
    @Column(name = "id_igt_definitivo", nullable = false)
    private boolean idIgtDefinitivo;

    @Setter
    @Column(name = "ordem_dinamica", nullable = false, length = 10)
    private String ordemDinamica;

    @Setter
    @Column(name = "ordem_definitiva", nullable = false)
    private boolean ordemDefinitiva;

    @Setter
    @Column(nullable = false)
    private short tentativa;

    // Três dígitos com prefixo (031, 131, 231).
    @Setter
    @Column(nullable = false, length = 3)
    private String procedimento;

    @Setter
    @Column(nullable = false)
    private short submissao;

    @Setter
    @Column(name = "pub_incm", length = 20)
    private String pubIncm;

    @Setter
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Builder
    private IdusProcesso(Processo processo, String idus, String sistemaOrigem, String idIgt, boolean idIgtDefinitivo,
                         String ordemDinamica, boolean ordemDefinitiva, short tentativa, String procedimento,
                         short submissao, String pubIncm, Instant atualizadoEm) {
        this.processo = processo;
        this.idus = idus;
        this.sistemaOrigem = sistemaOrigem;
        this.idIgt = idIgt;
        this.idIgtDefinitivo = idIgtDefinitivo;
        this.ordemDinamica = ordemDinamica;
        this.ordemDefinitiva = ordemDefinitiva;
        this.tentativa = tentativa;
        this.procedimento = procedimento;
        this.submissao = submissao;
        this.pubIncm = pubIncm;
        this.atualizadoEm = atualizadoEm;
    }
}
