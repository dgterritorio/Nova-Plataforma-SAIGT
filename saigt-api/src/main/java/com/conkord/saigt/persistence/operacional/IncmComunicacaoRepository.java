package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncmComunicacaoRepository extends JpaRepository<IncmComunicacao, Long> {

    List<IncmComunicacao> findByProcessoIdOrderByEnviadoEmAsc(UUID processoId);

    long countByProcessoIdAndOperacao(UUID processoId, String operacao);

    Optional<IncmComunicacao> findFirstByProcessoIdAndOperacaoOrderByEnviadoEmDesc(UUID processoId, String operacao);
}
