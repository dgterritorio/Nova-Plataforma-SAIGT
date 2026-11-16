package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Um contador por `nome`. Quem o usa atribui os valores sob `pg_advisory_xact_lock`. */
@Entity
@Table(name = "idus_contador")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IdusContador {

    @Id
    @Column(nullable = false, updatable = false, length = 50)
    private String nome;

    @Setter
    @Column(name = "valor_atual", nullable = false)
    private int valorAtual;
}
