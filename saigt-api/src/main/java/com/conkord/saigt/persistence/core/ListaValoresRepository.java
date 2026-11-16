package com.conkord.saigt.persistence.core;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListaValoresRepository extends JpaRepository<ListaValores, Long> {

    List<ListaValores> findByTipoOrderByOrdemAsc(String tipo);

    List<ListaValores> findByTipoAndAtivoTrueOrderByOrdemAsc(String tipo);

    List<ListaValores> findAllByOrderByTipoAscOrdemAsc();

    boolean existsByTipoAndCodigo(String tipo, String codigo);

    boolean existsByTipoAndAtivoTrue(String tipo);

    List<ListaValores> findByTipoInAndCodigoIn(Collection<String> tipos, Collection<String> codigos);

    List<ListaValores> findByTipoInAndAtivoTrueOrderByOrdemAsc(Collection<String> tipos);

    boolean existsByPaiAndAtivoTrue(ListaValores pai);

    // A resposta do Instrumento lê a Classe e o Domínio fora da transação.
    @EntityGraph(attributePaths = "pai.pai")
    Optional<ListaValores> findComPaisById(Long id);
}
