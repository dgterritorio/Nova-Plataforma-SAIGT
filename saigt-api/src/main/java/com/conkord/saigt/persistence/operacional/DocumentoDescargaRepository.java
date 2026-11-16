package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface DocumentoDescargaRepository extends JpaRepository<DocumentoDescarga, Long> {

    /** Só o IP: o resto da linha mantém-se. */
    @Modifying
    @Query("update DocumentoDescarga d set d.ip = null where d.ip is not null and d.iniciadaEm < :limite")
    int limparIpAnteriorA(Instant limite);
}
