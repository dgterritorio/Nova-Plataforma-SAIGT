package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.NotificacaoResumoResponse;
import com.conkord.saigt.exception.ConkordException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoEstadoInvalidoException;
import com.conkord.saigt.exception.NotificacaoNaoEncontradaException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoTipoNaoRemovivelException;
import com.conkord.saigt.exception.NotificacaoTipoSemLeituraException;
import com.conkord.saigt.exception.NotificacaoTipoSemResolucaoManualException;
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
import org.junit.jupiter.api.function.Executable;
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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificacaoEstadoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");

    private NotificacaoRepository notificacaoRepository;
    private ContextoUtilizadorResolver resolver;
    private NotificacaoEstadoService service;

    private Utilizador utilizadorU;
    private Utilizador outroUtilizador;
    private Utilizador master;

    @BeforeEach
    void criar() {
        notificacaoRepository = mock(NotificacaoRepository.class);
        NotificacaoMensagemRepository mensagemRepository = mock(NotificacaoMensagemRepository.class);
        NotificacaoDocumentoRepository documentoRepository = mock(NotificacaoDocumentoRepository.class);
        resolver = mock(ContextoUtilizadorResolver.class);
        service = new NotificacaoEstadoService(notificacaoRepository, mensagemRepository, documentoRepository,
                new NotificacaoVisibilidade(), resolver, Clock.fixed(AGORA, ZoneOffset.UTC));

        utilizadorU = utilizador("Ana Teixeira", null);
        outroUtilizador = utilizador("Rui Matos", null);
        master = utilizador("Marta Master", PerfilInterno.MASTER);
        when(mensagemRepository.contarPorNotificacao(anyCollection())).thenReturn(List.of());
        when(documentoRepository.contarPorNotificacao(anyCollection())).thenReturn(List.of());
    }

    // --- ler ---

    @Test
    void oDestinatarioLeASuaTemporariaAtiva() {
        Notificacao temporaria = notificacao(TipoNotificacao.TEMPORARIA, null, n -> n.setUtilizador(utilizadorU));
        when(notificacaoRepository.marcarLida(temporaria.getId(), AGORA)).thenAnswer(i -> {
            temporaria.setEstado(EstadoNotificacao.LIDA);
            temporaria.setLidoEm(AGORA);
            return 1;
        });
        visivelPara(externo(utilizadorU), temporaria);

        NotificacaoResumoResponse resposta = service.ler(temporaria.getId());

        assertThat(resposta.estado()).isEqualTo(EstadoNotificacao.LIDA);
        assertThat(resposta.lidoEm()).isEqualTo(AGORA);
        assertThat(service.acoesDisponiveis(comEstado(TipoNotificacao.TEMPORARIA, EstadoNotificacao.ATIVA,
                n -> n.setUtilizador(utilizadorU)), externo(utilizadorU))).containsExactly("LER");
    }

    @Test
    void outroUtilizadorQueVeATemporariaRecebe403() {
        Notificacao temporaria = notificacao(TipoNotificacao.TEMPORARIA, outroUtilizador,
                n -> n.setUtilizador(utilizadorU));
        visivelPara(externo(outroUtilizador), temporaria);

        assertRecusa(NotificacaoPerfilNaoAutorizadoException.class, "NOTIF_PERFIL_NAO_AUTORIZADO",
                () -> service.ler(temporaria.getId()));
        verify(notificacaoRepository, never()).marcarLida(any(), any());
        assertThat(service.acoesDisponiveis(temporaria, externo(outroUtilizador))).isEmpty();
    }

    @Test
    void qualquerMasterLeOAlertaEOUtilizadorExternoNao() {
        Notificacao alerta = notificacao(TipoNotificacao.ALERTA, null, n -> n.setPerfilDestino(PerfilInterno.MASTER));
        when(notificacaoRepository.marcarLida(alerta.getId(), AGORA)).thenReturn(1);
        visivelPara(interno(master, PerfilInterno.MASTER), alerta);

        service.ler(alerta.getId());

        verify(notificacaoRepository).marcarLida(alerta.getId(), AGORA);
        assertThat(service.acoesDisponiveis(alerta, interno(master, PerfilInterno.MASTER)))
                .containsExactly("LER", "REMOVER");

        visivelPara(externo(utilizadorU), alerta);
        assertRecusa(NotificacaoPerfilNaoAutorizadoException.class, "NOTIF_PERFIL_NAO_AUTORIZADO",
                () -> service.ler(alerta.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"ESTATICA", "RESPOSTA_OBRIGATORIA", "PEDIDO_APOIO",
            "REGULARIZACAO_PROCEDIMENTO"})
    void tiposSemLeituraDao409(TipoNotificacao tipo) {
        Notificacao notificacao = notificacao(tipo, utilizadorU, n -> n.setUtilizador(utilizadorU));
        visivelPara(externo(utilizadorU), notificacao);

        assertRecusa(NotificacaoTipoSemLeituraException.class, "NOTIF_TIPO_SEM_LEITURA",
                () -> service.ler(notificacao.getId()));
        verify(notificacaoRepository, never()).marcarLida(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoNotificacao.class, names = {"LIDA", "RESOLVIDA", "DESATIVADA", "REMOVIDA"})
    void temporariaQueJaNaoEstaAtivaDa409(EstadoNotificacao estado) {
        Notificacao temporaria = comEstado(TipoNotificacao.TEMPORARIA, estado, n -> n.setUtilizador(utilizadorU));
        visivelPara(externo(utilizadorU), temporaria);

        assertRecusa(NotificacaoEstadoInvalidoException.class, "NOTIF_ESTADO_INVALIDO",
                () -> service.ler(temporaria.getId()));
        verify(notificacaoRepository, never()).marcarLida(any(), any());
        assertThat(service.acoesDisponiveis(temporaria, externo(utilizadorU))).isEmpty();
    }

    // --- resolver ---

    @Test
    void masterResolveOPedidoDeApoioAtivo() {
        Notificacao pedido = pedidoDeApoio();
        when(notificacaoRepository.marcarResolvida(pedido.getId(), AGORA)).thenAnswer(i -> {
            pedido.resolver(AGORA);
            return 1;
        });
        visivelPara(interno(master, PerfilInterno.MASTER), pedido);
        assertThat(service.acoesDisponiveis(pedido, interno(master, PerfilInterno.MASTER))).containsExactly("RESOLVER");

        NotificacaoResumoResponse resposta = service.resolver(pedido.getId());

        assertThat(resposta.estado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(resposta.resolvidoEm()).isEqualTo(AGORA);
    }

    @Test
    void oAutorDoPedidoDeApoioNaoOResolveERecebe403() {
        Notificacao pedido = pedidoDeApoio();
        visivelPara(externo(utilizadorU), pedido);

        assertRecusa(NotificacaoPerfilNaoAutorizadoException.class, "NOTIF_PERFIL_NAO_AUTORIZADO",
                () -> service.resolver(pedido.getId()));
        assertThatThrownBy(() -> service.resolver(pedido.getId()))
                .hasMessage("Só um utilizador Master pode resolver esta notificação.");
        assertThat(service.acoesDisponiveis(pedido, externo(utilizadorU))).isEmpty();
    }

    @Test
    void regularizacaoNaoSeResolveManualmenteComAMensagemPropria() {
        Notificacao regularizacao = notificacao(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, utilizadorU,
                n -> n.setPerfilDestino(PerfilInterno.MASTER));
        visivelPara(interno(master, PerfilInterno.MASTER), regularizacao);

        assertRecusa(NotificacaoTipoSemResolucaoManualException.class, "NOTIF_TIPO_SEM_RESOLUCAO_MANUAL",
                () -> service.resolver(regularizacao.getId()));
        assertThatThrownBy(() -> service.resolver(regularizacao.getId()))
                .hasMessage("Esta notificação resolve-se com o registo do procedimento.");
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"ESTATICA", "TEMPORARIA", "RESPOSTA_OBRIGATORIA", "ALERTA"})
    void restantesTiposNaoSeResolvemManualmente(TipoNotificacao tipo) {
        Notificacao notificacao = notificacao(tipo, master, n -> n.setPerfilDestino(PerfilInterno.MASTER));
        visivelPara(interno(master, PerfilInterno.MASTER), notificacao);

        assertRecusa(NotificacaoTipoSemResolucaoManualException.class, "NOTIF_TIPO_SEM_RESOLUCAO_MANUAL",
                () -> service.resolver(notificacao.getId()));
        assertThatThrownBy(() -> service.resolver(notificacao.getId()))
                .hasMessage("Esta notificação não se resolve manualmente.");
        assertThat(service.acoesDisponiveis(notificacao, interno(master, PerfilInterno.MASTER)))
                .doesNotContain("RESOLVER");
    }

    @Test
    void pedidoDeApoioJaResolvidoDa409() {
        Notificacao pedido = pedidoDeApoio();
        pedido.resolver(AGORA);
        visivelPara(interno(master, PerfilInterno.MASTER), pedido);

        assertRecusa(NotificacaoEstadoInvalidoException.class, "NOTIF_ESTADO_INVALIDO",
                () -> service.resolver(pedido.getId()));
        assertThatThrownBy(() -> service.resolver(pedido.getId())).hasMessage("A notificação já não está ativa.");
    }

    // --- remover ---

    @Test
    void removerUmaNoticiaDeLoteAtualizaOLoteInteiro() {
        UUID loteId = UUID.randomUUID();
        Notificacao noticia = notificacao(TipoNotificacao.ESTATICA, master, n -> n.setLoteId(loteId));
        when(notificacaoRepository.marcarRemovidasDoLote(loteId, AGORA)).thenReturn(3);
        visivelPara(interno(master, PerfilInterno.MASTER), noticia);

        assertThat(service.remover(noticia.getId()).linhasAtualizadas()).isEqualTo(3);
        verify(notificacaoRepository, never()).marcarRemovida(any(), any());
        verify(notificacaoRepository, never()).delete(any(Notificacao.class));
        verify(notificacaoRepository, never()).deleteById(any());
    }

    @Test
    void removerUmaNoticiaSemLoteOuUmAlertaAtualizaSoALinha() {
        Notificacao noticia = notificacao(TipoNotificacao.ESTATICA, master, n -> { });
        Notificacao alerta = notificacao(TipoNotificacao.ALERTA, null, n -> n.setPerfilDestino(PerfilInterno.MASTER));
        when(notificacaoRepository.marcarRemovida(any(), any())).thenReturn(1);

        visivelPara(interno(master, PerfilInterno.MASTER), noticia);
        assertThat(service.remover(noticia.getId()).linhasAtualizadas()).isEqualTo(1);
        visivelPara(interno(master, PerfilInterno.MASTER), alerta);
        assertThat(service.remover(alerta.getId()).linhasAtualizadas()).isEqualTo(1);

        verify(notificacaoRepository).marcarRemovida(noticia.getId(), AGORA);
        verify(notificacaoRepository).marcarRemovida(alerta.getId(), AGORA);
        verify(notificacaoRepository, never()).marcarRemovidasDoLote(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"TEMPORARIA", "RESPOSTA_OBRIGATORIA", "PEDIDO_APOIO",
            "REGULARIZACAO_PROCEDIMENTO"})
    void tiposNaoRemoviveisDao409(TipoNotificacao tipo) {
        Notificacao notificacao = notificacao(tipo, master, n -> n.setPerfilDestino(PerfilInterno.MASTER));
        visivelPara(interno(master, PerfilInterno.MASTER), notificacao);

        assertRecusa(NotificacaoTipoNaoRemovivelException.class, "NOTIF_TIPO_NAO_REMOVIVEL",
                () -> service.remover(notificacao.getId()));
        assertThat(service.acoesDisponiveis(notificacao, interno(master, PerfilInterno.MASTER)))
                .doesNotContain("REMOVER");
    }

    @Test
    void utilizadorExternoNaoRemoveANoticiaParaTodos() {
        Notificacao paraTodos = notificacao(TipoNotificacao.ESTATICA, master, n -> { });
        visivelPara(externo(utilizadorU), paraTodos);

        assertRecusa(NotificacaoPerfilNaoAutorizadoException.class, "NOTIF_PERFIL_NAO_AUTORIZADO",
                () -> service.remover(paraTodos.getId()));
        assertThat(service.acoesDisponiveis(paraTodos, externo(utilizadorU))).isEmpty();
        assertThat(service.acoesDisponiveis(paraTodos, interno(master, PerfilInterno.MASTER)))
                .containsExactly("REMOVER");
    }

    // --- comum ---

    @Test
    void cadaOperacaoDa409QuandoAAtualizacaoCondicionalNaoMudaNenhumaLinha() {
        Notificacao alerta = notificacao(TipoNotificacao.ALERTA, null, n -> n.setPerfilDestino(PerfilInterno.MASTER));
        Notificacao pedido = pedidoDeApoio();
        visivelPara(interno(master, PerfilInterno.MASTER), alerta);
        when(notificacaoRepository.marcarLida(any(), any())).thenReturn(0);
        when(notificacaoRepository.marcarRemovida(any(), any())).thenReturn(0);
        when(notificacaoRepository.marcarResolvida(any(), any())).thenReturn(0);

        assertRecusa(NotificacaoEstadoInvalidoException.class, "NOTIF_ESTADO_INVALIDO",
                () -> service.ler(alerta.getId()));
        assertRecusa(NotificacaoEstadoInvalidoException.class, "NOTIF_ESTADO_INVALIDO",
                () -> service.remover(alerta.getId()));
        visivelPara(interno(master, PerfilInterno.MASTER), pedido);
        assertRecusa(NotificacaoEstadoInvalidoException.class, "NOTIF_ESTADO_INVALIDO",
                () -> service.resolver(pedido.getId()));
        verify(notificacaoRepository).marcarLida(alerta.getId(), AGORA);
        verify(notificacaoRepository).marcarRemovida(alerta.getId(), AGORA);
        verify(notificacaoRepository).marcarResolvida(pedido.getId(), AGORA);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilInterno.class, names = {"TECNICO", "VISITANTE"})
    void tecnicoEVisitanteRecebem404SemLerALinha(PerfilInterno perfil) {
        when(resolver.resolver()).thenReturn(interno(master, perfil));
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.ler(id)).isInstanceOf(NotificacaoNaoEncontradaException.class);
        assertThatThrownBy(() -> service.resolver(id)).isInstanceOf(NotificacaoNaoEncontradaException.class);
        assertThatThrownBy(() -> service.remover(id)).isInstanceOf(NotificacaoNaoEncontradaException.class);
        verifyNoInteractions(notificacaoRepository);
    }

    @Test
    void naoVisivelOuInexistenteDa404() {
        when(resolver.resolver()).thenReturn(interno(master, PerfilInterno.MASTER));
        when(notificacaoRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.ler(id)).isInstanceOf(NotificacaoNaoEncontradaException.class);
        assertThatThrownBy(() -> service.resolver(id)).isInstanceOf(NotificacaoNaoEncontradaException.class);
        assertThatThrownBy(() -> service.remover(id)).isInstanceOf(NotificacaoNaoEncontradaException.class);
    }

    @Test
    void asExcecoesNovasSao409DeEstadoComOCodigoDoContrato() {
        assertThat(new NotificacaoTipoSemLeituraException()).isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Este tipo de notificação não tem leitura.");
        assertThat(NotificacaoTipoSemResolucaoManualException.outroTipo()).isInstanceOf(EstadoInvalidoException.class);
        assertThat(new NotificacaoTipoNaoRemovivelException()).isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Este tipo de notificação não pode ser removido.");
    }

    // --- apoio ---

    private void visivelPara(ContextoUtilizador contexto, Notificacao notificacao) {
        when(resolver.resolver()).thenReturn(contexto);
        when(notificacaoRepository.findOne(any(Specification.class))).thenReturn(Optional.of(notificacao));
        when(notificacaoRepository.findById(notificacao.getId())).thenReturn(Optional.of(notificacao));
    }

    private void assertRecusa(Class<? extends ConkordException> tipo, String codigo, Executable chamada) {
        assertThatThrownBy(chamada::execute).isInstanceOf(tipo)
                .satisfies(erro -> assertThat(((ConkordException) erro).getCodigo()).isEqualTo(codigo));
    }

    private Notificacao pedidoDeApoio() {
        return notificacao(TipoNotificacao.PEDIDO_APOIO, utilizadorU, n -> n.setPerfilDestino(PerfilInterno.MASTER));
    }

    private static Notificacao comEstado(TipoNotificacao tipo, EstadoNotificacao estado, Consumer<Notificacao> destino) {
        Notificacao notificacao = notificacao(tipo, null, destino);
        notificacao.setEstado(estado);
        return notificacao;
    }

    private static Notificacao notificacao(TipoNotificacao tipo, Utilizador criadoPor, Consumer<Notificacao> destino) {
        Notificacao notificacao = new Notificacao(tipo, "Título", "Texto", criadoPor);
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

    private static ContextoUtilizador externo(Utilizador utilizador) {
        return new ContextoUtilizador(utilizador.getId(), "INCM", null, "511000001", "ENTIDADE", null, null, null,
                null, null, 1L);
    }

    private static ContextoUtilizador interno(Utilizador utilizador, PerfilInterno perfil) {
        return new ContextoUtilizador(utilizador.getId(), "AD_DGT", perfil, null, null, null, null, null, null, null,
                null);
    }
}
