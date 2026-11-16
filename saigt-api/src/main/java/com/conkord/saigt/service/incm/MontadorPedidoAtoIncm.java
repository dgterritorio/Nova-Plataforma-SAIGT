package com.conkord.saigt.service.incm;

import com.conkord.saigt.exception.DadosIncmEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.incm.SubmitNewActRequest;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.operacional.Processo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A estrutura do `SubmitNewAct` (1.ª ou 2.ª série) vem só da série efetiva do processo (a do Procedimento ou, sem
 * ela, a escolhida no processo), nunca do tipo de Instrumento nem da entidade; o pedido leva uma só das duas.
 */
@Component
public class MontadorPedidoAtoIncm {

    private static final String SERIE_DR_PRIMEIRA = "PRIMEIRA";

    private static final String MENSAGEM_SERIE_2 = "Faltam dados obrigatórios para publicação na 2.ª série.";
    private static final String MENSAGEM_SUMARIO_DATA = "Sumário e data de assinatura são obrigatórios.";
    private static final String MENSAGEM_TIPO_ATO = "O tipo de ato não está disponível na série deste procedimento.";

    public void validar(Processo processo) {
        String serieDr = processo.serieDrEfetiva();
        boolean primeiraSerie = SERIE_DR_PRIMEIRA.equals(serieDr);

        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        if (isBlank(serieDr)) {
            erros.add(new ValidacaoException.CampoErro("serieDr",
                    "O processo não tem série do Diário da República."));
        }
        TipoAto tipoAto = processo.getTipoAto();
        Long actTypeId = tipoAto == null ? null
                : primeiraSerie ? tipoAto.getIncmActTypeIdSerie1() : tipoAto.getIncmActTypeIdSerie2();
        if (actTypeId == null) {
            erros.add(new ValidacaoException.CampoErro("tipoAto", MENSAGEM_TIPO_ATO));
        }
        if (isBlank(processo.getIncmEnrollmentId())) {
            erros.add(new ValidacaoException.CampoErro("incmEnrollmentId",
                    "Falta o termo de adesão INCM associado à entidade."));
        }
        if (primeiraSerie) {
            validarPrimeiraSerie(processo, erros);
        } else {
            validarSegundaSerie(processo, erros);
        }
        if (isBlank(processo.getSumario())) {
            erros.add(new ValidacaoException.CampoErro("sumario", MENSAGEM_SUMARIO_DATA));
        }
        if (processo.getDataAssinatura() == null) {
            erros.add(new ValidacaoException.CampoErro("dataAssinatura", MENSAGEM_SUMARIO_DATA));
        }

        if (!erros.isEmpty()) {
            throw new DadosIncmEmFaltaException("Dados insuficientes para envio à INCM.", erros);
        }
    }

    private void validarPrimeiraSerie(Processo processo, List<ValidacaoException.CampoErro> erros) {
        if (processo.getPublicarSuplemento() == null) {
            erros.add(new ValidacaoException.CampoErro("publicarSuplemento",
                    "Obrigatório na submissão de atos de 1.ª série."));
        } else if (processo.getPublicarSuplemento() && processo.getDataSuplemento() == null) {
            erros.add(new ValidacaoException.CampoErro("dataSuplemento",
                    "Indique a data prevista de publicação em suplemento."));
        }
    }

    private void validarSegundaSerie(Processo processo, List<ValidacaoException.CampoErro> erros) {
        if (processo.getDrParteId() == null) {
            erros.add(new ValidacaoException.CampoErro("drParteId", MENSAGEM_SERIE_2));
        }
        if (isBlank(processo.getNormaLegal())) {
            erros.add(new ValidacaoException.CampoErro("normaLegal", MENSAGEM_SERIE_2));
        }
        if (isBlank(processo.getTipoInformacao())) {
            erros.add(new ValidacaoException.CampoErro("tipoInformacao", MENSAGEM_SERIE_2));
        }
    }

    public SubmitNewActRequest montar(Processo processo, LinksPublicosIncm.LinksIncm links) {
        boolean primeiraSerie = SERIE_DR_PRIMEIRA.equals(processo.serieDrEfetiva());
        return new SubmitNewActRequest(new SubmitNewActRequest.DgtAct(
                processo.getIncmEnrollmentId(),
                primeiraSerie ? atoPrimeiraSerie(processo) : null,
                primeiraSerie ? null : atoSegundaSerie(processo),
                List.copyOf(links.imagens()), List.copyOf(links.ficheiros())));
    }

    private SubmitNewActRequest.DiarioRepublicaSeries1Act atoPrimeiraSerie(Processo processo) {
        boolean suplemento = Boolean.TRUE.equals(processo.getPublicarSuplemento());
        return new SubmitNewActRequest.DiarioRepublicaSeries1Act(
                processo.getTipoAto().getIncmActTypeIdSerie1(),
                suplemento,
                suplemento ? processo.getDataSuplemento() : null,
                processo.getSumario(),
                processo.getDataAssinatura());
    }

    private SubmitNewActRequest.DiarioRepublicaSeries2Act atoSegundaSerie(Processo processo) {
        return new SubmitNewActRequest.DiarioRepublicaSeries2Act(
                processo.getTipoAto().getIncmActTypeIdSerie2(),
                processo.getDrParteId(),
                tipoInformacaoIncm(processo.getTipoInformacao()),
                processo.getSumario(),
                processo.getDataAssinatura(),
                processo.getNormaLegal());
    }

    private String tipoInformacaoIncm(String tipoInformacao) {
        return switch (tipoInformacao) {
            case "TEXTO_INTEGRAL" -> "full-text";
            case "EXTRATO" -> "excerpt";
            default -> throw new IllegalStateException("tipo_informacao desconhecido: " + tipoInformacao);
        };
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}
