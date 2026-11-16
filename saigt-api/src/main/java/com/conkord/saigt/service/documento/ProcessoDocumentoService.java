package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.ReclassificarDocumentoRequest;
import com.conkord.saigt.exception.CanalGeoconformException;
import com.conkord.saigt.exception.ClassificacaoInvalidaException;
import com.conkord.saigt.exception.DocumentoBloqueadoException;
import com.conkord.saigt.exception.DocumentoRetidoException;
import com.conkord.saigt.exception.DocumentoEstadoInvalidoException;
import com.conkord.saigt.exception.DocumentoVersaoDesatualizadaException;
import com.conkord.saigt.exception.JustificacaoEmFaltaException;
import com.conkord.saigt.exception.NomeDocumentoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Upload, substituição e remoção de documentos do Processo (§14.3, §14.5), sempre mediados pelo backend. O storage
 * não é transacional: um objeto novo é apagado se a transação reverter, um objeto antigo só depois do commit — o
 * pior caso é um objeto sem linha, que a limpeza noturna apaga pela intenção registada, nunca uma linha sem objeto.
 */
@Service
public class ProcessoDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessoDocumentoService.class);
    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_CARREGAR_DOCUMENTO = "CARREGAR_DOCUMENTO";
    private static final String ACAO_SUBSTITUIR_DOCUMENTO = "SUBSTITUIR_DOCUMENTO";
    private static final String ACAO_SUBSTITUIR_DOCUMENTO_RECUSADO = "SUBSTITUIR_DOCUMENTO_RECUSADO";
    private static final String ACAO_SUBSTITUIR_DOCUMENTO_DESBLOQUEADO = "SUBSTITUIR_DOCUMENTO_DESBLOQUEADO";
    private static final String ACAO_REMOVER_DOCUMENTO = "REMOVER_DOCUMENTO";
    private static final String MSG_BLOQUEADO = "O documento está bloqueado e já não pode ser alterado.";
    private static final String MSG_SUBSTITUIDO = "Este ficheiro já foi substituído e só pode ser consultado.";
    private static final String ACAO_DESBLOQUEAR_DOCUMENTO = "DESBLOQUEAR_DOCUMENTO";
    private static final String CAMPO_JUSTIFICACAO = "justificacao";
    private static final String MSG_JUSTIFICACAO_EM_FALTA = "Indique a justificação do desbloqueio.";
    private static final String MSG_DESBLOQUEIO_FORA_DA_CORRECAO =
            "Só é possível desbloquear um ficheiro depois de iniciar a correção.";
    private static final String MSG_NAO_BLOQUEADO = "O ficheiro não está bloqueado.";
    private static final String MSG_DESBLOQUEADO_NAO_SE_REMOVE =
            "Na correção, um ficheiro desbloqueado substitui-se; não se remove.";
    private static final String ACAO_RECLASSIFICAR_DOCUMENTO = "RECLASSIFICAR_DOCUMENTO";
    private static final String MSG_RETIDO_NAO_MUDA_DE_CAIXA = "Este ficheiro está retido e não pode mudar de caixa.";
    private static final String MSG_GEOCONFORM_NAO_SE_RECLASSIFICA =
            "Os ficheiros obtidos do GeoConform não se reclassificam.";
    private static final String CAMPO_CATEGORIA = "categoria";
    private static final String MSG_CATEGORIA_EM_FALTA = "Indique se o ficheiro é para publicação ou depósito.";
    private static final String MSG_CATEGORIA_DIFERENTE = "A categoria não corresponde à do item.";
    private static final String CAMPO_DOCUMENTO_CONFIG_ID = "documentoConfigId";
    private static final String MSG_ITEM_FORA_DO_DESTINO = "Este item não se aplica a um processo só para depósito.";
    private static final String MSG_OUTROS_SO_DEPOSITO = "Num processo só para depósito, os Outros só podem ser de depósito.";
    private static final String MSG_CANAL_GEOCONFORM =
            "Nesta Dinâmica as peças gráficas são obtidas do GeoConform e não podem ser carregadas.";
    private static final String CONTENT_TYPE_POR_OMISSAO = "application/octet-stream";
    private static final int TAMANHO_MAXIMO_NOME_FICHEIRO = 300;
    // `versionId` que o S3 dá aos objetos de um bucket sem versionamento; a coluna é obrigatória.
    private static final String VERSAO_SEM_VERSIONAMENTO = "null";
    private static final int TAMANHO_REVISAO = 8;
    private static final int TAMANHO_MAXIMO_EXTENSAO = 10;

    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final ProcessoAlteravel processoAlteravel;
    private final PlantaService plantaService;
    private final MetadadosMotuService metadadosMotuService;
    private final UtilizadorRepository utilizadorRepository;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AuditoriaService auditoriaService;
    private final ArmazenamentoDocumentos armazenamento;
    private final IntencaoArmazenamentoService intencaoArmazenamento;
    private final LinkPublicoService linkPublicoService;
    private final VerificadorZip verificadorZip;
    private final VerificadorTiff verificadorTiff;
    private final SubstituicaoDocumentosService substituicaoDocumentos;
    private final ValidadorNomeSubstituicao validadorNomeSubstituicao;
    private final TransactionTemplate leitura;
    private final TransactionTemplate escrita;

    @PersistenceContext
    private EntityManager entityManager;

    public ProcessoDocumentoService(ProcessoDocumentoRepository processoDocumentoRepository,
                                    ProcessoAlteravel processoAlteravel,
                                    PlantaService plantaService,
                                    MetadadosMotuService metadadosMotuService,
                                    UtilizadorRepository utilizadorRepository,
                                    ContextoUtilizadorResolver contextoUtilizadorResolver,
                                    AuditoriaService auditoriaService,
                                    ArmazenamentoDocumentos armazenamento,
                                    IntencaoArmazenamentoService intencaoArmazenamento,
                                    LinkPublicoService linkPublicoService,
                                    VerificadorZip verificadorZip,
                                    VerificadorTiff verificadorTiff,
                                    SubstituicaoDocumentosService substituicaoDocumentos,
                                    ValidadorNomeSubstituicao validadorNomeSubstituicao,
                                    PlatformTransactionManager transactionManager) {
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.processoAlteravel = processoAlteravel;
        this.plantaService = plantaService;
        this.metadadosMotuService = metadadosMotuService;
        this.utilizadorRepository = utilizadorRepository;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.auditoriaService = auditoriaService;
        this.armazenamento = armazenamento;
        this.intencaoArmazenamento = intencaoArmazenamento;
        this.linkPublicoService = linkPublicoService;
        this.verificadorZip = verificadorZip;
        this.verificadorTiff = verificadorTiff;
        this.substituicaoDocumentos = substituicaoDocumentos;
        this.validadorNomeSubstituicao = validadorNomeSubstituicao;
        this.escrita = new TransactionTemplate(transactionManager);
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
    }

    /**
     * Ordem do §14.3: autorização (Camadas 2, 3 e autoria; a PCM atribuída sem autoria), estado, categoria, extensão e só depois os bytes. A leitura
     * do corpo e a gravação no storage correm fora de transação, para não prender uma ligação do pool durante o
     * upload; o objeto é apagado antes de qualquer erro sair, e a linha só é criada com o objeto completo e verificado.
     */
    public ProcessoDocumento carregar(UUID processoId, PedidoUpload pedido) {
        PedidoCarga carga = leitura.execute(estado -> prepararCarga(processoId, pedido));
        UUID documentoId = UUID.randomUUID();
        String caminho = CaminhoDocumento.privado(carga.numeroProcesso(), carga.categoria(), OrigemDocumento.CARREGADO,
                documentoId, carga.nomeOriginal());
        intencaoArmazenamento.registar(caminho, OperacaoArmazenamento.GRAVACAO);
        ObjetoGravado gravado = gravar(caminho, processoId, documentoId, carga.nomeOriginal(), pedido,
                carga.limites(), carga.limite(), carga.regrasTiff());
        try {
            return escrita.execute(estado -> concluirCarga(processoId, documentoId, carga, caminho, gravado));
        } catch (RuntimeException e) {
            removerObjetoSilenciosamente(caminho);
            throw e;
        }
    }

    /**
     * Mesmas validações do upload, com a extensão contra o slot já associado. Mantém o id, a categoria e o
     * `documentoConfig`; o ficheiro novo vai para uma chave nova e o antigo só se apaga depois do commit.
     */
    public ProcessoDocumento substituir(UUID processoId, UUID documentoId, PedidoUpload pedido) {
        PedidoSubstituicao substituicao = leitura.execute(estado -> prepararSubstituicao(processoId, documentoId, pedido));
        if (substituicao.comoRecusado()) {
            return substituirRecusado(processoId, documentoId, substituicao, pedido);
        }
        String caminho = CaminhoDocumento.privadoSubstituto(substituicao.numeroProcesso(), substituicao.categoria(),
                substituicao.origem(), documentoId, novaRevisao(), substituicao.nomeOriginal());
        intencaoArmazenamento.registar(caminho, OperacaoArmazenamento.GRAVACAO);
        ObjetoGravado gravado = gravar(caminho, processoId, documentoId, substituicao.nomeOriginal(), pedido,
                substituicao.limites(), substituicao.limite(), substituicao.regrasTiff());
        try {
            return escrita.execute(estado -> concluirSubstituicao(processoId, documentoId, substituicao, caminho, gravado));
        } catch (ObjectOptimisticLockingFailureException e) {
            removerObjetoSilenciosamente(caminho);
            throw new DocumentoVersaoDesatualizadaException();
        } catch (RuntimeException e) {
            removerObjetoSilenciosamente(caminho);
            throw e;
        }
    }

    /**
     * Ficheiro recusado pela DGT (BL-DOC-026, BL-DOC-030), ou validado e desbloqueado na correção (OI-487): o ficheiro
     * novo entra como documento novo no mesmo item e o anterior vai para `SUBSTITUIDOS/` (nunca se perde). Se a
     * transação reverter, o objeto novo é apagado e a intenção registada antes fica, sem linha, para a limpeza noturna.
     */
    private ProcessoDocumento substituirRecusado(UUID processoId, UUID recusadoId, PedidoSubstituicao substituicao,
                                                 PedidoUpload pedido) {
        UUID novoId = UUID.randomUUID();
        String caminho = CaminhoDocumento.privado(substituicao.numeroProcesso(), substituicao.categoria(),
                OrigemDocumento.CARREGADO, novoId, substituicao.nomeOriginal());
        intencaoArmazenamento.registar(caminho, OperacaoArmazenamento.GRAVACAO);
        ObjetoGravado gravado = gravar(caminho, processoId, novoId, substituicao.nomeOriginal(), pedido,
                substituicao.limites(), substituicao.limite(), substituicao.regrasTiff());
        try {
            // O ZIP lê-se aqui, fora da transação: lido dentro, prendia a ligação e o bloqueio do Processo.
            List<String> tiffsNovos = VerificadorZip.aplicaSe(substituicao.nomeOriginal())
                    ? lerTiffs(caminho, gravado) : List.of();
            return escrita.execute(estado -> concluirSubstituicaoRecusada(processoId, recusadoId, novoId, substituicao,
                    caminho, gravado, tiffsNovos));
        } catch (ObjectOptimisticLockingFailureException e) {
            removerObjetoSilenciosamente(caminho);
            throw new DocumentoVersaoDesatualizadaException();
        } catch (RuntimeException e) {
            removerObjetoSilenciosamente(caminho);
            throw e;
        }
    }

    private ProcessoDocumento concluirSubstituicaoRecusada(UUID processoId, UUID recusadoId, UUID novoId,
                                                           PedidoSubstituicao substituicao, String caminho,
                                                           ObjetoGravado gravado, List<String> tiffsNovos) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obterComBloqueio(processoId, contexto);
        ProcessoDocumento recusado = obterDocumentoEditavel(recusadoId, processoId);
        if (!substituiComoRecusado(recusado)) {
            // Mudou durante o upload: o pedido já não é uma substituição de recusado.
            throw new DocumentoVersaoDesatualizadaException();
        }
        String acao = desbloqueado(recusado) ? ACAO_SUBSTITUIR_DOCUMENTO_DESBLOQUEADO : ACAO_SUBSTITUIR_DOCUMENTO_RECUSADO;
        DocumentoConfig documentoConfig = recusado.getDocumentoConfig();
        substituicao.limites().verificarAposGravar(ocupacao(processoId, documentoConfig, recusadoId),
                gravado.tamanhoBytes());
        validarFolhasDaSubstituicao(recusado, substituicao.nomeOriginal(), tiffsNovos);

        ProcessoDocumento novo = new ProcessoDocumento(novoId, processo, documentoConfig, recusado.getCategoria(),
                extensaoGuardada(substituicao.nomeOriginal()), substituicao.nomeOriginal(), caminho,
                gravado.hashSha256(), gravado.versaoObjeto(), gravado.tamanhoBytes(),
                utilizadorRepository.getReferenceById(contexto.utilizadorId()));
        removerObjetoSeReverter(caminho);
        entityManager.persist(novo);
        entityManager.flush();

        String hashAnterior = recusado.getHashSha256();
        substituicaoDocumentos.moverParaSubstituidos(recusado);
        plantaService.substituirRecusada(recusado, novo);
        metadadosMotuService.passarAoSubstituto(recusado, novo);
        sincronizar();

        auditar(processo, contexto, acao,
                Map.of("processoDocumentoId", recusadoId.toString(), "processoDocumentoNovoId", novoId.toString(),
                        "nomeFicheiro", substituicao.nomeOriginal(), "categoria", novo.getCategoria().name(),
                        "tamanhoBytes", gravado.tamanhoBytes(), "hashSha256", gravado.hashSha256(),
                        "hashSha256Anterior", hashAnterior));
        return novo;
    }

    /** O nome e, num ZIP de plantas, os TIFF do ZIP já gravado, lidos antes da transação. */
    private void validarFolhasDaSubstituicao(ProcessoDocumento recusado, String nomeNovo, List<String> tiffsNovos) {
        validadorNomeSubstituicao.validarNome(recusado, nomeNovo);
        if (VerificadorZip.aplicaSe(nomeNovo)) {
            validadorNomeSubstituicao.validarFolhas(recusado, tiffsNovos);
        }
    }

    /** Os TIFF do ZIP já gravado (só se lê com o objeto completo). */
    private List<String> lerTiffs(String caminho, ObjetoGravado gravado) {
        try (InputStream conteudo = armazenamento.abrir(caminho, gravado.versaoObjeto(), 0, 0)) {
            return LeitorPlantas.tiffsDoZip(conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PedidoCarga prepararCarga(UUID processoId, PedidoUpload pedido) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obterParaAcrescentar(processoId, contexto);
        DocumentoConfig documentoConfig = pedido.documentoConfigId() == null ? null
                : processoAlteravel.item(pedido.documentoConfigId(), processo);
        recusarSeObtidoDoGeoconform(processo, documentoConfig);
        recusarSeForaDoDestino(processo, documentoConfig);

        String nomeOriginal = validarNome(pedido, documentoConfig);
        CategoriaDocumento categoria = lerCategoria(pedido.categoria());
        validarCategoria(categoria, documentoConfig);
        recusarOutrosDePublicacaoSoDeposito(processo, documentoConfig, categoria);
        processoAlteravel.itensDasTags(pedido.tags(), documentoConfig, nomeOriginal, processo);
        if (documentoConfig != null) {
            RegrasExtensaoDocumento.validarFicheiro(nomeOriginal, documentoConfig);
        }
        LimitesDocumento limites = limitesDe(processo, documentoConfig);
        LimitesDocumento.Limite limite = limites.limiteDaLeitura(ocupacao(processoId, documentoConfig, null));
        return new PedidoCarga(processo.getNumeroProcesso(), categoria, pedido.documentoConfigId(), pedido.tags(),
                nomeOriginal, limites, limite,
                VerificadorTiff.regrasAplicaveis(categoria, documentoConfig).orElse(null));
    }

    private PedidoSubstituicao prepararSubstituicao(UUID processoId, UUID documentoId, PedidoUpload pedido) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obter(processoId, contexto);
        ProcessoDocumento documento = obterDocumentoEditavel(documentoId, processoId);
        DocumentoConfig documentoConfig = documento.getDocumentoConfig();
        recusarSeObtidoDoGeoconform(processo, documentoConfig);

        String nomeOriginal = validarNome(pedido, documentoConfig);
        if (documentoConfig != null) {
            RegrasExtensaoDocumento.validarFicheiro(nomeOriginal, documentoConfig);
        }
        // O ficheiro novo continua a satisfazer os itens das tags, que também o têm de aceitar.
        processoAlteravel.validarExtensaoNasTags(nomeOriginal, documento.getTags());
        boolean comoRecusado = substituiComoRecusado(documento);
        if (comoRecusado) {
            validadorNomeSubstituicao.validarNome(documento, nomeOriginal);
        }
        LimitesDocumento limites = limitesDe(processo, documentoConfig);
        LimitesDocumento.Limite limite = limites.limiteDaLeitura(ocupacao(processoId, documentoConfig, documentoId));
        return new PedidoSubstituicao(processo.getNumeroProcesso(), documento.getCategoria(), documento.getOrigem(),
                nomeOriginal, limites, limite,
                VerificadorTiff.regrasAplicaveis(documento.getCategoria(), documentoConfig).orElse(null), comoRecusado);
    }

    private void recusarSeForaDoDestino(Processo processo, DocumentoConfig documentoConfig) {
        if (documentoConfig == null || EstruturaDocumentalService.aplicaAoDestino(processo, documentoConfig)) {
            return;
        }
        log.warn("Carregamento recusado, item de publicação num processo só para depósito — processoId={} "
                + "documentoConfigId={}", processo.getId(), documentoConfig.getId());
        throw erroCampo(CAMPO_DOCUMENTO_CONFIG_ID, MSG_ITEM_FORA_DO_DESTINO);
    }

    /** Os Outros aceitam publicação (US-548), mas num processo só para depósito (BL-PROC-064) não há publicação. */
    private void recusarOutrosDePublicacaoSoDeposito(Processo processo, DocumentoConfig documentoConfig,
                                                     CategoriaDocumento categoria) {
        if (documentoConfig != null || categoria != CategoriaDocumento.PUBLICACAO
                || processo.getDestino() != DestinoProcesso.SO_DEPOSITO) {
            return;
        }
        log.warn("Carregamento recusado, Outros de publicação num processo só para depósito — processoId={}",
                processo.getId());
        throw erroCampo("categoria", MSG_OUTROS_SO_DEPOSITO);
    }

    private void recusarSeObtidoDoGeoconform(Processo processo, DocumentoConfig documentoConfig) {
        if (!CanalGeoconform.obtidoDoGeoconform(processo.getDinamica(), documentoConfig)) {
            return;
        }
        log.warn("Peça gráfica recusada, Dinâmica com GeoConform — processoId={} documentoConfigId={}",
                processo.getId(), documentoConfig.getId());
        throw new CanalGeoconformException(MSG_CANAL_GEOCONFORM);
    }

    /**
     * Transação curta: repete a autorização e o estado, que podem ter mudado durante o upload, e os limites com o
     * Processo bloqueado, para dois uploads simultâneos não passarem juntos o que só cabia a um.
     */
    private ProcessoDocumento concluirCarga(UUID processoId, UUID documentoId, PedidoCarga carga, String caminho,
                                            ObjetoGravado gravado) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obterParaAcrescentarComBloqueio(processoId, contexto);
        DocumentoConfig documentoConfig = carga.documentoConfigId() == null ? null
                : processoAlteravel.item(carga.documentoConfigId(), processo);
        // As tags revalidam-se com o Processo bloqueado: um item pode ter sido desativado durante o upload.
        List<DocumentoConfig> tags = processoAlteravel.itensDasTags(carga.tags(), documentoConfig, carga.nomeOriginal(),
                processo);
        carga.limites().verificarAposGravar(ocupacao(processoId, documentoConfig, null), gravado.tamanhoBytes());

        ProcessoDocumento documento = new ProcessoDocumento(documentoId, processo, documentoConfig, carga.categoria(),
                extensaoGuardada(carga.nomeOriginal()), carga.nomeOriginal(), caminho, gravado.hashSha256(),
                gravado.versaoObjeto(), gravado.tamanhoBytes(),
                utilizadorRepository.getReferenceById(contexto.utilizadorId()));
        documento.associarTags(tags);
        removerObjetoSeReverter(caminho);
        entityManager.persist(documento);
        entityManager.flush();
        plantaService.registarPecaGrafica(documento);
        metadadosMotuService.registarPecaGrafica(documento);

        auditar(processo, contexto, ACAO_CARREGAR_DOCUMENTO,
                Map.of("processoDocumentoId", documentoId.toString(), "nomeFicheiro", carga.nomeOriginal(),
                        "categoria", carga.categoria().name(), "tamanhoBytes", gravado.tamanhoBytes(),
                        "hashSha256", gravado.hashSha256(), "tags", documento.idsDasTags()));
        return documento;
    }

    private ProcessoDocumento concluirSubstituicao(UUID processoId, UUID documentoId, PedidoSubstituicao substituicao,
                                                   String caminho, ObjetoGravado gravado) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obterComBloqueio(processoId, contexto);
        ProcessoDocumento documento = obterDocumentoEditavel(documentoId, processoId);
        substituicao.limites().verificarAposGravar(ocupacao(processoId, documento.getDocumentoConfig(), documentoId),
                gravado.tamanhoBytes());

        String caminhoAnterior = documento.getCaminhoFicheiro();
        String hashAnterior = documento.getHashSha256();
        documento.setNomeFicheiro(substituicao.nomeOriginal());
        documento.setExtensao(extensaoGuardada(substituicao.nomeOriginal()));
        documento.setCaminhoFicheiro(caminho);
        documento.setHashSha256(gravado.hashSha256());
        documento.setTamanhoBytes(gravado.tamanhoBytes());
        documento.setVersaoObjeto(gravado.versaoObjeto());

        // A resposta é montada fora da transação (sem open-in-view): as tags, que se mantêm, leem-se já aqui.
        Hibernate.initialize(documento.getTags());
        removerObjetoSeReverter(caminho);
        linkPublicoService.revogar(documentoId, MotivoRevogacaoLink.SUBSTITUICAO);
        plantaService.atualizarAposSubstituicao(documento);
        metadadosMotuService.ligarFolhas(processoId);
        sincronizar();
        removerObjetoAposCommit(caminhoAnterior);

        auditar(processo, contexto, ACAO_SUBSTITUIR_DOCUMENTO,
                Map.of("processoDocumentoId", documentoId.toString(), "nomeFicheiro", substituicao.nomeOriginal(),
                        "categoria", documento.getCategoria().name(), "tamanhoBytes", gravado.tamanhoBytes(),
                        "hashSha256", gravado.hashSha256(), "hashSha256Anterior", hashAnterior));
        return documento;
    }

    /**
     * Uma só leitura do corpo, que segue direto para o storage. Qualquer falha — limite, corpo cortado, parte fora de
     * ordem, storage, conteúdo do ZIP — apaga o objeto antes de o erro sair. O ZIP é verificado aqui, fora da
     * transação, porque o diretório central está no fim do ficheiro e só se lê com o objeto completo; os GeoTIFF do
     * ZIP, quando há `regrasTiff`, logo a seguir, sobre o mesmo objeto.
     */
    private ObjetoGravado gravar(String caminho, UUID processoId, UUID documentoId, String nomeOriginal,
                                 PedidoUpload pedido, LimitesDocumento limites, LimitesDocumento.Limite limite,
                                 RegrasTiff regrasTiff) {
        long inicio = System.nanoTime();
        LeituraControlada conteudo = new LeituraControlada(pedido.conteudo(), limite.bytes());
        String contentType = pedido.contentType() == null ? CONTENT_TYPE_POR_OMISSAO : pedido.contentType();
        try (conteudo) {
            String versao = armazenamento.guardar(caminho, conteudo, contentType);
            if (conteudo.bytesLidos() == 0) {
                throw erroCampo("ficheiro", "Ficheiro vazio ou sem nome.");
            }
            pedido.verificarFim().run();
            if (VerificadorZip.aplicaSe(nomeOriginal)) {
                // O `versionId` cru da gravação, nunca o marcador da coluna: nulo lê a versão corrente.
                List<String> entradas = verificadorZip.verificar(documentoId, caminho, versao, conteudo.bytesLidos());
                if (regrasTiff != null) {
                    verificadorTiff.verificar(processoId, caminho, entradas, regrasTiff);
                }
            }
            ObjetoGravado gravado = new ObjetoGravado(conteudo.sha256(), conteudo.bytesLidos(),
                    versao == null ? VERSAO_SEM_VERSIONAMENTO : versao);
            log.info("Objeto de documento gravado — tamanhoBytes={} duracaoMs={}", gravado.tamanhoBytes(),
                    (System.nanoTime() - inicio) / 1_000_000);
            return gravado;
        } catch (IOException e) {
            removerObjetoSilenciosamente(caminho);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            removerObjetoSilenciosamente(caminho);
            if (conteudo.limiteExcedido()) {
                throw limites.excedido(limite);
            }
            if (conteudo.falhaLeitura() != null) {
                throw erroCampo("ficheiro", "Não foi possível ler o ficheiro enviado.");
            }
            throw e;
        }
    }

    private static LimitesDocumento limitesDe(Processo processo, DocumentoConfig documentoConfig) {
        return new LimitesDocumento(processo.getId(), documentoConfig,
                processo.getInstrumentoProcedimentoConfig().getTamanhoMaximoProcesso());
    }

    private LimitesDocumento.Ocupacao ocupacao(UUID processoId, DocumentoConfig documentoConfig, UUID excluirId) {
        long somaProcesso = processoDocumentoRepository.somaTamanhoDoProcesso(processoId, excluirId);
        if (documentoConfig == null) {
            return new LimitesDocumento.Ocupacao(0, 0, somaProcesso);
        }
        return new LimitesDocumento.Ocupacao(
                processoDocumentoRepository.somaTamanhoDoItem(processoId, documentoConfig.getId(), excluirId),
                processoDocumentoRepository.contarDoItem(processoId, documentoConfig.getId(), excluirId),
                somaProcesso);
    }

    private void removerObjetoSilenciosamente(String caminho) {
        try {
            armazenamento.removerTodasVersoes(caminho);
        } catch (RuntimeException e) {
            // Fica para a limpeza noturna, pela intenção registada antes.
            log.warn("Objeto órfão não removido após falha — caminho={}", caminho, e);
        }
    }

    /**
     * Linha apagada na transação, objeto logo depois do commit (§14.5). Um ficheiro recusado não se apaga (EF l.366,
     * OI-514, OI-526): vai para `SUBSTITUIDOS/` e o item fica sem ficheiro. As linhas `planta` e a ficha do Conjunto
     * saem como numa remoção normal (senão continuavam no consultaPlantas e duplicavam com o ZIP seguinte), antes da
     * cópia, para que uma falha aqui não deixe uma cópia retida órfã.
     */
    @Transactional
    public void remover(UUID processoId, UUID documentoId) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obter(processoId, contexto);
        ProcessoDocumento documento = obterDocumentoEditavel(documentoId, processoId);
        if (documento.getEstadoValidacao() == EstadoValidacao.REJEITADO) {
            plantaService.removerDoDocumento(documento);
            metadadosMotuService.removerDoDocumento(documentoId);
            metadadosMotuService.ligarFolhas(processoId);
            substituicaoDocumentos.moverParaSubstituidos(documento);
            auditar(processo, contexto, ACAO_REMOVER_DOCUMENTO,
                    Map.of("processoDocumentoId", documento.getId().toString(),
                            "nomeFicheiro", documento.getNomeFicheiro(), "hashSha256", documento.getHashSha256()));
            return;
        }
        if (desbloqueado(documento)) {
            throw new DocumentoEstadoInvalidoException(MSG_DESBLOQUEADO_NAO_SE_REMOVE);
        }

        linkPublicoService.apagarDoDocumento(documentoId);
        plantaService.removerDoDocumento(documento);
        metadadosMotuService.removerDoDocumento(documentoId);
        metadadosMotuService.ligarFolhas(processoId);
        processoDocumentoRepository.delete(documento);
        sincronizar();
        removerObjetoAposCommit(documento.getCaminhoFicheiro());

        auditar(processo, contexto, ACAO_REMOVER_DOCUMENTO,
                Map.of("processoDocumentoId", documento.getId().toString(),
                        "nomeFicheiro", documento.getNomeFicheiro(), "hashSha256", documento.getHashSha256()));
    }

    /**
     * Apaga definitivamente todos os documentos de um Processo — linhas e objetos — sem autorização
     * nem auditoria próprias. Só para a anulação automática por inatividade em EM_SUBMISSAO (§14.5): o chamador é o
     * job de anulação da EPIC[8], ainda por fazer (bloqueado pelo achado F6), que audita a anulação. Nunca chamar
     * depois de ENVIADO_DGT: a partir daí a retenção é permanente, incluindo na anulação manual pela DGT.
     */
    @Transactional
    public void removerTodosDoProcesso(UUID processoId) {
        List<ProcessoDocumento> documentos = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processoId);
        plantaService.removerDoProcesso(processoId);
        metadadosMotuService.removerConjuntosDoProcesso(processoId);
        processoDocumentoRepository.deleteAll(documentos);
        processoDocumentoRepository.flush();
        documentos.forEach(documento -> removerObjetoAposCommit(documento.getCaminhoFicheiro()));
    }

    /**
     * «Desbloquear ficheiro» na correção: o ficheiro validado deixa de estar bloqueado, com a justificação que a DGT
     * vê na validação. O estado de validação, o estado e a versão do Processo não mudam; a auditoria não leva o
     * texto, que pode ter dados pessoais.
     */
    @Transactional
    public ProcessoDocumento desbloquear(UUID processoId, UUID documentoId, String justificacao) {
        String texto = justificacao == null ? "" : justificacao.strip();
        if (texto.isEmpty()) {
            throw new JustificacaoEmFaltaException(MSG_JUSTIFICACAO_EM_FALTA,
                    List.of(new ValidacaoException.CampoErro(CAMPO_JUSTIFICACAO, MSG_JUSTIFICACAO_EM_FALTA)));
        }
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obterComBloqueio(processoId, contexto, MSG_DESBLOQUEIO_FORA_DA_CORRECAO);
        ProcessoDocumento documento = processoDocumentoRepository.findByIdAndProcesso_Id(documentoId, processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
        if (!documento.isBloqueadoEdicao()) {
            throw new DocumentoEstadoInvalidoException(MSG_NAO_BLOQUEADO);
        }

        documento.setBloqueadoEdicao(false);
        documento.setJustificacaoDesbloqueio(texto);
        // A resposta é montada fora da transação (sem open-in-view).
        Hibernate.initialize(documento.getTags());
        sincronizar();

        auditar(processo, contexto, ACAO_DESBLOQUEAR_DOCUMENTO, Map.of("processoDocumentoId", documentoId.toString()));
        log.info("Documento desbloqueado | processoId={} processoDocumentoId={}", processoId, documentoId);
        return documento;
    }

    /**
     * Muda o item, a caixa e as tags de um ficheiro já gravado. Na mesma caixa só a BD muda; entre caixas o objeto é
     * copiado para a chave da caixa nova e o original só se apaga depois do commit. Uma revalidação contra o item de
     * destino que falhe não toca no objeto: o ficheiro continua válido no item de origem. Tudo vem do item de destino,
     * nunca do tipo de Instrumento nem do Procedimento.
     */
    @Transactional
    public ProcessoDocumento reclassificar(UUID processoId, UUID documentoId, ReclassificarDocumentoRequest pedido) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        // Lido antes do bloqueio do Processo: se outra reclassificação fizer commit entretanto, esta cópia fica com a
        // versão antiga e o UPDATE dá o conflito, em vez de a segunda se sobrepor à primeira.
        Optional<ProcessoDocumento> encontrado = processoDocumentoRepository.findByIdAndProcesso_Id(documentoId, processoId);
        Processo processo = processoAlteravel.obterComBloqueio(processoId, contexto);
        ProcessoDocumento documento = obterDocumentoEditavel(encontrado);
        if (documento.getOrigem() == OrigemDocumento.GEOCONFORM) {
            throw new DocumentoBloqueadoException(MSG_GEOCONFORM_NAO_SE_RECLASSIFICA);
        }

        DocumentoConfig origem = documento.getDocumentoConfig();
        DocumentoConfig destino = pedido.documentoConfigId() == null ? null
                : processoAlteravel.item(pedido.documentoConfigId(), processo);
        CategoriaDocumento categoriaAnterior = documento.getCategoria();
        boolean mudaDeCaixa = pedido.categoria() != categoriaAnterior;
        if (mudaDeCaixa && armazenamento.temRetencao(documento.getCaminhoFicheiro(), documento.getVersaoObjeto())) {
            log.warn("Reclassificação recusada, objeto retido — processoId={} processoDocumentoId={}", processoId,
                    documentoId);
            throw new DocumentoRetidoException(MSG_RETIDO_NAO_MUDA_DE_CAIXA);
        }
        validarCategoria(pedido.categoria(), destino);
        boolean mudaDeItem = !Objects.equals(idDe(origem), idDe(destino));
        if (mudaDeItem) {
            revalidarNoDestino(processo, documento, pedido.categoria(), destino);
        }
        List<DocumentoConfig> tags = processoAlteravel.itensDasTags(
                pedido.tags().stream().map(tag -> tag == null ? null : tag.toString()).toList(),
                destino, documento.getNomeFicheiro(), processo);

        List<Long> tagsAnteriores = documento.idsDasTags();
        String caminhoAnterior = documento.getCaminhoFicheiro();
        if (mudaDeItem) {
            sairDasPecasGraficas(documento, destino);
        }
        boolean ganhaLinhaPlanta = mudaDeItem && plantaService.ganhaLinhaAoReclassificar(documento, destino);
        boolean entraNoMotu = mudaDeItem && !MetadadosMotuService.pecaGraficaDePublicacao(origem)
                && MetadadosMotuService.pecaGraficaDePublicacao(destino);
        if (mudaDeCaixa) {
            copiarParaACaixa(processo, documento, pedido.categoria());
        }
        documento.reclassificar(pedido.categoria(), destino);
        documento.substituirTags(tags);
        sincronizar();
        if (ganhaLinhaPlanta) {
            plantaService.registarPecaGrafica(documento);
        }
        if (entraNoMotu) {
            metadadosMotuService.registarPecaGrafica(documento);
        }
        if (mudaDeItem) {
            metadadosMotuService.ligarFolhas(processoId);
        }
        if (mudaDeCaixa) {
            removerObjetoAposCommit(caminhoAnterior);
        }

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("processoDocumentoId", documentoId.toString());
        dados.put("categoriaAnterior", categoriaAnterior.name());
        dados.put("categoria", pedido.categoria().name());
        dados.put("documentoConfigIdAnterior", idDe(origem));
        dados.put("documentoConfigId", idDe(destino));
        dados.put("tagsAnteriores", tagsAnteriores);
        dados.put("tags", documento.idsDasTags());
        auditar(processo, contexto, ACAO_RECLASSIFICAR_DOCUMENTO, dados);
        log.info("Documento reclassificado — processoId={} processoDocumentoId={} documentoConfigIdAnterior={} "
                        + "documentoConfigId={} mudouDeCaixa={}", processoId, documentoId, idDe(origem), idDe(destino),
                mudaDeCaixa);
        return documento;
    }

    /**
     * BL-DOC-015/016, limites, canal GeoConform e GeoTIFF contra o item de destino, pela ordem do carregamento. O
     * conteúdo do ZIP não depende do item e não se repete; uma recusa dos GeoTIFF não remove o objeto.
     */
    private void revalidarNoDestino(Processo processo, ProcessoDocumento documento, CategoriaDocumento categoria,
                                    DocumentoConfig destino) {
        String nome = documento.getNomeFicheiro();
        RegraNomenclatura regra = destino == null ? RegraNomenclatura.GERAL : destino.getRegraNomenclatura();
        RegrasNomeFicheiro.primeiroErro(nome, regra).ifPresent(erro -> {
            throw new NomeDocumentoInvalidoException("Documento inválido.",
                    List.of(new ValidacaoException.CampoErro("ficheiro", erro)));
        });
        if (destino == null) {
            return;
        }
        RegrasExtensaoDocumento.validarFicheiro(nome, destino);
        // O total do Processo não muda com a reclassificação.
        new LimitesDocumento(processo.getId(), destino, null)
                .verificarAposGravar(ocupacao(processo.getId(), destino, documento.getId()), documento.getTamanhoBytes());
        recusarSeObtidoDoGeoconform(processo, destino);
        Optional<RegrasTiff> regrasTiff = VerificadorTiff.regrasAplicaveis(categoria, destino);
        if (regrasTiff.isPresent() && VerificadorZip.aplicaSe(nome)) {
            String caminho = documento.getCaminhoFicheiro();
            List<String> entradas = verificadorZip.entradas(documento.getId(), caminho,
                    versaoParaLeitura(documento.getVersaoObjeto()), documento.getTamanhoBytes());
            verificadorTiff.verificar(processo.getId(), caminho, entradas, regrasTiff.get());
        }
    }

    /** Antes de o documento mudar: a ficha MOTU sai antes da planta, como na remoção. */
    private void sairDasPecasGraficas(ProcessoDocumento documento, DocumentoConfig destino) {
        if (MetadadosMotuService.pecaGraficaDePublicacao(documento.getDocumentoConfig())
                && !MetadadosMotuService.pecaGraficaDePublicacao(destino)) {
            metadadosMotuService.removerDoDocumento(documento.getId());
        }
        if (plantaService.perdeLinhasAoReclassificar(documento, destino)) {
            plantaService.removerDoDocumento(documento);
        }
    }

    /**
     * Cópia server-side para a chave da caixa nova, com uma revisão nova para nunca coincidir com uma chave que a
     * limpeza noturna ainda vá apagar. A intenção `COPIA` sobrevive a uma reversão; a cópia sem linha apaga-se logo
     * ou na limpeza.
     */
    private void copiarParaACaixa(Processo processo, ProcessoDocumento documento, CategoriaDocumento caixa) {
        String destino = CaminhoDocumento.privadoSubstituto(processo.getNumeroProcesso(), caixa, documento.getOrigem(),
                documento.getId(), novaRevisao(), documento.getNomeFicheiro());
        intencaoArmazenamento.registar(destino, OperacaoArmazenamento.COPIA);
        String versao = armazenamento.copiar(documento.getCaminhoFicheiro(),
                versaoParaLeitura(documento.getVersaoObjeto()), destino);
        removerObjetoSeReverter(destino);
        documento.setCaminhoFicheiro(destino);
        documento.setVersaoObjeto(versao == null ? VERSAO_SEM_VERSIONAMENTO : versao);
    }

    static String versaoParaLeitura(String versaoObjeto) {
        return VERSAO_SEM_VERSIONAMENTO.equals(versaoObjeto) ? null : versaoObjeto;
    }

    private static Long idDe(DocumentoConfig item) {
        return item == null ? null : item.getId();
    }

    /**
     * Um desbloqueado trata-se como um recusado: substituído no mesmo id, o ficheiro validado apagava-se e o novo
     * herdava o VALIDADO, sem passar pela DGT.
     */
    private static boolean substituiComoRecusado(ProcessoDocumento documento) {
        return documento.getEstadoValidacao() == EstadoValidacao.REJEITADO || desbloqueado(documento);
    }

    private static boolean desbloqueado(ProcessoDocumento documento) {
        return !documento.isBloqueadoEdicao() && documento.getJustificacaoDesbloqueio() != null
                && documento.getEstadoValidacao() == EstadoValidacao.VALIDADO;
    }

    private ProcessoDocumento obterDocumentoEditavel(UUID documentoId, UUID processoId) {
        return obterDocumentoEditavel(processoDocumentoRepository.findByIdAndProcesso_Id(documentoId, processoId));
    }

    private static ProcessoDocumento obterDocumentoEditavel(Optional<ProcessoDocumento> encontrado) {
        ProcessoDocumento documento = encontrado
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
        if (documento.getEstadoValidacao() == EstadoValidacao.SUBSTITUIDO) {
            throw new DocumentoBloqueadoException(MSG_SUBSTITUIDO);
        }
        if (documento.isBloqueadoEdicao()) {
            throw new DocumentoBloqueadoException(MSG_BLOQUEADO);
        }
        return documento;
    }

    private static CategoriaDocumento lerCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            throw new ClassificacaoInvalidaException(CAMPO_CATEGORIA, MSG_CATEGORIA_EM_FALTA);
        }
        try {
            return CategoriaDocumento.valueOf(categoria.trim());
        } catch (IllegalArgumentException e) {
            throw new ClassificacaoInvalidaException(CAMPO_CATEGORIA, MSG_CATEGORIA_EM_FALTA);
        }
    }

    /** Sem item só em depósito; com item, a categoria é a do item. */
    private static void validarCategoria(CategoriaDocumento categoria, DocumentoConfig documentoConfig) {
        if (documentoConfig != null && documentoConfig.getCategoria() != categoria) {
            throw new ClassificacaoInvalidaException(CAMPO_CATEGORIA, MSG_CATEGORIA_DIFERENTE);
        }
    }

    private void auditar(Processo processo, ContextoUtilizador contexto, String acao, Map<String, ?> dados) {
        auditoriaService.registar(TIPO_OCORRENCIA, processo.getId().toString(), acao, contexto.utilizadorId(),
                processo.getEntidade().getId(), null, dados);
    }

    /** A coluna guarda no máximo {@value #TAMANHO_MAXIMO_EXTENSAO} caracteres, como o nome sanitizado do objeto. */
    private static String extensaoGuardada(String nomeOriginal) {
        String extensao = CaminhoDocumento.extensao(nomeOriginal);
        return extensao.length() > TAMANHO_MAXIMO_EXTENSAO ? extensao.substring(0, TAMANHO_MAXIMO_EXTENSAO) : extensao;
    }

    private static String novaRevisao() {
        return UUID.randomUUID().toString().substring(0, TAMANHO_REVISAO);
    }

    /** Regra do item de destino; o balde «outros», sem item, usa a `GERAL`. */
    private String validarNome(PedidoUpload pedido, DocumentoConfig documentoConfig) {
        String nome = pedido.nomeFicheiro();
        if (pedido.conteudo() == null || nome == null || nome.isBlank()) {
            throw erroCampo("ficheiro", "Ficheiro vazio ou sem nome.");
        }
        if (nome.length() > TAMANHO_MAXIMO_NOME_FICHEIRO) {
            throw erroCampo("ficheiro", "Nome do ficheiro com mais de " + TAMANHO_MAXIMO_NOME_FICHEIRO + " caracteres.");
        }
        RegraNomenclatura regra = documentoConfig == null ? RegraNomenclatura.GERAL : documentoConfig.getRegraNomenclatura();
        RegrasNomeFicheiro.primeiroErro(nome, regra).ifPresent(erro -> {
            throw new NomeDocumentoInvalidoException("Documento inválido.",
                    List.of(new ValidacaoException.CampoErro("ficheiro", erro)));
        });
        return nome;
    }

    private void removerObjetoAposCommit(String caminho) {
        intencaoArmazenamento.registar(caminho, OperacaoArmazenamento.APAGAMENTO);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    armazenamento.removerTodasVersoes(caminho);
                } catch (RuntimeException e) {
                    // Fica para a limpeza noturna, pela intenção registada antes.
                    log.warn("Objeto órfão não removido após commit — caminho={}", caminho, e);
                }
            }
        });
    }

    private void removerObjetoSeReverter(String caminho) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    armazenamento.removerTodasVersoes(caminho);
                } catch (RuntimeException e) {
                    // Fica para a limpeza noturna, pela intenção registada antes.
                    log.warn("Objeto órfão não removido após rollback — caminho={}", caminho, e);
                }
            }
        });
    }

    /** Força o UPDATE/DELETE aqui, para o conflito de `versao` sair já como 409 e não só no commit. */
    private void sincronizar() {
        try {
            processoDocumentoRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new DocumentoVersaoDesatualizadaException();
        }
    }

    private record PedidoCarga(String numeroProcesso, CategoriaDocumento categoria, Long documentoConfigId,
                               List<String> tags, String nomeOriginal, LimitesDocumento limites, LimitesDocumento.Limite limite,
                               RegrasTiff regrasTiff) {
    }

    private record PedidoSubstituicao(String numeroProcesso, CategoriaDocumento categoria, OrigemDocumento origem,
                                      String nomeOriginal, LimitesDocumento limites, LimitesDocumento.Limite limite,
                                      RegrasTiff regrasTiff, boolean comoRecusado) {
    }

    private record ObjetoGravado(String hashSha256, long tamanhoBytes, String versaoObjeto) {
    }

    private static ValidacaoException erroCampo(String campo, String erro) {
        return new ValidacaoException("Documento inválido.", List.of(new ValidacaoException.CampoErro(campo, erro)));
    }
}
