package com.conkord.saigt.persistence.operacional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UtilizadorRepository extends JpaRepository<Utilizador, UUID>, JpaSpecificationExecutor<Utilizador> {

    /** O email só é único dentro de cada origem. */
    Optional<Utilizador> findByEmailAndOrigemAutenticacao(String email, String origemAutenticacao);

    /** Único nas contas `AD_DGT` (o nome de autenticação é a chave). */
    Optional<Utilizador> findByNomeAndOrigemAutenticacao(String nome, String origemAutenticacao);

    boolean existsByEmailAndOrigemAutenticacao(String email, String origemAutenticacao);

    boolean existsByEmailIgnoreCaseAndOrigemAutenticacaoAndIdNot(String email, String origemAutenticacao, UUID id);

    long countByPerfilInterno(PerfilInterno perfilInterno);
}
