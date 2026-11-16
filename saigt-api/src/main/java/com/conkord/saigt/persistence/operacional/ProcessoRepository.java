package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProcessoRepository extends JpaRepository<Processo, UUID>, JpaSpecificationExecutor<Processo> {

    /**
     * Processos com a origem e o estado dados sem a auditoria da ação de retenção dada: a retenção depois do commit
     * falhou e o passo tem de ser repetido.
     */
    @Query("SELECT p.id FROM Processo p WHERE p.origemDados = :origemDados AND p.estado = :estado AND NOT EXISTS ("
            + "SELECT a.id FROM Auditoria a WHERE a.tipoOcorrencia = :tipoOcorrencia AND a.acao = :acao "
            + "AND a.idOcorrencia = cast(p.id as String))")
    List<UUID> idsSemAuditoria(@Param("origemDados") String origemDados, @Param("estado") EstadoProcesso estado,
                               @Param("tipoOcorrencia") String tipoOcorrencia, @Param("acao") String acao);

    /** As configurações, de entre as dadas, com pelo menos um processo: uma consulta por página, nunca por linha. */
    @Query("SELECT DISTINCT p.instrumentoProcedimentoConfig.id FROM Processo p WHERE p.instrumentoProcedimentoConfig.id IN :ids")
    Set<Long> configsComProcessos(@Param("ids") Collection<Long> ids);

    @Query("SELECT p.id FROM Processo p WHERE p.numeroProcesso = :numero")
    Optional<UUID> findIdByNumeroProcesso(@Param("numero") String numero);

    /** A entidade tem pelo menos um processo na Dinâmica: dá-lhe leitura dos Procedimentos `visivel_na_dinamica`. */
    boolean existsByDinamicaAndEntidade_Id(Dinamica dinamica, Long entidadeId);

    /** O tipo de ato já está num processo: o seu `codigo` não muda (409 `EM_USO`). */
    boolean existsByTipoAto_Id(Long tipoAtoId);
    boolean existsByEntidadeId(Long entidadeId);

    /** Usa a unicidade de `numero_deposito`. */
    Optional<Processo> findByNumeroDeposito(String numeroDeposito);

    /** Usa a unicidade de `numero_processo`. */
    Optional<Processo> findByNumeroProcesso(String numeroProcesso);

    /** O mais recente da Dinâmica com o Procedimento e o estado dados (por exemplo, a Decisão de início publicada). */
    Optional<Processo> findFirstByDinamicaAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByCriadoEmDesc(
            Dinamica dinamica, String codigoProcedimento, EstadoProcesso estado);

    Optional<Processo> findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByDataPublicacaoDrDesc(
            UUID dinamicaId, String codigoProcedimento, EstadoProcesso estado);

    Optional<Processo> findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByOcorrenciaDesc(
            UUID dinamicaId, String codigoProcedimento, EstadoProcesso estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Processo p WHERE p.id = :id")
    Optional<Processo> findByIdComBloqueio(@Param("id") UUID id);

    /** Qualquer `ocorrencia` conta. Usa `idx_processo_dinamica_config`. */
    boolean existsByDinamicaAndInstrumentoProcedimentoConfigAndEstado(
            Dinamica dinamica, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig, EstadoProcesso estado);

    /** O sequenciamento conta só a Tentativa dada: uma nova Tentativa recomeça do primeiro Procedimento. */
    boolean existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(
            Dinamica dinamica, int tentativa, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig,
            EstadoProcesso estado);

    /** Em lote, evita N+1. */
    List<Processo> findByDinamicaAndInstrumentoProcedimentoConfigInAndEstado(
            Dinamica dinamica, List<InstrumentoProcedimentoConfig> instrumentoProcedimentoConfigs, EstadoProcesso estado);

    /** Os processos de várias Dinâmicas nos Procedimentos dados, numa só consulta; a Tentativa filtra quem chama. */
    List<Processo> findByDinamicaInAndInstrumentoProcedimentoConfigIn(
            Collection<Dinamica> dinamicas, Collection<InstrumentoProcedimentoConfig> instrumentoProcedimentoConfigs);

    /** Todas as ocorrências da Tentativa, não só as publicadas: é preciso o estado da última de cada Procedimento. */
    List<Processo> findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(
            Dinamica dinamica, int tentativa, List<InstrumentoProcedimentoConfig> instrumentoProcedimentoConfigs);

    /**
     * A ocorrência de um Procedimento é o par `dinamica_id + instrumento_procedimento_config_id`
     * (não há tabela `procedimento`); qualquer `ocorrencia` conta. O Instrumento é qualquer um dos que a Dinâmica
     * do processo abrange (`dinamica_instrumento`). Uma só ida à BD.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Processo p JOIN p.dinamica d "
            + "JOIN d.instrumentos i WHERE i.id = :instrumentoId "
            + "AND p.instrumentoProcedimentoConfig.tipoProcedimento.codigo = :codigoTipoProcedimento AND p.estado = :estado")
    boolean existeProcessoAbrangendoComTipoEEstado(@Param("instrumentoId") UUID instrumentoId,
                                                   @Param("codigoTipoProcedimento") String codigoTipoProcedimento,
                                                   @Param("estado") EstadoProcesso estado);

    /** Para `OCORRENCIA_NAO_EXISTE` sem `estado`. */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Processo p JOIN p.dinamica d "
            + "JOIN d.instrumentos i WHERE i.id = :instrumentoId "
            + "AND p.instrumentoProcedimentoConfig.tipoProcedimento.codigo = :codigoTipoProcedimento")
    boolean existeProcessoAbrangendoComTipo(@Param("instrumentoId") UUID instrumentoId,
                                            @Param("codigoTipoProcedimento") String codigoTipoProcedimento);

    /**
     * Decide a `ocorrencia` seguinte dentro da Tentativa e se pode ser criada outra (só após `ANULADO`).
     * Usa `uq_processo_ocorrencia`.
     */
    Optional<Processo> findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(
            Dinamica dinamica, int tentativa, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig);

    List<Processo> findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(
            Dinamica dinamica, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig);

    /**
     * Os processos da Tentativa da Dinâmica fora de `estadosExcluidos` e de Procedimentos `ato_pontual`, numa só
     * consulta. O `JOIN FETCH` traz a config, que quem chama lê sem nova ida à BD.
     */
    @Query("SELECT p FROM Processo p JOIN FETCH p.instrumentoProcedimentoConfig c "
            + "WHERE p.dinamica = :dinamica AND p.tentativa = :tentativa AND p.estado NOT IN :estadosExcluidos "
            + "AND c.atoPontual = false")
    List<Processo> findEmCursoDaTentativa(@Param("dinamica") Dinamica dinamica, @Param("tentativa") int tentativa,
                                          @Param("estadosExcluidos") Collection<EstadoProcesso> estadosExcluidos);

    /** Os processos de várias Dinâmicas numa só consulta, para o painel do Instrumento. */
    @EntityGraph(attributePaths = {"instrumentoProcedimentoConfig", "dinamica.instrumento", "entidade", "processoReferido"})
    List<Processo> findByDinamicaInOrderByOcorrenciaAsc(Collection<Dinamica> dinamicas);

    /** Os processos de uma Dinâmica num estado e com o tipo de Procedimento numa lista de códigos, pela publicação. */
    @EntityGraph(attributePaths = {"instrumentoProcedimentoConfig.tipoProcedimento"})
    List<Processo> findByDinamicaAndEstadoAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoInOrderByDataPublicacaoDrAsc(
            Dinamica dinamica, EstadoProcesso estado, Collection<String> codigos);

    @EntityGraph(attributePaths = {"instrumentoProcedimentoConfig.tipoProcedimento", "entidade"})
    List<Processo> findByDinamica(Dinamica dinamica);

    /** Os mais antigos primeiro: se o job for interrompido, os que esperam há mais tempo já foram consultados. */
    @Query("SELECT p.id FROM Processo p WHERE p.estado IN ("
            + "com.conkord.saigt.persistence.operacional.EstadoProcesso.ENVIADO_INCM, "
            + "com.conkord.saigt.persistence.operacional.EstadoProcesso.EM_VALIDACAO_INCM) "
            + "AND p.incmActIdentifier IS NOT NULL ORDER BY p.atualizadoEm ASC")
    List<UUID> findIdsParaConsultaIncm();

    /** Os mais antigos primeiro: se o job for interrompido, os mais atrasados já foram tratados. */
    @Query("SELECT p.id FROM Processo p WHERE p.estado = "
            + "com.conkord.saigt.persistence.operacional.EstadoProcesso.EM_SUBMISSAO "
            + "AND p.atualizadoEm <= :limite ORDER BY p.atualizadoEm ASC, p.id")
    List<UUID> findIdsEmSubmissaoSemAtualizacaoDesde(@Param("limite") Instant limite);

    long countByEstado(EstadoProcesso estado);

    long countByEstadoAndAtualizadoEmLessThanEqual(EstadoProcesso estado, Instant limite);

    /**
     * Nativa: diferença de datas no fuso de Lisboa. Média, em dias, da primeira submissão à publicação em DR. A
     * primeira submissão é a linha `SUBMETER` mais antiga da auditoria (`data_submissao` muda em cada resubmissão);
     * sem nenhuma (migrados, registo externo) ou sem `data_publicacao_dr`, o processo não conta. Nula sem processos.
     */
    @Query(value = "SELECT AVG(p.data_publicacao_dr - (s.primeira AT TIME ZONE 'Europe/Lisbon')::date) "
            + "FROM processo p JOIN (SELECT a.id_ocorrencia, MIN(a.criado_em) AS primeira FROM auditoria a "
            + "WHERE a.tipo_ocorrencia = 'PROCESSO' AND a.acao = 'SUBMETER' GROUP BY a.id_ocorrencia) s "
            + "ON s.id_ocorrencia = p.id::text "
            + "WHERE p.estado = 'PUBLICADO' AND p.data_publicacao_dr IS NOT NULL",
            nativeQuery = true)
    BigDecimal mediaDiasDeInstrucao();

    long countByEstadoIn(Collection<EstadoProcesso> estados);


    @Query("SELECT p.estado AS estado, COUNT(p) AS total FROM Processo p GROUP BY p.estado")
    List<EstadoContagemProjecao> contarPorEstado();

    /**
     * Nativa: `date_trunc` não existe em JPQL. Conta pela `data_submissao`, mesmo que o Processo
     * tenha avançado depois.
     */
    @Query(
            value = "SELECT date_trunc('month', data_submissao)::date AS mes, COUNT(*) AS total "
                    + "FROM processo WHERE data_submissao IS NOT NULL GROUP BY mes ORDER BY mes",
            nativeQuery = true)
    List<SubmissaoMensalProjecao> contarSubmissoesPorMes();

    /**
     * Nativa: `date_trunc` não existe em JPQL. Conta pela `data_publicacao_dr`; um `PUBLICADO` sem essa data
     * (só para depósito) fica de fora.
     */
    @Query(
            value = "SELECT date_trunc('month', data_publicacao_dr)::date AS mes, COUNT(*) AS total "
                    + "FROM processo WHERE estado = 'PUBLICADO' AND data_publicacao_dr IS NOT NULL "
                    + "GROUP BY mes ORDER BY mes",
            nativeQuery = true)
    List<PublicadosMensalProjecao> contarPublicadosPorMes();

    /**
     * Pela configuração da Dinâmica, sempre presente: o Instrumento de partida é nulo numa Dinâmica iniciada sem
     * IGT, e esses processos também contam.
     */
    @Query("SELECT p.dinamica.instrumentoDinamicaConfig.tipoInstrumento.codigo AS codigo, COUNT(p) AS total "
            + "FROM Processo p GROUP BY p.dinamica.instrumentoDinamicaConfig.tipoInstrumento.codigo "
            + "ORDER BY p.dinamica.instrumentoDinamicaConfig.tipoInstrumento.codigo")
    List<ContagemPorTipoInstrumentoProjecao> contarPorTipoInstrumento();

    /** Uma só consulta agregada para todos os workflows, evita N+1. */
    @Query("SELECT p.dinamica.instrumentoDinamicaConfig.id AS configId, COUNT(p) AS total "
            + "FROM Processo p WHERE p.estado NOT IN :estadosTerminais "
            + "GROUP BY p.dinamica.instrumentoDinamicaConfig.id")
    List<ContagemProcessosAtivosProjecao> contarProcessosAtivosPorConfigDinamica(
            @Param("estadosTerminais") Collection<EstadoProcesso> estadosTerminais);

    /** O pivot por tipo de instrumento é feito em Java. */
    @Query("SELECT p.entidade.descricaoIncm AS entidadeNome, "
            + "p.dinamica.instrumentoDinamicaConfig.tipoInstrumento.codigo AS tipoInstrumentoCodigo, COUNT(p) AS total "
            + "FROM Processo p WHERE p.estado IN :estadosSucesso "
            + "GROUP BY p.entidade.descricaoIncm, p.dinamica.instrumentoDinamicaConfig.tipoInstrumento.codigo")
    List<PublicadosPorEntidadeETipoProjecao> contarPublicadosPorEntidadeETipo(
            @Param("estadosSucesso") Collection<EstadoProcesso> estadosSucesso);

    @Query("SELECT p.entidade.descricaoIncm AS entidadeNome, COUNT(p) AS total "
            + "FROM Processo p WHERE p.estado IN :estadosCaducadosAnulados "
            + "GROUP BY p.entidade.descricaoIncm")
    List<ContagemPorEntidadeProjecao> contarCaducadosPorEntidade(
            @Param("estadosCaducadosAnulados") Collection<EstadoProcesso> estadosCaducadosAnulados);

    /** `data_publicacao_dr` só é preenchida pela integração INCM: `null` é um valor legítimo. */
    @Query("SELECT p.entidade.descricaoIncm AS entidadeNome, MAX(p.dataPublicacaoDr) AS ultimaPublicacao "
            + "FROM Processo p WHERE p.estado IN :estadosSucesso "
            + "GROUP BY p.entidade.descricaoIncm")
    List<UltimaPublicacaoPorEntidadeProjecao> ultimaPublicacaoPorEntidade(
            @Param("estadosSucesso") Collection<EstadoProcesso> estadosSucesso);

    interface EstadoContagemProjecao {
        EstadoProcesso getEstado();

        long getTotal();
    }

    interface SubmissaoMensalProjecao {
        LocalDate getMes();

        long getTotal();
    }

    interface PublicadosMensalProjecao {
        LocalDate getMes();

        long getTotal();
    }

    interface ContagemPorTipoInstrumentoProjecao {
        String getCodigo();

        long getTotal();
    }

    interface ContagemProcessosAtivosProjecao {
        Long getConfigId();

        long getTotal();
    }

    interface PublicadosPorEntidadeETipoProjecao {
        String getEntidadeNome();

        String getTipoInstrumentoCodigo();

        long getTotal();
    }

    interface ContagemPorEntidadeProjecao {
        String getEntidadeNome();

        long getTotal();
    }

    interface UltimaPublicacaoPorEntidadeProjecao {
        String getEntidadeNome();

        LocalDate getUltimaPublicacao();
    }
}
