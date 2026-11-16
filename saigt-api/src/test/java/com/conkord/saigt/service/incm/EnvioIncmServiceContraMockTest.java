package com.conkord.saigt.service.incm;

import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.integracao.SimuladorWireMockTeste;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIf("com.conkord.saigt.integracao.SimuladorWireMockTeste#incmDisponivel")
@SpringBootTest(properties = {"app.incm.service-key=chave-de-teste", "app.incm.read-timeout=2s"})
@ActiveProfiles("test")
class EnvioIncmServiceContraMockTest extends EnvioIncmTesteBase {

    @DynamicPropertySource
    static void urlIncm(DynamicPropertyRegistry registry) {
        registry.add("app.incm.base-url", SimuladorWireMockTeste.INCM::url);
    }

    @Test
    void deveFicarEmValidacaoIncmSemMarca() {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(recarregar(cenario.processo()).getIncmActIdentifier()).isEqualTo("900000000000001");
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getActIdentifier()).isEqualTo(900000000000001L);
        assertThat(linha.getTransicaoDerivada()).isEqualTo("EM_VALIDACAO_INCM");
    }

    @Test
    void deveFicarAnuladoIncmComMockErro() {
        Cenario cenario = criarCenario("[MOCK:ERRO] Aviso de início");

        enviar(cenario);

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getActIdentifier()).isNull();
        assertThat(linha.getResposta()).contains("ImageURLList cannot be empty.");
        assertThat(linha.getTransicaoDerivada()).isEqualTo("ANULADO_INCM");
    }

    @Test
    void deveManterValidadoDgtComMockHttp500() {
        Cenario cenario = criarCenario("[MOCK:HTTP500] Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class);

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(linksAtivos(cenario.publicacao())).hasSize(1);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo("ERRO_HTTP");
        assertThat(linha.getHttpStatus()).isEqualTo(500);
    }

    @Test
    void deveManterValidadoDgtComMockTimeout() {
        Cenario cenario = criarCenario("[MOCK:TIMEOUT] Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class);

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo("TIMEOUT");
        assertThat(linha.getRecebidoEm()).isNull();
    }

    @Test
    void deveManterValidadoDgtComOCircuitoAberto() {
        circuitBreakerRegistry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO).transitionToForcedOpenState();
        Cenario cenario = criarCenario("Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class);

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(comunicacoes(cenario.processo()).get(0).getFalhaTecnica()).isEqualTo("CIRCUITO_ABERTO");
    }
}
