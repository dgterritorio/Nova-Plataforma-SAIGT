package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessoDocumentoRepository extends JpaRepository<ProcessoDocumento, UUID> {

    String FORA_DE_SUBSTITUIDOS = "and d.caminhoFicheiro not like '%/SUBSTITUIDOS/%' ";
    String SEM_SUBSTITUIDO = "and d.estadoValidacao <> com.conkord.saigt.persistence.operacional.EstadoValidacao.SUBSTITUIDO ";
    String EXCLUIR = "and (:excluirId is null or d.id <> :excluirId)";

    /** Com as tags e o autor na mesma consulta: a estrutura documental e a completude leem-nos em cada ficheiro. */
    @EntityGraph(attributePaths = {"tags", "criadoPor"})
    List<ProcessoDocumento> findByProcesso_IdOrderByCriadoEmAsc(UUID processoId);

    boolean existsByDocumentoConfig_Id(Long documentoConfigId);

    boolean existsByProcesso_Id(UUID processoId);

    /** Verdadeiro se o Processo tem algum documento num estado fora de `estados`. */
    boolean existsByProcessoIdAndEstadoValidacaoNotIn(UUID processoId, Collection<EstadoValidacao> estados);

    boolean existsByProcessoIdAndEstadoValidacao(UUID processoId, EstadoValidacao estado);

    /** Os documentos atuais do Processo nos itens com estes códigos, com o item carregado. */
    @Query("select d from ProcessoDocumento d join fetch d.documentoConfig c where d.processo.id = :processoId "
            + "and c.codigo in :codigos " + FORA_DE_SUBSTITUIDOS + "order by d.criadoEm asc, d.id asc")
    List<ProcessoDocumento> findAtuaisDoProcessoPorCodigos(@Param("processoId") UUID processoId,
                                                           @Param("codigos") Collection<String> codigos);

    /** A ordem é fixa na consulta (`id` desempata) para as páginas não se repetirem nem saltarem linhas. */
    @Query(value = "select d from ProcessoDocumento d join fetch d.criadoPor "
            + "where d.processo.id = :processoId "
            + "and (:categoria is null or d.categoria = :categoria) "
            + "and (:documentoConfigId is null or d.documentoConfig.id = :documentoConfigId) "
            + "and (:estadoValidacao is null or d.estadoValidacao = :estadoValidacao) "
            + "order by d.criadoEm asc, d.id asc",
            countQuery = "select count(d) from ProcessoDocumento d "
                    + "where d.processo.id = :processoId "
                    + "and (:categoria is null or d.categoria = :categoria) "
                    + "and (:documentoConfigId is null or d.documentoConfig.id = :documentoConfigId) "
                    + "and (:estadoValidacao is null or d.estadoValidacao = :estadoValidacao)")
    Page<ProcessoDocumento> pesquisar(@Param("processoId") UUID processoId,
                                      @Param("categoria") CategoriaDocumento categoria,
                                      @Param("documentoConfigId") Long documentoConfigId,
                                      @Param("estadoValidacao") EstadoValidacao estadoValidacao,
                                      Pageable pageable);

    /** Os ficheiros de vários Processos numa só consulta, com o autor, para o histórico documental. */
    @EntityGraph(attributePaths = {"criadoPor", "tags"})
    List<ProcessoDocumento> findByProcesso_IdInOrderByCriadoEmAsc(Collection<UUID> processoIds);

    /** Vazio quando o documento existe mas é de outro Processo: o chamador responde 404. */
    Optional<ProcessoDocumento> findByIdAndProcesso_Id(UUID id, UUID processoId);

    /** Dos caminhos dados, os que têm linha: a limpeza noturna consulta em lote, nunca objeto a objeto. */
    @Query("select d.caminhoFicheiro from ProcessoDocumento d where d.caminhoFicheiro in :caminhos")
    List<String> findCaminhosExistentes(@Param("caminhos") Collection<String> caminhos);

    @Query("select coalesce(sum(d.tamanhoBytes), 0) from ProcessoDocumento d "
            + "where d.processo.id = :processoId " + FORA_DE_SUBSTITUIDOS + SEM_SUBSTITUIDO + EXCLUIR)
    long somaTamanhoDoProcesso(@Param("processoId") UUID processoId, @Param("excluirId") UUID excluirId);

    @Query("select coalesce(sum(d.tamanhoBytes), 0) from ProcessoDocumento d "
            + "where d.processo.id = :processoId and d.documentoConfig.id = :documentoConfigId "
            + FORA_DE_SUBSTITUIDOS + SEM_SUBSTITUIDO + EXCLUIR)
    long somaTamanhoDoItem(@Param("processoId") UUID processoId, @Param("documentoConfigId") Long documentoConfigId,
                           @Param("excluirId") UUID excluirId);

    @Query("select count(d) from ProcessoDocumento d "
            + "where d.processo.id = :processoId and d.documentoConfig.id = :documentoConfigId "
            + FORA_DE_SUBSTITUIDOS + SEM_SUBSTITUIDO + EXCLUIR)
    long contarDoItem(@Param("processoId") UUID processoId, @Param("documentoConfigId") Long documentoConfigId,
                      @Param("excluirId") UUID excluirId);

    /**
     * Bloqueia os ficheiros atuais do Processo com `estadoValidacao = validado` e desbloqueia os restantes. Devolve
     * as linhas tocadas (todas as atuais do Processo), não as bloqueadas. Os substituídos ficam como estão: já só
     * se podem consultar.
     * <p>
     * Não incrementa `versao` (@Version): o JPQL em lote passa ao lado do optimistic locking da entidade. Só corre
     * no `corrigir`, antes de o Processo voltar a `EM_SUBMISSAO` no commit, e substituir/remover só são aceites em
     * `EM_SUBMISSAO`, pelo que não há escrita concorrente sobre estes ficheiros a detetar.
     * Sem `clearAutomatically`: quem chama não tem `ProcessoDocumento` no contexto e ainda usa o `Processo`.
     */
    @Modifying(flushAutomatically = true)
    @Query("update ProcessoDocumento d set "
            + "d.bloqueadoEdicao = case when d.estadoValidacao = :validado then true else false end, "
            + "d.atualizadoEm = CURRENT_INSTANT "
            + "where d.processo.id = :processoId " + FORA_DE_SUBSTITUIDOS + SEM_SUBSTITUIDO)
    int bloquearSoValidados(@Param("processoId") UUID processoId, @Param("validado") EstadoValidacao validado);

    /** Bloqueia todos os ficheiros atuais do Processo. Não incrementa `versao`: ver {@link #bloquearSoValidados}. */
    @Modifying(flushAutomatically = true)
    @Query("update ProcessoDocumento d set d.bloqueadoEdicao = true, d.atualizadoEm = CURRENT_INSTANT "
            + "where d.processo.id = :processoId " + FORA_DE_SUBSTITUIDOS + SEM_SUBSTITUIDO)
    int bloquearTodos(@Param("processoId") UUID processoId);

    @Query("select count(d) from ProcessoDocumento d where d.processo.id = :processoId and d.bloqueadoEdicao = true "
            + FORA_DE_SUBSTITUIDOS + SEM_SUBSTITUIDO)
    long contarBloqueadosAtuais(@Param("processoId") UUID processoId);

    /**
     * Nativa: o processo liga-se à auditoria pelo texto do id. Total e média por item documental (pelo nome, que se
     * repete entre Procedimentos) dos ficheiros dos processos submetidos pelo menos uma vez; os documentos extra,
     * sem item, não entram.
     */
    @Query(value = "SELECT dc.nome AS rotulo, SUM(pd.tamanho_bytes)::bigint AS total, AVG(pd.tamanho_bytes) AS media "
            + "FROM processo_documento pd JOIN documento_config dc ON dc.id = pd.documento_config_id "
            + "WHERE EXISTS (SELECT 1 FROM auditoria a WHERE a.tipo_ocorrencia = 'PROCESSO' AND a.acao = 'SUBMETER' "
            + "AND a.id_ocorrencia = pd.processo_id::text) "
            + "GROUP BY dc.nome ORDER BY dc.nome",
            nativeQuery = true)
    List<TamanhoPorItemProjecao> somarTamanhosPorItemDosSubmetidos();

    interface TamanhoPorItemProjecao {
        String getRotulo();

        long getTotal();

        BigDecimal getMedia();
    }
}
