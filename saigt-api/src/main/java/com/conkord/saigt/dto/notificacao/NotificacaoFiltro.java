package com.conkord.saigt.dto.notificacao;

import com.conkord.saigt.exception.FiltroNotificacaoInvalidoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NotificacaoFiltro(Set<EstadoNotificacao> estados, Set<TipoNotificacao> tipos, UUID processoId,
                                LocalDate desde, LocalDate ate) {

    public static NotificacaoFiltro de(List<String> estado, List<String> tipo, UUID processoId, LocalDate desde,
                                       LocalDate ate) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        Set<EstadoNotificacao> estados = converter(estado, EstadoNotificacao.class, "estado", erros);
        Set<TipoNotificacao> tipos = converter(tipo, TipoNotificacao.class, "tipo", erros);
        if (!erros.isEmpty()) {
            throw new FiltroNotificacaoInvalidoException(erros);
        }
        return new NotificacaoFiltro(estados, tipos, processoId, desde, ate);
    }

    public boolean semEstado() {
        return estados.isEmpty();
    }

    private static <E extends Enum<E>> Set<E> converter(List<String> valores, Class<E> tipo, String parametro,
                                                        List<ValidacaoException.CampoErro> erros) {
        Set<E> resultado = EnumSet.noneOf(tipo);
        if (valores == null) {
            return resultado;
        }
        for (String valor : valores) {
            Arrays.stream(tipo.getEnumConstants()).filter(constante -> constante.name().equals(valor)).findFirst()
                    .ifPresentOrElse(resultado::add,
                            () -> erros.add(new ValidacaoException.CampoErro(parametro, "Valor inválido: " + valor + ".")));
        }
        return resultado;
    }
}
