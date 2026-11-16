package com.conkord.saigt.service.idus;

/**
 * Os dez segmentos do IDUS, já como texto com os algarismos que lhes cabem. `pubIncm` é nulo até haver publicação.
 * Quem os valida e junta é {@link ComposicaoIdus}.
 */
public record SegmentosIdus(String sistema, String dico, String igt, String idIgt, String dinamica,
                            String ordemDinamica, String tentativa, String procedimento, String submissao,
                            String pubIncm) {

    public SegmentosIdus comSubmissao(String novaSubmissao) {
        return new SegmentosIdus(sistema, dico, igt, idIgt, dinamica, ordemDinamica, tentativa, procedimento,
                novaSubmissao, pubIncm);
    }
    public SegmentosIdus comIgtEOrdem(String novoIdIgt, String novaOrdemDinamica) {
        return new SegmentosIdus(sistema, dico, igt, novoIdIgt, dinamica, novaOrdemDinamica, tentativa, procedimento,
                submissao, pubIncm);
    }


    public SegmentosIdus comPubIncm(String novaPubIncm) {
        return new SegmentosIdus(sistema, dico, igt, idIgt, dinamica, ordemDinamica, tentativa, procedimento,
                submissao, novaPubIncm);
    }
}
