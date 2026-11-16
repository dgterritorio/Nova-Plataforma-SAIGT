package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** O `AlertaProcessoService` é o real, para provar a linha gravada; só os repositórios são simulados. */
class AvisoPedidoPcgtServiceTest {

    private static final UUID PROCESSO_ID = UUID.randomUUID();

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private final AvisoPedidoPcgtService servico = new AvisoPedidoPcgtService(processoRepository,
            notificacaoRepository, new AlertaProcessoService(notificacaoRepository,
            mock(UtilizadorEntidadeRepository.class), publicador));

    private final Processo processo = mock(Processo.class);
    private final Dinamica dinamica = mock(Dinamica.class);
    private final TipoInstrumento tipoInstrumento = mock(TipoInstrumento.class);
    private final TipoProcedimento tipoProcedimento = mock(TipoProcedimento.class);
    private final Utilizador autor = new Utilizador("Fictício", "ficticio@exemplo.pt");

    @BeforeEach
    void decisaoDeInicioDeUmPdmComGeoconform() {
        ReflectionTestUtils.setField(autor, "id", UUID.randomUUID());
        InstrumentoDinamicaConfig dinamicaConfig = mock(InstrumentoDinamicaConfig.class);
        InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);
        when(dinamicaConfig.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(dinamica.getInstrumentoDinamicaConfig()).thenReturn(dinamicaConfig);
        when(dinamica.getComGeoconform()).thenReturn(true);
        when(procedimento.getTipoProcedimento()).thenReturn(tipoProcedimento);
        when(tipoProcedimento.getCodigo()).thenReturn("DECISAO_INICIO");
        when(tipoInstrumento.getCodigo()).thenReturn("PDM");
        when(processo.getId()).thenReturn(PROCESSO_ID);
        when(processo.getNumeroProcesso()).thenReturn("00107");
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        when(processo.getCriadoPor()).thenReturn(autor);
        when(processoRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(processo));
        when(notificacaoRepository.save(any(Notificacao.class))).thenAnswer(chamada -> {
            Notificacao linha = chamada.getArgument(0);
            ReflectionTestUtils.setField(linha, "id", UUID.randomUUID());
            return linha;
        });
    }

    private Notificacao gravada() {
        ArgumentCaptor<Notificacao> linha = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(linha.capture());
        return linha.getValue();
    }

    @Test
    void deveCriarUmAlertaTemporarioAtivoDoProcessoParaOAutorDaDecisaoDeInicio() {
        assertThat(servico.avisarPedidoEnviado(PROCESSO_ID)).isTrue();

        Notificacao linha = gravada();
        assertThat(linha.getTipo()).isEqualTo(TipoNotificacao.TEMPORARIA);
        assertThat(linha.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(linha.getProcesso()).isSameAs(processo);
        assertThat(linha.getUtilizador()).isSameAs(autor);
        assertThat(linha.getEntidade()).isNull();
        assertThat(linha.getPerfilDestino()).isNull();
        assertThat(linha.getLoteId()).isNull();
        assertThat(linha.getCriadoPor()).isNull();
        verify(publicador).publishEvent(any(NotificacaoCriadaEvento.class));
    }

    @Test
    void oTituloEOTextoDizemQueOPedidoFoiEnviadoParaAPcgtComONumeroDoProcesso() {
        servico.avisarPedidoEnviado(PROCESSO_ID);

        Notificacao linha = gravada();
        assertThat(linha.getTitulo()).isEqualTo("Pedido enviado para a PCGT");
        assertThat(linha.getTexto()).isEqualTo("O pedido 00107 foi enviado para a PCGT. A partir deste momento será "
                + "possível o preenchimento de toda a documentação na plataforma.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void naoDeveAvisarNumaDinamicaSemGeoconform(Boolean comGeoconform) {
        when(dinamica.getComGeoconform()).thenReturn(comGeoconform);

        assertThat(servico.avisarPedidoEnviado(PROCESSO_ID)).isFalse();

        verify(notificacaoRepository, never()).save(any());
        verifyNoInteractions(publicador);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONSTITUICAO_CC", "DISCUSSAO_PUBLICA", "APROVACAO", "PUBLICACAO_FINAL"})
    void naoDeveAvisarNumProcessoQueNaoEDaDecisaoDeInicio(String procedimento) {
        when(tipoProcedimento.getCodigo()).thenReturn(procedimento);

        assertThat(servico.avisarPedidoEnviado(PROCESSO_ID)).isFalse();

        verify(notificacaoRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PU", "PP", "PIOT", "PEOT"})
    void naoDeveAvisarNaDecisaoDeInicioDeUmInstrumentoQueNaoEUmPdm(String tipo) {
        when(tipoInstrumento.getCodigo()).thenReturn(tipo);

        assertThat(servico.avisarPedidoEnviado(PROCESSO_ID)).isFalse();

        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void naoDeveRepetirOAvisoQuandoAComunicacaoSeRepeteDepoisDaEntradaEmVigor() {
        when(notificacaoRepository.existsByProcesso_IdAndTitulo(PROCESSO_ID, "Pedido enviado para a PCGT"))
                .thenReturn(true);

        assertThat(servico.avisarPedidoEnviado(PROCESSO_ID)).isFalse();

        verify(notificacaoRepository, never()).save(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void naoDeveAvisarSemProcesso() {
        when(processoRepository.findById(PROCESSO_ID)).thenReturn(Optional.empty());

        assertThat(servico.avisarPedidoEnviado(PROCESSO_ID)).isFalse();

        verify(notificacaoRepository, never()).save(any());
    }
}
