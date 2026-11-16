package com.conkord.saigt.controller.relatorio;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class DashboardControllerIntegrationTest {

    private static final String CAMINHO = "/api/v1/dashboard/preferencias";
    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String[] CATALOGO = {"TOTAL_POR_TIPOLOGIA", "PROCESSOS_POR_ESTADO", "A_CADUCAR", "A_INATIVAR",
            "GRAFICO_ESTADO", "GRAFICO_MES", "TEMPO_MEDIO_INSTRUCAO", "PADROES_TAMANHO_FICHEIROS", "TAXA_REJEICAO",
            "ATIVIDADE_RECENTE"};
    private static final String TAXA_E_GRAFICO_MES = """
            [{"kpiCodigo":"TAXA_REJEICAO","visivel":true,"ordem":0},{"kpiCodigo":"GRAFICO_MES","visivel":false,"ordem":1}]""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Utilizador interno(PerfilInterno perfil) {
        int n = CONTADOR.incrementAndGet();
        Utilizador utilizador = new Utilizador("Dashboard " + perfil + " " + n, "dashboard." + n + "@dgt.pt", "AD_DGT");
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

    private ResultActions substituir(RequestPostProcessor sessao, String corpo) throws Exception {
        return mockMvc.perform(put(CAMINHO).with(sessao).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private int linhasDe(Utilizador utilizador) {
        Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM dashboard_preferencia WHERE utilizador_id = ?",
                Integer.class, utilizador.getId());
        return total == null ? 0 : total;
    }

    private static void esperarOmissao(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CATALOGO.length)))
                .andExpect(jsonPath("$[*].kpiCodigo", contains((Object[]) CATALOGO)))
                .andExpect(jsonPath("$[*].ordem", contains(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)))
                .andExpect(jsonPath("$[*].visivel", everyItem(org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$[0].designacao").value("Total de processos por tipologia, com os atos prévios e os publicados"))
                .andExpect(jsonPath("$[9].designacao").value("Atividade recente"));
    }

    private static void esperarTaxaEGraficoMesPrimeiro(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CATALOGO.length)))
                .andExpect(jsonPath("$[*].kpiCodigo", contains("TAXA_REJEICAO", "GRAFICO_MES", "TOTAL_POR_TIPOLOGIA",
                        "PROCESSOS_POR_ESTADO", "A_CADUCAR", "A_INATIVAR", "GRAFICO_ESTADO", "TEMPO_MEDIO_INSTRUCAO",
                        "PADROES_TAMANHO_FICHEIROS", "ATIVIDADE_RECENTE")))
                .andExpect(jsonPath("$[*].ordem", contains(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)))
                .andExpect(jsonPath("$[*].visivel", contains(true, false, true, true, true, true, true, true, true, true)))
                .andExpect(jsonPath("$[0].designacao").value("Taxa de rejeição por entidade, tipologia, dinâmica e motivo"));
    }

    @Test
    void masterSemPreferenciasRecebeAOmissao() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);

        esperarOmissao(mockMvc.perform(get(CAMINHO).with(jwtInterno(master))));
    }

    @Test
    void putGravaEDevolveOsDoPedidoPrimeiroEOGetSeguinteDevolveOMesmo(CapturedOutput saida) throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);

        esperarTaxaEGraficoMesPrimeiro(substituir(jwtInterno(master), TAXA_E_GRAFICO_MES));
        esperarTaxaEGraficoMesPrimeiro(mockMvc.perform(get(CAMINHO).with(jwtInterno(master))));

        assertThat(linhasDe(master)).isEqualTo(2);
        assertThat(saida.getOut()).contains("Preferências do dashboard gravadas | kpis=2");
        assertThat(saida.getOut().lines().filter(linha -> linha.contains("Preferências do dashboard gravadas")))
                .singleElement().satisfies(linha -> assertThat(linha).doesNotContain(master.getId().toString())
                        .doesNotContain(master.getEmail()).doesNotContain(master.getNome()));
    }

    @Test
    void putSegundoSubstituiPorCompletoOPrimeiro() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);
        substituir(jwtInterno(master), TAXA_E_GRAFICO_MES).andExpect(status().isOk());

        substituir(jwtInterno(master), """
                [{"kpiCodigo":"GRAFICO_MES","visivel":true,"ordem":0},{"kpiCodigo":"TAXA_REJEICAO","visivel":false,"ordem":1}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kpiCodigo").value("GRAFICO_MES"))
                .andExpect(jsonPath("$[1].kpiCodigo").value("TAXA_REJEICAO"))
                .andExpect(jsonPath("$[1].visivel").value(false));
        assertThat(linhasDe(master)).isEqualTo(2);
    }

    @Test
    void putComListaVaziaApagaAsLinhasEOGetDevolveAOmissao() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);
        substituir(jwtInterno(master), TAXA_E_GRAFICO_MES).andExpect(status().isOk());
        assertThat(linhasDe(master)).isEqualTo(2);

        esperarOmissao(substituir(jwtInterno(master), "[]"));

        assertThat(linhasDe(master)).isZero();
        esperarOmissao(mockMvc.perform(get(CAMINHO).with(jwtInterno(master))));
    }

    @Test
    void kpiDesconhecidoDa400ENadaEGravado() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);
        substituir(jwtInterno(master), TAXA_E_GRAFICO_MES).andExpect(status().isOk());

        substituir(jwtInterno(master), "[{\"kpiCodigo\":\"XPTO\",\"visivel\":true,\"ordem\":0}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("[0].kpiCodigo"))
                .andExpect(jsonPath("$.campos[0].erro").value("KPI desconhecido: XPTO."));

        assertThat(linhasDe(master)).isEqualTo(2);
        esperarTaxaEGraficoMesPrimeiro(mockMvc.perform(get(CAMINHO).with(jwtInterno(master))));
    }

    @Test
    void posicaoRepetidaDa400() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);

        substituir(jwtInterno(master), """
                [{"kpiCodigo":"GRAFICO_MES","visivel":true,"ordem":3},{"kpiCodigo":"TAXA_REJEICAO","visivel":true,"ordem":3}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("[1].ordem"))
                .andExpect(jsonPath("$.campos[0].erro").value("Posição repetida no dashboard."));
        assertThat(linhasDe(master)).isZero();
    }

    @Test
    void kpiRepetidoOrdemNegativaVisivelEmFaltaEItensAMaisDao400ComOCampo() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);

        substituir(jwtInterno(master), """
                [{"kpiCodigo":"GRAFICO_MES","visivel":true,"ordem":0},{"kpiCodigo":"GRAFICO_MES","visivel":true,"ordem":1}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("[1].kpiCodigo"))
                .andExpect(jsonPath("$.campos[0].erro").value("KPI repetido: GRAFICO_MES."));
        substituir(jwtInterno(master), "[{\"kpiCodigo\":\"GRAFICO_MES\",\"visivel\":true,\"ordem\":-1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("[0].ordem"));
        substituir(jwtInterno(master), "[{\"kpiCodigo\":\"GRAFICO_MES\",\"ordem\":0}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("[0].visivel"));

        StringBuilder doze = new StringBuilder("[");
        for (int i = 0; i < 12; i++) {
            doze.append(i == 0 ? "" : ",").append("{\"kpiCodigo\":\"").append(CATALOGO[i % CATALOGO.length])
                    .append("\",\"visivel\":true,\"ordem\":").append(i).append('}');
        }
        substituir(jwtInterno(master), doze.append(']').toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("corpo"))
                .andExpect(jsonPath("$.campos[0].erro").value("Há mais KPIs do que os do catálogo."));
        assertThat(linhasDe(master)).isZero();
    }

    @Test
    void tecnicoVisitanteEExternoRecebem403NoGetENoPut() throws Exception {
        for (RequestPostProcessor sessao : new RequestPostProcessor[] {jwtInterno(interno(PerfilInterno.TECNICO)),
                jwtInterno(interno(PerfilInterno.VISITANTE)), jwtExterno()}) {
            mockMvc.perform(get(CAMINHO).with(sessao))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
            substituir(sessao, TAXA_E_GRAFICO_MES)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }
        Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM dashboard_preferencia WHERE kpi_codigo = 'GRAFICO_MES' "
                + "AND visivel = false AND ordem = 1 AND utilizador_id NOT IN (SELECT id FROM utilizador WHERE perfil_interno = 'MASTER')",
                Integer.class);
        assertThat(total).isZero();
    }

    @Test
    void semSessaoDa401NoGetENoPut() throws Exception {
        mockMvc.perform(get(CAMINHO)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(CAMINHO).contentType(MediaType.APPLICATION_JSON).content(TAXA_E_GRAFICO_MES))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void asPreferenciasDeUmMasterNaoMudamAsDoOutro() throws Exception {
        Utilizador um = interno(PerfilInterno.MASTER);
        Utilizador outro = interno(PerfilInterno.MASTER);
        substituir(jwtInterno(outro), """
                [{"kpiCodigo":"ATIVIDADE_RECENTE","visivel":false,"ordem":0}]""").andExpect(status().isOk());

        substituir(jwtInterno(um), TAXA_E_GRAFICO_MES).andExpect(status().isOk());
        substituir(jwtInterno(um), "[]").andExpect(status().isOk());

        mockMvc.perform(get(CAMINHO).with(jwtInterno(outro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kpiCodigo").value("ATIVIDADE_RECENTE"))
                .andExpect(jsonPath("$[0].visivel").value(false))
                .andExpect(jsonPath("$[*].kpiCodigo", not(hasItem("PROCESSOS_POR_IGT"))));
        assertThat(linhasDe(outro)).isEqualTo(1);
        assertThat(linhasDe(um)).isZero();
    }

    @Test
    void umaLinhaComUmCodigoRetiradoDoCatalogoEIgnoradaSemErro() throws Exception {
        Utilizador master = interno(PerfilInterno.MASTER);
        Timestamp agora = Timestamp.from(Instant.now());
        jdbcTemplate.update("INSERT INTO dashboard_preferencia (utilizador_id, kpi_codigo, visivel, ordem, atualizado_em) "
                + "VALUES (?, 'PROCESSOS_POR_IGT', true, 0, ?), (?, 'GRAFICO_MES', false, 1, ?)",
                master.getId(), agora, master.getId(), agora);

        mockMvc.perform(get(CAMINHO).with(jwtInterno(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CATALOGO.length)))
                .andExpect(jsonPath("$[*].kpiCodigo", not(hasItem("PROCESSOS_POR_IGT"))))
                .andExpect(jsonPath("$[0].kpiCodigo").value("GRAFICO_MES"))
                .andExpect(jsonPath("$[0].visivel").value(false))
                .andExpect(jsonPath("$[0].ordem").value(1));
    }
}
