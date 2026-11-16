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
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
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
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBaseRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.ProcessoValidacaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
@ExtendWith(OutputCaptureExtension.class)
class ProcessoValidacaoControllerIntegrationTest {

    private static final String DECISAO_INICIO = "DECISAO_INICIO";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProcessoValidacaoService processoValidacaoService;

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

    private TipoProcedimento procedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(p -> p.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    private Processo criarProcesso(String codigo, String nif, String codigoProcedimento, EstadoProcesso estado) {
        List<ElementoInformacaoBase> marcados = DECISAO_INICIO.equals(codigoProcedimento)
                ? List.of(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO)
                : List.of();
        return criarProcesso(codigo, nif, codigoProcedimento, estado, marcados);
    }

    private Processo criarProcesso(String codigo, String nif, String codigoProcedimento, EstadoProcesso estado,
                                   List<ElementoInformacaoBase> marcados) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, procedimento(codigoProcedimento), 1, "SEGUNDA_H"));
        int ordem = 1;
        for (ElementoInformacaoBase elemento : marcados) {
            criarItemMarcado(procedimentoConfig, elemento, ordem++, true);
        }
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo + "@cm-" + codigo + ".pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = new Processo(dinamica, procedimentoConfig, autor, entidade);
        processo.setEstado(estado);
        return processoRepository.saveAndFlush(processo);
    }

    private void criarItemMarcado(InstrumentoProcedimentoConfig procedimentoConfig, ElementoInformacaoBase elemento,
                                  int ordem, boolean ativo) {
        DocumentoConfig item = new DocumentoConfig(procedimentoConfig, elemento.name() + "_PUB", elemento.name(),
                elemento.name(), CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                "[\"pdf\"]", ordem);
        item.setElementoInformacaoBase(elemento);
        item.setAtivo(ativo);
        documentoConfigRepository.saveAndFlush(item);
    }

    private ProcessoDocumento criarDocumento(Processo processo, String nome) {
        return processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, null,
                CategoriaDocumento.DEPOSITO, "pdf", nome, "privado/" + processo.getId() + "/" + nome, "a".repeat(64), "v1", 10L,
                processo.getCriadoPor()));
    }

    private Cenario criarCenario(String codigo, String nif, EstadoProcesso estado) {
        Processo processo = criarProcesso(codigo, nif, DECISAO_INICIO, estado);
        return new Cenario(processo, processo.getCriadoPor(), nif, criarDocumento(processo, "texto.pdf"),
                criarDocumento(processo, "planta.pdf"),
                processoInformacaoBaseRepository.saveAndFlush(
                        new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO)),
                processoInformacaoBaseRepository.saveAndFlush(
                        new ProcessoInformacaoBase(processo, ElementoInformacaoBase.TEXTO_ATO)));
    }

    private static RequestPostProcessor jwtComPerfilInterno(UUID utilizadorId, String perfilInterno) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", perfilInterno));
    }

    private static RequestPostProcessor jwtDaEntidade(Entidade entidade, UUID utilizadorId) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", "Entidade Teste", "pcm", false)));
    }

    private long versao(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow().getVersao();
    }

    private static Map<String, Object> item(String tipo, UUID itemId, String decisao) {
        return Map.of("tipo", tipo, "itemId", itemId.toString(), "decisao", decisao);
    }

    @SafeVarargs
    private String corpo(long versao, Map<String, Object>... itens) throws Exception {
        return objectMapper.writeValueAsString(Map.of("itens", List.of(itens), "versao", versao));
    }

    private org.springframework.test.web.servlet.ResultActions gravar(Processo processo, RequestPostProcessor autenticacao,
                                                                     String corpo) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + processo.getId() + "/validacao/itens")
                .with(autenticacao).contentType("application/json").content(corpo));
    }

    private EstadoValidacao estadoDocumento(ProcessoDocumento documento) {
        return processoDocumentoRepository.findById(documento.getId()).orElseThrow().getEstadoValidacao();
    }

    private EstadoValidacao estadoElemento(ProcessoInformacaoBase elemento) {
        return processoInformacaoBaseRepository.findById(elemento.getId()).orElseThrow().getEstadoValidacao();
    }

    @Test
    void deveDevolver200EValidarSoOItemDoPedidoQuandoUmMasterValidaUmDocumento() throws Exception {
        Cenario cenario = criarCenario("VAL01", "501100001", EstadoProcesso.EM_VALIDACAO_DGT);

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                corpo(versao(cenario.processo()), item("DOCUMENTO", cenario.documento().getId(), "VALIDADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(4))
                .andExpect(jsonPath("$.todosRevistos").value(false));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.VALIDADO);
        assertThat(estadoDocumento(cenario.outroDocumento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
    }

    @Test
    void deveDevolverTodosRevistosQuandoAUltimaDecisaoFechaTodosOsItens() throws Exception {
        Cenario cenario = criarCenario("VAL02", "501100002", EstadoProcesso.EM_VALIDACAO_DGT);
        RequestPostProcessor tecnico = jwtComPerfilInterno(UUID.randomUUID(), "TECNICO");

        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()),
                item("DOCUMENTO", cenario.documento().getId(), "VALIDADO"),
                item("DOCUMENTO", cenario.outroDocumento().getId(), "VALIDADO"),
                item("INFORMACAO_BASE", cenario.deliberacao().getId(), "VALIDADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosRevistos").value(false));

        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()),
                item("INFORMACAO_BASE", cenario.textoAto().getId(), "VALIDADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosRevistos").value(true))
                .andExpect(jsonPath("$.itens[?(@.tipo=='INFORMACAO_BASE')].estadoValidacao").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("VALIDADO"))));
    }

    @Test
    void deveDevolver200AoGravarValidadoNumItemJaValidado() throws Exception {
        Cenario cenario = criarCenario("VAL03", "501100003", EstadoProcesso.EM_VALIDACAO_DGT);
        RequestPostProcessor master = jwtComPerfilInterno(UUID.randomUUID(), "MASTER");
        String corpo = corpo(versao(cenario.processo()), item("DOCUMENTO", cenario.documento().getId(), "VALIDADO"));

        gravar(cenario.processo(), master, corpo).andExpect(status().isOk());
        gravar(cenario.processo(), master, corpo).andExpect(status().isOk());

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.VALIDADO);
    }

    @Test
    void deveDevolver400ValidacaoENaoGravarNadaQuandoUmItemRejeitadoVemSemNotas() throws Exception {
        Cenario cenario = criarCenario("VAL04", "501100004", EstadoProcesso.EM_VALIDACAO_DGT);

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                corpo(versao(cenario.processo()),
                        item("DOCUMENTO", cenario.documento().getId(), "VALIDADO"),
                        item("DOCUMENTO", cenario.outroDocumento().getId(), "REJEITADO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[?(@.path=='itens[1].notas')]").exists());

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(estadoDocumento(cenario.outroDocumento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
    }

    @Test
    void deveDevolver400ValidacaoQuandoADecisaoNaoEConhecida() throws Exception {
        Cenario cenario = criarCenario("VAL05", "501100005", EstadoProcesso.EM_VALIDACAO_DGT);

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                "{\"versao\":" + versao(cenario.processo()) + ",\"itens\":[{\"tipo\":\"DOCUMENTO\",\"itemId\":\""
                        + cenario.documento().getId() + "\",\"decisao\":\"ACEITE\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver400ValidacaoQuandoAListaDeItensVemVazia() throws Exception {
        Cenario cenario = criarCenario("VAL06", "501100006", EstadoProcesso.EM_VALIDACAO_DGT);

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                "{\"versao\":" + versao(cenario.processo()) + ",\"itens\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("itens"));
    }

    @Test
    void deveDevolver400ENaoGravarNenhumItemQuandoUmItemIdPertenceAOutroProcesso() throws Exception {
        Cenario cenario = criarCenario("VAL07", "501100007", EstadoProcesso.EM_VALIDACAO_DGT);
        Cenario outro = criarCenario("VAL08", "501100008", EstadoProcesso.EM_VALIDACAO_DGT);

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                corpo(versao(cenario.processo()),
                        item("DOCUMENTO", cenario.documento().getId(), "VALIDADO"),
                        item("DOCUMENTO", outro.documento().getId(), "VALIDADO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("itens[1].itemId"));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(estadoDocumento(outro.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
    }

    @Test
    void deveDevolver400QuandoOItemIdDeUmDocumentoVemComoInformacaoBase() throws Exception {
        Cenario cenario = criarCenario("VAL09", "501100009", EstadoProcesso.EM_VALIDACAO_DGT);

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                corpo(versao(cenario.processo()), item("INFORMACAO_BASE", cenario.documento().getId(), "VALIDADO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolver409EstadoInvalidoQuandoOProcessoNaoEstaEmValidacaoDgt() throws Exception {
        Map<EstadoProcesso, String> nifPorEstado = Map.of(
                EstadoProcesso.ENVIADO_DGT, "501100101", EstadoProcesso.VALIDADO_DGT, "501100102");
        for (EstadoProcesso estado : nifPorEstado.keySet()) {
            Cenario cenario = criarCenario("VAL1" + estado.name(), nifPorEstado.get(estado), estado);

            gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                    corpo(versao(cenario.processo()), item("DOCUMENTO", cenario.documento().getId(), "VALIDADO")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

            assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
        }
    }

    @Test
    void deveDevolver409VersaoDesatualizadaAntesDoEstadoEMantidaAVersaoNoSucesso() throws Exception {
        Cenario cenario = criarCenario("VAL20", "501100020", EstadoProcesso.EM_VALIDACAO_DGT);
        RequestPostProcessor master = jwtComPerfilInterno(UUID.randomUUID(), "MASTER");
        long atual = versao(cenario.processo());

        gravar(cenario.processo(), master, corpo(atual + 1, item("DOCUMENTO", cenario.documento().getId(), "VALIDADO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
        assertThat(versao(cenario.processo())).isEqualTo(atual);
        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);

        gravar(cenario.processo(), master, corpo(atual, item("DOCUMENTO", cenario.documento().getId(), "VALIDADO")))
                .andExpect(status().isOk());
        assertThat(versao(cenario.processo())).isEqualTo(atual);

        Cenario fora = criarCenario("VAL21", "501100021", EstadoProcesso.VALIDADO_DGT);
        gravar(fora.processo(), master, corpo(versao(fora.processo()) + 1,
                item("DOCUMENTO", fora.documento().getId(), "VALIDADO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
    }

    @Test
    void deveDevolver403SemPermissaoAoVisitanteEAoAutorDaEntidade() throws Exception {
        Cenario cenario = criarCenario("VAL22", "501100022", EstadoProcesso.EM_VALIDACAO_DGT);
        String corpo = corpo(versao(cenario.processo()), item("DOCUMENTO", cenario.documento().getId(), "VALIDADO"));

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "VISITANTE"), corpo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        gravar(cenario.processo(), jwtDaEntidade(cenario.processo().getEntidade(), cenario.autor().getId()), corpo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
    }

    @Test
    void deveDevolver404QuandoOProcessoNaoExiste() throws Exception {
        Cenario cenario = criarCenario("VAL23", "501100023", EstadoProcesso.EM_VALIDACAO_DGT);
        Processo inexistente = new Processo();
        org.springframework.test.util.ReflectionTestUtils.setField(inexistente, "id", UUID.randomUUID());

        gravar(inexistente, jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                corpo(0, item("DOCUMENTO", cenario.documento().getId(), "VALIDADO")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void deveCriarOsDoisElementosAoCapturarUmProcessoDecisaoInicioENaoDuplicarNaSegundaChamada() throws Exception {
        Processo processo = criarProcesso("VAL30", "501100030", DECISAO_INICIO, EstadoProcesso.ENVIADO_DGT);

        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")).contentType("application/json")
                        .content("{\"versao\":" + versao(processo) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_VALIDACAO_DGT"));

        assertThat(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId()))
                .extracting(ProcessoInformacaoBase::getElemento, ProcessoInformacaoBase::getEstadoValidacao)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.POR_VALIDAR),
                        org.assertj.core.groups.Tuple.tuple(ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.POR_VALIDAR));

        transactionTemplate.executeWithoutResult(estado -> processoValidacaoService.criarItensInformacaoBase(
                processoRepository.findById(processo.getId()).orElseThrow()));

        assertThat(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())).hasSize(2);
    }

    @Test
    void deveManterIntactoOEstadoJaValidadoQuandoACriacaoCorreOutraVez() throws Exception {
        Cenario cenario = criarCenario("VAL31", "501100031", EstadoProcesso.EM_VALIDACAO_DGT);
        ProcessoInformacaoBase deliberacao = cenario.deliberacao();
        deliberacao.setEstadoValidacao(EstadoValidacao.VALIDADO);
        processoInformacaoBaseRepository.saveAndFlush(deliberacao);

        transactionTemplate.executeWithoutResult(estado -> processoValidacaoService.criarItensInformacaoBase(
                processoRepository.findById(cenario.processo().getId()).orElseThrow()));

        assertThat(estadoElemento(deliberacao)).isEqualTo(EstadoValidacao.VALIDADO);
        assertThat(processoInformacaoBaseRepository.findByProcesso_Id(cenario.processo().getId())).hasSize(2);
    }

    @Test
    void naoDeveCriarElementosAoCapturarUmProcessoDeOutroProcedimento() throws Exception {
        Processo processo = criarProcesso("VAL32", "501100032", "ALTERACAO_SIMPLES", EstadoProcesso.ENVIADO_DGT);

        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")).contentType("application/json")
                        .content("{\"versao\":" + versao(processo) + "}"))
                .andExpect(status().isOk());

        assertThat(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())).isEmpty();
    }

    @Test
    void naoDeveCriarElementosAoCapturarUmProcessoDecisaoInicioSemItensMarcados() throws Exception {
        Processo processo = criarProcesso("VAL33", "501100033", DECISAO_INICIO, EstadoProcesso.ENVIADO_DGT, List.of());

        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")).contentType("application/json")
                        .content("{\"versao\":" + versao(processo) + "}"))
                .andExpect(status().isOk());

        assertThat(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())).isEmpty();
    }

    @Test
    void deveCriarOElementoAoCapturarUmProcessoDeProcedimentoQueNaoEDecisaoInicioComItemMarcado() throws Exception {
        Processo processo = criarProcesso("VAL34", "501100034", "ALTERACAO_SIMPLES", EstadoProcesso.ENVIADO_DGT,
                List.of(ElementoInformacaoBase.TEXTO_ATO));

        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")).contentType("application/json")
                        .content("{\"versao\":" + versao(processo) + "}"))
                .andExpect(status().isOk());

        assertThat(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId()))
                .extracting(ProcessoInformacaoBase::getElemento, ProcessoInformacaoBase::getEstadoValidacao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.POR_VALIDAR));
    }

    @Test
    void deveCriarAsLinhasMesmoQuandoOsElementosAfetadosEscondemOsItensMarcados() throws Exception {
        Processo processo = criarProcesso("VAL35", "501100035", DECISAO_INICIO, EstadoProcesso.ENVIADO_DGT);
        processo.setElementosAfetados(List.of("PECAS_GRAFICAS"));
        processoRepository.saveAndFlush(processo);

        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/capturar")
                        .with(jwtComPerfilInterno(UUID.randomUUID(), "TECNICO")).contentType("application/json")
                        .content("{\"versao\":" + versao(processo) + "}"))
                .andExpect(status().isOk());

        assertThat(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId()))
                .extracting(ProcessoInformacaoBase::getElemento)
                .containsExactlyInAnyOrder(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
    }

    @Test
    void deveDevolverOEstadoDeValidacaoDeCadaDocumentoNaListagem() throws Exception {
        Cenario cenario = criarCenario("VAL40", "501100040", EstadoProcesso.EM_VALIDACAO_DGT);
        ProcessoDocumento validado = cenario.documento();
        validado.setEstadoValidacao(EstadoValidacao.VALIDADO);
        processoDocumentoRepository.saveAndFlush(validado);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/documentos")
                        .with(jwtDaEntidade(cenario.processo().getEntidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.nomeFicheiro=='texto.pdf')].estadoValidacao").value("VALIDADO"))
                .andExpect(jsonPath("$.content[?(@.nomeFicheiro=='planta.pdf')].estadoValidacao").value("POR_VALIDAR"));
    }

    private org.springframework.test.web.servlet.ResultActions validar(Processo processo, long versao, String perfilInterno)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/validar")
                .with(jwtComPerfilInterno(UUID.randomUUID(), perfilInterno)).contentType("application/json")
                .content("{\"versao\":" + versao + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions detalhe(Processo processo) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + processo.getId())
                .with(jwtComPerfilInterno(UUID.randomUUID(), "MASTER")));
    }

    private void validarTodosOsItens(Cenario cenario) {
        for (ProcessoDocumento documento : List.of(cenario.documento(), cenario.outroDocumento())) {
            documento.setEstadoValidacao(EstadoValidacao.VALIDADO);
            processoDocumentoRepository.saveAndFlush(documento);
        }
        for (ProcessoInformacaoBase elemento : List.of(cenario.deliberacao(), cenario.textoAto())) {
            elemento.setEstadoValidacao(EstadoValidacao.VALIDADO);
            processoInformacaoBaseRepository.saveAndFlush(elemento);
        }
    }

    private EstadoProcesso estado(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow().getEstado();
    }

    @Test
    void deveDevolver409ItensPorReverAoValidarComUmDocumentoPorValidar() throws Exception {
        Cenario cenario = criarCenario("VAL50", "501100050", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);
        ProcessoDocumento porValidar = processoDocumentoRepository.findById(cenario.outroDocumento().getId()).orElseThrow();
        porValidar.setEstadoValidacao(EstadoValidacao.POR_VALIDAR);
        processoDocumentoRepository.saveAndFlush(porValidar);

        validar(cenario.processo(), versao(cenario.processo()), "TECNICO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITENS_POR_REVER"))
                .andExpect(jsonPath("$.mensagem").value("Reveja todos os documentos antes de continuar."));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
    }

    @Test
    void deveDevolver409ItensPorReverAoValidarComUmElementoDaInformacaoBasePorValidar() throws Exception {
        Cenario cenario = criarCenario("VAL51", "501100051", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);
        ProcessoInformacaoBase porValidar = cenario.textoAto();
        porValidar.setEstadoValidacao(EstadoValidacao.POR_VALIDAR);
        processoInformacaoBaseRepository.saveAndFlush(porValidar);

        validar(cenario.processo(), versao(cenario.processo()), "MASTER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITENS_POR_REVER"));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
    }

    @Test
    void deveDevolver409ItensPorReverQuandoUmItemFoiRejeitado() throws Exception {
        Cenario cenario = criarCenario("VAL52", "501100052", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);
        ProcessoDocumento rejeitado = processoDocumentoRepository.findById(cenario.documento().getId()).orElseThrow();
        rejeitado.setEstadoValidacao(EstadoValidacao.REJEITADO);
        processoDocumentoRepository.saveAndFlush(rejeitado);

        validar(cenario.processo(), versao(cenario.processo()), "MASTER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITENS_POR_REVER"));
    }

    @Test
    void deveDevolver200EPassarAValidadoDgtQuandoTodosOsItensEstaoValidadosParaMasterETecnico() throws Exception {
        for (String perfil : List.of("MASTER", "TECNICO")) {
            Cenario cenario = criarCenario("VAL53" + perfil, perfil.equals("MASTER") ? "501100053" : "501100054",
                    EstadoProcesso.EM_VALIDACAO_DGT);
            validarTodosOsItens(cenario);

            validar(cenario.processo(), versao(cenario.processo()), perfil)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));

            assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        }
    }

    @Test
    void deveDevolver200AoValidarUmProcessoSemDocumentosENemElementosEsperados() throws Exception {
        Processo processo = criarProcesso("VAL55", "501100055", "ALTERACAO_SIMPLES", EstadoProcesso.EM_VALIDACAO_DGT);

        validar(processo, versao(processo), "TECNICO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));
    }

    @Test
    void deveDevolver409ItensPorReverQuandoUmElementoEsperadoNaoTemLinha() throws Exception {
        Processo semLinhas = criarProcesso("VAL56", "501100056", DECISAO_INICIO, EstadoProcesso.EM_VALIDACAO_DGT);
        Cenario umaLinhaEmFalta = criarCenario("VAL57", "501100057", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(umaLinhaEmFalta);
        processoInformacaoBaseRepository.delete(umaLinhaEmFalta.textoAto());
        processoInformacaoBaseRepository.flush();

        validar(semLinhas, versao(semLinhas), "TECNICO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITENS_POR_REVER"));
        validar(umaLinhaEmFalta.processo(), versao(umaLinhaEmFalta.processo()), "TECNICO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITENS_POR_REVER"));

        assertThat(estado(semLinhas)).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(estado(umaLinhaEmFalta.processo())).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
    }

    @Test
    void deveDevolverVersaoDesatualizadaENaoItensPorReverQuandoAVersaoDoPedidoEAntiga() throws Exception {
        Cenario cenario = criarCenario("VAL58", "501100058", EstadoProcesso.EM_VALIDACAO_DGT);

        validar(cenario.processo(), versao(cenario.processo()) + 1, "TECNICO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));
    }

    @Test
    void deveDevolverNoDetalheOEstadoDeCadaElementoEATodosItensRevistosSoQuandoTudoEstaValidado() throws Exception {
        Cenario cenario = criarCenario("VAL59", "501100059", EstadoProcesso.EM_VALIDACAO_DGT);
        ProcessoInformacaoBase deliberacao = cenario.deliberacao();
        deliberacao.setEstadoValidacao(EstadoValidacao.VALIDADO);
        processoInformacaoBaseRepository.saveAndFlush(deliberacao);

        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosItensRevistos").value(false))
                .andExpect(jsonPath("$.informacaoBase.length()").value(2))
                .andExpect(jsonPath("$.informacaoBase[0].id").value(cenario.deliberacao().getId().toString()))
                .andExpect(jsonPath("$.informacaoBase[0].elemento").value("DELIBERACAO"))
                .andExpect(jsonPath("$.informacaoBase[0].estadoValidacao").value("VALIDADO"))
                .andExpect(jsonPath("$.informacaoBase[1].elemento").value("TEXTO_ATO"))
                .andExpect(jsonPath("$.informacaoBase[1].estadoValidacao").value("POR_VALIDAR"));

        validarTodosOsItens(cenario);

        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosItensRevistos").value(true))
                .andExpect(jsonPath("$.informacaoBase[1].estadoValidacao").value("VALIDADO"));
    }

    @Test
    void deveDevolverInformacaoBaseVaziaNoDetalheQuandoOProcessoAindaNaoFoiCapturado() throws Exception {
        Processo processo = criarProcesso("VAL60", "501100060", DECISAO_INICIO, EstadoProcesso.ENVIADO_DGT);

        detalhe(processo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.informacaoBase.length()").value(0));
    }

    @Test
    void deveManterValidarNasAcoesDisponiveisMesmoComItensPorRever() throws Exception {
        Cenario cenario = criarCenario("VAL61", "501100061", EstadoProcesso.EM_VALIDACAO_DGT);

        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosItensRevistos").value(false))
                .andExpect(jsonPath("$.acoesDisponiveis").value(org.hamcrest.Matchers.hasItem("validar")));
    }

    private static final String CATEGORIA = "CATEGORIA_REJEICAO_DGT";
    private static final String MOTIVO = "MOTIVO_REJEICAO_DGT";

    private record Listas(ListaValores categoria, ListaValores motivo) {
    }

    /** As listas são globais e a BD é partilhada pelos testes: cada teste fixa o estado de que precisa. */
    private void semListasDeRecusa() {
        for (String tipo : List.of(CATEGORIA, MOTIVO)) {
            for (ListaValores valor : listaValoresRepository.findByTipoOrderByOrdemAsc(tipo)) {
                valor.setAtivo(false);
                listaValoresRepository.saveAndFlush(valor);
            }
        }
    }

    private Listas comListasDeRecusa(String codigo) {
        return new Listas(listaValoresRepository.saveAndFlush(new ListaValores(CATEGORIA, "C" + codigo, "Forma", 1)),
                listaValoresRepository.saveAndFlush(new ListaValores(MOTIVO, "M" + codigo, "Ilegível", 1)));
    }

    private Utilizador tecnicoDgt(String codigo) {
        Utilizador tecnico = new Utilizador("Técnico " + codigo, "tecnico" + codigo + "@dgt.pt", "AD_DGT");
        tecnico.setPerfilInterno(PerfilInterno.TECNICO);
        return utilizadorRepository.saveAndFlush(tecnico);
    }

    private static Map<String, Object> rejeicao(String tipo, UUID itemId, Long categoriaId, Long motivoId, String notas) {
        Map<String, Object> item = new HashMap<>();
        item.put("tipo", tipo);
        item.put("itemId", itemId.toString());
        item.put("decisao", "REJEITADO");
        item.put("categoriaId", categoriaId);
        item.put("motivoId", motivoId);
        item.put("notas", notas);
        return item;
    }

    private record LinhaRascunho(Long id, UUID documentoId, UUID informacaoBaseId, Long categoriaId, Long motivoId,
                                 String notas, UUID atualizadoPorId, boolean comData) {
    }

    private List<LinhaRascunho> rascunho(Processo processo) {
        return transactionTemplate.execute(estado -> processoValidacaoRejeicaoRepository
                .findByProcesso_Id(processo.getId()).stream()
                .map(linha -> new LinhaRascunho(linha.getId(),
                        linha.getProcessoDocumento() == null ? null : linha.getProcessoDocumento().getId(),
                        linha.getProcessoInformacaoBase() == null ? null : linha.getProcessoInformacaoBase().getId(),
                        linha.getCategoria() == null ? null : linha.getCategoria().getId(),
                        linha.getMotivo() == null ? null : linha.getMotivo().getId(),
                        linha.getNotas(), linha.getAtualizadoPor().getId(), linha.getAtualizadoEm() != null))
                .toList());
    }

    private static String doItem(UUID itemId, String campo) {
        return "$.itens[?(@.itemId=='" + itemId + "')]." + campo;
    }

    @Test
    void deveGravarARejeicaoDeUmDocumentoNoRascunhoComCategoriaMotivoNotasEUtilizador() throws Exception {
        Cenario cenario = criarCenario("REJ01", "501100701", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ01");
        Utilizador tecnico = tecnicoDgt("REJ01");
        UUID documentoId = cenario.documento().getId();

        gravar(cenario.processo(), jwtComPerfilInterno(tecnico.getId(), "TECNICO"), corpo(versao(cenario.processo()),
                rejeicao("DOCUMENTO", documentoId, listas.categoria().getId(), listas.motivo().getId(),
                        "  Página 3 ilegível.  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(doItem(documentoId, "estadoValidacao")).value("REJEITADO"))
                .andExpect(jsonPath(doItem(documentoId, "rejeicao.categoriaId"))
                        .value(listas.categoria().getId().intValue()))
                .andExpect(jsonPath(doItem(documentoId, "rejeicao.motivoId")).value(listas.motivo().getId().intValue()))
                .andExpect(jsonPath(doItem(documentoId, "rejeicao.notas")).value("Página 3 ilegível."))
                .andExpect(jsonPath(doItem(cenario.outroDocumento().getId(), "rejeicao"))
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.todosRevistos").value(false));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.REJEITADO);
        assertThat(rascunho(cenario.processo())).singleElement().satisfies(linha -> {
            assertThat(linha.documentoId()).isEqualTo(documentoId);
            assertThat(linha.informacaoBaseId()).isNull();
            assertThat(linha.categoriaId()).isEqualTo(listas.categoria().getId());
            assertThat(linha.motivoId()).isEqualTo(listas.motivo().getId());
            assertThat(linha.notas()).isEqualTo("Página 3 ilegível.");
            assertThat(linha.atualizadoPorId()).isEqualTo(tecnico.getId());
            assertThat(linha.comData()).isTrue();
        });
    }

    @Test
    void deveAtualizarAMesmaLinhaDoRascunhoAoRejeitarDeNovoComOutroMotivo() throws Exception {
        Cenario cenario = criarCenario("REJ02", "501100702", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ02");
        ListaValores outroMotivo = listaValoresRepository.saveAndFlush(new ListaValores(MOTIVO, "MREJ02B", "Incompleto", 2));
        RequestPostProcessor tecnico = jwtComPerfilInterno(tecnicoDgt("REJ02").getId(), "TECNICO");

        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()), rejeicao("DOCUMENTO",
                cenario.documento().getId(), listas.categoria().getId(), listas.motivo().getId(), "Primeira.")))
                .andExpect(status().isOk());
        Long linhaId = rascunho(cenario.processo()).get(0).id();
        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()), rejeicao("DOCUMENTO",
                cenario.documento().getId(), listas.categoria().getId(), outroMotivo.getId(), "Segunda.")))
                .andExpect(status().isOk());

        assertThat(rascunho(cenario.processo())).singleElement().satisfies(linha -> {
            assertThat(linha.id()).isEqualTo(linhaId);
            assertThat(linha.motivoId()).isEqualTo(outroMotivo.getId());
            assertThat(linha.notas()).isEqualTo("Segunda.");
        });
    }

    @Test
    void deveApagarALinhaDoRascunhoEIgnorarOsCamposDaRejeicaoAoValidarUmDocumentoRejeitado() throws Exception {
        Cenario cenario = criarCenario("REJ03", "501100703", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ03");
        RequestPostProcessor tecnico = jwtComPerfilInterno(tecnicoDgt("REJ03").getId(), "TECNICO");
        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()), rejeicao("DOCUMENTO",
                cenario.documento().getId(), listas.categoria().getId(), listas.motivo().getId(), "Ilegível.")))
                .andExpect(status().isOk());

        Map<String, Object> validado = new HashMap<>(item("DOCUMENTO", cenario.documento().getId(), "VALIDADO"));
        validado.put("notas", "Ignorada.");
        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()), validado))
                .andExpect(status().isOk())
                .andExpect(jsonPath(doItem(cenario.documento().getId(), "rejeicao"))
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.VALIDADO);
        assertThat(rascunho(cenario.processo())).isEmpty();
    }

    @Test
    void deveApontarARejeicaoDaInformacaoBaseParaOElemento() throws Exception {
        Cenario cenario = criarCenario("REJ04", "501100704", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ04");

        gravar(cenario.processo(), jwtComPerfilInterno(tecnicoDgt("REJ04").getId(), "MASTER"),
                corpo(versao(cenario.processo()), rejeicao("INFORMACAO_BASE", cenario.deliberacao().getId(),
                        listas.categoria().getId(), listas.motivo().getId(), "Deliberação sem data.")))
                .andExpect(status().isOk());

        assertThat(estadoElemento(cenario.deliberacao())).isEqualTo(EstadoValidacao.REJEITADO);
        assertThat(rascunho(cenario.processo())).singleElement().satisfies(linha -> {
            assertThat(linha.informacaoBaseId()).isEqualTo(cenario.deliberacao().getId());
            assertThat(linha.documentoId()).isNull();
        });
    }

    @Test
    void deveDevolver400NosCamposEmFaltaENaoGravarNadaQuandoFaltaOMotivoEAsNotasSaoEspacos() throws Exception {
        Cenario cenario = criarCenario("REJ05", "501100705", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ05");
        String mensagem = "Indique a categoria, o motivo e as notas da rejeição.";

        gravar(cenario.processo(), jwtComPerfilInterno(tecnicoDgt("REJ05").getId(), "TECNICO"),
                corpo(versao(cenario.processo()), rejeicao("DOCUMENTO", cenario.documento().getId(),
                        listas.categoria().getId(), null, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[0].path").value("itens[0].motivoId"))
                .andExpect(jsonPath("$.campos[0].erro").value(mensagem))
                .andExpect(jsonPath("$.campos[1].path").value("itens[0].notas"))
                .andExpect(jsonPath("$.campos[1].erro").value(mensagem));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(rascunho(cenario.processo())).isEmpty();
    }

    @Test
    void deveDevolver400NaCategoriaQuandoEstaInativaOuEUmValorDeMotivo() throws Exception {
        Cenario cenario = criarCenario("REJ06", "501100706", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ06");
        ListaValores inativa = new ListaValores(CATEGORIA, "CREJ06I", "Antiga", 2);
        inativa.setAtivo(false);
        inativa = listaValoresRepository.saveAndFlush(inativa);
        RequestPostProcessor tecnico = jwtComPerfilInterno(tecnicoDgt("REJ06").getId(), "TECNICO");

        for (Long categoriaId : List.of(inativa.getId(), listas.motivo().getId())) {
            gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()), rejeicao("DOCUMENTO",
                    cenario.documento().getId(), categoriaId, listas.motivo().getId(), "Notas.")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos.length()").value(1))
                    .andExpect(jsonPath("$.campos[0].path").value("itens[0].categoriaId"));
        }
        assertThat(rascunho(cenario.processo())).isEmpty();
    }

    @Test
    void deveDevolver400NaDecisaoQuandoEPorValidar() throws Exception {
        Cenario cenario = criarCenario("REJ07", "501100707", EstadoProcesso.EM_VALIDACAO_DGT);

        gravar(cenario.processo(), jwtComPerfilInterno(UUID.randomUUID(), "MASTER"),
                corpo(versao(cenario.processo()), item("DOCUMENTO", cenario.documento().getId(), "POR_VALIDAR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("itens[0].decisao"));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
    }

    @Test
    void naoDeveGravarNenhumItemQuandoUmaRejeicaoValidaVemComUmaInvalida() throws Exception {
        Cenario cenario = criarCenario("REJ08", "501100708", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ08");

        gravar(cenario.processo(), jwtComPerfilInterno(tecnicoDgt("REJ08").getId(), "MASTER"),
                corpo(versao(cenario.processo()),
                        rejeicao("DOCUMENTO", cenario.documento().getId(), listas.categoria().getId(),
                                listas.motivo().getId(), "Válida."),
                        rejeicao("DOCUMENTO", cenario.outroDocumento().getId(), listas.categoria().getId(),
                                listas.motivo().getId(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("itens[1].notas"));

        assertThat(estadoDocumento(cenario.documento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(estadoDocumento(cenario.outroDocumento())).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(rascunho(cenario.processo())).isEmpty();
    }

    @Test
    void deveRejeitarSoComNotasQuandoAsListasDeRecusaEstaoVazias() throws Exception {
        Cenario cenario = criarCenario("REJ09", "501100709", EstadoProcesso.EM_VALIDACAO_DGT);
        semListasDeRecusa();
        RequestPostProcessor tecnico = jwtComPerfilInterno(tecnicoDgt("REJ09").getId(), "TECNICO");

        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()),
                rejeicao("DOCUMENTO", cenario.documento().getId(), null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].path").value("itens[0].notas"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique as notas da rejeição."));

        gravar(cenario.processo(), tecnico, corpo(versao(cenario.processo()),
                rejeicao("DOCUMENTO", cenario.documento().getId(), null, null, "Ficheiro corrompido.")))
                .andExpect(status().isOk());

        assertThat(rascunho(cenario.processo())).singleElement().satisfies(linha -> {
            assertThat(linha.categoriaId()).isNull();
            assertThat(linha.motivoId()).isNull();
            assertThat(linha.notas()).isEqualTo("Ficheiro corrompido.");
        });
    }

    @Test
    void deveDevolverAsRejeicoesEmValidacaoSoAoAtorDgt() throws Exception {
        Cenario cenario = criarCenario("REJ10", "501100710", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ10");
        gravar(cenario.processo(), jwtComPerfilInterno(tecnicoDgt("REJ10").getId(), "TECNICO"),
                corpo(versao(cenario.processo()), rejeicao("DOCUMENTO", cenario.documento().getId(),
                        listas.categoria().getId(), listas.motivo().getId(), "Ilegível.")))
                .andExpect(status().isOk());

        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejeicoesEmValidacao.length()").value(1))
                .andExpect(jsonPath("$.rejeicoesEmValidacao[0].tipo").value("DOCUMENTO"))
                .andExpect(jsonPath("$.rejeicoesEmValidacao[0].itemId").value(cenario.documento().getId().toString()))
                .andExpect(jsonPath("$.rejeicoesEmValidacao[0].categoriaId").value(listas.categoria().getId().intValue()))
                .andExpect(jsonPath("$.rejeicoesEmValidacao[0].motivoId").value(listas.motivo().getId().intValue()))
                .andExpect(jsonPath("$.rejeicoesEmValidacao[0].notas").value("Ilegível."));

        Utilizador colega = utilizadorRepository.saveAndFlush(
                new Utilizador("Colega REJ10", "colegaREJ10@cm-REJ10.pt", "INCM"));
        for (RequestPostProcessor semRascunho : List.of(jwtDaEntidade(cenario.processo().getEntidade(), cenario.autor().getId()),
                jwtDaEntidade(cenario.processo().getEntidade(), colega.getId()), jwtComPerfilInterno(UUID.randomUUID(), "VISITANTE"))) {
            mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(semRascunho))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rejeicoesEmValidacao.length()").value(0));
        }
    }

    @Test
    void deveDevolver409ItensPorReverAoValidarComUmItemRejeitadoEOsRestantesValidados() throws Exception {
        Cenario cenario = criarCenario("REJ11", "501100711", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);
        Listas listas = comListasDeRecusa("REJ11");
        gravar(cenario.processo(), jwtComPerfilInterno(tecnicoDgt("REJ11").getId(), "TECNICO"),
                corpo(versao(cenario.processo()), rejeicao("DOCUMENTO", cenario.documento().getId(),
                        listas.categoria().getId(), listas.motivo().getId(), "Ilegível.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosRevistos").value(true));

        validar(cenario.processo(), versao(cenario.processo()), "TECNICO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITENS_POR_REVER"));
        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosItensRevistos").value(false));
    }

    @Test
    void naoDeveEscreverAsNotasDaRejeicaoNosLogs(CapturedOutput saida) throws Exception {
        Cenario cenario = criarCenario("REJ12", "501100712", EstadoProcesso.EM_VALIDACAO_DGT);
        Listas listas = comListasDeRecusa("REJ12");

        gravar(cenario.processo(), jwtComPerfilInterno(tecnicoDgt("REJ12").getId(), "TECNICO"),
                corpo(versao(cenario.processo()), rejeicao("DOCUMENTO", cenario.documento().getId(),
                        listas.categoria().getId(), listas.motivo().getId(), "Nota confidencial REJ12.")))
                .andExpect(status().isOk());

        assertThat(saida.getAll())
                .contains("processo=" + cenario.processo().getId())
                .contains("rejeitados=1")
                .doesNotContain("Nota confidencial REJ12.");
    }

    private static final String ALTERACAO_PEOT = "ALTERACAO_PEOT";

    /** A lista é global e a BD é partilhada pelos testes: cada teste deixa só os itens de que precisa. */
    private void semAlteracoesPeot() {
        for (ListaValores valor : listaValoresRepository.findByTipoOrderByOrdemAsc(ALTERACAO_PEOT)) {
            valor.setAtivo(false);
            listaValoresRepository.saveAndFlush(valor);
        }
    }

    private ListaValores alteracaoPeot(String codigo, String descricao, int ordem) {
        return listaValoresRepository.saveAndFlush(new ListaValores(ALTERACAO_PEOT, codigo, descricao, ordem));
    }

    private org.springframework.test.web.servlet.ResultActions validarComResumo(Processo processo, String perfilInterno,
                                                                               Map<String, Object> campos) throws Exception {
        Map<String, Object> corpo = new HashMap<>(campos);
        corpo.put("versao", versao(processo));
        return mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/validar")
                .with(jwtComPerfilInterno(UUID.randomUUID(), perfilInterno)).contentType("application/json")
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private record ResumoGravado(EstadoProcesso estado, String notasIncm, String notasDgt, Boolean regUsoSolo,
                                 Long alteracaoPeotId) {
    }

    private ResumoGravado lido(Processo processo) {
        return transactionTemplate.execute(estado -> {
            Processo atual = processoRepository.findById(processo.getId()).orElseThrow();
            return new ResumoGravado(atual.getEstado(), atual.getNotasIncm(), atual.getNotasDgt(), atual.getRegUsoSolo(),
                    atual.getAlteracaoPeot() == null ? null : atual.getAlteracaoPeot().getId());
        });
    }

    @Test
    void deveGravarOsQuatroCamposDoResumoNaPassagemAValidadoDgtEDevolvelosNoDetalhe() throws Exception {
        semAlteracoesPeot();
        ListaValores poap = alteracaoPeot("RV01A", "POAP - Arriba Fóssil da Costa da Caparica", 1);
        alteracaoPeot("RV01B", "POC - Ovar-Marinha Grande", 2);
        Cenario cenario = criarCenario("RV01", "501200001", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);

        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opcoesAlteracaoPeot.length()").value(2))
                .andExpect(jsonPath("$.opcoesAlteracaoPeot[0].id").value(poap.getId()))
                .andExpect(jsonPath("$.opcoesAlteracaoPeot[0].descricao").value("POAP - Arriba Fóssil da Costa da Caparica"))
                .andExpect(jsonPath("$.opcoesAlteracaoPeot[1].codigo").value("RV01B"))
                .andExpect(jsonPath("$.notasIncm").isEmpty())
                .andExpect(jsonPath("$.regUsoSolo").isEmpty());

        validarComResumo(cenario.processo(), "TECNICO", Map.of("notasIncm", "  Publicar com a planta em anexo.  ",
                "notasDgt", "Conferido pela DGT.", "regUsoSolo", true, "alteracaoPeotId", poap.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));

        assertThat(lido(cenario.processo())).isEqualTo(new ResumoGravado(EstadoProcesso.VALIDADO_DGT,
                "Publicar com a planta em anexo.", "Conferido pela DGT.", true, poap.getId()));
        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notasIncm").value("Publicar com a planta em anexo."))
                .andExpect(jsonPath("$.notasDgt").value("Conferido pela DGT."))
                .andExpect(jsonPath("$.regUsoSolo").value(true))
                .andExpect(jsonPath("$.alteracaoPeotId").value(poap.getId()))
                .andExpect(jsonPath("$.alteracaoPeotDescricao").value("POAP - Arriba Fóssil da Costa da Caparica"));
    }

    @Test
    void deveValidarComOsCamposVaziosQuandoOPedidoSoLevaAVersaoOuTextoEmBranco() throws Exception {
        semAlteracoesPeot();
        alteracaoPeot("RV02A", "POAP", 1);
        Cenario soVersao = criarCenario("RV02", "501200002", EstadoProcesso.EM_VALIDACAO_DGT);
        Cenario emBranco = criarCenario("RV03", "501200003", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(soVersao);
        validarTodosOsItens(emBranco);

        validar(soVersao.processo(), versao(soVersao.processo()), "MASTER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));
        validarComResumo(emBranco.processo(), "TECNICO", Map.of("notasIncm", "   ", "notasDgt", ""))
                .andExpect(status().isOk());

        for (Cenario cenario : List.of(soVersao, emBranco)) {
            assertThat(lido(cenario.processo()))
                    .isEqualTo(new ResumoGravado(EstadoProcesso.VALIDADO_DGT, null, null, null, null));
        }
    }

    @Test
    void deveDevolver400NaAlteracaoPeotENaoMudarOEstadoQuandoOItemNaoEUmAtivoDaLista() throws Exception {
        semAlteracoesPeot();
        ListaValores inativo = alteracaoPeot("RV04A", "POAP desativado", 1);
        inativo.setAtivo(false);
        listaValoresRepository.saveAndFlush(inativo);
        ListaValores deOutraLista = listaValoresRepository.saveAndFlush(new ListaValores(CATEGORIA, "RV04C", "Forma", 1));
        Cenario cenario = criarCenario("RV04", "501200004", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);

        for (Long id : List.of(inativo.getId(), deOutraLista.getId(), -1L)) {
            validarComResumo(cenario.processo(), "TECNICO", Map.of("notasDgt", "Não grava.", "alteracaoPeotId", id))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("alteracaoPeotId"));
        }

        assertThat(lido(cenario.processo()))
                .isEqualTo(new ResumoGravado(EstadoProcesso.EM_VALIDACAO_DGT, null, null, null, null));
    }

    @Test
    void deveDevolver400QuandoAsNotasPassamDoLimiteENaoMudarOEstado() throws Exception {
        Cenario cenario = criarCenario("RV05", "501200005", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);

        validarComResumo(cenario.processo(), "TECNICO", Map.of("notasIncm", "a".repeat(4001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("notasIncm"));
        validarComResumo(cenario.processo(), "TECNICO", Map.of("notasDgt", "b".repeat(4001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("notasDgt"));

        assertThat(estado(cenario.processo())).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        validarComResumo(cenario.processo(), "TECNICO", Map.of("notasIncm", "a".repeat(4000)))
                .andExpect(status().isOk());
        assertThat(lido(cenario.processo()).notasIncm()).hasSize(4000);
    }

    @Test
    void deveDevolver409ItensPorReverENaoGravarOsCamposDoResumoComUmItemPorValidarOuRejeitado() throws Exception {
        semAlteracoesPeot();
        ListaValores poap = alteracaoPeot("RV06A", "POAP", 1);
        Cenario porValidar = criarCenario("RV06", "501200006", EstadoProcesso.EM_VALIDACAO_DGT);
        Cenario rejeitado = criarCenario("RV07", "501200007", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(rejeitado);
        ProcessoDocumento documento = processoDocumentoRepository.findById(rejeitado.documento().getId()).orElseThrow();
        documento.setEstadoValidacao(EstadoValidacao.REJEITADO);
        processoDocumentoRepository.saveAndFlush(documento);
        Map<String, Object> campos = Map.of("notasIncm", "x", "notasDgt", "y", "regUsoSolo", false,
                "alteracaoPeotId", poap.getId());

        for (Cenario cenario : List.of(porValidar, rejeitado)) {
            validarComResumo(cenario.processo(), "TECNICO", campos)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("ITENS_POR_REVER"));
            assertThat(lido(cenario.processo()))
                    .isEqualTo(new ResumoGravado(EstadoProcesso.EM_VALIDACAO_DGT, null, null, null, null));
        }
    }

    @Test
    void deveDevolver403AoVisitanteEAoAutorQueEnviamOValidarComOsCamposDoResumo() throws Exception {
        Cenario cenario = criarCenario("RV08", "501200008", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);
        String corpo = objectMapper.writeValueAsString(Map.of("versao", versao(cenario.processo()), "notasDgt", "z"));

        validarComResumo(cenario.processo(), "VISITANTE", Map.of("notasDgt", "z"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/validar")
                        .with(jwtDaEntidade(cenario.processo().getEntidade(), cenario.autor().getId()))
                        .contentType("application/json").content(corpo))
                .andExpect(status().isForbidden());

        assertThat(lido(cenario.processo()))
                .isEqualTo(new ResumoGravado(EstadoProcesso.EM_VALIDACAO_DGT, null, null, null, null));
    }

    @Test
    void deveDevolverAsOpcoesVaziasEValidarSemAlteracaoPeotQuandoAListaNaoTemItensAtivos() throws Exception {
        semAlteracoesPeot();
        Cenario cenario = criarCenario("RV09", "501200009", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);

        detalhe(cenario.processo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opcoesAlteracaoPeot.length()").value(0));
        validarComResumo(cenario.processo(), "TECNICO", Map.of("notasDgt", "Sem PEOT.", "regUsoSolo", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO_DGT"));
        assertThat(lido(cenario.processo()))
                .isEqualTo(new ResumoGravado(EstadoProcesso.VALIDADO_DGT, null, "Sem PEOT.", false, null));
    }

    @Test
    void naoDeveDevolverOsCamposDoResumoNemAsOpcoesAoAutorDaEntidade() throws Exception {
        semAlteracoesPeot();
        ListaValores poap = alteracaoPeot("RV10A", "POAP", 1);
        Cenario cenario = criarCenario("RV10", "501200010", EstadoProcesso.EM_VALIDACAO_DGT);
        validarTodosOsItens(cenario);
        validarComResumo(cenario.processo(), "TECNICO", Map.of("notasDgt", "Nota interna.", "regUsoSolo", true,
                "alteracaoPeotId", poap.getId())).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId())
                        .with(jwtDaEntidade(cenario.processo().getEntidade(), cenario.autor().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notasDgt").isEmpty())
                .andExpect(jsonPath("$.regUsoSolo").isEmpty())
                .andExpect(jsonPath("$.alteracaoPeotId").isEmpty())
                .andExpect(jsonPath("$.alteracaoPeotDescricao").isEmpty())
                .andExpect(jsonPath("$.opcoesAlteracaoPeot.length()").value(0));
    }
}
