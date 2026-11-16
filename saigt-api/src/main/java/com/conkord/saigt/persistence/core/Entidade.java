package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Entidade SAIGT. Não participa em autenticação nem autorização: a sessão usa o token (§8.1).
 * As linhas vêm da carga do extrato da DGT (identificadas por `codigo`); o login INCM só as refresca.
 */
@Entity
@Table(name = "entidade")
@Check(name = "ck_entidade_tipo_entidade", constraints = "tipo_entidade IN ('CM', 'CCDR_DR', 'AM', 'CIM', 'PCM', "
        + "'SEC_ESTADO', 'DGT', 'ICNF', 'APA', 'DGPC', 'IP')")
@Check(name = "ck_entidade_dico_cm", constraints = "dico IS NULL OR tipo_entidade = 'CM'")
@Getter
@NoArgsConstructor
public class Entidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    // Dado da correspondência com a adesão INCM: só o Master o altera, o login não.
    @Setter
    @Column(name = "descricao_incm", nullable = false, length = 200)
    private String descricaoIncm;

    @Setter
    @Column(name = "descricao_saigt", nullable = false, length = 200)
    private String descricaoSaigt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entidade", nullable = false, length = 20)
    private TipoEntidade tipoEntidade;

    // PublishingEntityTaxId, a chave de correspondência com a INCM. Não é único: o mesmo NIPC serve vários
    // códigos (3001 e 3007) e pode faltar.
    @Setter
    @Column(length = 20)
    private String nipc;

    // DTCC do concelho; só nas CM (o formato é garantido pelo CHECK da migração).
    @Setter
    @Column(length = 4)
    private String dico;

    @Setter
    @Column(length = 30)
    private String telefone;

    @Setter
    @Column(length = 200)
    private String email;

    // Responsável do termo de adesão (figura jurídica, não necessariamente o interlocutor operacional);
    // origem externa, refrescados a cada login. `responsavelEmail` identifica-o entre os utilizadores
    // (case-insensitive), sem FK: o `ValidateUser` não devolve outro identificador.
    @Setter
    @Column(name = "responsavel_nome", length = 200)
    private String responsavelNome;

    @Setter
    @Column(name = "responsavel_email", length = 200)
    private String responsavelEmail;

    @Setter
    @Column(name = "responsavel_cargo", length = 200)
    private String responsavelCargo;

    // Restantes campos da adesão no `ValidateUser`, refrescados a cada login.
    @Setter
    @Column(name = "morada_arteria", length = 200)
    private String moradaArteria;

    @Setter
    @Column(name = "morada_numero", length = 20)
    private String moradaNumero;

    @Setter
    @Column(name = "morada_piso", length = 20)
    private String moradaPiso;

    @Setter
    @Column(name = "morada_codigo_postal", length = 20)
    private String moradaCodigoPostal;

    @Setter
    @Column(name = "morada_designacao_postal", length = 200)
    private String moradaDesignacaoPostal;

    @Setter
    @Column(name = "morada_localidade", length = 200)
    private String moradaLocalidade;

    @Setter
    @Column(name = "incm_enrollment_id", length = 100)
    private String incmEnrollmentId;

    @Setter
    @Column(name = "dr_parte_id")
    private Long drParteId;

    @Setter
    @Column(name = "dr_parte_letra", length = 10)
    private String drParteLetra;

    @Setter
    @Column(name = "dr_parte_descricao", length = 200)
    private String drParteDescricao;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atos_serie1", columnDefinition = "jsonb")
    private String atosSerie1;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atos_serie2", columnDefinition = "jsonb")
    private String atosSerie2;

    // A adesão inteira do último login, tal como a INCM a devolve.
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_incm", columnDefinition = "jsonb")
    private String dadosIncm;

    // Último refresh a partir da INCM; vazio nas linhas da carga ainda sem login.
    @Setter
    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    public Entidade(String codigo, TipoEntidade tipoEntidade, String nipc, String descricaoIncm,
                    String descricaoSaigt) {
        this.codigo = codigo;
        this.tipoEntidade = tipoEntidade;
        this.nipc = nipc;
        this.descricaoIncm = descricaoIncm;
        this.descricaoSaigt = descricaoSaigt;
    }
}
