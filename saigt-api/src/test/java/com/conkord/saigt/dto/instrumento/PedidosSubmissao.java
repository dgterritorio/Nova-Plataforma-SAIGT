package com.conkord.saigt.dto.instrumento;

import java.time.LocalDate;
import java.util.List;

/** Pedidos de submissão com as confirmações que todos os formulários exigem, para testes de outras regras. */
public final class PedidosSubmissao {

    public static final List<String> CONFIRMACOES = List.of("CONTEUDO_PROCESSO");
    /** Fragmento JSON a juntar a um corpo de `PATCH /submeter` escrito à mão. */
    public static final String JSON_CONFIRMACOES = "\"confirmacoes\":[\"CONTEUDO_PROCESSO\"]";

    private PedidosSubmissao() {
    }

    public static SubmeterProcessoRequest confirmado(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                                      String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento) {
        return new SubmeterProcessoRequest(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento,
                dataSuplemento, null, CONFIRMACOES);
    }
}
