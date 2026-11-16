package com.conkord.saigt.service.login;

import com.conkord.saigt.integracao.incm.AdesaoIncm;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.service.core.ClaimsUtilizador;

import java.util.List;

/** Constrói as claims da entidade para os grants do servidor de autorização. */
public final class ClaimsEntidadeMapper {

    private ClaimsEntidadeMapper() {
    }

    /** Só a adesão da INCM: sem os dados do responsável e sem a entidade SAIGT, que vem da correspondência. */
    public static ClaimsUtilizador.ClaimsEntidade deAdesao(AdesaoIncm adesao) {
        // A INCM não devolve indicador PCM: vem do tipo da entidade SAIGT, em `comEntidade`.
        return new ClaimsUtilizador.ClaimsEntidade(null, null, null, null, adesao.nif(), adesao.nomeEntidade(),
                adesao.incmEnrollmentId(), false, paraMorada(adesao.morada()), adesao.telefone(), adesao.email(),
                paraDrParte(adesao), paraAtosAutorizados(adesao));
    }

    /** `dico` só nas CM; a entidade SAIGT vem sempre da correspondência, nunca do NIPC. */
    public static ClaimsUtilizador.ClaimsEntidade comEntidade(ClaimsUtilizador.ClaimsEntidade claims, Entidade entidade) {
        return claims.comEntidadeSaigt(entidade.getId(), entidade.getCodigo(), entidade.getTipoEntidade().name(),
                entidade.getDico());
    }

    private static ClaimsUtilizador.Morada paraMorada(AdesaoIncm.Morada morada) {
        if (morada == null) {
            return null;
        }
        return new ClaimsUtilizador.Morada(morada.arteria(), morada.numero(), morada.piso(), morada.codigoPostal(),
                morada.designacaoPostal(), morada.localidade());
    }

    /** Nulo, não um `DrParte` com campos nulos, quando a entidade não tem parte de 2.ª série. */
    private static ClaimsUtilizador.DrParte paraDrParte(AdesaoIncm adesao) {
        if (adesao.drParteId() == null) {
            return null;
        }
        return new ClaimsUtilizador.DrParte(adesao.drParteId(), adesao.drParteLetra(), adesao.drParteDescricao());
    }

    private static ClaimsUtilizador.AtosAutorizados paraAtosAutorizados(AdesaoIncm adesao) {
        List<ClaimsUtilizador.AtoAutorizado> serie2 = adesao.atosAutorizadosSerie2().stream()
                .map(ato -> new ClaimsUtilizador.AtoAutorizado(ato.id(), ato.nome(), ato.extrato()))
                .toList();
        return new ClaimsUtilizador.AtosAutorizados(adesao.atosAutorizadosSerie1(), serie2);
    }
}
