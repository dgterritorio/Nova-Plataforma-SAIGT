package com.conkord.saigt.service.documento;

/**
 * Não foi possível inspecionar o TIFF (executável em falta, tempo esgotado, storage, resposta ilegível): o ficheiro
 * não é aceite sem inspeção. A mensagem só tem a causa técnica, nunca credenciais, chave do objeto ou nome do
 * ficheiro; sai como 500 sem detalhe.
 */
public class InspecaoTiffIndisponivelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InspecaoTiffIndisponivelException(String causa) {
        super(causa);
    }
}
