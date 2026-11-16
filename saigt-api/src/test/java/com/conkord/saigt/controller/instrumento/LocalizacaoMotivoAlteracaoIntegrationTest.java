package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PedeLocalizacao;
import com.conkord.saigt.persistence.configuracao.PedeMotivoAlteracao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoAtoTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
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
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
class LocalizacaoMotivoAlteracaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;
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
    private PermissaoAcaoRepository permissaoAcaoRepository;
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
    private ListaValoresRepository listaValoresRepository;
    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    private final List<ListaValores> itensDoTeste = new ArrayList<>();

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    @AfterEach
    void limparListas() {
        listaValoresRepository.deleteAll(itensDoTeste);
        itensDoTeste.clear();
    }

    private void valor(String tipo, String codigo, String descricao, int ordem, boolean ativo) {
        ListaValores item = new ListaValores(tipo, codigo, descricao, ordem);
        item.setAtivo(ativo);
        itensDoTeste.add(listaValoresRepository.saveAndFlush(item));
    }

    private void valor(String tipo, String codigo, String descricao) {
        valor(tipo, codigo, descricao, 1, true);
    }

    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    private Cenario criarCenario(String codigo, PedeLocalizacao pedeLocalizacao, PedeMotivoAlteracao pedeMotivo,
                                 String dadosFormulario, boolean submetido) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("LOC-" + codigo, "Instrumento " + codigo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-LOC-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimento("DECISAO_INICIO"), 1, "SEGUNDA_H");
        procedimento.setPedeLocalizacao(pedeLocalizacao);
        procedimento.setPedeMotivoAlteracao(pedeMotivo);
        procedimento = procedimentoConfigRepository.saveAndFlush(procedimento);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5097" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-loc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/LOC/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Camara Municipal de Teste");
        processo.setDadosFormulario(dadosFormulario);
        if (submetido) {
            processo.setDataSubmissao(Instant.now());
        }
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private Cenario cenarioDaAlteracaoPdm(String codigo) {
        return criarCenario(codigo, PedeLocalizacao.OBRIGATORIA, PedeMotivoAlteracao.COM_PERGUNTA, null, false);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private ResultActions gravar(Cenario cenario, String camposExtra) throws Exception {
        String corpo = "{\"versao\":" + gravado(cenario).getVersao() + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"entidadeEmitente\":\"Camara Municipal de Teste\",\"elementosAfetados\":[\"PECAS_ESCRITAS\"]"
                + (camposExtra.isEmpty() ? "" : "," + camposExtra) + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDe(cenario.autor(), cenario.entidade()))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        String corpo = "{\"versao\":" + gravado(cenario).getVersao() + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDe(cenario.autor(), cenario.entidade()))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId())
                .with(jwtDe(cenario.autor(), cenario.entidade())));
    }

    private JsonNode dadosFormulario(Cenario cenario) throws Exception {
        String dados = gravado(cenario).getDadosFormulario();
        return dados == null ? objectMapper.missingNode() : objectMapper.readTree(dados);
    }

    private void preverDeliberacao(Cenario cenario) {
        DocumentoConfig item = new DocumentoConfig(cenario.processo().getInstrumentoProcedimentoConfig(), "DELIB_LOC",
                "Deliberação", "Deliberação", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA,
                FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        item.setElementoInformacaoBase(ElementoInformacaoBase.DELIBERACAO);
        documentoConfigRepository.saveAndFlush(item);
    }

    // Opções para os formulários

    @Test
    void opcoesDeUmaListaVaziaSaoUmaListaVaziaParaUmUtilizadorExterno() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5001");

        mockMvc.perform(get("/api/v1/lista-valores/opcoes").param("tipo", "FREGUESIA")
                        .with(jwtDe(cenario.autor(), cenario.entidade())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void opcoesTrazemSoOsValoresAtivosPorOrdem() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5002");
        valor("FREGUESIA", "LOC-F2", "Segunda freguesia", 2, true);
        valor("FREGUESIA", "LOC-FX", "Freguesia extinta", 3, false);
        valor("FREGUESIA", "LOC-F1", "Primeira freguesia", 1, true);

        mockMvc.perform(get("/api/v1/lista-valores/opcoes").param("tipo", "FREGUESIA")
                        .with(jwtDe(cenario.autor(), cenario.entidade())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("LOC-F1"))
                .andExpect(jsonPath("$[0].descricao").value("Primeira freguesia"))
                .andExpect(jsonPath("$[1].codigo").value("LOC-F2"));
    }

    @Test
    void opcoesDeUmTipoQueNaoEDeFormularioSaoRecusadas() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5003");

        for (String tipo : new String[] {"MOTIVO_REJEICAO_DGT", "NAO_EXISTE"}) {
            mockMvc.perform(get("/api/v1/lista-valores/opcoes").param("tipo", tipo)
                            .with(jwtDe(cenario.autor(), cenario.entidade())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("tipo"))
                    .andExpect(jsonPath("$.campos[0].erro").value("Tipo de lista não disponível para formulários."));
        }
        mockMvc.perform(get("/api/v1/lista-valores/opcoes").with(jwtDe(cenario.autor(), cenario.entidade())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipo"));
    }

    @Test
    void opcoesSemAutenticacaoSaoRecusadas() throws Exception {
        mockMvc.perform(get("/api/v1/lista-valores/opcoes").param("tipo", "FREGUESIA"))
                .andExpect(status().isUnauthorized());
    }

    // Gravação

    @Test
    void deveGravarALocalizacaoSemPerderAInformacaoBaseNemAFundamentacao() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5004");
        cenario.processo().getInstrumentoProcedimentoConfig().setPedeFundamentacaoRjigt(true);
        procedimentoConfigRepository.saveAndFlush(cenario.processo().getInstrumentoProcedimentoConfig());
        preverDeliberacao(cenario);
        valor("FREGUESIA", "LOC-F01", "Freguesia de teste");
        valor("FUNDAMENTACAO_RJIGT", "LOC-RJ1", "Fundamentação de teste");

        gravar(cenario, "\"localizacao\":{\"freguesia\":\"LOC-F01\",\"toponimoTexto\":\"Bairro da Estação\"},"
                + "\"fundamentacaoRjigt\":[\"LOC-RJ1\"],\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2}}")
                .andExpect(status().isOk());

        JsonNode dados = dadosFormulario(cenario);
        assertThat(dados.path("localizacao").path("freguesia").asText()).isEqualTo("LOC-F01");
        assertThat(dados.path("localizacao").path("toponimoTexto").asText()).isEqualTo("Bairro da Estação");
        assertThat(dados.path("fundamentacaoRjigt").toString()).isEqualTo("[\"LOC-RJ1\"]");
        assertThat(dados.path("informacaoBase").path("deliberacao").path("prazoAnos").asInt()).isEqualTo(2);
    }

    @Test
    void deveRecusarAplicavelATodaAAreaComFreguesiaSemMudarNada() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5005");
        valor("FREGUESIA", "LOC-F01", "Freguesia de teste");

        gravar(cenario, "\"localizacao\":{\"aplicavelTodaArea\":true,\"freguesia\":\"LOC-F01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("localizacao.aplicavelTodaArea"));
        gravar(cenario, "\"localizacao\":{\"toponimoTexto\":\"Bairro\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("localizacao.toponimoCodigo"));

        assertThat(gravado(cenario).getDadosFormulario()).isNull();
    }

    @Test
    void deveRecusarAFreguesiaNumProcedimentoQueNaoPedeALocalizacao() throws Exception {
        Cenario cenario = criarCenario("5006", PedeLocalizacao.NAO, PedeMotivoAlteracao.NAO, null, false);
        valor("FREGUESIA", "LOC-F01", "Freguesia de teste");

        gravar(cenario, "\"localizacao\":{\"freguesia\":\"LOC-F01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("localizacao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Este procedimento não pede a localização."));
    }

    @Test
    void deveRecusarMotivosComRespostaNao() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5007");
        valor("MOTIVO_ALTERACAO_LEI", "LOC-L1", "Lei de teste");

        gravar(cenario, "\"conformaHierarquiaSuperior\":false,"
                + "\"motivosAlteracao\":[{\"tipo\":\"MOTIVO_ALTERACAO_LEI\",\"codigo\":\"LOC-L1\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("motivosAlteracao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Sem conformação, não há motivos a indicar."));
    }

    @Test
    void deveRecusarMaisDeCemMotivos() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5008");
        String motivo = "{\"tipo\":\"MOTIVO_ALTERACAO_LEI\",\"codigo\":\"LOC-L1\"}";

        gravar(cenario, "\"conformaHierarquiaSuperior\":true,\"motivosAlteracao\":["
                + String.join(",", java.util.Collections.nCopies(101, motivo)) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    // Submissão

    @Test
    void deveRecusarASubmissaoSemLocalizacaoQuandoHaFreguesiasAtivas() throws Exception {
        Cenario cenario = criarCenario("5009", PedeLocalizacao.OBRIGATORIA, PedeMotivoAlteracao.COM_PERGUNTA,
                "{\"conformaHierarquiaSuperior\":false}", false);
        valor("FREGUESIA", "LOC-F01", "Freguesia de teste");

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(false));
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("localizacao"));
        assertThat(gravado(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveSubmeterSemLocalizacaoComAListaFreguesiaVaziaERespostaNao() throws Exception {
        Cenario cenario = criarCenario("5010", PedeLocalizacao.OBRIGATORIA, PedeMotivoAlteracao.COM_PERGUNTA,
                "{\"conformaHierarquiaSuperior\":false}", false);

        detalhe(cenario).andExpect(jsonPath("$.podeSubmeter").value(true));
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarASubmissaoSemRespostaAPerguntaMesmoSemMotivosNasListas() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5011");

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("conformaHierarquiaSuperior"));
    }

    @Test
    void comSimEUmaLeiAtivaExigeMotivosEAceitaDuasLeisEUmPrograma() throws Exception {
        Cenario cenario = cenarioDaAlteracaoPdm("5012");
        valor("MOTIVO_ALTERACAO_LEI", "LOC-L1", "Primeira lei");
        valor("MOTIVO_ALTERACAO_LEI", "LOC-L2", "Segunda lei", 2, true);
        valor("MOTIVO_ALTERACAO_PROGRAMA", "LOC-G1", "Programa de teste");

        gravar(cenario, "\"conformaHierarquiaSuperior\":true").andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("motivosAlteracao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Escolha pelo menos um motivo da alteração."));

        gravar(cenario, "\"conformaHierarquiaSuperior\":true,\"motivosAlteracao\":["
                + "{\"tipo\":\"MOTIVO_ALTERACAO_LEI\",\"codigo\":\"LOC-L1\"},"
                + "{\"tipo\":\"MOTIVO_ALTERACAO_LEI\",\"codigo\":\"LOC-L2\"},"
                + "{\"tipo\":\"MOTIVO_ALTERACAO_PROGRAMA\",\"codigo\":\"LOC-G1\"}]")
                .andExpect(status().isOk());
        JsonNode motivos = dadosFormulario(cenario).path("motivosAlteracao");
        assertThat(motivos.size()).isEqualTo(3);
        assertThat(motivos.get(2).path("tipo").asText()).isEqualTo("MOTIVO_ALTERACAO_PROGRAMA");
        submeter(cenario).andExpect(status().isOk());
    }

    @Test
    void deveRecusarASubmissaoComUmMotivoQueOMasterDesativouDepois() throws Exception {
        Cenario cenario = criarCenario("5013", PedeLocalizacao.NAO, PedeMotivoAlteracao.COM_PERGUNTA,
                "{\"conformaHierarquiaSuperior\":true,\"motivosAlteracao\":[{\"tipo\":\"MOTIVO_ALTERACAO_LEI\",\"codigo\":\"LOC-L9\"}]}",
                false);
        valor("MOTIVO_ALTERACAO_LEI", "LOC-L9", "Lei revogada", 1, false);

        submeter(cenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("motivosAlteracao[0]"));
    }


    @Test
    void detalheTrazAConfiguracaoOsValoresEAsDescricoesMesmoDeCodigosDesativados() throws Exception {
        Cenario cenario = criarCenario("5014", PedeLocalizacao.OBRIGATORIA, PedeMotivoAlteracao.COM_PERGUNTA,
                "{\"localizacao\":{\"freguesia\":\"LOC-F01\",\"toponimoCodigo\":\"LOC-T01\"},\"conformaHierarquiaSuperior\":true,"
                        + "\"motivosAlteracao\":[{\"tipo\":\"MOTIVO_ALTERACAO_PLANO\",\"codigo\":\"LOC-P1\"}]}", false);
        valor("FREGUESIA", "LOC-F01", "Freguesia de teste");
        valor("TOPONIMO", "LOC-T01", "Topónimo de teste");
        valor("MOTIVO_ALTERACAO_PLANO", "LOC-P1", "Plano desativado", 1, false);

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedeLocalizacao").value("OBRIGATORIA"))
                .andExpect(jsonPath("$.pedeMotivoAlteracao").value("COM_PERGUNTA"))
                .andExpect(jsonPath("$.localizacao.freguesia.codigo").value("LOC-F01"))
                .andExpect(jsonPath("$.localizacao.freguesia.descricao").value("Freguesia de teste"))
                .andExpect(jsonPath("$.localizacao.toponimo.descricao").value("Topónimo de teste"))
                .andExpect(jsonPath("$.conformaHierarquiaSuperior").value(true))
                .andExpect(jsonPath("$.motivosAlteracao[0].tipo").value("MOTIVO_ALTERACAO_PLANO"))
                .andExpect(jsonPath("$.motivosAlteracao[0].descricao").value("Plano desativado"));
    }

    @Test
    void detalheSemValoresTrazLocalizacaoNulaEMotivosVazios() throws Exception {
        Cenario cenario = criarCenario("5015", PedeLocalizacao.NAO, PedeMotivoAlteracao.NAO, null, false);

        detalhe(cenario)
                .andExpect(jsonPath("$.pedeLocalizacao").value("NAO"))
                .andExpect(jsonPath("$.localizacao").doesNotExist())
                .andExpect(jsonPath("$.motivosAlteracao.length()").value(0));
    }

    @Test
    void depoisDaPrimeiraSubmissaoAsChavesNovasFicamBloqueadas() throws Exception {
        Cenario cenario = criarCenario("5016", PedeLocalizacao.OBRIGATORIA, PedeMotivoAlteracao.COM_PERGUNTA,
                "{\"localizacao\":{\"aplicavelTodaArea\":true},\"conformaHierarquiaSuperior\":false}", true);
        valor("FREGUESIA", "LOC-F01", "Freguesia de teste");

        gravar(cenario, "\"localizacao\":{\"freguesia\":\"LOC-F01\"},\"conformaHierarquiaSuperior\":true")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos.length()").value(2));
        gravar(cenario, "\"localizacao\":{\"aplicavelTodaArea\":true},\"conformaHierarquiaSuperior\":false")
                .andExpect(status().isOk());
        detalhe(cenario)
                .andExpect(jsonPath("$.camposBloqueados[?(@ == 'localizacao')]").exists())
                .andExpect(jsonPath("$.camposBloqueados[?(@ == 'motivosAlteracao')]").exists());
    }
}
