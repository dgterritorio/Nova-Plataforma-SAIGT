package com.conkord.saigt.integracao.snit;

import com.conkord.saigt.integracao.SimuladorWireMockTeste;
import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Tag("docker")
@EnabledIf("com.conkord.saigt.integracao.SimuladorWireMockTeste#snitDisponivel")
class ClienteSnitContraMockTest {

    private static final ReferenciaChamada REFERENCIA =
            new ReferenciaChamada(UUID.fromString("7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"), null, null);

    private static ClienteSnit criarCliente() {
        SnitProperties propriedades = new SnitProperties(SimuladorWireMockTeste.SNIT.url(), "chave-de-teste",
                Duration.ofSeconds(5), Duration.ofSeconds(10));
        return new ClienteSnit(RestClient.builder(),
                new ExecutorChamadasExternas(CircuitBreakerRegistry.ofDefaults(), evento -> { }), propriedades);
    }

    private static PublicacaoSnit pedido(long ssaigtId) {
        return new PublicacaoSnit(ssaigtId, 1, 11, 3, "Correção Material do Plano Diretor Municipal de Exemplo",
                "NORTE", "9999", null, "00",
                new PublicacaoDrSnit("4321/2026", "2", "188", LocalDate.of(2026, 9, 27), null),
                new VerificacaoSnit(Instant.parse("2026-09-27T00:00:00Z"), "utilizador.teste"));
    }

    @Test
    void deveRegistarComResposta201EcoandoOSsaigtId() {
        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado = criarCliente().registarPublicacao(pedido(88001L),
                REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo().ssaigtId()).isEqualTo(88001L);
        assertThat(resultado.corpo().receivedAt()).isNotNull();
        assertThat(resultado.corpo().idIgt()).isNull();
    }

    @Test
    void deveDevolver200NaRepeticaoCom99200() {
        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado = criarCliente().registarPublicacao(pedido(99200L),
                REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().ssaigtId()).isEqualTo(99200L);
    }

    @Test
    void deveDevolverErroHttp409ComConflitoCom99409() {
        ResultadoChamadaExterna<PublicacaoRecebidaSnit> resultado = criarCliente().registarPublicacao(pedido(99409L),
                REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(409);
        assertThat(resultado.corpo()).isNull();
    }

    private static DepositoSnit deposito(long ssaigtId, String numeroDeposito) {
        return new DepositoSnit(ssaigtId, numeroDeposito, Instant.parse("2026-11-10T10:00:00Z"), null,
                "https://saigt.exemplo.pt/api/v1/integracoes/snit/depositos/00042", null);
    }

    @Test
    void deveRegistarODepositoCom201EcoandoOSsaigtIdEONumero() {
        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().registarDeposito(deposito(88100L, "03.11.11/PDM/02/2026/7"), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo().ssaigtId()).isEqualTo(88100L);
        assertThat(resultado.corpo().depositNumber()).isEqualTo("03.11.11/PDM/02/2026/7");
        assertThat(resultado.corpo().processStatus()).isEqualTo("pending_start");
        assertThat(resultado.corpo().existingTask()).isFalse();
    }

    @Test
    void deveDevolverErroHttp404NoDepositoSemPublicacaoCom99404() {
        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().registarDeposito(deposito(99404L, "1"), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(404);
    }

    @Test
    void deveDevolverErroHttp409NoNumeroDeDepositoDuplicado() {
        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado =
                criarCliente().registarDeposito(deposito(88101L, "99/DUPLICADO"), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(409);
    }

    @Test
    void deveAtualizarOIdusDaDinamicaNoDepositoCom200EcoandoOSsaigtId() {
        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado = criarCliente().atualizarIdusDeposito(88003L,
                "01.1199.01.000999.02.00005.20201226", REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
        assertThat(resultado.corpo().ssaigtId()).isEqualTo(88003L);
        assertThat(resultado.corpo().processStatus()).isEqualTo("in_progress");
    }

    @Test
    void deveDevolverErroHttp404NoIdusDeUmDepositoDesconhecidoCom99404() {
        ResultadoChamadaExterna<DepositoRecebidoSnit> resultado = criarCliente().atualizarIdusDeposito(99404L,
                "01.1199.01.000999.02.00005.20201226", REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(404);
    }

    @Test
    void deveRegistarOsFicheirosDoDepositoCom201() {
        FicheiroDepositoSnit ficheiro = new FicheiroDepositoSnit("8e1f0c2a-1111-4c3d-9e8f-000000000001", "PO_Sintra",
                null, "https://saigt.exemplo.pt/api/v1/integracoes/snit/depositos/00042/ficheiros/1", true,
                Instant.parse("2026-11-10T08:00:00Z"));

        ResultadoChamadaExterna<FicheirosRecebidosSnit> resultado =
                criarCliente().registarFicheirosDeposito(88100L, List.of(ficheiro), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo().ssaigtId()).isEqualTo(88100L);
        assertThat(resultado.corpo().fileCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ListaReferenciaSnit.class)
    void deveDevolverCadaTabelaDeReferenciaComAsChavesDoCodigoEDoNome(ListaReferenciaSnit lista) {
        List<JsonNode> registos = criarCliente().obterTabelaReferencia(lista);

        assertThat(registos).isNotEmpty().allSatisfy(registo -> {
            assertThat(registo.path(lista.chaveCodigo()).asText()).isNotBlank();
            assertThat(registo.path(lista.chaveNome()).asText()).isNotBlank();
        });
    }

    @Test
    void deveTrazerORen90015RepetidoNosTiposDeInstrumentoComoOSnitReal() {
        List<JsonNode> registos = criarCliente().obterTabelaReferencia(ListaReferenciaSnit.INSTRUMENT_TYPES);

        assertThat(registos).filteredOn(registo -> registo.path("id").asLong() == 90015L).hasSize(2);
        assertThat(registos).anySatisfy(registo -> assertThat(registo.path("id").asLong()).isEqualTo(37L));
    }

    /** Valores da tabela «Dados de teste» do README do `snit-mock`. */
    @Test
    void deveDevolverOsPdmEmVigorDeSintraEDeCascaisNaPesquisa() {
        PaginaSnit pagina = criarCliente().pesquisar("PDM", null, 1, 20);

        assertThat(pagina.items()).extracting(IgtSnit::idIgt, IgtSnit::typeCode, IgtSnit::designation,
                IgtSnit::validityStatus).containsExactlyInAnyOrder(
                tuple(1001L, "PDM", "PDM de Sintra (SNIT)", IgtSnit.EM_VIGOR),
                tuple(1002L, "PDM", "PDM de Cascais (SNIT)", IgtSnit.EM_VIGOR));
        assertThat(pagina.total()).isEqualTo(2);
    }

    @Test
    void deveDevolverODetalheComOTipoEADesignacaoDoIgtDaTabela() {
        IgtSnit igt = criarCliente().obter(1003).orElseThrow();

        assertThat(igt.idIgt()).isEqualTo(1003L);
        assertThat(igt.typeCode()).isEqualTo("PU");
        assertThat(igt.designation()).isEqualTo("PU da Zona Industrial de Sintra (SNIT)");
        assertThat(igt.emVigor()).isTrue();
    }

    @Test
    void deveDevolverVazioParaUmIdIgtInexistente() {
        assertThat(criarCliente().obter(9999)).isEmpty();
    }

    @Test
    void deveDevolverOsConcelhosNoDetalheDoIgtDoMock() {
        ClienteSnit cliente = criarCliente();

        assertThat(cliente.obter(1001).orElseThrow().municipalities())
                .containsExactly(new MunicipioIgtSnit("1111", "Sintra"));
        assertThat(cliente.obter(1006).orElseThrow().municipalities())
                .containsExactly(new MunicipioIgtSnit("1111", "Sintra"), new MunicipioIgtSnit("1105", "Cascais"));
    }
}
