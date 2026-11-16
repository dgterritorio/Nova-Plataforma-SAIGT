package com.conkord.saigt.controller.relatorio;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.relatorio.ProcessosKpiFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `GET /api/v1/dashboard/kpis` de ponta a ponta em PostgreSQL. Cada teste corre numa transação que esvazia os
 * processos e é revertida no fim.
 */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "logging.level.com.conkord.saigt.service.relatorio.DashboardKpiService=DEBUG"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class DashboardKpiControllerIntegrationTest extends PostgresTestcontainersBase {

    private static final String CAMINHO = "/api/v1/dashboard/kpis";
    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Object[] ESTADOS = Arrays.stream(EstadoProcesso.values()).map(Enum::name).toArray();
    private static final String[] COM_CALCULADOR = {"TOTAL_POR_TIPOLOGIA", "PROCESSOS_POR_ESTADO", "A_CADUCAR",
            "A_INATIVAR", "GRAFICO_ESTADO", "GRAFICO_MES", "TEMPO_MEDIO_INSTRUCAO", "PADROES_TAMANHO_FICHEIROS",
            "TAXA_REJEICAO"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private ApplicationContext contexto;

    private ProcessosKpiFixture fixture;

    @BeforeEach
    void esvaziar() {
        fixture = new ProcessosKpiFixture(contexto);
        fixture.esvaziar();
    }

    private Utilizador interno(PerfilInterno perfil) {
        int n = CONTADOR.incrementAndGet();
        Utilizador utilizador = new Utilizador("KPI " + perfil + " " + n, "kpi.dashboard." + n + "@dgt.pt", "AD_DGT");
        utilizador.setPerfilInterno(perfil);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    private static RequestPostProcessor jwtInterno(Utilizador utilizador) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", utilizador.getPerfilInterno().name()));
    }

    private static RequestPostProcessor jwtExterno() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", 1L, "nif", "500051062",
                        "nome", "Câmara Municipal de Teste", "pcm", false)));
    }

    private ResultActions kpis(Utilizador utilizador) throws Exception {
        return mockMvc.perform(get(CAMINHO).with(jwtInterno(utilizador)));
    }

    private void tresEmSubmissaoEDoisPublicados() {
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PU", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 12));
        fixture.criar("PU", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 4, 3));
    }

    @Test
    void masterSemPreferenciasRecebeOsKpisComCalculadorPelaOrdemDoCatalogoComOMesmoCalculadoEm() throws Exception {
        tresEmSubmissaoEDoisPublicados();
        Instant antes = Instant.now();

        String corpo = kpis(interno(PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains(COM_CALCULADOR)))
                .andExpect(jsonPath("$[1].valor").value(5))
                .andExpect(jsonPath("$[1].designacao").value("Processos nos principais estados, por IGT"))
                .andReturn().getResponse().getContentAsString();

        List<String> calculadoEm = JsonPath.read(corpo, "$[*].calculadoEm");
        assertThat(calculadoEm).hasSize(9).containsOnly(calculadoEm.get(0));
        assertThat(Instant.parse(calculadoEm.get(0))).isAfterOrEqualTo(antes.minusSeconds(1));
        assertThat(calculadoEm.get(0)).endsWith("Z");
    }

    @Test
    void processosPorEstadoTemUmPontoPorEstadoComTresEmSubmissaoEDoisPublicados() throws Exception {
        tresEmSubmissaoEDoisPublicados();

        kpis(interno(PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].codigo").value("PROCESSOS_POR_ESTADO"))
                .andExpect(jsonPath("$[1].serie[*].rotulo", contains(ESTADOS)))
                .andExpect(jsonPath("$[1].serie[*].valor", contains(3, 0, 0, 0, 0, 0, 0, 2, 0, 0)))
                .andExpect(jsonPath("$[4].codigo").value("GRAFICO_ESTADO"))
                .andExpect(jsonPath("$[4].valor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[4].serie[*].valor", contains(3, 0, 0, 0, 0, 0, 0, 2, 0, 0)));
    }

    @Test
    void graficoMesTemOsMesesDosPublicadosENaoContaOutrosEstados() throws Exception {
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 2));
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 30));
        fixture.criar("PU", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 4, 15));
        fixture.criar("PU", EstadoProcesso.EM_VALIDACAO_INCM, LocalDate.of(2026, 4, 16));
        fixture.criar("PU", EstadoProcesso.EM_SUBMISSAO);

        kpis(interno(PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[5].codigo").value("GRAFICO_MES"))
                .andExpect(jsonPath("$[5].valor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[5].serie[*].rotulo", contains("2026-03", "2026-04")))
                .andExpect(jsonPath("$[5].serie[*].valor", contains(2, 1)));
    }

    @Test
    void totalPorTipologiaTemPdmEPuEOValorEASoma() throws Exception {
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 2));
        fixture.criar("PU", EstadoProcesso.ENVIADO_DGT);
        fixture.criar("PU", EstadoProcesso.ANULADO);
        fixture.criarSemIgt("PU", EstadoProcesso.EM_SUBMISSAO);

        kpis(interno(PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("TOTAL_POR_TIPOLOGIA"))
                .andExpect(jsonPath("$[0].serie[*].rotulo", contains("PDM", "PU")))
                .andExpect(jsonPath("$[0].serie[*].valor", contains(2, 3)))
                .andExpect(jsonPath("$[0].valor").value(5));
    }

    @Test
    void masterComGraficoMesOcultoNaoORecebe() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);
        mockMvc.perform(put("/api/v1/dashboard/preferencias").with(jwtInterno(master))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"kpiCodigo":"GRAFICO_MES","visivel":false,"ordem":0}]"""))
                .andExpect(status().isOk());

        kpis(master)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("TOTAL_POR_TIPOLOGIA", "PROCESSOS_POR_ESTADO",
                        "A_CADUCAR", "A_INATIVAR", "GRAFICO_ESTADO", "TEMPO_MEDIO_INSTRUCAO",
                        "PADROES_TAMANHO_FICHEIROS", "TAXA_REJEICAO")))
                .andExpect(jsonPath("$[*].codigo", not(hasItem("GRAFICO_MES"))));
    }

    @Test
    void masterComAtividadeRecenteVisivelNaoARecebeESemErroEPelaOrdemDasPreferencias() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);
        mockMvc.perform(put("/api/v1/dashboard/preferencias").with(jwtInterno(master))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"kpiCodigo":"ATIVIDADE_RECENTE","visivel":true,"ordem":0},
                                 {"kpiCodigo":"GRAFICO_MES","visivel":true,"ordem":1},
                                 {"kpiCodigo":"PROCESSOS_POR_ESTADO","visivel":true,"ordem":2}]"""))
                .andExpect(status().isOk());

        kpis(master)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("GRAFICO_MES", "PROCESSOS_POR_ESTADO",
                        "TOTAL_POR_TIPOLOGIA", "A_CADUCAR", "A_INATIVAR", "GRAFICO_ESTADO", "TEMPO_MEDIO_INSTRUCAO",
                        "PADROES_TAMANHO_FICHEIROS", "TAXA_REJEICAO")))
                .andExpect(jsonPath("$[*].codigo", not(hasItem("ATIVIDADE_RECENTE"))));
    }

    @Test
    void baseVaziaDa200ComZerosESeriesVazias() throws Exception {
        kpis(interno(PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].valor").value(0))
                .andExpect(jsonPath("$[0].serie", empty()))
                .andExpect(jsonPath("$[1].valor").value(0))
                .andExpect(jsonPath("$[1].serie", hasSize(ESTADOS.length)))
                .andExpect(jsonPath("$[1].serie[*].valor", everyItem(is(0))))
                .andExpect(jsonPath("$[2].codigo").value("A_CADUCAR"))
                .andExpect(jsonPath("$[2].valor").value(0))
                .andExpect(jsonPath("$[2].serie").value(nullValue()))
                .andExpect(jsonPath("$[3].codigo").value("A_INATIVAR"))
                .andExpect(jsonPath("$[3].valor").value(0))
                .andExpect(jsonPath("$[3].serie").value(nullValue()))
                .andExpect(jsonPath("$[4].serie[*].valor", everyItem(is(0))))
                .andExpect(jsonPath("$[5].serie", empty()))
                .andExpect(jsonPath("$[6].codigo").value("TEMPO_MEDIO_INSTRUCAO"))
                .andExpect(jsonPath("$[6].valor").value(nullValue()))
                .andExpect(jsonPath("$[7].codigo").value("PADROES_TAMANHO_FICHEIROS"))
                .andExpect(jsonPath("$[7].valor").value(0))
                .andExpect(jsonPath("$[7].serie", empty()))
                .andExpect(jsonPath("$[8].codigo").value("TAXA_REJEICAO"))
                .andExpect(jsonPath("$[8].valor").value(nullValue()));
    }

    @Test
    void aCaducarContaOsProcessosDaDinamicaEmCursoComAvisoENaoAsDinamicas() throws Exception {
        Processo comAviso = fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.alterarDinamica(comAviso, EstadoDinamica.EM_CURSO, "[\"UM_MES\"]");
        fixture.alterarDinamica(fixture.criar("PU", EstadoProcesso.EM_SUBMISSAO), EstadoDinamica.EM_CURSO, "[]");
        fixture.alterarDinamica(fixture.criar("PP", EstadoProcesso.EM_SUBMISSAO), EstadoDinamica.CADUCADA,
                "[\"UM_MES\"]");

        kpis(interno(PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].codigo").value("A_CADUCAR"))
                .andExpect(jsonPath("$[2].designacao").value("Processos a caducar"))
                .andExpect(jsonPath("$[2].valor").value(2));
    }

    @Test
    void osCincoKpisDePrazosTemposFicheirosERecusasVemComOsValoresDaBase() throws Exception {
        Instant submissao = Instant.parse("2026-03-01T10:00:00Z");
        Processo publicado = fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 11));
        fixture.registarSubmissao(publicado, submissao);
        Processo resubmetido = fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 21));
        fixture.registarSubmissao(resubmetido, submissao);
        fixture.registarSubmissao(resubmetido, Instant.parse("2026-03-20T10:00:00Z"));
        fixture.rejeitar(resubmetido, OrigemRejeicao.DGT);
        fixture.rejeitar(resubmetido, OrigemRejeicao.INCM);
        Processo emCurso = fixture.criar("PU", EstadoProcesso.ENVIADO_DGT);
        fixture.registarSubmissao(emCurso, submissao);
        fixture.alterarDinamica(emCurso, EstadoDinamica.EM_CURSO, "[\"UM_MES\"]");
        fixture.carregarDocumento(publicado, "Texto do ato", 100);
        fixture.carregarDocumento(resubmetido, "Texto do ato", 300);
        fixture.carregarDocumento(emCurso, "Relatório", 600);
        Processo parado = fixture.criar("PP", EstadoProcesso.EM_SUBMISSAO);
        fixture.alterarAtualizadoEm(parado, Instant.now().minus(150, ChronoUnit.DAYS));
        fixture.carregarDocumento(parado, "Relatório", 9000);

        kpis(interno(PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].valor").value(1))
                .andExpect(jsonPath("$[3].codigo").value("A_INATIVAR"))
                .andExpect(jsonPath("$[3].valor").value(1))
                .andExpect(jsonPath("$[6].codigo").value("TEMPO_MEDIO_INSTRUCAO"))
                .andExpect(jsonPath("$[6].valor").value(15))
                .andExpect(jsonPath("$[6].serie").value(nullValue()))
                .andExpect(jsonPath("$[7].codigo").value("PADROES_TAMANHO_FICHEIROS"))
                .andExpect(jsonPath("$[7].valor").value(1000))
                .andExpect(jsonPath("$[7].serie[*].rotulo", contains("Relatório", "Texto do ato")))
                .andExpect(jsonPath("$[7].serie[*].valor", contains(600, 200)))
                .andExpect(jsonPath("$[8].codigo").value("TAXA_REJEICAO"))
                .andExpect(jsonPath("$[8].valor").value(25));
    }

    @Test
    void osCincoKpisVisiveisVemPelaOrdemDasPreferenciasEUmOcultoNaoVem() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);
        mockMvc.perform(put("/api/v1/dashboard/preferencias").with(jwtInterno(master))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"kpiCodigo":"TAXA_REJEICAO","visivel":true,"ordem":0},
                                 {"kpiCodigo":"PADROES_TAMANHO_FICHEIROS","visivel":true,"ordem":1},
                                 {"kpiCodigo":"GRAFICO_MES","visivel":true,"ordem":2},
                                 {"kpiCodigo":"TEMPO_MEDIO_INSTRUCAO","visivel":true,"ordem":3},
                                 {"kpiCodigo":"A_INATIVAR","visivel":true,"ordem":4},
                                 {"kpiCodigo":"A_CADUCAR","visivel":true,"ordem":5},
                                 {"kpiCodigo":"TOTAL_POR_TIPOLOGIA","visivel":false,"ordem":6},
                                 {"kpiCodigo":"PROCESSOS_POR_ESTADO","visivel":false,"ordem":7},
                                 {"kpiCodigo":"GRAFICO_ESTADO","visivel":false,"ordem":8},
                                 {"kpiCodigo":"ATIVIDADE_RECENTE","visivel":false,"ordem":9}]"""))
                .andExpect(status().isOk());

        String corpo = kpis(master)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("TAXA_REJEICAO", "PADROES_TAMANHO_FICHEIROS",
                        "GRAFICO_MES", "TEMPO_MEDIO_INSTRUCAO", "A_INATIVAR", "A_CADUCAR")))
                .andReturn().getResponse().getContentAsString();
        List<String> calculadoEm = JsonPath.read(corpo, "$[*].calculadoEm");
        assertThat(calculadoEm).hasSize(6).containsOnly(calculadoEm.get(0));

        mockMvc.perform(put("/api/v1/dashboard/preferencias").with(jwtInterno(master))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"kpiCodigo":"TAXA_REJEICAO","visivel":false,"ordem":0}]"""))
                .andExpect(status().isOk());

        kpis(master)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", hasItem("A_CADUCAR")))
                .andExpect(jsonPath("$[*].codigo", not(hasItem("TAXA_REJEICAO"))));
    }

    @Test
    void tecnicoVisitanteEExternoRecebem403() throws Exception {
        for (RequestPostProcessor sessao : new RequestPostProcessor[] {jwtInterno(interno(PerfilInterno.TECNICO)),
                jwtInterno(interno(PerfilInterno.VISITANTE)), jwtExterno()}) {
            mockMvc.perform(get(CAMINHO).with(sessao))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }
    }

    @Test
    void semSessaoDa401() throws Exception {
        mockMvc.perform(get(CAMINHO)).andExpect(status().isUnauthorized());
    }

    @Test
    void oLogEmDebugSoTemCodigosDeKpiETempos(CapturedOutput saida) throws Exception {
        tresEmSubmissaoEDoisPublicados();

        kpis(interno(PerfilInterno.MASTER)).andExpect(status().isOk());

        // O log sai em JSON: a mensagem é o campo `message`; os restantes são do traçado (traceId, correlationId).
        ObjectMapper json = new ObjectMapper();
        List<String> mensagens = new ArrayList<>();
        for (String linha : saida.getOut().lines().filter(linha -> linha.contains("DashboardKpiService")).toList()) {
            mensagens.add(json.readTree(linha).get("message").asText());
        }
        assertThat(mensagens).hasSize(9)
                .allMatch(mensagem -> mensagem.matches("KPI calculado \\| codigo=[A-Z_]+ ms=\\d+"));
        assertThat(String.join("\n", mensagens)).doesNotContain("PDM", "PU", "Câmara", "@", "2026");
    }
}
