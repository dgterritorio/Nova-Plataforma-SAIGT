package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.CriarMensagemNotificacaoResponse;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoSemConversacaoException;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagem;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificacaoMensagemServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant RESOLVIDA_ANTES = Instant.parse("2026-11-10T09:00:00Z");

    private NotificacaoRepository notificacaoRepository;
    private NotificacaoMensagemRepository mensagemRepository;
    private UtilizadorRepository utilizadorRepository;
    private ContextoUtilizadorResolver resolver;
    private NotificacaoMensagemService service;

    private Entidade entidadeA;
    private Entidade entidadeB;
    private Utilizador utilizadorU;
    private Utilizador outroDeA;
    private Utilizador master;
    private Utilizador outroMaster;
    private Utilizador tecnico;

    @BeforeEach
    void criar() {
        notificacaoRepository = mock(NotificacaoRepository.class);
        mensagemRepository = mock(NotificacaoMensagemRepository.class);
        utilizadorRepository = mock(UtilizadorRepository.class);
        resolver = mock(ContextoUtilizadorResolver.class);
        service = new NotificacaoMensagemService(notificacaoRepository, mensagemRepository, utilizadorRepository,
                new NotificacaoVisibilidade(), resolver, Clock.fixed(AGORA, ZoneOffset.UTC));

        entidadeA = comId(EntidadeTeste.nova("511000001", "Câmara A"), 1L);
        entidadeB = comId(EntidadeTeste.nova("511000002", "Câmara B"), 2L);
        utilizadorU = utilizador("Ana Teixeira", null);
        outroDeA = utilizador("Rui Matos", null);
        master = utilizador("Marta Master", PerfilInterno.MASTER);
        outroMaster = utilizador("Mário Master", PerfilInterno.MASTER);
        tecnico = utilizador("Tiago Técnico", PerfilInterno.TECNICO);

        when(notificacaoRepository.exists(any(Specification.class))).thenReturn(true);
        when(mensagemRepository.saveAndFlush(any(NotificacaoMensagem.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));
        for (Utilizador u : new Utilizador[]{utilizadorU, outroDeA, master, outroMaster, tecnico}) {
            when(utilizadorRepository.getReferenceById(u.getId())).thenReturn(u);
        }
    }

    // --- PEDIDO_APOIO ---

    @Test
    void pedidoDeApoioAtivoRespondidoPorUmMasterFicaResolvido() {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        CriarMensagemNotificacaoResponse resposta = responderComo(master, null, PerfilInterno.MASTER, pedido,
                "  Já tratámos do pedido.  ");

        assertThat(resposta.estado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(pedido.getResolvidoEm()).isEqualTo(AGORA);
        assertThat(resposta.mensagem().texto()).isEqualTo("Já tratámos do pedido.");
        assertThat(resposta.mensagem().autorId()).isEqualTo(master.getId());
        assertThat(resposta.mensagem().autorNome()).isEqualTo("Marta Master");
        assertThat(service.podeResponder(pedido, interno(master, PerfilInterno.MASTER))).isFalse();
    }

    @Test
    void pedidoDeApoioAtivoRespondidoPeloAutorContinuaAtivo() {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);
        assertThat(service.podeResponder(pedido, externo(utilizadorU, entidadeA))).isTrue();

        CriarMensagemNotificacaoResponse resposta = responderComo(utilizadorU, entidadeA, null, pedido, "Mais dados");

        assertThat(resposta.estado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(pedido.getResolvidoEm()).isNull();
    }

    @Test
    void pedidoDeApoioResolvidoNaoAceitaMensagensNemDoAutorNemDoMaster() {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.RESOLVIDA);

        assertRecusa(NotificacaoEstadoInvalidoException.class,
                () -> responderComo(utilizadorU, entidadeA, null, pedido, "Olá"));
        assertRecusa(NotificacaoEstadoInvalidoException.class,
                () -> responderComo(outroMaster, null, PerfilInterno.MASTER, pedido, "Olá"));
        verify(mensagemRepository, never()).saveAndFlush(any());
        assertThat(service.podeResponder(pedido, externo(utilizadorU, entidadeA))).isFalse();
        assertThat(service.podeResponder(pedido, interno(master, PerfilInterno.MASTER))).isFalse();
    }

    @Test
    void outroUtilizadorDaEntidadeDoAutorNaoParticipaNoPedidoDeApoio() {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        assertRecusa(NotificacaoPerfilNaoAutorizadoException.class,
                () -> responderComo(outroDeA, entidadeA, null, pedido, "Olá"));
        assertThat(service.podeResponder(pedido, externo(outroDeA, entidadeA))).isFalse();
    }

    // --- RESPOSTA_OBRIGATORIA ---

    @Test
    void respostaObrigatoriaDaEntidadeResolveComARespostaDeQualquerUtilizadorDaEntidade() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA, n -> n.setEntidade(entidadeA));
        assertThat(service.podeResponder(resposta, externo(outroDeA, entidadeA))).isTrue();

        CriarMensagemNotificacaoResponse criada = responderComo(outroDeA, entidadeA, null, resposta, "Segue o parecer");

        assertThat(criada.estado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(resposta.getResolvidoEm()).isEqualTo(AGORA);
    }

    @Test
    void respostaObrigatoriaParaUmUtilizadorResolveComARespostaDele() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA, n -> n.setUtilizador(utilizadorU));

        assertThat(responderComo(utilizadorU, entidadeA, null, resposta, "Feito").estado())
                .isEqualTo(EstadoNotificacao.RESOLVIDA);
    }

    @Test
    void respostaObrigatoriaDeOutraEntidadeNaoTemOUtilizadorComoParticipante() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA, n -> n.setEntidade(entidadeA));

        assertRecusa(NotificacaoPerfilNaoAutorizadoException.class,
                () -> responderComo(utilizadorU, entidadeB, null, resposta, "Olá"));
    }

    @Test
    void respostaObrigatoriaAtivaRespondidaPeloMasterAutorContinuaAtiva() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA, n -> n.setEntidade(entidadeA));
        assertThat(service.podeResponder(resposta, interno(master, PerfilInterno.MASTER))).isTrue();

        assertThat(responderComo(master, null, PerfilInterno.MASTER, resposta, "Lembrete").estado())
                .isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(resposta.getResolvidoEm()).isNull();
    }

    @Test
    void respostaObrigatoriaResolvidaContinuaAAceitarMensagensSemMudarOEstado() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.RESOLVIDA, n -> n.setEntidade(entidadeA));
        resposta.setResolvidoEm(RESOLVIDA_ANTES);
        assertThat(service.podeResponder(resposta, interno(master, PerfilInterno.MASTER))).isTrue();
        assertThat(service.podeResponder(resposta, externo(outroDeA, entidadeA))).isTrue();

        assertThat(responderComo(master, null, PerfilInterno.MASTER, resposta, "Obrigado").estado())
                .isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(responderComo(outroDeA, entidadeA, null, resposta, "De nada").estado())
                .isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(resposta.getResolvidoEm()).isEqualTo(RESOLVIDA_ANTES);
    }

    @Test
    void outroMasterQueVeALinhaMasNaoACriouNaoParticipaNaRespostaObrigatoria() {
        Notificacao resposta = respostaObrigatoria(EstadoNotificacao.ATIVA, n -> n.setEntidade(entidadeA));

        assertRecusa(NotificacaoPerfilNaoAutorizadoException.class,
                () -> responderComo(outroMaster, null, PerfilInterno.MASTER, resposta, "Olá"));
    }

    // --- REGULARIZACAO_PROCEDIMENTO ---

    @Test
    void regularizacaoNuncaMudaDeEstadoPorMensagem() {
        Notificacao regularizacao = notificacao(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, utilizadorU,
                EstadoNotificacao.ATIVA, n -> n.setPerfilDestino(PerfilInterno.MASTER));
        assertThat(service.podeResponder(regularizacao, interno(master, PerfilInterno.MASTER))).isTrue();

        assertThat(responderComo(master, null, PerfilInterno.MASTER, regularizacao, "Falta a deliberação").estado())
                .isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(responderComo(utilizadorU, entidadeA, null, regularizacao, "Segue").estado())
                .isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(regularizacao.getResolvidoEm()).isNull();
    }

    // --- tipo, estado, acesso ---

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"ESTATICA", "TEMPORARIA", "ALERTA"})
    void tiposSemConversacaoDao409(TipoNotificacao tipo) {
        Notificacao semConversa = notificacao(tipo, master, EstadoNotificacao.ATIVA, n -> n.setUtilizador(utilizadorU));

        assertRecusa(NotificacaoSemConversacaoException.class,
                () -> responderComo(utilizadorU, entidadeA, null, semConversa, "Olá"));
        assertThat(service.podeResponder(semConversa, externo(utilizadorU, entidadeA))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoNotificacao.class, names = {"LIDA", "DESATIVADA", "REMOVIDA"})
    void estadosQueNaoAceitamMensagensDao409(EstadoNotificacao estado) {
        Notificacao resposta = respostaObrigatoria(estado, n -> n.setEntidade(entidadeA));

        assertRecusa(NotificacaoEstadoInvalidoException.class,
                () -> responderComo(outroDeA, entidadeA, null, resposta, "Olá"));
        assertThat(service.podeResponder(resposta, externo(outroDeA, entidadeA))).isFalse();
    }

    @Test
    void regularizacaoResolvidaNaoAceitaMensagens() {
        Notificacao regularizacao = notificacao(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, utilizadorU,
                EstadoNotificacao.RESOLVIDA, n -> n.setPerfilDestino(PerfilInterno.MASTER));

        assertRecusa(NotificacaoEstadoInvalidoException.class,
                () -> responderComo(master, null, PerfilInterno.MASTER, regularizacao, "Olá"));
    }

    @ParameterizedTest
    @EnumSource(value = PerfilInterno.class, names = {"TECNICO", "VISITANTE"})
    void tecnicoEVisitanteRecebem404SemLerALinha(PerfilInterno perfil) {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);
        when(resolver.resolver()).thenReturn(interno(tecnico, perfil));

        assertThatThrownBy(() -> service.responder(pedido.getId(), "Olá"))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);
        verifyNoInteractions(notificacaoRepository, mensagemRepository);
        assertThat(service.podeResponder(pedido, interno(tecnico, perfil))).isFalse();
    }

    @Test
    void inexistenteENaoVisivelDaoOMesmo404() {
        when(resolver.resolver()).thenReturn(externo(utilizadorU, entidadeA));
        UUID inexistente = UUID.randomUUID();
        when(notificacaoRepository.findComBloqueioById(inexistente)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.responder(inexistente, "Olá"))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);

        Notificacao deB = respostaObrigatoria(EstadoNotificacao.ATIVA, n -> n.setEntidade(entidadeB));
        when(notificacaoRepository.findComBloqueioById(deB.getId())).thenReturn(Optional.of(deB));
        when(notificacaoRepository.exists(any(Specification.class))).thenReturn(false);
        assertThatThrownBy(() -> service.responder(deB.getId(), "Olá"))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);
        verify(mensagemRepository, never()).saveAndFlush(any());
    }

    @Test
    void ocodigoEAMensagemDasRecusasSaoOsDoContrato() {
        assertThat(new NotificacaoSemConversacaoException().getCodigo()).isEqualTo("NOTIF_TIPO_SEM_CONVERSACAO");
        assertThat(new NotificacaoEstadoInvalidoException().getCodigo()).isEqualTo("NOTIF_ESTADO_INVALIDO");
        assertThat(new NotificacaoEstadoInvalidoException().getMessage())
                .isEqualTo("A notificação já não aceita mensagens ou anexos.");
        assertThat(new NotificacaoPerfilNaoAutorizadoException("x").getCodigo())
                .isEqualTo("NOTIF_PERFIL_NAO_AUTORIZADO");
    }

    // --- apoio ---

    private CriarMensagemNotificacaoResponse responderComo(Utilizador utilizador, Entidade entidade,
                                                           PerfilInterno perfil, Notificacao notificacao, String texto) {
        when(resolver.resolver()).thenReturn(perfil == null ? externo(utilizador, entidade) : interno(utilizador, perfil));
        when(notificacaoRepository.findComBloqueioById(notificacao.getId())).thenReturn(Optional.of(notificacao));
        return service.responder(notificacao.getId(), texto);
    }

    private void assertRecusa(Class<? extends Throwable> tipo, Executable chamada) {
        assertThatThrownBy(chamada::execute).isInstanceOf(tipo);
    }

    private Notificacao pedidoDeApoio(EstadoNotificacao estado) {
        return notificacao(TipoNotificacao.PEDIDO_APOIO, utilizadorU, estado,
                n -> n.setPerfilDestino(PerfilInterno.MASTER));
    }

    private Notificacao respostaObrigatoria(EstadoNotificacao estado, Consumer<Notificacao> destino) {
        return notificacao(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, estado, destino);
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

    private static ContextoUtilizador interno(Utilizador utilizador, PerfilInterno perfil) {
        return new ContextoUtilizador(utilizador.getId(), "AD_DGT", perfil, null, null, null, null, null, null, null,
                null);
    }
}
