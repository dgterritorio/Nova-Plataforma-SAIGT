package com.conkord.saigt.exception;

/**
 * Processo desconhecido da SAIGT para o SNIT: inexistente, sem depósito comunicado ou ficheiro fora do manifesto.
 * Sem mensagem própria, a resposta é a mesma nos três casos, para o SNIT não distinguir «não existe» de «não tem
 * acesso». Falha → 404.
 */
public class ProcessoDesconhecidoException extends RecursoNaoEncontradoException {
    private static final long serialVersionUID = 1L;

    public ProcessoDesconhecidoException() {
        this("Depósito não encontrado.");
    }

    public ProcessoDesconhecidoException(String mensagem) {
        super("PROCESSO_DESCONHECIDO", mensagem);
    }
}
