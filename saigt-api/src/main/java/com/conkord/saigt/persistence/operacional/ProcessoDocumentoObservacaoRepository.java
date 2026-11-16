package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProcessoDocumentoObservacaoRepository
        extends JpaRepository<ProcessoDocumentoObservacao, ProcessoDocumentoObservacao.Id> {

    List<ProcessoDocumentoObservacao> findByIdProcessoId(UUID processoId);
}
