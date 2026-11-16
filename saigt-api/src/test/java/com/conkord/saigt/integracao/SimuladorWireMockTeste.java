package com.conkord.saigt.integracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.UnaryOperator;

/**
 * Simulador WireMock do `saigt-implementation` (os mesmos mappings do docker-compose) para os testes contra os mocks.
 * O URL vem, por esta ordem, da variável de ambiente (o mock do compose), de um contentor levantado uma vez por JVM
 * com os mappings da pasta irmã `../saigt-implementation` (o pipeline clona-a), ou não há simulador.
 */
public final class SimuladorWireMockTeste {

    public static final SimuladorWireMockTeste INCM = new SimuladorWireMockTeste("incm-mock", "INCM_MOCK_URL");
    public static final SimuladorWireMockTeste SNIT = new SimuladorWireMockTeste("snit-mock", "SNIT_MOCK_URL");

    static final String ETIQUETA = "saigt.simulador";

    private static final String IMAGEM = "wiremock/wiremock:3.9.1-alpine";
    private static final Logger LOG = LoggerFactory.getLogger(SimuladorWireMockTeste.class);
    private static final int PORTA = 8080;

    private final String pasta;
    private final String variavel;
    private final UnaryOperator<String> ambiente;
    private final Path mappings;

    private GenericContainer<?> contentor;
    private int contentoresArrancados;

    private SimuladorWireMockTeste(String pasta, String variavel) {
        this(pasta, variavel, System::getenv, Path.of("..", "saigt-implementation"));
    }

    SimuladorWireMockTeste(String pasta, String variavel, UnaryOperator<String> ambiente, Path saigtImplementation) {
        this.pasta = pasta;
        this.variavel = variavel;
        this.ambiente = ambiente;
        this.mappings = saigtImplementation.resolve(Path.of("docker", pasta, "mappings"));
    }

    /** Para `@EnabledIf`: avaliar a condição não arranca contentores. */
    public static boolean incmDisponivel() {
        return INCM.disponivel();
    }

    /** Para `@EnabledIf`: avaliar a condição não arranca contentores. */
    public static boolean snitDisponivel() {
        return SNIT.disponivel();
    }

    public boolean disponivel() {
        return urlDaVariavel() != null || (Files.isDirectory(mappings) && dockerDisponivel());
    }

    public synchronized String url() {
        String daVariavel = urlDaVariavel();
        if (daVariavel != null) {
            return daVariavel;
        }
        if (!Files.isDirectory(mappings)) {
            throw new IllegalStateException("Simulador " + pasta + " indisponível: sem " + variavel + " nem "
                    + mappings.toAbsolutePath().normalize());
        }
        if (contentor == null) {
            contentor = arrancar();
        }
        return "http://" + contentor.getHost() + ":" + contentor.getMappedPort(PORTA);
    }

    /** Os cenários do WireMock (por exemplo `ato-publicado`) são globais ao contentor. */
    public void reporCenarios() {
        RestClient.create(url()).post().uri("/__admin/scenarios/reset").retrieve().toBodilessEntity();
    }

    synchronized int contentoresArrancados() {
        return contentoresArrancados;
    }

    private String urlDaVariavel() {
        String valor = ambiente.apply(variavel);
        return valor == null || valor.isBlank() ? null : valor;
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> arrancar() {
        GenericContainer<?> novo = new GenericContainer<>(IMAGEM)
                .withCommand("--verbose")
                .withLabel(ETIQUETA, pasta)
                // Cópia e não bind mount: com o Maven num contentor ou no dind, o Docker não vê o caminho do host.
                .withCopyFileToContainer(MountableFile.forHostPath(mappings), "/home/wiremock/mappings")
                .withExposedPorts(PORTA)
                .waitingFor(Wait.forHttp("/__admin/mappings").forPort(PORTA).withStartupTimeout(Duration.ofMinutes(2)));
        novo.start();
        contentoresArrancados++;
        LOG.info("Simulador {} arrancado com os mappings de {}", pasta, mappings.normalize());
        return novo;
    }

    private static boolean dockerDisponivel() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
