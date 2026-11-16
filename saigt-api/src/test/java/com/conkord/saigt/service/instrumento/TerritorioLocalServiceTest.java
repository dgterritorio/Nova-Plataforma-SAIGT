package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoEntidade;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TerritorioLocalServiceTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Município de Sintra|Sintra",
            "Município da Amadora|Amadora",
            "Município do Porto|Porto",
            "Município das Lajes das Flores|Lajes das Flores",
            "Câmara Municipal de Sintra|Sintra",
            "Município de Calheta-Madeira|Calheta-Madeira",
    })
    void deveTirarOPrefixoDaDesignacaoSaigt(String designacaoSaigt, String concelho) {
        Entidade camara = new Entidade("5999", TipoEntidade.CM, "509999999", "Outro nome da INCM", designacaoSaigt);

        assertThat(TerritorioLocalService.nomeConcelho(camara)).isEqualTo(concelho);
    }
}
