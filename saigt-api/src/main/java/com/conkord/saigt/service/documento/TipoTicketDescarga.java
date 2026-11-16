package com.conkord.saigt.service.documento;

/** O tipo vai no ticket para a descarga saber de que tabela lê o objeto. */
public enum TipoTicketDescarga {
    DOCUMENTO_PROCESSO('D'),
    ANEXO_NOTIFICACAO('N');

    private final char codigo;

    TipoTicketDescarga(char codigo) {
        this.codigo = codigo;
    }

    char codigo() {
        return codigo;
    }

    static TipoTicketDescarga de(char codigo) {
        for (TipoTicketDescarga tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de ticket desconhecido.");
    }
}
