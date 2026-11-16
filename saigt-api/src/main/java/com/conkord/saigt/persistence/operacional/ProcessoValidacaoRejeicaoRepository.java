package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessoValidacaoRejeicaoRepository extends JpaRepository<ProcessoValidacaoRejeicao, Long> {

    List<ProcessoValidacaoRejeicao> findByProcesso_Id(UUID processoId);

    Optional<ProcessoValidacaoRejeicao> findByProcessoDocumento_Id(UUID processoDocumentoId);

    Optional<ProcessoValidacaoRejeicao> findByProcessoInformacaoBase_Id(UUID processoInformacaoBaseId);

    void deleteByProcesso_Id(UUID processoId);
}
