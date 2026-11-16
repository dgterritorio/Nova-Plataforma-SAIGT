package com.conkord.saigt.service.regras;

import com.conkord.saigt.service.regras.avaliador.Avaliador;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistoAvaliadoresTest {

    private static Avaliador avaliador(String codigo) {
        Avaliador avaliador = mock(Avaliador.class);
        when(avaliador.codigo()).thenReturn(codigo);
        return avaliador;
    }

    @Test
    void encontraCadaAvaliadorPeloCodigoEListaOsCodigos() {
        Avaliador emVigor = avaliador("INSTRUMENTO_EM_VIGOR");
        Avaliador naoExiste = avaliador("OCORRENCIA_NAO_EXISTE");

        RegistoAvaliadores registo = new RegistoAvaliadores(List.of(emVigor, naoExiste));

        assertThat(registo.porCodigo("INSTRUMENTO_EM_VIGOR")).containsSame(emVigor);
        assertThat(registo.porCodigo("OCORRENCIA_NAO_EXISTE")).containsSame(naoExiste);
        assertThat(registo.codigos()).containsExactlyInAnyOrder("INSTRUMENTO_EM_VIGOR", "OCORRENCIA_NAO_EXISTE");
    }

    @Test
    void umCodigoDesconhecidoOuNuloNaoTemAvaliador() {
        RegistoAvaliadores registo = new RegistoAvaliadores(List.of(avaliador("INSTRUMENTO_EM_VIGOR")));

        assertThat(registo.porCodigo("QUALQUER")).isEmpty();
        assertThat(registo.porCodigo(null)).isEmpty();
    }

    @Test
    void doisAvaliadoresComOMesmoCodigoFalhamOArranque() {
        List<Avaliador> repetidos = List.of(avaliador("INSTRUMENTO_EM_VIGOR"), avaliador("INSTRUMENTO_EM_VIGOR"));

        assertThatThrownBy(() -> new RegistoAvaliadores(repetidos)).isInstanceOf(IllegalStateException.class);
    }
}
