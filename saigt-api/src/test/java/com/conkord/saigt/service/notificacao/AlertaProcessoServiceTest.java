package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertaProcessoServiceTest {

    private static final long ENTIDADE_ID = 42L;

    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final UtilizadorEntidadeRepository utilizadorEntidadeRepository = mock(UtilizadorEntidadeRepository.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private final AlertaProcessoService servico =
            new AlertaProcessoService(notificacaoRepository, utilizadorEntidadeRepository, publicador);

    private final Utilizador autor = utilizador();
    private final Utilizador colega = utilizador();
    private Processo processo;

    @BeforeEach
    void setUp() {
        Entidade entidade = EntidadeTeste.nova("500000001", "Câmara de teste");
        ReflectionTestUtils.setField(entidade, "id", ENTIDADE_ID);
        processo = new Processo(null, null, autor, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        when(notificacaoRepository.saveAll(anyList())).thenAnswer(chamada -> {
            List<Notificacao> linhas = chamada.getArgument(0);
            linhas.forEach(linha -> ReflectionTestUtils.setField(linha, "id", UUID.randomUUID()));
            return linhas;
        });
    }

    private static Utilizador utilizador() {
        Utilizador utilizador = new Utilizador("Fictício", "ficticio@exemplo.pt");
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());
        return utilizador;
    }

    private NotificacaoCriadaEvento eventoPublicado() {
        ArgumentCaptor<NotificacaoCriadaEvento> captor = ArgumentCaptor.forClass(NotificacaoCriadaEvento.class);
        verify(publicador, times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void deveCriarUmaLinhaPorDestinatarioSemRepetirOAutorQueTambemEDaEntidade() {
        when(utilizadorEntidadeRepository.findUtilizadoresByEntidadeId(ENTIDADE_ID)).thenReturn(List.of(autor, colega));

        List<Notificacao> criadas = servico.criarParaAutorEEntidade(processo, "Título", "Texto");

        assertThat(criadas).extracting(Notificacao::getUtilizador).containsExactly(autor, colega);
        assertThat(criadas).allSatisfy(linha -> {
            assertThat(linha.getTipo()).isEqualTo(TipoNotificacao.TEMPORARIA);
            assertThat(linha.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
            assertThat(linha.getProcesso()).isSameAs(processo);
            assertThat(linha.getTitulo()).isEqualTo("Título");
            assertThat(linha.getTexto()).isEqualTo("Texto");
            assertThat(linha.getCriadoPor()).isNull();
            assertThat(linha.getEntidade()).isNull();
            assertThat(linha.getPerfilDestino()).isNull();
            assertThat(linha.getLoteId()).isNotNull();
        });
        assertThat(criadas).extracting(Notificacao::getLoteId).containsOnly(criadas.get(0).getLoteId());
    }

    @Test
    void devePublicarUmSoEventoComTodosOsIdsEOLote() {
        when(utilizadorEntidadeRepository.findUtilizadoresByEntidadeId(ENTIDADE_ID)).thenReturn(List.of(colega));

        List<Notificacao> criadas = servico.criarParaAutorEEntidade(processo, "Título", "Texto");

        NotificacaoCriadaEvento evento = eventoPublicado();
        assertThat(evento.notificacaoIds()).containsExactlyElementsOf(criadas.stream().map(Notificacao::getId).toList());
        assertThat(evento.loteId()).isEqualTo(criadas.get(0).getLoteId()).isNotNull();
        assertThat(evento.emailContactoTramitacao()).as("o email do alerta vai para o contacto de tramitação").isTrue();
    }

    @Test
    void comSoOAutorNaEntidadeDeveCriarUmaLinhaSemLote() {
        when(utilizadorEntidadeRepository.findUtilizadoresByEntidadeId(ENTIDADE_ID)).thenReturn(List.of(autor));

        List<Notificacao> criadas = servico.criarParaAutorEEntidade(processo, "Título", "Texto");

        assertThat(criadas).singleElement().satisfies(linha -> {
            assertThat(linha.getUtilizador()).isSameAs(autor);
            assertThat(linha.getLoteId()).isNull();
        });
        NotificacaoCriadaEvento evento = eventoPublicado();
        assertThat(evento.notificacaoIds()).containsExactly(criadas.get(0).getId());
        assertThat(evento.loteId()).isNull();
        assertThat(evento.emailContactoTramitacao()).isTrue();
    }

    @Test
    void criarParaUtilizadorDeveCriarUmaTemporariaAtivaSemLoteNemEntidadeEPublicarSemContactoDeTramitacao() {
        when(notificacaoRepository.save(any(Notificacao.class))).thenAnswer(chamada -> {
            Notificacao linha = chamada.getArgument(0);
            ReflectionTestUtils.setField(linha, "id", UUID.randomUUID());
            return linha;
        });

        Notificacao criada = servico.criarParaUtilizador(processo, colega, "Título", "Texto");

        assertThat(criada.getTipo()).isEqualTo(TipoNotificacao.TEMPORARIA);
        assertThat(criada.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(criada.getUtilizador()).isSameAs(colega);
        assertThat(criada.getProcesso()).isSameAs(processo);
        assertThat(criada.getTitulo()).isEqualTo("Título");
        assertThat(criada.getTexto()).isEqualTo("Texto");
        assertThat(criada.getCriadoPor()).isNull();
        assertThat(criada.getEntidade()).isNull();
        assertThat(criada.getPerfilDestino()).isNull();
        assertThat(criada.getLoteId()).isNull();
        verify(utilizadorEntidadeRepository, never()).findUtilizadoresByEntidadeId(any());
        NotificacaoCriadaEvento evento = eventoPublicado();
        assertThat(evento.notificacaoIds()).containsExactly(criada.getId());
        assertThat(evento.loteId()).isNull();
        assertThat(evento.emailContactoTramitacao()).isFalse();
    }
}
