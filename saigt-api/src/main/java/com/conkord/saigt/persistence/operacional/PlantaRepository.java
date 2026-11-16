package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PlantaRepository extends JpaRepository<Planta, UUID>, JpaSpecificationExecutor<Planta> {

    /** Uma peça gráfica fora da página das Plantas tem uma só linha; um ZIP das Plantas tem uma por folha. */
    List<Planta> findByProcessoDocumento_Id(UUID processoDocumentoId);

    List<Planta> findByProcesso_Id(UUID processoId);

    List<Planta> findByMetadadosMotu_Id(UUID metadadosMotuId);

    void deleteByProcessoDocumento_Id(UUID processoDocumentoId);

    void deleteByProcesso_Id(UUID processoId);

    /** Apaga as linhas do processo que vêm dos ficheiros dos itens de uma página (os ZIP da página das Plantas). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Planta p WHERE p.processo.id = :processoId AND p.processoDocumento.id IN "
            + "(SELECT d.id FROM ProcessoDocumento d WHERE d.documentoConfig.pagina = :pagina)")
    void apagarDaPagina(UUID processoId, PaginaDocumento pagina);
}
