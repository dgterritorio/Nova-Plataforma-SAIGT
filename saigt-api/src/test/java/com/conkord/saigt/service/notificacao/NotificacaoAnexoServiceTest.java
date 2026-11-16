package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.configuration.AnexosNotificacaoProperties;
import com.conkord.saigt.exception.AnexoNotificacaoExtensaoNaoAceiteException;
import com.conkord.saigt.exception.AnexoNotificacaoTamanhoExcedidoException;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoSemConversacaoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.DescargaDocumentoService;
import com.conkord.saigt.service.documento.IntencaoArmazenamentoService;
import com.conkord.saigt.service.documento.PedidoUpload;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class NotificacaoAnexoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-13T10:00:00Z");
    private static final long LIMITE = 1024;
    private static final String NOME = "Relatório final.pdf";

    private NotificacaoRepository notificacaoRepository;
    private NotificacaoDocumentoRepository documentoRepository;
    private UtilizadorRepository utilizadorRepository;
    private ContextoUtilizadorResolver resolver;
    private ArmazenamentoDocumentos armazenamento;
    private IntencaoArmazenamentoService intencao;
    private DescargaDocumentoService descarga;
    private EntityManager entityManager;
    private NotificacaoAnexoService service;

    private Entidade entidadeA;
    private Entidade entidadeB;
    private Utilizador utilizadorA;
    private Utilizador master;
    private Utilizador outroMaster;

    @BeforeEach
    void criar() {
        notificacaoRepository = mock(NotificacaoRepository.class);
        documentoRepository = mock(NotificacaoDocumentoRepository.class);
        utilizadorRepository = mock(UtilizadorRepository.class);
        resolver = mock(ContextoUtilizadorResolver.class);
        armazenamento = mock(ArmazenamentoDocumentos.class);
        intencao = mock(IntencaoArmazenamentoService.class);
        descarga = mock(DescargaDocumentoService.class);
        entityManager = mock(EntityManager.class);
        Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
        NotificacaoMensagemService mensagens = new NotificacaoMensagemService(notificacaoRepository,
                mock(NotificacaoMensagemRepository.class), utilizadorRepository, new NotificacaoVisibilidade(),
                resolver, relogio);
        service = new NotificacaoAnexoService(notificacaoRepository, documentoRepository, utilizadorRepository,
                new NotificacaoVisibilidade(), mensagens, resolver, armazenamento, intencao, descarga,
                new AnexosNotificacaoProperties(DataSize.ofBytes(LIMITE), List.of("pdf", " DOCX ")), relogio,
                new GestorSemRecursos());
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        entidadeA = comId(EntidadeTeste.nova("511000001", "Câmara A"), 1L);
        entidadeB = comId(EntidadeTeste.nova("511000002", "Câmara B"), 2L);
        utilizadorA = utilizador("Ana Teixeira", null);
        master = utilizador("Marta Master", PerfilInterno.MASTER);
        outroMaster = utilizador("Mário Master", PerfilInterno.MASTER);
        for (Utilizador u : new Utilizador[]{utilizadorA, master, outroMaster}) {
            when(utilizadorRepository.getReferenceById(u.getId())).thenReturn(u);
        }
        when(notificacaoRepository.exists(any(Specification.class))).thenReturn(true);
        doAnswer(invocacao -> {
            try (InputStream conteudo = invocacao.getArgument(1)) {
                conteudo.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return "v1";
        }).when(armazenamento).guardar(anyString(), any(InputStream.class), anyString());
    }

    @Test
    void entidadeDestinatariaCarregaNaRespostaObrigatoriaComIntencaoAntesDaGravacao(CapturedOutput saida) {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);

        NotificacaoDocumento documento = carregarComo(externo(utilizadorA, entidadeA), resposta, NOME, 700);

        String prefixo = "notificacoes/" + resposta.getId() + "/" + documento.getId() + "-";
        assertThat(documento.getCaminhoFicheiro()).isEqualTo(prefixo + "Relatorio_final.pdf");
        assertThat(documento.getNomeFicheiro()).isEqualTo(NOME);
        assertThat(documento.getTamanhoBytes()).isEqualTo(700L);
        assertThat(documento.getCriadoPor()).isSameAs(utilizadorA);
        assertThat(documento.getNotificacao()).isSameAs(resposta);
        InOrder ordem = inOrder(intencao, armazenamento, entityManager);
        ordem.verify(intencao).registar(documento.getCaminhoFicheiro(), OperacaoArmazenamento.GRAVACAO);
        ordem.verify(armazenamento).guardar(eq(documento.getCaminhoFicheiro()), any(), eq("application/pdf"));
        ordem.verify(entityManager).persist(documento);
        verify(notificacaoRepository).findComBloqueioById(resposta.getId());
        verify(armazenamento, never()).removerTodasVersoes(anyString());
        assertThat(saida.getOut()).contains("notificacaoId=" + resposta.getId())
                .contains("notificacaoDocumentoId=" + documento.getId()).contains("tamanhoBytes=700")
                .contains("duracaoMs=").doesNotContain("Relat");
    }

    @Test
    void masterCarregaNoPedidoDeApoioParaOMaster() {
        Notificacao pedido = notificacao(TipoNotificacao.PEDIDO_APOIO, utilizadorA, EstadoNotificacao.ATIVA,
                n -> n.setPerfilDestino(PerfilInterno.MASTER));

        assertThat(carregarComo(interno(outroMaster), pedido, "pedido.pdf", 10)).isNotNull();
        assertThat(service.podeAnexar(pedido, interno(outroMaster))).isTrue();
    }

    @Test
    void oAutorDaRegularizacaoCarregaOsComprovativosDepoisDeACriarEOMasterTambem() {
        Notificacao regularizacao = notificacao(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, utilizadorA,
                EstadoNotificacao.ATIVA, n -> n.setPerfilDestino(PerfilInterno.MASTER));

        assertThat(carregarComo(externo(utilizadorA, entidadeA), regularizacao, "oficio.pdf", 10).getCriadoPor())
                .isSameAs(utilizadorA);
        assertThat(carregarComo(externo(utilizadorA, entidadeA), regularizacao, "aviso-dr.pdf", 10)).isNotNull();
        assertThat(service.podeAnexar(regularizacao, externo(utilizadorA, entidadeA))).isTrue();
        assertThat(service.podeAnexar(regularizacao, interno(outroMaster))).isTrue();

        regularizacao.setEstado(EstadoNotificacao.RESOLVIDA);
        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), regularizacao, "oficio.pdf", 10))
                .isInstanceOf(NotificacaoEstadoInvalidoException.class);
    }

    @Test
    void naNoticiaSoOMasterQueACriouCarregaEnquantoAtiva() {
        Notificacao noticia = notificacao(TipoNotificacao.ESTATICA, master, EstadoNotificacao.ATIVA, n -> { });

        assertThat(carregarComo(interno(master), noticia, "aviso.pdf", 10)).isNotNull();
        assertThat(service.podeAnexar(noticia, interno(master))).isTrue();

        assertThatThrownBy(() -> carregarComo(interno(outroMaster), noticia, "aviso.pdf", 10))
                .isInstanceOf(NotificacaoPerfilNaoAutorizadoException.class);
        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), noticia, "aviso.pdf", 10))
                .isInstanceOf(NotificacaoPerfilNaoAutorizadoException.class);
        assertThat(service.podeAnexar(noticia, externo(utilizadorA, entidadeA))).isFalse();

        noticia.setEstado(EstadoNotificacao.REMOVIDA);
        assertThatThrownBy(() -> carregarComo(interno(master), noticia, "aviso.pdf", 10))
                .isInstanceOf(NotificacaoEstadoInvalidoException.class);
        assertThat(service.podeAnexar(noticia, interno(master))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"TEMPORARIA", "ALERTA"})
    void tiposSemConversacaoDao409SemTocarNoStorage(TipoNotificacao tipo) {
        Notificacao semConversa = notificacao(tipo, null, EstadoNotificacao.ATIVA, n -> n.setUtilizador(utilizadorA));

        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), semConversa, NOME, 10))
                .isInstanceOf(NotificacaoSemConversacaoException.class);
        verifyNoInteractions(intencao, armazenamento);
        assertThat(service.podeAnexar(semConversa, externo(utilizadorA, entidadeA))).isFalse();
    }

    @Test
    void pedidoDeApoioResolvidoDa409EARespostaObrigatoriaResolvidaAceita() {
        Notificacao pedido = notificacao(TipoNotificacao.PEDIDO_APOIO, utilizadorA, EstadoNotificacao.RESOLVIDA,
                n -> n.setPerfilDestino(PerfilInterno.MASTER));
        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), pedido, NOME, 10))
                .isInstanceOf(NotificacaoEstadoInvalidoException.class);

        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.RESOLVIDA);
        assertThat(carregarComo(externo(utilizadorA, entidadeA), resposta, NOME, 10)).isNotNull();
        assertThat(service.podeAnexar(resposta, externo(utilizadorA, entidadeA))).isTrue();
    }

    @Test
    void quemNaoParticipaRecebe403() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);

        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeB), resposta, NOME, 10))
                .isInstanceOf(NotificacaoPerfilNaoAutorizadoException.class);
        verifyNoInteractions(intencao, armazenamento);
    }

    @Test
    void extensaoForaDaListaDa400SemIntencaoNemObjeto() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);

        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), resposta, "programa.exe", 10))
                .isInstanceOf(AnexoNotificacaoExtensaoNaoAceiteException.class)
                .satisfies(e -> assertThat(((ValidacaoException) e).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("ficheiro"));
        verifyNoInteractions(intencao, armazenamento, entityManager);
        assertThat(carregarComo(externo(utilizadorA, entidadeA), resposta, "Parecer.DOCX", 10)).isNotNull();
    }

    @Test
    void ficheiroAcimaDoLimiteRemoveOObjetoESemLinha() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);

        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), resposta, NOME, (int) LIMITE + 1))
                .isInstanceOf(AnexoNotificacaoTamanhoExcedidoException.class);
        ArgumentCaptor<String> caminho = ArgumentCaptor.forClass(String.class);
        verify(intencao).registar(caminho.capture(), eq(OperacaoArmazenamento.GRAVACAO));
        verify(armazenamento).removerTodasVersoes(caminho.getValue());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void ficheiroNoLimiteEAceite() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);

        assertThat(carregarComo(externo(utilizadorA, entidadeA), resposta, NOME, (int) LIMITE).getTamanhoBytes())
                .isEqualTo(LIMITE);
    }

    @Test
    void ficheiroVazioOuAusenteDa400Validacao() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);

        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), resposta, NOME, 0))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(e -> assertThat(((ValidacaoException) e).getCodigo()).isEqualTo("VALIDACAO"));
        verify(armazenamento).removerTodasVersoes(anyString());
        verify(entityManager, never()).persist(any());

        when(resolver.resolver()).thenReturn(externo(utilizadorA, entidadeA));
        PedidoUpload semFicheiro = new PedidoUpload(null, null, List.of(), null, null, null, () -> { });
        assertThatThrownBy(() -> service.carregar(resposta.getId(), semFicheiro))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(e -> assertThat(((ValidacaoException) e).getCodigo()).isEqualTo("VALIDACAO"));
    }

    @Test
    void storageIndisponivelPropagaSemLinha() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);
        doThrow(new StorageIndisponivelException("em baixo", null))
                .when(armazenamento).guardar(anyString(), any(InputStream.class), anyString());

        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), resposta, NOME, 10))
                .isInstanceOf(StorageIndisponivelException.class);
        verify(armazenamento).removerTodasVersoes(anyString());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void falhaAGravarALinhaRemoveOObjeto() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);
        doThrow(new IllegalStateException("falha da BD")).when(entityManager).flush();

        assertThatThrownBy(() -> carregarComo(externo(utilizadorA, entidadeA), resposta, NOME, 10))
                .isInstanceOf(IllegalStateException.class);
        ArgumentCaptor<String> caminho = ArgumentCaptor.forClass(String.class);
        verify(intencao).registar(caminho.capture(), eq(OperacaoArmazenamento.GRAVACAO));
        verify(armazenamento, atLeastOnce()).removerTodasVersoes(caminho.getValue());
    }

    @Test
    void estadoMudadoDuranteOUploadERecusadoComANotificacaoBloqueada() {
        Notificacao lida = respostaObrigatoria(EstadoNotificacao.ATIVA);
        Notificacao removida = respostaObrigatoria(EstadoNotificacao.REMOVIDA);
        ReflectionTestUtils.setField(removida, "id", lida.getId());
        when(resolver.resolver()).thenReturn(externo(utilizadorA, entidadeA));
        when(notificacaoRepository.findOne(any(Specification.class))).thenReturn(Optional.of(lida));
        when(notificacaoRepository.findComBloqueioById(lida.getId())).thenReturn(Optional.of(removida));

        assertThatThrownBy(() -> service.carregar(lida.getId(), pedido(NOME, 10)))
                .isInstanceOf(NotificacaoEstadoInvalidoException.class);
        verify(armazenamento).removerTodasVersoes(anyString());
    }

    @Test
    void naoVisivelOuInternoSemAcessoDao404() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);
        when(resolver.resolver()).thenReturn(externo(utilizadorA, entidadeB));
        when(notificacaoRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.carregar(resposta.getId(), pedido(NOME, 10)))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);

        when(resolver.resolver()).thenReturn(interno(utilizador("Tiago Técnico", PerfilInterno.TECNICO),
                PerfilInterno.TECNICO));
        assertThatThrownBy(() -> service.carregar(resposta.getId(), pedido(NOME, 10)))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);
        verifyNoInteractions(intencao, armazenamento);
    }

    @Test
    void descargaDeAnexoVisivelEmiteTicketEAnexoDeOutraNotificacaoDa404() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA);
        UUID anexoId = UUID.randomUUID();
        UUID alheioId = UUID.randomUUID();
        when(resolver.resolver()).thenReturn(externo(utilizadorA, entidadeA));
        when(notificacaoRepository.findOne(any(Specification.class))).thenReturn(Optional.of(resposta));
        when(documentoRepository.findByIdAndNotificacao_Id(anexoId, resposta.getId()))
                .thenReturn(Optional.of(mock(NotificacaoDocumento.class)));
        when(documentoRepository.findByIdAndNotificacao_Id(alheioId, resposta.getId())).thenReturn(Optional.empty());
        when(descarga.emitirAnexoNotificacao(anexoId)).thenReturn("http://localhost:8080/api/v1/descargas/t");

        assertThat(service.emitirDescarga(resposta.getId(), anexoId))
                .isEqualTo("http://localhost:8080/api/v1/descargas/t");
        assertThatThrownBy(() -> service.emitirDescarga(resposta.getId(), alheioId))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);
        verify(descarga, never()).emitirAnexoNotificacao(alheioId);
    }

    @Test
    void descargaDeNotificacaoNaoVisivelDa404SemTicket() {
        when(resolver.resolver()).thenReturn(externo(utilizadorA, entidadeB));
        when(notificacaoRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitirDescarga(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);
        verifyNoInteractions(descarga);
    }

    @Test
    void asExcecoesTemOsCodigosEOCampoDoContrato() {
        assertThat(new AnexoNotificacaoTamanhoExcedidoException().getCodigo()).isEqualTo("NOTIF_FICHEIRO_EXCEDE_LIMITE");
        assertThat(new AnexoNotificacaoTamanhoExcedidoException().getMessage())
                .isEqualTo("O ficheiro excede o tamanho máximo permitido.");
        assertThat(new AnexoNotificacaoExtensaoNaoAceiteException().getCodigo()).isEqualTo("NOTIF_EXTENSAO_NAO_ACEITE");
        assertThat(new AnexoNotificacaoExtensaoNaoAceiteException().getMessage()).isEqualTo("Extensão não aceite.");
    }

    private NotificacaoDocumento carregarComo(ContextoUtilizador contexto, Notificacao notificacao, String nome,
                                              int bytes) {
        when(resolver.resolver()).thenReturn(contexto);
        when(notificacaoRepository.findOne(any(Specification.class))).thenReturn(Optional.of(notificacao));
        when(notificacaoRepository.findComBloqueioById(notificacao.getId())).thenReturn(Optional.of(notificacao));
        return service.carregar(notificacao.getId(), pedido(nome, bytes));
    }

    private static PedidoUpload pedido(String nome, int bytes) {
        return new PedidoUpload(null, null, List.of(), nome, "application/pdf", new ByteArrayInputStream(new byte[bytes]),
                () -> { });
    }

    private Notificacao respostaObrigatoria(EstadoNotificacao estado) {
        return notificacao(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, estado, n -> n.setEntidade(entidadeA));
    }

    private static Notificacao notificacao(TipoNotificacao tipo, Utilizador criadoPor, EstadoNotificacao estado,
                                           Consumer<Notificacao> destino) {
        Notificacao notificacao = new Notificacao(tipo, "Título", "Texto", criadoPor);
        notificacao.setEstado(estado);
        destino.accept(notificacao);
        ReflectionTestUtils.setField(notificacao, "id", UUID.randomUUID());
        return notificacao;
    }

    private static Utilizador utilizador(String nome, PerfilInterno perfil) {
        Utilizador utilizador = perfil == null ? new Utilizador(nome, nome.replace(' ', '.') + "@exemplo.pt", "INCM")
                : new Utilizador(nome, nome.replace(' ', '.') + "@exemplo.pt");
        utilizador.setPerfilInterno(perfil);
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());
        return utilizador;
    }

    private static Entidade comId(Entidade entidade, Long id) {
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }

    private static ContextoUtilizador externo(Utilizador utilizador, Entidade entidade) {
        return new ContextoUtilizador(utilizador.getId(), "INCM", null, entidade.getNipc(), "ENTIDADE", null, null,
                null, null, null, entidade.getId());
    }

    private ContextoUtilizador interno(Utilizador utilizador) {
        return interno(utilizador, PerfilInterno.MASTER);
    }

    private static ContextoUtilizador interno(Utilizador utilizador, PerfilInterno perfil) {
        return new ContextoUtilizador(utilizador.getId(), "AD_DGT", perfil, null, null, null, null, null, null, null,
                null);
    }

    /** Só o necessário para haver sincronização de transação, que o serviço usa para limpar o storage. */
    private static final class GestorSemRecursos extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // Sem recursos para abrir.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // Sem recursos para confirmar.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // Sem recursos para reverter.
        }
    }
}
