package com.conkord.saigt.service.relatorio;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NomesConcelhoTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SINTRA|Sintra",
            "VILA NOVA DE GAIA|Vila Nova de Gaia",
            "MONTEMOR-O-NOVO|Montemor-o-Novo",
            "PÓVOA DE VARZIM|Póvoa de Varzim",
            "ÉVORA|Évora",
            "A DOS CUNHADOS|A dos Cunhados",
            "Lisboa|Lisboa",
            "VILA REAL DE SANTO ANTÓNIO|Vila Real de Santo António",
            "SANTA COMBA DÃO|Santa Comba Dão",
            "CALHETA (MADEIRA)|Calheta (Madeira)"
    })
    void formataComoOEcra(String doSnit, String esperado) {
        assertThat(NomesConcelho.formatar(doSnit)).isEqualTo(esperado);
    }
}
