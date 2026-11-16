package com.conkord.saigt.controller.documento;

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
import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
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
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublicoRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.documento.RegrasNomeFicheiro;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessoDocumentoControllerIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 conteúdo de teste".getBytes(StandardCharsets.UTF_8);
    private static final long MB = 1024L * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @SpyBean
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

    @Autowired
    private DocumentoLinkPublicoRepository documentoLinkPublicoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private ArmazenamentoIntencaoRepository intencaoRepository;

    @SpyBean
    private AuditoriaService auditoriaService;

    private record Cenario(Instrumento instrumento, Processo processo, Utilizador autor, Entidade entidade,
                           DocumentoConfig documentoConfig) {
    }

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DOC-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DOC-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig documentoConfig = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"pdf\"]", 1));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("509000" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo + "@cm-doc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOC/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        limparObjetos("processos/" + processo.getNumeroProcesso() + "/");
        return new Cenario(instrumento, processo, autor, entidade, documentoConfig);
    }

    private RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static MockMultipartFile ficheiro(String nome) {
        return new MockMultipartFile("ficheiro", nome, "application/pdf", ZipsDeTeste.conteudo(nome, CONTEUDO));
    }

    private static MockMultipartFile ficheiroGrande() {
        return new MockMultipartFile("ficheiro", "grande.pdf", "application/pdf", new byte[2048]);
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    @Test
    void deveCarregarDocumentoEGravarObjetoNoBucketSobPrivado() throws Exception {
        Cenario cenario = criarCenario("001");

        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("Regulamento_Fin.pdf"))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeFicheiro").value("Regulamento_Fin.pdf"))
                .andExpect(jsonPath("$.categoria").value("PUBLICACAO"))
                .andExpect(jsonPath("$.extensao").value("pdf"))
                .andExpect(jsonPath("$.origem").value("CARREGADO"))
                .andExpect(jsonPath("$.bloqueadoEdicao").value(false))
                .andExpect(jsonPath("$.documentoConfigId").value(cenario.documentoConfig().getId()))
                .andExpect(jsonPath("$.tamanhoBytes").value(CONTEUDO.length))
                .andExpect(jsonPath("$.caminhoFicheiro").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID documentoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        ProcessoDocumento documento = processoDocumentoRepository.findById(documentoId).orElseThrow();
        // O nome válido de 19 caracteres chega inteiro à chave.
        String caminhoEsperado = "processos/" + cenario.processo().getNumeroProcesso() + "/PUBLICACAO/" + documentoId
                + "-Regulamento_Fin.pdf";
        assertThat(documento.getCaminhoFicheiro()).isEqualTo(caminhoEsperado);
        assertThat(documento.getHashSha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTEUDO)));
        assertThat(documento.getTamanhoBytes()).isEqualTo(CONTEUDO.length);
        assertThat(documento.getCategoria()).isEqualTo(CategoriaDocumento.PUBLICACAO);
        assertThat(documento.getExtensao()).isEqualTo("pdf");
        assertThat(documento.getOrigem()).isEqualTo(OrigemDocumento.CARREGADO);
        assertThat(documento.isBloqueadoEdicao()).isFalse();
        assertThat(documento.getVersaoObjeto()).isNotBlank().isNotEqualTo("null");
        assertThat(caminhoEsperado).doesNotContain("privado/").doesNotContain(cenario.instrumento().getId().toString());
        assertThat(intencaoRepository.findByChaveObjeto(caminhoEsperado)).singleElement()
                .extracting(ArmazenamentoIntencao::getOperacao).isEqualTo(OperacaoArmazenamento.GRAVACAO);

        StatObjectResponse objeto = minioClient.statObject(
                StatObjectArgs.builder().bucket(BUCKET).object(caminhoEsperado).build());
        assertThat(objeto.size()).isEqualTo(CONTEUDO.length);

        List<Auditoria> auditoria = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia(
                "PROCESSO", cenario.processo().getId().toString());
        assertThat(auditoria).extracting(Auditoria::getAcao).containsExactly("CARREGAR_DOCUMENTO");
        JsonNode dados = objectMapper.readTree(auditoria.get(0).getDadosAlteracao());
        assertThat(dados.get("processoDocumentoId").asText()).isEqualTo(documentoId.toString());
        assertThat(dados.get("categoria").asText()).isEqualTo("PUBLICACAO");
    }

    @Test
    void deveGravarSobDepositoQuandoNaoHaDocumentoConfig() throws Exception {
        Cenario cenario = criarCenario("002");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("anexo.txt"))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value("DEPOSITO"))
                .andExpect(jsonPath("$.documentoConfigId").doesNotExist());

        String pasta = "processos/" + cenario.processo().getNumeroProcesso() + "/";
        assertThat(objetosSob(pasta + "DEPOSITO/")).hasSize(1);
        assertThat(objetosSob(pasta + "OUTROS/")).isEmpty();
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .singleElement()
                .satisfies(documento -> {
                    assertThat(documento.getCategoria()).isEqualTo(CategoriaDocumento.DEPOSITO);
                    assertThat(documento.getExtensao()).isEqualTo("txt");
                });
    }

    @Test
    void deveDevolver400SemCategoriaENaoGravarLinhaNemObjeto() throws Exception {
        Cenario cenario = criarCenario("014");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("categoria"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique se o ficheiro é para publicação ou depósito."));

        assertSemDocumentos(cenario);
    }

    @Test
    void deveDevolver400QuandoACategoriaEInvalida() throws Exception {
        Cenario cenario = criarCenario("015");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("anexo.pdf"))
                        .param("categoria", "OUTROS")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("categoria"));

        assertSemDocumentos(cenario);
    }

    @Test
    void deveDevolver400QuandoACategoriaNaoCorrespondeAoItem() throws Exception {
        Cenario cenario = criarCenario("701");
        DocumentoConfig itemDeposito = documentoConfigRepository.saveAndFlush(new DocumentoConfig(
                cenario.documentoConfig().getInstrumentoProcedimentoConfig(), "ANEXO", "Anexo", "Anexo",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 2));

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("anexo.pdf"))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", itemDeposito.getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("categoria"))
                .andExpect(jsonPath("$.campos[0].erro").value("A categoria não corresponde à do item."));
        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("categoria", "DEPOSITO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].erro").value("A categoria não corresponde à do item."));

        assertSemDocumentos(cenario);
    }

    @Test
    void deveGravarSobPublicacaoUmDocumentoSemItemNaSeccaoOutros() throws Exception {
        Cenario cenario = criarCenario("702");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("anexo.pdf"))
                        .param("categoria", "PUBLICACAO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value("PUBLICACAO"))
                .andExpect(jsonPath("$.documentoConfigId").doesNotExist());

        String pasta = "processos/" + cenario.processo().getNumeroProcesso() + "/";
        assertThat(objetosSob(pasta + "PUBLICACAO/")).hasSize(1);
    }

    @Test
    void deveGuardarOVersionIdNoUploadENaSubstituicao() throws Exception {
        Cenario cenario = criarCenario("018");
        doAnswer(consumindoOCorpo(respostaDoStorage("versao-upload")))
                .doAnswer(consumindoOCorpo(respostaDoStorage("versao-substituicao")))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        assertThat(processoDocumentoRepository.findById(documentoId).orElseThrow().getVersaoObjeto())
                .isEqualTo("versao-upload");

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + documentoId)
                        .file(ficheiro("regulamento_v2.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk());

        assertThat(processoDocumentoRepository.findById(documentoId).orElseThrow().getVersaoObjeto())
                .isEqualTo("versao-substituicao");
    }

    @Test
    void deveRevogarOsLinksPublicosDoDocumentoSubstituido() throws Exception {
        Cenario cenario = criarCenario("019");
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        DocumentoLinkPublico link = documentoLinkPublicoRepository.saveAndFlush(new DocumentoLinkPublico(
                processoDocumentoRepository.findById(documentoId).orElseThrow(), DestinoLinkPublico.INCM,
                UUID.randomUUID(), Instant.now()));

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + documentoId)
                        .file(ficheiro("regulamento_v2.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk());

        DocumentoLinkPublico revogado = documentoLinkPublicoRepository.findById(link.getId()).orElseThrow();
        assertThat(revogado.getRevogadoEm()).isNotNull();
        assertThat(revogado.getMotivoRevogacao()).isEqualTo(MotivoRevogacaoLink.SUBSTITUICAO);
    }

    @Test
    void deveRemoverODocumentoComLinksPublicos() throws Exception {
        Cenario cenario = criarCenario("020");
        String resposta = mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        ProcessoDocumento documento = processoDocumentoRepository.findById(documentoId).orElseThrow();
        DocumentoLinkPublico revogado = new DocumentoLinkPublico(documento, DestinoLinkPublico.INCM, UUID.randomUUID(),
                Instant.now());
        revogado.revogar(MotivoRevogacaoLink.SUBSTITUICAO, Instant.now());
        documentoLinkPublicoRepository.saveAndFlush(revogado);
        documentoLinkPublicoRepository.saveAndFlush(new DocumentoLinkPublico(documento, DestinoLinkPublico.INCM,
                UUID.randomUUID(), Instant.now()));

        mockMvc.perform(MockMvcRequestBuilders.delete(url(cenario.processo()) + "/" + documentoId)
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());

        assertThat(processoDocumentoRepository.findById(documentoId)).isEmpty();
        assertThat(documentoLinkPublicoRepository.findByProcessoDocumento_Id(documentoId)).isEmpty();
    }

    @Test
    void deveDevolver400ESemRastoQuandoONomeEstaForaDasRegras() throws Exception {
        Cenario cenario = criarCenario("801");
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("plano final.pdf", RegrasNomeFicheiro.MSG_CARACTERES);
        casos.put("plano-final.pdf", RegrasNomeFicheiro.MSG_CARACTERES);
        casos.put("alteração.pdf", RegrasNomeFicheiro.MSG_CARACTERES);
        casos.put("123456789012345678901.pdf", RegrasNomeFicheiro.MSG_CARACTERES);
        casos.put(".pdf", RegrasNomeFicheiro.MSG_CARACTERES);
        casos.put("plano.v2.pdf", RegrasNomeFicheiro.MSG_PONTO);

        for (Map.Entry<String, String> caso : casos.entrySet()) {
            mockMvc.perform(multipart(url(cenario.processo()))
                            .file(ficheiro(caso.getKey()))
                            .param("categoria", "PUBLICACAO")
                            .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                            .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DOC_NOME_INVALIDO"))
                    .andExpect(jsonPath("$.mensagem").value("Documento inválido."))
                    .andExpect(jsonPath("$.campos[0].path").value("ficheiro"))
                    .andExpect(jsonPath("$.campos[0].erro").value(caso.getValue()))
                    .andExpect(jsonPath("$.traceId").exists());
        }

        assertThat(processoDocumentoRepository.findAll())
                .noneMatch(documento -> documento.getProcesso().getId().equals(cenario.processo().getId()));
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    @Test
    void deveDevolver403AntesDeValidarONome() throws Exception {
        Cenario cenario = criarCenario("802");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro 802", "outro802@cm-doc.pt", "INCM"));

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("plano final.pdf"))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    private DocumentoConfig criarItemSgifr(Cenario cenario) {
        DocumentoConfig item = criarItem(cenario, "PLANTA_SGIFR", CategoriaDocumento.DEPOSITO,
                TipoElemento.OUTROS, "[\"tif\",\"zip\"]");
        item.setRegraNomenclatura(RegraNomenclatura.SGIFR);
        return documentoConfigRepository.saveAndFlush(item);
    }

    @Test
    void deveDevolver400ComAMensagemDoSgifrESemRastoQuandoONomeNumItemSgifrEstaForaDoPadrao() throws Exception {
        Cenario cenario = criarCenario("810");
        DocumentoConfig item = criarItemSgifr(cenario);
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("PSA-AML-RVF.tif", RegrasNomeFicheiro.MSG_SGIFR);
        casos.put("PSAAMLRVF.tif", RegrasNomeFicheiro.MSG_SGIFR);
        casos.put("PSA__RVF.tif", RegrasNomeFicheiro.MSG_SGIFR);
        casos.put("Região_AML_RVF.tif", RegrasNomeFicheiro.MSG_SGIFR);
        casos.put("PSA_AML_RVFGC1234.tif", RegrasNomeFicheiro.MSG_SGIFR);
        casos.put("PSA.AML_RVF.tif", RegrasNomeFicheiro.MSG_PONTO);

        for (Map.Entry<String, String> caso : casos.entrySet()) {
            carregarNoItem(cenario, item, caso.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DOC_NOME_INVALIDO"))
                    .andExpect(jsonPath("$.mensagem").value("Documento inválido."))
                    .andExpect(jsonPath("$.campos[0].path").value("ficheiro"))
                    .andExpect(jsonPath("$.campos[0].erro").value(caso.getValue()));
        }

        assertSemDocumentos(cenario);
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    @Test
    void deveAceitarNumItemSgifrONomeNoPadraoEUmZipComOutroNome() throws Exception {
        Cenario cenario = criarCenario("811");
        DocumentoConfig item = criarItemSgifr(cenario);

        carregarNoItem(cenario, item, "PSA_AML_RVF.tif").andExpect(status().isCreated());
        carregarNoItem(cenario, item, "Plantas do PSA.zip").andExpect(status().isCreated());
    }

    @Test
    void deveAplicarARegraDoItemNaSubstituicaoEAGeralNoBaldeOutros() throws Exception {
        Cenario cenario = criarCenario("812");
        DocumentoConfig item = criarItemSgifr(cenario);
        String resposta = carregarNoItem(cenario, item, "PSA_AML_RVF.tif")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + documentoId)
                        .file(ficheiro("planta_v2.tif"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_NOME_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value(RegrasNomeFicheiro.MSG_SGIFR));
        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + documentoId)
                        .file(ficheiro("PSA_AML_RVF2.tif"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk());

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("relatorio.pdf"))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("plano-final.pdf"))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].erro").value(RegrasNomeFicheiro.MSG_CARACTERES));
    }

    private DocumentoConfig criarItem(Cenario cenario, String codigo, CategoriaDocumento categoria,
                                      TipoElemento tipoElemento, String extensoes) {
        FormatoDocumento formato = categoria == CategoriaDocumento.PUBLICACAO
                ? FormatoDocumento.EDITAVEL : FormatoDocumento.NAO_EDITAVEL;
        return documentoConfigRepository.saveAndFlush(new DocumentoConfig(
                cenario.documentoConfig().getInstrumentoProcedimentoConfig(), codigo, codigo, codigo, categoria,
                tipoElemento, formato, extensoes, 2));
    }

    private ResultActions carregarNoItem(Cenario cenario, DocumentoConfig item, String nome) throws Exception {
        return mockMvc.perform(multipart(url(cenario.processo()))
                .file(ficheiro(nome))
                .param("categoria", item.getCategoria().name())
                .param("documentoConfigId", item.getId().toString())
                .with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    @Test
    void deveDevolver400DocExtensaoNaoAceiteSemLinhaNemObjetoQuandoAExtensaoNaoEstaNoItem() throws Exception {
        Cenario cenario = criarCenario("003");

        carregarNoItem(cenario, cenario.documentoConfig(), "script.exe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"))
                .andExpect(jsonPath("$.mensagem").value("Extensão não aceite para este documento."))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"))
                .andExpect(jsonPath("$.campos[0].erro").value("Extensão não aceite para este documento."))
                .andExpect(jsonPath("$.traceId").exists());

        assertSemDocumentos(cenario);
    }

    @Test
    void deveAceitarDocxNumItemDePublicacaoDePecaEscrita() throws Exception {
        Cenario cenario = criarCenario("901");
        DocumentoConfig item = criarItem(cenario, "REGULAMENTO_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, "[\"doc\",\"docx\",\"pdf\"]");

        carregarNoItem(cenario, item, "regulamento.docx").andExpect(status().isCreated());
    }

    @Test
    void deveDevolver400ParaPublicacaoQuandoOItemAceitaUmaExtensaoForaDaTabela() throws Exception {
        Cenario cenario = criarCenario("902");
        DocumentoConfig item = criarItem(cenario, "ERRADO_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, "[\"pdf\",\"jpg\"]");

        carregarNoItem(cenario, item, "foto.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"))
                .andExpect(jsonPath("$.mensagem").value("Extensão não aceite para publicação."))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"));

        assertSemDocumentos(cenario);
    }

    @Test
    void deveAceitarSoZipNaPublicacaoDePecaGrafica() throws Exception {
        Cenario cenario = criarCenario("903");
        DocumentoConfig item = criarItem(cenario, "PLANTA_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, "[\"zip\"]");

        carregarNoItem(cenario, item, "planta.tif")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"));
        carregarNoItem(cenario, item, "planta.gpkg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_EXTENSAO_NAO_ACEITE"));
        assertSemDocumentos(cenario);

        carregarNoItem(cenario, item, "plantas.zip").andExpect(status().isCreated());
    }

    @Test
    void deveAceitarNoDepositoAExtensaoDoItemSemAColunaDePublicacao() throws Exception {
        Cenario cenario = criarCenario("904");
        DocumentoConfig item = criarItem(cenario, "DELIBERACAO_DEP", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, "[\"pdf\",\"jpg\",\"png\"]");

        carregarNoItem(cenario, item, "acta.jpg").andExpect(status().isCreated());
    }

    @Test
    void deveAceitarNumItemOutrosAExtensaoDoItem() throws Exception {
        Cenario cenario = criarCenario("905");
        DocumentoConfig item = criarItem(cenario, "DESENHO", CategoriaDocumento.PUBLICACAO, TipoElemento.OUTROS,
                "[\"dwg\"]");

        carregarNoItem(cenario, item, "desenho.dwg").andExpect(status().isCreated());
    }

    @Test
    void naoDeveVerificarAExtensaoNoBaldeOutros() throws Exception {
        Cenario cenario = criarCenario("906");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("programa.exe"))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void deveCompararAExtensaoSemMaiusculas() throws Exception {
        Cenario cenario = criarCenario("907");

        carregarNoItem(cenario, cenario.documentoConfig(), "REGULAMENTO.PDF").andExpect(status().isCreated());
    }

    @Test
    void deveDevolver403AntesDeValidarAExtensao() throws Exception {
        Cenario cenario = criarCenario("908");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro 908", "outro908@cm-doc.pt", "INCM"));

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("script.exe"))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = "EM_SUBMISSAO", mode = EnumSource.Mode.EXCLUDE)
    void deveDevolver409QuandoProcessoJaNaoEstaEmSubmissao(EstadoProcesso estado) throws Exception {
        Cenario cenario = criarCenario(String.format("%03d", 100 + estado.ordinal()));
        Processo processo = cenario.processo();
        processo.setEstado(estado);
        processoRepository.saveAndFlush(processo);

        mockMvc.perform(multipart(url(processo))
                        .file(ficheiro("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));
    }

    @Test
    void deveDevolver403QuandoQuemCarregaNaoEOAutor() throws Exception {
        Cenario cenario = criarCenario("005");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro 005", "outro005@cm-doc.pt", "INCM"));

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void naoDeveReavaliarSequenciamentoNumProcessoQueJaExiste() throws Exception {
        Cenario cenario = criarCenario("012");
        InstrumentoProcedimentoConfig anterior = cenario.processo().getInstrumentoProcedimentoConfig();
        InstrumentoProcedimentoConfig seguinte = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                anterior.getInstrumentoDinamicaConfig(), tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento("PROC-DOC-012B", "Procedimento 012B")), 2, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, seguinte));
        // O anterior, obrigatório, não está PUBLICADO: o sequenciamento falharia se fosse reavaliado.
        Processo processo = processoRepository.saveAndFlush(
                new Processo(cenario.processo().getDinamica(), seguinte, cenario.autor(), cenario.processo().getEntidade()));

        mockMvc.perform(multipart(url(processo))
                        .file(ficheiro("anexo.pdf"))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void deveDevolver401SemToken() throws Exception {
        Cenario cenario = criarCenario("009");

        mockMvc.perform(multipart(url(cenario.processo())).file(ficheiro("regulamento.pdf")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveDevolver404QuandoProcessoEDeOutraEntidade() throws Exception {
        Cenario cenario = criarCenario("010");
        Cenario outraEntidade = criarCenario("011");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .with(jwtDe(outraEntidade.entidade(), outraEntidade.autor().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));

        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deveDevolver400QuandoOFicheiroPassaOLimitePorFicheiroDoItem(CapturedOutput saida) throws Exception {
        Cenario cenario = criarCenario("007");
        limitarItem(cenario.documentoConfig(), MB, null, null);

        carregarNoItem(cenario, ficheiroDe(2 * MB, "Planta_secreta.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TAMANHO_EXCEDIDO"))
                .andExpect(jsonPath("$.mensagem").value("O ficheiro excede o tamanho máximo permitido."))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"))
                .andExpect(jsonPath("$.campos[0].erro").value("Limite por ficheiro: 1 MB."));

        assertSemDocumentos(cenario);
        assertThat(saida.getAll())
                .contains("processoId=" + cenario.processo().getId())
                .contains("motivo=FICHEIRO")
                .doesNotContain("Planta_secreta")
                .doesNotContain(cenario.autor().getEmail());
    }

    @Test
    void deveRecusarOQueNaoCabeNoTotalDoItemEAceitarOQueCabe() throws Exception {
        Cenario cenario = criarCenario("201");
        limitarItem(cenario.documentoConfig(), null, 3 * MB, null);
        jaCarregado(cenario, cenario.documentoConfig(), 2 * MB);

        carregarNoItem(cenario, ficheiroDe(2 * MB, "peca.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TAMANHO_EXCEDIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value("Limite total do item: 3 MB."));
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).hasSize(1);
        assertThat(objetosSob(pasta(cenario))).isEmpty();

        carregarNoItem(cenario, ficheiroDe(MB, "peca.pdf")).andExpect(status().isCreated());
    }

    @Test
    void deveRecusarOFicheiroAMaisNoItemAntesDeGravarQualquerByte() throws Exception {
        Cenario cenario = criarCenario("202");
        limitarItem(cenario.documentoConfig(), null, null, 2);
        jaCarregado(cenario, cenario.documentoConfig(), 10);
        jaCarregado(cenario, cenario.documentoConfig(), 10);

        carregarNoItem(cenario, ficheiro("regulamento.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TAMANHO_EXCEDIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value("O item já tem o número máximo de ficheiros (2)."));

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        assertThat(intencaoRepository.findAll())
                .noneMatch(intencao -> intencao.getChaveObjeto().startsWith(pasta(cenario)));
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).hasSize(2);
    }

    @Test
    void deveRecusarNoutroItemENosOutrosOQueJaNaoCabeNoProcesso() throws Exception {
        Cenario cenario = criarCenario("203");
        limitarProcesso(cenario.processo(), 5 * MB);
        jaCarregado(cenario, null, 4 * MB);

        carregarNoItem(cenario, ficheiroDe(2 * MB, "peca.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].erro").value("Limite total do processo: 5 MB."));
        carregarNosOutros(cenario, ficheiroDe(2 * MB, "peca.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_TAMANHO_EXCEDIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value("Limite total do processo: 5 MB."));

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).hasSize(1);
        assertThat(objetosSob(pasta(cenario))).isEmpty();
    }

    @Test
    void comOsLimitesDoItemVaziosSoDeveContarOLimiteDoProcesso() throws Exception {
        Cenario cenario = criarCenario("204");
        limitarItem(cenario.documentoConfig(), null, null, null);
        jaCarregado(cenario, cenario.documentoConfig(), 3 * MB);
        jaCarregado(cenario, cenario.documentoConfig(), 3 * MB);

        carregarNoItem(cenario, ficheiroDe(2 * MB, "peca.pdf")).andExpect(status().isCreated());

        limitarProcesso(cenario.processo(), 9 * MB);
        carregarNoItem(cenario, ficheiroDe(2 * MB, "peca.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].erro").value("Limite total do processo: 9 MB."));
    }

    @Test
    void naoDeveContarOsFicheirosQueJaPassaramParaSubstituidos() throws Exception {
        Cenario cenario = criarCenario("205");
        limitarItem(cenario.documentoConfig(), null, 3 * MB, 1);
        processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), cenario.documentoConfig(),
                CategoriaDocumento.PUBLICACAO, "pdf", "antigo.pdf",
                pasta(cenario) + "SUBSTITUIDOS/" + UUID.randomUUID() + "-antigo.pdf", "a".repeat(64), "v1", 3 * MB,
                cenario.autor()));

        carregarNoItem(cenario, ficheiroDe(2 * MB, "peca.pdf")).andExpect(status().isCreated());
    }

    @Test
    void naoDeveHaverTetoGlobalPorFicheiro() throws Exception {
        assertThat(applicationContext.getEnvironment().getProperty("app.documentos.tamanho-maximo")).isNull();

        Cenario cenario = criarCenario("206");
        limitarItem(cenario.documentoConfig(), 500 * MB, 2048 * MB, 10);
        limitarProcesso(cenario.processo(), 5120 * MB);

        carregarNoItem(cenario, ficheiroDe(3 * MB, "peca.pdf"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tamanhoBytes").value(3 * MB));
    }

    @Test
    void deveGravarSoUmDeDoisUploadsSimultaneosQueJuntosPassamOLimiteDoProcesso() throws Exception {
        Cenario cenario = criarCenario("207");
        limitarProcesso(cenario.processo(), 5 * MB);
        // Os dois só gravam depois de ambos terem passado a verificação inicial, como num upload simultâneo real.
        CyclicBarrier ambosAGravar = new CyclicBarrier(2);
        doAnswer(invocacao -> {
            ambosAGravar.await(30, TimeUnit.SECONDS);
            return invocacao.callRealMethod();
        }).when(minioClient).putObject(any(PutObjectArgs.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> pedidos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                pedidos.add(pool.submit(() -> carregarNosOutros(cenario, ficheiroDe(3 * MB, "peca.pdf")).andReturn()));
            }
            List<MvcResult> resultados = new ArrayList<>();
            for (Future<MvcResult> pedido : pedidos) {
                resultados.add(pedido.get(60, TimeUnit.SECONDS));
            }

            assertThat(resultados).extracting(resultado -> resultado.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 400);
            MvcResult recusado = resultados.stream().filter(r -> r.getResponse().getStatus() == 400).findFirst()
                    .orElseThrow();
            JsonNode corpo = objectMapper.readTree(recusado.getResponse().getContentAsString());
            assertThat(corpo.get("codigo").asText()).isEqualTo("DOC_TAMANHO_EXCEDIDO");
            assertThat(corpo.get("campos").get(0).get("erro").asText()).isEqualTo("Limite total do processo: 5 MB.");

            List<ProcessoDocumento> gravados = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(
                    cenario.processo().getId());
            assertThat(gravados).hasSize(1);
            assertThat(objetosSob(pasta(cenario))).containsExactly(gravados.get(0).getCaminhoFicheiro());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void deveDevolver400QuandoAParteFicheiroVemAntesDasPartesDeTexto() throws Exception {
        Cenario cenario = criarCenario("703");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                        .with(UploadMultipartReal.ficheiroAntesDoTexto())
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                // As partes de texto depois do ficheiro não chegam a ser lidas: a categoria está em falta (BL-DOC-020).
                .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("categoria"));

        assertSemDocumentos(cenario);
    }

    @Test
    void deveDevolver400ParaFicheiroVazioENaoGravarObjeto() throws Exception {
        Cenario cenario = criarCenario("704");

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(new MockMultipartFile("ficheiro", "vazio.pdf", "application/pdf", new byte[0]))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"));

        assertSemDocumentos(cenario);
    }

    @Test
    void naoDeveTerMultipartResolverDoSpring() {
        assertThat(applicationContext.getBeanNamesForType(org.springframework.web.multipart.MultipartResolver.class))
                .isEmpty();
    }

    @Test
    void deveAutorizarAntesDeValidarTamanho() throws Exception {
        Cenario cenario = criarCenario("008");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro 008", "outro008@cm-doc.pt", "INCM"));

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiroGrande())
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoDeveDeixarObjetoNoBucketQuandoATransacaoFalhaDepoisDoUpload() throws Exception {
        Cenario cenario = criarCenario("006");
        doThrow(new IllegalStateException("falha simulada")).when(auditoriaService).registar(eq("PROCESSO"),
                anyString(), eq("CARREGAR_DOCUMENTO"), any(), any(), any(), any());

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isInternalServerError());

        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
        assertThat(processoDocumentoRepository.findAll())
                .noneMatch(documento -> documento.getProcesso().getId().equals(cenario.processo().getId()));
        assertThat(intencaoRepository.findAll())
                .anyMatch(intencao -> intencao.getOperacao() == OperacaoArmazenamento.GRAVACAO
                        && intencao.getChaveObjeto().startsWith("processos/" + cenario.processo().getNumeroProcesso()
                        + "/DEPOSITO/"));
    }

    @Test
    void deveDevolver500SemGravarLinhaQuandoOStorageFalha() throws Exception {
        Cenario cenario = criarCenario("013");
        doThrow(new java.io.IOException("storage em baixo")).when(minioClient).putObject(any(PutObjectArgs.class));

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro("regulamento.pdf"))
                        .param("categoria", "DEPOSITO")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("INTEGRACAO_INDISPONIVEL"));

        assertThat(processoDocumentoRepository.findAll())
                .noneMatch(documento -> documento.getProcesso().getId().equals(cenario.processo().getId()));
    }

    private void limitarItem(DocumentoConfig item, Long porFicheiro, Long total, Integer numero) {
        DocumentoConfig atual = documentoConfigRepository.findById(item.getId()).orElseThrow();
        atual.setTamanhoMaximoFicheiro(porFicheiro);
        atual.setTamanhoMaximoTotal(total);
        atual.setNumeroMaximoFicheiros(numero);
        documentoConfigRepository.saveAndFlush(atual);
    }

    private void limitarProcesso(Processo processo, long bytes) {
        InstrumentoProcedimentoConfig config = procedimentoConfigRepository
                .findById(processo.getInstrumentoProcedimentoConfig().getId()).orElseThrow();
        config.setTamanhoMaximoProcesso(bytes);
        procedimentoConfigRepository.saveAndFlush(config);
    }

    /** Só a linha: para os limites conta o que está na BD, não no bucket. */
    private void jaCarregado(Cenario cenario, DocumentoConfig item, long tamanhoBytes) {
        CategoriaDocumento categoria = item == null ? CategoriaDocumento.DEPOSITO : CategoriaDocumento.PUBLICACAO;
        processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(cenario.processo(), item, categoria, "pdf",
                "existente.pdf", pasta(cenario) + categoria + "/" + UUID.randomUUID() + "-existente.pdf",
                "a".repeat(64), "v1", tamanhoBytes, cenario.autor()));
    }

    private static MockMultipartFile ficheiroDe(long bytes, String nome) {
        return new MockMultipartFile("ficheiro", nome, "application/pdf", new byte[(int) bytes]);
    }

    private ResultActions carregarNoItem(Cenario cenario, MockMultipartFile ficheiro) throws Exception {
        return mockMvc.perform(multipart(url(cenario.processo()))
                .file(ficheiro)
                .param("categoria", "PUBLICACAO")
                .param("documentoConfigId", cenario.documentoConfig().getId().toString())
                .with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    private ResultActions carregarNosOutros(Cenario cenario, MockMultipartFile ficheiro) throws Exception {
        return mockMvc.perform(multipart(url(cenario.processo()))
                .file(ficheiro)
                .param("categoria", "DEPOSITO")
                .with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    private static String pasta(Cenario cenario) {
        return "processos/" + cenario.processo().getNumeroProcesso() + "/";
    }

    private void assertSemDocumentos(Cenario cenario) {
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).isEmpty();
        assertThat(objetosSob("processos/" + cenario.processo().getNumeroProcesso() + "/")).isEmpty();
    }

    /** O storage de teste lê o corpo até ao fim, como o real: sem isso o serviço vê um ficheiro vazio. */
    private static org.mockito.stubbing.Answer<ObjectWriteResponse> consumindoOCorpo(ObjectWriteResponse resposta) {
        return invocacao -> {
            invocacao.<PutObjectArgs>getArgument(0).stream().transferTo(java.io.OutputStream.nullOutputStream());
            return resposta;
        };
    }

    private static ObjectWriteResponse respostaDoStorage(String versionId) {
        return new ObjectWriteResponse(Headers.of(), BUCKET, null, "objeto", "etag", versionId);
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
        return java.util.stream.StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder()
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
