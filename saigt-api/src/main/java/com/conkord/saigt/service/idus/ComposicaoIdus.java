package com.conkord.saigt.service.idus;

import com.conkord.saigt.exception.IdusInvalidoException;

import java.util.ArrayList;
import java.util.List;

/**
 * O único sítio que monta a cadeia do IDUS, para que mudar a composição dos segmentos não obrigue a mexer em quem a
 * usa. Ordem: Sistema, DICO, IGT, ID_IGT, Dinâmica, Ordem_Dinâmica, Tentativa, Procedimento, Submissão e, só quando
 * existe, pubINCM, separados por ponto. A mais longa tem 45 caracteres, dentro dos 50 de `idus_processo.idus`.
 */
public final class ComposicaoIdus {

    private static final String SEPARADOR = ".";
    private static final int SEGMENTOS_SEM_PUB_INCM = 9;

    private ComposicaoIdus() {
    }

    public static String compor(SegmentosIdus segmentos) {
        List<String> partes = new ArrayList<>();
        partes.add(validar("Sistema", segmentos.sistema(), 2));
        partes.add(validar("DICO", segmentos.dico(), 4));
        partes.add(validar("IGT", segmentos.igt(), 2));
        partes.add(validar("ID_IGT", segmentos.idIgt(), 6));
        partes.add(validar("Dinâmica", segmentos.dinamica(), 2));
        partes.add(validar("Ordem_Dinâmica", segmentos.ordemDinamica(), 5));
        partes.add(validar("Tentativa", segmentos.tentativa(), 2));
        partes.add(validar("Procedimento", segmentos.procedimento(), 3));
        partes.add(validar("Submissão", segmentos.submissao(), 2));
        if (segmentos.pubIncm() != null) {
            partes.add(validar("pubINCM", segmentos.pubIncm(), 8));
        }
        return String.join(SEPARADOR, partes);
    }

    /**
     * IDUS da Dinâmica (EF v1.5, «Identificador Único»): Sistema, DICO, IGT, ID_IGT, Dinâmica, Ordem_Dinâmica e o pubINCM
     * do processo da publicação final, quando existe.
     */
    public static String comporDinamica(SegmentosIdus segmentos) {
        List<String> partes = new ArrayList<>(List.of(validar("Sistema", segmentos.sistema(), 2),
                validar("DICO", segmentos.dico(), 4), validar("IGT", segmentos.igt(), 2),
                validar("ID_IGT", segmentos.idIgt(), 6), validar("Dinâmica", segmentos.dinamica(), 2),
                validar("Ordem_Dinâmica", segmentos.ordemDinamica(), 5)));
        if (segmentos.pubIncm() != null) {
            partes.add(validar("pubINCM", segmentos.pubIncm(), 8));
        }
        return String.join(SEPARADOR, partes);
    }

    /**
     * O inverso de {@link #compor}: serve para mudar um só segmento de um IDUS já gravado sem resolver outra vez os
     * que vêm dos catálogos e do Instrumento, que entretanto podem ter mudado.
     */
    public static SegmentosIdus decompor(String idus) {
        String[] partes = idus == null ? new String[0] : idus.split("\\" + SEPARADOR, -1);
        if (partes.length != SEGMENTOS_SEM_PUB_INCM && partes.length != SEGMENTOS_SEM_PUB_INCM + 1) {
            throw new IdusInvalidoException("IDUS inválido: tem de ter " + SEGMENTOS_SEM_PUB_INCM + " ou "
                    + (SEGMENTOS_SEM_PUB_INCM + 1) + " segmentos e tem '" + idus + "'.");
        }
        SegmentosIdus segmentos = new SegmentosIdus(partes[0], partes[1], partes[2], partes[3], partes[4], partes[5],
                partes[6], partes[7], partes[8], partes.length > SEGMENTOS_SEM_PUB_INCM ? partes[9] : null);
        compor(segmentos);
        return segmentos;
    }

    private static String validar(String segmento, String valor, int algarismos) {
        if (valor == null || valor.length() != algarismos || !soAlgarismos(valor)) {
            throw new IdusInvalidoException("Segmento " + segmento + " do IDUS inválido: tem de ter " + algarismos
                    + " algarismos e tem '" + valor + "'.");
        }
        return valor;
    }

    private static boolean soAlgarismos(String valor) {
        return valor.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
