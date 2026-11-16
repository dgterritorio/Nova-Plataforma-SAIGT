package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.TipoAtoDisponivelResponse;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Os tipos de ato que a sessão pode escolher num Procedimento: o catálogo da `serie_dr` (nunca por
 * `tipo_instrumento.categoria`) × `atos_autorizados` do utilizador, incluindo a parte da adesão na 2.ª série. Uma só
 * regra para o `/disponivel`, o detalhe do processo e a gravação do tipo de ato no rascunho.
 */
public final class TiposAtoDisponiveis {

    static final String SERIE_DR_PRIMEIRA = "PRIMEIRA";
    static final String SERIE_DR_SEGUNDA_H = "SEGUNDA_H";
    static final String SERIE_DR_SEGUNDA_C = "SEGUNDA_C";

    private TiposAtoDisponiveis() {
    }

    /**
     * `serie_dr` nulo (série escolhida no processo, ainda sem escolha) devolve vazio. Os utilizadores internos
     * (`atosAutorizados() == null`) não são filtrados pelo claim, só pela série.
     */
    public static List<TipoAto> filtrar(String serieDr, ContextoUtilizador contexto,
                                        List<TipoAto> catalogoSerie1, List<TipoAto> catalogoSerie2) {
        if (serieDr == null) {
            return List.of();
        }
        List<TipoAto> catalogo = switch (serieDr) {
            case SERIE_DR_PRIMEIRA -> catalogoSerie1;
            case SERIE_DR_SEGUNDA_H, SERIE_DR_SEGUNDA_C -> catalogoSerie2;
            default -> throw new IllegalStateException("serie_dr não mapeado para tiposAtoDisponiveis: " + serieDr);
        };

        ClaimsUtilizador.AtosAutorizados atosAutorizados = contexto.atosAutorizados();
        if (atosAutorizados == null) {
            return List.copyOf(catalogo);
        }
        if (!parteDrPublicavelPelaEntidade(serieDr, contexto)) {
            return List.of();
        }
        boolean primeira = SERIE_DR_PRIMEIRA.equals(serieDr);
        Set<Long> idsAutorizados = primeira
                ? Set.copyOf(atosAutorizados.serie1())
                : atosAutorizados.serie2().stream().map(ClaimsUtilizador.AtoAutorizado::id).collect(Collectors.toSet());
        return catalogo.stream()
                .filter(tipoAto -> idsAutorizados.contains(
                        primeira ? tipoAto.getIncmActTypeIdSerie1() : tipoAto.getIncmActTypeIdSerie2()))
                .toList();
    }

    /** Na 2.ª série, a parte do DR (H ou C) tem de ser a da adesão da entidade; os utilizadores internos não são filtrados. */
    public static boolean parteDrPublicavelPelaEntidade(String serieDr, ContextoUtilizador contexto) {
        boolean segundaSerie = SERIE_DR_SEGUNDA_H.equals(serieDr) || SERIE_DR_SEGUNDA_C.equals(serieDr);
        if (!segundaSerie || contexto.atosAutorizados() == null) {
            return true;
        }
        String parteExigida = serieDr.substring(serieDr.indexOf('_') + 1);
        return parteExigida.equals(contexto.entidadeDrParteLetra());
    }

    /**
     * Para um Procedimento sem série configurada (escolhida no processo): cada tipo disponível em alguma das três
     * séries, com as séries em que o está, pela mesma regra de {@link #filtrar}. Ordem do catálogo da 1.ª série e
     * depois os só da 2.ª.
     */
    public static List<TipoAtoDisponivelResponse> comSeries(ContextoUtilizador contexto,
                                                             List<TipoAto> catalogoSerie1, List<TipoAto> catalogoSerie2) {
        Map<Long, TipoAto> tipos = new LinkedHashMap<>();
        Map<Long, List<String>> series = new LinkedHashMap<>();
        for (String serieDr : List.of(SERIE_DR_PRIMEIRA, SERIE_DR_SEGUNDA_H, SERIE_DR_SEGUNDA_C)) {
            for (TipoAto tipoAto : filtrar(serieDr, contexto, catalogoSerie1, catalogoSerie2)) {
                tipos.putIfAbsent(tipoAto.getId(), tipoAto);
                series.computeIfAbsent(tipoAto.getId(), id -> new ArrayList<>()).add(serieDr);
            }
        }
        return tipos.values().stream()
                .map(tipoAto -> new TipoAtoDisponivelResponse(tipoAto.getId(), tipoAto.getCodigo(), tipoAto.getNome(),
                        List.copyOf(series.get(tipoAto.getId()))))
                .toList();
    }

    public static List<TipoAtoDisponivelResponse> paraResponse(List<TipoAto> tiposAto) {
        return tiposAto.stream()
                .map(tipoAto -> new TipoAtoDisponivelResponse(tipoAto.getId(), tipoAto.getCodigo(), tipoAto.getNome()))
                .toList();
    }
}
