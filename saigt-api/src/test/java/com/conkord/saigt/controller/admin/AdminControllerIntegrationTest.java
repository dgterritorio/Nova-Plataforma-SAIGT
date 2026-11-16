package com.conkord.saigt.controller.admin;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Asserções por delta: `processo`/`auditoria` são partilhados pela suite, sem rollback. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    private static final String URL = "/api/v1/admin/estatisticas";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProcessoRepository processoRepository;
    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;
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

    private RequestPostProcessor comoAdmin() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private RequestPostProcessor comoTecnico() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "TECNICO"));
    }

    private RequestPostProcessor comoEntidade() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", java.util.Map.of("nif", "999888777", "nome", "Entidade Teste", "pcm", false)));
    }

    /** `estado` e `dataCaducidade` da Dinâmica fixados diretamente, sem passar pelas transições. */
    private Processo criarProcesso(String codigo, EstadoProcesso estado, LocalDate dataCaducidadeDinamica) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigo, "Plano " + codigo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PROC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("900000" + codigo.substring(3), "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor " + codigo, "autor" + codigo + "@empresa.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica novaDinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor);
        novaDinamica.setDataCaducidade(dataCaducidadeDinamica);
        Dinamica dinamica = dinamicaRepository.saveAndFlush(novaDinamica);
        Processo processo = new Processo(dinamica, procedimentoConfig, autor, entidade);
        processo.setEstado(estado);
        return processoRepository.saveAndFlush(processo);
    }

    @Test
    void deveCalcularMetricasReaisAPartirDeProcessosConhecidos() throws Exception {
        long totalAntes = processoRepository.count();
        long emAnaliseAntes = processoRepository.countByEstadoIn(java.util.Set.of(EstadoProcesso.EM_VALIDACAO_DGT));
        long sucessoAntes = processoRepository.countByEstadoIn(java.util.Set.of(EstadoProcesso.ENVIADO_INCM, EstadoProcesso.PUBLICADO));
        long prazoExpiradoAntes = dinamicaRepository.countByEstadoAndDataCaducidadeBefore(EstadoDinamica.EM_CURSO, LocalDate.now());

        criarProcesso("ADM801", EstadoProcesso.EM_VALIDACAO_DGT, null);
        criarProcesso("ADM802", EstadoProcesso.ENVIADO_INCM, null);
        criarProcesso("ADM803", EstadoProcesso.PUBLICADO, null);
        criarProcesso("ADM804", EstadoProcesso.ENVIADO_DGT, LocalDate.now().minusDays(5));
        criarProcesso("ADM805", EstadoProcesso.EM_SUBMISSAO, null);

        mockMvc.perform(get(URL).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessos").value((int) (totalAntes + 5)))
                .andExpect(jsonPath("$.emAnalise").value((int) (emAnaliseAntes + 1)))
                .andExpect(jsonPath("$.publicadosComSucesso").value((int) (sucessoAntes + 2)))
                .andExpect(jsonPath("$.comPrazoExpirado").value((int) (prazoExpiradoAntes + 1)));
    }

    @Test
    void deveDevolverAtividadeRecenteComNoMaximoDezLinhas() throws Exception {
        criarProcesso("ADM806", EstadoProcesso.EM_SUBMISSAO, null);

        mockMvc.perform(get(URL).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atividadeRecente", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.lessThanOrEqualTo(10))));
    }

    @Test
    void deveIncluirNaDistribuicaoPorEstadoOEstadoRecemCriado() throws Exception {
        criarProcesso("ADM807", EstadoProcesso.ANULADO_INCM, null);

        mockMvc.perform(get(URL).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribuicaoPorEstado[?(@.estado=='ANULADO_INCM')]").exists());
    }

    @Test
    void tecnicoNaoDeveConseguirConsultar() throws Exception {
        mockMvc.perform(get(URL).with(comoTecnico())).andExpect(status().isForbidden());
    }

    @Test
    void masterDeveConseguirConsultar() throws Exception {
        mockMvc.perform(get(URL).with(comoAdmin())).andExpect(status().isOk());
    }

    @Test
    void utilizadorExternoNaoDeveConseguirConsultar() throws Exception {
        mockMvc.perform(get(URL).with(comoEntidade())).andExpect(status().isForbidden());
    }

    @Test
    void deveDevolver401SemJwt() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
