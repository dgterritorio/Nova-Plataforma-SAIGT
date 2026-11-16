package com.conkord.saigt.persistence.core;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Imutável: a BD impede update/delete e a entidade não tem setters.
 * `utilizadorId` é UUID simples, sem relação JPA, para evitar acoplamento. Vazio só nas ações do sistema
 * (jobs, via {@code AuditoriaService.registarSistema}); fora disso, sem utilizador (ex.: login com email
 * desconhecido) não se regista nada.
 */
@Entity
@Table(name = "auditoria")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "tipo_ocorrencia", nullable = false, updatable = false, length = 100)
    private String tipoOcorrencia;

    @Column(name = "id_ocorrencia", nullable = false, updatable = false, length = 100)
    private String idOcorrencia;

    @Column(nullable = false, updatable = false, length = 100)
    private String acao;

    @Column(name = "estado_antes", updatable = false, length = 30)
    private String estadoAntes;

    @Column(name = "estado_depois", updatable = false, length = 30)
    private String estadoDepois;

    @Column(name = "utilizador_id", updatable = false)
    private UUID utilizadorId;

    // Vazio para utilizadores da DGT. Guardada aqui porque `sessao` é purgável e esta tabela é permanente.
    @Column(name = "entidade_id", updatable = false)
    private Long entidadeId;

    @Column(name = "ip_origem", updatable = false, length = 45)
    private String ipOrigem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_alteracao", updatable = false, columnDefinition = "jsonb")
    private String dadosAlteracao;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
