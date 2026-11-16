package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcorrenciaNaoExisteAvaliadorTest {

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
    private final CatalogoTipos catalogoTipos = mock(CatalogoTipos.class);
    private final OcorrenciaNaoExisteAvaliador avaliador = new OcorrenciaNaoExisteAvaliador(dinamicaRepository,
            processoRepository, instrumentoRepository, catalogoTipos, new ObjectMapper());

    @Test
    void deveDevolverFalseParaElaboracaoQuandoOInstrumentoVeioDoSnitSemElaboracaoLocal() {
        UUID instrumentoId = UUID.randomUUID();
        when(instrumentoRepository.existsByIdAndIdIgtSnitNotNull(instrumentoId)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACAO\"}")).isFalse();
    }

    @Test
    void deveDevolverTrueParaElaboracaoQuandoOInstrumentoNaoTemElaboracaoNemVeioDoSnit() {
        UUID instrumentoId = UUID.randomUUID();

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACAO\"}")).isTrue();
    }

    @Test
    void deveDevolverFalseParaDelimitacaoQuandoOInstrumentoVeioDoSnitSemDelimitacaoLocal() {
        UUID instrumentoId = UUID.randomUUID();
        when(instrumentoRepository.existsByIdAndIdIgtSnitNotNull(instrumentoId)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"DELIMITACAO\"}")).isFalse();
    }

    @Test
    void deveDevolverTrueParaDelimitacaoQuandoOInstrumentoNaoTemDelimitacaoNemVeioDoSnit() {
        UUID instrumentoId = UUID.randomUUID();

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"DELIMITACAO\"}")).isTrue();
    }

    @Test
    void deveDevolverFalseParaDelimitacaoQuandoOInstrumentoJaTemDelimitacaoLocal() {
        UUID instrumentoId = UUID.randomUUID();
        when(dinamicaRepository.existeAbrangendoComTipo(
                instrumentoId, "DELIMITACAO")).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"DELIMITACAO\"}")).isFalse();
    }

    @Test
    void naoDeveContarAsDinamicasQueCriamOInstrumentoComoExistentesNumaCondicaoComEstado() {
        UUID instrumentoId = UUID.randomUUID();
        when(instrumentoRepository.existsByIdAndIdIgtSnitNotNull(instrumentoId)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId,
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"DELIMITACAO\",\"estado\":\"EM_CURSO\"}")).isTrue();
        assertThat(avaliador.avaliar(instrumentoId,
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACAO\",\"estado\":\"EM_CURSO\"}")).isTrue();
    }

    @Test
    void naoDeveTratarOutrasDinamicasComoJaExistentesParaUmInstrumentoDoSnit() {
        UUID instrumentoId = UUID.randomUUID();
        when(instrumentoRepository.existsByIdAndIdIgtSnitNotNull(instrumentoId)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"REVISAO\"}")).isTrue();
    }

    @Test
    void deveDevolverFalseQuandoAOcorrenciaExisteNoEstadoIndicado() {
        UUID instrumentoId = UUID.randomUUID();
        String parametros = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ALTERACAO\",\"estado\":\"EM_CURSO\"}";
        when(dinamicaRepository.existeAbrangendoComTipoEEstado(
                instrumentoId, "ALTERACAO", EstadoDinamica.EM_CURSO)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, parametros)).isFalse();
    }

    @Test
    void deveDevolverTrueQuandoAOcorrenciaNaoExisteNoEstadoIndicado() {
        UUID instrumentoId = UUID.randomUUID();
        String parametros = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ALTERACAO\",\"estado\":\"EM_CURSO\"}";
        when(dinamicaRepository.existeAbrangendoComTipoEEstado(
                instrumentoId, "ALTERACAO", EstadoDinamica.EM_CURSO)).thenReturn(false);

        assertThat(avaliador.avaliar(instrumentoId, parametros)).isTrue();
    }

    @Test
    void deveCobrirQualquerEstadoQuandoEstadoOmitido() {
        UUID instrumentoId = UUID.randomUUID();
        String parametros = "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"REVISAO\"}";
        when(dinamicaRepository.existeAbrangendoComTipo(
                instrumentoId, "REVISAO")).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, parametros)).isFalse();

        verify(dinamicaRepository, never())
                .existeAbrangendoComTipoEEstado(
                        instrumentoId, "REVISAO", null);
    }

    @Test
    void deveDespacharParaProcessoQuandoTipoOcorrenciaEProcesso() {
        UUID instrumentoId = UUID.randomUUID();
        String parametros = "{\"tipo_ocorrencia\":\"PROCESSO\",\"codigo\":\"DECISAO_INICIO\"}";
        when(processoRepository.existeProcessoAbrangendoComTipo(
                instrumentoId, "DECISAO_INICIO")).thenReturn(false);

        assertThat(avaliador.avaliar(instrumentoId, parametros)).isTrue();
    }

    @Test
    void deveAceitarEstadoOmitido() {
        when(catalogoTipos.existeTipoDinamica("REVOGACAO")).thenReturn(true);

        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"REVOGACAO\"}")).isEmpty();
    }

    @Test
    void deveAceitarEstadoNullComoOmitido() {
        when(catalogoTipos.existeTipoDinamica("REVOGACAO")).thenReturn(true);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"REVOGACAO\",\"estado\":null}")).isEmpty();
    }

    @Test
    void deveRejeitarChaveMalEscritaEmVezDeATratarComoEstadoOmitido() {
        when(catalogoTipos.existeTipoDinamica("REVISAO")).thenReturn(true);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"REVISAO\",\"estadp\":\"EM_CURSO\"}"))
                .containsExactly("chave desconhecida: estadp");
    }

    @Test
    void deveAceitarDinamicaSemCodigoComEstado() {
        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":\"DINAMICA\",\"estado\":\"EM_CURSO\"}")).isEmpty();
    }

    @Test
    void deveAceitarDinamicaSemCodigoNemEstado() {
        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":\"DINAMICA\"}")).isEmpty();
    }

    @Test
    void deveContinuarAExigirQueOCodigoIndicadoExistaEmTipoDinamica() {
        when(catalogoTipos.existeTipoDinamica("ALTERACA")).thenReturn(false);

        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ALTERACA\"}"))
                .containsExactly("codigo inexistente em tipo_dinamica: ALTERACA");
    }

    @Test
    void deveRejeitarProcessoSemCodigo() {
        assertThat(avaliador.validarParametros("{\"tipo_ocorrencia\":\"PROCESSO\",\"estado\":\"PUBLICADO\"}"))
                .containsExactly("codigo obrigatório");
    }

    @Test
    void deveDevolverFalseSemCodigoQuandoHaUmaDinamicaDeQualquerTipoNoEstadoIndicado() {
        UUID instrumentoId = UUID.randomUUID();
        when(dinamicaRepository.existeAbrangendoComEstado(instrumentoId, EstadoDinamica.EM_CURSO)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"estado\":\"EM_CURSO\"}")).isFalse();
    }

    /** Só TERMINADA ou CADUCADA: a consulta por EM_CURSO não encontra nenhuma. */
    @Test
    void deveDevolverTrueSemCodigoQuandoNaoHaDinamicasNoEstadoIndicado() {
        UUID instrumentoId = UUID.randomUUID();
        when(dinamicaRepository.existeAbrangendoComEstado(instrumentoId, EstadoDinamica.EM_CURSO)).thenReturn(false);
        when(dinamicaRepository.existeAbrangendo(instrumentoId)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\",\"estado\":\"EM_CURSO\"}")).isTrue();
        verify(dinamicaRepository).existeAbrangendoComEstado(instrumentoId, EstadoDinamica.EM_CURSO);
        verify(instrumentoRepository, never()).existsByIdAndIdIgtSnitNotNull(instrumentoId);
    }

    @Test
    void deveDevolverTrueSemCodigoNemEstadoQuandoOInstrumentoNaoTemDinamicas() {
        UUID instrumentoId = UUID.randomUUID();
        when(dinamicaRepository.existeAbrangendo(instrumentoId)).thenReturn(false);
        when(instrumentoRepository.existsByIdAndIdIgtSnitNotNull(instrumentoId)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\"}")).isTrue();
    }

    @Test
    void deveDevolverFalseSemCodigoNemEstadoQuandoOInstrumentoTemAlgumaDinamica() {
        UUID instrumentoId = UUID.randomUUID();
        when(dinamicaRepository.existeAbrangendo(instrumentoId)).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"tipo_ocorrencia\":\"DINAMICA\"}")).isFalse();
    }

    @Test
    void deveRejeitarEstadoInexistenteQuandoIndicado() {
        when(catalogoTipos.existeTipoDinamica("REVISAO")).thenReturn(true);

        assertThat(avaliador.validarParametros(
                "{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"REVISAO\",\"estado\":\"ABERTA\"}"))
                .singleElement().asString().contains("estado ABERTA não pertence a dinamica");
    }
}
