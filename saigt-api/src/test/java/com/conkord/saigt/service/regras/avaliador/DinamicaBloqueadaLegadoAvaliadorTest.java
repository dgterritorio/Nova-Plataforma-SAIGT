package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DinamicaBloqueadaLegadoAvaliadorTest {

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final CatalogoTipos catalogoTipos = mock(CatalogoTipos.class);
    private final DinamicaBloqueadaLegadoAvaliador avaliador =
            new DinamicaBloqueadaLegadoAvaliador(dinamicaRepository, catalogoTipos, new ObjectMapper());

    @Test
    void deveDevolverTrueQuandoExisteDinamicaEspelhoAbertoHomonima() {
        UUID instrumentoId = UUID.randomUUID();
        when(dinamicaRepository.existeAbrangendoComOrigemETipo(
                instrumentoId, "ESPELHO_ABERTO", "ALTERACAO")).thenReturn(true);

        assertThat(avaliador.avaliar(instrumentoId, "{\"codigo\":\"ALTERACAO\"}")).isTrue();
    }

    @Test
    void deveDevolverFalseQuandoNaoExisteDinamicaEspelhoAbertoHomonima() {
        UUID instrumentoId = UUID.randomUUID();
        when(dinamicaRepository.existeAbrangendoComOrigemETipo(
                instrumentoId, "ESPELHO_ABERTO", "ALTERACAO")).thenReturn(false);

        assertThat(avaliador.avaliar(instrumentoId, "{\"codigo\":\"ALTERACAO\"}")).isFalse();
    }

    @Test
    void deveAceitarCodigoExistente() {
        when(catalogoTipos.existeTipoDinamica("ALTERACAO")).thenReturn(true);

        assertThat(avaliador.validarParametros("{\"codigo\":\"ALTERACAO\"}")).isEmpty();
    }

    @Test
    void deveExigirCodigo() {
        assertThat(avaliador.validarParametros("{}")).containsExactly("codigo obrigatório");
    }

    @Test
    void deveRejeitarCodigoInexistenteEChaveDesconhecida() {
        when(catalogoTipos.existeTipoDinamica("ALTERACA")).thenReturn(false);

        assertThat(avaliador.validarParametros("{\"codigo\":\"ALTERACA\",\"estado\":\"EM_CURSO\"}"))
                .containsExactlyInAnyOrder("chave desconhecida: estado", "codigo inexistente em tipo_dinamica: ALTERACA");
    }
}
