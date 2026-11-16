package com.conkord.saigt.persistence.operacional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DinamicaRepository extends JpaRepository<Dinamica, UUID> {

    /** O Instrumento `i` tem um concelho em comum com o Instrumento `:instrumentoId` (`instrumento_concelho`). */
    String CONCELHO_COMUM = "EXISTS (SELECT 1 FROM InstrumentoConcelho ic, InstrumentoConcelho oc "
            + "WHERE ic.instrumento = i AND oc.id.instrumentoId = :instrumentoId "
            + "AND oc.id.municipioCodigo = ic.id.municipioCodigo)";

    /**
     * Dinâmica `d` não terminada e Instrumento `i` que ela abrange, de um dos tipos e com concelho comum; entre os
     * vários que cumprem, só o primeiro por designação (e id), para cada Dinâmica dar uma só linha.
     */
    String ASSOCIAVEL = "d.estado <> TERMINADA AND i.tipoInstrumento.id IN :tipos AND " + CONCELHO_COMUM
            + " AND NOT EXISTS (SELECT 1 FROM Instrumento j, InstrumentoConcelho jc, InstrumentoConcelho jo "
            + "WHERE j MEMBER OF d.instrumentos AND j.tipoInstrumento.id IN :tipos AND jc.instrumento = j "
            + "AND jo.id.instrumentoId = :instrumentoId AND jo.id.municipioCodigo = jc.id.municipioCodigo "
            + "AND (j.designacao < i.designacao OR (j.designacao = i.designacao AND j.id < i.id)))";

    /**
     * As Dinâmicas que um processo do Instrumento pode associar, com o Instrumento associável e os tipos já lidos:
     * uma só ida à BD por página, paginada na BD. Da mais recente para a mais antiga.
     */
    @Query(value = "SELECT d AS dinamica, i AS instrumento FROM Dinamica d "
            + "JOIN FETCH d.instrumentoDinamicaConfig c JOIN FETCH c.tipoDinamica "
            + "JOIN d.instrumentos i JOIN FETCH i.tipoInstrumento WHERE " + ASSOCIAVEL
            + " ORDER BY d.dataInicio DESC, d.id",
            countQuery = "SELECT COUNT(d) FROM Dinamica d JOIN d.instrumentos i WHERE " + ASSOCIAVEL)
    Page<DinamicaAssociavel> listarAssociaveis(@Param("instrumentoId") UUID instrumentoId,
                                               @Param("tipos") Collection<Long> tiposAssociaveis, Pageable pagina);

    /** A mesma regra de {@link #listarAssociaveis} para uma só Dinâmica. */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dinamica d JOIN d.instrumentos i "
            + "WHERE d.id = :dinamicaId AND d.estado <> TERMINADA AND i.tipoInstrumento.id IN :tipos AND "
            + CONCELHO_COMUM)
    boolean existeAssociavel(@Param("instrumentoId") UUID instrumentoId,
                             @Param("tipos") Collection<Long> tiposAssociaveis, @Param("dinamicaId") UUID dinamicaId);

    /** As configurações, de entre as dadas, com pelo menos uma Dinâmica: uma consulta por página, nunca por linha. */
    @Query("SELECT DISTINCT d.instrumentoDinamicaConfig.id FROM Dinamica d WHERE d.instrumentoDinamicaConfig.id IN :ids")
    Set<Long> configsComDinamicas(@Param("ids") Collection<Long> ids);

    /**
     * Lê a Dinâmica com bloqueio de escrita: dois pedidos de criação na mesma Dinâmica passam um de cada vez,
     * e o segundo vê o processo que o primeiro criou. Exige transação.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Dinamica d WHERE d.id = :id")
    Optional<Dinamica> findByIdParaCriarProcesso(@Param("id") UUID id);

    /*
     * As consultas "Abrangendo" leem `dinamica_instrumento` (os Instrumentos que a Dinâmica abrange), não
     * `dinamica.instrumento_id`: uma Dinâmica que abranja vários Instrumentos conta em cada um.
     * Uma só ida à BD cada; usam `idx_dinamica_instrumento_instrumento`.
     */

    /** Avaliadores `OCORRENCIA_EM_ESTADO`/`OCORRENCIA_NAO_EXISTE` com `tipo_ocorrencia = DINAMICA`. */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dinamica d JOIN d.instrumentos i "
            + "WHERE i.id = :instrumentoId AND d.instrumentoDinamicaConfig.tipoDinamica.codigo = :codigoTipoDinamica "
            + "AND d.estado = :estado")
    boolean existeAbrangendoComTipoEEstado(@Param("instrumentoId") UUID instrumentoId,
                                           @Param("codigoTipoDinamica") String codigoTipoDinamica,
                                           @Param("estado") EstadoDinamica estado);

    /** Para `OCORRENCIA_NAO_EXISTE` sem `estado`. */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dinamica d JOIN d.instrumentos i "
            + "WHERE i.id = :instrumentoId AND d.instrumentoDinamicaConfig.tipoDinamica.codigo = :codigoTipoDinamica")
    boolean existeAbrangendoComTipo(@Param("instrumentoId") UUID instrumentoId,
                                    @Param("codigoTipoDinamica") String codigoTipoDinamica);

    /** Para `OCORRENCIA_NAO_EXISTE` de `DINAMICA` sem `codigo`: qualquer tipo de Dinâmica. */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dinamica d JOIN d.instrumentos i "
            + "WHERE i.id = :instrumentoId AND d.estado = :estado")
    boolean existeAbrangendoComEstado(@Param("instrumentoId") UUID instrumentoId, @Param("estado") EstadoDinamica estado);

    /** Para `OCORRENCIA_NAO_EXISTE` de `DINAMICA` sem `codigo` nem `estado`. */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dinamica d JOIN d.instrumentos i "
            + "WHERE i.id = :instrumentoId")
    boolean existeAbrangendo(@Param("instrumentoId") UUID instrumentoId);

    /** `DINAMICA_BLOQUEADA_LEGADO`. */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dinamica d JOIN d.instrumentos i "
            + "WHERE i.id = :instrumentoId AND d.origemDados = :origemDados "
            + "AND d.instrumentoDinamicaConfig.tipoDinamica.codigo = :codigoTipoDinamica")
    boolean existeAbrangendoComOrigemETipo(@Param("instrumentoId") UUID instrumentoId,
                                           @Param("origemDados") String origemDados,
                                           @Param("codigoTipoDinamica") String codigoTipoDinamica);

    /** A Dinâmica no estado dado que abrange o Instrumento com a `data_conclusao` mais recente, com o tipo já lido. */
    @EntityGraph(attributePaths = "instrumentoDinamicaConfig.tipoDinamica")
    @Query("SELECT d FROM Dinamica d JOIN d.instrumentos i WHERE i.id = :instrumentoId AND d.estado = :estado "
            + "ORDER BY d.dataConclusao DESC NULLS LAST, d.id")
    List<Dinamica> listarAbrangendoComEstadoPelaConclusaoMaisRecente(@Param("instrumentoId") UUID instrumentoId,
                                                                     @Param("estado") EstadoDinamica estado,
                                                                     Limit limite);

    /** Usa `idx_dinamica_instrumento`. */
    List<Dinamica> findByInstrumento_IdAndEstado(UUID instrumentoId, EstadoDinamica estado);

    /** Usa `idx_dinamica_instrumento`. */
    List<Dinamica> findByInstrumento_IdAndEstadoIn(UUID instrumentoId, Collection<EstadoDinamica> estados);

    /**
     * As Dinâmicas `TERMINADA` iniciadas a partir do Instrumento cuja configuração tem Procedimentos ativos, não
     * obrigatórios e não `ato_pontual` depois do final (a Prorrogação), numa só consulta: as outras nunca oferecem nada.
     * Os processos já feitos decide-os {@code AutorizacaoService#procedimentosPosterioresAoFinalPorDinamica}.
     */
    @EntityGraph(attributePaths = "instrumentoDinamicaConfig.tipoDinamica")
    @Query("SELECT d FROM Dinamica d WHERE d.instrumento.id = :instrumentoId AND d.estado = TERMINADA "
            + "AND EXISTS (SELECT 1 FROM InstrumentoProcedimentoConfig c WHERE c.instrumentoDinamicaConfig = d.instrumentoDinamicaConfig "
            + "AND c.ativo = true AND c.obrigatorio = false AND c.atoPontual = false "
            + "AND c.ordem > (SELECT MAX(f.ordem) FROM InstrumentoProcedimentoConfig f "
            + "WHERE f.instrumentoDinamicaConfig = d.instrumentoDinamicaConfig AND f.ativo = true AND f.obrigatorio = true "
            + "AND f.atoPontual = false))")
    List<Dinamica> listarTerminadasComProcedimentosPosterioresAoFinal(@Param("instrumentoId") UUID instrumentoId);

    /**
     * Nativa: `jsonb_array_length` não existe em JPQL. Processos fora de `PUBLICADO` e `ANULADO` das Dinâmicas
     * `EM_CURSO` com pelo menos um aviso de caducidade enviado: a caducidade é da Dinâmica, não do processo.
     */
    @Query(value = "SELECT COUNT(*) FROM processo p JOIN dinamica d ON d.id = p.dinamica_id "
            + "WHERE d.estado = 'EM_CURSO' AND jsonb_array_length(COALESCE(d.alertas_enviados, '[]'::jsonb)) > 0 "
            + "AND p.estado NOT IN ('PUBLICADO', 'ANULADO')",
            nativeQuery = true)
    long contarProcessosACaducar();

    /** Usa `idx_dinamica_data_caducidade`. */
    long countByEstadoAndDataCaducidadeBefore(EstadoDinamica estado, LocalDate data);

    /** As Dinâmicas de tipos `inicio_sem_igt` no estado, da mais antiga para a mais recente. */
    @EntityGraph(attributePaths = "instrumentoDinamicaConfig.tipoDinamica")
    @Query("SELECT d FROM Dinamica d WHERE d.instrumentoDinamicaConfig.tipoDinamica.inicioSemIgt = true "
            + "AND d.estado = :estado ORDER BY d.dataInicio, d.id")
    List<Dinamica> listarSemIgtComEstado(@Param("estado") EstadoDinamica estado);

    /** Como {@link #listarSemIgtComEstado}, só as criadas pelo utilizador ou com um processo da entidade. */
    @EntityGraph(attributePaths = "instrumentoDinamicaConfig.tipoDinamica")
    @Query("SELECT d FROM Dinamica d WHERE d.instrumentoDinamicaConfig.tipoDinamica.inicioSemIgt = true "
            + "AND d.estado = :estado AND (d.criadoPor.id = :utilizadorId "
            + "OR EXISTS (SELECT p.id FROM Processo p WHERE p.dinamica = d AND p.entidade.id = :entidadeId)) "
            + "ORDER BY d.dataInicio, d.id")
    List<Dinamica> listarSemIgtDaSessaoComEstado(@Param("estado") EstadoDinamica estado,
                                                 @Param("utilizadorId") UUID utilizadorId,
                                                 @Param("entidadeId") Long entidadeId);

    /** O mesmo `descritivo` noutra Dinâmica do tipo, sem distinguir maiúsculas nem espaços nas pontas. */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dinamica d "
            + "WHERE d.instrumentoDinamicaConfig.tipoDinamica.id = :tipoDinamicaId "
            + "AND lower(trim(d.descritivo)) = lower(trim(:descritivo))")
    boolean existeDoTipoComDescritivo(@Param("tipoDinamicaId") Long tipoDinamicaId, @Param("descritivo") String descritivo);

    /** As Dinâmicas que abrangem o Instrumento, por `dinamica_instrumento`, da mais antiga para a mais recente. */
    @EntityGraph(attributePaths = "instrumentoDinamicaConfig.tipoDinamica")
    @Query("SELECT d FROM Dinamica d JOIN d.instrumentos i WHERE i.id = :instrumentoId ORDER BY d.dataInicio, d.id")
    List<Dinamica> listarQueAbrangem(@Param("instrumentoId") UUID instrumentoId);

    /** As `EM_CURSO` que abrangem algum dos Instrumentos, sem a excluída, da mais antiga para a mais recente. */
    @EntityGraph(attributePaths = "instrumentoDinamicaConfig.tipoDinamica")
    @Query("SELECT DISTINCT d FROM Dinamica d JOIN d.instrumentos i WHERE i.id IN :instrumentoIds "
            + "AND d.id <> :excluirDinamicaId AND d.estado = EM_CURSO ORDER BY d.dataInicio, d.id")
    List<Dinamica> listarEmCursoQueAbrangem(@Param("instrumentoIds") Collection<UUID> instrumentoIds,
                                            @Param("excluirDinamicaId") UUID excluirDinamicaId);

    /** As `EM_CURSO` com `data_caducidade` até ao limite, da mais próxima para a mais distante. Usa `idx_dinamica_data_caducidade`. */
    @Query("SELECT d.id FROM Dinamica d WHERE d.estado = EM_CURSO AND d.dataCaducidade IS NOT NULL "
            + "AND d.dataCaducidade <= :limite ORDER BY d.dataCaducidade, d.id")
    List<UUID> findIdsParaVerificacaoCaducidade(@Param("limite") LocalDate limite);

    /**
     * As escritas de caducidade são condicionais porque `Dinamica` não tem `@Version` e a publicação do
     * procedimento final pode passá-la a `TERMINADA` ao mesmo tempo. Devolve 0 se o estado já mudou.
     */
    @Modifying
    @Query("UPDATE Dinamica d SET d.estado = CADUCADA, d.atualizadoEm = CURRENT_INSTANT "
            + "WHERE d.id = :id AND d.estado = EM_CURSO")
    int marcarCaducada(@Param("id") UUID id);

    /** Mesma condição de {@link #marcarCaducada}. */
    @Modifying
    @Query("UPDATE Dinamica d SET d.alertasEnviados = :alertas, d.atualizadoEm = CURRENT_INSTANT "
            + "WHERE d.id = :id AND d.estado = EM_CURSO")
    int registarAlertas(@Param("id") UUID id, @Param("alertas") List<String> alertas);
}
