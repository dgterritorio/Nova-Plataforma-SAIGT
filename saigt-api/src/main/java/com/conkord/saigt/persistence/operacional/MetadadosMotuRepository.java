package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetadadosMotuRepository extends JpaRepository<MetadadosMotu, UUID> {

    Optional<MetadadosMotu> findByProcesso_IdAndTipo(UUID processoId, TipoMetadadosMotu tipo);

    Optional<MetadadosMotu> findByProcessoDocumento_IdAndTipoAndOpcionalFalse(UUID processoDocumentoId,
                                                                             TipoMetadadosMotu tipo);

    boolean existsByProcessoDocumento_IdAndTipoAndIdNot(UUID processoDocumentoId, TipoMetadadosMotu tipo, UUID id);

    List<MetadadosMotu> findByProcessoDocumento_Id(UUID processoDocumentoId);

    List<MetadadosMotu> findByProcessoDocumento_IdAndOpcionalTrue(UUID processoDocumentoId);

    List<MetadadosMotu> findByProcesso_Id(UUID processoId);

    void deleteByProcesso_Id(UUID processoId);
}
