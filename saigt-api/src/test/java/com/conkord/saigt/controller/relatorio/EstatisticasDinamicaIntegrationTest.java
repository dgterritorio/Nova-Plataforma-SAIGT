package com.conkord.saigt.controller.relatorio;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
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
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstatisticasDinamicaIntegrationTest {

    private static final String CAMINHO = "/api/v1/dinamicas/{id}/estatisticas";
    private static final AtomicInteger CONTADOR = new AtomicInteger(500);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    private IdusProcessoRepository idusProcessoRepository;

    private record Cenario(Dinamica dinamica, InstrumentoProcedimentoConfig procedimento, Utilizador autor, Entidade entidade,
                           Utilizador tecnico) {
    }

    @Test
    void deveContarRejeicoesDgtEAnulacoesIncmPorProcessoENoTotal() throws Exception {
        Cenario cenario = criarCenario();
        Processo rejeitadoDuasVezes = criarProcesso(cenario, 1);
        Processo anuladoUmaVez = criarProcesso(cenario, 2);
        transicao(rejeitadoDuasVezes, "REJEITAR", "EM_VALIDACAO_DGT", "REJEITADO_DGT");
        transicao(rejeitadoDuasVezes, "SUBMETER", "EM_SUBMISSAO", "ENVIADO_DGT");
        transicao(rejeitadoDuasVezes, "REJEITAR", "EM_VALIDACAO_DGT", "REJEITADO_DGT");
        transicao(anuladoUmaVez, "SUBMISSAO_INCM_REJEITADA", "ENVIADO_INCM", "ANULADO_INCM");

        mockMvc.perform(get(CAMINHO, cenario.dinamica().getId()).with(jwtDgt(cenario.tecnico())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicaId").value(cenario.dinamica().getId().toString()))
                .andExpect(jsonPath("$.numeroRejeicoesDgt").value(2))
                .andExpect(jsonPath("$.numeroAnulacoesIncm").value(1))
                .andExpect(jsonPath("$", hasKey("numeroComunicacoes")))
                .andExpect(jsonPath("$.numeroComunicacoes").value(nullValue()))
                .andExpect(jsonPath("$.numeroCiclosResubmissao").value(nullValue()))
                .andExpect(jsonPath("$.processos", hasSize(2)))
                .andExpect(jsonPath("$.processos[?(@.processoId == '%s')].numeroRejeicoesDgt",
                        rejeitadoDuasVezes.getId()).value(2))
                .andExpect(jsonPath("$.processos[?(@.processoId == '%s')].numeroAnulacoesIncm",
                        rejeitadoDuasVezes.getId()).value(0))
                .andExpect(jsonPath("$.processos[?(@.processoId == '%s')].numeroRejeicoesDgt",
                        anuladoUmaVez.getId()).value(0))
                .andExpect(jsonPath("$.processos[?(@.processoId == '%s')].numeroAnulacoesIncm",
                        anuladoUmaVez.getId()).value(1))
                .andExpect(jsonPath("$.processos[0].tipoProcedimento").value(cenario.procedimento().getTipoProcedimento().getCodigo()))
                .andExpect(jsonPath("$.processos[0]", hasKey("numeroComunicacoes")));
    }

    @Test
    void anulacoesPeloSubmitNewActEPeloGetActDetailsContamAmbas() throws Exception {
        Cenario cenario = criarCenario();
        Processo peloSubmitNewAct = criarProcesso(cenario, 1);
        Processo peloGetActDetails = criarProcesso(cenario, 2);
        // As ações e os estados de origem são os que o envio e a consulta à INCM gravam.
        transicao(peloSubmitNewAct, "SUBMISSAO_INCM_REJEITADA", "ENVIADO_INCM", "ANULADO_INCM");
        transicao(peloGetActDetails, "REJEICAO_INCM", "EM_VALIDACAO_INCM", "ANULADO_INCM");

        mockMvc.perform(get(CAMINHO, cenario.dinamica().getId()).with(jwtDgt(cenario.tecnico())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroAnulacoesIncm").value(2))
                .andExpect(jsonPath("$.numeroRejeicoesDgt").value(0));
    }

    @Test
    void ciclosVemDoSubContadorSubmissaoEOAgregadoSomaSoOsNaoNulos() throws Exception {
        Cenario cenario = criarCenario();
        Processo tresSubmissoes = criarProcesso(cenario, 1);
        Processo semSubmissao = criarProcesso(cenario, 2);
        Processo semIdus = criarProcesso(cenario, 3);
        idus(tresSubmissoes, 3);
        idus(semSubmissao, 0);

        mockMvc.perform(get(CAMINHO, cenario.dinamica().getId()).with(jwtDgt(cenario.tecnico())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCiclosResubmissao").value(2))
                .andExpect(jsonPath("$.processos[?(@.processoId == '%s')].numeroCiclosResubmissao",
                        tresSubmissoes.getId()).value(2))
                .andExpect(jsonPath("$.processos[?(@.processoId == '%s')].numeroCiclosResubmissao",
                        semSubmissao.getId()).value(0))
                .andExpect(jsonPath("$.processos[2].processoId").value(semIdus.getId().toString()))
                .andExpect(jsonPath("$.processos[2].numeroCiclosResubmissao").value(nullValue()));
    }

    @Test
    void utilizadorDaEntidadeDoInstrumentoVeAsEstatisticas() throws Exception {
        Cenario cenario = criarCenario();
        Processo processo = criarProcesso(cenario, 1);
        transicao(processo, "REJEITAR", "EM_VALIDACAO_DGT", "REJEITADO_DGT");

        mockMvc.perform(get(CAMINHO, cenario.dinamica().getId())
                        .with(jwtDaEntidade(cenario.autor().getId(), cenario.entidade())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processos", hasSize(1)))
                .andExpect(jsonPath("$.numeroRejeicoesDgt").value(1));
    }

    @Test
    void outraEntidadeSemProcessosVisiveisNaDinamicaNaoVeNadaEDinamicaInexistenteDaO404() throws Exception {
        Cenario cenario = criarCenario();
        criarProcesso(cenario, 1);
        Cenario outraEntidade = criarCenario();

        // Os Instrumentos e as Dinâmicas são visíveis a todos (OI-496): a outra entidade só não vê os processos alheios.
        mockMvc.perform(get(CAMINHO, cenario.dinamica().getId())
                        .with(jwtDaEntidade(outraEntidade.autor().getId(), outraEntidade.entidade())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processos", hasSize(0)))
                .andExpect(jsonPath("$.numeroRejeicoesDgt").value(0));
        mockMvc.perform(get(CAMINHO, UUID.randomUUID()).with(jwtDgt(cenario.tecnico())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensagem").value("Dinâmica não encontrada."));
    }

    @Test
    void semSessaoDevolve401() throws Exception {
        mockMvc.perform(get(CAMINHO, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private Cenario criarCenario() {
        int n = CONTADOR.incrementAndGet();
        String nif = "508800" + n;
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("EST" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("EST-ELAB-" + n, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("EST-PROC-" + n, "Decisão de Início"));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + n, "autor" + n + "@cm-estatisticas.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/EST/" + n, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Utilizador tecnico = new Utilizador("Técnico " + n, "tecnico" + n + "@dgt.pt", "AD_DGT");
        tecnico.setPerfilInterno(PerfilInterno.TECNICO);
        return new Cenario(dinamica, procedimento, autor, entidade, utilizadorRepository.saveAndFlush(tecnico));
    }

    private Processo criarProcesso(Cenario cenario, int ocorrencia) {
        Processo processo = new Processo(cenario.dinamica(), cenario.procedimento(), cenario.autor(), cenario.entidade());
        processo.setOcorrencia(ocorrencia);
        return processoRepository.saveAndFlush(processo);
    }

    private void transicao(Processo processo, String acao, String estadoAntes, String estadoDepois) {
        auditoriaRepository.save(Auditoria.builder().tipoOcorrencia("PROCESSO")
                .idOcorrencia(processo.getId().toString()).acao(acao)
                .estadoAntes(estadoAntes).estadoDepois(estadoDepois).build());
    }

    private void idus(Processo processo, int submissao) {
        UUID processoId = processo.getId();
        transactionTemplate.executeWithoutResult(estado -> idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                .processo(processoRepository.getReferenceById(processoId))
                .idus("02.0307.01." + processoId.toString().substring(0, 8) + "." + submissao)
                .sistemaOrigem("SAIGT").idIgt("001234").idIgtDefinitivo(true).ordemDinamica("03")
                .ordemDefinitiva(true).tentativa((short) 1).procedimento("002").submissao((short) submissao)
                .atualizadoEm(Instant.now()).build()));
    }

    private static RequestPostProcessor jwtDgt(Utilizador utilizador) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "TECNICO"));
    }

    private static RequestPostProcessor jwtDaEntidade(UUID utilizadorId, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", "Entidade Teste", "pcm", false)));
    }
}
