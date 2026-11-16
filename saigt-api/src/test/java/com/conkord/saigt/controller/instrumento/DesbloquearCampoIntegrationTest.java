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
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Desbloqueio de um campo do formulário inicial na correção, a gravação seguinte e o regresso ao bloqueio. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesbloquearCampoIntegrationTest {

    private static final String TEXTO_PUBLICAR = "Texto a publicar.";
    private static final String SUMARIO = "Resumo do ato.";
    private static final String MOTIVO = "A DGT recusou o sumário.";

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
    private DocumentoConfigRepository documentoConfigRepository;

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
    private AuditoriaRepository auditoriaRepository;

    private record Cenario(Processo processo, Utilizador autor, Utilizador outro, Entidade entidade) {
    }

    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("CONSTITUICAO_CC")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")));
    }

    /** Um processo já submetido e de volta a `EM_SUBMISSAO`, como fica depois de `corrigir`. */
    private Cenario emCorrecao(String codigo) {
        return criarCenario(codigo, EstadoProcesso.EM_SUBMISSAO, true);
    }

    private Cenario criarCenario(String codigo, EstadoProcesso estado, boolean submetido) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DESB-" + codigo, "Instrumento " + codigo, "SRUP"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-DESB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoComFormulario(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));
        DocumentoConfig itemDeliberacao = new DocumentoConfig(procedimento, "DELIBERACAO_PUB", "Deliberação", "Deliberação",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        itemDeliberacao.setElementoInformacaoBase(ElementoInformacaoBase.DELIBERACAO);
        documentoConfigRepository.saveAndFlush(itemDeliberacao);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5327" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-desb.pt", "INCM"));
        Utilizador outro = utilizadorRepository.saveAndFlush(
                new Utilizador("Outro " + codigo, "outro" + codigo + "@cm-desb.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DESB/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario(SUMARIO);
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"" + TEXTO_PUBLICAR + "\"}}}");
        processo.setEstado(estado);
        if (submetido) {
            processo.setDataSubmissao(Instant.now());
        }
        return new Cenario(processoRepository.saveAndFlush(processo), autor, outro, entidade);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwtEntidade(utilizador.getId(), entidade);
    }

    private static RequestPostProcessor jwtEntidade(UUID utilizadorId, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor(), cenario.entidade());
    }

    private static RequestPostProcessor jwtInterno(String perfil) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", perfil));
    }

    private Processo gravado(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private long versao(Cenario cenario) {
        return gravado(cenario).getVersao();
    }

    private ResultActions desbloquear(Cenario cenario, RequestPostProcessor autenticacao, long versao, String campo,
                                      String motivo) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/desbloquear-campo")
                .with(autenticacao)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("versao", versao, "campo", campo, "motivo", motivo))));
    }

    private ResultActions desbloquear(Cenario cenario, String campo) throws Exception {
        return desbloquear(cenario, jwtDoAutor(cenario), versao(cenario), campo, MOTIVO);
    }

    private ResultActions gravar(Cenario cenario, String sumario, String normaLegal) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"" + sumario + "\",\"dataAssinatura\":\"" + LocalDate.now()
                + "\",\"normaLegal\":\"" + normaLegal + "\",\"tipoInformacao\":\"TEXTO_INTEGRAL\",\"elementosAfetados\":"
                + "[\"PECAS_ESCRITAS\"],\"informacaoBase\":{\"deliberacao\":{\"textoPublicar\":\"" + TEXTO_PUBLICAR + "\"}}}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private ResultActions submeter(Cenario cenario, String sumario) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"" + sumario + "\",\"dataAssinatura\":\"" + LocalDate.now()
                + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\"," + PedidosSubmissao.JSON_CONFIRMACOES + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content(corpo));
    }

    private List<Auditoria> auditoria(Cenario cenario, String acao) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "PROCESSO", cenario.processo().getId().toString(), acao);
    }

    private JsonNode dados(Auditoria registo) throws Exception {
        return objectMapper.readTree(registo.getDadosAlteracao());
    }

    /** Nada mudou: nem os desbloqueados, nem a `versao`, nem a auditoria do desbloqueio. */
    private void nadaMudou(Cenario cenario) {
        Processo depois = gravado(cenario);
        assertThat(depois.getCamposDesbloqueados()).isNullOrEmpty();
        assertThat(depois.getVersao()).isEqualTo(cenario.processo().getVersao());
        assertThat(auditoria(cenario, "DESBLOQUEAR_CAMPO")).isEmpty();
    }

    @Test
    void autorDesbloqueiaOSumarioSemMudarOEstadoEOsRestantesContinuamBloqueados() throws Exception {
        Cenario cenario = emCorrecao("0001");

        desbloquear(cenario, "sumario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"))
                .andExpect(jsonPath("$.versao").value(cenario.processo().getVersao() + 1))
                .andExpect(jsonPath("$.camposBloqueados.length()").value(30))
                .andExpect(jsonPath("$.camposBloqueados").value(not(hasItem("sumario"))))
                .andExpect(jsonPath("$.camposBloqueados").value(hasItems("normaLegal", "dataAssinatura")));

        Processo depois = gravado(cenario);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(depois.getCamposDesbloqueados()).containsExactly("sumario");
    }

    @Test
    void oDesbloqueioFicaNaAuditoriaComOUtilizadorOCampoOMotivoEADataEHora() throws Exception {
        Cenario cenario = emCorrecao("0002");
        Instant antes = Instant.now().minusSeconds(1);

        desbloquear(cenario, "sumario").andExpect(status().isOk());

        List<Auditoria> registos = auditoria(cenario, "DESBLOQUEAR_CAMPO");
        assertThat(registos).hasSize(1);
        Auditoria registo = registos.get(0);
        assertThat(registo.getUtilizadorId()).isEqualTo(cenario.autor().getId());
        assertThat(registo.getEntidadeId()).isEqualTo(cenario.entidade().getId());
        assertThat(registo.getCriadoEm()).isAfter(antes);
        assertThat(dados(registo).path("campo").asText()).isEqualTo("sumario");
        assertThat(dados(registo).path("motivo").asText()).isEqualTo(MOTIVO);
    }

    @Test
    void desbloquearDuasVezesNaoDuplicaOCampo() throws Exception {
        Cenario cenario = emCorrecao("0003");

        desbloquear(cenario, "sumario").andExpect(status().isOk());
        desbloquear(cenario, "sumario").andExpect(status().isOk());

        assertThat(gravado(cenario).getCamposDesbloqueados()).containsExactly("sumario");
    }

    @Test
    void campoBloqueadoRecusaAAlteracaoEDesbloqueadoAceitaOValorNovo() throws Exception {
        Cenario cenario = emCorrecao("0004");

        gravar(cenario, "Resumo corrigido.", "Norma Legal X")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[0].path").value("sumario"));

        desbloquear(cenario, "sumario").andExpect(status().isOk());
        gravar(cenario, "Resumo corrigido.", "Norma Legal X")
                .andExpect(status().isOk());
        assertThat(gravado(cenario).getSumario()).isEqualTo("Resumo corrigido.");
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(jsonPath("$.sumario").value("Resumo corrigido."));
    }

    @Test
    void gravarOCampoDesbloqueadoRegistaAntigoENovoEVoltaABloqueaLo() throws Exception {
        Cenario cenario = emCorrecao("0005");
        desbloquear(cenario, "sumario").andExpect(status().isOk());

        gravar(cenario, "Resumo corrigido.", "Norma Legal X")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados.length()").value(31))
                .andExpect(jsonPath("$.camposBloqueados").value(hasItem("sumario")));

        List<Auditoria> alteracoes = auditoria(cenario, "ALTERAR_CAMPO");
        assertThat(alteracoes).hasSize(1);
        assertThat(alteracoes.get(0).getUtilizadorId()).isEqualTo(cenario.autor().getId());
        assertThat(alteracoes.get(0).getCriadoEm()).isNotNull();
        JsonNode dados = dados(alteracoes.get(0));
        assertThat(dados.path("campo").asText()).isEqualTo("sumario");
        assertThat(dados.path("valorAntigo").asText()).isEqualTo(SUMARIO);
        assertThat(dados.path("valorNovo").asText()).isEqualTo("Resumo corrigido.");

        gravar(cenario, "Outra vez outro resumo.", "Norma Legal X")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMPO_BLOQUEADO"))
                .andExpect(jsonPath("$.campos[0].path").value("sumario"));
        assertThat(gravado(cenario).getSumario()).isEqualTo("Resumo corrigido.");
    }

    @Test
    void gravarSemMudarOCampoDesbloqueadoNaoRegistaAlteracaoEOMantemDesbloqueado() throws Exception {
        Cenario cenario = emCorrecao("0006");
        desbloquear(cenario, "sumario").andExpect(status().isOk());

        gravar(cenario, SUMARIO, "Norma Legal X")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados").value(not(hasItem("sumario"))));

        assertThat(auditoria(cenario, "ALTERAR_CAMPO")).isEmpty();
    }

    @Test
    void motivoEmBrancoDa400NoMotivoENadaMuda() throws Exception {
        Cenario cenario = emCorrecao("0007");

        desbloquear(cenario, jwtDoAutor(cenario), versao(cenario), "sumario", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("motivo"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique o motivo do desbloqueio."));

        nadaMudou(cenario);
    }

    @Test
    void campoForaDoFormularioInicialDa400NoCampoENadaMuda() throws Exception {
        Cenario cenario = emCorrecao("0008");

        for (String campo : List.of("entidade", "designacaoInstrumento", "numeroProcesso", "id", "destino")) {
            desbloquear(cenario, campo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("campo"));
        }

        nadaMudou(cenario);
    }

    @Test
    void processoNuncaSubmetidoOuForaDeEmSubmissaoDa409EstadoInvalido() throws Exception {
        Cenario nuncaSubmetido = criarCenario("0009", EstadoProcesso.EM_SUBMISSAO, false);
        Cenario enviado = criarCenario("0010", EstadoProcesso.ENVIADO_DGT, true);
        Cenario publicado = criarCenario("0011", EstadoProcesso.PUBLICADO, true);

        for (Cenario cenario : List.of(nuncaSubmetido, enviado, publicado)) {
            desbloquear(cenario, "sumario")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
            nadaMudou(cenario);
        }
    }

    @Test
    void quemNaoEAutorRecebe403OutraEntidade404ESemSessao401() throws Exception {
        Cenario cenario = emCorrecao("0012");
        Entidade outraEntidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("53279012", "Câmara de fora"));
        long versao = versao(cenario);

        desbloquear(cenario, jwtDe(cenario.outro(), cenario.entidade()), versao, "sumario", MOTIVO)
                .andExpect(status().isForbidden());
        for (String perfil : List.of("MASTER", "TECNICO", "VISITANTE")) {
            desbloquear(cenario, jwtInterno(perfil), versao, "sumario", MOTIVO).andExpect(status().isForbidden());
        }
        desbloquear(cenario, jwtEntidade(cenario.autor().getId(), outraEntidade), versao, "sumario", MOTIVO)
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/desbloquear-campo")
                        .contentType("application/json")
                        .content("{\"versao\":" + versao + ",\"campo\":\"sumario\",\"motivo\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        nadaMudou(cenario);
    }

    @Test
    void versaoAntigaDa409VersaoDesatualizada() throws Exception {
        Cenario cenario = emCorrecao("0013");

        desbloquear(cenario, jwtDoAutor(cenario), versao(cenario) - 1, "sumario", MOTIVO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        nadaMudou(cenario);
    }

    @Test
    void aDinamicaAssociadaEARespostaSimultaneoComPmotDesbloqueiamSeJuntas() throws Exception {
        Cenario cenario = emCorrecao("0014");

        desbloquear(cenario, "dadosFormulario.simultaneoComPmot")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camposBloqueados.length()").value(29))
                .andExpect(jsonPath("$.camposBloqueados").value(not(hasItem("dinamicaAssociadaId"))))
                .andExpect(jsonPath("$.camposBloqueados").value(not(hasItem("dadosFormulario.simultaneoComPmot"))));

        assertThat(gravado(cenario).getCamposDesbloqueados())
                .containsExactly("dinamicaAssociadaId", "dadosFormulario.simultaneoComPmot");
        assertThat(dados(auditoria(cenario, "DESBLOQUEAR_CAMPO").get(0)).path("campos").size()).isEqualTo(2);
    }

    @Test
    void submeterRegistaAAlteracaoEVoltaABloquearTodosOsDesbloqueados() throws Exception {
        Cenario cenario = emCorrecao("0015");
        desbloquear(cenario, "sumario").andExpect(status().isOk());
        desbloquear(cenario, "normaLegal").andExpect(status().isOk());

        submeter(cenario, "Resumo corrigido na submissão.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(gravado(cenario).getCamposDesbloqueados()).isNullOrEmpty();
        List<Auditoria> alteracoes = auditoria(cenario, "ALTERAR_CAMPO");
        assertThat(alteracoes).hasSize(1);
        assertThat(dados(alteracoes.get(0)).path("campo").asText()).isEqualTo("sumario");
        assertThat(dados(alteracoes.get(0)).path("valorNovo").asText()).isEqualTo("Resumo corrigido na submissão.");
    }

    @Test
    void acoesDisponiveisTrazDesbloquearCampoSoAoAutorNaCorrecao() throws Exception {
        Cenario cenario = emCorrecao("0016");
        Cenario nuncaSubmetido = criarCenario("0017", EstadoProcesso.EM_SUBMISSAO, false);

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(hasItem("desbloquear-campo")));
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId())
                        .with(jwtDe(cenario.outro(), cenario.entidade())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(not(hasItem("desbloquear-campo"))));
        mockMvc.perform(get("/api/v1/processos/" + nuncaSubmetido.processo().getId()).with(jwtDoAutor(nuncaSubmetido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(not(hasItem("desbloquear-campo"))));
    }
}
