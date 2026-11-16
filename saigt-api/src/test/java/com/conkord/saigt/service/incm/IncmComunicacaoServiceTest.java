package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.incm.FalhaTecnicaIncm;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.IncmComunicacaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contexto completo: o `REQUIRES_NEW` só se prova com o proxy transacional do Spring e uma transação externa. */
@SpringBootTest(properties = "app.incm.service-key=" + IncmComunicacaoServiceTest.SERVICE_KEY)
@ActiveProfiles("test")
class IncmComunicacaoServiceTest {

    static final String SERVICE_KEY = "chave-secreta-da-incm";

    private static final Instant ENVIO = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired
    private IncmComunicacaoService service;

    @Autowired
    private IncmComunicacaoRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void limpar() {
        repository.deleteAll();
        MDC.clear();
    }

    @Test
    void deveManterALinhaQuandoATransacaoDoChamadorFazRollback() {
        UUID processoId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(estado -> {
            service.registar(envio(processoId, 1, null, FalhaTecnicaIncm.TIMEOUT));
            estado.setRollbackOnly();
        });

        assertThat(repository.findByProcessoIdOrderByEnviadoEmAsc(processoId)).hasSize(1);
    }

    @Test
    void deveDeixarOsCamposDaRespostaVaziosNumTimeout() {
        UUID processoId = UUID.randomUUID();

        service.registar(envio(processoId, 1, "corr-timeout", FalhaTecnicaIncm.TIMEOUT));

        IncmComunicacao gravada = repository.findByProcessoIdOrderByEnviadoEmAsc(processoId).get(0);
        assertThat(gravada.getFalhaTecnica()).isEqualTo("TIMEOUT");
        assertThat(gravada.getResposta()).isNull();
        assertThat(gravada.getHttpStatus()).isNull();
        assertThat(gravada.getIsSuccess()).isNull();
        assertThat(gravada.getRecebidoEm()).isNull();
        assertThat(gravada.getOperacao()).isEqualTo("SUBMIT_NEW_ACT");
        assertThat(gravada.getCorrelationId()).isEqualTo("corr-timeout");
    }

    @Test
    void deveGravarOsNomesDosEnumsEARespostaQuandoHaResposta() {
        UUID processoId = UUID.randomUUID();
        Instant recebidoEm = ENVIO.plusSeconds(2);

        service.registar(new RegistoComunicacaoIncm(processoId, OperacaoIncm.GET_ACT_DETAILS, 123L, 1, null,
                Map.of("ActIdentifier", 123), Map.of("IsSuccess", true), 200, true, "Processed", "Published", null,
                EstadoProcesso.PUBLICADO, "corr-consulta", ENVIO, recebidoEm));

        IncmComunicacao gravada = repository.findByProcessoIdOrderByEnviadoEmAsc(processoId).get(0);
        assertThat(gravada.getOperacao()).isEqualTo("GET_ACT_DETAILS");
        assertThat(gravada.getTransicaoDerivada()).isEqualTo("PUBLICADO");
        assertThat(gravada.getResposta()).contains("\"IsSuccess\":true");
        assertThat(gravada.getActIdentifier()).isEqualTo(123L);
        assertThat(gravada.getRecebidoEm()).isEqualTo(recebidoEm);
    }

    @Test
    void deveDevolverUmEUmParaUmProcessoSemLinhas() {
        UUID processoId = UUID.randomUUID();

        assertThat(service.submissaoParaNovoEnvio(processoId)).isEqualTo(1);
        assertThat(service.submissaoCorrente(processoId)).isEqualTo(1);
    }

    @Test
    void deveDevolverDoisParaNovoEnvioEUmCorrenteDepoisDeUmaSubmissao() {
        UUID processoId = UUID.randomUUID();
        service.registar(envio(processoId, 1, null, null));
        service.registar(new RegistoComunicacaoIncm(processoId, OperacaoIncm.GET_ACT_DETAILS, 9L, 1, null,
                Map.of("ActIdentifier", 9), null, null, null, null, null, FalhaTecnicaIncm.ERRO_REDE, null, null,
                ENVIO.plusSeconds(60), null));

        assertThat(service.submissaoParaNovoEnvio(processoId)).isEqualTo(2);
        assertThat(service.submissaoCorrente(processoId)).isEqualTo(1);
    }

    @Test
    void naoDeveGravarOServiceKeyNoPedido() {
        UUID processoId = UUID.randomUUID();

        service.registar(envio(processoId, 1, null, null));

        String pedido = repository.findByProcessoIdOrderByEnviadoEmAsc(processoId).get(0).getPedido();
        assertThat(pedido).contains("\"ActTitle\"").doesNotContain(SERVICE_KEY);
    }

    @Test
    void deveUsarOTraceIdDoPedidoHttpQuandoORegistoNaoTrazCorrelationId() {
        UUID processoId = UUID.randomUUID();
        MDC.put("apiTraceId", "trace-do-pedido");

        service.registar(envio(processoId, 1, null, null));

        assertThat(repository.findByProcessoIdOrderByEnviadoEmAsc(processoId).get(0).getCorrelationId())
                .isEqualTo("trace-do-pedido");
    }

    @Test
    void deveGerarUmCorrelationIdQuandoNaoHaNenhum() {
        UUID processoId = UUID.randomUUID();

        service.registar(envio(processoId, 1, null, null));

        List<IncmComunicacao> linhas = repository.findByProcessoIdOrderByEnviadoEmAsc(processoId);
        assertThat(UUID.fromString(linhas.get(0).getCorrelationId())).isNotNull();
    }

    private static RegistoComunicacaoIncm envio(UUID processoId, int submissao, String correlationId,
                                                FalhaTecnicaIncm falhaTecnica) {
        return new RegistoComunicacaoIncm(processoId, OperacaoIncm.SUBMIT_NEW_ACT, null, submissao, "SEGUNDA_H",
                Map.of("ActTitle", "Aviso de alteração do PDM"), null, null, null, null, null, falhaTecnica, null,
                correlationId, ENVIO, null);
    }
}
