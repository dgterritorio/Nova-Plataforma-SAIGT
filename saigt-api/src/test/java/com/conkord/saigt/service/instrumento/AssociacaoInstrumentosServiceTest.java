package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.AssociarInstrumentosRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.AssociacaoInvalidaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssociacaoInstrumentosServiceTest {

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
    private final InstrumentoDinamicaConfigRepository configRepository = mock(InstrumentoDinamicaConfigRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);

    private final AssociacaoInstrumentosService service = new AssociacaoInstrumentosService(dinamicaRepository,
            processoRepository, instrumentoRepository, configRepository, autorizacaoService, contextoUtilizadorResolver,
            auditoriaService);

    private final TipoInstrumento pdm = tipo(1L, "PDM");
    private final TipoInstrumento prot = tipo(9L, "PROT");
    private UUID autorId;
    private Utilizador autor;
    private InstrumentoDinamicaConfig config;
    private Dinamica dinamica;
    private Processo processo;
    private ContextoUtilizador contexto;

    @BeforeEach
    void setUp() {
        autorId = UUID.randomUUID();
        autor = new Utilizador("Autor", "autor@ccdr-lvt.pt", "INCM");
        ReflectionTestUtils.setField(autor, "id", autorId);
        TipoDinamica suspensao = new TipoDinamica("SUSPENSAO_CCDR", "Suspensão");
        ReflectionTestUtils.setField(suspensao, "id", 20L);
        config = new InstrumentoDinamicaConfig(pdm, suspensao);
        config.setAssociacaoInstrumentos(AssociacaoInstrumentos.OPCIONAL);
        dinamica = new Dinamica(config, "Suspensão Sintra", autor);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());
        processo = processo(autor, EstadoProcesso.EM_SUBMISSAO);
        contexto = new ContextoUtilizador(autorId, "INCM", null, "517622610", "ENTIDADE", null, null, null, null,
                TipoEntidade.CCDR_DR, 8001L);

        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        when(dinamicaRepository.findById(dinamica.getId())).thenReturn(Optional.of(dinamica));
        when(processoRepository.findByDinamica(dinamica)).thenReturn(List.of(processo));
        when(configRepository.listarTiposInstrumentoAdmitidos(20L)).thenReturn(List.of(pdm));
        when(autorizacaoService.processoVisivel(any(), any())).thenReturn(true);
    }

    @Test
    void substituiOConjuntoAssociandoOsNovosEDesassociandoOsQueSaem() {
        Instrumento a = instrumento(pdm, true);
        Instrumento b = instrumento(pdm, true);
        Instrumento c = instrumento(pdm, true);
        dinamica.substituirInstrumentos(List.of(a, b));
        encontrar(b, c);

        Dinamica resultado = service.associar(dinamica.getId(), pedido(b, c));

        assertThat(resultado.getInstrumentos()).containsExactlyInAnyOrder(b, c);
        verify(dinamicaRepository).saveAndFlush(dinamica);
        verify(auditoriaService).registar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void recusaInstrumentoForaDeVigorDeTipoNaoAdmitidoRepetidoOuInexistenteSemMudarOConjunto() {
        Instrumento emVigor = instrumento(pdm, true);
        Instrumento revogado = instrumento(pdm, false);
        Instrumento doProt = instrumento(prot, true);
        encontrar(emVigor, revogado, doProt);
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> service.associar(dinamica.getId(), new AssociarInstrumentosRequest(
                List.of(emVigor.getId(), revogado.getId(), doProt.getId(), emVigor.getId(), inexistente))))
                .isInstanceOf(AssociacaoInvalidaException.class)
                .satisfies(e -> assertThat(((AssociacaoInvalidaException) e).getCampos()).extracting(CampoErro::path)
                        .containsExactly("instrumentoIds[1]", "instrumentoIds[2]", "instrumentoIds[3]", "instrumentoIds[4]"))
                .extracting("codigo").isEqualTo("ASSOCIACAO_INVALIDA");
        assertThat(dinamica.getInstrumentos()).isEmpty();
        verify(dinamicaRepository, never()).saveAndFlush(any());
    }

    @Test
    void naDinamicaDeUmSoInstrumentoDa409() {
        config.setAssociacaoInstrumentos(AssociacaoInstrumentos.UM);

        assertThatThrownBy(() -> service.associar(dinamica.getId(), new AssociarInstrumentosRequest(List.of())))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void semProcessoEmSubmissaoDoChamadorDa409EComOProcessoDeOutroAutorDa403() {
        Processo enviado = processo(autor, EstadoProcesso.ENVIADO_DGT);
        when(processoRepository.findByDinamica(dinamica)).thenReturn(List.of(enviado));
        assertThatThrownBy(() -> service.associar(dinamica.getId(), new AssociarInstrumentosRequest(List.of())))
                .isInstanceOf(EstadoInvalidoException.class);

        Utilizador outro = new Utilizador("Outro", "outro@ccdr-lvt.pt", "INCM");
        ReflectionTestUtils.setField(outro, "id", UUID.randomUUID());
        Processo deOutro = processo(outro, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findByDinamica(dinamica)).thenReturn(List.of(deOutro));
        assertThatThrownBy(() -> service.associar(dinamica.getId(), new AssociarInstrumentosRequest(List.of())))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void perfisInternosRecebem403EForaDaVisibilidade404() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "AD_DGT",
                PerfilInterno.TECNICO, null, "DGT", null, null, null, null, null, null));
        assertThatThrownBy(() -> service.associar(dinamica.getId(), new AssociarInstrumentosRequest(List.of())))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "INCM", null,
                "500051062", "ENTIDADE", null, null, null, null, TipoEntidade.CM, 5245L));
        when(autorizacaoService.processoVisivel(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.associar(dinamica.getId(), new AssociarInstrumentosRequest(List.of())))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void podeAssociarSoOAutorEmSubmissaoNumaDinamicaQueAssocia() {
        when(processo.getDinamica()).thenReturn(dinamica);
        when(autorizacaoService.podeAgirComoAutor(contexto, processo)).thenReturn(true);
        assertThat(service.podeAssociar(contexto, processo)).isTrue();

        when(processo.getEstado()).thenReturn(EstadoProcesso.ENVIADO_DGT);
        assertThat(service.podeAssociar(contexto, processo)).isFalse();

        when(processo.getEstado()).thenReturn(EstadoProcesso.EM_SUBMISSAO);
        config.setAssociacaoInstrumentos(AssociacaoInstrumentos.UM);
        assertThat(service.podeAssociar(contexto, processo)).isFalse();
        assertThat(service.tiposAdmitidos(dinamica)).isEmpty();

        config.setAssociacaoInstrumentos(AssociacaoInstrumentos.UM_OU_MAIS);
        assertThat(service.tiposAdmitidos(dinamica)).containsExactly(pdm);
    }

    private AssociarInstrumentosRequest pedido(Instrumento... instrumentos) {
        return new AssociarInstrumentosRequest(java.util.Arrays.stream(instrumentos).map(Instrumento::getId).toList());
    }

    @SuppressWarnings("unchecked")
    private void encontrar(Instrumento... instrumentos) {
        when(instrumentoRepository.findAllById(anyCollection())).thenAnswer(invocation -> {
            Set<UUID> ids = Set.copyOf((Collection<UUID>) invocation.getArgument(0));
            return java.util.Arrays.stream(instrumentos).filter(i -> ids.contains(i.getId())).toList();
        });
    }

    private static Processo processo(Utilizador criadoPor, EstadoProcesso estado) {
        Processo processo = mock(Processo.class);
        when(processo.getCriadoPor()).thenReturn(criadoPor);
        when(processo.getEstado()).thenReturn(estado);
        return processo;
    }

    private static Instrumento instrumento(TipoInstrumento tipo, boolean emVigor) {
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipo);
        instrumento.setEmVigor(emVigor);
        return instrumento;
    }

    private static TipoInstrumento tipo(Long id, String codigo) {
        TipoInstrumento tipo = new TipoInstrumento(codigo, codigo, "PLANO");
        ReflectionTestUtils.setField(tipo, "id", id);
        return tipo;
    }
}
