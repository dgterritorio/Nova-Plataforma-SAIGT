package com.conkord.saigt.controller.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import com.conkord.saigt.exception.StorageIndisponivelException;
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
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.DocumentoDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescargaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.auth-disabled=false", "app.documentos.descarga.limite-simultaneas=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DescargaDocumentoControllerIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 conteúdo para descarga com bytes suficientes"
            .getBytes(StandardCharsets.UTF_8);
    private static final String PREFIXO_URL = "http://localhost:8080/api/v1/descargas/";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @SpyBean
    private ArmazenamentoDocumentos armazenamento;
    @Autowired
    private DescargaProperties propriedades;
    @Autowired
    private DocumentoDescargaRepository descargaRepository;
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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig documentoConfig) {
    }

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DESC-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DESC-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DESC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig documentoConfig = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"pdf\"]", 1));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("509000" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-desc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DESC/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        return new Cenario(processo, autor, entidade, documentoConfig);
    }

    private static RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String urlDocumentos(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private UUID carregar(Cenario cenario) throws Exception {
        var pedido = multipart(urlDocumentos(cenario.processo()))
                .file(new MockMultipartFile("ficheiro", "regulamento.pdf", "application/pdf", CONTEUDO))
                .param("categoria", "PUBLICACAO")
                .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                .with(jwtDe(cenario.entidade(), cenario.autor().getId()));
        String resposta = mockMvc.perform(pedido).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private String pedirTicket(Cenario cenario, UUID documentoId) throws Exception {
        String resposta = mockMvc.perform(post(urlDocumentos(cenario.processo()) + "/" + documentoId + "/descarga")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(resposta).get("url").asText();
        assertThat(url).startsWith(PREFIXO_URL);
        return url.substring(PREFIXO_URL.length());
    }

    private List<DocumentoDescarga> linhasDe(UUID documentoId) {
        return descargaRepository.findAll().stream()
                .filter(linha -> documentoId.equals(linha.getProcessoDocumentoId())).toList();
    }

    @Test
    void naoDeveEmitirTicketAUtilizadorSemAcessoAoProcesso() throws Exception {
        Cenario cenario = criarCenario("001");
        Cenario alheio = criarCenario("002");
        UUID documentoId = carregar(cenario);

        mockMvc.perform(post(urlDocumentos(cenario.processo()) + "/" + documentoId + "/descarga")
                        .with(jwtDe(alheio.entidade(), alheio.autor().getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveServirOFicheiroCompletoComCabecalhosEAuditoriaConcluida() throws Exception {
        Cenario cenario = criarCenario("003");
        UUID documentoId = carregar(cenario);
        ProcessoDocumento documento = processoDocumentoRepository.findById(documentoId).orElseThrow();

        MvcResult resultado = mockMvc.perform(get("/api/v1/descargas/" + pedirTicket(cenario, documentoId)))
                .andExpect(status().isOk()).andReturn();

        var resposta = resultado.getResponse();
        assertThat(resposta.getContentAsByteArray()).isEqualTo(CONTEUDO);
        assertThat(resposta.getHeader("Content-Length")).isEqualTo(String.valueOf(CONTEUDO.length));
        assertThat(resposta.getHeader("ETag")).isEqualTo("\"" + documento.getHashSha256() + "\"");
        assertThat(resposta.getHeader("Accept-Ranges")).isEqualTo("bytes");
        assertThat(resposta.getHeader("Content-Disposition")).startsWith("attachment").contains("regulamento.pdf");
        assertThat(resposta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(resposta.getHeader("Content-Security-Policy")).isEqualTo("sandbox; default-src 'none'");
        assertThat(resposta.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(resposta.getHeader("Referrer-Policy")).isEqualTo("no-referrer");

        List<DocumentoDescarga> linhas = linhasDe(documentoId);
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getResultado()).isEqualTo(ResultadoDescarga.CONCLUIDA);
        assertThat(linhas.get(0).getBytesEnviados()).isEqualTo(CONTEUDO.length);
        assertThat(linhas.get(0).getUtilizadorId()).isEqualTo(cenario.autor().getId());
        assertThat(linhas.get(0).getProcessoId()).isEqualTo(cenario.processo().getId());
    }

    @Test
    void deveReutilizarOTicketDentroDaValidade() throws Exception {
        Cenario cenario = criarCenario("004");
        UUID documentoId = carregar(cenario);
        String ticket = pedirTicket(cenario, documentoId);

        mockMvc.perform(get("/api/v1/descargas/" + ticket)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/descargas/" + ticket)).andExpect(status().isOk());

        assertThat(linhasDe(documentoId)).hasSize(2);
    }

    @Test
    void deveServirUmIntervaloEIgnorarIfRangeDiferenteDoEtag() throws Exception {
        Cenario cenario = criarCenario("005");
        UUID documentoId = carregar(cenario);
        String ticket = pedirTicket(cenario, documentoId);
        String etag = "\"" + processoDocumentoRepository.findById(documentoId).orElseThrow().getHashSha256() + "\"";

        var parcial = mockMvc.perform(get("/api/v1/descargas/" + ticket).header("Range", "bytes=5-14"))
                .andExpect(status().isPartialContent()).andReturn().getResponse();
        assertThat(parcial.getContentAsByteArray()).hasSize(10)
                .isEqualTo(java.util.Arrays.copyOfRange(CONTEUDO, 5, 15));
        assertThat(parcial.getHeader("Content-Range")).isEqualTo("bytes 5-14/" + CONTEUDO.length);

        var comIfRangeIgual = mockMvc.perform(get("/api/v1/descargas/" + ticket)
                        .header("Range", "bytes=5-14").header("If-Range", etag))
                .andExpect(status().isPartialContent()).andReturn().getResponse();
        assertThat(comIfRangeIgual.getContentAsByteArray()).hasSize(10);

        var completo = mockMvc.perform(get("/api/v1/descargas/" + ticket)
                        .header("Range", "bytes=5-14").header("If-Range", "\"outro\""))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(completo.getContentAsByteArray()).isEqualTo(CONTEUDO);

        mockMvc.perform(get("/api/v1/descargas/" + ticket).header("Range", "bytes=9999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }

    @Test
    void headDeveDarOsCabecalhosSemCorpoESemAuditoria() throws Exception {
        Cenario cenario = criarCenario("006");
        UUID documentoId = carregar(cenario);

        var resposta = mockMvc.perform(head("/api/v1/descargas/" + pedirTicket(cenario, documentoId)))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(resposta.getContentAsByteArray()).isEmpty();
        assertThat(resposta.getHeader("Content-Length")).isEqualTo(String.valueOf(CONTEUDO.length));
        assertThat(linhasDe(documentoId)).isEmpty();
    }

    @Test
    void deveDar404IgualParaTicketAdulteradoOuInvalido() throws Exception {
        Cenario cenario = criarCenario("007");
        UUID documentoId = carregar(cenario);
        String ticket = pedirTicket(cenario, documentoId);
        String adulterado = ticket.substring(0, ticket.length() - 2) + (ticket.endsWith("AA") ? "BB" : "AA");

        String esperado = corpoSemRastos(mockMvc.perform(get("/api/v1/descargas/lixo"))
                .andExpect(status().isNotFound()).andReturn());
        assertThat(corpoSemRastos(mockMvc.perform(get("/api/v1/descargas/" + adulterado))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo(esperado);
        assertThat(corpoSemRastos(mockMvc.perform(get("/api/v1/descargas/a.b"))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo(esperado);
        assertThat(linhasDe(documentoId)).isEmpty();
    }

    @Test
    void deveRecusarComTooManyRequestsNoLimiteERegistarRecusada() throws Exception {
        Cenario cenario = criarCenario("008");
        UUID documentoId = carregar(cenario);
        String ticket = pedirTicket(cenario, documentoId);
        CountDownLatch aberto = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);
        doAnswer(invocacao -> {
            InputStream real = (InputStream) invocacao.callRealMethod();
            aberto.countDown();
            return new FilterInputStream(real) {
                @Override
                public int read(byte[] destino, int inicio, int tamanho) throws IOException {
                    esperar(libertar);
                    return super.read(destino, inicio, tamanho);
                }
            };
        }).when(armazenamento).abrir(any(), any(), anyLong(), anyLong());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> primeira = executor.submit(
                    () -> mockMvc.perform(get("/api/v1/descargas/" + ticket)).andReturn().getResponse().getStatus());
            assertThat(aberto.await(10, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(get("/api/v1/descargas/" + ticket)).andExpect(status().isTooManyRequests());

            libertar.countDown();
            assertThat(primeira.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            libertar.countDown();
            executor.shutdownNow();
        }

        assertThat(linhasDe(documentoId)).extracting(DocumentoDescarga::getResultado)
                .containsExactlyInAnyOrder(ResultadoDescarga.CONCLUIDA, ResultadoDescarga.RECUSADA);
    }

    @Test
    void deveDar503ComRetryAfterQuandoOStorageFalhaAntesDeComecar() throws Exception {
        Cenario cenario = criarCenario("009");
        UUID documentoId = carregar(cenario);
        String ticket = pedirTicket(cenario, documentoId);
        doThrow(new StorageIndisponivelException("falha", new IOException("rede")))
                .when(armazenamento).abrir(any(), any(), anyLong(), anyLong());

        var resposta = mockMvc.perform(get("/api/v1/descargas/" + ticket))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse();

        assertThat(resposta.getHeader("Retry-After")).isEqualTo(String.valueOf(propriedades.retryAfter().toSeconds()));
        assertThat(linhasDe(documentoId)).extracting(DocumentoDescarga::getResultado)
                .containsExactly(ResultadoDescarga.RECUSADA);
    }

    @Test
    void deveRegistarInterrompidaQuandoALeituraFalhaAMeioDoEnvio() throws Exception {
        Cenario cenario = criarCenario("010");
        UUID documentoId = carregar(cenario);
        String ticket = pedirTicket(cenario, documentoId);
        doAnswer(invocacao -> {
            InputStream real = (InputStream) invocacao.callRealMethod();
            return new FilterInputStream(real) {
                @Override
                public int read(byte[] destino, int inicio, int tamanho) throws IOException {
                    throw new IOException("ligação cortada");
                }
            };
        }).when(armazenamento).abrir(any(), any(), anyLong(), anyLong());

        try {
            mockMvc.perform(get("/api/v1/descargas/" + ticket));
        } catch (Exception esperada) {
            // A falha a meio do envio corta a ligação em vez de devolver um erro com corpo.
        }

        assertThat(linhasDe(documentoId)).extracting(DocumentoDescarga::getResultado)
                .containsExactly(ResultadoDescarga.INTERROMPIDA);
    }

    private static void esperar(CountDownLatch trinco) throws IOException {
        try {
            trinco.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private String corpoSemRastos(MvcResult resultado) throws Exception {
        var no = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return no.get("codigo").asText() + "|" + no.get("mensagem").asText();
    }
}
