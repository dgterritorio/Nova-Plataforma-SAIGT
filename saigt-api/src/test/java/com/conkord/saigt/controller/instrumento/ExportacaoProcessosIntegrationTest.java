package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
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
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.configuration.LogJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A base é partilhada pelos testes da classe: cada teste usa uma designação própria e filtra por ela, para contar
 * só os seus processos quando o perfil vê todas as entidades.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ExportacaoProcessosIntegrationTest {

    private static final String URL = "/api/v1/processos/exportacao";
    private static final String TIPO_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final List<String> CABECALHO =
            List.of("Referência", "Tipo IGT", "Designação", "Dinâmica", "Procedimento", "Concelho", "Estado", "Utilizador");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntidadeRepository entidadeRepository;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired private InstrumentoRepository instrumentoRepository;
    @Autowired private DinamicaRepository dinamicaRepository;
    @Autowired private ProcessoRepository processoRepository;
    @Autowired private InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    @Autowired private SnitReferenciaRepository snitReferenciaRepository;

    @Test
    void oMasterExportaEmExcelUmaFolhaComOCabecalhoEUmaLinhaPorProcesso() throws Exception {
        Cenario cenario = cenarioComTresProcessos("Exc");

        MvcResult resultado = mockMvc.perform(exportar("EXCEL", cenario.designacao()).with(interno("MASTER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", TIPO_EXCEL))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"processos.xlsx\""))
                .andReturn();

        try (XSSFWorkbook livro = new XSSFWorkbook(new ByteArrayInputStream(resultado.getResponse().getContentAsByteArray()))) {
            assertThat(livro.getNumberOfSheets()).isEqualTo(1);
            List<List<String>> linhas = linhas(livro.getSheetAt(0));
            assertThat(linhas.get(0)).isEqualTo(CABECALHO);
            assertThat(linhas).hasSize(4);
            String tipo = cenario.tiposInstrumento().get(2);
            assertThat(linhas.get(1)).containsExactly(cenario.processos().get(2).getNumeroProcesso(), tipo,
                    cenario.designacao(), "Elaboração", "Procedimento " + tipo, "", "Em submissão",
                    "Utilizador " + cenario.designacao());
        }
    }

    @Test
    void aColunaConcelhoTemOsNomesDosConcelhosDoInstrumentoNoExcelENoPdf() throws Exception {
        String designacao = designacaoUnica("Concelho");
        novoProcesso(designacao, novaEntidade(), novoUtilizador(designacao), EstadoProcesso.EM_SUBMISSAO);
        // A réplica guarda a designação do SNIT em maiúsculas; o ficheiro mostra-a como o ecrã.
        municipio("9583", "SINTRA");
        municipio("9584", "VILA NOVA DE GAIA");
        Instrumento instrumento = instrumentoRepository.findAll().stream()
                .filter(candidato -> designacao.equals(candidato.getDesignacao())).findFirst().orElseThrow();
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumento, "9584"));
        instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumento, "9583"));

        List<List<String>> linhas = linhasDoExcel(mockMvc.perform(exportar("EXCEL", designacao).with(interno("MASTER")))
                .andExpect(status().isOk()).andReturn());
        assertThat(linhas.get(1).get(CABECALHO.indexOf("Concelho"))).isEqualTo("Sintra, Vila Nova de Gaia");

        MvcResult pdf = mockMvc.perform(exportar("PDF", designacao).with(interno("MASTER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(textoDoPdf(pdf.getResponse().getContentAsByteArray()).replaceAll("\\s+", " ")).contains("Sintra, Vila Nova de Gaia");
    }

    @Test
    void oMasterExportaEmPdfATabelaComAsMesmasColunasELinhas() throws Exception {
        Cenario cenario = cenarioComTresProcessos("Pdf");

        MvcResult resultado = mockMvc.perform(exportar("PDF", cenario.designacao()).with(interno("MASTER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"processos.pdf\""))
                .andReturn();

        String texto = textoDoPdf(resultado.getResponse().getContentAsByteArray());
        assertThat(texto).contains(CABECALHO);
        for (Processo processo : cenario.processos()) {
            assertThat(texto).contains(processo.getNumeroProcesso());
        }
        assertThat(texto.split("Em submissão", -1)).hasSize(4);
    }

    @Test
    void oFicheiroTemAsLinhasDaListagemParaOsMesmosFiltrosSomandoAsPaginasPelaMesmaOrdem() throws Exception {
        String designacao = designacaoUnica("Lisboa");
        Entidade entidade = novaEntidade();
        Utilizador autor = novoUtilizador(designacao);
        for (int i = 0; i < 3; i++) {
            novoProcesso(designacao, entidade, autor, EstadoProcesso.EM_VALIDACAO_DGT);
        }
        novoProcesso(designacao, entidade, autor, EstadoProcesso.EM_SUBMISSAO);

        List<String> listagem = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            JsonNode corpo = objectMapper.readTree(mockMvc.perform(get("/api/v1/processos")
                            .param("estado", "EM_VALIDACAO_DGT").param("designacaoInstrumento", designacao)
                            .param("page", String.valueOf(pagina)).param("size", "1").with(interno("TECNICO")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            corpo.get("content").forEach(linha -> listagem.add(linha.get("numeroProcesso").asText()));
        }

        List<List<String>> linhas = linhasDoExcel(mockMvc.perform(get(URL).param("formato", "EXCEL")
                        .param("estado", "EM_VALIDACAO_DGT").param("designacaoInstrumento", designacao)
                        .with(interno("TECNICO")))
                .andExpect(status().isOk()).andReturn());
        assertThat(listagem).hasSize(3);
        assertThat(colunaReferencia(linhas)).isEqualTo(listagem);
        assertThat(linhas.subList(1, linhas.size())).allSatisfy(linha -> assertThat(linha.get(CABECALHO.indexOf("Estado"))).isEqualTo("Em Validação DGT"));
    }

    @Test
    void ordenaPorNumeroDeProcessoAscendenteEmParaCorrecaoEDescendenteNosRestantes() throws Exception {
        String designacao = designacaoUnica("Ordem");
        Entidade entidade = novaEntidade();
        Utilizador autor = novoUtilizador(designacao);
        Processo p1 = novoProcesso(designacao, entidade, autor, EstadoProcesso.REJEITADO_DGT);
        Processo p2 = novoProcesso(designacao, entidade, autor, EstadoProcesso.ANULADO_INCM);
        Processo p3 = novoProcesso(designacao, entidade, autor, EstadoProcesso.REJEITADO_DGT);

        List<List<String>> paraCorrecao = linhasDoExcel(mockMvc.perform(exportar("EXCEL", designacao)
                        .param("grupo", "PARA_CORRECAO").with(externo(entidade, autor)))
                .andExpect(status().isOk()).andReturn());
        assertThat(colunaReferencia(paraCorrecao))
                .containsExactly(p1.getNumeroProcesso(), p2.getNumeroProcesso(), p3.getNumeroProcesso());

        List<List<String>> semGrupo = linhasDoExcel(mockMvc.perform(exportar("EXCEL", designacao)
                        .with(externo(entidade, autor)))
                .andExpect(status().isOk()).andReturn());
        assertThat(colunaReferencia(semGrupo))
                .containsExactly(p3.getNumeroProcesso(), p2.getNumeroProcesso(), p1.getNumeroProcesso());
    }

    @Test
    void oUtilizadorDeUmaEntidadeSoExportaOsSeusEOTecnicoEOVisitanteExportamTodos() throws Exception {
        String designacao = designacaoUnica("Visib");
        Entidade minha = novaEntidade();
        Entidade outra = novaEntidade();
        Utilizador autor = novoUtilizador(designacao);
        Processo meu = novoProcesso(designacao, minha, autor, EstadoProcesso.EM_SUBMISSAO);
        Processo alheio = novoProcesso(designacao, outra, novoUtilizador(designacao + "o"), EstadoProcesso.EM_SUBMISSAO);

        assertThat(colunaReferencia(linhasDoExcel(mockMvc.perform(exportar("EXCEL", designacao)
                        .with(externo(minha, autor))).andExpect(status().isOk()).andReturn())))
                .containsExactly(meu.getNumeroProcesso());
        for (String perfil : List.of("TECNICO", "VISITANTE")) {
            assertThat(colunaReferencia(linhasDoExcel(mockMvc.perform(exportar("EXCEL", designacao)
                            .with(interno(perfil))).andExpect(status().isOk()).andReturn())))
                    .containsExactlyInAnyOrder(meu.getNumeroProcesso(), alheio.getNumeroProcesso());
        }
    }

    @Test
    void oVisitanteExportaEmExcelEEmPdf() throws Exception {
        Cenario cenario = cenarioComTresProcessos("Vis");

        mockMvc.perform(exportar("EXCEL", cenario.designacao()).with(interno("VISITANTE")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", TIPO_EXCEL));
        mockMvc.perform(exportar("PDF", cenario.designacao()).with(interno("VISITANTE")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void recusaOFormatoEmFaltaOuNaoSuportadoSemFicheiro() throws Exception {
        mockMvc.perform(get(URL).with(interno("MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.codigo").value("EXPORTACAO_FORMATO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Formato de exportação não suportado."));
        for (String formato : List.of("CSV", "pdf", "")) {
            mockMvc.perform(get(URL).param("formato", formato).with(interno("MASTER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().doesNotExist("Content-Disposition"))
                    .andExpect(jsonPath("$.codigo").value("EXPORTACAO_FORMATO_INVALIDO"));
        }
    }

    @Test
    void recusaUmFiltroComValorInvalidoIndicandoONomeDoFiltro() throws Exception {
        String[][] pedidos = {{"ano", "abc"}, {"estado", "XPTO"}, {"nutsII", "NORTE;DROP"},
                {"tipoInstrumentoId", "abc"}, {"dataDe", "13-11-2026"}, {"numeroProcesso", "12a"}};
        for (String[] pedido : pedidos) {
            mockMvc.perform(get(URL).param("formato", "EXCEL").param(pedido[0], pedido[1]).with(interno("MASTER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().doesNotExist("Content-Disposition"))
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.mensagem").value("Valor inválido para o filtro " + pedido[0] + "."))
                    .andExpect(jsonPath("$.campos[0].path").value(pedido[0]));
        }
    }

    @Test
    void recusaUmGrupoQueNaoSeAplicaAoPerfil() throws Exception {
        mockMvc.perform(get(URL).param("formato", "PDF").param("grupo", "EM_CURSO").with(interno("MASTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para o filtro grupo."));
    }

    @Test
    void recusaUmParametroQueNaoEFiltroDaListagem() throws Exception {
        for (String nome : List.of("page", "size", "designacao")) {
            mockMvc.perform(get(URL).param("formato", "EXCEL").param(nome, "1").with(interno("MASTER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.mensagem").value("Filtro não suportado nesta listagem: " + nome + "."))
                    .andExpect(jsonPath("$.campos[0].path").value(nome));
        }
    }

    @Test
    void semProcessosDevolveOFicheiroSoComOCabecalho() throws Exception {
        String designacao = designacaoUnica("Vazio");

        List<List<String>> linhas = linhasDoExcel(mockMvc.perform(exportar("EXCEL", designacao).with(interno("MASTER")))
                .andExpect(status().isOk()).andReturn());
        assertThat(linhas).containsExactly(CABECALHO);

        String texto = textoDoPdf(mockMvc.perform(exportar("PDF", designacao).with(interno("MASTER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(texto).contains(CABECALHO);
    }

    @Test
    void semSessaoDevolve401() throws Exception {
        mockMvc.perform(get(URL).param("formato", "EXCEL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oLogDaExportacaoSoTemOFormatoONumeroDeLinhasEOTempo() throws Exception {
        Cenario cenario = cenarioComTresProcessos("Log");
        LogJson log = LogJson.capturar();
        String linha;
        try {
            mockMvc.perform(exportar("PDF", cenario.designacao()).with(interno("MASTER"))).andExpect(status().isOk());
            linha = log.linhas().stream().filter(l -> l.contains("Exportação de processos gerada"))
                    .reduce((primeira, ultima) -> ultima).orElseThrow();
        } finally {
            log.parar();
        }

        // O log é JSON numa linha (US-317): o formato, as linhas e o tempo vêm na mensagem.
        assertThat(LogJson.ler(linha).path("message").asText())
                .containsPattern("\\| formato=PDF linhas=3 duracaoMs=\\d+$");
        assertThat(linha).doesNotContain(cenario.designacao());
        for (Processo processo : cenario.processos()) {
            assertThat(linha).doesNotContain(processo.getNumeroProcesso());
        }
    }

    // --- Dados e auxiliares ---

    /** `tiposInstrumento` alinhado com `processos`: cada processo tem o seu tipo de Instrumento. */
    private record Cenario(String designacao, List<String> tiposInstrumento, List<Processo> processos) {
    }

    /** Três processos da mesma entidade e designação, por ordem de criação (n.º de processo crescente). */
    private Cenario cenarioComTresProcessos(String prefixo) {
        String designacao = designacaoUnica(prefixo);
        Entidade entidade = novaEntidade();
        Utilizador autor = novoUtilizador(designacao);
        List<Processo> processos = new ArrayList<>();
        List<String> tipos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String codigo = novoCodigo();
            processos.add(novoProcesso(codigo, designacao, entidade, autor, EstadoProcesso.EM_SUBMISSAO));
            tipos.add(codigo);
        }
        return new Cenario(designacao, tipos, processos);
    }

    private static String novoCodigo() {
        return "E333-" + SEQUENCIA.incrementAndGet();
    }

    private static String designacaoUnica(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Entidade novaEntidade() {
        String nif = "5033" + String.format("%05d", SEQUENCIA.incrementAndGet());
        return entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + nif));
    }

    private Utilizador novoUtilizador(String designacao) {
        return utilizadorRepository.saveAndFlush(
                new Utilizador("Utilizador " + designacao, UUID.randomUUID() + "@cm-teste.pt", "INCM"));
    }

    private Processo novoProcesso(String designacao, Entidade entidade, Utilizador autor, EstadoProcesso estado) {
        return novoProcesso(novoCodigo(), designacao, entidade, autor, estado);
    }

    private Processo novoProcesso(String codigo, String designacao, Entidade entidade, Utilizador autor,
                                  EstadoProcesso estado) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamicaElaboracao()));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento("P-" + codigo, "Procedimento " + codigo)), 1, "SEGUNDA_H"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", designacao, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Elaboração", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));
        processo.setEstado(estado);
        return processoRepository.saveAndFlush(processo);
    }

    private void municipio(String dtcc, String nome) {
        if (snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, dtcc).isEmpty()) {
            snitReferenciaRepository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, dtcc, nome,
                    "{\"dtcc\":\"" + dtcc + "\",\"name\":\"" + nome + "\"}", Instant.parse("2026-11-16T10:00:00Z")));
        }
    }

    private TipoDinamica tipoDinamicaElaboracao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> "ELABORACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
    }

    private static MockHttpServletRequestBuilder exportar(String formato, String designacao) {
        return get(URL).param("formato", formato).param("designacaoInstrumento", designacao);
    }

    private static RequestPostProcessor interno(String perfil) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", perfil));
    }

    private static RequestPostProcessor externo(Entidade entidade, Utilizador utilizador) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", "Entidade Teste", "pcm", false)));
    }

    private static List<List<String>> linhasDoExcel(MvcResult resultado) throws IOException {
        try (XSSFWorkbook livro = new XSSFWorkbook(new ByteArrayInputStream(resultado.getResponse().getContentAsByteArray()))) {
            return linhas(livro.getSheetAt(0));
        }
    }

    private static List<List<String>> linhas(Sheet folha) {
        List<List<String>> linhas = new ArrayList<>();
        for (Row linha : folha) {
            List<String> valores = new ArrayList<>();
            linha.forEach(celula -> valores.add(celula.getStringCellValue()));
            linhas.add(valores);
        }
        return linhas;
    }

    private static List<String> colunaReferencia(List<List<String>> linhas) {
        return linhas.stream().skip(1).map(linha -> linha.get(0)).toList();
    }

    private static String textoDoPdf(byte[] conteudo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(conteudo)) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
