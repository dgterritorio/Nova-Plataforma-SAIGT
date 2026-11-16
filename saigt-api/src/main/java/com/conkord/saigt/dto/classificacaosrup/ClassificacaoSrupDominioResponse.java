package com.conkord.saigt.dto.classificacaosrup;

import java.util.List;

/** Domínio da classificação das servidões com as suas Classes e as Tipologias ativas de cada uma. */
public record ClassificacaoSrupDominioResponse(Long id, String codigo, String descricao, List<Classe> classes) {

    public record Classe(Long id, String codigo, String descricao, List<Tipologia> tipologias) {
    }

    public record Tipologia(Long id, String codigo, String descricao) {
    }
}
