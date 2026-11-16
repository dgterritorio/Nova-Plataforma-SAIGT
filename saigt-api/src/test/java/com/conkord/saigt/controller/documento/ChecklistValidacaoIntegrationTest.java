package com.conkord.saigt.controller.documento;

import com.conkord.saigt.exception.StorageIndisponivelException;
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
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CaracteristicasTiff;
import com.conkord.saigt.service.documento.CaracteristicasTiff.BandaTiff;
import com.conkord.saigt.service.documento.InspetorTiff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checklist de validação DGT pelo endpoint real, com os ZIP gravados no RustFS. Os ficheiros entram diretamente na
 * base e no bucket: um `.jpg` numa peça escrita de publicação e um ZIP com pastas já não passam no carregamento.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ChecklistValidacaoIntegrationTest extends StorageTestcontainersBase {

    private static final String MARCA = "CK";
    private static final String SERIE_NIPC = "5183021";
    private static final String HASH = "a".repeat(64);
    private static final String MANUAL_ZIP = "Não foi possível verificar automaticamente; verifique o ZIP à mão.";
    private static final String FOLHA = "Confirme que o n.º de folha do nome do ficheiro corresponde à planta.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private ArmazenamentoDocumentos armazenamento;

    /** O GDAL não corre aqui: a leitura das suas saídas está no {@code GdalinfoInspetorTiffTest}. */
    @MockBean
    private InspetorTiff inspetor;

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig escritaPublicacao,
                           DocumentoConfig escritaDeposito, DocumentoConfig graficaDeposito) {
    }

    @Test
    void deveDevolverAsLinhasDosSeisItensPelaOrdemDoFr092ComOsCamposDoContrato(CapturedOutput saida)
            throws Exception {
        Cenario cenario = criarCenario("01");
        ProcessoDocumento docx = gravar(cenario, cenario.escritaPublicacao(), CategoriaDocumento.PUBLICACAO,
                "Regulamento.docx", "docx".getBytes(StandardCharsets.UTF_8));
        ProcessoDocumento pdf = gravar(cenario, cenario.escritaDeposito(), CategoriaDocumento.DEPOSITO,
                "Relatorio.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        ProcessoDocumento jpg = gravar(cenario, cenario.escritaPublicacao(), CategoriaDocumento.PUBLICACAO,
                "Quadro.jpg", "jpg".getBytes(StandardCharsets.UTF_8));
        ProcessoDocumento plantas = gravar(cenario, cenario.graficaDeposito(), CategoriaDocumento.DEPOSITO,
                "Plantas.zip", ZipsDeTeste.comEntradas("1199_Ord_A.tif", "1199_Ord_A.tfw", "1199_Ord_B.tif",
                        "1199_Ord_C.tif", "1199_Ord_C.tfw"));
        ProcessoDocumento antigo = gravar(cenario, null, CategoriaDocumento.DEPOSITO, "Antigo.zip",
                ZipsDeTeste.comEntradas("dados/", "dados/plano.gpkg"));
        when(inspetor.inspecionar(eq(plantas.getCaminhoFicheiro()), anyString())).thenReturn(tiff(null, false));
        when(inspetor.inspecionar(plantas.getCaminhoFicheiro(), "1199_Ord_A.tif")).thenReturn(tiff(3763, true));
        when(inspetor.inspecionar(plantas.getCaminhoFicheiro(), "1199_Ord_C.tif")).thenReturn(tiff(null, true));

        JsonNode linhas = pedir(cenario);

        assertThat(linhas(linhas)).containsExactly(
                linha("CORRESPONDENCIA_ATO_TEXTO", null, null, "MANUAL",
                        "Confirme que o texto do ato corresponde ao diploma."),
                linha("FORMATO_QUADRO_ANEXO", jpg, "Quadro.jpg", "FALHA", "Formato jpg não é editável para publicação."),
                linha("FORMATO_QUADRO_ANEXO", docx, "Regulamento.docx", "OK", null),
                linha("FORMATO_QUADRO_ANEXO", pdf, "Relatorio.pdf", "OK", null),
                linha("GEORREFERENCIACAO_MATRICIAL", plantas, "1199_Ord_A.tif", "OK",
                        "GeoTIFF com sistema de referência EPSG:3763."),
                linha("GEORREFERENCIACAO_MATRICIAL", plantas, "1199_Ord_B.tif", "FALHA",
                        "Sem georreferenciação: falta o sistema de referência no TIFF ou o ficheiro 1199_Ord_B.tfw."),
                linha("GEORREFERENCIACAO_MATRICIAL", plantas, "1199_Ord_C.tif", "MANUAL",
                        "TIF com TFW: confirme o sistema de referência, que o ficheiro não indica."),
                linha("CONSISTENCIA_VETORIAL_MATRICIAL", null, null, "MANUAL",
                        "Confirme a consistência entre a informação vetorial e as plantas matriciais."),
                linha("FOLHA_NOME_TIF", plantas, "1199_Ord_A.tif", "MANUAL", FOLHA),
                linha("FOLHA_NOME_TIF", plantas, "1199_Ord_B.tif", "MANUAL", FOLHA),
                linha("FOLHA_NOME_TIF", plantas, "1199_Ord_C.tif", "MANUAL", FOLHA),
                linha("CORRECAO_ZIP", antigo, "Antigo.zip", "FALHA",
                        "O ZIP contém pastas ou ficheiros com nome inválido: dados/, dados/plano.gpkg."),
                linha("CORRECAO_ZIP", plantas, "Plantas.zip", "OK", null));
        linhas.forEach(linha -> assertThat(iteravel(linha.fieldNames()))
                .containsExactly("verificacao", "documentoId", "nomeFicheiro", "resultado", "detalhe"));
        assertThat(saida.getOut()).contains("processoId=" + cenario.processo().getId() + " verificacoes=13 falhas=3")
                .doesNotContain("1199_Ord").doesNotContain("Quadro.jpg");
    }

    @Test
    void deveResponder200ComOZipEmManualEAvisarComOsIdsQuandoOArmazenamentoEstaIndisponivel(CapturedOutput saida)
            throws Exception {
        Cenario cenario = criarCenario("02");
        ProcessoDocumento plantas = gravar(cenario, cenario.graficaDeposito(), CategoriaDocumento.DEPOSITO,
                "Plantas.zip", ZipsDeTeste.comEntradas("1199_Ord_A.tif", "1199_Ord_A.tfw"));
        doThrow(new StorageIndisponivelException("Armazenamento de documentos temporariamente indisponível.",
                new IOException("ligação recusada")))
                .when(armazenamento).abrir(contains(plantas.getId().toString()), any(), anyLong(), anyLong());

        JsonNode linhas = pedir(cenario);

        assertThat(linhas(linhas)).containsExactly(
                linha("CORRESPONDENCIA_ATO_TEXTO", null, null, "MANUAL",
                        "Confirme que o texto do ato corresponde ao diploma."),
                linha("GEORREFERENCIACAO_MATRICIAL", plantas, "Plantas.zip", "MANUAL", MANUAL_ZIP),
                linha("CONSISTENCIA_VETORIAL_MATRICIAL", null, null, "MANUAL",
                        "Confirme a consistência entre a informação vetorial e as plantas matriciais."),
                linha("FOLHA_NOME_TIF", plantas, "Plantas.zip", "MANUAL", FOLHA),
                linha("CORRECAO_ZIP", plantas, "Plantas.zip", "MANUAL", MANUAL_ZIP));
        assertThat(saida.getOut()).contains("WARN")
                .contains("processoId=" + cenario.processo().getId() + " processoDocumentoId=" + plantas.getId())
                .doesNotContain("Plantas.zip");
    }

    @Test
    void deveResponder404AUmaEntidadeSemVisibilidadeE401SemToken() throws Exception {
        Cenario cenario = criarCenario("03");
        Entidade outra = entidadeRepository.saveAndFlush(EntidadeTeste.nova(SERIE_NIPC + "93", "Câmara " + MARCA + "93"));
        Utilizador outroUtilizador = utilizadorRepository.saveAndFlush(
                new Utilizador("Outro " + MARCA, "outro" + MARCA.toLowerCase() + "93@cm-checklist.pt", "INCM"));

        mockMvc.perform(get(url(cenario.processo())).with(jwtDe(outra, outroUtilizador.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
        mockMvc.perform(get(url(cenario.processo())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/processos/" + UUID.randomUUID() + "/documentos/verificacoes")
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isNotFound());
    }

    private JsonNode pedir(Cenario cenario) throws Exception {
        String corpo = mockMvc.perform(get(url(cenario.processo()))
                        .with(jwtDe(cenario.entidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(corpo);
    }

    private static List<List<String>> linhas(JsonNode linhas) {
        List<List<String>> resultado = new ArrayList<>();
        linhas.forEach(linha -> resultado.add(java.util.Arrays.asList(texto(linha, "verificacao"),
                texto(linha, "documentoId"), texto(linha, "nomeFicheiro"), texto(linha, "resultado"),
                texto(linha, "detalhe"))));
        return resultado;
    }

    private static String texto(JsonNode linha, String campo) {
        JsonNode valor = linha.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static List<String> linha(String verificacao, ProcessoDocumento documento, String nome, String resultado,
                                      String detalhe) {
        return java.util.Arrays.asList(verificacao, documento == null ? null : documento.getId().toString(), nome,
                resultado, detalhe);
    }

    private static CaracteristicasTiff tiff(Integer codigoEpsg, boolean georreferenciado) {
        return new CaracteristicasTiff("GTiff", codigoEpsg, "NONE", List.of(new BandaTiff("Byte", "Red"),
                new BandaTiff("Byte", "Green"), new BandaTiff("Byte", "Blue")), 300.0, 300.0, 2, georreferenciado);
    }

    private static List<String> iteravel(java.util.Iterator<String> nomes) {
        List<String> lista = new ArrayList<>();
        nomes.forEachRemaining(lista::add);
        return lista;
    }

    private ProcessoDocumento gravar(Cenario cenario, DocumentoConfig item, CategoriaDocumento categoria, String nome,
                                     byte[] conteudo) {
        UUID id = UUID.randomUUID();
        String caminho = "processos/" + cenario.processo().getNumeroProcesso() + "/" + categoria + "/" + id + "-"
                + nome;
        String versao = armazenamento.guardar(caminho, new ByteArrayInputStream(conteudo), "application/octet-stream");
        String extensao = nome.substring(nome.lastIndexOf('.') + 1).toLowerCase();
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(id, cenario.processo(), item, categoria,
                extensao, nome, caminho, HASH, versao == null ? "null" : versao, conteudo.length, cenario.autor()));
    }

    private Cenario criarCenario(String numero) {
        String codigo = MARCA + numero;
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DOCK-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(
                new TipoDinamica("ELAB-DOCK-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-DOCK-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        DocumentoConfig escritaPublicacao = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REG_PUB", "Regulamento", "Regulamento",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"docx\",\"pdf\"]", 1));
        DocumentoConfig escritaDeposito = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "REL_DEP", "Relatório", "Relatório",
                        CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, FormatoDocumento.NAO_EDITAVEL,
                        "[\"pdf\"]", 2));
        DocumentoConfig graficaDeposito = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "PLA_DEP", "Plantas", "Plantas",
                        CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                        "[\"zip\"]", 3));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(SERIE_NIPC + numero, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo.toLowerCase() + "@cm-checklist.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DOCK/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(
                new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        return new Cenario(processo, autor, entidade, escritaPublicacao, escritaDeposito, graficaDeposito);
    }

    private RequestPostProcessor jwtDe(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static String url(Processo processo) {
        return "/api/v1/processos/" + processo.getId() + "/documentos/verificacoes";
    }
}
