package com.conkord.saigt.service.documento;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Object storage dos documentos; o serviço não conhece a implementação. */
public interface ArmazenamentoDocumentos {

    /**
     * Grava o stream até ao fim, sem conhecer o tamanho nem o guardar em disco. Devolve o `versionId` do objeto
     * gravado; nulo quando o bucket não tem versionamento.
     */
    String guardar(String caminho, InputStream conteudo, String contentType);

    /**
     * Cópia server-side da versão `versaoOrigem` (nula: a corrente): os bytes não passam pelo backend e o objeto de
     * origem mantém-se. Devolve o `versionId` da cópia; nulo quando o bucket não tem versionamento.
     */
    String copiar(String origem, String versaoOrigem, String destino);

    /** Idempotente: remover um objeto inexistente não é erro. Tem o efeito de {@link #removerTodasVersoes}. */
    void remover(String caminho);

    void removerTodasVersoes(String caminho);

    /**
     * `DELETE` sem `versionId`: num bucket versionado só acrescenta um marcador de eliminação e as versões ficam, como
     * as retidas exigem.
     */
    void marcarEliminado(String caminho);

    /**
     * Abre o objeto em streaming, a partir de `inicio` e com `comprimento` bytes; quem recebe fecha o stream.
     * `versao` nula lê a versão corrente. Objeto inexistente lança {@link com.conkord.saigt.exception.RecursoNaoEncontradoException};
     * qualquer outra falha antes de haver bytes, {@link com.conkord.saigt.exception.StorageIndisponivelException}.
     */
    InputStream abrir(String caminho, String versao, long inicio, long comprimento);

    /** Versões do objeto com a chave exata, com a retenção de cada uma; vazia se o objeto não existe. */
    List<VersaoObjeto> versoes(String caminho);

    /**
     * Retenção GOVERNANCE da versão dada até `ate`, sem bypass. Uma versão retida não se apaga: {@link #remover}
     * lança {@link com.conkord.saigt.exception.StorageIndisponivelException}.
     */
    void reter(String caminho, String versionId, Instant ate);

    /** Retenção com data no futuro na versão dada; falso num bucket sem Object Lock. */
    boolean temRetencao(String caminho, String versionId);

    /** A data da retenção da versão dada, se for futura; vazio num bucket sem Object Lock. */
    Optional<Instant> retidoAte(String caminho, String versionId);
}
