package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoAtoTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBaseRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.dto.instrumento.RejeitarProcessoRequest;
import com.conkord.saigt.service.instrumento.ProcessoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RejeitarProcessoIntegrationTest {

    private static final String CATEGORIA = "CATEGORIA_REJEICAO_DGT";
    private static final String MOTIVO = "MOTIVO_REJEICAO_DGT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcessoService processoService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

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
    private ProcessoInformacaoBaseRepository processoInformacaoBaseRepository;

    @Autowired
    private ProcessoValidacaoRejeicaoRepository processoValidacaoRejeicaoRepository;

    @Autowired
    private ListaValoresRepository listaValoresRepository;

    private record Cenario(Processo processo, Utilizador autor, String nif, ProcessoDocumento documento,
                           ProcessoDocumento outroDocumento, ProcessoInformacaoBase deliberacao,
                           ProcessoInformacaoBase textoAto) {
    }

    private record Listas(ListaValores categoria, ListaValores motivo) {
    }

    private record Recusa(int sequencia, String origem, String notasGerais, UUID criadoPor) {
    }

    private record ItemRecusa(int sequencia, UUID documentoId, UUID informacaoBaseId, Long categoriaId, Long motivoId,
                              String notas) {
    }

    private Cenario criarCenario(String codigo, String nif) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento procedimento = tipoProcedimentoRepository.findAll().stream()
                .filter(p -> p.getCodigo().equals("DECISAO_INICIO")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento("DECISAO_INICIO", "Decisão de Início")));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, procedimento, 1, "SEGUNDA_H"));
        int ordem = 1;
        for (ElementoInformacaoBase elemento : ElementoInformacaoBase.values()) {
            DocumentoConfig item = new DocumentoConfig(procedimentoConfig, elemento.name() + "_PUB", elemento.name(),
                    elemento.name(), CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                    "[\"pdf\"]", ordem++);
            item.setElementoInformacaoBase(elemento);
            documentoConfigRepository.saveAndFlush(item);
        }
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo + "@cm-" + codigo + ".pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = new Processo(dinamica, procedimentoConfig, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processo = processoRepository.saveAndFlush(processo);
        return new Cenario(processo, autor, nif, documento(processo, "texto.pdf"), documento(processo, "planta.pdf"),
                processoInformacaoBaseRepository.saveAndFlush(
                        new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO)),
                processoInformacaoBaseRepository.saveAndFlush(
                        new ProcessoInformacaoBase(processo, ElementoInformacaoBase.TEXTO_ATO)));
    }

    private ProcessoDocumento documento(Processo processo, String nome) {
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, null,
                CategoriaDocumento.DEPOSITO, "pdf", nome, "privado/" + processo.getId() + "/" + nome, "a".repeat(64),
                "v1", 10L, processo.getCriadoPor()));
    }

    private Listas listas(String codigo) {
        return new Listas(listaValoresRepository.saveAndFlush(new ListaValores(CATEGORIA, "C" + codigo, "Forma", 1)),
                listaValoresRepository.saveAndFlush(new ListaValores(MOTIVO, "M" + codigo, "Ilegível", 1)));
    }

    private void semListasDeRecusa() {
        for (String tipo : List.of(CATEGORIA, MOTIVO)) {
            for (ListaValores valor : listaValoresRepository.findByTipoOrderByOrdemAsc(tipo)) {
                valor.setAtivo(false);
                listaValoresRepository.saveAndFlush(valor);
            }
        }
    }

    private Utilizador tecnicoDgt(String codigo) {
        Utilizador tecnico = new Utilizador("Técnico " + codigo, "tecnico" + codigo + "@dgt.pt", "AD_DGT");
        tecnico.setPerfilInterno(PerfilInterno.TECNICO);
        return utilizadorRepository.saveAndFlush(tecnico);
    }

    private static RequestPostProcessor jwtDgt(Utilizador utilizador) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "TECNICO"));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.processo().getEntidade().getId(), "nif", cenario.nif(), "nome", "Entidade Teste", "pcm", false)));
    }

    private long versao(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow().getVersao();
    }

    private static Map<String, Object> validado(String tipo, UUID itemId) {
        return Map.of("tipo", tipo, "itemId", itemId.toString(), "decisao", "VALIDADO");
    }

    private static Map<String, Object> rejeitado(String tipo, UUID itemId, Listas listas, String notas) {
        Map<String, Object> item = new HashMap<>();
        item.put("tipo", tipo);
        item.put("itemId", itemId.toString());
        item.put("decisao", "REJEITADO");
        item.put("categoriaId", listas == null ? null : listas.categoria().getId());
        item.put("motivoId", listas == null ? null : listas.motivo().getId());
        item.put("notas", notas);
        return item;
    }

    @SafeVarargs
    private void gravarItens(Cenario cenario, Utilizador tecnico, Map<String, Object>... itens) throws Exception {
        mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/validacao/itens")
                        .with(jwtDgt(tecnico)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("itens", List.of(itens),
                                "versao", versao(cenario.processo())))))
                .andExpect(status().isOk());
    }

    private ResultActions rejeitar(Cenario cenario, Utilizador tecnico, String notasGerais) throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", versao(cenario.processo()));
        corpo.put("notasGerais", notasGerais);
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/rejeitar")
                .with(jwtDgt(tecnico)).contentType("application/json")
                .content(objectMapper.writeValueAsString(corpo)));
    }

    /** Dois documentos e a Deliberação rejeitados, o Texto do Ato validado. */
    private void rejeitarTresItens(Cenario cenario, Utilizador tecnico, Listas listas) throws Exception {
        gravarItens(cenario, tecnico,
                rejeitado("DOCUMENTO", cenario.documento().getId(), listas, "Página 3 ilegível."),
                rejeitado("DOCUMENTO", cenario.outroDocumento().getId(), listas, "Falta a legenda."),
                rejeitado("INFORMACAO_BASE", cenario.deliberacao().getId(), listas, "Falta a data da deliberação."),
                validado("INFORMACAO_BASE", cenario.textoAto().getId()));
    }

    private EstadoProcesso estado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getEstado();
    }

    private List<Recusa> recusas(Cenario cenario) {
        return jdbcTemplate.query("SELECT sequencia, origem, notas_gerais, criado_por FROM processo_rejeicao "
                        + "WHERE processo_id = ? ORDER BY sequencia",
                (rs, i) -> new Recusa(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class)),
                cenario.processo().getId());
    }

    private List<ItemRecusa> itens(Cenario cenario) {
        return jdbcTemplate.query("SELECT r.sequencia, i.processo_documento_id, i.processo_informacao_base_id, "
                        + "i.categoria_id, i.motivo_id, i.notas FROM processo_rejeicao_item i "
                        + "JOIN processo_rejeicao r ON r.id = i.rejeicao_id WHERE r.processo_id = ? ORDER BY i.id",
                (rs, i) -> new ItemRecusa(rs.getInt(1), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, Long.class), rs.getObject(5, Long.class), rs.getString(6)),
                cenario.processo().getId());
    }

    private int linhasNoRascunho(Cenario cenario) {
        return processoValidacaoRejeicaoRepository.findByProcesso_Id(cenario.processo().getId()).size();
    }

    @Test
    void deveCriarARecusaNumeradaComOsItensDoRascunhoEApagarORascunho() throws Exception {
        Cenario cenario = criarCenario("RJC01", "501200001");
        Utilizador tecnico = tecnicoDgt("RJC01");
        Listas listas = listas("RJC01");
        rejeitarTresItens(cenario, tecnico, listas);

        rejeitar(cenario, tecnico, "  Rever o conjunto antes de voltar a submeter.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REJEITADO_DGT"));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.REJEITADO_DGT);
        assertThat(recusas(cenario)).containsExactly(
                new Recusa(1, "DGT", "Rever o conjunto antes de voltar a submeter.", tecnico.getId()));
        Long categoria = listas.categoria().getId();
        Long motivo = listas.motivo().getId();
        assertThat(itens(cenario)).containsExactlyInAnyOrder(
                new ItemRecusa(1, cenario.documento().getId(), null, categoria, motivo, "Página 3 ilegível."),
                new ItemRecusa(1, cenario.outroDocumento().getId(), null, categoria, motivo, "Falta a legenda."),
                new ItemRecusa(1, null, cenario.deliberacao().getId(), categoria, motivo, "Falta a data da deliberação."));
        assertThat(linhasNoRascunho(cenario)).isZero();
        assertThat(processoDocumentoRepository.findById(cenario.documento().getId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.REJEITADO);
    }

    @Test
    void deveDarASequenciaSeguinteComSoOsItensDaSegundaRejeicao() throws Exception {
        Cenario cenario = criarCenario("RJC02", "501200002");
        Utilizador tecnico = tecnicoDgt("RJC02");
        Listas listas = listas("RJC02");
        rejeitarTresItens(cenario, tecnico, listas);
        rejeitar(cenario, tecnico, null).andExpect(status().isOk());

        // Corrigido, submetido e capturado de novo: só a planta volta a ser rejeitada.
        Processo processo = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        processo.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(processo);
        gravarItens(cenario, tecnico,
                validado("DOCUMENTO", cenario.documento().getId()),
                rejeitado("DOCUMENTO", cenario.outroDocumento().getId(), listas, "A legenda continua em falta."),
                validado("INFORMACAO_BASE", cenario.deliberacao().getId()));
        rejeitar(cenario, tecnico, null).andExpect(status().isOk());

        assertThat(recusas(cenario)).extracting(Recusa::sequencia).containsExactly(1, 2);
        assertThat(itens(cenario)).filteredOn(item -> item.sequencia() == 2).singleElement().satisfies(item -> {
            assertThat(item.documentoId()).isEqualTo(cenario.outroDocumento().getId());
            assertThat(item.notas()).isEqualTo("A legenda continua em falta.");
        });
        assertThat(itens(cenario)).filteredOn(item -> item.sequencia() == 1).hasSize(3);
    }

    @Test
    void naSegundaRondaOsItensRejeitadosVoltamAPorValidarEADgtPodeValidarOProcesso() throws Exception {
        Cenario cenario = criarCenario("RJC90", "501200090");
        Utilizador tecnico = tecnicoDgt("RJC90");
        Listas listas = listas("RJC90");
        rejeitarTresItens(cenario, tecnico, listas);
        rejeitar(cenario, tecnico, null).andExpect(status().isOk());

        // Corrigido e submetido de novo: a DGT captura-o para a segunda ronda.
        Processo processo = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        processo.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(processo);
        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/capturar")
                        .with(jwtDgt(tecnico)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("versao", versao(processo)))))
                .andExpect(status().isOk());

        assertThat(processoDocumentoRepository.findById(cenario.documento().getId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(processoDocumentoRepository.findById(cenario.outroDocumento().getId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(processoInformacaoBaseRepository.findById(cenario.deliberacao().getId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(processoInformacaoBaseRepository.findById(cenario.textoAto().getId()).orElseThrow().getEstadoValidacao())
                .isEqualTo(EstadoValidacao.VALIDADO);

        gravarItens(cenario, tecnico,
                validado("DOCUMENTO", cenario.documento().getId()),
                validado("DOCUMENTO", cenario.outroDocumento().getId()),
                validado("INFORMACAO_BASE", cenario.deliberacao().getId()));
        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/validar")
                        .with(jwtDgt(tecnico)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("versao", versao(processo)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));
    }

    @Test
    void naSegundaRondaADgtPodeRejeitarDeNovoSoOQueContinuaErrado() throws Exception {
        Cenario cenario = criarCenario("RJC91", "501200091");
        Utilizador tecnico = tecnicoDgt("RJC91");
        Listas listas = listas("RJC91");
        rejeitarTresItens(cenario, tecnico, listas);
        rejeitar(cenario, tecnico, null).andExpect(status().isOk());

        Processo processo = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        processo.setEstado(EstadoProcesso.ENVIADO_DGT);
        processoRepository.saveAndFlush(processo);
        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/capturar")
                        .with(jwtDgt(tecnico)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("versao", versao(processo)))))
                .andExpect(status().isOk());
        gravarItens(cenario, tecnico,
                validado("DOCUMENTO", cenario.documento().getId()),
                validado("DOCUMENTO", cenario.outroDocumento().getId()),
                rejeitado("INFORMACAO_BASE", cenario.deliberacao().getId(), listas, "A data continua errada."));

        rejeitar(cenario, tecnico, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REJEITADO_DGT"));
        assertThat(itens(cenario)).filteredOn(item -> item.sequencia() == 2).singleElement()
                .satisfies(item -> assertThat(item.informacaoBaseId()).isEqualTo(cenario.deliberacao().getId()));
    }

    @Test
    void deveGravarNotasGeraisNulasQuandoVemSoComEspacos() throws Exception {
        Cenario cenario = criarCenario("RJC03", "501200003");
        Utilizador tecnico = tecnicoDgt("RJC03");
        rejeitarTresItens(cenario, tecnico, listas("RJC03"));

        rejeitar(cenario, tecnico, "   ").andExpect(status().isOk());

        assertThat(recusas(cenario)).singleElement().satisfies(recusa -> assertThat(recusa.notasGerais()).isNull());
    }

    @Test
    void deveDevolver400SemMudarOProcessoQuandoNaoHaItensRejeitados() throws Exception {
        Cenario cenario = criarCenario("RJC04", "501200004");
        Utilizador tecnico = tecnicoDgt("RJC04");
        gravarItens(cenario, tecnico, validado("DOCUMENTO", cenario.documento().getId()));
        long versaoAntes = versao(cenario.processo());

        rejeitar(cenario, tecnico, "Notas.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("Indique pelo menos um item rejeitado."))
                .andExpect(jsonPath("$.campos[0].path").value("itens"));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(versao(cenario.processo())).isEqualTo(versaoAntes);
        assertThat(recusas(cenario)).isEmpty();
    }

    @Test
    void deveDevolver400SemGravarNadaQuandoUmItemRejeitadoNaoTemLinhaNoRascunho() throws Exception {
        Cenario cenario = criarCenario("RJC05", "501200005");
        Utilizador tecnico = tecnicoDgt("RJC05");
        gravarItens(cenario, tecnico, rejeitado("DOCUMENTO", cenario.documento().getId(), listas("RJC05"), "Ilegível."));
        ProcessoDocumento semRascunho = processoDocumentoRepository.findById(cenario.outroDocumento().getId()).orElseThrow();
        semRascunho.setEstadoValidacao(EstadoValidacao.REJEITADO);
        processoDocumentoRepository.saveAndFlush(semRascunho);

        rejeitar(cenario, tecnico, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("itens"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique a categoria, o motivo e as notas da rejeição."));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(recusas(cenario)).isEmpty();
        assertThat(linhasNoRascunho(cenario)).isEqualTo(1);
    }

    @Test
    void deveDevolver400NoCampoDoItemQuandoOMotivoFoiDesativadoDepoisDeGravado() throws Exception {
        Cenario cenario = criarCenario("RJC06", "501200006");
        Utilizador tecnico = tecnicoDgt("RJC06");
        Listas listas = listas("RJC06");
        gravarItens(cenario, tecnico, rejeitado("DOCUMENTO", cenario.documento().getId(), listas, "Ilegível."));
        ListaValores motivo = listaValoresRepository.findById(listas.motivo().getId()).orElseThrow();
        motivo.setAtivo(false);
        listaValoresRepository.saveAndFlush(motivo);

        rejeitar(cenario, tecnico, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("itens[" + cenario.documento().getId() + "].motivoId"));

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(recusas(cenario)).isEmpty();
        assertThat(linhasNoRascunho(cenario)).isEqualTo(1);
    }

    @Test
    void deveAceitarItensSemCategoriaNemMotivoGravadosComAsListasVazias() throws Exception {
        Cenario cenario = criarCenario("RJC07", "501200007");
        Utilizador tecnico = tecnicoDgt("RJC07");
        semListasDeRecusa();
        gravarItens(cenario, tecnico, rejeitado("DOCUMENTO", cenario.documento().getId(), null, "Ficheiro corrompido."));
        listas("RJC07");

        rejeitar(cenario, tecnico, null).andExpect(status().isOk());

        assertThat(itens(cenario)).singleElement().satisfies(item -> {
            assertThat(item.categoriaId()).isNull();
            assertThat(item.motivoId()).isNull();
            assertThat(item.notas()).isEqualTo("Ficheiro corrompido.");
        });
    }

    @Test
    void naoDeveDeixarRecusaNemApagarORascunhoQuandoATransacaoReverte() throws Exception {
        Cenario cenario = criarCenario("RJC08", "501200008");
        Utilizador tecnico = tecnicoDgt("RJC08");
        rejeitarTresItens(cenario, tecnico, listas("RJC08"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("token")
                .header("alg", "RS256").claim(SUB, tecnico.getId().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "TECNICO").build()));
        try {
            // Uma falha depois da recusa criada, dentro da mesma transação, reverte tudo.
            int recusasAntesDaFalha = transactionTemplate.execute(estado -> {
                processoService.rejeitar(cenario.processo().getId(),
                        new RejeitarProcessoRequest(versao(cenario.processo()), "Notas."));
                entityManager.flush();
                estado.setRollbackOnly();
                return recusas(cenario).size() + itens(cenario).size();
            });
            assertThat(recusasAntesDaFalha).isEqualTo(4);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(estado(cenario)).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(recusas(cenario)).isEmpty();
        assertThat(itens(cenario)).isEmpty();
        assertThat(linhasNoRascunho(cenario)).isEqualTo(3);
    }

    @Test
    void deveAuditarASequenciaEONumeroDeItensSemAsNotas() throws Exception {
        Cenario cenario = criarCenario("RJC09", "501200009");
        Utilizador tecnico = tecnicoDgt("RJC09");
        rejeitarTresItens(cenario, tecnico, listas("RJC09"));

        rejeitar(cenario, tecnico, "Nota geral confidencial RJC09.").andExpect(status().isOk());

        String dados = auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "PROCESSO", cenario.processo().getId().toString(), "REJEITAR").orElseThrow().getDadosAlteracao();
        assertThat(objectMapper.readTree(dados).path("sequencia").asInt()).isEqualTo(1);
        assertThat(objectMapper.readTree(dados).path("itens").asInt()).isEqualTo(3);
        assertThat(dados).doesNotContain("confidencial").doesNotContain("ilegível").doesNotContain("motivoId");
    }

    @Test
    void deveMostrarAoAutorADataEAsNotasGeraisDaUltimaRecusa() throws Exception {
        Cenario cenario = criarCenario("RJC10", "501200010");
        Utilizador tecnico = tecnicoDgt("RJC10");
        rejeitarTresItens(cenario, tecnico, listas("RJC10"));
        rejeitar(cenario, tecnico, "Rever o regulamento.").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimaRejeicaoDgt.notasGerais").value("Rever o regulamento."))
                .andExpect(jsonPath("$.ultimaRejeicaoDgt.data").isNotEmpty())
                .andExpect(jsonPath("$.ultimaRejeicaoDgt.motivoCodigo").isEmpty())
                .andExpect(jsonPath("$.rejeicoesEmValidacao.length()").value(0));
    }
}
