package com.conkord.saigt.controller.documento;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.CompressaoTiff;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.service.documento.ValidadorRegrasTiff;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** `regrasTiff` e `regraNomenclatura` no API-DOC-013. Na base partilhada: cada teste cria o seu Procedimento. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentoConfigRegrasIntegrationTest extends PostgresTestcontainersBase {

    private static final String URL = "/api/v1/documento-config";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private AuditoriaRepository auditoriaRepository;

    private Long procedimentoId;

    @BeforeEach
    void criarProcedimento() {
        String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("D" + sufixo, "Dinâmica"));
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("I" + sufixo, "Instrumento " + sufixo, "PROGRAMA"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("P" + sufixo, "Procedimento " + sufixo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        procedimentoId = procedimento.getId();
    }

    private static RequestPostProcessor comPerfil(String perfil) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil));
    }

    private static Map<String, Object> corpoAtualizacao(String tipoElemento) {
        boolean grafica = "PECA_GRAFICA".equals(tipoElemento);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", "Planta de ordenamento");
        corpo.put("separador", "Planta de ordenamento");
        corpo.put("categoria", "DEPOSITO");
        corpo.put("tipoElemento", tipoElemento);
        corpo.put("formato", "NAO_EDITAVEL");
        corpo.put("extensoesAceites", grafica ? List.of("zip") : List.of("pdf"));
        corpo.put("obrigatorio", true);
        corpo.put("ordem", 1);
        corpo.put("linkPublicoPermitido", false);
        corpo.put("ativo", true);
        return corpo;
    }

    private Map<String, Object> corpoCriacao(String codigo, String tipoElemento) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("instrumentoProcedimentoConfigId", procedimentoId);
        corpo.put("codigo", codigo);
        corpo.putAll(corpoAtualizacao(tipoElemento));
        return corpo;
    }

    private static Map<String, Object> regras(List<String> sistemas, List<String> compressoes, Integer dpi) {
        Map<String, Object> regras = new LinkedHashMap<>();
        regras.put("sistemasReferencia", sistemas);
        regras.put("compressoesAceites", compressoes);
        regras.put("bandaAlfaTolerada", false);
        regras.put("modeloCor", "RGB");
        regras.put("bitsPorBanda", 8);
        regras.put("resolucaoDpi", dpi);
        regras.put("elementosLegenda", List.of("ID do plano"));
        return regras;
    }

    private ResultActions criar(Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(URL).with(comPerfil("MASTER")).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions atualizar(long id, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(URL + "/" + id).with(comPerfil("MASTER")).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private long criarERetornarId(Map<String, Object> corpo) throws Exception {
        String resposta = criar(corpo).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asLong();
    }

    private ResultActions listar() throws Exception {
        return mockMvc.perform(get(URL).param("instrumentoProcedimentoConfigId", String.valueOf(procedimentoId))
                .with(comPerfil("TECNICO")));
    }

    @Test
    void pecaGraficaCriadaSemRegrasDeveFicarComAsIniciaisGeraisEOGetDevolveLas() throws Exception {
        long id = criarERetornarId(corpoCriacao("PLANTA_ORD", "PECA_GRAFICA"));

        assertThat(documentoConfigRepository.findById(id).orElseThrow().getRegrasTiff())
                .isEqualTo(RegrasTiff.inicialGeral());
        listar().andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].regrasTiff.sistemasReferencia.length()").value(4))
                .andExpect(jsonPath("$.content[0].regrasTiff.sistemasReferencia[0]").value("EPSG:3763"))
                .andExpect(jsonPath("$.content[0].regrasTiff.compressoesAceites[1]").value("LZW"))
                .andExpect(jsonPath("$.content[0].regrasTiff.bandaAlfaTolerada").value(true))
                .andExpect(jsonPath("$.content[0].regrasTiff.resolucaoDpi").doesNotExist());
    }

    @Test
    void pecaEscritaCriadaSemRegrasNaoTemRegrasTiff() throws Exception {
        long id = criarERetornarId(corpoCriacao("REGULAMENTO", "PECA_ESCRITA"));

        assertThat(documentoConfigRepository.findById(id).orElseThrow().getRegrasTiff()).isNull();
        listar().andExpect(jsonPath("$.content[0].regrasTiff").doesNotExist());
    }

    @Test
    void putComRegrasAlteradasDeveGravarEOGetDevolverExatamenteEssesValoresEAuditar() throws Exception {
        long id = criarERetornarId(corpoCriacao("PLANTA_ORD", "PECA_GRAFICA"));
        Map<String, Object> corpo = corpoAtualizacao("PECA_GRAFICA");
        corpo.put("regrasTiff", regras(List.of("EPSG:3763"), List.of("NONE"), 300));

        atualizar(id, corpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.regrasTiff.sistemasReferencia.length()").value(1));

        listar().andExpect(jsonPath("$.content[0].regrasTiff.sistemasReferencia[0]").value("EPSG:3763"))
                .andExpect(jsonPath("$.content[0].regrasTiff.sistemasReferencia.length()").value(1))
                .andExpect(jsonPath("$.content[0].regrasTiff.compressoesAceites[0]").value("NONE"))
                .andExpect(jsonPath("$.content[0].regrasTiff.compressoesAceites.length()").value(1))
                .andExpect(jsonPath("$.content[0].regrasTiff.bandaAlfaTolerada").value(false))
                .andExpect(jsonPath("$.content[0].regrasTiff.modeloCor").value("RGB"))
                .andExpect(jsonPath("$.content[0].regrasTiff.bitsPorBanda").value(8))
                .andExpect(jsonPath("$.content[0].regrasTiff.resolucaoDpi").value(300))
                .andExpect(jsonPath("$.content[0].regrasTiff.elementosLegenda[0]").value("ID do plano"));
        RegrasTiff gravadas = documentoConfigRepository.findById(id).orElseThrow().getRegrasTiff();
        assertThat(gravadas.compressoesAceites()).containsExactly(CompressaoTiff.NONE);
        List<Auditoria> linhas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "documento-config:" + id);
        assertThat(linhas).extracting(Auditoria::getAcao).containsExactly("CRIAR", "ATUALIZAR");
        assertThat(linhas.get(1).getDadosAlteracao()).contains("regrasTiff").contains("EPSG:3763");
    }

    @Test
    void putSemRegrasDeveManterAsGravadasESaemSeOItemDeixarDeSerPecaGrafica() throws Exception {
        Map<String, Object> criacao = corpoCriacao("PLANTA_ORD", "PECA_GRAFICA");
        criacao.put("regrasTiff", regras(List.of("EPSG:3763"), List.of("NONE"), 300));
        long id = criarERetornarId(criacao);

        atualizar(id, corpoAtualizacao("PECA_GRAFICA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.regrasTiff.resolucaoDpi").value(300));
        assertThat(documentoConfigRepository.findById(id).orElseThrow().getRegrasTiff().resolucaoDpi()).isEqualTo(300);

        Map<String, Object> outros = corpoAtualizacao("OUTROS");
        outros.put("extensoesAceites", List.of("pdf"));
        atualizar(id, outros).andExpect(status().isOk()).andExpect(jsonPath("$.regrasTiff").doesNotExist());
        assertThat(documentoConfigRepository.findById(id).orElseThrow().getRegrasTiff()).isNull();
    }

    @Test
    void regrasNumaPecaEscritaDevemDar400NoCampoRegrasTiffSemGravar() throws Exception {
        Map<String, Object> corpo = corpoCriacao("REGULAMENTO", "PECA_ESCRITA");
        corpo.put("regrasTiff", regras(List.of("EPSG:3763"), List.of("NONE"), 300));

        criar(corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("regrasTiff"))
                .andExpect(jsonPath("$.campos[0].erro").value(ValidadorRegrasTiff.MSG_SO_PECAS_GRAFICAS));
        listar().andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void tresRegrasInvalidasDevemDarUmSo400ComOsTresCampos() throws Exception {
        long id = criarERetornarId(corpoCriacao("PLANTA_ORD", "PECA_GRAFICA"));
        Map<String, Object> corpo = corpoAtualizacao("PECA_GRAFICA");
        corpo.put("regrasTiff", regras(List.of("3763"), List.of(), 0));

        atualizar(id, corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(3))
                .andExpect(jsonPath("$.campos[?(@.path=='regrasTiff.sistemasReferencia')]").isNotEmpty())
                .andExpect(jsonPath("$.campos[?(@.path=='regrasTiff.compressoesAceites')]").isNotEmpty())
                .andExpect(jsonPath("$.campos[?(@.path=='regrasTiff.resolucaoDpi')]").isNotEmpty());
        assertThat(documentoConfigRepository.findById(id).orElseThrow().getRegrasTiff())
                .isEqualTo(RegrasTiff.inicialGeral());
    }

    @Test
    void valorForaDosEnumsDeveDar400NoCampo() throws Exception {
        Map<String, Object> compressao = corpoCriacao("PLANTA_A", "PECA_GRAFICA");
        compressao.put("regrasTiff", regras(List.of("EPSG:3763"), List.of("JPEG"), 300));
        criar(compressao).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("regrasTiff.compressoesAceites[0]"));

        Map<String, Object> cor = corpoCriacao("PLANTA_B", "PECA_GRAFICA");
        Map<String, Object> regrasCor = regras(List.of("EPSG:3763"), List.of("NONE"), 300);
        regrasCor.put("modeloCor", "CMYK");
        cor.put("regrasTiff", regrasCor);
        criar(cor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("regrasTiff.modeloCor"));
        listar().andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void campoObrigatorioDasRegrasEmFaltaOuNuloDeveDar400NoCampoSemGravar() throws Exception {
        Map<String, Object> emFalta = corpoCriacao("PLANTA_A", "PECA_GRAFICA");
        Map<String, Object> regrasSemAlfa = regras(List.of("EPSG:3763"), List.of("NONE"), 300);
        regrasSemAlfa.remove("bandaAlfaTolerada");
        emFalta.put("regrasTiff", regrasSemAlfa);
        criar(emFalta).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("regrasTiff.bandaAlfaTolerada"));

        Map<String, Object> nulo = corpoCriacao("PLANTA_B", "PECA_GRAFICA");
        Map<String, Object> regrasNulas = regras(List.of("EPSG:3763"), List.of("NONE"), 300);
        regrasNulas.put("sistemasReferencia", null);
        nulo.put("regrasTiff", regrasNulas);
        criar(nulo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("regrasTiff.sistemasReferencia"));
        listar().andExpect(jsonPath("$.totalElements").value(0));
    }

    /** O externo é coberto em DocumentoConfigControllerIntegrationTest; aqui, os perfis internos sem MASTER. */
    @Test
    void tecnicoEVisitanteNaoPodemCriarNemAtualizar() throws Exception {
        long id = criarERetornarId(corpoCriacao("PLANTA_ORD", "PECA_GRAFICA"));
        Map<String, Object> corpo = corpoAtualizacao("PECA_GRAFICA");
        corpo.put("regrasTiff", regras(List.of("EPSG:3763"), List.of("NONE"), 300));

        for (RequestPostProcessor quem : List.of(comPerfil("TECNICO"), comPerfil("VISITANTE"))) {
            mockMvc.perform(post(URL).with(quem).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(corpoCriacao("OUTRO", "PECA_GRAFICA"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
            mockMvc.perform(put(URL + "/" + id).with(quem).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(corpo)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }
        DocumentoConfig item = documentoConfigRepository.findById(id).orElseThrow();
        assertThat(item.getRegrasTiff()).isEqualTo(RegrasTiff.inicialGeral());
    }

    @Test
    void postSemRegraNomenclaturaFicaGeralEPutComSgifrMudaEAudita() throws Exception {
        long id = criarERetornarId(corpoCriacao("PLANTA_ORD", "PECA_GRAFICA"));
        assertThat(documentoConfigRepository.findById(id).orElseThrow().getRegraNomenclatura())
                .isEqualTo(RegraNomenclatura.GERAL);
        listar().andExpect(jsonPath("$.content[0].regraNomenclatura").value("GERAL"));

        Map<String, Object> corpo = corpoAtualizacao("PECA_GRAFICA");
        corpo.put("regraNomenclatura", "SGIFR");
        atualizar(id, corpo).andExpect(status().isOk()).andExpect(jsonPath("$.regraNomenclatura").value("SGIFR"));

        listar().andExpect(jsonPath("$.content[0].regraNomenclatura").value("SGIFR"));
        List<Auditoria> linhas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("CONFIGURACAO",
                "documento-config:" + id);
        assertThat(linhas).extracting(Auditoria::getAcao).containsExactly("CRIAR", "ATUALIZAR");
        assertThat(linhas.get(0).getDadosAlteracao()).contains("\"regraNomenclatura\"").contains("GERAL");
        assertThat(linhas.get(1).getDadosAlteracao()).contains("\"regraNomenclatura\"").contains("SGIFR")
                .doesNotContain("regrasTiff");
    }

    @Test
    void putSemRegraNomenclaturaMantemAGravadaEPostPodeTrazerSgifr() throws Exception {
        Map<String, Object> criacao = corpoCriacao("PLANTA_SGIFR", "PECA_GRAFICA");
        criacao.put("regraNomenclatura", "SGIFR");
        long id = criarERetornarId(criacao);

        atualizar(id, corpoAtualizacao("PECA_GRAFICA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.regraNomenclatura").value("SGIFR"));
        assertThat(documentoConfigRepository.findById(id).orElseThrow().getRegraNomenclatura())
                .isEqualTo(RegraNomenclatura.SGIFR);
    }

    @Test
    void regraNomenclaturaForaDoEnumDeveDar400NoCampo() throws Exception {
        Map<String, Object> corpo = corpoCriacao("PLANTA_X", "PECA_GRAFICA");
        corpo.put("regraNomenclatura", "LIVRE");

        criar(corpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("regraNomenclatura"));
    }
}
