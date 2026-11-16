package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.NotificacaoResumoResponse;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoTipoNaoReabrivelException;
import com.conkord.saigt.persistence.operacional.ContagemPorNotificacao;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificacaoReaberturaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant RESOLVIDA_EM = Instant.parse("2026-11-10T09:00:00Z");

    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final NotificacaoMensagemRepository mensagemRepository = mock(NotificacaoMensagemRepository.class);
    private final NotificacaoDocumentoRepository documentoRepository = mock(NotificacaoDocumentoRepository.class);
    private final ContextoUtilizadorResolver resolver = mock(ContextoUtilizadorResolver.class);
    private final NotificacaoReaberturaService service = new NotificacaoReaberturaService(notificacaoRepository,
            mensagemRepository, documentoRepository, new NotificacaoVisibilidade(), resolver,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private final Utilizador autor = utilizador("Ana Teixeira");
    private final Utilizador outro = utilizador("Rui Matos");
    private final Utilizador master = utilizador("Marta Master");

    @BeforeEach
    void setUp() {
        when(notificacaoRepository.exists(any(Specification.class))).thenReturn(true);
        when(mensagemRepository.contarPorNotificacao(anyList())).thenReturn(List.of());
        when(documentoRepository.contarPorNotificacao(anyList())).thenReturn(List.of());
    }

    @Test
    void oAutorReabreOPedidoResolvidoQueFicaAtivoSemDataDeResolucao() {
        Notificacao pedido = guardada(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.RESOLVIDA);
        externo(autor);
        ContagemPorNotificacao duasMensagens = contagem(pedido.getId(), 2);
        when(mensagemRepository.contarPorNotificacao(List.of(pedido.getId()))).thenReturn(List.of(duasMensagens));

        NotificacaoResumoResponse resposta = service.reabrir(pedido.getId());

        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(pedido.getResolvidoEm()).isNull();
        assertThat(resposta.estado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(resposta.resolvidoEm()).isNull();
        assertThat(resposta.numeroMensagens()).isEqualTo(2);
        verify(notificacaoRepository).findComBloqueioById(pedido.getId());
    }

    @Test
    void oMasterQueVeOPedidoRecebe403EOEstadoNaoMuda() {
        Notificacao pedido = guardada(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.RESOLVIDA);
        interno(master, PerfilInterno.MASTER);

        assertThatThrownBy(() -> service.reabrir(pedido.getId()))
                .isInstanceOfSatisfying(NotificacaoPerfilNaoAutorizadoException.class, erro ->
                        assertThat(erro.getMessage()).isEqualTo("Só quem criou o pedido o pode reabrir."));
        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(pedido.getResolvidoEm()).isEqualTo(RESOLVIDA_EM);
    }

    @Test
    void quemNaoVeOPedidoRecebe404IgualAoDeUmIdInexistente() {
        Notificacao pedido = guardada(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.RESOLVIDA);
        externo(outro);
        when(notificacaoRepository.exists(any(Specification.class))).thenReturn(false);
        UUID inexistente = UUID.randomUUID();
        when(notificacaoRepository.findComBloqueioById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reabrir(pedido.getId())).isInstanceOf(NotificacaoNaoEncontradaException.class);
        assertThatThrownBy(() -> service.reabrir(inexistente)).isInstanceOf(NotificacaoNaoEncontradaException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilInterno.class, names = {"TECNICO", "VISITANTE"})
    void tecnicoEVisitanteRecebem404SemLerALinha(PerfilInterno perfil) {
        Notificacao pedido = guardada(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.RESOLVIDA);
        interno(master, perfil);

        assertThatThrownBy(() -> service.reabrir(pedido.getId())).isInstanceOf(NotificacaoNaoEncontradaException.class);
        verify(notificacaoRepository, never()).findComBloqueioById(any());
    }

    @Test
    void pedidoAtivoDa409EstadoInvalido() {
        Notificacao pedido = guardada(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA);
        externo(autor);

        assertThatThrownBy(() -> service.reabrir(pedido.getId()))
                .isInstanceOfSatisfying(NotificacaoEstadoInvalidoException.class, erro ->
                        assertThat(erro.getCodigo()).isEqualTo("NOTIF_ESTADO_INVALIDO"));
        assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"RESPOSTA_OBRIGATORIA", "REGULARIZACAO_PROCEDIMENTO"})
    void outrosTiposResolvidosDao409TipoNaoReabrivelMesmoAoAutor(TipoNotificacao tipo) {
        Notificacao notificacao = guardada(tipo, EstadoNotificacao.RESOLVIDA);
        externo(autor);

        assertThatThrownBy(() -> service.reabrir(notificacao.getId()))
                .isInstanceOfSatisfying(NotificacaoTipoNaoReabrivelException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("NOTIF_TIPO_NAO_REABRIVEL");
                    assertThat(erro.getMessage()).isEqualTo("Só um pedido de apoio pode ser reaberto.");
                });
        assertThat(notificacao.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
    }

    @Test
    void podeReabrirSoParaOAutorDeUmPedidoResolvido() {
        Notificacao resolvido = guardada(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.RESOLVIDA);
        Notificacao ativo = guardada(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA);
        Notificacao regularizacao = guardada(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.RESOLVIDA);

        assertThat(NotificacaoReaberturaService.podeReabrir(resolvido, contexto(autor, null))).isTrue();
        assertThat(NotificacaoReaberturaService.podeReabrir(resolvido, contexto(master, PerfilInterno.MASTER)))
                .isFalse();
        assertThat(NotificacaoReaberturaService.podeReabrir(ativo, contexto(autor, null))).isFalse();
        assertThat(NotificacaoReaberturaService.podeReabrir(regularizacao, contexto(autor, null))).isFalse();
    }

    private Notificacao guardada(TipoNotificacao tipo, EstadoNotificacao estado) {
        Notificacao notificacao = new Notificacao(tipo, "Pedido", "Texto", autor);
        ReflectionTestUtils.setField(notificacao, "id", UUID.randomUUID());
        notificacao.setPerfilDestino(PerfilInterno.MASTER);
        if (estado == EstadoNotificacao.RESOLVIDA) {
            notificacao.resolver(RESOLVIDA_EM);
        } else {
            notificacao.setEstado(estado);
        }
        when(notificacaoRepository.findComBloqueioById(notificacao.getId())).thenReturn(Optional.of(notificacao));
        return notificacao;
    }

    private void externo(Utilizador utilizador) {
        when(resolver.resolver()).thenReturn(contexto(utilizador, null));
    }

    private void interno(Utilizador utilizador, PerfilInterno perfil) {
        when(resolver.resolver()).thenReturn(contexto(utilizador, perfil));
    }

    private static ContextoUtilizador contexto(Utilizador utilizador, PerfilInterno perfil) {
        return perfil == null
                ? new ContextoUtilizador(utilizador.getId(), "INCM", null, "511000001", "ENTIDADE", null, null, null,
                null, null, 1L)
                : new ContextoUtilizador(utilizador.getId(), "AD_DGT", perfil, null, null, null, null, null, null,
                null, null);
    }

    private static ContagemPorNotificacao contagem(UUID id, long total) {
        return new ContagemPorNotificacao() {
            @Override
            public UUID getNotificacaoId() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static Utilizador utilizador(String nome) {
        Utilizador utilizador = new Utilizador(nome, "ficticio@exemplo.pt");
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());
        return utilizador;
    }
}
