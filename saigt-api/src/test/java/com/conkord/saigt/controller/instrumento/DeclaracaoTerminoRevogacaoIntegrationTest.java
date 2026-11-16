package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
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
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.DinamicaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BL-PROC-040: a Revogação do IGT com outras Dinâmicas em curso exige a declaração de término na submissão. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeclaracaoTerminoRevogacaoIntegrationTest {

    private static final String MENSAGEM = "Declare que a revogação termina os procedimentos em curso.";
    private static final String COM_DECLARACAO = "[\"CONTEUDO_PROCESSO\",\"DECLARACAO_TERMINO\"]";
    private static final String SEM_DECLARACAO = "[\"CONTEUDO_PROCESSO\"]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

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
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private DinamicaService dinamicaService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private record Cenario(TipoInstrumento tipoInstrumento, Instrumento instrumento, Utilizador autor, Entidade entidade,
                           InstrumentoDinamicaConfig configRevogacao, InstrumentoDinamicaConfig configAlteracao,
                           InstrumentoProcedimentoConfig procedimentoRevogacao,
                           InstrumentoProcedimentoConfig procedimentoAlteracao) {
    }

    private TipoProcedimento decisaoInicio() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("DECISAO_INICIO")).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão de início")));
    }

    private TipoDinamica revogacao() {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals("REVOGACAO")).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVOGACAO", "Revogação")));
    }

    /** Um PDM em vigor, com as configurações da Revogação e de uma Alteração; ainda sem Dinâmicas. */
    private Cenario criarCenario() {
        // A BD H2 é partilhada entre classes de teste: códigos aleatórios evitam colisões entre execuções.
        String sufixo = String.format("%05d", ThreadLocalRandom.current().nextInt(100_000));
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("RVG-" + sufixo, "Plano " + sufixo, "PLANO"));
        TipoDinamica alteracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-RVG-" + sufixo, "Alteração"));
        InstrumentoDinamicaConfig configRevogacao = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, revogacao()));
        InstrumentoDinamicaConfig configAlteracao = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, alteracao));
        InstrumentoProcedimentoConfig procedimentoRevogacao = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(configRevogacao, decisaoInicio(), 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig procedimentoAlteracao = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(configAlteracao, decisaoInicio(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoRevogacao));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoAlteracao));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5779" + sufixo, "Câmara " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cm-revoga.pt", "INCM"));
        Instrumento instrumento = new Instrumento(tipoInstrumento, "1", "2026/RVG/" + sufixo, autor, autor);
        instrumento.setEmVigor(true);
        return new Cenario(tipoInstrumento, instrumentoRepository.saveAndFlush(instrumento), autor, entidade,
                configRevogacao, configAlteracao, procedimentoRevogacao, procedimentoAlteracao);
    }

    private Dinamica dinamica(Cenario cenario, InstrumentoDinamicaConfig config, String descritivo, EstadoDinamica estado) {
        Dinamica dinamica = new Dinamica(cenario.instrumento(), config, descritivo, cenario.autor());
        dinamica.setEstado(estado);
        return dinamicaRepository.saveAndFlush(dinamica);
    }

    /** Processo em `EM_SUBMISSAO` com os dados INCM completos e sem itens documentais. */
    private Processo processo(Cenario cenario, Dinamica dinamica, InstrumentoProcedimentoConfig procedimento) {
        Processo processo = new Processo(dinamica, procedimento, cenario.autor(), cenario.entidade());
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        return processoRepository.saveAndFlush(processo);
    }

    private Processo processoDaRevogacao(Cenario cenario) {
        return processo(cenario, dinamica(cenario, cenario.configRevogacao(), "Revogação", EstadoDinamica.EM_CURSO),
                cenario.procedimentoRevogacao());
    }

    private static RequestPostProcessor jwtDe(UUID utilizadorId, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(), "nome", entidade.getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor().getId(), cenario.entidade());
    }

    private Processo gravado(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow();
    }

    private ResultActions submeter(Processo processo, RequestPostProcessor sessao, String confirmacoes) throws Exception {
        String corpo = "{\"versao\":" + gravado(processo).getVersao() + ",\"sumario\":\"Resumo do ato.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\""
                + (confirmacoes == null ? "" : ",\"confirmacoes\":" + confirmacoes) + "}";
        return mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/submeter")
                .with(sessao).contentType("application/json").content(corpo));
    }

    private Auditoria auditoriaSubmeter(Processo processo) {
        return auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "PROCESSO", processo.getId().toString(), "SUBMETER").orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {SEM_DECLARACAO, "[\"CONTEUDO_PROCESSO\",\"OUTRA\"]"})
    void deveRecusarARevogacaoComUmaAlteracaoEmCursoSemADeclaracaoEManterOProcesso(String confirmacoes) throws Exception {
        Cenario cenario = criarCenario();
        dinamica(cenario, cenario.configAlteracao(), "Alteração", EstadoDinamica.EM_CURSO);
        Processo revogacao = processoDaRevogacao(cenario);
        long versaoAntes = gravado(revogacao).getVersao();

        submeter(revogacao, jwtDoAutor(cenario), confirmacoes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACAO_EM_FALTA"))
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("confirmacoes"))
                .andExpect(jsonPath("$.campos[0].erro").value(MENSAGEM));

        Processo depois = gravado(revogacao);
        assertThat(depois.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(depois.getVersao()).isEqualTo(versaoAntes);
        assertThat(depois.getDataSubmissao()).isNull();
    }

    @Test
    void deveSubmeterARevogacaoComADeclaracaoEGuardaLaNaAuditoria() throws Exception {
        Cenario cenario = criarCenario();
        Dinamica alteracao = dinamica(cenario, cenario.configAlteracao(), "Alteração", EstadoDinamica.EM_CURSO);
        Processo revogacao = processoDaRevogacao(cenario);

        submeter(revogacao, jwtDoAutor(cenario), COM_DECLARACAO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(gravado(revogacao).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(auditoriaSubmeter(revogacao).getDadosAlteracao())
                .contains("\"declaracaoTermino\":true")
                .contains("\"dinamicasATerminar\":[\"" + alteracao.getId() + "\"]");
        assertThat(dinamicaRepository.findById(alteracao.getId()).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
    }

    @Test
    void deveSubmeterSemADeclaracaoARevogacaoSemOutrasDinamicasEmCurso() throws Exception {
        Cenario cenario = criarCenario();
        dinamica(cenario, cenario.configAlteracao(), "Alteração terminada", EstadoDinamica.TERMINADA);
        dinamica(cenario, cenario.configAlteracao(), "Alteração caducada", EstadoDinamica.CADUCADA);
        Processo revogacao = processoDaRevogacao(cenario);

        submeter(revogacao, jwtDoAutor(cenario), SEM_DECLARACAO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        assertThat(auditoriaSubmeter(revogacao).getDadosAlteracao()).doesNotContain("declaracaoTermino");
    }

    @Test
    void naoDeveAplicarARegraAUmaAlteracaoComOutraDinamicaEmCurso() throws Exception {
        Cenario cenario = criarCenario();
        dinamica(cenario, cenario.configAlteracao(), "Outra alteração", EstadoDinamica.EM_CURSO);
        dinamica(cenario, cenario.configRevogacao(), "Revogação em curso", EstadoDinamica.EM_CURSO);
        Processo alteracao = processo(cenario, dinamica(cenario, cenario.configAlteracao(), "Alteração", EstadoDinamica.EM_CURSO),
                cenario.procedimentoAlteracao());

        submeter(alteracao, jwtDoAutor(cenario), SEM_DECLARACAO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarCom403QuemNaoEOAutorAntesDaRegra() throws Exception {
        Cenario cenario = criarCenario();
        dinamica(cenario, cenario.configAlteracao(), "Alteração", EstadoDinamica.EM_CURSO);
        Processo revogacao = processoDaRevogacao(cenario);
        Utilizador outro = utilizadorRepository.saveAndFlush(
                new Utilizador("Outro", "outro" + UUID.randomUUID().toString().substring(0, 8) + "@cm-revoga.pt", "INCM"));

        submeter(revogacao, jwtDe(outro.getId(), cenario.entidade()), SEM_DECLARACAO)
                .andExpect(status().isForbidden());

        assertThat(gravado(revogacao).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"CONTEUDO_PROCESSO\",\"declaracao_termino\"]", "[\"CONTEUDO_PROCESSO\",\"DECLARACAO-TERMINO\"]",
            "[\"CONTEUDO_PROCESSO\",\"DECLARACAO TERMINO\"]", "[\"CONTEUDO_PROCESSO\",\"DECLARAÇÃO_TERMINO\"]"})
    void deveRecusarComValidacaoUmaChaveForaDoPadrao(String confirmacoes) throws Exception {
        Cenario cenario = criarCenario();
        dinamica(cenario, cenario.configAlteracao(), "Alteração", EstadoDinamica.EM_CURSO);
        Processo revogacao = processoDaRevogacao(cenario);

        submeter(revogacao, jwtDoAutor(cenario), confirmacoes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));

        assertThat(gravado(revogacao).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveRecusarComValidacaoMaisDe20Chaves() throws Exception {
        Cenario cenario = criarCenario();
        Processo revogacao = processoDaRevogacao(cenario);
        String chaves = IntStream.range(0, 21).mapToObj(i -> "\"CHAVE_" + (char) ('A' + i) + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        submeter(revogacao, jwtDoAutor(cenario), chaves)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void deveDevolverNoDetalheDaRevogacaoAsDinamicasATerminarEVaziaNasOutras() throws Exception {
        Cenario cenario = criarCenario();
        Dinamica alteracao = dinamica(cenario, cenario.configAlteracao(), "Alteração ao regulamento", EstadoDinamica.EM_CURSO);
        Processo revogacao = processoDaRevogacao(cenario);
        Processo daAlteracao = processo(cenario, alteracao, cenario.procedimentoAlteracao());

        mockMvc.perform(get("/api/v1/processos/" + revogacao.getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasATerminar", hasSize(1)))
                .andExpect(jsonPath("$.dinamicasATerminar[0].dinamicaId").value(alteracao.getId().toString()))
                .andExpect(jsonPath("$.dinamicasATerminar[0].tipoDinamica").value("Alteração"))
                .andExpect(jsonPath("$.dinamicasATerminar[0].descritivo").value("Alteração ao regulamento"));
        mockMvc.perform(get("/api/v1/processos/" + daAlteracao.getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasATerminar", hasSize(0)));
    }

    @Test
    void deveListarSoAsEmCursoQueAbrangemOsInstrumentosSemAExcluidaNemRepetidas() {
        Cenario cenario = criarCenario();
        Instrumento outro = instrumentoRepository.saveAndFlush(
                new Instrumento(cenario.tipoInstrumento(), "2", "Outro " + UUID.randomUUID(), cenario.autor(), cenario.autor()));
        Dinamica propria = dinamica(cenario, cenario.configRevogacao(), "Revogação", EstadoDinamica.EM_CURSO);
        Dinamica emCurso = dinamica(cenario, cenario.configAlteracao(), "Em curso", EstadoDinamica.EM_CURSO);
        dinamica(cenario, cenario.configAlteracao(), "Caducada", EstadoDinamica.CADUCADA);
        dinamica(cenario, cenario.configAlteracao(), "Terminada", EstadoDinamica.TERMINADA);
        // Sem IGT de partida, abrange os dois Instrumentos só por `dinamica_instrumento`.
        Dinamica semIgt = new Dinamica(cenario.configAlteracao(), "Sem IGT", cenario.autor());
        semIgt.substituirInstrumentos(List.of(cenario.instrumento(), outro));
        semIgt = dinamicaRepository.saveAndFlush(semIgt);
        Instrumento alheio = instrumentoRepository.saveAndFlush(
                new Instrumento(cenario.tipoInstrumento(), "3", "Alheio " + UUID.randomUUID(), cenario.autor(), cenario.autor()));
        dinamicaRepository.saveAndFlush(new Dinamica(alheio, cenario.configAlteracao(), "De outro plano", cenario.autor()));

        List<Dinamica> encontradas = dinamicaRepository.listarEmCursoQueAbrangem(
                List.of(cenario.instrumento().getId(), outro.getId()), propria.getId());

        assertThat(encontradas).extracting(Dinamica::getId).containsExactlyInAnyOrder(emCurso.getId(), semIgt.getId());
    }

    @Test
    void devePublicadaARevogacaoTerminarEPorOPlanoForaDeVigorSemMexerNaAlteracao() throws Exception {
        Cenario cenario = criarCenario();
        Dinamica alteracao = dinamica(cenario, cenario.configAlteracao(), "Alteração", EstadoDinamica.EM_CURSO);
        Processo processoAlteracao = processo(cenario, alteracao, cenario.procedimentoAlteracao());
        Processo revogacao = processoDaRevogacao(cenario);
        submeter(revogacao, jwtDoAutor(cenario), COM_DECLARACAO).andExpect(status().isOk());

        transactionTemplate.executeWithoutResult(estado -> {
            Processo publicado = gravado(revogacao);
            publicado.setEstado(EstadoProcesso.PUBLICADO);
            processoRepository.saveAndFlush(publicado);
            dinamicaService.verificarConclusaoPeloSistema(publicado.getDinamica());
        });

        assertThat(dinamicaRepository.findById(gravado(revogacao).getDinamica().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(instrumentoRepository.findById(cenario.instrumento().getId()).orElseThrow().isEmVigor()).isFalse();
        assertThat(dinamicaRepository.findById(alteracao.getId()).orElseThrow().getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(gravado(processoAlteracao).getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }
}
