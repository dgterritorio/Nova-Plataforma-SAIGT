package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Imutável: um depósito novo grava um manifesto novo. */
@Entity
@Table(name = "deposito_snit_manifesto")
@Getter
@NoArgsConstructor
public class DepositoSnitManifesto {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "processo_id", nullable = false, updatable = false)
    private UUID processoId;

    @Convert(converter = CaixasIncluidasConverter.class)
    @Column(name = "caixas_incluidas", nullable = false, updatable = false, length = 30)
    private Set<CategoriaDocumento> caixasIncluidas;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public DepositoSnitManifesto(UUID processoId, Set<CategoriaDocumento> caixasIncluidas, Instant criadoEm) {
        if (caixasIncluidas == null || caixasIncluidas.isEmpty()) {
            throw new IllegalArgumentException("O manifesto tem de incluir pelo menos uma caixa.");
        }
        this.id = UUID.randomUUID();
        this.processoId = processoId;
        this.caixasIncluidas = EnumSet.copyOf(caixasIncluidas);
        this.criadoEm = criadoEm;
    }

    public Set<CategoriaDocumento> getCaixasIncluidas() {
        return Set.copyOf(caixasIncluidas);
    }
}
