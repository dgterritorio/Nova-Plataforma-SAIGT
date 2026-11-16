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
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessoDocumentoConsultaControllerIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 conteúdo para download".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
                new TipoInstrumento("DOCC-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DOCC-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig documentoConfig = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"pdf\"]", 1));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("508000" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-docc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOCC/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        return new Cenario(processo, autor, entidade, documentoConfig);
    }

    private static RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    /** Perfil de consulta, sem ator: não passaria na Camada 3. */
    private static RequestPostProcessor jwtVisitante() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "VISITANTE"));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos";
    }

    private UUID carregar(Cenario cenario, String nome, boolean comSlot) throws Exception {
        var pedido = multipart(url(cenario.processo()))
                .file(new MockMultipartFile("ficheiro", nome, "application/pdf", CONTEUDO))
                .param("categoria", comSlot ? "PUBLICACAO" : "DEPOSITO")
                .with(jwtDe(cenario.entidade(), cenario.autor().getId()));
        if (comSlot) {
            pedido.param("documentoConfigId", cenario.documentoConfig().getId().toString());
        }
        String resposta = mockMvc.perform(pedido).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    @Test
    void deveDevolverListaVaziaQuandoProcessoNaoTemDocumentos() throws Exception {
        Cenario cenario = criarCenario("001");

        mockMvc.perform(get(url(cenario.processo())).with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void deveListarSoOsDocumentosDoProcessoComCategoriaEAutor() throws Exception {
        Cenario cenario = criarCenario("002");
        Cenario outro = criarCenario("003");
        UUID regulamento = carregar(cenario, "regulamento.pdf", true);
        UUID anexo = carregar(cenario, "anexo.pdf", false);
        carregar(outro, "alheio.pdf", false);

        String resposta = mockMvc.perform(get(url(cenario.processo()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].caminhoFicheiro").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode lista = objectMapper.readTree(resposta).get("content");
        JsonNode primeiro = lista.get(0);
        JsonNode segundo = lista.get(1);
        assertThat(primeiro.get("id").asText()).isEqualTo(regulamento.toString());
        assertThat(primeiro.get("nomeFicheiro").asText()).isEqualTo("regulamento.pdf");
        assertThat(primeiro.get("categoria").asText()).isEqualTo("PUBLICACAO");
        assertThat(primeiro.get("extensao").asText()).isEqualTo("pdf");
        assertThat(primeiro.get("origem").asText()).isEqualTo("CARREGADO");
        assertThat(primeiro.get("bloqueadoEdicao").asBoolean()).isFalse();
        assertThat(primeiro.get("documentoConfigId").asLong()).isEqualTo(cenario.documentoConfig().getId());
        assertThat(primeiro.get("tamanhoBytes").asLong()).isEqualTo(CONTEUDO.length);
        assertThat(primeiro.get("criadoEm").asText()).isNotBlank();
        assertThat(primeiro.get("criadoPorId").asText()).isEqualTo(cenario.autor().getId().toString());
        assertThat(primeiro.get("criadoPorNome").asText()).isEqualTo("Autora 002");
        assertThat(segundo.get("id").asText()).isEqualTo(anexo.toString());
        assertThat(segundo.get("categoria").asText()).isEqualTo("DEPOSITO");
        assertThat(segundo.get("documentoConfigId").isNull()).isTrue();
    }

    @Test
    void deveListarParaPerfilDeConsultaSemAtor() throws Exception {
        Cenario cenario = criarCenario("004");
        carregar(cenario, "regulamento.pdf", true);

        mockMvc.perform(get(url(cenario.processo())).with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].categoria").value("PUBLICACAO"))
                .andExpect(jsonPath("$.content[0].extensao").value("pdf"))
                .andExpect(jsonPath("$.content[0].origem").value("CARREGADO"))
                .andExpect(jsonPath("$.content[0].bloqueadoEdicao").value(false));
    }

    private void guardarFicheiros(Cenario cenario, int quantidade) {
        for (int i = 0; i < quantidade; i++) {
            processoDocumentoRepository.save(documento(cenario, null, CategoriaDocumento.DEPOSITO, "outro-" + i + ".pdf"));
        }
        processoDocumentoRepository.flush();
    }

    private ProcessoDocumento documento(Cenario cenario, DocumentoConfig item, CategoriaDocumento categoria, String nome) {
        return new ProcessoDocumento(cenario.processo(), item, categoria, "pdf", nome,
                "privado/teste/" + UUID.randomUUID() + "-" + nome, "c".repeat(64), "v1", 10L, cenario.autor());
    }

    private JsonNode listar(Cenario cenario, String query) throws Exception {
        String resposta = mockMvc.perform(get(url(cenario.processo()) + query)
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta);
    }

    @Test
    void devePaginarPorOmissaoComVinteLinhasPorOrdemDeCriacao() throws Exception {
        Cenario cenario = criarCenario("014");
        guardarFicheiros(cenario, 25);

        JsonNode pagina0 = listar(cenario, "");
        JsonNode pagina1 = listar(cenario, "?page=1");

        assertThat(pagina0.get("content")).hasSize(20);
        assertThat(pagina0.get("page").asInt()).isZero();
        assertThat(pagina0.get("size").asInt()).isEqualTo(20);
        assertThat(pagina0.get("totalElements").asLong()).isEqualTo(25);
        assertThat(pagina0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(pagina1.get("content")).hasSize(5);

        List<String> ids = new ArrayList<>();
        pagina0.get("content").forEach(linha -> ids.add(linha.get("id").asText()));
        pagina1.get("content").forEach(linha -> ids.add(linha.get("id").asText()));
        assertThat(ids).doesNotHaveDuplicates();

        // Como instantes e não como texto: «…656Z» viria depois de «…656492Z» na ordem alfabética.
        List<java.time.Instant> datas = new ArrayList<>();
        pagina0.get("content").forEach(linha -> datas.add(java.time.Instant.parse(linha.get("criadoEm").asText())));
        pagina1.get("content").forEach(linha -> datas.add(java.time.Instant.parse(linha.get("criadoEm").asText())));
        assertThat(datas).isSorted();
    }

    @Test
    void deveFiltrarPorCategoria() throws Exception {
        Cenario cenario = criarCenario("015");
        carregar(cenario, "regulamento.pdf", true);
        carregar(cenario, "anexo.pdf", false);
        carregar(cenario, "anexo2.pdf", false);

        JsonNode publicacao = listar(cenario, "?categoria=PUBLICACAO");
        JsonNode deposito = listar(cenario, "?categoria=DEPOSITO");

        assertThat(publicacao.get("totalElements").asLong()).isEqualTo(1);
        assertThat(publicacao.get("content").get(0).get("categoria").asText()).isEqualTo("PUBLICACAO");
        assertThat(deposito.get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void deveFiltrarPorItemEEstadoCombinados() throws Exception {
        Cenario cenario = criarCenario("016");
        ProcessoDocumento porValidar = documento(cenario, cenario.documentoConfig(), CategoriaDocumento.PUBLICACAO, "a.pdf");
        ProcessoDocumento validado = documento(cenario, cenario.documentoConfig(), CategoriaDocumento.PUBLICACAO, "b.pdf");
        validado.setEstadoValidacao(EstadoValidacao.VALIDADO);
        ProcessoDocumento semItem = documento(cenario, null, CategoriaDocumento.DEPOSITO, "c.pdf");
        processoDocumentoRepository.saveAllAndFlush(List.of(porValidar, validado, semItem));
        String item = cenario.documentoConfig().getId().toString();

        JsonNode soItem = listar(cenario, "?documentoConfigId=" + item);
        JsonNode itemEEstado = listar(cenario, "?documentoConfigId=" + item + "&estadoValidacao=POR_VALIDAR");
        JsonNode soEstado = listar(cenario, "?estadoValidacao=VALIDADO");

        assertThat(soItem.get("totalElements").asLong()).isEqualTo(2);
        assertThat(itemEEstado.get("totalElements").asLong()).isEqualTo(1);
        assertThat(itemEEstado.get("content").get(0).get("id").asText()).isEqualTo(porValidar.getId().toString());
        assertThat(soEstado.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void deveDevolverPaginaVaziaParaItemDeOutroProcedimento() throws Exception {
        Cenario cenario = criarCenario("017");
        Cenario outro = criarCenario("018");
        carregar(cenario, "regulamento.pdf", true);

        JsonNode pagina = listar(cenario, "?documentoConfigId=" + outro.documentoConfig().getId());

        assertThat(pagina.get("content")).isEmpty();
        assertThat(pagina.get("totalElements").asLong()).isZero();
    }

    @Test
    void deveDevolver400ParaParametrosInvalidos() throws Exception {
        Cenario cenario = criarCenario("019");
        String[][] casos = {
                {"size=101", "size"}, {"size=0", "size"}, {"page=-1", "page"},
                {"categoria=XPTO", "categoria"}, {"estadoValidacao=XPTO", "estadoValidacao"}};

        for (String[] caso : casos) {
            mockMvc.perform(get(url(cenario.processo()) + "?" + caso[0])
                            .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value(caso[1]));
        }
    }

    @Test
    void deveListarPaginaParaPerfilDeConsultaSemAtor() throws Exception {
        Cenario cenario = criarCenario("020");
        guardarFicheiros(cenario, 3);

        mockMvc.perform(get(url(cenario.processo()) + "?size=2").with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void deveDevolver404ComFiltrosQuandoProcessoEDeOutraEntidade() throws Exception {
        Cenario cenario = criarCenario("021");
        Cenario outraEntidade = criarCenario("022");

        mockMvc.perform(get(url(cenario.processo()) + "?categoria=PUBLICACAO&page=0&size=10")
                        .with(jwtDe(outraEntidade.entidade(), outraEntidade.autor().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveDevolver404NaListagemQuandoProcessoEDeOutraEntidade() throws Exception {
        Cenario cenario = criarCenario("005");
        Cenario outraEntidade = criarCenario("006");

        mockMvc.perform(get(url(cenario.processo()))
                        .with(jwtDe(outraEntidade.entidade(), outraEntidade.autor().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    private static String urlHistorico(Processo processo) {
        return url(processo) + "/historico";
    }

    private Processo novaOcorrencia(Cenario cenario, int ocorrencia) {
        Processo novo = new Processo(cenario.processo().getDinamica(), cenario.processo().getInstrumentoProcedimentoConfig(),
                cenario.autor(), cenario.processo().getEntidade());
        novo.setOcorrencia(ocorrencia);
        return processoRepository.saveAndFlush(novo);
    }

    private void guardarFicheirosDe(Cenario cenario, Processo dono, int quantidade) {
        for (int i = 0; i < quantidade; i++) {
            processoDocumentoRepository.save(new ProcessoDocumento(dono, null, CategoriaDocumento.DEPOSITO, "pdf",
                    "f" + i + ".pdf", "privado/teste/" + UUID.randomUUID(), "c".repeat(64), "v1", 10L, cenario.autor()));
        }
        processoDocumentoRepository.flush();
    }

    private JsonNode historico(Processo processo, RequestPostProcessor token) throws Exception {
        String resposta = mockMvc.perform(get(urlHistorico(processo)).with(token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta);
    }

    @Test
    void deveDevolverOHistoricoComAsDuasOcorrenciasPorOrdemEComOsDadosDeCadaUma() throws Exception {
        Cenario cenario = criarCenario("030");
        Processo primeira = cenario.processo();
        primeira.setEstado(EstadoProcesso.PUBLICADO);
        primeira.setNumeroDr("123/2026");
        primeira.setDataPublicacaoDr(LocalDate.of(2026, 10, 15));
        primeira.setLinkDr("https://diariodarepublica.pt/dr/detalhe/aviso/123-2026");
        processoRepository.saveAndFlush(primeira);
        Processo segunda = novaOcorrencia(cenario, 2);
        guardarFicheirosDe(cenario, primeira, 2);
        guardarFicheirosDe(cenario, segunda, 1);

        JsonNode processos = historico(segunda, jwtDe(cenario.entidade(), cenario.autor().getId())).get("processos");

        assertThat(processos).hasSize(2);
        JsonNode um = processos.get(0);
        JsonNode dois = processos.get(1);
        assertThat(um.get("processoId").asText()).isEqualTo(primeira.getId().toString());
        assertThat(um.get("ocorrencia").asInt()).isEqualTo(1);
        assertThat(um.get("estado").asText()).isEqualTo("PUBLICADO");
        assertThat(um.get("numeroDr").asText()).isEqualTo("123/2026");
        assertThat(um.get("dataPublicacaoDr").asText()).isEqualTo("2026-10-15");
        assertThat(um.get("linkDr").asText()).isEqualTo("https://diariodarepublica.pt/dr/detalhe/aviso/123-2026");
        assertThat(um.get("atual").asBoolean()).isFalse();
        assertThat(um.get("documentos")).hasSize(2);
        assertThat(dois.get("processoId").asText()).isEqualTo(segunda.getId().toString());
        assertThat(dois.get("ocorrencia").asInt()).isEqualTo(2);
        assertThat(dois.get("estado").asText()).isEqualTo("EM_SUBMISSAO");
        assertThat(dois.get("atual").asBoolean()).isTrue();
        assertThat(dois.get("documentos")).hasSize(1);
    }

    @Test
    void deveDevolverUmSoProcessoMarcadoComoAtualQuandoOProcedimentoTemUmaOcorrencia() throws Exception {
        Cenario cenario = criarCenario("031");

        JsonNode processos = historico(cenario.processo(), jwtDe(cenario.entidade(), cenario.autor().getId()))
                .get("processos");

        assertThat(processos).hasSize(1);
        assertThat(processos.get(0).get("atual").asBoolean()).isTrue();
        assertThat(processos.get(0).get("documentos")).isEmpty();
    }

    @Test
    void deveIncluirOProcessoAnuladoEONovoQualquerQueSejaOPedido() throws Exception {
        Cenario cenario = criarCenario("032");
        Processo anulado = cenario.processo();
        anulado.setEstado(EstadoProcesso.ANULADO);
        processoRepository.saveAndFlush(anulado);
        Processo novo = novaOcorrencia(cenario, 2);
        RequestPostProcessor token = jwtDe(cenario.entidade(), cenario.autor().getId());

        for (Processo pedido : List.of(anulado, novo)) {
            JsonNode processos = historico(pedido, token).get("processos");
            assertThat(processos).hasSize(2);
            assertThat(processos.get(0).get("estado").asText()).isEqualTo("ANULADO");
            assertThat(processos.get(1).get("estado").asText()).isEqualTo("EM_SUBMISSAO");
        }
    }

    @Test
    void naoDeveIncluirProcessosDeOutroProcedimentoDaMesmaDinamica() throws Exception {
        Cenario cenario = criarCenario("033");
        Cenario outro = criarCenario("034");
        TipoProcedimento outroTipo = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("OUTRO-DOCC-033", "Outro"));
        InstrumentoProcedimentoConfig outroConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(
                        cenario.processo().getInstrumentoProcedimentoConfig().getInstrumentoDinamicaConfig(), outroTipo, 2,
                        "SEGUNDA_H"));
        Processo doutroProcedimento = processoRepository.saveAndFlush(
                new Processo(cenario.processo().getDinamica(), outroConfig, cenario.autor(), cenario.processo().getEntidade()));
        guardarFicheirosDe(cenario, doutroProcedimento, 1);
        guardarFicheirosDe(outro, outro.processo(), 1);

        JsonNode processos = historico(cenario.processo(), jwtDe(cenario.entidade(), cenario.autor().getId()))
                .get("processos");

        assertThat(processos).hasSize(1);
        assertThat(processos.get(0).get("processoId").asText()).isEqualTo(cenario.processo().getId().toString());
        assertThat(processos.get(0).get("documentos")).isEmpty();
    }

    @Test
    void deveDevolverOHistoricoAoPerfilDeConsultaSemCaminhoFicheiro() throws Exception {
        Cenario cenario = criarCenario("035");
        guardarFicheirosDe(cenario, cenario.processo(), 1);

        mockMvc.perform(get(urlHistorico(cenario.processo())).with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processos[0].documentos.length()").value(1))
                .andExpect(jsonPath("$.processos[0].documentos[0].caminhoFicheiro").doesNotExist())
                .andExpect(jsonPath("$.processos[0].documentos[0].nomeFicheiro").value("f0.pdf"));
    }

    @Test
    void deveDevolver404NoHistoricoDeOutraEntidadeE401SemToken() throws Exception {
        Cenario cenario = criarCenario("036");
        Cenario outraEntidade = criarCenario("037");

        mockMvc.perform(get(urlHistorico(cenario.processo()))
                        .with(jwtDe(outraEntidade.entidade(), outraEntidade.autor().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        mockMvc.perform(get(urlHistorico(cenario.processo()))).andExpect(status().isUnauthorized());
    }

    @Test
    void deveFazerOMesmoNumeroDeConsultasIndependentementeDoNumeroDeProcessosEFicheiros() throws Exception {
        Cenario pequeno = criarCenario("038");
        guardarFicheirosDe(pequeno, pequeno.processo(), 1);
        Cenario grande = criarCenario("039");
        guardarFicheirosDe(grande, grande.processo(), 10);
        guardarFicheirosDe(grande, novaOcorrencia(grande, 2), 10);
        Processo terceira = novaOcorrencia(grande, 3);
        guardarFicheirosDe(grande, terceira, 10);
        historico(pequeno.processo(), jwtDe(pequeno.entidade(), pequeno.autor().getId()));

        long consultasPequeno = consultasPara(pequeno, pequeno.processo());
        long consultasGrande = consultasPara(grande, terceira);

        assertThat(consultasGrande).isEqualTo(consultasPequeno);
    }

    private long consultasPara(Cenario cenario, Processo processo) throws Exception {
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        JsonNode resposta = historico(processo, jwtDe(cenario.entidade(), cenario.autor().getId()));
        assertThat(resposta.get("processos")).isNotEmpty();
        return estatisticas.getPrepareStatementCount();
    }

    @Test
    void deveDevolver401SemToken() throws Exception {
        Cenario cenario = criarCenario("007");

        mockMvc.perform(get(url(cenario.processo()))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(url(cenario.processo()) + "/" + UUID.randomUUID() + "/descarga"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveEmitirTicketDeDescargaAoPerfilDeConsulta() throws Exception {
        Cenario cenario = criarCenario("008");
        UUID documentoId = carregar(cenario, "regulamento.pdf", true);

        mockMvc.perform(post(url(cenario.processo()) + "/" + documentoId + "/descarga").with(jwtVisitante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith(
                        "http://localhost:8080/api/v1/descargas/")));
    }

    @Test
    void deveDevolver404QuandoDocumentoEDeOutroProcesso() throws Exception {
        Cenario cenario = criarCenario("009");
        Cenario outro = criarCenario("010");
        UUID documentoAlheio = carregar(outro, "alheio.pdf", false);

        mockMvc.perform(post(url(cenario.processo()) + "/" + documentoAlheio + "/descarga")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveDevolver404NoDownloadQuandoProcessoEDeOutraEntidade() throws Exception {
        Cenario cenario = criarCenario("011");
        Cenario outraEntidade = criarCenario("012");
        UUID documentoId = carregar(cenario, "regulamento.pdf", true);

        mockMvc.perform(post(url(cenario.processo()) + "/" + documentoId + "/descarga")
                        .with(jwtDe(outraEntidade.entidade(), outraEntidade.autor().getId())))
                .andExpect(status().isNotFound());
    }
}
