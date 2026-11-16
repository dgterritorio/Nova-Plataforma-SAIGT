package com.conkord.saigt.integracao.incm;

import com.conkord.saigt.integracao.SimuladorWireMockTeste;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Tag("docker")
@EnabledIf("com.conkord.saigt.integracao.SimuladorWireMockTeste#incmDisponivel")
class ClienteIncmContraMockTest {

    private static final UUID PROCESSO_ID = UUID.fromString("0e6f3a52-7c1d-4b8e-a9f0-3d2c5b6a7e81");

    private static ClienteIncm criarCliente(String serviceKey, Duration readTimeout) {
        IncmProperties propriedades = new IncmProperties(SimuladorWireMockTeste.INCM.url(), serviceKey,
                Duration.ofSeconds(5), readTimeout);
        return new ClienteIncm(RestClient.builder(),
                new ExecutorChamadasExternas(CircuitBreakerRegistry.ofDefaults(), evento -> { }), propriedades);
    }

    private static ClienteIncm criarCliente() {
        return criarCliente("chave-de-teste", Duration.ofSeconds(10));
    }

    private static SubmitNewActRequest pedido(String sumario) {
        return new SubmitNewActRequest(new SubmitNewActRequest.DgtAct("ENR-001", null,
                new SubmitNewActRequest.DiarioRepublicaSeries2Act(10L, 3L, "full-text", sumario,
                        LocalDate.of(2026, 10, 20), "Artigo 76.º do RJIGT"),
                List.of(), List.of("https://exemplo.pt/ficheiros/aviso.pdf")));
    }

    private static SubmitNewActRequest pedidoPrimeiraSerie(String sumario, boolean suplemento, LocalDate dataSuplemento) {
        return new SubmitNewActRequest(new SubmitNewActRequest.DgtAct("ENR-001",
                new SubmitNewActRequest.DiarioRepublicaSeries1Act(2L, suplemento, dataSuplemento, sumario,
                        LocalDate.of(2026, 10, 20)),
                null, List.of(), List.of("https://exemplo.pt/ficheiros/portaria.pdf")));
    }

    @BeforeEach
    void reporCenarios() {
        SimuladorWireMockTeste.INCM.reporCenarios();
    }

    @Test
    void deveDevolverTimeoutComMockTimeout() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado = criarCliente("chave-de-teste", Duration.ofSeconds(2))
                .submeterAto(PROCESSO_ID, pedido("[MOCK:TIMEOUT] Aviso de início"));

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.TIMEOUT);
        assertThat(resultado.httpStatus()).isNull();
    }

    @Test
    void deveDevolverErroHttp500ComMockHttp500() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado =
                criarCliente().submeterAto(PROCESSO_ID, pedido("[MOCK:HTTP500] Aviso de início"));

        assertThat(resultado.httpStatus()).isEqualTo(500);
        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.ERRO_HTTP);
    }

    @Test
    void deveDevolverErroHttp404SemServiceKey() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado =
                criarCliente("", Duration.ofSeconds(10)).submeterAto(PROCESSO_ID, pedido("Aviso de início"));

        assertThat(resultado.httpStatus()).isEqualTo(404);
        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaIncm.ERRO_HTTP);
    }

    @Test
    void deveDevolverIsSuccessFalsoSemFalhaTecnicaComMockErro() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado =
                criarCliente().submeterAto(PROCESSO_ID, pedido("[MOCK:ERRO] Aviso de início"));

        assertThat(resultado.falhaTecnica()).isNull();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().isSuccess()).isFalse();
        assertThat(resultado.corpo().errorMessageList()).containsExactly("ImageURLList cannot be empty.");
        assertThat(resultado.corpo().actIdentifier()).isNull();
    }

    @Test
    void deveSubmeterAtoEConsultarAteAPublicacao() {
        ClienteIncm cliente = criarCliente();

        long actIdentifier = cliente.submeterAto(PROCESSO_ID, pedido("Aviso de início")).corpo().actIdentifier();
        GetActDetailsResponse primeira = cliente.obterDetalhesAto(PROCESSO_ID, actIdentifier).corpo();
        LocalDate hojeEmLisboa = LocalDate.now(ZoneId.of("Europe/Lisbon"));
        GetActDetailsResponse segunda = cliente.obterDetalhesAto(PROCESSO_ID, actIdentifier).corpo();

        assertThat(actIdentifier).isEqualTo(900000000000001L);
        assertThat(primeira.processingStatus()).isEqualTo("SubmissionAccepted");
        assertThat(primeira.actStatus()).isEqualTo("Em edição");
        assertThat(primeira.publication()).isNull();
        assertThat(segunda.actStatus()).isEqualTo("Publicado");
        assertThat(segunda.actReferenceIdentifier()).isEqualTo("DR-2026-000001");
        assertThat(segunda.publication().actUrl()).isEqualTo("https://exemplo.pt/dr/2026/000001");
        assertThat(segunda.publication().actPublicationDate()).isEqualTo(hojeEmLisboa);
    }

    @Test
    void deveDevolverSubmissionRejectedNaConsultaComMockRejeitar() {
        ClienteIncm cliente = criarCliente();

        long actIdentifier = cliente.submeterAto(PROCESSO_ID, pedido("[MOCK:REJEITAR] Aviso")).corpo().actIdentifier();
        GetActDetailsResponse detalhes = cliente.obterDetalhesAto(PROCESSO_ID, actIdentifier).corpo();

        assertThat(actIdentifier).isEqualTo(900000000000002L);
        assertThat(detalhes.processingStatus()).isEqualTo("SubmissionRejected");
        assertThat(detalhes.actValidationErrorMessageList()).isNotEmpty();
        assertThat(detalhes.publication()).isNull();
    }

    @Test
    void deveDevolverAceiteEDepoisSubmissionRejectedComMockAnularPagamento() {
        ClienteIncm cliente = criarCliente();

        long actIdentifier =
                cliente.submeterAto(PROCESSO_ID, pedido("[MOCK:ANULAR_PAGAMENTO] Aviso")).corpo().actIdentifier();
        GetActDetailsResponse primeira = cliente.obterDetalhesAto(PROCESSO_ID, actIdentifier).corpo();
        GetActDetailsResponse segunda = cliente.obterDetalhesAto(PROCESSO_ID, actIdentifier).corpo();
        GetActDetailsResponse terceira = cliente.obterDetalhesAto(PROCESSO_ID, actIdentifier).corpo();

        assertThat(actIdentifier).isEqualTo(900000000000003L);
        assertThat(primeira.processingStatus()).isEqualTo("SubmissionAccepted");
        assertThat(primeira.actStatus()).isEqualTo("Em edição");
        assertThat(primeira.publication()).isNull();
        assertThat(segunda.processingStatus()).isEqualTo("SubmissionRejected");
        assertThat(segunda.actValidationErrorMessageList())
                .containsExactly("[MOCK] Ato anulado por falta de pagamento (mensagem fictícia).");
        assertThat(segunda.actStatus()).isNull();
        assertThat(segunda.publication()).isNull();
        assertThat(terceira).isEqualTo(segunda);
    }

    @Test
    void deveAceitarUmAtoDePrimeiraSerieSemSuplemento() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado =
                criarCliente().submeterAto(PROCESSO_ID, pedidoPrimeiraSerie("Portaria do plano", false, null));

        assertThat(resultado.falhaTecnica()).isNull();
        assertThat(resultado.corpo().isSuccess()).isTrue();
        assertThat(resultado.corpo().actIdentifier()).isEqualTo(900000000000001L);
    }

    @Test
    void deveAceitarUmAtoDePrimeiraSerieEmSuplementoComData() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado = criarCliente().submeterAto(PROCESSO_ID,
                pedidoPrimeiraSerie("Portaria do plano", true, LocalDate.of(2026, 12, 4)));

        assertThat(resultado.corpo().isSuccess()).isTrue();
        assertThat(resultado.corpo().actIdentifier()).isEqualTo(900000000000001L);
    }

    @Test
    void deveDevolverOErroDoContratoNumAtoDePrimeiraSerieEmSuplementoSemData() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado =
                criarCliente().submeterAto(PROCESSO_ID, pedidoPrimeiraSerie("Portaria do plano", true, null));

        assertThat(resultado.falhaTecnica()).isNull();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().isSuccess()).isFalse();
        assertThat(resultado.corpo().errorMessageList()).containsExactly(
                "When PublishInSuplement is \"True\", the field SuplementPublishingDate must have a valid date.");
        assertThat(resultado.corpo().actIdentifier()).isNull();
    }

    @Test
    void deveAplicarAsMarcasDoMockNoSumarioDaPrimeiraSerie() {
        ResultadoChamadaIncm<SubmitNewActResponse> resultado = criarCliente().submeterAto(PROCESSO_ID,
                pedidoPrimeiraSerie("[MOCK:REJEITAR] Portaria do plano", false, null));

        assertThat(resultado.corpo().actIdentifier()).isEqualTo(900000000000002L);
    }

    @Test
    void deveDevolverIsSuccessFalsoParaUmAtoInexistente() {
        ResultadoChamadaIncm<GetActDetailsResponse> resultado = criarCliente().obterDetalhesAto(PROCESSO_ID, 123L);

        assertThat(resultado.falhaTecnica()).isNull();
        assertThat(resultado.corpo().isSuccess()).isFalse();
        assertThat(resultado.corpo().errorMessage()).contains("123");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULO", value = {
            "ana.entidade@incm.pt, 1, RESPONSAVEL",
            "carlos.multiplas@incm.pt, 2, RESPONSAVEL",
            "maria.tres@incm.pt, 3, Técnico Superior",
            "sofia.semcargo@incm.pt, 2, NULO",
            "duarte.semadesao@incm.pt, 0, RESPONSAVEL",
            "helena.sintra@incm.pt, 1, RESPONSAVEL",
            "ricardo.silva@incm.pt, 2, RESPONSAVEL"})
    void deveAutenticarOsUtilizadoresDoReadmeDoMock(String email, int adesoes, String cargo) {
        Optional<UtilizadorIncm> utilizador = new RestIncmAutenticador(criarCliente()).autenticar(email, "Senha123!");

        assertThat(utilizador).isPresent();
        assertThat(utilizador.get().adesoes()).hasSize(adesoes);
        assertThat(utilizador.get().cargo()).isEqualTo(cargo);
    }

    @Test
    void deveDevolverAsDuasAdesoesDoRicardoComOsNipcDaCarga() {
        UtilizadorIncm ricardo = new RestIncmAutenticador(criarCliente())
                .autenticar("ricardo.silva@incm.pt", "Senha123!").orElseThrow();

        assertThat(ricardo.adesoes()).extracting(AdesaoIncm::incmEnrollmentId, AdesaoIncm::nif, AdesaoIncm::nomeEntidade)
                .containsExactly(
                        tuple("ENR-101", "505187531", "Município de Cascais"),
                        tuple("ENR-102", "500051062", "Câmara Municipal de Sintra"));
    }

    @Test
    void deveRecusarCredenciaisDesconhecidasDoMock() {
        RestIncmAutenticador autenticador = new RestIncmAutenticador(criarCliente());

        assertThat(autenticador.autenticar("ana.entidade@incm.pt", "errada")).isEmpty();
        assertThat(autenticador.autenticar("ninguem@incm.pt", "Senha123!")).isEmpty();
    }
}
