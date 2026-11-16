package com.conkord.saigt.db;

import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.service.regras.AvaliadorRegistoValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * O seed das Dinâmicas transversais (Suspensão da CCDR, Suspensão e Medidas Preventivas do Governo, Ratificação) contra o
 * PostgreSQL migrado pelo {@code master.xml}, com a aplicação a arrancar sobre ele.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("docker")
class SeedDinamicasTransversaisPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final List<String> PLANOS = List.of("PDIM", "PDM", "PP", "PPIM", "PU", "PUIM");
    private static final String DAS_DINAMICAS = "FROM instrumento_dinamica_config idc "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id ";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:15-alpine")
            .withEnv("POSTGRES_PASSWORD", "teste")
            .withEnv("POSTGRES_DB", "saigt")
            .withCopyToContainer(Transferable.of("CREATE ROLE saigt_app LOGIN PASSWORD 'teste';"),
                    "/docker-entrypoint-initdb.d/01-saigt-app.sql")
            .withExposedPorts(PORTA_POSTGRES)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\n", 2));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PORTA_POSTGRES) + "/saigt";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "saigt_app");
        registry.add("spring.datasource.password", () -> "teste");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.url", () -> url);
        registry.add("spring.liquibase.user", () -> "postgres");
        registry.add("spring.liquibase.password", () -> "teste");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AvaliadorRegistoValidator avaliadorRegistoValidator;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Test
    void deveSemearOsQuatroTiposTransversaisSemIgtSemIdusEComOSnitDoGoverno() {
        assertThat(linhas("SELECT codigo || ':' || nome || ':' || inicio_sem_igt || ':' || coalesce(snit_codigo, '-') || ':' "
                + "|| coalesce(codigo_idus, '-') || ':' || ativo FROM tipo_dinamica WHERE codigo IN ('SUSPENSAO_CCDR', "
                + "'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO') ORDER BY codigo")).containsExactly(
                "MEDIDAS_PREVENTIVAS_GOVERNO:Medidas Preventivas (iniciativa do Governo):true:15:-:true",
                "RATIFICACAO:Ratificação:true:-:-:true",
                "SUSPENSAO_CCDR:Suspensão (iniciativa da CCDR):true:-:-:true",
                "SUSPENSAO_GOVERNO:Suspensão (iniciativa do Governo):true:18:-:true");
        assertThat(tipoDinamicaRepository.findAll().stream().filter(TipoDinamica::isInicioSemIgt).map(TipoDinamica::getCodigo))
                .containsExactlyInAnyOrder("MEDIDAS_PREVENTIVAS", "SUSPENSAO_CCDR", "SUSPENSAO_GOVERNO",
                        "MEDIDAS_PREVENTIVAS_GOVERNO", "RATIFICACAO");
    }

    @Test
    void asMedidasPreventivasDoMunicipioDevemIniciarSeSemIgtComAssociacaoOpcionalNosSeisPlanos() {
        assertThat(linhas("SELECT nome || ':' || inicio_sem_igt FROM tipo_dinamica WHERE codigo = 'MEDIDAS_PREVENTIVAS'"))
                .containsExactly("Medidas Preventivas (iniciativa do município):true");
        assertThat(linhas("SELECT ti.codigo || ':' || idc.associacao_instrumentos " + DAS_DINAMICAS
                + "WHERE td.codigo = 'MEDIDAS_PREVENTIVAS' ORDER BY ti.codigo"))
                .containsExactlyElementsOf(PLANOS.stream().map(t -> t + ":OPCIONAL").toList());
    }

    @Test
    void cadaDinamicaDeveTerUmaConfiguracaoAtivaPorTipoDeInstrumentoAdmitidoPresenteNaBase() {
        List<String> planosEProgramas = linhas("SELECT codigo FROM tipo_instrumento WHERE categoria IN ('PLANO', 'PROGRAMA') "
                + "ORDER BY codigo");
        assertThat(planosEProgramas).containsAll(List.of("PDM", "PU", "PP", "PDIM", "PUIM", "PPIM", "PROT", "PEOT"));

        assertThat(configuracoes("SUSPENSAO_CCDR")).containsExactlyElementsOf(comAssociacao(PLANOS, "OPCIONAL"));
        assertThat(configuracoes("SUSPENSAO_GOVERNO")).containsExactlyElementsOf(comAssociacao(planosEProgramas, "UM_OU_MAIS"));
        assertThat(configuracoes("MEDIDAS_PREVENTIVAS_GOVERNO"))
                .containsExactlyElementsOf(comAssociacao(planosEProgramas, "UM_OU_MAIS"));
        assertThat(configuracoes("RATIFICACAO")).containsExactly("PDIM:UM:true", "PDM:UM:true");
        assertThat(linhas("SELECT DISTINCT ti.categoria " + DAS_DINAMICAS + "WHERE td.codigo IN ('SUSPENSAO_CCDR', "
                + "'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO') ORDER BY 1")).containsExactly("PLANO", "PROGRAMA");
    }

    @Test
    void cadaConfiguracaoDeveTerOsProcedimentosPelaOrdemComASerieDaEf() {
        assertThat(procedimentosDistintos("SUSPENSAO_GOVERNO")).containsExactly(
                "1:PUBLICACAO_SUSPENSAO_GOVERNO:true:PRIMEIRA:true:false:false:5368709120",
                "2:PRORROGACAO_SUSPENSAO_GOVERNO:false:PRIMEIRA:true:false:false:5368709120");
        assertThat(procedimentosDistintos("MEDIDAS_PREVENTIVAS_GOVERNO")).containsExactly(
                "1:PUBLICACAO_MP_GOVERNO:true:PRIMEIRA:true:false:false:5368709120",
                "2:PRORROGACAO_MP_GOVERNO:false:PRIMEIRA:true:false:false:5368709120");
        assertThat(procedimentosDistintos("SUSPENSAO_CCDR"))
                .containsExactly("1:SUSPENSAO_CCDR:true:SEGUNDA_C:true:false:false:5368709120");
        assertThat(procedimentosDistintos("RATIFICACAO"))
                .containsExactly("1:RATIFICACAO:true:PRIMEIRA:true:false:false:5368709120");

        assertThat(contar("SELECT count(*) FROM instrumento_dinamica_config idc JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE td.codigo IN ('SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO') "
                + "AND (SELECT count(*) FROM instrumento_procedimento_config ipc WHERE ipc.instrumento_dinamica_config_id = idc.id) "
                + "<> CASE WHEN td.codigo LIKE '%\\_GOVERNO' THEN 2 ELSE 1 END")).isZero();
        assertThat(linhas("SELECT DISTINCT tp.codigo || ':' || coalesce(ipc.codigo_idus, '-') FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id JOIN instrumento_dinamica_config idc "
                + "ON idc.id = ipc.instrumento_dinamica_config_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE td.codigo IN ('SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO') ORDER BY 1"))
                .allMatch(linha -> linha.endsWith(":-"));
    }

    @Test
    void soASuspensaoDaCcdrEAsDoGovernoDevemTerARegraDoInstrumentoEmVigor() {
        assertThat(linhas("SELECT td.codigo || ':' || count(DISTINCT idc.id) || ':' || count(DISTINCT r.id) || ':' "
                + "|| string_agg(DISTINCT r.operador || '/' || r.substitui_sequenciamento || '/' || r.descricao || '/' "
                + "|| c.avaliador || '/' || c.parametros::text, ',') " + DAS_DINAMICAS
                + "JOIN regra r ON r.instrumento_dinamica_config_id = idc.id JOIN condicao c ON c.regra_id = r.id "
                + "WHERE td.codigo IN ('SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO') "
                + "AND r.ativo AND c.ativo GROUP BY td.codigo ORDER BY td.codigo")).containsExactly(
                "MEDIDAS_PREVENTIVAS_GOVERNO:" + configuracoes("MEDIDAS_PREVENTIVAS_GOVERNO").size() + ":"
                        + configuracoes("MEDIDAS_PREVENTIVAS_GOVERNO").size() + ":AND/false/Requer instrumento em vigor/INSTRUMENTO_EM_VIGOR/{}",
                "SUSPENSAO_CCDR:6:6:AND/false/Requer instrumento em vigor/INSTRUMENTO_EM_VIGOR/{}",
                "SUSPENSAO_GOVERNO:" + configuracoes("SUSPENSAO_GOVERNO").size() + ":"
                        + configuracoes("SUSPENSAO_GOVERNO").size() + ":AND/false/Requer instrumento em vigor/INSTRUMENTO_EM_VIGOR/{}");
        assertThat(contar("SELECT count(*) FROM regra r JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE td.codigo = 'RATIFICACAO'")).isZero();
        assertThat(contar("SELECT count(*) FROM regra r JOIN instrumento_procedimento_config ipc ON ipc.id = r.instrumento_procedimento_config_id "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id JOIN tipo_dinamica td "
                + "ON td.id = idc.tipo_dinamica_id WHERE td.codigo IN ('SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', "
                + "'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO')")).isZero();
        assertThat(contar("SELECT count(*) FROM condicao WHERE avaliador = 'INSTRUMENTO_EM_VIGOR' AND regra_id IN (SELECT r.id FROM regra r "
                + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id JOIN tipo_dinamica td "
                + "ON td.id = idc.tipo_dinamica_id WHERE td.codigo IN ('SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', "
                + "'MEDIDAS_PREVENTIVAS_GOVERNO'))")).isEqualTo(6 + 2 * configuracoes("SUSPENSAO_GOVERNO").size());
    }

    @Test
    void cadaProcedimentoDeveTerAsPermissoesDaEntidadeDeOrigemEDaDgtSemCamaraEAPcmSoNaPrimeiraSerie() {
        assertThat(linhas("SELECT td.codigo || ':' || string_agg(DISTINCT pa.tipo_entidade, ',' ORDER BY pa.tipo_entidade) "
                + DAS_DINAMICAS + "JOIN instrumento_procedimento_config ipc ON ipc.instrumento_dinamica_config_id = idc.id "
                + "JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id AND pa.ativo "
                + "WHERE td.codigo IN ('SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO') "
                + "GROUP BY td.codigo ORDER BY td.codigo")).containsExactly("MEDIDAS_PREVENTIVAS_GOVERNO:DGT,PCM,SEC_ESTADO",
                "RATIFICACAO:DGT,PCM,SEC_ESTADO", "SUSPENSAO_CCDR:CCDR_DR,DGT", "SUSPENSAO_GOVERNO:DGT,PCM,SEC_ESTADO");
        assertThat(contar("SELECT count(*) FROM instrumento_procedimento_config ipc JOIN instrumento_dinamica_config idc "
                + "ON idc.id = ipc.instrumento_dinamica_config_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE td.codigo IN ('SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO') "
                + "AND (SELECT count(*) FROM permissao_acao pa WHERE pa.instrumento_procedimento_config_id = ipc.id AND pa.ativo "
                + "AND pa.tipo_entidade <> 'PCM') <> 2"))
                .isZero();
        assertThat(linhas("SELECT DISTINCT pa.tipo_entidade FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
                + "ON ipc.id = pa.instrumento_procedimento_config_id JOIN instrumento_dinamica_config idc "
                + "ON idc.id = ipc.instrumento_dinamica_config_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE td.codigo = 'MEDIDAS_PREVENTIVAS' ORDER BY 1")).containsExactly("CM", "DGT");
    }

    @Test
    void oValidadorDosAvaliadoresDeveAceitarAConfiguracaoSemeada() {
        assertThatCode(() -> avaliadorRegistoValidator.run(null)).doesNotThrowAnyException();
    }

    private List<String> configuracoes(String tipoDinamica) {
        return linhas("SELECT ti.codigo || ':' || idc.associacao_instrumentos || ':' || idc.ativo " + DAS_DINAMICAS
                + "WHERE td.codigo = ? ORDER BY ti.codigo", tipoDinamica);
    }

    private static List<String> comAssociacao(List<String> tipos, String associacao) {
        List<String> esperadas = new ArrayList<>();
        tipos.forEach(tipo -> esperadas.add(tipo + ":" + associacao + ":true"));
        return esperadas;
    }

    private List<String> procedimentosDistintos(String tipoDinamica) {
        return linhas("SELECT DISTINCT ipc.ordem || ':' || tp.codigo || ':' || ipc.obrigatorio || ':' || ipc.serie_dr || ':' "
                + "|| ipc.ativo || ':' || ipc.repetivel || ':' || ipc.ato_pontual || ':' || ipc.tamanho_maximo_processo "
                + "FROM instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE td.codigo = ? ORDER BY 1", tipoDinamica);
    }

    private List<String> linhas(String consulta, Object... argumentos) {
        return jdbcTemplate.queryForList(consulta, String.class, argumentos);
    }

    private int contar(String consulta) {
        return jdbcTemplate.queryForObject(consulta, Integer.class);
    }
}
