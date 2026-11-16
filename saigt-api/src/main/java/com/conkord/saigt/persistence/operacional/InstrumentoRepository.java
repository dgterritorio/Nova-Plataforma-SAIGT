package com.conkord.saigt.persistence.operacional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentoRepository extends JpaRepository<Instrumento, UUID>, JpaSpecificationExecutor<Instrumento> {

    /** Fora de `@GeneratedValue` porque a migração do legado insere valores explícitos na mesma sequência. */
    @Query(value = "SELECT nextval('seq_instrumento_numero_sequencial')", nativeQuery = true)
    long proximoNumeroSequencial();

    // Evita LazyInitializationException (open-in-view desligado): `tipoInstrumento`, a classificação e a servidão de origem são lidos na resposta.
    @EntityGraph(attributePaths = {"tipoInstrumento", "tipologiaSrup.pai.pai", "instrumentoOrigem"})
    @Override
    Page<Instrumento> findAll(Pageable pageable);

    // A pesquisa dos Instrumentos a associar: os mesmos dados de `findAll(Pageable)`, filtrados.
    @EntityGraph(attributePaths = {"tipoInstrumento", "instrumentoOrigem"})
    @Override
    Page<Instrumento> findAll(Specification<Instrumento> especificacao, Pageable pageable);

    // Mesmo motivo: a entidade é lida fora da transação do serviço.
    @EntityGraph(attributePaths = {"tipoInstrumento", "tipologiaSrup.pai.pai"})
    @Override
    Optional<Instrumento> findById(UUID id);

    List<Instrumento> findAllByIdIgtSnitIn(Collection<String> idsIgtSnit);

    boolean existsByIdIgtSnit(String idIgtSnit);

    boolean existsByIdAndIdIgtSnitNotNull(UUID id);

    boolean existsByTipoInstrumento_Id(Long tipoInstrumentoId);
}
