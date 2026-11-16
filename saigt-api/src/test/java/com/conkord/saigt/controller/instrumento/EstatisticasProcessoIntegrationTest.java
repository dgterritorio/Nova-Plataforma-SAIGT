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
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBaseRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.RejeicaoProcessoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
class EstatisticasProcessoIntegrationTest {

    private static final String SEPARADOR_REGULAMENTO = "Alteração ao Regulamento";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RejeicaoProcessoService rejeicaoProcessoService;

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
    private ListaValoresRepository listaValoresRepository;

    private record Cenario(Processo processo, Utilizador autor, String nif, ProcessoDocumento documento,
                           ProcessoInformacaoBase deliberacao, Utilizador tecnico, ListaValores categoria,
                           ListaValores motivo) {
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
        DocumentoConfig regulamento = documentoConfigRepository.saveAndFlush(new DocumentoConfig(procedimentoConfig,
                "REG_" + codigo, "Regulamento", SEPARADOR_REGULAMENTO, CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo + "@cm-" + codigo + ".pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        ProcessoDocumento documento = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, regulamento,
                CategoriaDocumento.PUBLICACAO, "pdf", "alteracao-regulamento-v1.pdf",
                "privado/" + processo.getId() + "/regulamento.pdf", "a".repeat(64), "v1", 10L, autor));
        ProcessoInformacaoBase deliberacao = processoInformacaoBaseRepository.saveAndFlush(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));
        Utilizador tecnico = new Utilizador("Técnico " + codigo, "tecnico" + codigo + "@dgt.pt", "AD_DGT");
        tecnico.setPerfilInterno(PerfilInterno.TECNICO);
        return new Cenario(processo, autor, nif, documento, deliberacao, utilizadorRepository.saveAndFlush(tecnico),
                listaValoresRepository.saveAndFlush(new ListaValores("CATEGORIA_REJEICAO_DGT", "F" + codigo, "Formato", 1)),
                listaValoresRepository.saveAndFlush(new ListaValores("MOTIVO_REJEICAO_DGT", "N" + codigo,
                        "Ficheiro em formato não editável", 1)));
    }

    private static RequestPostProcessor jwtDgt(Utilizador utilizador) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "TECNICO"));
    }

    private static RequestPostProcessor jwtDaEntidade(UUID utilizadorId, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", "Entidade Teste", "pcm", false)));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDaEntidade(cenario.autor().getId(), cenario.processo().getEntidade());
    }

    private long versao(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow().getVersao();
    }

    private void mudarEstado(Cenario cenario, EstadoProcesso estado) {
        Processo processo = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        processo.setEstado(estado);
        processoRepository.saveAndFlush(processo);
    }

    /** O que `submeter` deixa na auditoria e de que a contagem de submissões depende. */
    private void submeter(Cenario cenario) throws InterruptedException {
        mudarEstado(cenario, EstadoProcesso.ENVIADO_DGT);
        auditoriaRepository.save(Auditoria.builder().tipoOcorrencia("PROCESSO")
                .idOcorrencia(cenario.processo().getId().toString()).acao("SUBMETER").estadoAntes("EM_SUBMISSAO")
                .estadoDepois("ENVIADO_DGT").utilizadorId(cenario.autor().getId()).build());
        // As datas comparam-se ao milissegundo: uma pausa garante que a recusa seguinte fica depois.
        Thread.sleep(5);
    }

    private Map<String, Object> rejeitado(String tipo, UUID itemId, Cenario cenario, String notas) {
        Map<String, Object> item = new HashMap<>();
        item.put("tipo", tipo);
        item.put("itemId", itemId.toString());
        item.put("decisao", "REJEITADO");
        item.put("categoriaId", cenario.categoria().getId());
        item.put("motivoId", cenario.motivo().getId());
        item.put("notas", notas);
        return item;
    }

    private void rejeitarPelaDgt(Cenario cenario, List<Map<String, Object>> itens, String notasGerais) throws Exception {
        mudarEstado(cenario, EstadoProcesso.EM_VALIDACAO_DGT);
        mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/validacao/itens")
                        .with(jwtDgt(cenario.tecnico())).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("itens", itens,
                                "versao", versao(cenario.processo())))))
                .andExpect(status().isOk());
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", versao(cenario.processo()));
        corpo.put("notasGerais", notasGerais);
        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/rejeitar")
                        .with(jwtDgt(cenario.tecnico())).contentType("application/json")
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk());
        Thread.sleep(5);
    }

    private void anularPelaIncm(Cenario cenario, List<String> mensagens) throws InterruptedException {
        mudarEstado(cenario, EstadoProcesso.ANULADO_INCM);
        transactionTemplate.executeWithoutResult(estado -> rejeicaoProcessoService.registarRecusaIncm(
                processoRepository.findById(cenario.processo().getId()).orElseThrow(), mensagens));
        Thread.sleep(5);
    }

    /** Recusada pela DGT, corrigida e submetida, anulada pela INCM, corrigida de novo e rejeitada pela DGT. */
    private Cenario cenarioComTresRecusas(String codigo, String nif) throws Exception {
        Cenario cenario = criarCenario(codigo, nif);
        submeter(cenario);
        rejeitarPelaDgt(cenario, List.of(
                rejeitado("DOCUMENTO", cenario.documento().getId(), cenario, "Solicitar novo carregamento em PDF/A."),
                rejeitado("INFORMACAO_BASE", cenario.deliberacao().getId(), cenario, "Falta a data da deliberação.")),
                "Rever o regulamento.");
        submeter(cenario);
        anularPelaIncm(cenario, List.of("ImageURLList cannot be empty.", "ActDate inválida."));
        submeter(cenario);
        rejeitarPelaDgt(cenario, List.of(
                rejeitado("DOCUMENTO", cenario.documento().getId(), cenario, "Continua sem assinatura."),
                Map.of("tipo", "INFORMACAO_BASE", "itemId", cenario.deliberacao().getId().toString(),
                        "decisao", "VALIDADO")), null);
        return cenario;
    }

    private String url(Cenario cenario) {
        return "/api/v1/processos/" + cenario.processo().getId() + "/estatisticas";
    }

    @Test
    void deveNumerarAsRecusasDgtEIncmComASubmissaoRecusadaEOsItens() throws Exception {
        Cenario cenario = cenarioComTresRecusas("EST01", "501300001");

        mockMvc.perform(get(url(cenario)).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroSubmissoes").value(3))
                .andExpect(jsonPath("$.recusas[*].sequencia", contains(1, 2, 3)))
                .andExpect(jsonPath("$.recusas[*].origem", contains("DGT", "INCM", "DGT")))
                .andExpect(jsonPath("$.recusas[*].submissao", contains(1, 2, 3)))
                .andExpect(jsonPath("$.recusas[0].notasGerais").value("Rever o regulamento."))
                .andExpect(jsonPath("$.recusas[0].data").isNotEmpty())
                .andExpect(jsonPath("$.recusas[0].motivoIncm", hasSize(0)))
                .andExpect(jsonPath("$.recusas[0].itens", hasSize(2)))
                .andExpect(jsonPath("$.recusas[0].itens[0].tipo").value("DOCUMENTO"))
                .andExpect(jsonPath("$.recusas[0].itens[0].documentoId").value(cenario.documento().getId().toString()))
                .andExpect(jsonPath("$.recusas[0].itens[0].nomeFicheiro").value("alteracao-regulamento-v1.pdf"))
                .andExpect(jsonPath("$.recusas[0].itens[0].separador").value(SEPARADOR_REGULAMENTO))
                .andExpect(jsonPath("$.recusas[0].itens[0].elemento").isEmpty())
                .andExpect(jsonPath("$.recusas[0].itens[0].categoria.codigo").value("FEST01"))
                .andExpect(jsonPath("$.recusas[0].itens[0].categoria.descricao").value("Formato"))
                .andExpect(jsonPath("$.recusas[0].itens[0].motivo.descricao").value("Ficheiro em formato não editável"))
                .andExpect(jsonPath("$.recusas[0].itens[0].notas").value("Solicitar novo carregamento em PDF/A."))
                .andExpect(jsonPath("$.recusas[0].itens[1].tipo").value("INFORMACAO_BASE"))
                .andExpect(jsonPath("$.recusas[0].itens[1].elemento").value("DELIBERACAO"))
                .andExpect(jsonPath("$.recusas[0].itens[1].documentoId").isEmpty())
                .andExpect(jsonPath("$.recusas[0].itens[1].nomeFicheiro").isEmpty())
                .andExpect(jsonPath("$.recusas[0].itens[1].notas").value("Falta a data da deliberação."))
                .andExpect(jsonPath("$.recusas[1].motivoIncm", contains("ImageURLList cannot be empty.", "ActDate inválida.")))
                .andExpect(jsonPath("$.recusas[1].itens", hasSize(0)))
                .andExpect(jsonPath("$.recusas[1].notasGerais").isEmpty())
                .andExpect(jsonPath("$.recusas[2].notasGerais").isEmpty())
                .andExpect(jsonPath("$.recusas[2].itens[*].notas", contains("Continua sem assinatura.")));
    }

    @Test
    void deveFiltrarPelaSubmissaoEPelaData() throws Exception {
        Cenario cenario = cenarioComTresRecusas("EST02", "501300002");

        mockMvc.perform(get(url(cenario)).param("submissao", "2").with(jwtDgt(cenario.tecnico())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroSubmissoes").value(3))
                .andExpect(jsonPath("$.recusas[*].sequencia", contains(2)));
        mockMvc.perform(get(url(cenario)).param("dataDe", java.time.LocalDate.now().plusDays(1).toString())
                        .with(jwtDgt(cenario.tecnico())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recusas", hasSize(0)));
        mockMvc.perform(get(url(cenario)).param("dataAte", java.time.LocalDate.now().plusDays(1).toString())
                        .with(jwtDgt(cenario.tecnico())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recusas", hasSize(3)));
    }

    @Test
    void deveDevolver400ComOParametroInvalido() throws Exception {
        Cenario cenario = criarCenario("EST03", "501300003");

        for (Map.Entry<String, String> parametro : List.of(Map.entry("submissao", "0"), Map.entry("submissao", "dois"),
                Map.entry("dataDe", "2026-13-01"), Map.entry("dataAte", "ontem"))) {
            mockMvc.perform(get(url(cenario)).param(parametro.getKey(), parametro.getValue()).with(jwtDoAutor(cenario)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value(parametro.getKey()));
        }
    }

    @Test
    void deveDevolver404AOutraEntidadeE401SemToken() throws Exception {
        Cenario cenario = criarCenario("EST04", "501300004");
        Entidade outra = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501300044", "Outra Câmara"));
        Utilizador deOutra = utilizadorRepository.saveAndFlush(new Utilizador("Rui Sousa", "rui.sousa@cm-outra.pt", "INCM"));

        mockMvc.perform(get(url(cenario)).with(jwtDaEntidade(deOutra.getId(), outra)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        mockMvc.perform(get(url(cenario)))
                .andExpect(status().isUnauthorized());
    }
}
