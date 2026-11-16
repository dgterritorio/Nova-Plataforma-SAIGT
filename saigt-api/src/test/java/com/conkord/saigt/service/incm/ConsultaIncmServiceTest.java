package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.SimuladorWireMockTeste;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Contra o `incm-mock` real (WireMock): os cenários são globais ao contentor, por isso repõem-se a cada teste. O job
 * consulta todos os Processos consultáveis da BD partilhada, por isso as verificações ficam presas ao Processo do teste.
 */
@Tag("docker")
@EnabledIf("com.conkord.saigt.integracao.SimuladorWireMockTeste#incmDisponivel")
@SpringBootTest(properties = {"app.incm.service-key=chave-de-teste", "app.incm.consulta.cron=-"})
@ExtendWith(OutputCaptureExtension.class)
class ConsultaIncmServiceTest extends ConsultaIncmServiceTestBase {

    private static final String ATO_PUBLICADO = "900000000000001";
    private static final String ATO_REJEITADO = "900000000000002";
    private static final String ATO_ANULADO_PAGAMENTO = "900000000000003";
    private static final String MENSAGEM_ANULACAO_PAGAMENTO =
            "[MOCK] Ato anulado por falta de pagamento (mensagem fictícia).";

    @Autowired
    private ConsultaIncmService service;

    @Autowired
    private ConsultaIncmJob job;

    @Autowired
    private ProcessoRejeicaoRepository rejeicaoRepository;

    @DynamicPropertySource
    static void urlIncm(DynamicPropertyRegistry registry) {
        registry.add("app.incm.base-url", SimuladorWireMockTeste.INCM::url);
    }

    @BeforeEach
    void reporCenarios() {
        SimuladorWireMockTeste.INCM.reporCenarios();
    }

    @Test
    void deveDevolverSemAlteracaoNaPrimeiraConsultaEPublicadoNaSegunda() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ATO_PUBLICADO);
        long versaoInicial = processo.getVersao();

        DesfechoConsultaIncm primeira = service.consultar(processo.getId());

        assertThat(primeira).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);
        Processo inalterado = recarregar(processo);
        assertThat(inalterado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(inalterado.getVersao()).isEqualTo(versaoInicial);
        assertThat(inalterado.getNumeroDr()).isNull();
        List<IncmComunicacao> aposPrimeira = comunicacoes(processo);
        assertThat(aposPrimeira).hasSize(1);
        assertThat(aposPrimeira.get(0).getOperacao()).isEqualTo("GET_ACT_DETAILS");
        assertThat(aposPrimeira.get(0).getProcessingStatus()).isEqualTo("SubmissionAccepted");
        assertThat(aposPrimeira.get(0).getActStatus()).isEqualTo("Em edição");
        assertThat(aposPrimeira.get(0).getTransicaoDerivada()).isNull();

        LocalDate hojeEmLisboa = LocalDate.now(ZoneId.of("Europe/Lisbon"));
        DesfechoConsultaIncm segunda = service.consultar(processo.getId());

        assertThat(segunda).isEqualTo(DesfechoConsultaIncm.PUBLICADO);
        Processo publicado = recarregar(processo);
        assertThat(publicado.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(publicado.getNumeroDr()).isEqualTo("DR-2026-000001");
        assertThat(publicado.getDataPublicacaoDr()).isEqualTo(hojeEmLisboa);
        assertThat(publicado.getLinkDr()).isEqualTo("https://exemplo.pt/dr/2026/000001");
        List<IncmComunicacao> linhas = comunicacoes(processo);
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(1).getTransicaoDerivada()).isEqualTo("PUBLICADO");
        assertThat(linhas.get(1).getActIdentifier()).isEqualTo(900000000000001L);
        assertThat(linhas.get(1).getSerieDr()).isNull();
    }

    @Test
    void deveAnularOProcessoQuandoAIncmRejeitaOAto() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ATO_REJEITADO);

        DesfechoConsultaIncm desfecho = service.consultar(processo.getId());

        assertThat(desfecho).isEqualTo(DesfechoConsultaIncm.ANULADO_INCM);
        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        IncmComunicacao linha = comunicacoes(processo).get(0);
        assertThat(linha.getProcessingStatus()).isEqualTo("SubmissionRejected");
        assertThat(linha.getTransicaoDerivada()).isEqualTo("ANULADO_INCM");
        assertThat(linha.getResposta()).contains("ActValidationErrorMessageList");
        List<Auditoria> auditoria = auditorias(processo);
        assertThat(auditoria).extracting(Auditoria::getAcao, Auditoria::getEstadoAntes, Auditoria::getEstadoDepois,
                Auditoria::getUtilizadorId).containsExactly(
                tuple("REJEICAO_INCM", "EM_VALIDACAO_INCM", "ANULADO_INCM", null));
        assertThat(semEspacos(auditoria.get(0).getDadosAlteracao())).contains("\"mensagens\":[\"");
    }

    @Test
    void devePassarPorEmValidacaoIncmAntesDePublicarUmProcessoEnviado() {
        Processo processo = criarProcesso(EstadoProcesso.ENVIADO_INCM, ATO_PUBLICADO);

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);
        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_INCM);
        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.PUBLICADO);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(auditorias(processo)).extracting(Auditoria::getAcao, Auditoria::getEstadoAntes,
                Auditoria::getEstadoDepois).containsExactlyInAnyOrder(
                tuple("RETER_DOCUMENTOS", null, null),
                tuple("VALIDACAO_INCM", "ENVIADO_INCM", "EM_VALIDACAO_INCM"),
                tuple("PUBLICACAO_INCM", "EM_VALIDACAO_INCM", "PUBLICADO"));
    }

    @Test
    void deveAnularPorFaltaDePagamentoUmAtoJaAceiteEDeixarDeOConsultar(CapturedOutput saida) {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ATO_ANULADO_PAGAMENTO);
        long versaoInicial = processo.getVersao();

        job.executar();

        Processo aceite = recarregar(processo);
        assertThat(aceite.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(aceite.getVersao()).isEqualTo(versaoInicial);
        assertThat(auditorias(processo)).isEmpty();
        assertThat(rejeicaoRepository.maiorSequencia(processo.getId())).isEmpty();

        job.executar();

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(comunicacoes(processo)).extracting(IncmComunicacao::getOperacao,
                IncmComunicacao::getProcessingStatus, IncmComunicacao::getActStatus,
                IncmComunicacao::getTransicaoDerivada).containsExactly(
                tuple("GET_ACT_DETAILS", "SubmissionAccepted", "Em edição", null),
                tuple("GET_ACT_DETAILS", "SubmissionRejected", null, "ANULADO_INCM"));
        List<Auditoria> auditoria = auditorias(processo);
        assertThat(auditoria).extracting(Auditoria::getAcao, Auditoria::getEstadoAntes, Auditoria::getEstadoDepois,
                Auditoria::getUtilizadorId).containsExactly(
                tuple("REJEICAO_INCM", "EM_VALIDACAO_INCM", "ANULADO_INCM", null));
        assertThat(auditoria.get(0).getDadosAlteracao()).contains(MENSAGEM_ANULACAO_PAGAMENTO);
        assertThat(rejeicaoRepository.maiorSequencia(processo.getId())).contains(1);
        ProcessoRejeicao rejeicao = rejeicaoRepository
                .findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(processo.getId(), OrigemRejeicao.INCM)
                .orElseThrow();
        assertThat(rejeicao.getMotivoTexto()).isEqualTo(MENSAGEM_ANULACAO_PAGAMENTO);
        assertThat(rejeicao.getCriadoPor()).isNull();

        int pedidosAntes = pedidosGetActDetails(ATO_ANULADO_PAGAMENTO);
        job.executar();

        assertThat(pedidosGetActDetails(ATO_ANULADO_PAGAMENTO)).isEqualTo(pedidosAntes);
        assertThat(comunicacoes(processo)).hasSize(2);
        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);

        List<String> linhasDoProcesso = saida.getAll().lines()
                .filter(linha -> linha.contains(processo.getId().toString()))
                .toList();
        assertThat(linhasDoProcesso)
                .anyMatch(linha -> linha.contains("Consulta à INCM aplicada")
                        && linha.contains("desfecho=ANULADO_INCM"))
                .noneMatch(linha -> linha.contains("Autor ") || linha.matches(".*[\\w.+-]+@[\\w-]+\\.[\\w.]+.*"));
        // O corpo da resposta do GetActDetails vai para o log da chamada externa (develop, 0d0784a), que no log em JSON
        // (US-317) leva o processoId; as linhas do serviço não repetem a mensagem da INCM.
        assertThat(linhasDoProcesso)
                .filteredOn(linha -> !linha.contains("ExecutorChamadasExternas"))
                .noneMatch(linha -> linha.contains("falta de pagamento"));
    }

    @Test
    void deveAnularPorFaltaDePagamentoUmProcessoAindaEnviado() {
        Processo processo = criarProcesso(EstadoProcesso.ENVIADO_INCM, ATO_ANULADO_PAGAMENTO);

        job.executar();

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_INCM);

        job.executar();

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(auditorias(processo)).extracting(Auditoria::getAcao, Auditoria::getEstadoAntes,
                Auditoria::getEstadoDepois).containsExactly(
                tuple("REJEICAO_INCM", "ENVIADO_INCM", "ANULADO_INCM"));
        assertThat(comunicacoes(processo)).extracting(IncmComunicacao::getTransicaoDerivada)
                .containsExactly(null, "ANULADO_INCM");
        assertThat(rejeicaoRepository
                .findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(processo.getId(), OrigemRejeicao.INCM))
                .map(ProcessoRejeicao::getMotivoTexto).contains(MENSAGEM_ANULACAO_PAGAMENTO);
    }

    @Test
    void deveManterOProcessoQuandoAIncmNaoConheceOAto() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, "123");

        DesfechoConsultaIncm desfecho = service.consultar(processo.getId());

        assertThat(desfecho).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);
        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        IncmComunicacao linha = comunicacoes(processo).get(0);
        assertThat(linha.getIsSuccess()).isFalse();
        assertThat(linha.getTransicaoDerivada()).isNull();
        assertThat(auditorias(processo)).isEmpty();
    }

    private static int pedidosGetActDetails(String actIdentifier) {
        String filtro = """
                {"method": "POST", "url": "/GetActDetails", "bodyPatterns": [
                  {"matchesJsonPath": {"expression": "$.ActIdentifier", "equalTo": "%s"}}]}""".formatted(actIdentifier);
        Map<?, ?> resposta = RestClient.create(SimuladorWireMockTeste.INCM.url()).post()
                .uri("/__admin/requests/count")
                .contentType(MediaType.APPLICATION_JSON).body(filtro).retrieve().body(Map.class);
        return ((Number) resposta.get("count")).intValue();
    }
}
