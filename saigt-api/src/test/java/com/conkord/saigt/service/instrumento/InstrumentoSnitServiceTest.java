package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.instrumento.IgtSnitResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.SnitIndisponivelException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.IgtSnit;
import com.conkord.saigt.integracao.snit.PaginaSnit;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstrumentoSnitServiceTest {

    private final ClienteSnit clienteSnit = mock(ClienteSnit.class);
    private final TipoInstrumentoRepository tipoInstrumentoRepository = mock(TipoInstrumentoRepository.class);
    private final InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);

    private final InstrumentoSnitService servico = new InstrumentoSnitService(clienteSnit, tipoInstrumentoRepository,
            instrumentoRepository);

    private TipoInstrumento pdm;
    private ContextoUtilizador contexto;

    @BeforeEach
    void iniciar() {
        pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ReflectionTestUtils.setField(pdm, "id", 1L);
        contexto = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, "501234567", "ENTIDADE", null, null, null, null, TipoEntidade.CM, null);
        when(tipoInstrumentoRepository.findById(1L)).thenReturn(Optional.of(pdm));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
    }

    private static IgtSnit igt(long id, String tipo, String designacao, String estado) {
        return new IgtSnit(id, tipo, designacao, estado);
    }

    private Instrumento instrumentoDoIgt(String idIgtSnit) {
        Instrumento instrumento = new Instrumento(pdm, "1", "PDM",
                new Utilizador("Ana", "ana@cm.pt"), new Utilizador("Ana", "ana@cm.pt"));
        instrumento.setIdIgtSnit(idIgtSnit);
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        return instrumento;
    }

    @Test
    void devePesquisarComOCodigoDoTipoEAPaginaDoSnitAPartirDeUm() {
        when(clienteSnit.pesquisar("PDM", "1111", 3, 20))
                .thenReturn(new PaginaSnit(List.of(igt(1001, "PDM", "PDM de Sintra (SNIT)", "in_force")), 41, 3, 20));

        PageResponse<IgtSnitResponse> pagina = servico.pesquisar(1L, "1111", 2, 20);

        assertThat(pagina.page()).isEqualTo(2);
        assertThat(pagina.size()).isEqualTo(20);
        assertThat(pagina.totalElements()).isEqualTo(41);
        assertThat(pagina.totalPages()).isEqualTo(3);
        assertThat(pagina.content()).containsExactly(new IgtSnitResponse("1001", "PDM de Sintra (SNIT)", "PDM", null));
    }

    @Test
    void deveIndicarOInstrumentoDeQualquerEntidadeQuandoOIgtJaExiste() {
        Instrumento visivel = instrumentoDoIgt("1001");
        Instrumento invisivel = instrumentoDoIgt("1002");
        when(clienteSnit.pesquisar("PDM", null, 1, 20)).thenReturn(new PaginaSnit(List.of(
                igt(1001, "PDM", "A", "in_force"), igt(1002, "PDM", "B", "in_force")), 2, 1, 20));
        when(instrumentoRepository.findAllByIdIgtSnitIn(List.of("1001", "1002"))).thenReturn(List.of(visivel, invisivel));

        PageResponse<IgtSnitResponse> pagina = servico.pesquisar(1L, null, 0, 20);

        assertThat(pagina.content()).extracting(IgtSnitResponse::instrumentoId)
                .containsExactly(visivel.getId(), invisivel.getId());
    }

    @Test
    void naoDeveConsultarAsInstrumentosLocaisQuandoOSnitNaoDevolveNada() {
        when(clienteSnit.pesquisar("PDM", null, 1, 20)).thenReturn(new PaginaSnit(List.of(), 0, 1, 20));

        PageResponse<IgtSnitResponse> pagina = servico.pesquisar(1L, null, 0, 20);

        assertThat(pagina.content()).isEmpty();
        assertThat(pagina.totalPages()).isZero();
        verifyNoInteractions(instrumentoRepository);
    }

    @Test
    void deveIgnorarItensDoSnitSemIdentificador() {
        when(clienteSnit.pesquisar("PDM", null, 1, 20)).thenReturn(new PaginaSnit(List.of(
                igt(1001, "PDM", "A", "in_force"), new IgtSnit(null, "PDM", "Sem id", "in_force")), 2, 1, 20));

        PageResponse<IgtSnitResponse> pagina = servico.pesquisar(1L, null, 0, 20);

        assertThat(pagina.content()).extracting(IgtSnitResponse::idIgtSnit).containsExactly("1001");
    }

    @Test
    void deveDevolverNaoEncontradoNaPesquisaComTipoInexistente() {
        assertThatThrownBy(() -> servico.pesquisar(99L, null, 0, 20)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(clienteSnit);
    }

    @Test
    void deveAdotarIgtEmVigorDoTipoPedido() {
        IgtSnit emVigor = igt(1001, "PDM", "PDM de Sintra (SNIT)", "in_force");
        when(clienteSnit.obter(1001L)).thenReturn(Optional.of(emVigor));

        assertThat(servico.obterParaAdocao("1001", pdm)).isEqualTo(emVigor);
    }

    @Test
    void deveRecusarIgtQueJaExisteNaSaigtSemConsultarOSnit() {
        when(instrumentoRepository.existsByIdIgtSnit("1001")).thenReturn(true);

        assertThatThrownBy(() -> servico.obterParaAdocao("1001", pdm)).isInstanceOf(ReferenciaDuplicadaException.class);
        verify(clienteSnit, never()).obter(anyLong());
    }

    @Test
    void deveRecusarIgtQueOSnitNaoConhece() {
        when(clienteSnit.obter(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.obterParaAdocao("1001", pdm)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveRecusarIgtQueNaoEstaEmVigor() {
        when(clienteSnit.obter(1001L)).thenReturn(Optional.of(igt(1001, "PDM", "PDM revogado", "revoked")));

        assertThatThrownBy(() -> servico.obterParaAdocao("1001", pdm))
                .isInstanceOfSatisfying(ValidacaoException.class,
                        e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("idIgtSnit"));
    }

    @Test
    void deveRecusarIgtDeOutroTipoDeInstrumento() {
        when(clienteSnit.obter(1001L)).thenReturn(Optional.of(igt(1001, "PU", "PU da Zona Industrial", "in_force")));

        assertThatThrownBy(() -> servico.obterParaAdocao("1001", pdm))
                .isInstanceOfSatisfying(ValidacaoException.class,
                        e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("tipoInstrumentoId"));
    }

    @Test
    void deveTratarIgtSemDesignacaoComoRespostaInvalidaDoSnit() {
        when(clienteSnit.obter(1001L)).thenReturn(Optional.of(igt(1001, "PDM", " ", "in_force")));

        assertThatThrownBy(() -> servico.obterParaAdocao("1001", pdm)).isInstanceOf(SnitIndisponivelException.class);
    }

    @Test
    void deveCortarADesignacaoDoSnitAos255CaracteresDaColuna() {
        String longa = "PDM de um concelho com um nome muito comprido: " + "x".repeat(250);

        assertThat(InstrumentoSnitService.designacaoLocal(igt(1, "PDM", " " + longa + " ", "in_force")))
                .hasSize(255).isEqualTo(longa.substring(0, 255));
    }

    @Test
    void naoDeveCortarUmaDesignacaoComMaisDe50CaracteresQueAindaCabeNaColuna() {
        String designacao = "Plano de Urbanização da Zona Industrial de Sintra e Arredores (SNIT)";

        assertThat(designacao).hasSizeGreaterThan(50);
        assertThat(InstrumentoSnitService.designacaoLocal(igt(1, "PU", designacao, "in_force"))).isEqualTo(designacao);
    }

    @Test
    void naoDeveCortarUmaDesignacaoQueCabeNaColuna() {
        assertThat(InstrumentoSnitService.designacaoLocal(igt(1, "PDM", "PDM de Sintra", "in_force")))
                .isEqualTo("PDM de Sintra");
    }
}
