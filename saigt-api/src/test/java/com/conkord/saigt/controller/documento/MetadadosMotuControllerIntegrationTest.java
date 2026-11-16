package com.conkord.saigt.controller.documento;

import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
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
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.MetadadosMotuService;
import com.conkord.saigt.service.incm.EnvioIncmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A ficha MOTU (Série) do procedimento final e a troca da ficha única da Decisão de elaborar sem GeoConform. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetadadosMotuControllerIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger(9800);
    private static final String SERIE_COMPLETA = "{\"tituloAlternativo\":\"PDM de Teste\",\"resumo\":\"Plano diretor municipal\","
            + "\"palavrasChave\":\"ordenamento; plano\",\"denominadorEscala\":\"10000\","
            + "\"sistemaReferencia\":\"PT-TM06/ETRS89\",\"codigoSistema\":\"3763\",\"historico\":\"Primeira versão\","
            + "\"relatorio\":\"Relatório de qualidade\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArmazenamentoDocumentos armazenamento;

    @MockBean
    private EnvioIncmService envioIncmService;

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private CompletudeDocumentalService completudeDocumentalService;

    @Autowired
    private MetadadosMotuService metadadosMotuService;

    @Autowired
    private MetadadosMotuRepository metadadosMotuRepository;

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, UUID serieId) {
    }

    /**
     * Um procedimento final com (ou sem) peça gráfica de publicação, sem GeoConform e com os itens da página das
     * Plantas; com a peça a ficha MOTU aplica-se e a Série fica criada, como em `ProcessoService.criar`.
     */
    private Cenario cenario(boolean comPecaGraficaDePublicacao) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("MOT-" + n, "Plano " + n, "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream().filter(tipo -> "ELABORACAO".equals(tipo.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, elaboracao));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("MOT-FIM-" + n, "Procedimento final"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        item(procedimento, "TEXTO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, "[\"pdf\"]", 1, PaginaDocumento.DOCUMENTOS);
        if (comPecaGraficaDePublicacao) {
            item(procedimento, "PLANTA_ORDENAMENTO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", 7,
                    PaginaDocumento.DOCUMENTOS);
        }
        item(procedimento, "PLANTAS_ZIP_ORDENAMENTO_DEP", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"zip\"]", 30,
                PaginaDocumento.PLANTAS);
        item(procedimento, "PLANTAS_TXT_NOMES_DEP", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, "[\"txt\"]", 32,
                PaginaDocumento.PLANTAS);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("510" + n, "Município de Teste " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-mot.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "PDM de Teste " + n, autor, autor));
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Procedimento final do PDM", autor);
        dinamica.setComGeoconform(false);
        dinamica = dinamicaRepository.saveAndFlush(dinamica);
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));
        transacao.executeWithoutResult(estado -> metadadosMotuService.criarSerie(
                processoRepository.findById(processo.getId()).orElseThrow()));
        UUID serieId = comPecaGraficaDePublicacao
                ? metadadosMotuRepository.findByProcesso_IdAndTipo(processo.getId(), TipoMetadadosMotu.SERIE).orElseThrow().getId()
                : null;
        return new Cenario(processo, autor, entidade, serieId);
    }

    private void item(InstrumentoProcedimentoConfig procedimento, String codigo, CategoriaDocumento categoria,
                      TipoElemento tipoElemento, String extensoes, int ordem, PaginaDocumento pagina) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, codigo, codigo, categoria, tipoElemento,
                FormatoDocumento.NAO_EDITAVEL, extensoes, ordem);
        item.setPagina(pagina);
        documentoConfigRepository.saveAndFlush(item);
    }

    private static RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static RequestPostProcessor jwtDe(Cenario cenario) {
        return jwtDe(cenario.entidade(), cenario.autor().getId());
    }

    private String url(Cenario cenario) {
        return "/api/v1/processos/" + cenario.processo().getId() + "/metadados-motu";
    }

    private ResultActions obter(Cenario cenario) throws Exception {
        return mockMvc.perform(get(url(cenario)).with(jwtDe(cenario)));
    }

    private ResultActions gravar(Cenario cenario, UUID motuId, String corpo) throws Exception {
        return mockMvc.perform(put(url(cenario) + "/" + motuId).with(jwtDe(cenario)).contentType("application/json").content(corpo));
    }

    private List<String> pendenciasDeMetadados(Cenario cenario) {
        return transacao.execute(estado -> completudeDocumentalService.pendenciasPlantasEMetadados(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()).stream()
                .map(CampoErro::path).filter(caminho -> caminho.startsWith("metadados.")).toList());
    }

    private void colocarEmEstado(Cenario cenario, EstadoProcesso estado) {
        Processo atual = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        atual.setEstado(estado);
        processoRepository.saveAndFlush(atual);
    }

    @Test
    void deveDevolverASerieComOTituloDoInstrumentoOResponsavelEOsDadosVazios() throws Exception {
        Cenario cenario = cenario(true);

        obter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaFichaMotu").value(true))
                .andExpect(jsonPath("$.responsavel.funcao").value("Produtor"))
                .andExpect(jsonPath("$.responsavel.pais").value("Portugal"))
                .andExpect(jsonPath("$.registos.length()").value(1))
                .andExpect(jsonPath("$.registos[0].id").value(cenario.serieId().toString()))
                .andExpect(jsonPath("$.registos[0].tipo").value("SERIE"))
                .andExpect(jsonPath("$.registos[0].opcional").value(false))
                .andExpect(jsonPath("$.registos[0].completo").value(false))
                .andExpect(jsonPath("$.registos[0].titulo").value(cenario.processo().getDinamica().getInstrumento().getDesignacao()))
                .andExpect(jsonPath("$.registos[0].dados.resumo").doesNotExist())
                .andExpect(jsonPath("$.registos[0].dados.historico").doesNotExist());
    }

    @Test
    void deveDevolverAplicaFichaMotuFalsoESemRegistosNumProcedimentoSemFicha() throws Exception {
        Cenario cenario = cenario(false);

        obter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaFichaMotu").value(false))
                .andExpect(jsonPath("$.registos.length()").value(0));
    }

    @Test
    void deveDevolver404QuandoOProcessoNaoExiste() throws Exception {
        Cenario cenario = cenario(true);

        mockMvc.perform(get("/api/v1/processos/" + UUID.randomUUID() + "/metadados-motu").with(jwtDe(cenario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveGravarACerieCompletaERecalcularCompletoSemOHistorico() throws Exception {
        Cenario cenario = cenario(true);

        gravar(cenario, cenario.serieId(), SERIE_COMPLETA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cenario.serieId().toString()))
                .andExpect(jsonPath("$.tipo").value("SERIE"))
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.dados.denominadorEscala").value("10000"));
        obter(cenario)
                .andExpect(jsonPath("$.registos[0].completo").value(true))
                .andExpect(jsonPath("$.registos[0].dados.relatorio").value("Relatório de qualidade"));

        gravar(cenario, cenario.serieId(), SERIE_COMPLETA.replace("\"historico\":\"Primeira versão\"", "\"historico\":\"\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));
        obter(cenario)
                .andExpect(jsonPath("$.registos[0].completo").value(false))
                .andExpect(jsonPath("$.registos[0].dados.resumo").value("Plano diretor municipal"));
    }

    @Test
    void deveGravarComoRascunhoComCamposEmBrancoEComTextoSoDeEspacos() throws Exception {
        Cenario cenario = cenario(true);

        gravar(cenario, cenario.serieId(), "{\"resumo\":\"   \",\"palavrasChave\":\"plano\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));
        gravar(cenario, cenario.serieId(), SERIE_COMPLETA.replace("\"relatorio\":\"Relatório de qualidade\"", "\"relatorio\":\"  \""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));
    }

    @Test
    void deveRecusarUmLimiteUltrapassadoSemAlterarNada() throws Exception {
        Cenario cenario = cenario(true);
        gravar(cenario, cenario.serieId(), SERIE_COMPLETA).andExpect(status().isOk());

        gravar(cenario, cenario.serieId(), SERIE_COMPLETA.replace("PDM de Teste", "x".repeat(301)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MOTU_CAMPOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("tituloAlternativo")));

        obter(cenario)
                .andExpect(jsonPath("$.registos[0].completo").value(true))
                .andExpect(jsonPath("$.registos[0].dados.tituloAlternativo").value("PDM de Teste"));
    }

    @Test
    void deveDevolver404ParaUmMotuIdDeOutroProcessoOuInexistente() throws Exception {
        Cenario cenario = cenario(true);
        Cenario outro = cenario(true);

        gravar(cenario, outro.serieId(), SERIE_COMPLETA).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        gravar(cenario, UUID.randomUUID(), SERIE_COMPLETA).andExpect(status().isNotFound());

        obter(outro).andExpect(jsonPath("$.registos[0].dados.resumo").doesNotExist());
    }

    @Test
    void deveRecusarQuemNaoEOAutorEOProcessoForaDeEdicao() throws Exception {
        Cenario cenario = cenario(true);
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outro", "outro" + SEQUENCIA.incrementAndGet() + "@cm-mot.pt", "INCM"));

        mockMvc.perform(put(url(cenario) + "/" + cenario.serieId()).with(jwtDe(cenario.entidade(), outro.getId()))
                        .contentType("application/json").content(SERIE_COMPLETA))
                .andExpect(status().isForbidden());

        colocarEmEstado(cenario, EstadoProcesso.ENVIADO_DGT);
        gravar(cenario, cenario.serieId(), SERIE_COMPLETA).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"));
        obter(cenario).andExpect(jsonPath("$.registos[0].completo").value(false));
    }

    @Test
    void deveDevolver409MotuNaoAplicavelNumProcedimentoSemFicha() throws Exception {
        Cenario cenario = cenario(false);

        gravar(cenario, UUID.randomUUID(), SERIE_COMPLETA).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MOTU_NAO_APLICAVEL"));
    }

    @Test
    void umConjuntoOpcionalSemDocumentoVemSemTituloEZipEGravaSeComoRascunho() throws Exception {
        Cenario cenario = cenario(true);
        MetadadosMotu conjunto = transacao.execute(estado -> {
            MetadadosMotu ficha = MetadadosMotu.conjunto(processoRepository.findById(cenario.processo().getId()).orElseThrow(), null);
            ReflectionTestUtils.setField(ficha, "opcional", true);
            return metadadosMotuRepository.saveAndFlush(ficha);
        });

        gravar(cenario, conjunto.getId(), SERIE_COMPLETA).andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));
        obter(cenario)
                .andExpect(jsonPath("$.registos[*].tipo").value(contains("SERIE", "CDG")))
                .andExpect(jsonPath("$.registos[1].titulo").doesNotExist())
                .andExpect(jsonPath("$.registos[1].zip").doesNotExist())
                .andExpect(jsonPath("$.registos[1].dados.resumo").value("Plano diretor municipal"));
    }

    @Test
    void noProcedimentoComFichaMotuAFichaUnicaSaiDaSubmissaoEOPutDaFichaUnicaDa409() throws Exception {
        Cenario cenario = cenario(true);

        assertThat(pendenciasDeMetadados(cenario)).containsExactly("metadados.serie");
        mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/metadados").with(jwtDe(cenario))
                        .contentType("application/json").content("{\"palavrasChave\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MOTU_NAO_APLICAVEL"));
    }

    @Test
    void semFichaMotuAFichaUnicaContinuaAExigirSeEAGravar() throws Exception {
        Cenario cenario = cenario(false);

        assertThat(pendenciasDeMetadados(cenario)).contains("metadados.palavrasChave", "metadados.exatidaoTematica");
        mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/metadados").with(jwtDe(cenario))
                        .contentType("application/json").content("{\"palavrasChave\":\"x\"}"))
                .andExpect(status().isOk());
        assertThat(pendenciasDeMetadados(cenario)).doesNotContain("metadados.palavrasChave");
    }
}
