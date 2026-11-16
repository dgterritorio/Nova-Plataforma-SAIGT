package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.instrumento.EnviarPcmRequest;
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
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Destino do processo de ponta a ponta: gravação, documentos exigidos, carregamento e envio à INCM. O formulário da
 * REN ainda não existe: o processo «da REN» usa um tipo de Procedimento próprio a que o duplo do catálogo dá o
 * formulário `INFORMACAO_GERAL` e a regra «admite só depósito»; o «de PDM» usa o catálogo verdadeiro.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DestinoProcessoIntegrationTest extends StorageTestcontainersBase {

    private static final byte[] CONTEUDO = "%PDF-1.4 destino".getBytes(StandardCharsets.UTF_8);
    private static final String PREFIXO_REN = "REN-DESTINO-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioClient minioClient;

    @SpyBean
    private CatalogoFormularios catalogoFormularios;

    @MockBean
    private EnvioIncmService envioIncmService;

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig publicacao,
                           DocumentoConfig deposito) {
    }

    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Um item obrigatório em cada caixa, ambos de peças escritas (os elementos afetados do processo). */
    private Cenario criarCenario(String sufixo, boolean daRen, String serieDr) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento((daRen ? "RENX-" : "PDMX-") + sufixo, "Instrumento " + sufixo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("DEST-" + sufixo, "Delimitação"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento procedimento = tipoProcedimento(daRen ? PREFIXO_REN + sufixo : "DECISAO_INICIO");
        InstrumentoProcedimentoConfig config = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, procedimento, 1, serieDr));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, config));
        DocumentoConfig publicacao = item(config, "TEXTO_PUB", CategoriaDocumento.PUBLICACAO, FormatoDocumento.EDITAVEL, 1);
        DocumentoConfig deposito = item(config, "TEXTO_DEP", CategoriaDocumento.DEPOSITO, FormatoDocumento.NAO_EDITAVEL, 2);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5094" + sufixo, "Câmara " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cm-destino.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DEST/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Delimitação", autor));
        Processo processo = new Processo(dinamica, config, autor, entidade);
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo = processoRepository.saveAndFlush(processo);
        return new Cenario(processo, autor, entidade, publicacao, deposito);
    }

    private DocumentoConfig item(InstrumentoProcedimentoConfig config, String codigo, CategoriaDocumento categoria,
                                 FormatoDocumento formato, int ordem) {
        DocumentoConfig item = new DocumentoConfig(config, codigo, "Texto " + codigo, "Regulamento", categoria,
                TipoElemento.PECA_ESCRITA, formato, "[\"pdf\"]", ordem);
        item.setObrigatorio(true);
        return documentoConfigRepository.saveAndFlush(item);
    }

    /** O duplo só responde ao tipo de Procedimento «da REN»; os outros seguem o catálogo verdadeiro. */
    private void formularioDaRenAdmiteSoDeposito() {
        doReturn(Optional.of(FormularioFixo.INFORMACAO_GERAL)).when(catalogoFormularios)
                .formularioDe(argThat(tipo -> tipo != null && tipo.getCodigo().startsWith(PREFIXO_REN)));
        doReturn(true).when(catalogoFormularios)
                .admiteSoDeposito(argThat(tipo -> tipo != null && tipo.getCodigo().startsWith(PREFIXO_REN)));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif",
                        cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(), "pcm", false,
                        "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtMaster() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "MASTER"));
    }

    private Processo lido(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private ResultActions gravarDestino(Cenario cenario, String destino) throws Exception {
        String campoDestino = destino == null ? "" : ",\"destino\":\"" + destino + "\"";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDoAutor(cenario)).contentType("application/json")
                .content("{\"versao\":" + lido(cenario).getVersao() + ",\"sumario\":\"Resumo.\","
                        + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"]" + campoDestino + "}"));
    }

    private ResultActions detalhe(Cenario cenario, RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwt));
    }

    private ResultActions estrutura(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/estrutura-documental")
                .with(jwtDoAutor(cenario)));
    }

    private ResultActions carregar(Cenario cenario, DocumentoConfig item) throws Exception {
        return mockMvc.perform(multipart("/api/v1/processos/" + cenario.processo().getId() + "/documentos")
                .file(new MockMultipartFile("ficheiro", "texto.pdf", "application/pdf", CONTEUDO))
                .param("categoria", item.getCategoria().name())
                .param("documentoConfigId", item.getId().toString())
                .with(jwtDoAutor(cenario)));
    }

    private List<String> objetosDoProcesso(Cenario cenario) {
        return StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder().bucket(BUCKET)
                        .prefix("processos/" + lido(cenario).getNumeroProcesso() + "/").recursive(true).build())
                        .spliterator(), false)
                .map(resultado -> {
                    try {
                        return resultado.get().objectName();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    private void passarAValidadoDgt(Cenario cenario, DestinoProcesso destino) {
        Processo processo = lido(cenario);
        processo.setEstado(EstadoProcesso.VALIDADO_DGT);
        processo.setDestino(destino);
        processoRepository.saveAndFlush(processo);
    }

    @Test
    void deveGravarSoDepositoNumProcessoDaRenSemFicheirosEDevolveloNoDetalheENoPainel() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00101", true, "SEGUNDA_H");

        detalhe(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destino").value("PUBLICACAO_DEPOSITO"))
                .andExpect(jsonPath("$.destinoEditavel").value(true));

        gravarDestino(cenario, "SO_DEPOSITO")
                .andExpect(status().isOk());

        assertThat(lido(cenario).getDestino()).isEqualTo(DestinoProcesso.SO_DEPOSITO);
        detalhe(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destino").value("SO_DEPOSITO"))
                .andExpect(jsonPath("$.destinoEditavel").value(true));
        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.processo().getDinamica().getInstrumento().getId())
                        .with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicas[*].procedimentos[*].processos[*].destino").value(hasItem("SO_DEPOSITO")));
    }

    @Test
    void deveManterODestinoQuandoOPedidoOOmite() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00102", true, "SEGUNDA_H");
        gravarDestino(cenario, "SO_DEPOSITO").andExpect(status().isOk());

        gravarDestino(cenario, null).andExpect(status().isOk());

        assertThat(lido(cenario).getDestino()).isEqualTo(DestinoProcesso.SO_DEPOSITO);
    }

    @Test
    void deveRecusarSoDepositoNumProcessoDePdmCom400DestinoInvalidoNoCampoDestino() throws Exception {
        Cenario cenario = criarCenario("00103", false, "SEGUNDA_H");

        gravarDestino(cenario, "SO_DEPOSITO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DESTINO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Este processo não pode ser só para depósito."))
                .andExpect(jsonPath("$.campos[0].path").value("destino"));

        Processo processo = lido(cenario);
        assertThat(processo.getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
        assertThat(processo.getSumario()).isNull();
        detalhe(cenario, jwtDoAutor(cenario))
                .andExpect(jsonPath("$.destino").value("PUBLICACAO_DEPOSITO"))
                .andExpect(jsonPath("$.destinoEditavel").value(false));
    }

    @Test
    void deveRecusarUmDestinoForaDosDoisCom400Validacao() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00104", true, "SEGUNDA_H");

        gravarDestino(cenario, "OUTRO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("destino"))
                .andExpect(jsonPath("$.campos[0].erro").value("Valores aceites: PUBLICACAO_DEPOSITO, SO_DEPOSITO."));

        assertThat(lido(cenario).getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
    }

    @Test
    void soParaDepositoAEstruturaNaoTemItensDePublicacaoEAsPendenciasSaoSoDeDeposito() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00105", true, "SEGUNDA_H");

        estrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].publicacao[*].codigo").value(hasItem("TEXTO_PUB")))
                .andExpect(jsonPath("$.pendencias[*].codigo").value(hasItems("TEXTO_PUB", "TEXTO_DEP")));

        gravarDestino(cenario, "SO_DEPOSITO").andExpect(status().isOk());

        estrutura(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separadores[0].publicacao").isEmpty())
                .andExpect(jsonPath("$.separadores[0].deposito[*].codigo").value(hasItem("TEXTO_DEP")))
                .andExpect(jsonPath("$.pendencias[*].codigo").value(everyItem(is("TEXTO_DEP"))))
                .andExpect(jsonPath("$.pendencias[*].categoria").value(everyItem(is("DEPOSITO"))));
    }

    @Test
    void soParaDepositoRecusaOCarregamentoNumItemDePublicacaoSemGravarNadaEAceitaODeDeposito() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00106", true, "SEGUNDA_H");
        gravarDestino(cenario, "SO_DEPOSITO").andExpect(status().isOk());
        // O RustFS é partilhado pela suite e cada contexto recomeça a numeração: o prefixo pode ter objetos de outra classe.
        List<String> objetosAntes = objetosDoProcesso(cenario);

        carregar(cenario, cenario.publicacao())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("documentoConfigId"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este item não se aplica a um processo só para depósito."));

        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).isEmpty();
        assertThat(objetosDoProcesso(cenario)).containsExactlyInAnyOrderElementsOf(objetosAntes);

        carregar(cenario, cenario.deposito()).andExpect(status().isCreated());
    }

    @Test
    void soParaDepositoOsOutrosSoAceitamDeposito() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00120", true, "SEGUNDA_H");
        gravarDestino(cenario, "SO_DEPOSITO").andExpect(status().isOk());

        carregarOutros(cenario, "PUBLICACAO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("categoria"))
                .andExpect(jsonPath("$.campos[0].erro").value("Num processo só para depósito, os Outros só podem ser de depósito."));
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId())).isEmpty();

        carregarOutros(cenario, "DEPOSITO").andExpect(status().isCreated());
    }

    private ResultActions carregarOutros(Cenario cenario, String categoria) throws Exception {
        return mockMvc.perform(multipart("/api/v1/processos/" + cenario.processo().getId() + "/documentos")
                .file(new MockMultipartFile("ficheiro", "outro.pdf", "application/pdf", CONTEUDO))
                .param("categoria", categoria)
                .with(jwtDoAutor(cenario)));
    }

    @Test
    void comFicheirosMudarODestinoDa409EOMesmoDestinoPassa() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00107", true, "SEGUNDA_H");
        carregar(cenario, cenario.deposito()).andExpect(status().isCreated());

        detalhe(cenario, jwtDoAutor(cenario)).andExpect(jsonPath("$.destinoEditavel").value(false));
        gravarDestino(cenario, "SO_DEPOSITO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Remova os ficheiros carregados antes de mudar o destino."));
        assertThat(lido(cenario).getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
        assertThat(lido(cenario).getSumario()).isNull();

        gravarDestino(cenario, "PUBLICACAO_DEPOSITO").andExpect(status().isOk());
        assertThat(lido(cenario).getSumario()).isEqualTo("Resumo.");
    }

    @Test
    void soParaDepositoEmValidadoDgtRecusaEnviarIncmEPcmSemChamarAIncmESemAcoesDeEnvio() throws Exception {
        formularioDaRenAdmiteSoDeposito();
        Cenario cenario = criarCenario("00108", true, "PRIMEIRA");
        passarAValidadoDgt(cenario, DestinoProcesso.SO_DEPOSITO);
        long versao = lido(cenario).getVersao();

        detalhe(cenario, jwtMaster())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destino").value("SO_DEPOSITO"))
                .andExpect(jsonPath("$.acoesDisponiveis").value(not(hasItem("enviar-incm"))))
                .andExpect(jsonPath("$.acoesDisponiveis").value(not(hasItem("enviar-pcm"))));

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/enviar-incm").with(jwtMaster())
                        .contentType("application/json").content("{\"versao\":" + versao + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DESTINO_SO_DEPOSITO"))
                .andExpect(jsonPath("$.mensagem").value("Este processo é só para depósito e não é enviado à INCM."));
        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/enviar-pcm").with(jwtMaster())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnviarPcmRequest(versao, UUID.randomUUID()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DESTINO_SO_DEPOSITO"));

        verify(envioIncmService, never()).enviar(any(), any());
        Processo processo = lido(cenario);
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(processo.getPcmAtribuidoA()).isNull();
    }

    @Test
    void paraPublicacaoEDepositoOEnvioAIncmEOdeHoje() throws Exception {
        Cenario cenario = criarCenario("00109", false, "SEGUNDA_H");
        passarAValidadoDgt(cenario, DestinoProcesso.PUBLICACAO_DEPOSITO);
        UUID id = cenario.processo().getId();
        when(envioIncmService.enviar(eq(id), any())).thenAnswer(invocacao -> processoRepository.findById(id).orElseThrow());

        detalhe(cenario, jwtMaster()).andExpect(jsonPath("$.acoesDisponiveis").value(hasItem("enviar-incm")));
        mockMvc.perform(patch("/api/v1/processos/" + id + "/enviar-incm").with(jwtMaster())
                        .contentType("application/json").content("{\"versao\":" + lido(cenario).getVersao() + "}"))
                .andExpect(status().isOk());

        verify(envioIncmService).enviar(eq(id), any());
    }
}
