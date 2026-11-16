package com.conkord.saigt.controller.snit;

import com.conkord.saigt.configuration.EmissorTokensUtilizador;
import com.conkord.saigt.configuration.ClienteHttpTeste;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

/**
 * `consultaPlantas`: a consulta do SNIT, os erros do contrato e o endpoint aberto só ao cliente OAuth `snit` da #393,
 * com tokens reais do `/oauth2/token` do Spring Authorization Server da SAIGT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=" + ConsultaPlantasIntegrationTest.PORTA,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:" + ConsultaPlantasIntegrationTest.PORTA
                + "/oauth2/jwks",
        "app.security.auth-disabled=false",
        "app.oauth.clientes.snit.client-id=snit-teste",
        "app.oauth.clientes.snit.client-secret=segredo-ficticio-snit-0123456789",
        "app.oauth.clientes.geoconform.client-id=geoconform-teste",
        "app.oauth.clientes.geoconform.client-secret=segredo-ficticio-geoconform-0123456789"
})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(ConsultaPlantasIntegrationTest.Nio2TomcatConfig.class)
class ConsultaPlantasIntegrationTest {

    static final int PORTA = 18095;
    private static final String CAMINHO = "/api/v1/integracoes/snit/plantas";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(7100);
    private static final String HASH = "d".repeat(64);
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** NIO2 evita o mecanismo de wakeup por Pipe do conector NIO padrão do Tomcat, que falha neste ambiente sandboxed. */
    @TestConfiguration
    static class Nio2TomcatConfig {
        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2Customizer() {
            return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        }
    }

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EmissorTokensUtilizador emissorTokens;
    @Autowired
    private TransactionTemplate transacao;
    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private InstrumentoRepository instrumentoRepository;
    @Autowired
    private DinamicaRepository dinamicaRepository;
    @Autowired
    private ProcessoRepository processoRepository;
    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;
    @Autowired
    private PlantaRepository plantaRepository;
    @Autowired
    private MetadadosMotuRepository metadadosMotuRepository;
    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

    @MockBean
    private ArmazenamentoDocumentos armazenamento;
    @MockBean
    private EnvioIncmService envioIncmService;
    @MockBean
    private ClienteSnit clienteSnit;

    private record Cenario(Processo processo, String idus, List<Planta> plantas, Utilizador autor, int n) {
        String numeroDeposito() {
            return processo.getNumeroDeposito();
        }

        long saigtId() {
            return Long.parseLong(processo.getNumeroProcesso());
        }
    }

    private final ClienteHttpTeste http = new ClienteHttpTeste(PORTA);

    /** Token de cliente de sistema pedido ao `/oauth2/token` real (client credentials, `client_secret_basic`). */
    private String tokenDe(String clientId, String clientSecret) throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", clientId, clientSecret, "grant_type=client_credentials");
        assertThat(resposta.statusCode()).isEqualTo(200);
        return objectMapper.readTree(resposta.body()).get("access_token").asText();
    }

    /** Um só token para a classe: o `/oauth2/token` tem limite de pedidos e a classe passou dos 15 testes. */
    private static volatile String tokenSnitEmCache;

    private String tokenSnit() throws Exception {
        if (tokenSnitEmCache == null) {
            tokenSnitEmCache = tokenDe("snit-teste", "segredo-ficticio-snit-0123456789");
        }
        return tokenSnitEmCache;
    }

    private String tokenGeoconform() throws Exception {
        return tokenDe("geoconform-teste", "segredo-ficticio-geoconform-0123456789");
    }

    private String tokenUtilizadorSaigt(Cenario cenario) {
        return emissorTokens.emitirAcesso(new ClaimsUtilizador(cenario.autor().getId(), "Autora " + cenario.n(),
                "autora" + cenario.n() + "@cm-cpl.pt", "INCM", PerfilInterno.MASTER, null, null)).getTokenValue();
    }

    /** A resposta HTTP, com as verificações de estado e de JSON que a consulta usa. */
    private record Resposta(HttpResponse<String> http) {
        Resposta estado(int esperado) {
            assertThat(http.statusCode()).as(http.body()).isEqualTo(esperado);
            return this;
        }

        Resposta json(String caminho, Object esperado) {
            new JsonPathExpectationsHelper(caminho).assertValue(http.body(), esperado);
            return this;
        }

        <T> Resposta json(String caminho, org.hamcrest.Matcher<T> esperado) {
            new JsonPathExpectationsHelper(caminho).assertValue(http.body(), esperado);
            return this;
        }

        Resposta existe(String caminho) {
            new JsonPathExpectationsHelper(caminho).exists(http.body());
            return this;
        }

        Resposta naoExiste(String caminho) {
            new JsonPathExpectationsHelper(caminho).doesNotExist(http.body());
            return this;
        }

        Resposta cabecalhoComecaPor(String nome, String prefixo) {
            assertThat(http.headers().firstValue(nome)).hasValueSatisfying(valor -> assertThat(valor).startsWith(prefixo));
            return this;
        }

        String corpo() {
            return http.body();
        }
    }

    /** Um processo depositado com {@code folhas} plantas, todas ligadas ao seu Conjunto. */
    private Cenario cenario(int folhas) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("CPL-" + n, "Plano " + n, "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream().filter(t -> "ELABORACAO".equals(t.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, elaboracao));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CPL-FIM-" + n, "Procedimento final"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("513" + n, "Município de Teste " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-cpl.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "PDM de Teste " + n, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Procedimento final do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));
        processo.setNumeroDeposito("01.03.07/PDM/03/2024/" + n);
        processo = processoRepository.saveAndFlush(processo);

        String idus = "01.0307.01.001234.03.00005.01.002.01.2024" + n;
        UUID processoId = processo.getId();
        transacao.executeWithoutResult(e -> idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                .processo(processoRepository.getReferenceById(processoId)).idus(idus).sistemaOrigem("SAIGT")
                .idIgt("001234").idIgtDefinitivo(true).ordemDinamica("03").ordemDefinitiva(true).tentativa((short) 1)
                .procedimento("002").submissao((short) 1).pubIncm("20241216").atualizadoEm(Instant.now()).build()));

        List<Planta> plantas = new ArrayList<>();
        for (int i = 1; i <= folhas; i++) {
            plantas.add(planta(procedimento, processo, autor, n, i, true));
        }
        return new Cenario(processo, idus, plantas, autor, n);
    }

    private Planta planta(InstrumentoProcedimentoConfig procedimento, Processo processo, Utilizador autor, int n, int folha,
                          boolean comConjunto) {
        DocumentoConfig item = new DocumentoConfig(procedimento, "CPL_" + n + "_" + folha, "Planta " + folha, "Planta",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", 40 + folha);
        item.setPagina(PaginaDocumento.PLANTAS);
        item = documentoConfigRepository.saveAndFlush(item);
        ProcessoDocumento documento = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, item,
                item.getCategoria(), "zip", "planta" + folha + ".zip", "privado/cpl/" + n + "-" + folha, HASH, "v1", 10, autor));
        Planta planta = new Planta(processo, documento);
        planta.setNomeRegulamento("Planta de Ordenamento " + folha);
        planta.setNumeroFolha("08" + folha + "-4");
        planta.setNomeTiff("0307PO" + n + folha);
        planta.setNomeZip("0307Plantas_" + n + folha + ".zip");
        if (comConjunto) {
            planta.setMetadadosMotu(metadadosMotuRepository.saveAndFlush(MetadadosMotu.conjunto(processo, documento)));
        }
        return plantaRepository.saveAndFlush(planta);
    }

    private void alterar(Planta planta, java.util.function.Consumer<Planta> alteracao) {
        Planta linha = plantaRepository.findById(planta.getId()).orElseThrow();
        alteracao.accept(linha);
        plantaRepository.saveAndFlush(linha);
    }

    private Resposta consultar(String consulta) throws Exception {
        return new Resposta(http.get(CAMINHO + consulta, tokenSnit()));
    }

    @Test
    void devolveOProcessoEAsTresPlantasPeloNumeroDeDeposito() throws Exception {
        Cenario c = cenario(3);
        alterar(c.plantas().get(0), p -> p.setEmVigor(true));

        consultar("?numeroDeposito=" + c.numeroDeposito())
                .estado(200)
                .json("$.processos", hasSize(1))
                .json("$.processos[0].saigtId", c.saigtId())
                .json("$.processos[0].numeroDeposito", c.numeroDeposito())
                .json("$.processos[0].idus", c.idus())
                .json("$.processos[0].plantas", hasSize(3))
                .json("$.processos[0].plantas[*].idMotuCdg", everyUuid())
                .json("$.processos[0].plantas[*].nomeRegulamento", hasSize(3))
                .json("$.processos[0].plantas[*].numeroFolha", hasSize(3))
                .json("$.processos[0].plantas[*].nomeTIFF", hasSize(3))
                .json("$.processos[0].plantas[*].nomeZIP", hasSize(3))
                .json("$.processos[0].plantas[*].emVigor", containsInAnyOrder(1, 0, 0))
                .json("$.processos[0].plantas[*].atualizadoEm", hasSize(3))
                .json("$.processos[0].plantas[0].atualizadoEm", matchesPattern(".*Z$"));
    }

    private static org.hamcrest.Matcher<Iterable<? extends String>> everyUuid() {
        return org.hamcrest.Matchers.everyItem(matchesPattern(UUID_REGEX));
    }

    @Test
    void mesmoResultadoPeloSaigtId() throws Exception {
        Cenario c = cenario(3);
        String peloDeposito = consultar("?numeroDeposito=" + c.numeroDeposito()).estado(200).corpo();
        String peloSaigtId = consultar("?saigtId=" + c.saigtId()).estado(200).corpo();
        assertThat(peloSaigtId).isEqualTo(peloDeposito);

        consultar("?saigtId=" + c.saigtId() + "&numeroDeposito=" + c.numeroDeposito()).estado(200)
                .json("$.processos[0].plantas", hasSize(3));
    }

    @Test
    void semParametrosDa400NoFormatoDoContrato() throws Exception {
        consultar("").estado(400)
                .json("$.codigo", "VALIDACAO_FALHOU")
                .json("$.mensagem", "Indique pelo menos numeroDeposito ou saigtId.")
                .json("$.campos[0].path", "numeroDeposito")
                .json("$.campos[0].erro", "Obrigatório quando saigtId não é indicado.")
                .existe("$.timestamp")
                .existe("$.traceId");
    }

    @Test
    void depositoESaigtIdDeProcessosDiferentesDa400() throws Exception {
        Cenario a = cenario(1);
        Cenario b = cenario(1);
        consultar("?numeroDeposito=" + a.numeroDeposito() + "&saigtId=" + b.saigtId()).estado(400)
                .json("$.codigo", "VALIDACAO_FALHOU")
                .json("$.campos[0].path", "saigtId");
    }

    @Test
    void valoresInvalidosDao400() throws Exception {
        consultar("?saigtId=abc").estado(400).json("$.codigo", "VALIDACAO_FALHOU")
                .json("$.campos[0].path", "saigtId");
        consultar("?saigtId=0").estado(400).json("$.codigo", "VALIDACAO_FALHOU")
                .json("$.campos[0].path", "saigtId");
        consultar("?saigtId=-1").estado(400).json("$.campos[0].path", "saigtId");
        consultar("?numeroDeposito=X&emVigor=2").estado(400)
                .json("$.campos[0].path", "emVigor");
    }

    @Test
    void depositoDesconhecidoDa404() throws Exception {
        consultar("?numeroDeposito=NAO-EXISTE").estado(404)
                .json("$.codigo", "PROCESSO_DESCONHECIDO")
                .json("$.campos", nullValue());
    }

    @Test
    void processoSemDepositoNaoSeDevolvePeloSaigtId() throws Exception {
        Cenario c = cenario(1);
        Processo processo = processoRepository.findById(c.processo().getId()).orElseThrow();
        processo.setNumeroDeposito(null);
        processoRepository.saveAndFlush(processo);
        consultar("?saigtId=" + c.saigtId()).estado(404)
                .json("$.codigo", "PROCESSO_DESCONHECIDO");
    }

    @Test
    void tokenDeUtilizadorDaSaigtDa403ESemTokenDa401() throws Exception {
        Cenario c = cenario(1);
        String consulta = CAMINHO + "?numeroDeposito=" + c.numeroDeposito();

        new Resposta(http.get(consulta, tokenUtilizadorSaigt(c))).estado(403)
                .json("$.codigo", "ACAO_NAO_AUTORIZADA")
                .cabecalhoComecaPor("WWW-Authenticate", "Bearer");
        new Resposta(http.get(consulta, null)).estado(401)
                .json("$.codigo", "NAO_AUTENTICADO").cabecalhoComecaPor("WWW-Authenticate", "Bearer");
        new Resposta(http.get(consulta, "nao-e-um-jwt")).estado(401)
                .json("$.codigo", "NAO_AUTENTICADO").cabecalhoComecaPor("WWW-Authenticate", "Bearer");
    }

    @Test
    void tokenDoClienteDoGeoConformDa403() throws Exception {
        Cenario c = cenario(1);

        new Resposta(http.get(CAMINHO + "?numeroDeposito=" + c.numeroDeposito(), tokenGeoconform())).estado(403)
                .json("$.codigo", "ACAO_NAO_AUTORIZADA").cabecalhoComecaPor("WWW-Authenticate", "Bearer");
    }

    @Test
    void tokenDoClienteSnitNaoServeOutrosEndpointsDaApi() throws Exception {
        new Resposta(http.get("/api/v1/processos", tokenSnit())).estado(403).json("$.codigo", "SEM_PERMISSAO");
    }

    @Test
    void linkJpgDrVemQuandoPreenchidoEFicaOmitidoQuandoNulo() throws Exception {
        Cenario c = cenario(2);
        alterar(c.plantas().get(0), p -> p.setLinkJpeg("https://saigt.exemplo.pt/api/v1/publico/7c9e6679/PCgeral.jpg"));

        consultar("?numeroDeposito=" + c.numeroDeposito()).estado(200)
                .json("$.processos[0].plantas[0].linkJPG_DR", "https://saigt.exemplo.pt/api/v1/publico/7c9e6679/PCgeral.jpg")
                .naoExiste("$.processos[0].plantas[1].linkJPG_DR")
                .json("$.processos[0].plantas[1]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("linkJPG_DR")));
    }

    @Test
    void folhaSemConjuntoNaoVemEFicaUmWarnComAContagem(CapturedOutput output) throws Exception {
        Cenario c = cenario(2);
        InstrumentoProcedimentoConfig procedimento = processoRepository.findById(c.processo().getId()).orElseThrow()
                .getInstrumentoProcedimentoConfig();
        transacao.executeWithoutResult(e -> {
            Processo processo = processoRepository.findById(c.processo().getId()).orElseThrow();
            planta(procedimento, processo, c.autor(), c.n(), 8, false);
            planta(procedimento, processo, c.autor(), c.n(), 9, false);
        });

        consultar("?numeroDeposito=" + c.numeroDeposito()).estado(200)
                .json("$.processos[0].plantas", hasSize(2))
                .json("$.processos[0].plantas[?(@.nomeTIFF=='0307PO" + c.n() + "9')]", hasSize(0))
                .json("$.processos[0].plantas[?(@.nomeTIFF=='0307PO" + c.n() + "8')]", hasSize(0));

        assertThat(output.getAll()).contains("WARN")
                .contains("consultaPlantas: 2 planta(s) do processo " + c.processo().getNumeroProcesso()
                        + " sem Conjunto MOTU, não devolvidas");
    }

    /** Correção: a linha `planta` do ZIP de publicação (sem `nome_tiff`) não é uma folha e o contrato exige `nomeTIFF`. */
    @Test
    void plantaSemNomeTiffNaoVemEFicaUmWarnComAContagem(CapturedOutput output) throws Exception {
        Cenario c = cenario(3);
        alterar(c.plantas().get(0), p -> p.setNomeTiff(null));
        alterar(c.plantas().get(1), p -> p.setNomeTiff(" "));

        consultar("?numeroDeposito=" + c.numeroDeposito()).estado(200)
                .json("$.processos[0].plantas", hasSize(1))
                .json("$.processos[0].plantas[0].nomeTIFF", is("0307PO" + c.n() + "3"));

        assertThat(output.getAll()).contains("WARN")
                .contains("consultaPlantas: 2 planta(s) do processo " + c.processo().getNumeroProcesso()
                        + " sem nomeTIFF, não devolvidas");
    }

    /** AC10: `nomeRegulamento`, `numeroFolha` e `nomeTIFF`, por esta ordem de chaves, com os nulos no fim de cada chave. */
    @Test
    void plantasVemOrdenadasPorRegulamentoFolhaETiffComOsNulosNoFim() throws Exception {
        Cenario c = cenario(8);
        // regulamento, folha, tiff, por ordem de criação (propositadamente fora da ordem final); o tiff é sempre
        // preenchido, porque a linha sem nome_tiff não se devolve (ver plantaSemNomeTiffNaoVem...)
        String[][] valores = {
                {"B", "1", "a"}, {null, "1", "a"}, {"A", "2", "z"}, {"A", null, "a"},
                {"A", "1", "c"}, {"A", "1", "b"}, {"A", "1", "a"}, {null, null, "d"}};
        for (int i = 0; i < valores.length; i++) {
            String[] v = valores[i];
            alterar(c.plantas().get(i), p -> {
                p.setNomeRegulamento(v[0]);
                p.setNumeroFolha(v[1]);
                p.setNomeTiff(v[2]);
            });
        }

        Resposta resposta = consultar("?numeroDeposito=" + c.numeroDeposito()).estado(200)
                .json("$.processos[0].plantas", hasSize(8));
        List<String> obtidas = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode planta : objectMapper.readTree(resposta.corpo()).at("/processos/0/plantas")) {
            obtidas.add(planta.path("nomeRegulamento").asText("-") + "|" + planta.path("numeroFolha").asText("-") + "|"
                    + planta.path("nomeTIFF").asText("-"));
        }
        assertThat(obtidas).containsExactly("A|1|a", "A|1|b", "A|1|c", "A|2|z", "A|-|a", "B|1|a", "-|1|a", "-|-|d");
    }

    @Test
    void emVigorFiltraPelaColunaDaPlanta() throws Exception {
        Cenario c = cenario(3);
        alterar(c.plantas().get(0), p -> p.setEmVigor(true));
        alterar(c.plantas().get(1), p -> p.setEmVigor(true));
        String base = "?numeroDeposito=" + c.numeroDeposito();

        consultar(base + "&emVigor=1").json("$.processos[0].plantas", hasSize(2))
                .json("$.processos[0].plantas[*].emVigor", containsInAnyOrder(1, 1));
        consultar(base + "&emVigor=0").json("$.processos[0].plantas", hasSize(1))
                .json("$.processos[0].plantas[0].emVigor", is(0));
        consultar(base).json("$.processos[0].plantas", hasSize(3));
    }
}
