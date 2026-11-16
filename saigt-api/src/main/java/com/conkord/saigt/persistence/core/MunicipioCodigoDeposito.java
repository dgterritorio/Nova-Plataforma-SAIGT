package com.conkord.saigt.persistence.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Código do concelho no n.º de depósito (`RR.DD.CC`), da tabela que a plataforma atual usa; só o seed a preenche. */
@Entity
@Table(name = "municipio_codigo_deposito")
@Getter
@NoArgsConstructor
public class MunicipioCodigoDeposito {

    @Id
    @Column(name = "dtcc", nullable = false, updatable = false, length = 4)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String dtcc;

    @Column(name = "codigo_regiao", nullable = false, updatable = false, length = 2)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String codigoRegiao;

    @Column(name = "codigo_municipio", nullable = false, updatable = false, length = 8)
    private String codigoMunicipio;

    public MunicipioCodigoDeposito(String dtcc, String codigoRegiao, String codigoMunicipio) {
        this.dtcc = dtcc;
        this.codigoRegiao = codigoRegiao;
        this.codigoMunicipio = codigoMunicipio;
    }
}
