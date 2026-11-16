package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.login.LoginRecusadoEntidadeSemCorrespondencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PedidoApoioLoginRecusadoListenerTest {

    private static final String NOME = "Ricardo Fictício";
    private static final String EMAIL = "ricardo.ficticio@teste.invalid";
    private static final String DESCRICAO = "Junta de Freguesia Fictícia";
    private static final String NIPC = "509999990";

    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);
    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PedidoApoioLoginRecusadoListener listener = new PedidoApoioLoginRecusadoListener(
            utilizadorRepository, notificacaoRepository, publicador, transactionManager);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(PedidoApoioLoginRecusadoListener.class);
    private final UUID utilizadorId = UUID.randomUUID();
    private final UUID notificacaoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Utilizador utilizador = new Utilizador(NOME, EMAIL, "INCM");
        ReflectionTestUtils.setField(utilizador, "id", utilizadorId);
        when(utilizadorRepository.findById(utilizadorId)).thenReturn(Optional.of(utilizador));
        when(notificacaoRepository.saveAndFlush(any(Notificacao.class))).thenAnswer(invocacao -> {
            Notificacao notificacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(notificacao, "id", notificacaoId);
            return notificacao;
        });
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private LoginRecusadoEntidadeSemCorrespondencia evento(String nipc) {
        return new LoginRecusadoEntidadeSemCorrespondencia(utilizadorId, DESCRICAO, nipc);
    }

    private Notificacao gravada() {
        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void semDadosPessoaisNosLogs() {
        assertThat(logs.list).isNotEmpty().allSatisfy(linha -> assertThat(linha.getFormattedMessage())
                .doesNotContain(NOME, EMAIL, DESCRICAO, NIPC)
                .contains("utilizadorId=" + utilizadorId));
    }

    @Test
    void deveCriarUmPedidoDeApoioDaPlataformaParaOsMasterComNomeEmailDescricaoENipc() {
        listener.aoLoginRecusado(evento(NIPC));

        Notificacao pedido = gravada();
        assertThat(pedido.getTipo()).isEqualTo(TipoNotificacao.PEDIDO_APOIO);
        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(pedido.getPerfilDestino()).isEqualTo(PerfilInterno.MASTER);
        assertThat(pedido.getCriadoPor()).isNull();
        assertThat(pedido.getUtilizador()).isNull();
        assertThat(pedido.getEntidade()).isNull();
        assertThat(pedido.getProcesso()).isNull();
        assertThat(pedido.getTitulo()).isEqualTo("Login recusado: entidade sem correspondência na SAIGT");
        assertThat(pedido.getTexto()).isEqualTo("O utilizador Ricardo Fictício (ricardo.ficticio@teste.invalid) "
                + "tentou iniciar sessão na entidade «Junta de Freguesia Fictícia» (NIPC 509999990), que não está "
                + "criada na SAIGT. Crie ou ajuste a entidade em Gestão de Entidades.");
        verify(transactionManager).commit(any());
    }

    @Test
    void semNipcOTextoDizQueAIncmNaoODevolveu() {
        listener.aoLoginRecusado(evento(null));

        assertThat(gravada().getTexto())
                .contains("na entidade «Junta de Freguesia Fictícia» (sem NIPC devolvido pela INCM), que não está")
                .doesNotContain("(NIPC");
    }

    @Test
    void devePublicarONotificacaoCriadaEventoComOIdESemLote() {
        listener.aoLoginRecusado(evento(NIPC));

        verify(publicador).publishEvent(new NotificacaoCriadaEvento(List.of(notificacaoId), null, false));
        assertThat(logs.list).anySatisfy(linha -> {
            assertThat(linha.getLevel()).isEqualTo(Level.INFO);
            assertThat(linha.getFormattedMessage()).contains("Pedido de apoio de login recusado criado")
                    .contains("notificacaoId=" + notificacaoId);
        });
        semDadosPessoaisNosLogs();
    }

    @Test
    void naoCriaOutroQuandoJaHaUmPedidoIgualAtivo() {
        String texto = PedidoApoioLoginRecusadoListener.texto(
                utilizadorRepository.findById(utilizadorId).orElseThrow(), evento(NIPC));
        when(notificacaoRepository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTexto(
                TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, texto)).thenReturn(true);

        listener.aoLoginRecusado(evento(NIPC));

        verify(notificacaoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(publicador);
        assertThat(logs.list).anySatisfy(linha -> assertThat(linha.getFormattedMessage())
                .contains("Pedido de apoio já existente"));
        semDadosPessoaisNosLogs();
    }

    @Test
    void criaUmNovoQuandoSoHaUmPedidoIgualResolvido() {
        // a consulta só procura pedidos ATIVA: um RESOLVIDA igual devolve false
        when(notificacaoRepository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTexto(
                eq(TipoNotificacao.PEDIDO_APOIO), eq(EstadoNotificacao.ATIVA), eq(PerfilInterno.MASTER), anyString()))
                .thenReturn(false);

        listener.aoLoginRecusado(evento(NIPC));

        assertThat(gravada().getTipo()).isEqualTo(TipoNotificacao.PEDIDO_APOIO);
    }

    @Test
    void utilizadorInexistenteDeixaUmAvisoENaoCriaNada() {
        UUID outro = UUID.randomUUID();
        when(utilizadorRepository.findById(outro)).thenReturn(Optional.empty());

        listener.aoLoginRecusado(new LoginRecusadoEntidadeSemCorrespondencia(outro, DESCRICAO, NIPC));

        verify(notificacaoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(publicador);
        assertThat(logs.list).anySatisfy(linha -> {
            assertThat(linha.getLevel()).isEqualTo(Level.WARN);
            assertThat(linha.getFormattedMessage()).contains("utilizadorId=" + outro);
        });
    }

    @Test
    void falhaAGravarNaoPropagaEDeixaUmErroSemDadosPessoais() {
        when(notificacaoRepository.saveAndFlush(any(Notificacao.class)))
                .thenThrow(new DataAccessResourceFailureException("BD em baixo"));

        assertThatCode(() -> listener.aoLoginRecusado(evento(NIPC))).doesNotThrowAnyException();

        verify(transactionManager).rollback(any());
        verifyNoInteractions(publicador);
        assertThat(logs.list).anySatisfy(linha -> {
            assertThat(linha.getLevel()).isEqualTo(Level.ERROR);
            assertThat(linha.getFormattedMessage()).contains("utilizadorId=" + utilizadorId);
        });
        semDadosPessoaisNosLogs();
    }

    @Test
    void falhaNoCommitTambemNaoPropaga() {
        doThrow(new TransactionSystemException("commit falhou")).when(transactionManager).commit(any());

        assertThatCode(() -> listener.aoLoginRecusado(evento(NIPC))).doesNotThrowAnyException();

        assertThat(logs.list).anySatisfy(linha -> assertThat(linha.getLevel()).isEqualTo(Level.ERROR));
        semDadosPessoaisNosLogs();
    }
}
