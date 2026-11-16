package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha por mudança do IDUS do Processo. Só inserção: sem setters, colunas não atualizáveis, e a `saigt_app`
 * não tem UPDATE nem DELETE na tabela.
 */
@Entity
@Table(name = "idus_processo_historico")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class IdusProcessoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "processo_id", nullable = false, updatable = false)
    private UUID processoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private SegmentoIdus segmento;

    @Column(name = "idus_anterior", updatable = false, length = 50)
    private String idusAnterior;

    @Column(name = "idus_novo", nullable = false, updatable = false, length = 50)
    private String idusNovo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EventoIdus evento;

    @Column(name = "registado_em", nullable = false, updatable = false)
    private Instant registadoEm;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 100)
    private String correlationId;
}
