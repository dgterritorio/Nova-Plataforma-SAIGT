package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incm_comunicacao")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class IncmComunicacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "processo_id", nullable = false, updatable = false)
    private UUID processoId;

    @Column(nullable = false, updatable = false, length = 20)
    private String operacao;

    @Column(name = "act_identifier", updatable = false)
    private Long actIdentifier;

    @Column(nullable = false, updatable = false)
    private Short submissao;

    @Column(name = "serie_dr", updatable = false, length = 10)
    private String serieDr;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String pedido;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private String resposta;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Column(name = "is_success", updatable = false)
    private Boolean isSuccess;

    @Column(name = "processing_status", updatable = false, length = 50)
    private String processingStatus;

    @Column(name = "act_status", updatable = false, length = 100)
    private String actStatus;

    @Column(name = "falha_tecnica", updatable = false, length = 20)
    private String falhaTecnica;

    @Column(name = "transicao_derivada", updatable = false, length = 30)
    private String transicaoDerivada;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 100)
    private String correlationId;

    @Column(name = "enviado_em", nullable = false, updatable = false)
    private Instant enviadoEm;

    @Column(name = "recebido_em", updatable = false)
    private Instant recebidoEm;
}
