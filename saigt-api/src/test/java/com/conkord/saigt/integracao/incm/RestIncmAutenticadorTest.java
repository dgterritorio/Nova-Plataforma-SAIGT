package com.conkord.saigt.integracao.incm;

import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato "SSA - Communication Model DGT": campos PascalCase; credenciais inválidas vêm como 200 com `IsSuccess: false`. */
class RestIncmAutenticadorTest {

    private static final String CHAVE = "chave-servico";

    private ServidorIncmTeste servidor;

    @BeforeEach
    void iniciar() {
        servidor = ServidorIncmTeste.iniciar();
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private RestIncmAutenticador criarAutenticador(String baseUrl, CircuitBreakerRegistry registry) {
        IncmProperties propriedades = new IncmProperties(baseUrl, CHAVE, Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new RestIncmAutenticador(
                new ClienteIncm(RestClient.builder(), new ExecutorChamadasExternas(registry, evento -> { }), propriedades));
    }

    private RestIncmAutenticador criarAutenticador(CircuitBreakerRegistry registry) {
        return criarAutenticador(servidor.url(), registry);
    }

    @Test
    void deveEnviarUserEmailAddressEUserPasswordNoPedido() throws Exception {
        RestIncmAutenticador autenticador = criarAutenticador(CircuitBreakerRegistry.ofDefaults());
        servidor.responder("/ValidateUser", 200, """
                {"IsSuccess": false, "ErrorMessage": "User email address and/or password not valid", "UserInformation": null}
                """);

        autenticador.autenticar("ana@cm-lisboa.pt", "senha");

        assertThat(servidor.pedidos()).singleElement().satisfies(pedido -> {
            assertThat(pedido.caminho()).isEqualTo("/ValidateUser");
            assertThat(pedido.cabecalhos().getFirst("ServiceKey")).isEqualTo(CHAVE);
        });
        JsonNode corpo = new ObjectMapper().readTree(servidor.pedidos().get(0).corpo());
        assertThat(corpo.get("UserEmailAddress").asText()).isEqualTo("ana@cm-lisboa.pt");
        assertThat(corpo.get("UserPassword").asText()).isEqualTo("senha");
    }

    @Test
    void deveMapearAdesaoComSucesso() {
        RestIncmAutenticador autenticador = criarAutenticador(CircuitBreakerRegistry.ofDefaults());
        servidor.responder("/ValidateUser", 200, """
                {
                  "IsSuccess": true,
                  "ErrorMessage": "",
                  "UserInformation": {
                    "UserName": "Ana Silva",
                    "UserEmailAddress": "ana@cm-lisboa.pt",
                    "UserJobTitle": "Técnica",
                    "UserEnrollmentAgreementList": [
                      {
                        "UserEnrollmentAgreementIdentifier": "2015482",
                        "PublishingEntityName": "Município de Lisboa",
                        "PublishingEntityTaxId": "512523698",
                        "PublishingEntityAddress": "Praça do Município",
                        "PublishingEntityAddressNumber": "1",
                        "PublishingEntityFloor": null,
                        "PublishingEntityPostalCode": "1100-365",
                        "PublishingEntityPostalDesignation": "Lisboa",
                        "PublishingEntityLocality": "Lisboa",
                        "PublishingEntityPhoneNumber": "210000000",
                        "PublishingEntityEmailAddress": "geral@cm-lisboa.pt",
                        "PublishingEntityResponsible": {
                          "PublishingEntityResponsibleName": "Resp",
                          "PublishingEntityResponsibleEmailAddress": "resp@x.pt",
                          "PublishingEntityResponsibleJobTitle": "Diretor"
                        },
                        "PublishingEntityDRSeries2PartInformation": {
                          "PartId": 1234567891234567,
                          "PartLetter": "C",
                          "PartDescription": "Administração Pública"
                        },
                        "DiarioRepublicaSeries1AvailableActTypeList": [],
                        "DiarioRepublicaSeries2AvailableActTypeList": [
                          {"ActTypeId": 1234567891234568, "ActTypeName": "Despacho", "ActTypeCanPublishExtract": true}
                        ]
                      }
                    ]
                  }
                }
                """);

        Optional<UtilizadorIncm> resultado = autenticador.autenticar("ana@cm-lisboa.pt", "senha");

        assertThat(resultado).isPresent();
        UtilizadorIncm incm = resultado.get();
        assertThat(incm.nome()).isEqualTo("Ana Silva");
        assertThat(incm.cargo()).isEqualTo("Técnica");
        assertThat(incm.adesoes()).hasSize(1);
        AdesaoIncm adesao = incm.adesoes().get(0);
        assertThat(adesao.nif()).isEqualTo("512523698");
        assertThat(adesao.incmEnrollmentId()).isEqualTo("2015482");
        assertThat(adesao.drParteLetra()).isEqualTo("C");
        assertThat(adesao.drParteDescricao()).isEqualTo("Administração Pública");
        assertThat(AdesaoIncm.class.getRecordComponents()).extracting(componente -> componente.getName())
                .doesNotContain("pcm");
        assertThat(adesao.telefone()).isEqualTo("210000000");
        assertThat(adesao.email()).isEqualTo("geral@cm-lisboa.pt");
        assertThat(adesao.morada().arteria()).isEqualTo("Praça do Município");
        assertThat(adesao.morada().localidade()).isEqualTo("Lisboa");
        assertThat(adesao.atosAutorizadosSerie1()).isEmpty();
        assertThat(adesao.atosAutorizadosSerie2()).hasSize(1);
        assertThat(adesao.atosAutorizadosSerie2().get(0).nome()).isEqualTo("Despacho");
        assertThat(adesao.atosAutorizadosSerie2().get(0).extrato()).isTrue();
        assertThat(adesao.corpo()).contains("\"PublishingEntityTaxId\":\"512523698\"", "\"UserEnrollmentAgreementIdentifier\":\"2015482\"",
                "\"PublishingEntityLocality\":\"Lisboa\"");
    }

    /** `PublishingEntityAddress`/`...DRSeries2PartInformation` são 0..1 — ausentes na resposta, não objetos vazios. */
    @Test
    void deveOmitirMoradaEParteQuandoAusentesNaResposta() {
        RestIncmAutenticador autenticador = criarAutenticador(CircuitBreakerRegistry.ofDefaults());
        servidor.responder("/ValidateUser", 200, """
                {
                  "IsSuccess": true,
                  "UserInformation": {
                    "UserName": "Bruno Sem Morada",
                    "UserEmailAddress": "bruno@semmorada.pt",
                    "UserJobTitle": "Técnico",
                    "UserEnrollmentAgreementList": [
                      {
                        "UserEnrollmentAgreementIdentifier": "2015500",
                        "PublishingEntityName": "Entidade Sem Morada",
                        "PublishingEntityTaxId": "999888777",
                        "PublishingEntityResponsible": {
                          "PublishingEntityResponsibleName": "Resp",
                          "PublishingEntityResponsibleEmailAddress": "resp@x.pt",
                          "PublishingEntityResponsibleJobTitle": "Diretor"
                        },
                        "DiarioRepublicaSeries1AvailableActTypeList": [],
                        "DiarioRepublicaSeries2AvailableActTypeList": []
                      }
                    ]
                  }
                }
                """);

        AdesaoIncm adesao = autenticador.autenticar("bruno@semmorada.pt", "senha").orElseThrow().adesoes().get(0);

        assertThat(adesao.morada()).isNull();
        assertThat(adesao.drParteId()).isNull();
    }

    @Test
    void deveDevolverVazioQuandoIsSuccessFalso() {
        RestIncmAutenticador autenticador = criarAutenticador(CircuitBreakerRegistry.ofDefaults());
        servidor.responder("/ValidateUser", 200, """
                {"IsSuccess": false, "ErrorMessage": "User email address and/or password not valid", "UserInformation": null}
                """);

        Optional<UtilizadorIncm> resultado = autenticador.autenticar("ninguem@cm-lisboa.pt", "errada");

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveDevolverOMotivoDaIncmQuandoARecusaOuNenhumQuandoNaoOEnvia() {
        RestIncmAutenticador autenticador = criarAutenticador(CircuitBreakerRegistry.ofDefaults());
        servidor.responder("/ValidateUser", 200, """
                {"IsSuccess": false, "ErrorMessage": "User email address and/or password not valid", "UserInformation": null}
                """);

        ResultadoAutenticacaoIncm comMotivo = autenticador.autenticarComMotivo("ninguem@cm-lisboa.pt", "errada");

        assertThat(comMotivo.utilizador()).isEmpty();
        assertThat(comMotivo.motivoRecusa()).isEqualTo("User email address and/or password not valid");

        servidor.responder("/ValidateUser", 200, """
                {"IsSuccess": false, "ErrorMessage": "  ", "UserInformation": null}
                """);

        assertThat(autenticador.autenticarComMotivo("ninguem@cm-lisboa.pt", "errada").motivoRecusa()).isNull();
    }

    @Test
    void deveAbrirCircuitoApos2FalhasEDevolverIntegracaoIndisponivelSemNovaChamadaHttp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();
        RestIncmAutenticador autenticador = criarAutenticador(CircuitBreakerRegistry.of(config));
        servidor.responder("/ValidateUser", 500, null);

        assertThatThrownBy(() -> autenticador.autenticar("ana@cm-lisboa.pt", "senha"))
                .isInstanceOf(IncmIndisponivelException.class);
        assertThatThrownBy(() -> autenticador.autenticar("ana@cm-lisboa.pt", "senha"))
                .isInstanceOf(IncmIndisponivelException.class);

        // Circuito aberto — a 3.ª chamada tem de falhar sem chegar ao servidor.
        assertThatThrownBy(() -> autenticador.autenticar("ana@cm-lisboa.pt", "senha"))
                .isInstanceOf(IncmIndisponivelException.class);
        assertThat(servidor.pedidos()).hasSize(2);
    }

    @Test
    void deveDevolverIntegracaoIndisponivelQuandoAIncmEstaInacessivel() {
        RestIncmAutenticador autenticador =
                criarAutenticador(ServidorIncmTeste.urlInacessivel(), CircuitBreakerRegistry.ofDefaults());

        assertThatThrownBy(() -> autenticador.autenticar("ana@cm-lisboa.pt", "senha"))
                .isInstanceOf(IncmIndisponivelException.class)
                .hasMessage("Serviço da INCM temporariamente indisponível.");
    }
}
