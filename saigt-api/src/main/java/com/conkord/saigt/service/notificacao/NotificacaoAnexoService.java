package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.configuration.AnexosNotificacaoProperties;
import com.conkord.saigt.exception.AnexoNotificacaoExtensaoNaoAceiteException;
import com.conkord.saigt.exception.AnexoNotificacaoTamanhoExcedidoException;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoSemConversacaoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CaminhoDocumento;
import com.conkord.saigt.service.documento.DescargaDocumentoService;
import com.conkord.saigt.service.documento.IntencaoArmazenamentoService;
import com.conkord.saigt.service.documento.LeituraControlada;
import com.conkord.saigt.service.documento.PedidoUpload;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificacaoAnexoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoAnexoService.class);
    private static final String CONTENT_TYPE_POR_OMISSAO = "application/octet-stream";
    private static final String PARTE_FICHEIRO = "ficheiro";

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoDocumentoRepository documentoRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final NotificacaoVisibilidade visibilidade;
    private final NotificacaoMensagemService mensagemService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ArmazenamentoDocumentos armazenamento;
    private final IntencaoArmazenamentoService intencaoArmazenamento;
    private final DescargaDocumentoService descargaService;
    private final AnexosNotificacaoProperties propriedades;
    private final Clock relogio;
    private final TransactionTemplate leitura;
    private final TransactionTemplate escrita;

    @PersistenceContext
    private EntityManager entityManager;

    public NotificacaoAnexoService(NotificacaoRepository notificacaoRepository,
                                   NotificacaoDocumentoRepository documentoRepository,
                                   UtilizadorRepository utilizadorRepository, NotificacaoVisibilidade visibilidade,
                                   NotificacaoMensagemService mensagemService,
                                   ContextoUtilizadorResolver contextoUtilizadorResolver,
                                   ArmazenamentoDocumentos armazenamento,
                                   IntencaoArmazenamentoService intencaoArmazenamento,
                                   DescargaDocumentoService descargaService, AnexosNotificacaoProperties propriedades,
                                   Clock relogio, PlatformTransactionManager transactionManager) {
        this.notificacaoRepository = notificacaoRepository;
        this.documentoRepository = documentoRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.visibilidade = visibilidade;
        this.mensagemService = mensagemService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.armazenamento = armazenamento;
        this.intencaoArmazenamento = intencaoArmazenamento;
        this.descargaService = descargaService;
        this.propriedades = propriedades;
        this.relogio = relogio;
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
        this.escrita = new TransactionTemplate(transactionManager);
    }

    public NotificacaoDocumento carregar(UUID notificacaoId, PedidoUpload pedido) {
        String nomeOriginal = leitura.execute(estado -> prepararCarga(notificacaoId, pedido));
        UUID documentoId = UUID.randomUUID();
        String caminho = CaminhoDocumento.notificacao(notificacaoId, documentoId, nomeOriginal);
        intencaoArmazenamento.registar(caminho, OperacaoArmazenamento.GRAVACAO);
        long inicio = System.nanoTime();
        long tamanhoBytes = gravar(caminho, pedido);
        try {
            NotificacaoDocumento documento = escrita.execute(estado ->
                    concluirCarga(notificacaoId, documentoId, nomeOriginal, caminho, tamanhoBytes));
            log.info("Anexo de notificação carregado — notificacaoId={} notificacaoDocumentoId={} tamanhoBytes={} "
                    + "duracaoMs={}", notificacaoId, documentoId, tamanhoBytes, (System.nanoTime() - inicio) / 1_000_000);
            return documento;
        } catch (RuntimeException e) {
            removerObjetoSilenciosamente(caminho);
            throw e;
        }
    }

    public String emitirDescarga(UUID notificacaoId, UUID documentoId) {
        leitura.executeWithoutResult(estado -> {
            obterVisivel(notificacaoId, contextoUtilizadorResolver.resolver());
            documentoRepository.findByIdAndNotificacao_Id(documentoId, notificacaoId)
                    .orElseThrow(NotificacaoNaoEncontradaException::new);
        });
        log.debug("Ticket de descarga de anexo emitido — notificacaoId={} notificacaoDocumentoId={}", notificacaoId,
                documentoId);
        return descargaService.emitirAnexoNotificacao(documentoId);
    }

    public boolean podeAnexar(Notificacao notificacao, ContextoUtilizador contexto) {
        try {
            verificarPodeAnexar(notificacao, contexto);
            return true;
        } catch (NotificacaoSemConversacaoException | NotificacaoPerfilNaoAutorizadoException
                 | NotificacaoEstadoInvalidoException e) {
            return false;
        }
    }

    private String prepararCarga(UUID notificacaoId, PedidoUpload pedido) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        verificarPodeAnexar(obterVisivel(notificacaoId, contexto), contexto);
        if (pedido.conteudo() == null || pedido.nomeFicheiro() == null || pedido.nomeFicheiro().isBlank()) {
            throw erroFicheiro("Ficheiro vazio ou sem nome.");
        }
        String nomeOriginal = pedido.nomeFicheiro().strip();
        if (!propriedades.extensoesAceites().contains(CaminhoDocumento.extensao(nomeOriginal))) {
            throw new AnexoNotificacaoExtensaoNaoAceiteException();
        }
        return nomeOriginal;
    }

    private NotificacaoDocumento concluirCarga(UUID notificacaoId, UUID documentoId, String nomeOriginal,
                                               String caminho, long tamanhoBytes) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Notificacao notificacao = notificacaoRepository.findComBloqueioById(notificacaoId)
                .orElseThrow(NotificacaoNaoEncontradaException::new);
        if (!notificacaoRepository.exists(visivelComId(notificacaoId, contexto))) {
            throw new NotificacaoNaoEncontradaException();
        }
        verificarPodeAnexar(notificacao, contexto);

        NotificacaoDocumento documento = new NotificacaoDocumento(documentoId, notificacao, nomeOriginal, caminho,
                tamanhoBytes, utilizadorRepository.getReferenceById(contexto.utilizadorId()));
        removerObjetoSeReverter(caminho);
        entityManager.persist(documento);
        entityManager.flush();
        return documento;
    }

    private long gravar(String caminho, PedidoUpload pedido) {
        LeituraControlada conteudo = new LeituraControlada(pedido.conteudo(), propriedades.tamanhoMaximo().toBytes());
        String contentType = pedido.contentType() == null ? CONTENT_TYPE_POR_OMISSAO : pedido.contentType();
        try (conteudo) {
            armazenamento.guardar(caminho, conteudo, contentType);
            if (conteudo.bytesLidos() == 0) {
                throw erroFicheiro("Ficheiro vazio ou sem nome.");
            }
            pedido.verificarFim().run();
            return conteudo.bytesLidos();
        } catch (IOException e) {
            removerObjetoSilenciosamente(caminho);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            removerObjetoSilenciosamente(caminho);
            if (conteudo.limiteExcedido()) {
                throw new AnexoNotificacaoTamanhoExcedidoException();
            }
            if (conteudo.falhaLeitura() != null) {
                throw erroFicheiro("Não foi possível ler o ficheiro enviado.");
            }
            throw e;
        }
    }

    private void verificarPodeAnexar(Notificacao notificacao, ContextoUtilizador contexto) {
        switch (notificacao.getTipo()) {
            case TEMPORARIA, ALERTA -> throw new NotificacaoSemConversacaoException();
            case ESTATICA -> {
                if (!masterAutor(notificacao, contexto)) {
                    throw new NotificacaoPerfilNaoAutorizadoException("Não pode anexar documentos a esta notificação.");
                }
                if (notificacao.getEstado() != EstadoNotificacao.ATIVA) {
                    throw new NotificacaoEstadoInvalidoException();
                }
            }
            default -> {
                if (!mensagemService.participa(notificacao, contexto)) {
                    throw new NotificacaoPerfilNaoAutorizadoException("Não pode anexar documentos a esta notificação.");
                }
                if (!NotificacaoMensagemService.aceitaMensagens(notificacao)) {
                    throw new NotificacaoEstadoInvalidoException();
                }
            }
        }
    }

    private Notificacao obterVisivel(UUID notificacaoId, ContextoUtilizador contexto) {
        if (contexto.perfilInterno() != null && contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new NotificacaoNaoEncontradaException();
        }
        return notificacaoRepository.findOne(visivelComId(notificacaoId, contexto))
                .orElseThrow(NotificacaoNaoEncontradaException::new);
    }

    private Specification<Notificacao> visivelComId(UUID id, ContextoUtilizador contexto) {
        return Specification.<Notificacao>where((root, query, cb) -> cb.equal(root.get("id"), id))
                .and(visibilidade.visiveisPara(contexto))
                .and(visibilidade.emVigencia(Instant.now(relogio)));
    }

    private static boolean masterAutor(Notificacao notificacao, ContextoUtilizador contexto) {
        return contexto.perfilInterno() == PerfilInterno.MASTER && notificacao.getCriadoPor() != null
                && Objects.equals(notificacao.getCriadoPor().getId(), contexto.utilizadorId());
    }

    private void removerObjetoSilenciosamente(String caminho) {
        try {
            armazenamento.removerTodasVersoes(caminho);
        } catch (RuntimeException e) {
            log.warn("Anexo de notificação órfão não removido após falha", e);
        }
    }

    private void removerObjetoSeReverter(String caminho) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    removerObjetoSilenciosamente(caminho);
                }
            }
        });
    }

    private static ValidacaoException erroFicheiro(String erro) {
        return new ValidacaoException("Documento inválido.", List.of(new ValidacaoException.CampoErro(PARTE_FICHEIRO, erro)));
    }
}
