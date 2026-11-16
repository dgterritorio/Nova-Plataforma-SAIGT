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
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.CamposFormularioInicial;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Formulário da Ratificação com a configuração criada no teste, como o seed a faz: Procedimento `RATIFICACAO` de 1.ª
 * série num tipo de Instrumento de categoria PLANO, com permissão da Secretaria de Estado.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormularioRatificacaoIntegrationTest {

    private static final String RATIFICACAO = "RATIFICACAO";
    private static final String MSG_MODALIDADE = "Indique se a ratificação é uma Publicação ou uma Alteração.";

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
    private PermissaoAcaoRepository permissaoAcaoRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

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

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, DocumentoConfig textoAto) {
    }

    /** O `codigo` é único na BD partilhada pelos testes. */
    private TipoProcedimento ratificacao() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(RATIFICACAO))
                .findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(RATIFICACAO, "Ratificação")));
    }

    /**
     * Só o Texto do ato, obrigatório na publicação: as Peças gráficas opcionais do seed trariam a ficha MOTU (regra do
     * procedimento final com peça gráfica de publicação), que aqui não interessa; os itens do seed estão no teste do
     * Liquibase e no teste funcional.
     */
    private Cenario criarCenario(String sufixo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDMR-" + sufixo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("RAT-" + sufixo, "Ratificação"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, ratificacao(), 1, "PRIMEIRA"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.SEC_ESTADO, procedimento));
        DocumentoConfig textoAto = new DocumentoConfig(procedimento, "TEXTO_ATO_PUB",
                "Texto do ato a publicar no Diário da República", "Texto do Ato", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        textoAto.setObrigatorio(true);
        textoAto = documentoConfigRepository.saveAndFlush(textoAto);

        Entidade entidade = entidadeRepository.saveAndFlush(
                EntidadeTeste.nova("5097" + sufixo, "Secretaria de Estado " + sufixo, TipoEntidade.SEC_ESTADO));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@gov-teste.pt", "INCM"));
        Instrumento pdm = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "PDM de Sintra " + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(pdm, dinamicaConfig, "Ratificação", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        TipoAto resolucao = new TipoAto("RCM-" + sufixo, "Resolução do Conselho de Ministros", 1);
        resolucao.setIncmActTypeIdSerie1(Long.parseLong("97" + sufixo));
        processo.setTipoAto(tipoAtoRepository.saveAndFlush(resolucao));
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade, textoAto);
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "SEC_ESTADO", "id", cenario.entidade().getId(),
                        "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(), "pcm", false,
                        "incm_enrollment_id", "2015482")));
    }

    private static RequestPostProcessor jwtMaster() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "MASTER"));
    }

    private Processo lido(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    /** Todos os campos do formulário da Ratificação; `modalidade` nula fica fora do corpo. */
    private String corpo(Cenario cenario, String modalidade, String extra) {
        String campoModalidade = modalidade == null ? "" : ",\"modalidadeRatificacao\":\"" + modalidade + "\"";
        return "{\"versao\":" + lido(cenario).getVersao() + ",\"sumario\":\"Ratificação do PDM.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"publicarSuplemento\":false,\"entidadeEmitente\":\"Secretaria de Estado de Teste\","
                + "\"contactoTramitacao\":\"tramitacao@gov-teste.pt\",\"contactoPublicacao\":\"Gabinete, 210 000 000\","
                + "\"elementosAfetados\":[]" + campoModalidade + extra + "}";
    }

    private ResultActions gravar(Cenario cenario, String modalidade) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDoAutor(cenario)).contentType("application/json").content(corpo(cenario, modalidade, "")));
    }

    private ResultActions submeter(Cenario cenario, String modalidade) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario)).contentType("application/json")
                .content(corpo(cenario, modalidade, "," + PedidosSubmissao.JSON_CONFIRMACOES)));
    }

    private ResultActions detalhe(Cenario cenario, RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwt));
    }

    private void carregarTextoDoAto(Cenario cenario) {
        processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(lido(cenario), cenario.textoAto(),
                CategoriaDocumento.PUBLICACAO, "pdf", "texto-ato.pdf", "processos/teste/texto-ato.pdf",
                "a".repeat(64), "1", 10L, cenario.autor()));
    }

    @Test
    void deveDevolverOFormularioDaRatificacaoNaPrimeiraSerieComOPdmAbrangidoESemPedirElementosAfetados() throws Exception {
        Cenario cenario = criarCenario("00001");

        detalhe(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value(RATIFICACAO))
                .andExpect(jsonPath("$.serieDr").value("PRIMEIRA"))
                .andExpect(jsonPath("$.instrumentosAbrangidos[0].designacao").value("PDM de Sintra 00001"))
                .andExpect(jsonPath("$.exigeElementosAfetados").value(false))
                .andExpect(jsonPath("$.destinoEditavel").value(false))
                .andExpect(jsonPath("$.modalidadeRatificacao").doesNotExist());
    }

    @Test
    void semModalidadeASubmissaoRecebe400NoCampoEComValorForaDaListaORascunhoRecebe400() throws Exception {
        Cenario cenario = criarCenario("00002");
        gravar(cenario, null).andExpect(status().isOk());
        carregarTextoDoAto(cenario);

        submeter(cenario, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value(MSG_MODALIDADE))
                .andExpect(jsonPath("$.campos[0].path").value(CamposFormularioInicial.MODALIDADE_RATIFICACAO))
                .andExpect(jsonPath("$.campos[0].erro").value(MSG_MODALIDADE))
                .andExpect(jsonPath("$.campos.length()").value(1));
        assertThat(lido(cenario).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        long versao = lido(cenario).getVersao();
        gravar(cenario, "REVOGACAO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value(CamposFormularioInicial.MODALIDADE_RATIFICACAO));
        assertThat(lido(cenario).getVersao()).isEqualTo(versao);
    }

    @Test
    void aModalidadeGravadaMantemSeAoReabrirESubmeteComOTextoDoAtoParaEnviadoDgt() throws Exception {
        Cenario cenario = criarCenario("00003");

        gravar(cenario, "ALTERACAO").andExpect(status().isOk());
        detalhe(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modalidadeRatificacao").value("ALTERACAO"))
                .andExpect(jsonPath("$.podeSubmeter").value(false));
        assertThat(CamposFormularioInicial.lerModalidadeRatificacao(lido(cenario).getDadosFormulario(), objectMapper))
                .isEqualTo("ALTERACAO");

        submeter(cenario, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_OBRIGATORIOS_EM_FALTA"));

        carregarTextoDoAto(cenario);
        submeter(cenario, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        Processo submetido = lido(cenario);
        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(CamposFormularioInicial.lerModalidadeRatificacao(submetido.getDadosFormulario(), objectMapper))
                .isEqualTo("ALTERACAO");
        assertThat(submetido.getElementosAfetados()).isNullOrEmpty();
    }

    @Test
    void aSubmissaoExigeOsCamposDoFormularioNumaSoRecusa() throws Exception {
        Cenario cenario = criarCenario("00004");
        mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                        .with(jwtDoAutor(cenario)).contentType("application/json")
                        .content("{\"versao\":" + lido(cenario).getVersao() + ",\"modalidadeRatificacao\":\"PUBLICACAO\"}"))
                .andExpect(status().isOk());
        carregarTextoDoAto(cenario);

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                        .with(jwtDoAutor(cenario)).contentType("application/json")
                        .content("{\"versao\":" + lido(cenario).getVersao() + "," + PedidosSubmissao.JSON_CONFIRMACOES + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(org.hamcrest.Matchers.containsInAnyOrder("entidadeEmitente",
                        "contactoTramitacao", "contactoPublicacao", "normaLegal", "publicarSuplemento", "tipoInformacao",
                        "sumario", "dataAssinatura")))
                .andExpect(jsonPath("$.campos[?(@.path == 'dataAssinatura')].erro").value(hasItem("Indique a data de assinatura do ato.")));
    }

    @Test
    void aModalidadeSegueOBloqueioDepoisDaSubmissaoEODesbloqueioDaDgt() throws Exception {
        Cenario cenario = criarCenario("00005");
        Processo processo = cenario.processo();
        processo.setDadosFormulario("{\"modalidadeRatificacao\":\"PUBLICACAO\"}");
        processo.setDataSubmissao(Instant.now());
        processo.setEstado(EstadoProcesso.EM_SUBMISSAO);
        processoRepository.saveAndFlush(processo);

        detalhe(cenario, jwtDoAutor(cenario))
                .andExpect(jsonPath("$.camposBloqueados").value(hasItem(CamposFormularioInicial.MODALIDADE_RATIFICACAO)));
        gravar(cenario, "ALTERACAO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos[*].path").value(hasItem(CamposFormularioInicial.MODALIDADE_RATIFICACAO)));
        assertThat(CamposFormularioInicial.lerModalidadeRatificacao(lido(cenario).getDadosFormulario(), objectMapper))
                .isEqualTo("PUBLICACAO");

        Processo desbloqueado = lido(cenario);
        desbloqueado.setCamposDesbloqueados(List.of(CamposFormularioInicial.MODALIDADE_RATIFICACAO, "sumario",
                "dataAssinatura", "normaLegal", "tipoInformacao", "publicarSuplemento", "contactoTramitacao",
                "entidadeEmitente", "contactoPublicacao", "elementosAfetados"));
        processoRepository.saveAndFlush(desbloqueado);
        gravar(cenario, "ALTERACAO").andExpect(status().isOk());
        assertThat(CamposFormularioInicial.lerModalidadeRatificacao(lido(cenario).getDadosFormulario(), objectMapper))
                .isEqualTo("ALTERACAO");
        // Gravado com outro valor, o campo desbloqueado volta a bloquear-se.
        detalhe(cenario, jwtDoAutor(cenario))
                .andExpect(jsonPath("$.camposBloqueados").value(hasItem(CamposFormularioInicial.MODALIDADE_RATIFICACAO)));
    }

    /** A Ratificação passa pela PCM antes da publicação (EF v1.5, «Procedimentos publicados na 1.ª série»). */
    @Test
    void validadaPelaDgtTemEnviarPcmENaoEnviarIncm() throws Exception {
        Cenario cenario = criarCenario("00006");
        Processo processo = cenario.processo();
        processo.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoRepository.saveAndFlush(processo);

        detalhe(cenario, jwtMaster())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(hasItem("enviar-pcm")))
                .andExpect(jsonPath("$.acoesDisponiveis").value(not(hasItem("enviar-incm"))));

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/enviar-incm").with(jwtMaster())
                        .contentType("application/json").content("{\"versao\":" + lido(cenario).getVersao() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Na Ratificação, envie o processo à PCM antes da publicação."));
        assertThat(lido(cenario).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }
}
