package com.conkord.saigt.service.core;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;

import java.util.List;
import java.util.UUID;

/**
 * Claims já resolvidos pelo fluxo de autenticação. Externo (`INCM`) tem só {@code entidade}; interno (`AD_DGT`)
 * tem só {@code perfilInterno}. O `ator` não é claim: deriva-se destes campos em runtime.
 */
public record ClaimsUtilizador(
        UUID utilizadorId,
        String nome,
        String email,
        String origem,
        PerfilInterno perfilInterno,
        ClaimsEntidade entidade,
        /** Só para `origem = INCM`: o AD da DGT não o devolve. */
        String cargo
) {

    /**
     * A entidade SAIGT (`id`, `codigo`, `tipoEntidade`, `dico`) mais a adesão INCM escolhida, sem os dados do
     * responsável. Antes da correspondência (contexto de pré-autenticação) os quatro primeiros são nulos; o token
     * de acesso só se emite com `id`. `dico` só existe nas CM.
     */
    public record ClaimsEntidade(
            Long id,
            String codigo,
            String tipoEntidade,
            String dico,
            String nipc,
            String nome,
            String incmEnrollmentId,
            boolean pcm,
            Morada morada,
            String telefone,
            String email,
            DrParte drParte,
            AtosAutorizados atosAutorizados
    ) {

        /** O indicador `pcm` decide-se aqui, pelo tipo da entidade SAIGT, e fixa-se quando a sessão abre. */
        public ClaimsEntidade comEntidadeSaigt(Long id, String codigo, String tipoEntidade, String dico) {
            return new ClaimsEntidade(id, codigo, tipoEntidade, dico, nipc, nome, incmEnrollmentId,
                    TipoEntidade.PCM.name().equals(tipoEntidade), morada,
                    telefone, email, drParte, atosAutorizados);
        }
    }

    public record Morada(String arteria, String numero, String piso, String codigoPostal,
                          String designacaoPostal, String localidade) {
    }

    /** Ausente do token (não `null`) quando a entidade não tem parte de 2.ª série. */
    public record DrParte(Long id, String letra, String descricao) {
    }

    /** Só a 2.ª série traz o indicador `extrato` (`ActTypeCanPublishExtract`). */
    public record AtosAutorizados(List<Long> serie1, List<AtoAutorizado> serie2) {

        /** `idTipoAtoSerie2` é o `incm_act_type_id_serie2` do tipo de ato; um tipo fora da lista não o permite. */
        public boolean permiteExtrato(Long idTipoAtoSerie2) {
            return serie2.stream().anyMatch(ato -> ato.extrato() && ato.id().equals(idTipoAtoSerie2));
        }
    }

    /** `nome` é nulo: a integração INCM ainda não o fornece. */
    public record AtoAutorizado(Long id, String nome, boolean extrato) {
    }
}
