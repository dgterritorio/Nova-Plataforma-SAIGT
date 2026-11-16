package com.conkord.saigt.exception;

public class NotificacaoTipoSemResolucaoManualException extends EstadoInvalidoException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "NOTIF_TIPO_SEM_RESOLUCAO_MANUAL";

    protected NotificacaoTipoSemResolucaoManualException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public static NotificacaoTipoSemResolucaoManualException regularizacao() {
        return new NotificacaoTipoSemResolucaoManualException(CODIGO,
                "Esta notificação resolve-se com o registo do procedimento.");
    }

    public static NotificacaoTipoSemResolucaoManualException outroTipo() {
        return new NotificacaoTipoSemResolucaoManualException(CODIGO, "Esta notificação não se resolve manualmente.");
    }
}
