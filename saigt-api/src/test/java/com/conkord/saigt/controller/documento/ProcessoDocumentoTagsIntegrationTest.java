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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tags de depósito obrigatório num ficheiro (BL-DOC-021, DB-DOC-003): upload, estrutura, listagem, PUT e DELETE. */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessoDocumentoTagsIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 conteúdo com tags".getBytes(StandardCharsets.UTF_8);
    private static final String MSG_TAGS = "Só é possível associar o ficheiro a itens de depósito obrigatório.";
    private static final String MSG_TAGS_ORIGEM =
            "Só um ficheiro de depósito obrigatório pode ser associado a outros itens.";
    private static final String MSG_TAGS_EXTENSAO = "A extensão do ficheiro não é aceite num dos itens associados.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
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

    /** A e B: depósito obrigatório; publicacao: item de publicação; opcional: depósito não obrigatório. */
    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, InstrumentoProcedimentoConfig procedimento,
                           DocumentoConfig a, DocumentoConfig b, DocumentoConfig publicacao, DocumentoConfig opcional) {
    }

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("TAG-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-TAG-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-TAG-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        DocumentoConfig a = item(procedimento, "RELATORIO_DEP", CategoriaDocumento.DEPOSITO, true, 1);
        DocumentoConfig b = item(procedimento, "PARECERES_DEP", CategoriaDocumento.DEPOSITO, true, 2);
        DocumentoConfig publicacao = item(procedimento, "REGULAMENTO_PUB", CategoriaDocumento.PUBLICACAO, true, 3);
        DocumentoConfig opcional = item(procedimento, "ANEXO_DEP", CategoriaDocumento.DEPOSITO, false, 4);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("507100" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-tag.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/TAG/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Elaboração do PDM", autor));
        Processo novo = new Processo(dinamica, procedimento, autor, entidade);
        // Num PLANO a estrutura só mostra os itens dos elementos afetados (FR-168).
        novo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        Processo processo = processoRepository.saveAndFlush(novo);
        limparObjetos("processos/" + processo.getNumeroProcesso() + "/");
        return new Cenario(processo, autor, entidade, procedimento, a, b, publicacao, opcional);
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, CategoriaDocumento categoria,
                                 boolean obrigatorio, int ordem) {
        return item(procedimento, codigo, categoria, obrigatorio, ordem, "[\"pdf\"]");
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo, CategoriaDocumento categoria,
                                 boolean obrigatorio, int ordem, String extensoes) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, "Item " + codigo, "Separador " + ordem,
                categoria, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, extensoes, ordem);
        item.setObrigatorio(obrigatorio);
        return documentoConfigRepository.saveAndFlush(item);
    }

    private static RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private static MockMultipartFile ficheiro(String nome) {
        return new MockMultipartFile("ficheiro", nome, "application/pdf", CONTEUDO);
    }

    private ResultActions carregar(Cenario cenario, DocumentoConfig item, UUID utilizadorId, String... tags)
            throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart(url(cenario.processo())).file(ficheiro("ficheiro.pdf"));
        pedido.param("categoria", item == null ? "DEPOSITO" : item.getCategoria().name());
        if (item != null) {
            pedido.param("documentoConfigId", item.getId().toString());
        }
        if (tags.length > 0) {
            pedido.param("tags", tags);
        }
        return mockMvc.perform(pedido.with(jwtDe(cenario.entidade(), utilizadorId)));
    }

    private ResultActions carregar(Cenario cenario, DocumentoConfig item, String... tags) throws Exception {
        return carregar(cenario, item, cenario.autor().getId(), tags);
    }

    private UUID idDe(ResultActions resposta) throws Exception {
        return UUID.fromString(objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString())
                .get("id").asText());
    }

    private static String id(DocumentoConfig item) {
        return item.getId().toString();
    }

    private List<Long> tagsGravadas(UUID documentoId) {
        return jdbcTemplate.queryForList("SELECT documento_config_id FROM processo_documento_tag "
                + "WHERE processo_documento_id = ? ORDER BY documento_config_id", Long.class, documentoId);
    }

    private JsonNode estrutura(Cenario cenario) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()
                        + "/estrutura-documental").with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static JsonNode itemDaEstrutura(JsonNode estrutura, DocumentoConfig item) {
        for (JsonNode separador : estrutura.get("separadores")) {
            for (String caixa : List.of("publicacao", "deposito")) {
                for (JsonNode candidato : separador.get(caixa)) {
                    if (candidato.get("documentoConfigId").asLong() == item.getId()) {
                        return candidato;
                    }
                }
            }
        }
        throw new AssertionError("Item não encontrado na estrutura: " + item.getCodigo());
    }

    private static List<String> idsDosFicheiros(JsonNode item) {
        List<String> ids = new ArrayList<>();
        item.get("ficheiros").forEach(ficheiro -> ids.add(ficheiro.get("id").asText()));
        return ids;
    }

    private void assertSemDocumentos(Cenario cenario) {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processo_documento WHERE processo_id = ?",
                Long.class, cenario.processo().getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processo_documento_tag t JOIN processo_documento d "
                + "ON d.id = t.processo_documento_id WHERE d.processo_id = ?", Long.class, cenario.processo().getId())).isZero();
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

    @Test
    void deveAssociarATagEGravarALinhaDaTag() throws Exception {
        Cenario cenario = criarCenario("001");

        ResultActions resposta = carregar(cenario, cenario.a(), id(cenario.b()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentoConfigId").value(cenario.a().getId()))
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andExpect(jsonPath("$.tags[0]").value(cenario.b().getId()));

        assertThat(tagsGravadas(idDe(resposta))).containsExactly(cenario.b().getId());
    }

    @Test
    void deveDevolverTagsVaziasQuandoOFicheiroNaoTemTags() throws Exception {
        Cenario cenario = criarCenario("002");

        carregar(cenario, cenario.a())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void deveMostrarOFicheiroNosDoisItensSemNenhumPendente() throws Exception {
        Cenario cenario = criarCenario("003");
        UUID documentoId = idDe(carregar(cenario, cenario.a(), id(cenario.b())).andExpect(status().isCreated()));

        JsonNode estrutura = estrutura(cenario);

        JsonNode itemA = itemDaEstrutura(estrutura, cenario.a());
        JsonNode itemB = itemDaEstrutura(estrutura, cenario.b());
        assertThat(idsDosFicheiros(itemA)).containsExactly(documentoId.toString());
        assertThat(idsDosFicheiros(itemB)).containsExactly(documentoId.toString());
        assertThat(itemA.get("pendente").asBoolean()).isFalse();
        assertThat(itemB.get("pendente").asBoolean()).isFalse();
        // O ficheiro mantém o item principal, para o ecrã distinguir o ficheiro associado por tag.
        assertThat(itemB.get("ficheiros").get(0).get("documentoConfigId").asLong()).isEqualTo(cenario.a().getId());
        assertThat(itemB.get("ficheiros").get(0).get("tags").get(0).asLong()).isEqualTo(cenario.b().getId());
        List<String> pendentes = new ArrayList<>();
        estrutura.get("pendencias").forEach(pendencia -> pendentes.add(pendencia.get("codigo").asText()));
        assertThat(pendentes).containsExactly("REGULAMENTO_PUB");
    }

    @Test
    void deveRecusarTagDeItemDePublicacao() throws Exception {
        Cenario cenario = criarCenario("004");

        assertTagRecusada(cenario, carregar(cenario, cenario.a(), id(cenario.publicacao())));
    }

    @Test
    void deveRecusarTagDeItemDeDepositoOpcional() throws Exception {
        Cenario cenario = criarCenario("005");

        assertTagRecusada(cenario, carregar(cenario, cenario.a(), id(cenario.opcional())));
    }

    @Test
    void deveRecusarTagDeItemDeOutroProcedimento() throws Exception {
        Cenario cenario = criarCenario("006");
        Cenario outro = criarCenario("007");

        assertTagRecusada(cenario, carregar(cenario, cenario.a(), id(outro.b())));
    }

    @Test
    void deveRecusarTagDeItemInativo() throws Exception {
        Cenario cenario = criarCenario("008");
        DocumentoConfig inativo = item(cenario.procedimento(), "INATIVO_DEP", CategoriaDocumento.DEPOSITO, true, 5);
        inativo.setAtivo(false);
        documentoConfigRepository.saveAndFlush(inativo);

        assertTagRecusada(cenario, carregar(cenario, cenario.a(), id(inativo)));
    }

    @Test
    void deveRecusarTagInexistenteOuNaoNumerica() throws Exception {
        Cenario cenario = criarCenario("009");

        assertTagRecusada(cenario, carregar(cenario, cenario.a(), id(cenario.b()), "999999"));
        assertTagRecusada(cenario, carregar(cenario, cenario.a(), "abc"));
    }

    private void assertTagRecusada(Cenario cenario, ResultActions resposta) throws Exception {
        assertTagRecusada(cenario, resposta, MSG_TAGS);
    }

    private void assertTagRecusada(Cenario cenario, ResultActions resposta, String mensagem) throws Exception {
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("tags"))
                .andExpect(jsonPath("$.campos[0].erro").value(mensagem));
        assertSemDocumentos(cenario);
    }

    @Test
    void deveDescartarTagsRepetidasEATagDoItemPrincipal() throws Exception {
        Cenario cenario = criarCenario("010");

        ResultActions resposta = carregar(cenario, cenario.a(), id(cenario.b()), id(cenario.b()), id(cenario.a()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andExpect(jsonPath("$.tags[0]").value(cenario.b().getId()));

        assertThat(tagsGravadas(idDe(resposta))).containsExactly(cenario.b().getId());
    }

    // EF v1.5, Recolha de ficheiros: só os «ficheiros para depósito obrigatório» têm mais do que um tag.
    @Test
    void deveRecusarTagsNumFicheiroDoBaldeOutros() throws Exception {
        Cenario cenario = criarCenario("011");

        assertTagRecusada(cenario, carregar(cenario, null, id(cenario.b())), MSG_TAGS_ORIGEM);
    }

    @Test
    void deveRecusarTagsNumFicheiroDePublicacaoOuDeDepositoOpcional() throws Exception {
        Cenario cenario = criarCenario("020");

        assertTagRecusada(cenario, carregar(cenario, cenario.publicacao(), id(cenario.b())), MSG_TAGS_ORIGEM);
        assertTagRecusada(cenario, carregar(cenario, cenario.opcional(), id(cenario.b())), MSG_TAGS_ORIGEM);
    }

    @Test
    void deveRecusarTagDeItemQueNaoAceitaAExtensaoDoFicheiro() throws Exception {
        Cenario cenario = criarCenario("021");
        DocumentoConfig soImagens = item(cenario.procedimento(), "IMAGENS_DEP", CategoriaDocumento.DEPOSITO, true, 6,
                "[\"jpg\",\"png\"]");

        assertTagRecusada(cenario, carregar(cenario, cenario.a(), id(soImagens)), MSG_TAGS_EXTENSAO);
    }

    @Test
    void deveRecusarSubstituirPorUmFicheiroQueOItemDaTagNaoAceita() throws Exception {
        Cenario cenario = criarCenario("022");
        DocumentoConfig pdfOuJpg = item(cenario.procedimento(), "PDF_JPG_DEP", CategoriaDocumento.DEPOSITO, true, 6,
                "[\"pdf\",\"jpg\"]");
        UUID documentoId = idDe(carregar(cenario, pdfOuJpg, id(cenario.b())).andExpect(status().isCreated()));

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + documentoId)
                        .file(new MockMultipartFile("ficheiro", "novo.jpg", "image/jpeg", CONTEUDO))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_CLASSIFICACAO_INVALIDA"))
                .andExpect(jsonPath("$.campos[0].path").value("tags"))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_TAGS_EXTENSAO));
        assertThat(tagsGravadas(documentoId)).containsExactly(cenario.b().getId());
    }

    @Test
    void deveManterAsTagsAoSubstituirEApagaLasAoRemover() throws Exception {
        Cenario cenario = criarCenario("012");
        UUID documentoId = idDe(carregar(cenario, cenario.a(), id(cenario.b())).andExpect(status().isCreated()));

        mockMvc.perform(multipart(HttpMethod.PUT, url(cenario.processo()) + "/" + documentoId)
                        .file(ficheiro("novo.pdf"))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeFicheiro").value("novo.pdf"))
                .andExpect(jsonPath("$.tags[0]").value(cenario.b().getId()));
        assertThat(tagsGravadas(documentoId)).containsExactly(cenario.b().getId());

        mockMvc.perform(delete(url(cenario.processo()) + "/" + documentoId)
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNoContent());
        assertThat(tagsGravadas(documentoId)).isEmpty();
        assertThat(itemDaEstrutura(estrutura(cenario), cenario.b()).get("pendente").asBoolean()).isTrue();
    }

    @Test
    void deveDevolver403QuandoQuemCarregaComTagsNaoEOAutor() throws Exception {
        Cenario cenario = criarCenario("013");
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro 013", "outro013@cm-tag.pt", "INCM"));

        carregar(cenario, cenario.a(), outro.getId(), id(cenario.b()))
                .andExpect(status().isForbidden());

        assertSemDocumentos(cenario);
    }

    @Test
    void deveListarAsTagsDeCadaFicheiroSemUmaConsultaPorFicheiro() throws Exception {
        Cenario pequeno = criarCenario("014");
        carregar(pequeno, pequeno.a(), id(pequeno.b())).andExpect(status().isCreated());
        Cenario grande = criarCenario("015");
        for (int i = 0; i < 6; i++) {
            carregar(grande, grande.a(), id(grande.b())).andExpect(status().isCreated());
            carregar(grande, grande.opcional()).andExpect(status().isCreated());
        }
        listar(pequeno);

        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        JsonNode linhasPequeno = listar(pequeno);
        long consultasPequeno = estatisticas.getPrepareStatementCount();
        estatisticas.clear();
        JsonNode linhasGrande = listar(grande);
        long consultasGrande = estatisticas.getPrepareStatementCount();

        assertThat(linhasPequeno).hasSize(1);
        assertThat(linhasPequeno.get(0).get("tags").get(0).asLong()).isEqualTo(pequeno.b().getId());
        assertThat(linhasGrande).hasSize(12);
        linhasGrande.forEach(linha -> assertThat(linha.get("tags")).hasSize(
                linha.get("documentoConfigId").asLong() == grande.a().getId() ? 1 : 0));
        assertThat(consultasGrande).isEqualTo(consultasPequeno);
    }

    private JsonNode listar(Cenario cenario) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url(cenario.processo())).param("size", "100")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("content");
    }
}
