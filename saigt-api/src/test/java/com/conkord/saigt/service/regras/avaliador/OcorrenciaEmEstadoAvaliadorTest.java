package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcorrenciaEmEstadoAvaliadorTest {

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final CatalogoTipos catalogoTipos = mock(CatalogoTipos.class);
    private final OcorrenciaEmEstadoAvaliador avaliador =
            new OcorrenciaEmEstadoAvaliador(dinamicaRepository, processoRepository, catalogoTipos, new ObjectMapper());

    @Test
    void codigoDeveSerOcorrenciaEmEstado() {
        assertThat(avaliador.codigo()).isEqualTo("OCORRENCIA_EM_ESTADO");
    }

    @Test
    void deveConsultarDinamicaQuandoTipoOcorrenciaEDinamica() {
        UUID instrumentoId = UUID.randomUUID();
        String parametros = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACAO\",\"estado\":\"TERMINADA\"}";
        when(dinamicaRepository.existeAbrangendoComTipoEEstado(
                instrumentoId, "ELABORACAO", EstadoDinamica.TERMINADA)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, parametros)).isTrue();
    }

    @Test
    void deveConsultarProcessoQuandoTipoOcorrenciaEProcesso() {
        UUID instrumentoId = UUID.randomUUID();
        String parametros = "{\"tipo_ocorrencia\":\"PROCESSO\",\"codigo\":\"DECISAO_INICIO\",\"estado\":\"PUBLICADO\"}";
        when(processoRepository.existeProcessoAbrangendoComTipoEEstado(
                instrumentoId, "DECISAO_INICIO", EstadoProcesso.PUBLICADO)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, parametros)).isTrue();

        verify(processoRepository).existeProcessoAbrangendoComTipoEEstado(
                instrumentoId, "DECISAO_INICIO", EstadoProcesso.PUBLICADO);
    }

    @Test
    void deveDevolverFalseQuandoNaoExisteAOcorrencia() {
        UUID instrumentoId = UUID.randomUUID();
        String parametros = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ALTERACAO\",\"estado\":\"EM_CURSO\"}";
        when(dinamicaRepository.existeAbrangendoComTipoEEstado(
                instrumentoId, "ALTERACAO", EstadoDinamica.EM_CURSO)).thenReturn(false);

        assertThat(avaliador.avaliar(instrumentoId, parametros)).isFalse();
    }

    @Test
    void deveAceitarParametrosCompletosDeDinamica() {
        when(catalogoTipos.existeTipoDinamica("ELABORACAO")).thenReturn(true);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACAO\",\"estado\":\"TERMINADA\"}")).isEmpty();
    }

    @Test
    void deveAceitarParametrosCompletosDeProcesso() {
        when(catalogoTipos.existeTipoProcedimento("PUBLICACAO_PLANO")).thenReturn(true);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"PROCESSO\",\"codigo\":\"PUBLICACAO_PLANO\",\"estado\":\"PUBLICADO\"}")).isEmpty();
    }

    @Test
    void deveRejeitarJsonInvalido() {
        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":"))
                .singleElement().asString().startsWith("JSON inválido");
    }

    @Test
    void deveRejeitarJsonQueNaoEObjeto() {
        assertThat(avaliador.validarParametros("[]"))
                .singleElement().asString().contains("não é um objeto JSON");
    }

    @Test
    void deveRejeitarEstadoQueNaoPertenceAMaquinaDeEstadosDaDinamica() {
        when(catalogoTipos.existeTipoDinamica("SUSPENSAO")).thenReturn(true);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"SUSPENSAO\",\"estado\":\"LEVANTADA\"}"))
                .singleElement().asString().contains("estado LEVANTADA não pertence a dinamica");
    }

    @Test
    void deveRejeitarEstadoDeDinamicaNumProcesso() {
        when(catalogoTipos.existeTipoProcedimento("PUBLICACAO_PLANO")).thenReturn(true);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"PROCESSO\",\"codigo\":\"PUBLICACAO_PLANO\",\"estado\":\"EM_CURSO\"}"))
                .singleElement().asString().contains("estado EM_CURSO não pertence a processo");
    }

    @Test
    void deveRejeitarCodigoInexistenteEmTipoDinamica() {
        when(catalogoTipos.existeTipoDinamica("ELABORACA")).thenReturn(false);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACA\",\"estado\":\"TERMINADA\"}"))
                .containsExactly("codigo inexistente em tipo_dinamica: ELABORACA");
    }

    @Test
    void deveRejeitarCodigoInexistenteEmTipoProcedimento() {
        when(catalogoTipos.existeTipoProcedimento("NAO_EXISTE")).thenReturn(false);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"PROCESSO\",\"codigo\":\"NAO_EXISTE\",\"estado\":\"PUBLICADO\"}"))
                .containsExactly("codigo inexistente em tipo_procedimento: NAO_EXISTE");
    }

    @Test
    void deveExigirEstado() {
        when(catalogoTipos.existeTipoDinamica("ELABORACAO")).thenReturn(true);

        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACAO\"}"))
                .containsExactly("estado obrigatório");
    }

    @Test
    void deveContinuarAExigirCodigoNumaDinamica() {
        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":\"DINAMICA\",\"estado\":\"EM_CURSO\"}"))
                .containsExactly("codigo obrigatório");
    }

    @Test
    void deveRejeitarTipoOcorrenciaDesconhecido() {
        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"INSTRUMENTO\",\"codigo\":\"ELABORACAO\",\"estado\":\"TERMINADA\"}"))
                .singleElement().asString().startsWith("tipo_ocorrencia tem de ser DINAMICA ou PROCESSO");
    }

    @Test
    void deveListarTodosOsMotivosDaMesmaCondicao() {
        assertThat(avaliador.validarParametros("{\"tipo\":\"DINAMICA\",\"codigo\":42}"))
                .containsExactlyInAnyOrder("chave desconhecida: tipo", "tipo_ocorrencia obrigatório",
                        "codigo tem de ser texto não vazio: 42", "estado obrigatório");
    }
}
