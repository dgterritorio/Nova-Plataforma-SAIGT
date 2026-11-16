package com.conkord.saigt.service.incm;

import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.integracao.SimuladorWireMockTeste;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.Processo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Envio e consulta com o {@link com.conkord.saigt.integracao.incm.ClienteIncm} real contra o WireMock do compose, com
 * os mesmos mappings. O cenário `ato-publicado` do mock é global e o sucesso devolve sempre o mesmo ActIdentifier: só
 * um Processo enviado de cada vez, com o mock reposto antes de cada teste.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("com.conkord.saigt.integracao.SimuladorWireMockTeste#incmDisponivel")
@SpringBootTest(properties = {"app.incm.service-key=chave-de-teste", "app.incm.read-timeout=2s"})
@ActiveProfiles("test")
class FluxoPublicacaoIncmIntegrationTest extends EnvioIncmTesteBase {

    @Autowired
    private ConsultaIncmService consultaIncmService;

    @Autowired
    private ConsultaIncmJob consultaIncmJob;

    @DynamicPropertySource
    static void urlIncm(DynamicPropertyRegistry registry) {
        registry.add("app.incm.base-url", SimuladorWireMockTeste.INCM::url);
    }

    @BeforeEach
    void reporCenarios() {
        SimuladorWireMockTeste.INCM.reporCenarios();
    }

    @Test
    void deveFicarPublicadoAposEnvioEDuasConsultas() {
        Cenario cenario = criarCenario("Aviso de início do procedimento");
        Processo processo = cenario.processo();

        enviar(cenario);
        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(recarregar(processo).getIncmActIdentifier()).isEqualTo("900000000000001");

        assertThat(consultaIncmService.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);
        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);

        // Outro Processo consultável gastaria a sequência Aceite → Publicado do mock antes deste.
        assertThat(processoRepository.findIdsParaConsultaIncm()).containsExactly(processo.getId());
        consultaIncmJob.executar();

        Processo publicado = recarregar(processo);
        assertThat(publicado.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(publicado.getNumeroDr()).isEqualTo("DR-2026-000001");
        assertThat(publicado.getDataPublicacaoDr()).isEqualTo(LocalDate.now(ZoneId.of("Europe/Lisbon")));
        assertThat(publicado.getLinkDr()).isEqualTo("https://exemplo.pt/dr/2026/000001");

        List<IncmComunicacao> linhas = comunicacoes(processo);
        assertThat(linhas).extracting(IncmComunicacao::getOperacao)
                .containsExactly("SUBMIT_NEW_ACT", "GET_ACT_DETAILS", "GET_ACT_DETAILS");
        assertThat(linhas).extracting(IncmComunicacao::getActIdentifier).containsOnly(900000000000001L);
        assertThat(linhas).extracting(IncmComunicacao::getTransicaoDerivada)
                .containsExactly("EM_VALIDACAO_INCM", null, "PUBLICADO");
    }

    @Test
    void deveGravarADataDeCaducidadeAoPublicarADecisaoDeInicio() {
        Cenario cenario = criarCenario("SEGUNDA_H", "Aviso de início do procedimento", "DECISAO_INICIO",
                "{\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":2,\"prazoMeses\":3,\"prazoDias\":10,"
                        + "\"avaliacaoAmbiental\":false}}}");

        publicar(cenario);

        LocalDate dataPublicacao = recarregar(cenario.processo()).getDataPublicacaoDr();
        assertThat(dataCaducidadeGravada(cenario.processo()))
                .isEqualTo(dataPublicacao.plusYears(2).plusMonths(3).plusDays(10));
    }

    @Test
    void naoDeveMudarADataDeCaducidadeAoPublicarOutroProcedimento() {
        Cenario cenario = criarCenario("SEGUNDA_H", "Aviso de discussão pública", "ABERTURA_DISCUSSAO_PUBLICA",
                "{\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":1,\"prazoMeses\":0,\"prazoDias\":0,"
                        + "\"avaliacaoAmbiental\":false}}}");
        gravarDataCaducidade(cenario.processo(), LocalDate.of(2028, 5, 1));

        publicar(cenario);

        assertThat(dataCaducidadeGravada(cenario.processo())).isEqualTo(LocalDate.of(2028, 5, 1));
    }

    /** Envio e duas consultas: o mock responde Aceite e depois Publicado. */
    private void publicar(Cenario cenario) {
        enviar(cenario);
        consultaIncmService.consultar(cenario.processo().getId());
        consultaIncmService.consultar(cenario.processo().getId());
        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
    }

    @Test
    void deveFicarAnuladoIncmComMockRejeitarNaConsulta() {
        Cenario cenario = criarCenario("[MOCK:REJEITAR] Aviso de início");
        Processo processo = cenario.processo();

        enviar(cenario);
        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(recarregar(processo).getIncmActIdentifier()).isEqualTo("900000000000002");

        assertThat(consultaIncmService.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.ANULADO_INCM);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(comunicacoes(processo)).extracting(IncmComunicacao::getProcessingStatus)
                .containsExactly(null, "SubmissionRejected");
    }

    @Test
    void deveFicarAnuladoIncmSemActIdentifierComMockErro() {
        Cenario cenario = criarCenario("[MOCK:ERRO] Aviso de início");

        enviar(cenario);

        Processo processo = recarregar(cenario.processo());
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(processo.getIncmActIdentifier()).isNull();
        assertThat(comunicacoes(processo)).singleElement()
                .satisfies(linha -> assertThat(linha.getActIdentifier()).isNull());
    }

    @Test
    void deveManterValidadoDgtComMockHttp500() {
        Cenario cenario = criarCenario("[MOCK:HTTP500] Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class)
                .extracting("codigo").isEqualTo("INTEGRACAO_INDISPONIVEL");

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo("ERRO_HTTP");
        assertThat(linha.getHttpStatus()).isEqualTo(500);
    }

    @Test
    void deveManterValidadoDgtComMockTimeout() {
        Cenario cenario = criarCenario("[MOCK:TIMEOUT] Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class)
                .extracting("codigo").isEqualTo("INTEGRACAO_INDISPONIVEL");

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo("TIMEOUT");
        assertThat(linha.getHttpStatus()).isNull();
    }
}
