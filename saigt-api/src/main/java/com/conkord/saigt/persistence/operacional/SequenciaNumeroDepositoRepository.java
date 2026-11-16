package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.SerieNumeroDeposito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SequenciaNumeroDepositoRepository
        extends JpaRepository<SequenciaNumeroDeposito, SequenciaNumeroDeposito.Id> {

    /**
     * Próximo sequencial da série no ano, na transação de quem chama. O UPDATE bloqueia a linha até ao commit, por isso
     * duas publicações simultâneas nunca recebem o mesmo valor; o INSERT sem conflito cria a linha do primeiro do ano.
     */
    default int proximoSequencial(SerieNumeroDeposito serie, int ano) {
        criarSeNaoExistir(serie.name(), ano);
        incrementar(serie.name(), ano);
        return ultimoSequencial(serie.name(), ano);
    }

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO sequencia_numero_deposito (serie, ano, ultimo_sequencial) VALUES (:serie, :ano, 0) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void criarSeNaoExistir(@Param("serie") String serie, @Param("ano") int ano);

    @Modifying
    @Query(value = "UPDATE sequencia_numero_deposito SET ultimo_sequencial = ultimo_sequencial + 1 "
            + "WHERE serie = :serie AND ano = :ano", nativeQuery = true)
    void incrementar(@Param("serie") String serie, @Param("ano") int ano);

    @Query(value = "SELECT ultimo_sequencial FROM sequencia_numero_deposito WHERE serie = :serie AND ano = :ano",
            nativeQuery = true)
    int ultimoSequencial(@Param("serie") String serie, @Param("ano") int ano);
}
