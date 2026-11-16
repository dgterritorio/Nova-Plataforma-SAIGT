package com.conkord.saigt.service.relatorio;

import java.util.Map;

/**
 * Os rótulos que o ecrã mostra (`EstadoProcessoBadgeComponent` no saigt-ui), para o ficheiro exportado dizer o
 * mesmo que a listagem. Ao mudar um rótulo lá, muda-se aqui.
 */
final class RotulosEstadoProcesso {

    private static final Map<String, String> ROTULOS = Map.of(
            "EM_SUBMISSAO", "Em submissão",
            "ENVIADO_DGT", "Enviado DGT",
            "EM_VALIDACAO_DGT", "Em Validação DGT",
            "EM_VALIDACAO_INCM", "Em validação INCM",
            "VALIDADO_DGT", "Validado DGT",
            "REJEITADO_DGT", "Rejeitado DGT",
            "ANULADO", "Anulado",
            "ANULADO_INCM", "Anulado INCM",
            "ENVIADO_INCM", "Enviado INCM",
            "PUBLICADO", "Publicado");

    private RotulosEstadoProcesso() {
    }

    /** Um estado sem rótulo sai tal como vem, nunca em branco, como no ecrã. */
    static String rotulo(String estado) {
        return ROTULOS.getOrDefault(estado, estado);
    }
}
