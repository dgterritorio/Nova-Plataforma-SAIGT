package com.conkord.saigt.integracao.geoconform;

import com.conkord.saigt.integracao.core.ExecutorChamadasExternas;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cenários de `docker/geoconform-mock/README.md`, com o token do stub `/mock/oauth/token`; corre com
 * `GEOCONFORM_MOCK_URL=http://localhost:8092`. As credenciais vêm de `GEOCONFORM_CLIENT_ID` e `GEOCONFORM_CLIENT_SECRET`
 * (o mock não as valida).
 */
@EnabledIfEnvironmentVariable(named = "GEOCONFORM_MOCK_URL", matches = ".+")
class ClienteGeoconformContraMockTest {

    private static final String URL_MOCK = System.getenv("GEOCONFORM_MOCK_URL");
    private static final String CLIENTE = System.getenv().getOrDefault("GEOCONFORM_CLIENT_ID", "changeme");
    private static final String SEGREDO = System.getenv().getOrDefault("GEOCONFORM_CLIENT_SECRET", "changeme");
    private static final String IDUS_DA_TABELA = "01.1199.01.012345.02.90001.01.031.01";
    private static final ReferenciaChamada REFERENCIA =
            new ReferenciaChamada(UUID.fromString("9d3e2f10-4b5c-4d6e-8f70-81a2b3c4d5e6"), null, null);

    private static ClienteGeoconform criarCliente() {
        return new ClienteGeoconform(RestClient.builder(), new ExecutorChamadasExternas(CircuitBreakerRegistry.ofDefaults(), evento -> { }),
                new GeoconformProperties(URL_MOCK, URL_MOCK + "/mock/oauth/token", CLIENTE, SEGREDO, Duration.ofSeconds(5),
                        Duration.ofSeconds(10)), Clock.systemUTC());
    }

    private static PedidoCriacaoProcessoGeoconform pedido(String idus, String descricaoIgt) {
        return new PedidoCriacaoProcessoGeoconform(idus, "US263-1", descricaoIgt, "Revisão — teste contra o mock",
                new EntidadeGeoconform("Câmara Municipal de Teste", "500000000"), true);
    }

    @Test
    void deveObterOTokenECriarComUmIdusNovoDevolvendoOUrlDeAcessoEOLocation() {
        String idus = "01.1199.01.%06d.02.90001.01.031.01".formatted(ThreadLocalRandom.current().nextInt(100_000, 999_999));
        ClienteGeoconform cliente = criarCliente();

        ResultadoGeoconform<ProcessoGeoconform> resultado = cliente.criarProcesso(pedido(idus, "PDM_TESTE"), REFERENCIA);
        ResultadoGeoconform<ProcessoGeoconform> outra = cliente.criarProcesso(pedido(idus, "PDM_TESTE"), REFERENCIA);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(201);
        assertThat(resultado.corpo().idus()).isEqualTo(idus);
        assertThat(resultado.corpo().numprocSaigt()).isEqualTo("US263-1");
        assertThat(resultado.corpo().estado()).isEqualTo("aguarda_ativacao");
        assertThat(resultado.corpo().urlAcesso()).isEqualTo("http://localhost:8092/acesso/" + idus);
        assertThat(resultado.location()).isEqualTo("/api/geoconform/v1/processos/" + idus);
        assertThat(outra.sucesso()).isTrue();
    }

    @Test
    void deveTratarUmIdusDaTabelaComo409IduDuplicado() {
        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(
                pedido(IDUS_DA_TABELA, "PDM_TESTE"), REFERENCIA);

        assertThat(resultado.duplicado()).isTrue();
        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.chamada().httpStatus()).isEqualTo(409);
        assertThat(resultado.codigoErro()).isEqualTo("idu_duplicado");
    }

    @Test
    void deveDevolver422PayloadInvalidoSemOsCamposObrigatorios() {
        ResultadoGeoconform<ProcessoGeoconform> resultado = criarCliente().criarProcesso(
                pedido("01.1199.01.900011.02.90001.01.031.01", null), REFERENCIA);

        assertThat(resultado.chamada().falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.chamada().httpStatus()).isEqualTo(422);
        assertThat(resultado.codigoErro()).isEqualTo("payload_invalido");
    }

    @Test
    void deveRetirarERepetirComSucessoNumIdusDaTabela() {
        ClienteGeoconform cliente = criarCliente();
        PedidoVisibilidadeGeoconform retirar = new PedidoVisibilidadeGeoconform(false, "caducidade",
                LocalDate.of(2028, 8, 4));

        ResultadoChamadaExterna<Void> primeira = cliente.alterarVisibilidade(IDUS_DA_TABELA, retirar, REFERENCIA);
        ResultadoChamadaExterna<Void> repetida = cliente.alterarVisibilidade(IDUS_DA_TABELA, retirar, REFERENCIA);

        assertThat(primeira.temFalhaTecnica()).isFalse();
        assertThat(primeira.httpStatus()).isEqualTo(200);
        assertThat(repetida.temFalhaTecnica()).isFalse();
        assertThat(repetida.httpStatus()).isEqualTo(200);
    }

    @Test
    void deveReporComVisivelTrueNumIdusDaTabela() {
        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(IDUS_DA_TABELA,
                new PedidoVisibilidadeGeoconform(true, null, null), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
    }

    @Test
    void deveDevolver404NaVisibilidadeDeUmIdusDesconhecido() {
        ResultadoChamadaExterna<Void> resultado = criarCliente().alterarVisibilidade(
                "01.1199.01.900001.02.90001.01.031.01",
                new PedidoVisibilidadeGeoconform(false, "caducidade", LocalDate.of(2028, 8, 4)), REFERENCIA);

        assertThat(resultado.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(resultado.httpStatus()).isEqualTo(404);
    }

    @Test
    void deveRegistarAPublicacaoNumIdusDaTabelaEDevolver404NumDesconhecido() {
        ClienteGeoconform cliente = criarCliente();
        PedidoPublicacaoGeoconform pedido = new PedidoPublicacaoGeoconform("900001", "18452/2026",
                "https://dre.example/18452-2026", LocalDate.of(2026, 10, 14));

        ResultadoChamadaExterna<Void> publicada = cliente.registarPublicacao(IDUS_DA_TABELA, pedido, REFERENCIA);
        ResultadoChamadaExterna<Void> desconhecida = cliente.registarPublicacao(
                "01.1199.01.900001.02.90001.01.031.01", pedido, REFERENCIA);

        assertThat(publicada.temFalhaTecnica()).isFalse();
        assertThat(publicada.httpStatus()).isEqualTo(200);
        assertThat(desconhecida.falhaTecnica()).isEqualTo(FalhaTecnicaExterna.ERRO_HTTP);
        assertThat(desconhecida.httpStatus()).isEqualTo(404);
    }

    @Test
    void deveCorrigirOsIdentificadoresDaTentativaComPublicacaoFinal() {
        ResultadoChamadaExterna<Void> resultado = criarCliente().corrigirIdentificadores(
                PedidoCorrecaoIdentificadoresGeoconform.publicacaoFinal("01.1199.01.012345.02.00003.01", List.of(
                        new CorrespondenciaIdusGeoconform(IDUS_DA_TABELA, "01.1199.01.012345.02.00003.01.031.01",
                                "900001"))), REFERENCIA);

        assertThat(resultado.temFalhaTecnica()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(200);
    }
}
