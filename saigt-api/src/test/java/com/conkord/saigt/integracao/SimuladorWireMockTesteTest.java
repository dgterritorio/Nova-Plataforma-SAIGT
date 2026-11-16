package com.conkord.saigt.integracao;

import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimuladorWireMockTesteTest {

    @TempDir
    Path saigtImplementation;

    @Test
    void deveUsarAVariavelDeAmbienteSemArrancarContentorMesmoComAPastaIrma() throws Exception {
        Files.createDirectories(saigtImplementation.resolve("docker/incm-mock/mappings"));
        SimuladorWireMockTeste simulador = new SimuladorWireMockTeste("incm-mock", "INCM_MOCK_URL",
                variavel -> "INCM_MOCK_URL".equals(variavel) ? "http://localhost:8089" : null, saigtImplementation);

        assertThat(simulador.disponivel()).isTrue();
        assertThat(simulador.url()).isEqualTo("http://localhost:8089");
        assertThat(simulador.url()).isEqualTo("http://localhost:8089");
        assertThat(simulador.contentoresArrancados()).isZero();
    }

    @Test
    void deveFicarIndisponivelSemVariavelNemPastaIrma() {
        SimuladorWireMockTeste simulador =
                new SimuladorWireMockTeste("snit-mock", "SNIT_MOCK_URL", variavel -> null, saigtImplementation);

        assertThat(simulador.disponivel()).isFalse();
        assertThatThrownBy(simulador::url).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SNIT_MOCK_URL").hasMessageContaining("snit-mock");
        assertThat(simulador.contentoresArrancados()).isZero();
    }

    @Test
    void deveTratarAVariavelEmBrancoComoAusente() {
        SimuladorWireMockTeste simulador =
                new SimuladorWireMockTeste("incm-mock", "INCM_MOCK_URL", variavel -> "  ", saigtImplementation);

        assertThat(simulador.disponivel()).isFalse();
    }

    /** Os simuladores partilhados servem todas as classes de teste da JVM: um contentor por simulador. */
    @Test
    @Tag("docker")
    @EnabledIf("com.conkord.saigt.integracao.SimuladorWireMockTeste#incmDisponivel")
    void deveArrancarUmSoContentorPorSimuladorEmVariasUtilizacoes() {
        String primeira = SimuladorWireMockTeste.INCM.url();
        String segunda = SimuladorWireMockTeste.INCM.url();

        assertThat(segunda).isEqualTo(primeira);
        if (System.getenv("INCM_MOCK_URL") == null || System.getenv("INCM_MOCK_URL").isBlank()) {
            assertThat(SimuladorWireMockTeste.INCM.contentoresArrancados()).isEqualTo(1);
            assertThat(contentoresDaSessao("incm-mock")).hasSize(1);
        } else {
            assertThat(SimuladorWireMockTeste.INCM.contentoresArrancados()).isZero();
            assertThat(contentoresDaSessao("incm-mock")).isEmpty();
        }
    }

    private static List<Container> contentoresDaSessao(String pasta) {
        return DockerClientFactory.instance().client().listContainersCmd()
                .withLabelFilter(Map.of(SimuladorWireMockTeste.ETIQUETA, pasta,
                        DockerClientFactory.TESTCONTAINERS_SESSION_ID_LABEL, DockerClientFactory.SESSION_ID))
                .exec();
    }
}
