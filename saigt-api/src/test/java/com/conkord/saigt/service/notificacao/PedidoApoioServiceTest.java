package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoApoioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant RESOLVIDO_ANTES = Instant.parse("2026-11-10T09:00:00Z");

    private NotificacaoRepository notificacaoRepository;
    private PedidoApoioService service;
    private Processo processo;
    private Processo outroProcesso;
    private Utilizador autorDoPedido;

    @BeforeEach
    void criar() {
        notificacaoRepository = mock(NotificacaoRepository.class);
        service = new PedidoApoioService(notificacaoRepository, Clock.fixed(AGORA, ZoneOffset.UTC));
        processo = processoComId();
        outroProcesso = processoComId();
        autorDoPedido = new Utilizador("Ana Teixeira", "ana.teixeira@exemplo.pt", "INCM");
        ReflectionTestUtils.setField(autorDoPedido, "id", UUID.randomUUID());
    }

    @Test
    void pedidoDeApoioAtivoDoProcessoFicaResolvidoComAHoraAtual() {
        Notificacao pedido = guardado(pedidoDeApoio(EstadoNotificacao.ATIVA, processo));

        Notificacao resolvido = service.resolverPedidoApoio(processo, pedido.getId());

        assertThat(resolvido).isSameAs(pedido);
        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(pedido.getResolvidoEm()).isEqualTo(AGORA);
        verify(notificacaoRepository).findComBloqueioById(pedido.getId());
        verify(notificacaoRepository, never()).findById(any());
    }

    @Test
    void pedidoDeApoioGeralAtivoTambemFicaResolvido() {
        Notificacao pedido = guardado(pedidoDeApoio(EstadoNotificacao.ATIVA, null));

        service.resolverPedidoApoio(processo, pedido.getId());

        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(pedido.getResolvidoEm()).isEqualTo(AGORA);
    }

    @Test
    void pedidoDeApoioJaResolvidoERecusadoCom400NoCampoPedidoApoioIdESemMudar() {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.RESOLVIDA, processo);
        pedido.setResolvidoEm(RESOLVIDO_ANTES);
        guardado(pedido);

        assertNaoPendente(pedido);

        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(pedido.getResolvidoEm()).isEqualTo(RESOLVIDO_ANTES);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoNotificacao.class, names = {"LIDA", "DESATIVADA", "REMOVIDA"})
    void pedidoDeApoioNoutroEstadoQueNaoAtivaERecusado(EstadoNotificacao estado) {
        Notificacao pedido = guardado(pedidoDeApoio(estado, processo));

        assertNaoPendente(pedido);

        assertThat(pedido.getEstado()).isEqualTo(estado);
        assertThat(pedido.getResolvidoEm()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = "PEDIDO_APOIO", mode = EnumSource.Mode.EXCLUDE)
    void notificacaoDeOutroTipoERecusadaCom400NoCampoPedidoApoioId(TipoNotificacao tipo) {
        Notificacao outra = new Notificacao(tipo, "Título", "Texto", autorDoPedido);
        outra.setPerfilDestino(PerfilInterno.MASTER);
        outra.setProcesso(processo);
        guardado(outra);

        assertNaoPendente(outra);

        assertThat(outra.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(outra.getResolvidoEm()).isNull();
    }

    /** Os pedidos de apoio dirigem-se aos Master; um que não lhes chega não é deles para resolver. */
    @Test
    void pedidoDeApoioQueNaoSeDirigeAosMasterERecusado() {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA, processo);
        pedido.setPerfilDestino(null);
        guardado(pedido);

        assertNaoPendente(pedido);

        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
    }

    @Test
    void pedidoDeApoioDeOutroProcessoERecusadoCom400NoCampoPedidoApoioIdESemMudar() {
        Notificacao pedido = guardado(pedidoDeApoio(EstadoNotificacao.ATIVA, outroProcesso));

        assertThatThrownBy(() -> service.resolverPedidoApoio(processo, pedido.getId()))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(e.getMessage()).isEqualTo("O pedido de apoio é de outro processo.");
                    assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                            .containsExactly("pedidoApoioId");
                });

        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(pedido.getResolvidoEm()).isNull();
    }

    @Test
    void pedidoDeApoioInexistenteDa404() {
        UUID inexistente = UUID.randomUUID();
        when(notificacaoRepository.findComBloqueioById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolverPedidoApoio(processo, inexistente))
                .isInstanceOfSatisfying(RecursoNaoEncontradoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("NAO_ENCONTRADO");
                    assertThat(e.getMessage()).isEqualTo("Pedido de apoio não encontrado.");
                });
    }

    /** O bloqueio da linha só vale dentro da transação de quem chama; sem ela, a chamada falha. */
    @Test
    void exigeATransacaoDeQuemChama() throws NoSuchMethodException {
        Transactional transacional = PedidoApoioService.class
                .getMethod("resolverPedidoApoio", Processo.class, UUID.class).getAnnotation(Transactional.class);

        assertThat(transacional).isNotNull();
        assertThat(transacional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    private void assertNaoPendente(Notificacao notificacao) {
        assertThatThrownBy(() -> service.resolverPedidoApoio(processo, notificacao.getId()))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(e.getMessage()).isEqualTo("O pedido de apoio indicado não está pendente.");
                    assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                            .containsExactly("pedidoApoioId");
                });
    }

    private Notificacao pedidoDeApoio(EstadoNotificacao estado, Processo doProcesso) {
        Notificacao pedido = new Notificacao(TipoNotificacao.PEDIDO_APOIO, "Pedido de transferência", "Texto",
                autorDoPedido);
        pedido.setPerfilDestino(PerfilInterno.MASTER);
        pedido.setEstado(estado);
        pedido.setProcesso(doProcesso);
        return pedido;
    }

    private Notificacao guardado(Notificacao notificacao) {
        ReflectionTestUtils.setField(notificacao, "id", UUID.randomUUID());
        when(notificacaoRepository.findComBloqueioById(notificacao.getId())).thenReturn(Optional.of(notificacao));
        return notificacao;
    }

    private static Processo processoComId() {
        Processo novo = new Processo();
        ReflectionTestUtils.setField(novo, "id", UUID.randomUUID());
        return novo;
    }
}
