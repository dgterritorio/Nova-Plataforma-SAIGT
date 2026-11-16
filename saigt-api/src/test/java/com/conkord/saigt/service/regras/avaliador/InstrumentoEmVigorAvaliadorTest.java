package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentoEmVigorAvaliadorTest {

    private final InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
    private final InstrumentoEmVigorAvaliador avaliador =
            new InstrumentoEmVigorAvaliador(instrumentoRepository, new ObjectMapper());

    @Test
    void codigoDeveSerInstrumentoEmVigor() {
        assertThat(avaliador.codigo()).isEqualTo("INSTRUMENTO_EM_VIGOR");
    }

    @Test
    void devePassarQuandoAColunaEmVigorEVerdadeira() {
        UUID instrumentoId = UUID.randomUUID();
        Instrumento instrumento = new Instrumento();
        instrumento.setEmVigor(true);
        when(instrumentoRepository.findById(instrumentoId)).thenReturn(Optional.of(instrumento));

        assertThat(avaliador.avaliar(instrumentoId, "{}")).isTrue();
    }

    @Test
    void deveFalharQuandoAColunaEmVigorEFalsa() {
        UUID instrumentoId = UUID.randomUUID();
        when(instrumentoRepository.findById(instrumentoId)).thenReturn(Optional.of(new Instrumento()));

        assertThat(avaliador.avaliar(instrumentoId, "{}")).isFalse();
    }

    @Test
    void deveFalharParaInstrumentoInexistente() {
        UUID instrumentoId = UUID.randomUUID();
        when(instrumentoRepository.findById(instrumentoId)).thenReturn(Optional.empty());

        assertThat(avaliador.avaliar(instrumentoId, "{}")).isFalse();
    }

    @Test
    void deveAceitarParametrosVazios() {
        assertThat(avaliador.validarParametros("{}")).isEmpty();
    }

    @Test
    void deveRejeitarQualquerParametro() {
        assertThat(avaliador.validarParametros("{\"codigo\":\"ELABORACAO\"}"))
                .containsExactly("chave desconhecida: codigo");
    }

    @Test
    void deveRejeitarJsonInvalido() {
        assertThat(avaliador.validarParametros("{")).singleElement().asString().startsWith("JSON inválido");
    }
}
