package com.conkord.saigt.service.regras;

import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.service.regras.avaliador.Avaliador;
import com.conkord.saigt.service.regras.avaliador.CatalogoTipos;
import com.conkord.saigt.service.regras.avaliador.OcorrenciaNaoExisteAvaliador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvaliadorRegistoValidatorTest {

    private final CondicaoRepository condicaoRepository = mock(CondicaoRepository.class);
    private final Avaliador avaliadorRegistado = mock(Avaliador.class);

    @Test
    void deveFalharComMensagemAIdentificarOValorQuandoAvaliadorNaoTemImplementacao() {
        when(avaliadorRegistado.codigo()).thenReturn("OCORRENCIA_EM_ESTADO");
        when(condicaoRepository.findAvaliadoresDistintos()).thenReturn(List.of("OCORRENCIA_EM_ESTADO", "TYPO_INEXISTENTE"));
        AvaliadorRegistoValidator validator = new AvaliadorRegistoValidator(condicaoRepository,
                new RegistoAvaliadores(List.of(avaliadorRegistado)));
        DefaultApplicationArguments args = new DefaultApplicationArguments();

        assertThatThrownBy(() -> validator.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TYPO_INEXISTENTE");
    }

    @Test
    void naoDeveFalharQuandoTodosOsValoresEmBdTemImplementacao() {
        when(avaliadorRegistado.codigo()).thenReturn("OCORRENCIA_EM_ESTADO");
        when(condicaoRepository.findAvaliadoresDistintos()).thenReturn(List.of("OCORRENCIA_EM_ESTADO"));
        AvaliadorRegistoValidator validator = new AvaliadorRegistoValidator(condicaoRepository,
                new RegistoAvaliadores(List.of(avaliadorRegistado)));

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void deveListarTodasAsCondicoesInvalidasNumaSoMensagem() {
        when(avaliadorRegistado.codigo()).thenReturn("OCORRENCIA_EM_ESTADO");
        when(avaliadorRegistado.validarParametros("{\"estadp\":\"EM_CURSO\"}"))
                .thenReturn(List.of("chave desconhecida: estadp", "estado obrigatório"));
        when(avaliadorRegistado.validarParametros("{")).thenReturn(List.of("JSON inválido: {"));
        when(avaliadorRegistado.validarParametros("{}")).thenReturn(List.of());
        when(condicaoRepository.findAvaliadoresDistintos()).thenReturn(List.of("OCORRENCIA_EM_ESTADO"));
        when(condicaoRepository.findByAtivoTrue()).thenReturn(List.of(
                condicao(401L, "{\"estadp\":\"EM_CURSO\"}"), condicao(402L, "{}"), condicao(403L, "{")));
        AvaliadorRegistoValidator validator = new AvaliadorRegistoValidator(condicaoRepository,
                new RegistoAvaliadores(List.of(avaliadorRegistado)));
        DefaultApplicationArguments args = new DefaultApplicationArguments();

        assertThatThrownBy(() -> validator.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("condicao.id=401 (OCORRENCIA_EM_ESTADO): chave desconhecida: estadp")
                .hasMessageContaining("condicao.id=401 (OCORRENCIA_EM_ESTADO): estado obrigatório")
                .hasMessageContaining("condicao.id=403 (OCORRENCIA_EM_ESTADO): JSON inválido: {")
                .hasMessageNotContaining("condicao.id=402");
    }

    @Test
    void naoDeveFalharQuandoOsParametrosDasCondicoesAtivasSaoValidos() {
        when(avaliadorRegistado.codigo()).thenReturn("OCORRENCIA_EM_ESTADO");
        when(avaliadorRegistado.validarParametros("{}")).thenReturn(List.of());
        when(condicaoRepository.findAvaliadoresDistintos()).thenReturn(List.of("OCORRENCIA_EM_ESTADO"));
        when(condicaoRepository.findByAtivoTrue()).thenReturn(List.of(condicao(401L, "{}")));
        AvaliadorRegistoValidator validator = new AvaliadorRegistoValidator(condicaoRepository,
                new RegistoAvaliadores(List.of(avaliadorRegistado)));

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarNoArranqueAOcorrenciaNaoExisteDeQualquerDinamicaSemCodigo() {
        when(condicaoRepository.findAvaliadoresDistintos()).thenReturn(List.of("OCORRENCIA_NAO_EXISTE"));
        when(condicaoRepository.findByAtivoTrue()).thenReturn(List.of(condicao(501L, "OCORRENCIA_NAO_EXISTE",
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"estado\":\"EM_CURSO\"}")));
        AvaliadorRegistoValidator validator = new AvaliadorRegistoValidator(condicaoRepository,
                new RegistoAvaliadores(List.of(ocorrenciaNaoExiste())));

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarNoArranqueAOcorrenciaNaoExisteDeProcessoSemCodigo() {
        when(condicaoRepository.findAvaliadoresDistintos()).thenReturn(List.of("OCORRENCIA_NAO_EXISTE"));
        when(condicaoRepository.findByAtivoTrue()).thenReturn(List.of(condicao(502L, "OCORRENCIA_NAO_EXISTE",
                "{\"tipo_ocorrencia\":\"PROCESSO\",\"estado\":\"PUBLICADO\"}")));
        AvaliadorRegistoValidator validator = new AvaliadorRegistoValidator(condicaoRepository,
                new RegistoAvaliadores(List.of(ocorrenciaNaoExiste())));
        DefaultApplicationArguments args = new DefaultApplicationArguments();

        assertThatThrownBy(() -> validator.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("condicao.id=502 (OCORRENCIA_NAO_EXISTE): codigo obrigatório");
    }

    private static OcorrenciaNaoExisteAvaliador ocorrenciaNaoExiste() {
        return new OcorrenciaNaoExisteAvaliador(mock(DinamicaRepository.class), mock(ProcessoRepository.class),
                mock(InstrumentoRepository.class), new CatalogoTipos(mock(TipoDinamicaRepository.class),
                mock(TipoProcedimentoRepository.class)), new ObjectMapper());
    }

    private static Condicao condicao(Long id, String parametros) {
        return condicao(id, "OCORRENCIA_EM_ESTADO", parametros);
    }

    private static Condicao condicao(Long id, String avaliador, String parametros) {
        Condicao condicao = new Condicao(null, avaliador, parametros);
        ReflectionTestUtils.setField(condicao, "id", id);
        return condicao;
    }

    @Test
    void naoDeveFalharQuandoNaoHaNenhumaCondicaoEmBd() {
        when(condicaoRepository.findAvaliadoresDistintos()).thenReturn(List.of());
        AvaliadorRegistoValidator validator = new AvaliadorRegistoValidator(condicaoRepository,
                new RegistoAvaliadores(List.of()));

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }
}
