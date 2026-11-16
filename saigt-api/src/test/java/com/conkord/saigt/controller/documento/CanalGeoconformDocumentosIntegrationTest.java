package com.conkord.saigt.controller.documento;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ProcessoDocumentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Numa Dinâmica com GeoConform as peças gráficas não se carregam nem se substituem; o resto segue as regras de sempre. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CanalGeoconformDocumentosIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 versão original".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTEUDO_NOVO = "%PDF-1.4 versão corrigida, mais longa".getBytes(StandardCharsets.UTF_8);
    private static final String MENSAGEM =
            "Nesta Dinâmica as peças gráficas são obtidas do GeoConform e não podem ser carregadas.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioClient minioClient;

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
    private PermissaoAcaoRepository permissaoAcaoRepository;

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

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ProcessoDocumentoService.class);

    @BeforeEach
    void iniciarCapturaDeLogs() {
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void terminarCapturaDeLogs() {
        logger.detachAppender(logs);
        logger.setLevel(null);
    }

    private record Cenario(Instrumento instrumento, Dinamica dinamica, Processo processo, Utilizador autor,
                           Entidade entidade, DocumentoConfig planta, DocumentoConfig regulamento) {
    }

    private Cenario criarCenario(String codigo, Boolean comGeoconform) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("GEOC-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-GEOC-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-GEOC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig planta = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "PLANTA", "Planta de ordenamento", "Planta",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                        "[\"zip\"]", 1));
        DocumentoConfig regulamento = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"pdf\"]", 2));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("510000" + codigo, "Câmara G" + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor G" + codigo, "autorg" + codigo + "@cm-geoc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/GEOC/" + codigo, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor);
        dinamica.setComGeoconform(comGeoconform);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        limparObjetos("processos/" + processo.getNumeroProcesso() + "/");
        return new Cenario(instrumento, dinamica, processo, autor, entidade, planta, regulamento);
    }

    private void passarAGeoconform(Cenario cenario) {
        Dinamica dinamica = dinamicaRepository.findById(cenario.dinamica().getId()).orElseThrow();
        dinamica.setComGeoconform(true);
        dinamicaRepository.saveAndFlush(dinamica);
    }

    private RequestPostProcessor jwtDe(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(),
                        "pcm", false)));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private static String url(Processo processo, UUID documentoId) {
        return url(processo) + "/" + documentoId;
    }

    private ResultActions carregar(Cenario cenario, DocumentoConfig item, String nome)
            throws Exception {
        MockHttpServletRequestBuilder pedido = multipart(url(cenario.processo()))
                .file(new MockMultipartFile("ficheiro", nome, "application/pdf", ZipsDeTeste.conteudo(nome, CONTEUDO)))
                .param("categoria", item == null ? "DEPOSITO" : "PUBLICACAO");
        if (item != null) {
            pedido.param("documentoConfigId", item.getId().toString());
        }
        return mockMvc.perform(pedido.with(jwtDe(cenario)));
    }

    private ProcessoDocumento carregarComSucesso(Cenario cenario, DocumentoConfig item, String nome) throws Exception {
        String resposta = carregar(cenario, item, nome).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return processoDocumentoRepository.findById(UUID.fromString(objectMapper.readTree(resposta).get("id").asText()))
                .orElseThrow();
    }

    private ResultActions substituir(Cenario cenario, ProcessoDocumento documento, String nome)
            throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo(), documento.getId()))
                .file(new MockMultipartFile("ficheiro", nome, "application/pdf", ZipsDeTeste.conteudo(nome, CONTEUDO_NOVO)))
                .with(jwtDe(cenario)));
    }

    private List<ILoggingEvent> avisosDeRecusa() {
        return logs.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .filter(evento -> evento.getFormattedMessage().contains("Peça gráfica recusada"))
                .toList();
    }

    @Test
    void deveRecusarOCarregamentoDePecaGraficaSemGravarObjetoNemLinha() throws Exception {
        Cenario cenario = criarCenario("001", true);

        carregar(cenario, cenario.planta(), "planta.zip")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_CANAL_GEOCONFORM"))
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM));

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).isEmpty();
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    @Test
    void deveRegistarWarnSemDadosPessoaisAoRecusarOCarregamento() throws Exception {
        Cenario cenario = criarCenario("002", true);

        carregar(cenario, cenario.planta(), "planta-confidencial.zip").andExpect(status().isConflict());

        List<ILoggingEvent> avisos = avisosDeRecusa();
        assertThat(avisos).hasSize(1);
        String mensagem = avisos.get(0).getFormattedMessage();
        assertThat(mensagem).contains(cenario.processo().getId().toString(), cenario.planta().getId().toString())
                .doesNotContain(cenario.autor().getNome(), cenario.autor().getEmail(), cenario.entidade().getNipc(),
                        "planta-confidencial");
    }

    @Test
    void deveRecusarASubstituicaoDePecaGraficaEManterOFicheiroAnterior() throws Exception {
        Cenario cenario = criarCenario("003", null);
        ProcessoDocumento original = carregarComSucesso(cenario, cenario.planta(), "planta.zip");
        passarAGeoconform(cenario);

        substituir(cenario, original, "nova.zip")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_CANAL_GEOCONFORM"))
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM));

        ProcessoDocumento atual = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(atual.getNomeFicheiro()).isEqualTo("planta.zip");
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(original.getCaminhoFicheiro());
        assertThat(atual.getHashSha256()).isEqualTo(original.getHashSha256());
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/"))
                .containsExactly(original.getCaminhoFicheiro());
        assertThat(avisosDeRecusa()).hasSize(1);
    }

    @Test
    void deveAceitarPecaEscritaEBaldeOutrosNumaDinamicaComGeoconform() throws Exception {
        Cenario cenario = criarCenario("004", true);

        ProcessoDocumento regulamento = carregarComSucesso(cenario, cenario.regulamento(), "regulamento.pdf");
        carregarComSucesso(cenario, null, "notas.txt");
        substituir(cenario, regulamento, "novo.pdf").andExpect(status().isOk());

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).hasSize(2);
        assertThat(avisosDeRecusa()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void deveAceitarPecaGraficaQuandoAOpcaoGeoconformEFalsaOuNula(Boolean comGeoconform) throws Exception {
        Cenario cenario = criarCenario(comGeoconform == null ? "005" : "006", comGeoconform);

        ProcessoDocumento planta = carregarComSucesso(cenario, cenario.planta(), "planta.zip");
        substituir(cenario, planta, "nova.zip").andExpect(status().isOk());

        assertThat(processoDocumentoRepository.findById(planta.getId())).isPresent();
        assertThat(avisosDeRecusa()).isEmpty();
    }

    @Test
    void devePermitirRemoverUmaPecaGraficaCarregadaAntesDeAOpcaoGeoconformSerVerdadeira() throws Exception {
        Cenario cenario = criarCenario("007", null);
        ProcessoDocumento planta = carregarComSucesso(cenario, cenario.planta(), "planta.zip");
        passarAGeoconform(cenario);

        mockMvc.perform(delete(url(cenario.processo(), planta.getId())).with(jwtDe(cenario)))
                .andExpect(status().isNoContent());

        assertThat(processoDocumentoRepository.findById(planta.getId())).isEmpty();
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    /** O bucket é partilhado entre classes e o número do processo recomeça com cada BD de teste. */
    private void limparObjetos(String prefixo) {
        for (String objeto : objetosSob(prefixo)) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(BUCKET).object(objeto).build());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private List<String> objetosSob(String prefixo) {
        return StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(BUCKET).prefix(prefixo).recursive(true).build()).spliterator(), false)
                .map(resultado -> {
                    try {
                        return resultado.get().objectName();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }
}
