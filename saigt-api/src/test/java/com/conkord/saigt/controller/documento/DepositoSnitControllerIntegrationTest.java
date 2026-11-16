package com.conkord.saigt.controller.documento;

import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifesto;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinha;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinhaRepository;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoRepository;
import com.conkord.saigt.persistence.operacional.ConsumidorDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescargaRepository;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CaminhoDocumento;
import com.conkord.saigt.service.incm.EnvioIncmTesteBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pasta de depósito e ZIP de peças gráficas lidos pelo SNIT, com o RustFS real num bucket versionado. */
@SpringBootTest(properties = {
        "app.security.auth-disabled=false",
        "app.documentos.descarga.limite-por-cliente-snit=1",
        "saigt.teste.bucket=" + DepositoSnitControllerIntegrationTest.BUCKET_VERSIONADO})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepositoSnitControllerIntegrationTest extends EnvioIncmTesteBase {

    static final String BUCKET_VERSIONADO = "saigt-deposito-snit-versionado-teste";
    private static final String CLIENTE = "snit-teste";
    private static final byte[] PLANTA = "%PDF-1.4 planta do deposito".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ASSINADO = "PK-assinado-geoconform".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REGULAMENTO = "%PDF-1.4 regulamento".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PECA = "PK-peca-grafica-zip-comunicada-um-a-um".getBytes(StandardCharsets.UTF_8);

    static {
        MinioClient cliente = MinioClient.builder()
                .endpoint("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3))
                .credentials(ACCESS_KEY, SECRET_KEY).build();
        try {
            if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_VERSIONADO).build())) {
                cliente.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_VERSIONADO).objectLock(true).build());
            }
            cliente.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(BUCKET_VERSIONADO)
                    .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @SpyBean
    private ArmazenamentoDocumentos armazenamento;
    @Autowired
    private DepositoSnitManifestoRepository manifestoRepository;
    @Autowired
    private DepositoSnitManifestoLinhaRepository linhaRepository;
    @Autowired
    private DocumentoDescargaRepository descargaRepository;

    private record Linha(ProcessoDocumento documento, boolean umAUm) {
    }

    private record Deposito(Cenario cenario, DepositoSnitManifesto manifesto, ProcessoDocumento planta,
                            ProcessoDocumento assinado, ProcessoDocumento regulamento, ProcessoDocumento peca,
                            ProcessoDocumento substituido) {
        String numero() {
            return cenario.processo().getNumeroProcesso();
        }
    }

    // --- 1. ZIP da pasta -------------------------------------------------------------------------------------

    @Test
    void deveServirOZipDasCaixasDoManifestoSemSubstituidosNemPecasUmAUmEFicarAuditado() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO, CategoriaDocumento.PUBLICACAO));

        MockHttpServletResponse resposta = mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(resposta.getContentType()).isEqualTo("application/zip");
        assertThat(resposta.getHeader("Accept-Ranges")).isEqualTo("none");
        assertThat(resposta.getHeader("Content-Length")).isNull();
        assertThat(resposta.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(resposta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(resposta.getHeader("Content-Disposition")).startsWith("attachment");

        Map<String, byte[]> entradas = new TreeMap<>();
        Map<String, Integer> metodos = new TreeMap<>();
        lerZip(resposta.getContentAsByteArray(), entradas, metodos);
        assertThat(entradas).containsOnlyKeys(
                "DEPOSITO/" + deposito.planta().getId() + "-Ata_de_Reuniao.pdf",
                "DEPOSITO/GEOCONFORM/" + deposito.assinado().getId() + "-assinado.zip",
                "PUBLICACAO/" + deposito.regulamento().getId() + "-Regulamento.pdf");
        assertThat(entradas.get("DEPOSITO/" + deposito.planta().getId() + "-Ata_de_Reuniao.pdf")).isEqualTo(PLANTA);
        assertThat(entradas.get("DEPOSITO/GEOCONFORM/" + deposito.assinado().getId() + "-assinado.zip"))
                .isEqualTo(ASSINADO);
        assertThat(metodos.values()).containsOnly(ZipEntry.STORED);

        assertThat(descargasDe(deposito)).singleElement().satisfies(descarga -> {
            assertThat(descarga.getConsumidor()).isEqualTo(ConsumidorDescarga.SNIT);
            assertThat(descarga.getManifestoId()).isEqualTo(deposito.manifesto().getId());
            assertThat(descarga.getProcessoId()).isEqualTo(deposito.cenario().processo().getId());
            assertThat(descarga.getProcessoDocumentoId()).isNull();
            assertThat(descarga.getUtilizadorId()).isNull();
            assertThat(descarga.getResultado()).isEqualTo(ResultadoDescarga.CONCLUIDA);
            assertThat(descarga.getBytesEnviados()).isEqualTo(resposta.getContentAsByteArray().length);
        });
    }

    @Test
    void deveServirSoACaixaPorOmissaoDoManifesto() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));

        MockHttpServletResponse resposta = mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE))
                .andExpect(status().isOk()).andReturn().getResponse();

        Map<String, byte[]> entradas = new TreeMap<>();
        lerZip(resposta.getContentAsByteArray(), entradas, new TreeMap<>());
        assertThat(entradas.keySet()).allMatch(nome -> nome.startsWith("DEPOSITO/"));
        assertThat(entradas).hasSize(2);
    }

    @Test
    void deveServirOConteudoDoManifestoPeloVersionIdMesmoDepoisDeSubstituirOuAcrescentar() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
        byte[] novo = "%PDF-1.4 versao corrigida depois do deposito".getBytes(StandardCharsets.UTF_8);
        ProcessoDocumento planta = deposito.planta();
        String versaoNova = armazenamento.guardar(planta.getCaminhoFicheiro(), new ByteArrayInputStream(novo),
                "application/pdf");
        planta.setVersaoObjeto(versaoNova);
        planta.setHashSha256(sha256(novo));
        planta.setTamanhoBytes(novo.length);
        processoDocumentoRepository.saveAndFlush(planta);
        ProcessoDocumento acrescentado = carregar(deposito.cenario(), CategoriaDocumento.DEPOSITO,
                OrigemDocumento.CARREGADO, "Extra.pdf", novo, false);

        MockHttpServletResponse resposta = mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE))
                .andExpect(status().isOk()).andReturn().getResponse();

        Map<String, byte[]> entradas = new TreeMap<>();
        lerZip(resposta.getContentAsByteArray(), entradas, new TreeMap<>());
        assertThat(entradas.get("DEPOSITO/" + planta.getId() + "-Ata_de_Reuniao.pdf")).isEqualTo(PLANTA);
        assertThat(entradas.keySet()).noneMatch(nome -> nome.contains(acrescentado.getId().toString()));
    }

    @Test
    void deveServirSempreOUltimoDepositoComunicado() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
        DepositoSnitManifesto segundo = manifestoRepository.saveAndFlush(new DepositoSnitManifesto(
                deposito.cenario().processo().getId(), EnumSet.of(CategoriaDocumento.DEPOSITO),
                Instant.now().plusSeconds(60)));
        linhaRepository.saveAndFlush(new DepositoSnitManifestoLinha(segundo.getId(), deposito.assinado(), false));

        MockHttpServletResponse resposta = mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE))
                .andExpect(status().isOk()).andReturn().getResponse();

        Map<String, byte[]> entradas = new TreeMap<>();
        lerZip(resposta.getContentAsByteArray(), entradas, new TreeMap<>());
        assertThat(entradas).containsOnlyKeys("DEPOSITO/GEOCONFORM/" + deposito.assinado().getId() + "-assinado.zip");
    }

    // --- 2. ZIP de peça gráfica -----------------------------------------------------------------------------

    @Test
    void deveServirOZipDaPecaGraficaComCabecalhosHeadERange() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
        String url = urlFicheiro(deposito, deposito.peca().getId().toString());

        MockHttpServletResponse completo = mockMvc.perform(pedido(get(url), CLIENTE)).andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(completo.getContentAsByteArray()).isEqualTo(PECA);
        assertThat(completo.getHeader("Content-Length")).isEqualTo(String.valueOf(PECA.length));
        assertThat(completo.getHeader("ETag")).isEqualTo("\"" + sha256(PECA) + "\"");
        assertThat(completo.getHeader("Accept-Ranges")).isEqualTo("bytes");
        assertThat(completo.getContentType()).isEqualTo("application/zip");

        MockHttpServletResponse cabecalhos = mockMvc.perform(pedido(head(url), CLIENTE)).andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(cabecalhos.getContentAsByteArray()).isEmpty();
        assertThat(cabecalhos.getHeader("Content-Length")).isEqualTo(String.valueOf(PECA.length));
        assertThat(cabecalhos.getHeader("ETag")).isEqualTo("\"" + sha256(PECA) + "\"");

        MockHttpServletResponse parcial = mockMvc.perform(pedido(get(url), CLIENTE).header("Range", "bytes=0-1"))
                .andExpect(status().isPartialContent()).andReturn().getResponse();
        assertThat(parcial.getContentAsByteArray()).isEqualTo(Arrays.copyOfRange(PECA, 0, 2));
        assertThat(parcial.getHeader("Content-Range")).isEqualTo("bytes 0-1/" + PECA.length);

        assertThat(descargasDe(deposito)).extracting(DocumentoDescarga::getResultado)
                .containsExactlyInAnyOrder(ResultadoDescarga.CONCLUIDA, ResultadoDescarga.CONCLUIDA);
        assertThat(descargasDe(deposito)).allSatisfy(descarga -> {
            assertThat(descarga.getConsumidor()).isEqualTo(ConsumidorDescarga.SNIT);
            assertThat(descarga.getProcessoDocumentoId()).isEqualTo(deposito.peca().getId());
            assertThat(descarga.getManifestoId()).isEqualTo(deposito.manifesto().getId());
        });
        assertThat(descargasDe(deposito).stream().mapToLong(DocumentoDescarga::getBytesEnviados).sum())
                .isEqualTo(PECA.length + 2);
    }

    // --- 3. 404 igual -----------------------------------------------------------------------------------------

    @Test
    void deveDarOMesmo404ParaProcessoInexistenteSemDepositoEIdForaDoManifesto() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
        Cenario semDeposito = criarCenario("Sem deposito");

        String inexistente = corpo404(get("/api/v1/integracoes/snit/depositos/99999999999"));
        String semManifesto = corpo404(get("/api/v1/integracoes/snit/depositos/"
                + semDeposito.processo().getNumeroProcesso()));
        String idDesconhecido = corpo404(get(urlFicheiro(deposito, UUID.randomUUID().toString())));
        String idDaPasta = corpo404(get(urlFicheiro(deposito, deposito.planta().getId().toString())));
        String idInvalido = corpo404(get(urlFicheiro(deposito, "nao-e-um-id")));
        String ficheiroSemManifesto = corpo404(get("/api/v1/integracoes/snit/depositos/"
                + semDeposito.processo().getNumeroProcesso() + "/ficheiros/" + deposito.peca().getId()));

        assertThat(List.of(semManifesto, idDesconhecido, idDaPasta, idInvalido, ficheiroSemManifesto))
                .allSatisfy(corpo -> assertThat(corpo).isEqualTo(inexistente));
        assertThat(inexistente).startsWith("PROCESSO_DESCONHECIDO|");
        assertThat(descargasDe(deposito)).extracting(DocumentoDescarga::getResultado)
                .containsOnly(ResultadoDescarga.RECUSADA).hasSize(3);
    }

    // --- 4. Limites -------------------------------------------------------------------------------------------

    @Test
    void deveRecusarComTooManyRequestsQuandoOClienteTemDescargasEmCursoNoLimite() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
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
            Future<Integer> primeira = executor.submit(() -> mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE))
                    .andReturn().getResponse().getStatus());
            assertThat(aberto.await(10, TimeUnit.SECONDS)).isTrue();

            String corpo = mockMvc.perform(pedido(get(urlFicheiro(deposito, deposito.peca().getId().toString())),
                            CLIENTE))
                    .andExpect(status().isTooManyRequests()).andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(corpo).get("codigo").asText()).isEqualTo("LIMITE_REQUISICOES_EXCEDIDO");

            libertar.countDown();
            assertThat(primeira.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            libertar.countDown();
            executor.shutdownNow();
        }

        assertThat(descargasDe(deposito)).extracting(DocumentoDescarga::getResultado)
                .containsExactlyInAnyOrder(ResultadoDescarga.CONCLUIDA, ResultadoDescarga.RECUSADA);
    }

    @Test
    void deveRecusarComTooManyRequestsOPedidoSeguinteAoLimiteDePedidosPorMinutoDoCliente() throws Exception {
        String clienteProprio = "snit-limite-pedidos";
        String url = "/api/v1/integracoes/snit/depositos/99999999998";

        for (int i = 0; i < 120; i++) {
            mockMvc.perform(pedido(head(url), clienteProprio)).andExpect(status().isNotFound());
        }
        String corpo = mockMvc.perform(pedido(head(url), clienteProprio)).andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(corpo).get("codigo").asText()).isEqualTo("LIMITE_REQUISICOES_EXCEDIDO");
        mockMvc.perform(pedido(head(url), "outro-cliente")).andExpect(status().isNotFound());
    }

    // --- 5. Falhas do storage ---------------------------------------------------------------------------------

    @Test
    void deveDar503ComRetryAfterQuandoOStorageFalhaAntesDeComecarOEnvio() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
        doThrow(new StorageIndisponivelException("falha", new IOException("rede")))
                .when(armazenamento).abrir(any(), any(), anyLong(), anyLong());

        MockHttpServletResponse pasta = mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse();
        MockHttpServletResponse ficheiro = mockMvc.perform(pedido(get(urlFicheiro(deposito,
                        deposito.peca().getId().toString())), CLIENTE))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse();

        assertThat(pasta.getHeader("Retry-After")).isEqualTo("5");
        assertThat(ficheiro.getHeader("Retry-After")).isEqualTo("5");
        assertThat(descargasDe(deposito)).extracting(DocumentoDescarga::getResultado)
                .containsOnly(ResultadoDescarga.RECUSADA).hasSize(2);
    }

    @Test
    void deveRegistarInterrompidaELibertarOLugarQuandoALeituraFalhaAMeioDoEnvio() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
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
            mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE));
        } catch (Exception esperada) {
            // A falha a meio do envio corta a ligação em vez de devolver um erro com corpo.
        }

        assertThat(descargasDe(deposito)).extracting(DocumentoDescarga::getResultado)
                .containsExactly(ResultadoDescarga.INTERROMPIDA);
        org.mockito.Mockito.reset(armazenamento);
        mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE)).andExpect(status().isOk());
    }

    @Test
    void deveCortarOEnvioQuandoOObjetoLidoNaoEOComunicado() throws Exception {
        Deposito deposito = depositar(EnumSet.of(CategoriaDocumento.DEPOSITO));
        byte[] adulterado = "%PDF-1.4 conteudo diferente do comunicado".getBytes(StandardCharsets.UTF_8);
        doAnswer(invocacao -> new ByteArrayInputStream(adulterado))
                .when(armazenamento).abrir(any(), any(), anyLong(), anyLong());

        try {
            mockMvc.perform(pedido(get(urlPasta(deposito)), CLIENTE));
        } catch (Exception esperada) {
            // O ZIP fica sem diretório central.
        }

        assertThat(descargasDe(deposito)).extracting(DocumentoDescarga::getResultado)
                .containsExactly(ResultadoDescarga.INTERROMPIDA);
    }

    // --- cenário ----------------------------------------------------------------------------------------------

    /** Planta (com nome acentuado), ZIP assinado em GEOCONFORM, regulamento, peça um a um e um objeto em SUBSTITUIDOS. */
    private Deposito depositar(Set<CategoriaDocumento> caixas) {
        Cenario cenario = criarCenario("Deposito");
        ProcessoDocumento planta = carregar(cenario, CategoriaDocumento.DEPOSITO, OrigemDocumento.CARREGADO,
                "Ata de Reunião.pdf", PLANTA, false);
        ProcessoDocumento assinado = carregar(cenario, CategoriaDocumento.DEPOSITO, OrigemDocumento.GEOCONFORM,
                "assinado.zip", ASSINADO, false);
        ProcessoDocumento regulamento = carregar(cenario, CategoriaDocumento.PUBLICACAO, OrigemDocumento.CARREGADO,
                "Regulamento.pdf", REGULAMENTO, false);
        ProcessoDocumento peca = carregar(cenario, CategoriaDocumento.DEPOSITO, OrigemDocumento.CARREGADO,
                "Peca.zip", PECA, false);
        ProcessoDocumento substituido = carregar(cenario, CategoriaDocumento.DEPOSITO, OrigemDocumento.CARREGADO,
                "Antigo.pdf", PLANTA, true);

        DepositoSnitManifesto manifesto = manifestoRepository.saveAndFlush(new DepositoSnitManifesto(
                cenario.processo().getId(), caixas, Instant.now()));
        List<Linha> linhas = List.of(new Linha(planta, false), new Linha(assinado, false),
                new Linha(regulamento, false), new Linha(peca, true), new Linha(substituido, false));
        linhaRepository.saveAllAndFlush(linhas.stream()
                .filter(linha -> caixas.contains(linha.documento().getCategoria()))
                .map(linha -> new DepositoSnitManifestoLinha(manifesto.getId(), linha.documento(), linha.umAUm()))
                .toList());
        return new Deposito(cenario, manifesto, planta, assinado, regulamento, peca, substituido);
    }

    private ProcessoDocumento carregar(Cenario cenario, CategoriaDocumento categoria, OrigemDocumento origem,
                                       String nome, byte[] conteudo, boolean substituido) {
        Processo processo = cenario.processo();
        ProcessoDocumento documento = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, null,
                categoria, "pdf", nome, "", sha256(conteudo), "v", conteudo.length, processo.getCriadoPor()));
        documento.setOrigem(origem);
        String caminho = substituido
                ? CaminhoDocumento.substituido(processo.getNumeroProcesso(), origem, documento.getId(), "abcd1234", nome)
                : CaminhoDocumento.privado(processo.getNumeroProcesso(), categoria, origem, documento.getId(), nome);
        documento.setCaminhoFicheiro(caminho);
        String versao = armazenamento.guardar(caminho, new ByteArrayInputStream(conteudo), "application/octet-stream");
        documento.setVersaoObjeto(versao == null ? "null" : versao);
        return processoDocumentoRepository.saveAndFlush(documento);
    }

    private List<DocumentoDescarga> descargasDe(Deposito deposito) {
        return descargaRepository.findAll().stream()
                .filter(descarga -> deposito.manifesto().getId().equals(descarga.getManifestoId())).toList();
    }

    private static String urlPasta(Deposito deposito) {
        return "/api/v1/integracoes/snit/depositos/" + deposito.numero();
    }

    private static String urlFicheiro(Deposito deposito, String id) {
        return urlPasta(deposito) + "/ficheiros/" + id;
    }

    private MockHttpServletRequestBuilder pedido(MockHttpServletRequestBuilder pedido, String cliente) {
        return pedido.with(tokenSnit(cliente));
    }

    private static RequestPostProcessor tokenSnit(String cliente) {
        return jwt().jwt(builder -> builder.subject(cliente).claim("sistema", "snit"));
    }

    private String corpo404(MockHttpServletRequestBuilder pedido) throws Exception {
        String corpo = mockMvc.perform(pedido(pedido, CLIENTE)).andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString();
        JsonNode no = objectMapper.readTree(corpo);
        return no.get("codigo").asText() + "|" + no.get("mensagem").asText();
    }

    private static void lerZip(byte[] zip, Map<String, byte[]> conteudos, Map<String, Integer> metodos)
            throws IOException {
        Path ficheiro = Files.createTempFile("deposito-snit", ".zip");
        try {
            Files.write(ficheiro, zip);
            try (ZipFile arquivo = new ZipFile(ficheiro.toFile())) {
                for (ZipEntry entrada : java.util.Collections.list(arquivo.entries())) {
                    byte[] bytes = arquivo.getInputStream(entrada).readAllBytes();
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    assertThat(crc.getValue()).isEqualTo(entrada.getCrc());
                    assertThat(entrada.getSize()).isEqualTo(bytes.length);
                    conteudos.put(entrada.getName(), bytes);
                    metodos.put(entrada.getName(), entrada.getMethod());
                }
            }
        } finally {
            Files.deleteIfExists(ficheiro);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void esperar(CountDownLatch trinco) throws IOException {
        try {
            trinco.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}
