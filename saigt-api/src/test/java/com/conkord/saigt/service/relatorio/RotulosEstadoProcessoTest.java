package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RotulosEstadoProcessoTest {

    @Test
    void todosOsEstadosTemORotuloDoEcra() {
        for (EstadoProcesso estado : EstadoProcesso.values()) {
            assertThat(RotulosEstadoProcesso.rotulo(estado.name())).as(estado.name()).isNotEqualTo(estado.name());
        }
        assertThat(RotulosEstadoProcesso.rotulo("EM_VALIDACAO_DGT")).isEqualTo("Em Validação DGT");
    }

    @Test
    void umEstadoDesconhecidoSaiTalComoVem() {
        assertThat(RotulosEstadoProcesso.rotulo("XPTO")).isEqualTo("XPTO");
    }
}
