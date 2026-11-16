package com.conkord.saigt.controller.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
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
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** `PATCH .../documentos/{docId}/desbloquear` de ponta a ponta, com a releitura e a nova correção. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesbloquearDocumentoIntegrationTest {

    private static final String JUSTIFICACAO = "A planta de ordenamento tinha a legenda desatualizada.";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

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
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade, ProcessoDocumento validado) {
    }

    /** Processo em correção com um ficheiro validado e bloqueado pela correção. */
    private Cenario criarCenario(EstadoProcesso estado) {
        String codigo = String.format("%03d", SEQUENCIA.incrementAndGet());
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("DSBQ-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("DSBQ-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DSBQ-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("50931" + codigo, "Câmara DSBQ " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-dsbq.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/DSBQ/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setEstado(estado);
        processo = processoRepository.saveAndFlush(processo);

        ProcessoDocumento validado = new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "regulamento.pdf", "processos/dsbq/" + UUID.randomUUID() + "/regulamento.pdf", "a".repeat(64), "v1",
                10L, autor);
        validado.setEstadoValidacao(EstadoValidacao.VALIDADO);
        validado.setBloqueadoEdicao(true);
        return new Cenario(processo, autor, entidade, processoDocumentoRepository.saveAndFlush(validado));
    }

    private static RequestPostProcessor jwtDe(UUID utilizadorId, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor().getId(), cenario.entidade());
    }

    private static String url(Cenario cenario) {
        return "/api/v1/processos/" + cenario.processo().getId() + "/documentos/" + cenario.validado().getId()
                + "/desbloquear";
    }

    private static String corpo(String justificacao) {
        return "{\"justificacao\":\"" + justificacao + "\"}";
    }

    private ResultActions desbloquear(Cenario cenario, RequestPostProcessor quem, String corpo) throws Exception {
        var pedido = patch(url(cenario)).with(quem).contentType("application/json");
        return mockMvc.perform(corpo == null ? pedido : pedido.content(corpo));
    }

    private ProcessoDocumento relido(Cenario cenario) {
        return processoDocumentoRepository.findById(cenario.validado().getId()).orElseThrow();
    }

    private Processo processoRelido(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private List<Auditoria> auditorias(Cenario cenario, String acao) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "PROCESSO", cenario.processo().getId().toString(), acao);
    }

    private void naoMudouNada(Cenario cenario) {
        ProcessoDocumento documento = relido(cenario);
        assertThat(documento.isBloqueadoEdicao()).isTrue();
        assertThat(documento.getJustificacaoDesbloqueio()).isNull();
        assertThat(auditorias(cenario, "DESBLOQUEAR_DOCUMENTO")).isEmpty();
    }

    @Test
    void deveDesbloquearOValidadoGuardandoAJustificacaoEAuditarSemOTexto() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);
        long versaoProcesso = processoRelido(cenario).getVersao();

        desbloquear(cenario, jwtDoAutor(cenario), corpo("  " + JUSTIFICACAO + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cenario.validado().getId().toString()))
                .andExpect(jsonPath("$.bloqueadoEdicao").value(false))
                .andExpect(jsonPath("$.estadoValidacao").value("VALIDADO"))
                .andExpect(jsonPath("$.justificacaoDesbloqueio").value(JUSTIFICACAO));

        ProcessoDocumento documento = relido(cenario);
        assertThat(documento.isBloqueadoEdicao()).isFalse();
        assertThat(documento.getJustificacaoDesbloqueio()).isEqualTo(JUSTIFICACAO);
        assertThat(documento.getEstadoValidacao()).isEqualTo(EstadoValidacao.VALIDADO);
        Processo processo = processoRelido(cenario);
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getVersao()).isEqualTo(versaoProcesso);

        List<Auditoria> registos = auditorias(cenario, "DESBLOQUEAR_DOCUMENTO");
        assertThat(registos).singleElement().satisfies(registo -> {
            assertThat(registo.getDadosAlteracao()).contains(cenario.validado().getId().toString());
            assertThat(registo.getDadosAlteracao()).doesNotContain("legenda", "justificacao");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"justificacao\":\"\"}", "{\"justificacao\":\"   \"}", "{}", "{\"justificacao\":null}"})
    void deveRecusarAJustificacaoEmFaltaSemMudarNada(String corpo) throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);

        desbloquear(cenario, jwtDoAutor(cenario), corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_JUSTIFICACAO_EM_FALTA"))
                .andExpect(jsonPath("$.campos[0].path").value("justificacao"))
                .andExpect(jsonPath("$.campos[0].erro").value("Indique a justificação do desbloqueio."));

        naoMudouNada(cenario);
    }

    @Test
    void deveRecusarOPedidoSemCorpoComoJustificacaoEmFalta() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);

        desbloquear(cenario, jwtDoAutor(cenario), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOC_JUSTIFICACAO_EM_FALTA"));

        naoMudouNada(cenario);
    }

    @Test
    void deveRecusarComValidacaoAJustificacaoComMaisDeDoisMilCaracteres() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);

        desbloquear(cenario, jwtDoAutor(cenario), corpo("x".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        naoMudouNada(cenario);

        desbloquear(cenario, jwtDoAutor(cenario), corpo("x".repeat(2000))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"})
    void deveRecusarComConflitoEmRejeitadoDgtEAnuladoIncm(EstadoProcesso estado) throws Exception {
        Cenario cenario = criarCenario(estado);

        desbloquear(cenario, jwtDoAutor(cenario), corpo(JUSTIFICACAO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Só é possível desbloquear um ficheiro depois de iniciar a correção."));

        naoMudouNada(cenario);
    }

    @Test
    void deveRecusarComConflitoUmFicheiroQueNaoEstaBloqueado() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);
        ProcessoDocumento documento = relido(cenario);
        documento.setBloqueadoEdicao(false);
        processoDocumentoRepository.saveAndFlush(documento);

        desbloquear(cenario, jwtDoAutor(cenario), corpo(JUSTIFICACAO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOC_ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("O ficheiro não está bloqueado."));

        assertThat(relido(cenario).getJustificacaoDesbloqueio()).isNull();
    }

    @Test
    void deveRecusarQuemNaoEOAutorQuemEDeOutraEntidadeEOPedidoSemToken() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);
        Utilizador colega = utilizadorRepository.saveAndFlush(
                new Utilizador("Colega DSBQ", "colega" + UUID.randomUUID().toString().substring(0, 8) + "@cm-dsbq.pt", "INCM"));
        Cenario outra = criarCenario(EstadoProcesso.EM_SUBMISSAO);

        desbloquear(cenario, jwtDe(colega.getId(), cenario.entidade()), corpo(JUSTIFICACAO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        desbloquear(cenario, jwtDoAutor(outra), corpo(JUSTIFICACAO)).andExpect(status().isNotFound());
        mockMvc.perform(patch(url(cenario)).contentType("application/json").content(corpo(JUSTIFICACAO)))
                .andExpect(status().isUnauthorized());

        naoMudouNada(cenario);
    }

    @Test
    void deveDarNaoEncontradoParaUmDocumentoDeOutroProcesso() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);
        Cenario outra = criarCenario(EstadoProcesso.EM_SUBMISSAO);

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/documentos/"
                        + outra.validado().getId() + "/desbloquear")
                        .with(jwtDoAutor(cenario)).contentType("application/json").content(corpo(JUSTIFICACAO)))
                .andExpect(status().isNotFound());

        assertThat(relido(outra).isBloqueadoEdicao()).isTrue();
    }

    @Test
    void deveDevolverAJustificacaoNaListagemENaEstruturaDocumental() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);
        desbloquear(cenario, jwtDoAutor(cenario), corpo(JUSTIFICACAO)).andExpect(status().isOk());
        String processo = "/api/v1/processos/" + cenario.processo().getId();

        mockMvc.perform(get(processo + "/documentos").with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].justificacaoDesbloqueio").value(JUSTIFICACAO));
        mockMvc.perform(get(processo + "/estrutura-documental").with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outros[0].justificacaoDesbloqueio").value(JUSTIFICACAO));
    }

    @Test
    void deveVoltarABloquearNaCorrecaoSeguinteMantendoAJustificacao() throws Exception {
        Cenario cenario = criarCenario(EstadoProcesso.EM_SUBMISSAO);
        desbloquear(cenario, jwtDoAutor(cenario), corpo(JUSTIFICACAO)).andExpect(status().isOk());
        Processo processo = processoRelido(cenario);
        processo.setEstado(EstadoProcesso.REJEITADO_DGT);
        processoRepository.saveAndFlush(processo);

        mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/corrigir")
                        .with(jwtDoAutor(cenario)).contentType("application/json")
                        .content("{\"versao\":" + processoRelido(cenario).getVersao() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"));

        ProcessoDocumento documento = relido(cenario);
        assertThat(documento.isBloqueadoEdicao()).isTrue();
        assertThat(documento.getJustificacaoDesbloqueio()).isEqualTo(JUSTIFICACAO);
    }
}
