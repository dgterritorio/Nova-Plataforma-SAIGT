package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoLinkPublicoRepository extends JpaRepository<DocumentoLinkPublico, UUID> {

    Optional<DocumentoLinkPublico> findByProcessoDocumento_IdAndDestinoAndRevogadoEmIsNull(UUID processoDocumentoId,
                                                                                         DestinoLinkPublico destino);

    List<DocumentoLinkPublico> findByProcessoDocumento_IdAndRevogadoEmIsNull(UUID processoDocumentoId);

    List<DocumentoLinkPublico> findByProcessoDocumento_Id(UUID processoDocumentoId);

    /** O documento vem com o link: a descarga lê tudo numa transação curta e envia sem nenhuma aberta. */
    @Query("select l from DocumentoLinkPublico l join fetch l.processoDocumento d join fetch d.processo "
            + "where l.token = :token")
    Optional<DocumentoLinkPublico> findComDocumentoByToken(@Param("token") UUID token);
}
