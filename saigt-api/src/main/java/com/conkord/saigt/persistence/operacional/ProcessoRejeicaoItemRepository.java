package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProcessoRejeicaoItemRepository extends JpaRepository<ProcessoRejeicaoItem, Long> {

    List<ProcessoRejeicaoItem> findByRejeicao_IdOrderByIdAsc(Long rejeicaoId);

    @EntityGraph(attributePaths = {"processoDocumento.documentoConfig", "processoInformacaoBase", "categoria", "motivo"})
    List<ProcessoRejeicaoItem> findByRejeicao_IdInOrderByIdAsc(Collection<Long> rejeicaoIds);
}
