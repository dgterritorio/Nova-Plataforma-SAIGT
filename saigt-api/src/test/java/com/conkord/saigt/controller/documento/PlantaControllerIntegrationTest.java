package com.conkord.saigt.controller.documento;

import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
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
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlantaControllerIntegrationTest {

    private static final String HASH = "c".repeat(64);
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(9700);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ArmazenamentoDocumentos armazenamento;

    @MockBean
    private EnvioIncmService envioIncmService;

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private PlantaRepository plantaRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

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

    private record Cenario(Processo processo, List<Planta> plantas, Entidade entidade, Utilizador autor) {
        String numeroProcesso() {
            return processo.getNumeroProcesso();
        }
    }

    /** Um processo com uma planta por TIFF indicado, nenhuma em vigor; com IDUS só se {@code comIdus}. */
    private Cenario cenario(boolean comIdus, String... tiffs) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PBO-" + n, "Plano " + n, "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream().filter(t -> "ELABORACAO".equals(t.getCodigo()))
                .findFirst().orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, elaboracao));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PBO-FIM-" + n, "Procedimento final"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("514" + n, "Município de Teste " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + n, "autora" + n + "@cm-pbo.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(new Instrumento(tipoInstrumento, "1", "PDM de Teste " + n, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Procedimento final do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));
        processo.setNumeroDeposito("01.03.07/PDM/03/2025/" + n);
        processo = processoRepository.saveAndFlush(processo);
        if (comIdus) {
            UUID processoId = processo.getId();
            transacao.executeWithoutResult(e -> idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                    .processo(processoRepository.getReferenceById(processoId)).idus("01.0307.01.004321.03.00005.01.002.01.2025" + n)
                    .sistemaOrigem("SAIGT").idIgt("004321").idIgtDefinitivo(true).ordemDinamica("03").ordemDefinitiva(true)
                    .tentativa((short) 1).procedimento("002").submissao((short) 1).pubIncm("20250116")
                    .atualizadoEm(Instant.now()).build()));
        }

        DocumentoConfig item = new DocumentoConfig(procedimento, "PBO_" + n, "Planta", "Planta", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", 40);
        item = documentoConfigRepository.saveAndFlush(item);
        ProcessoDocumento documento = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, item,
                item.getCategoria(), "zip", "plantas.zip", "privado/pbo/" + n, HASH, "v1", 10, autor));
        List<Planta> plantas = new java.util.ArrayList<>();
        for (int i = 0; i < tiffs.length; i++) {
            Planta planta = new Planta(processo, documento);
            planta.setNomeRegulamento("Planta de Ordenamento " + (i + 1));
            planta.setNumeroFolha("0" + (i + 1));
            planta.setNomeTiff(tiffs[i]);
            planta.setNomeZip("0307Plantas_" + n + ".zip");
            planta.setLinkJpeg("https://saigt.invalid/api/v1/publico/token-ficticio/" + tiffs[i] + ".jpg");
            planta.setLinhaTxt(i + 1);
            plantas.add(plantaRepository.saveAndFlush(planta));
        }
        return new Cenario(processo, plantas, entidade, autor);
    }

    private Utilizador interno(PerfilInterno perfil) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Utilizador utilizador = new Utilizador("Técnica Fictícia " + sufixo, "tecnica-" + sufixo + "@dgt.invalid", "AD_DGT");
        utilizador.setPerfilInterno(perfil);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    private static RequestPostProcessor jwtInterno(Utilizador utilizador, String perfil) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", perfil));
    }

    private RequestPostProcessor tecnico() {
        return jwtInterno(interno(PerfilInterno.TECNICO), "TECNICO");
    }

    private RequestPostProcessor master() {
        return jwtInterno(interno(PerfilInterno.MASTER), "MASTER");
    }

    private RequestPostProcessor visitante() {
        return jwtInterno(interno(PerfilInterno.VISITANTE), "VISITANTE");
    }

    private static RequestPostProcessor externo(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(),
                        "nif", cenario.entidade().getNipc(), "nome", "Município", "pcm", false)));
    }

    private ResultActions listar(RequestPostProcessor quem, String consulta) throws Exception {
        return mockMvc.perform(get("/api/v1/plantas" + consulta).with(quem));
    }

    private ResultActions atualizar(RequestPostProcessor quem, UUID plantaId, String corpo) throws Exception {
        return mockMvc.perform(patch("/api/v1/plantas/" + plantaId).with(quem)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private List<Auditoria> auditoriasDe(Planta planta) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PLANTA", planta.getId().toString()).stream()
                .filter(a -> "ATUALIZAR_PLANTA".equals(a.getAcao()))
                .toList();
    }

    @Test
    void masterListaAsPlantasDoProcessoComIdusNumeroDeDepositoEOrdemPorTiff() throws Exception {
        Cenario c = cenario(true, "0307PO_B", "0307PO_A");

        listar(master(), "?numeroProcesso=" + c.numeroProcesso())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].nomeTiff").value(contains("0307PO_A", "0307PO_B")))
                .andExpect(jsonPath("$.content[0].processoId").value(c.processo().getId().toString()))
                .andExpect(jsonPath("$.content[0].numeroProcesso").value(c.numeroProcesso()))
                .andExpect(jsonPath("$.content[0].numeroDeposito").value(c.processo().getNumeroDeposito()))
                .andExpect(jsonPath("$.content[0].idus").value(org.hamcrest.Matchers.startsWith("01.0307.01.004321")))
                .andExpect(jsonPath("$.content[0].processoDocumentoId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].linkJpeg").value(org.hamcrest.Matchers.endsWith("0307PO_A.jpg")))
                .andExpect(jsonPath("$.content[0].emVigor").value(false))
                .andExpect(jsonPath("$.content[0].atualizadoEm").isNotEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void tecnicoVeIdusNuloQuandoOProcessoNaoTemIdus() throws Exception {
        Cenario c = cenario(false, "0307PO_X");

        listar(tecnico(), "?numeroProcesso=" + c.numeroProcesso())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].idus").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void filtraPorNumeroDeDepositoEPorEmVigorEPagina() throws Exception {
        Cenario c = cenario(true, "T1", "T2", "T3");
        cenario(true, "OUTRO");
        Planta emVigor = plantaRepository.findById(c.plantas().get(1).getId()).orElseThrow();
        emVigor.setEmVigor(true);
        plantaRepository.saveAndFlush(emVigor);

        listar(tecnico(), "?numeroDeposito=" + c.processo().getNumeroDeposito())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nomeTiff").value(contains("T1", "T2", "T3")));
        listar(tecnico(), "?numeroProcesso=" + c.numeroProcesso() + "&emVigor=true")
                .andExpect(jsonPath("$.content[*].nomeTiff").value(contains("T2")));
        listar(tecnico(), "?numeroProcesso=" + c.numeroProcesso() + "&emVigor=false")
                .andExpect(jsonPath("$.content[*].nomeTiff").value(contains("T1", "T3")));
        listar(tecnico(), "?numeroProcesso=" + c.numeroProcesso() + "&page=1&size=2")
                .andExpect(jsonPath("$.content[*].nomeTiff").value(contains("T3")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void paginacaoForaDosLimitesDa400Validacao() throws Exception {
        listar(tecnico(), "?size=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("size"));
        listar(tecnico(), "?page=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("page"));
        listar(tecnico(), "?emVigor=talvez")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void visitanteEUtilizadorExternoRecebem403ENadaMuda() throws Exception {
        Cenario c = cenario(false, "0307PO_V");
        Planta planta = c.plantas().get(0);

        listar(visitante(), "?numeroProcesso=" + c.numeroProcesso())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        listar(externo(c), "?numeroProcesso=" + c.numeroProcesso()).andExpect(status().isForbidden());
        atualizar(visitante(), planta.getId(), "{\"numeroFolha\":\"99\"}").andExpect(status().isForbidden());
        atualizar(externo(c), planta.getId(), "{\"numeroFolha\":\"99\"}").andExpect(status().isForbidden());

        Planta depois = plantaRepository.findById(planta.getId()).orElseThrow();
        assertThat(depois.getNumeroFolha()).isEqualTo("01");
        assertThat(auditoriasDe(planta)).isEmpty();
    }

    @Test
    void tecnicoAtualizaFolhaEEmVigorComAuditoriaEAutoria() throws Exception {
        Cenario c = cenario(true, "0307PO_E");
        Planta planta = c.plantas().get(0);
        Utilizador tecnica = interno(PerfilInterno.TECNICO);

        atualizar(jwtInterno(tecnica, "TECNICO"), planta.getId(), "{\"numeroFolha\":\"  12-A \",\"emVigor\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planta.getId().toString()))
                .andExpect(jsonPath("$.numeroFolha").value("12-A"))
                .andExpect(jsonPath("$.emVigor").value(true))
                .andExpect(jsonPath("$.nomeRegulamento").value("Planta de Ordenamento 1"))
                .andExpect(jsonPath("$.atualizadoPorNome").value(tecnica.getNome()))
                .andExpect(jsonPath("$.idus").isNotEmpty());

        Planta depois = transacao.execute(e -> {
            Planta p = plantaRepository.findById(planta.getId()).orElseThrow();
            p.getAtualizadoPor().getId();
            return p;
        });
        assertThat(depois.getNumeroFolha()).isEqualTo("12-A");
        assertThat(depois.isEmVigor()).isTrue();
        assertThat(depois.getAtualizadoPor().getId()).isEqualTo(tecnica.getId());
        assertThat(depois.getAtualizadoEm()).isAfter(planta.getAtualizadoEm());

        List<Auditoria> auditorias = auditoriasDe(planta);
        assertThat(auditorias).hasSize(1);
        Auditoria auditoria = auditorias.get(0);
        assertThat(auditoria.getTipoOcorrencia()).isEqualTo("PLANTA");
        assertThat(auditoria.getUtilizadorId()).isEqualTo(tecnica.getId());
        JsonNode dados = objectMapper.readTree(auditoria.getDadosAlteracao());
        assertThat(dados.at("/antes/numeroFolha").asText()).isEqualTo("01");
        assertThat(dados.at("/depois/numeroFolha").asText()).isEqualTo("12-A");
        assertThat(dados.at("/antes/emVigor").asBoolean()).isFalse();
        assertThat(dados.at("/depois/emVigor").asBoolean()).isTrue();
        assertThat(dados.at("/antes").has("nomeRegulamento")).isFalse();
    }

    @Test
    void stringVaziaGravaNuloEOLinkJpegNaoMuda() throws Exception {
        Cenario c = cenario(false, "0307PO_L");
        Planta planta = c.plantas().get(0);

        atualizar(master(), planta.getId(), "{\"nomeZip\":\"  \",\"linkJpeg\":\"https://outro.invalid/x.jpg\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeZip").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.linkJpeg").value(planta.getLinkJpeg()));

        Planta depois = plantaRepository.findById(planta.getId()).orElseThrow();
        assertThat(depois.getNomeZip()).isNull();
        assertThat(depois.getLinkJpeg()).isEqualTo(planta.getLinkJpeg());
    }

    @Test
    void corpoInvalidoDa400ValidacaoENadaMuda() throws Exception {
        Cenario c = cenario(false, "0307PO_I");
        Planta planta = c.plantas().get(0);

        atualizar(tecnico(), planta.getId(), "{\"nomeRegulamento\":\"" + "x".repeat(501) + "\",\"numeroFolha\":\"77\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("nomeRegulamento"));
        atualizar(tecnico(), planta.getId(), "{\"numeroFolha\":\"" + "9".repeat(21) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("numeroFolha"));
        atualizar(tecnico(), planta.getId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        atualizar(tecnico(), planta.getId(), "{\"linkJpeg\":\"https://outro.invalid/x.jpg\"}")
                .andExpect(status().isBadRequest());

        Planta depois = plantaRepository.findById(planta.getId()).orElseThrow();
        assertThat(depois.getNomeRegulamento()).isEqualTo("Planta de Ordenamento 1");
        assertThat(depois.getNumeroFolha()).isEqualTo("01");
        assertThat(auditoriasDe(planta)).isEmpty();
    }

    @Test
    void plantaInexistenteDa404PlantaNaoEncontrada() throws Exception {
        atualizar(tecnico(), UUID.randomUUID(), "{\"numeroFolha\":\"1\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANTA_NAO_ENCONTRADA"));
    }
}
