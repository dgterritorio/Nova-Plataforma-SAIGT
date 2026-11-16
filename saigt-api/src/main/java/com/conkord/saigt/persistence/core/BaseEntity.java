package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Base das entidades operacionais. Sem `tenant_id`, ao contrário do standard: a visibilidade resolve-se
 * pelas camadas de autorização (§8.2). Catálogos e configuração usam PK numérica e não estendem esta classe.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidAtribuivel
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Só para ids que a aplicação precisa de conhecer antes do `persist`, como a chave do objeto no storage. */
    protected void atribuirId(UUID id) {
        this.id = id;
    }

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

}
