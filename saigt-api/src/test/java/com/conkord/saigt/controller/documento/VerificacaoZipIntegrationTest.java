package com.conkord.saigt.controller.documento;

import com.conkord.saigt.configuration.StorageProperties;
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
import com.conkord.saigt.persistence.core.TipoEntidade;
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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectLockConfigurationArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verificação do conteúdo dos ZIP pelo endpoint real, sobre o RustFS, num bucket próprio com versionamento e Object
 * Lock, como em produção: um ZIP recusado tem de sair com todas as versões, e a versão lida é a que a gravação
 * devolveu. O caso sem versionamento só existe no teste unitário do {@code VerificadorZip}.
 */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "saigt.teste.bucket=" + VerificacaoZipIntegrationTest.BUCKET})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacaoZipIntegrationTest extends StorageTestcontainersBase {

    static final String BUCKET = "saigt-documentos-zip-teste";
    private static final String MARCA = "ZI";
    private static final String SERIE_NIPC = "5183020";
    private static final String PREFIXO_ERRO = "O ZIP contém pastas ou ficheiros com nome inválido: ";

    static {
        MinioClient cliente = MinioClient.builder()
                .endpoint("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3))
                .credentials(ACCESS_KEY, SECRET_KEY).build();
        try {
            if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
                cliente.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).objectLock(true).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StorageProperties storageProperties;

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

    @SpyBean
    private ArmazenamentoDocumentos armazenamento;

    private record Cenario(String codigo, Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig item) {

        /** Nome único do ZIP do cenário, para encontrar as suas chaves no bucket. */
        String nomeZip() {
            return "plantas_" + codigo + ".zip";
        }
    }

    @BeforeEach
    void confirmarQueOBucketTemObjectLock() throws Exception {
        assertThat(storageProperties.bucket()).isEqualTo(BUCKET);
        assertThat(minioClient.getObjectLockConfiguration(
                GetObjectLockConfigurationArgs.builder().bucket(BUCKET).build())).isNotNull();
    }

    @Test
    void deveAceitarUmZipValidoSemValidarONomeDoZipELerAVersaoGravada() throws Exception {
        Cenario cenario = criarCenario("01");

        ProcessoDocumento documento = carregarComSucesso(cenario, "Plantas finais.zip",
                ZipsDeTeste.comEntradas("ordenamento.gpkg", "legenda.pdf"));

        assertThat(documento.getNomeFicheiro()).isEqualTo("Plantas finais.zip");
        ArgumentCaptor<String> versao = ArgumentCaptor.forClass(String.class);
        verify(armazenamento, atLeastOnce()).abrir(eq(documento.getCaminhoFicheiro()), versao.capture(), anyLong(),
                anyLong());
        assertThat(versao.getAllValues()).allSatisfy(lida ->
                assertThat(lida).isNotBlank().isNotEqualTo("null").isEqualTo(documento.getVersaoObjeto()));
        assertThat(objetosCom(cenario, documento.getId().toString()))
                .containsExactly(documento.getCaminhoFicheiro() + "@" + documento.getVersaoObjeto());
    }

    @Test
    void deveRecusarUmZipComPastasSemDeixarLinhaNemObjeto() throws Exception {
        Cenario cenario = criarCenario("02");

        carregar(cenario, cenario.nomeZip(), ZipsDeTeste.comEntradas("dados/", "dados/plano.gpkg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_ZIP_INVALIDO"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"))
                .andExpect(jsonPath("$.campos[0].erro").value(PREFIXO_ERRO + "dados/, dados/plano.gpkg."));

        assertGravadoEApagado(cenario);
        assertSemDocumentosNemObjetos(cenario);
    }

    @Test
    void deveRecusarUmZipComNomesInvalidosNaRaiz() throws Exception {
        Cenario cenario = criarCenario("03");

        carregar(cenario, cenario.nomeZip(), ZipsDeTeste.comEntradas("planta final.tif", "planta.v2.tfw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_ZIP_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value(PREFIXO_ERRO + "planta final.tif, planta.v2.tfw."));

        assertGravadoEApagado(cenario);
        assertSemDocumentosNemObjetos(cenario);
    }

    @Test
    void deveRecusarComoIlegivelUmZipQueNaoEZip() throws Exception {
        Cenario cenario = criarCenario("04");

        carregar(cenario, cenario.nomeZip(), "%PDF-1.4 não sou um zip".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_ZIP_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value(PREFIXO_ERRO + "ficheiro ZIP ilegível."));

        assertGravadoEApagado(cenario);
        assertSemDocumentosNemObjetos(cenario);
    }

    @Test
    void deveManterODocumentoEOObjetoAnterioresQuandoASubstituicaoTrazUmZipInvalido() throws Exception {
        Cenario cenario = criarCenario("05");
        ProcessoDocumento original = carregarComSucesso(cenario, "plantas.zip",
                ZipsDeTeste.comEntradas("legenda.pdf"));

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + original.getId())
                        .file(ficheiro(cenario.nomeZip(), ZipsDeTeste.comEntradas("dados/plano.gpkg")))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_ZIP_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].erro").value(PREFIXO_ERRO + "dados/plano.gpkg."));

        ProcessoDocumento atual = processoDocumentoRepository.findById(original.getId()).orElseThrow();
        assertThat(atual.getCaminhoFicheiro()).isEqualTo(original.getCaminhoFicheiro());
        assertThat(atual.getHashSha256()).isEqualTo(original.getHashSha256());
        assertThat(atual.getVersaoObjeto()).isEqualTo(original.getVersaoObjeto());
        assertThat(objetosCom(cenario, original.getId().toString()))
                .containsExactly(original.getCaminhoFicheiro() + "@" + original.getVersaoObjeto());
        assertGravadoEApagado(cenario);
        assertThat(objetosCom(cenario, cenario.nomeZip())).isEmpty();
    }

    @Test
    void deveResponder403AntesDeGravarQuandoQuemCarregaNaoEOAutor() throws Exception {
        Cenario cenario = criarCenario("06");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro " + MARCA + "06",
                "outro" + MARCA.toLowerCase() + "06@cm-zip.pt", "INCM"));

        mockMvc.perform(multipart(url(cenario.processo()))
                        .file(ficheiro(cenario.nomeZip(), ZipsDeTeste.comEntradas("dados/plano.gpkg")))
                        .param("categoria", "PUBLICACAO")
                        .param("documentoConfigId", cenario.item().getId().toString())
                        .with(jwtDe(cenario.entidade(), outro.getId())))
                .andExpect(status().isForbidden());

        verify(armazenamento, never()).guardar(contains(cenario.nomeZip()), any(), anyString());
        assertSemDocumentosNemObjetos(cenario);
    }

    /** Sem linha e sem nenhuma versão nem marcador de eliminação do ZIP no bucket. */
    private void assertSemDocumentosNemObjetos(Cenario cenario) throws Exception {
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()))
                .isEmpty();
        assertThat(objetosCom(cenario, cenario.nomeZip())).isEmpty();
    }

    /** O ZIP chegou ao storage e foi apagado com todas as versões; sem isto "nenhum objeto" passaria sem gravar. */
    private void assertGravadoEApagado(Cenario cenario) {
        verify(armazenamento).guardar(contains(cenario.nomeZip()), any(), anyString());
        verify(armazenamento).removerTodasVersoes(contains(cenario.nomeZip()));
    }

    /** Só as chaves deste cenário: o id do documento ou o nome único do ZIP fazem parte da chave. */
    private List<String> objetosCom(Cenario cenario, String marcador) throws Exception {
        return objetosSob(prefixo(cenario)).stream().filter(chave -> chave.contains(marcador)).toList();
    }

    private Cenario criarCenario(String numero) {
        String codigo = MARCA + numero;
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DOCZ-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(
                new TipoDinamica("ELAB-DOCZ-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCZ-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig item = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "PLANTAS_ZIP", "Plantas", "Plantas",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                        "[\"zip\"]", 1));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(SERIE_NIPC + numero, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo.toLowerCase() + "@cm-zip.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOCZ/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(
                new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        return new Cenario(codigo, processo, autor, entidade, item);
    }

    private ResultActions carregar(Cenario cenario, String nome, byte[] conteudo) throws Exception {
        return mockMvc.perform(multipart(url(cenario.processo()))
                .file(ficheiro(nome, conteudo))
                .param("categoria", "PUBLICACAO")
                .param("documentoConfigId", cenario.item().getId().toString())
                .with(jwtDe(cenario.entidade(), cenario.autor().getId())));
    }

    private ProcessoDocumento carregarComSucesso(Cenario cenario, String nome, byte[] conteudo) throws Exception {
        String resposta = carregar(cenario, nome, conteudo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return processoDocumentoRepository.findById(UUID.fromString(objectMapper.readTree(resposta).get("id").asText()))
                .orElseThrow();
    }

    private static MockMultipartFile ficheiro(String nome, byte[] conteudo) {
        return new MockMultipartFile("ficheiro", nome, "application/zip", conteudo);
    }

    private RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private static String prefixo(Cenario cenario) {
        return "processos/" + cenario.processo().getNumeroProcesso() + "/";
    }

    /** Versões e marcadores de eliminação sob o prefixo, como `chave@versionId`. */
    private List<String> objetosSob(String prefixo) throws Exception {
        List<String> resultado = new ArrayList<>();
        for (Result<Item> resposta : minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(storageProperties.bucket()).prefix(prefixo).recursive(true).includeVersions(true).build())) {
            Item item = resposta.get();
            resultado.add(item.objectName() + "@" + item.versionId());
        }
        return resultado;
    }
}
