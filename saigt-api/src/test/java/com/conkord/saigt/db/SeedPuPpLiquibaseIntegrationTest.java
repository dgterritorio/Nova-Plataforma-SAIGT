package com.conkord.saigt.db;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesmo padrão do {@link SeedPdmLiquibaseIntegrationTest}: Liquibase real só sobre os seeds, contra o
 * schema H2 do Hibernate.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:saigt_seed_pu_pp-${random.uuid};MODE=PostgreSQL")
class SeedPuPpLiquibaseIntegrationTest {

    private static final long PDM = 1L;

    private static final String[] SEED_CHANGELOGS = {
            "db/changelog/data/2026-09-10-2-seed-catalogos-globais-pdm.xml",
            "db/changelog/data/2026-09-10-3-seed-motivos-rejeicao.xml",
            "db/changelog/data/2026-09-10-4-seed-config-estrutura-pdm.xml",
            "db/changelog/data/2026-09-10-5-seed-permissao-acao-pdm.xml",
            "db/changelog/data/2026-09-10-6-seed-motor-regras-pdm.xml",
            "db/changelog/data/2026-09-10-7-seed-tipos-instrumento-adicionais.xml",
            "db/changelog/data/2026-09-28-1-seed-config-estrutura-pu-pp.xml",
            "db/changelog/data/2026-09-28-2-seed-permissao-acao-pu-pp.xml",
            "db/changelog/data/2026-09-28-3-seed-motor-regras-pu-pp.xml",
    };

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() throws Exception {
        // Os seeds ainda inserem `ordem`, que a migração seguinte retira e o esquema do Hibernate já não tem.
        jdbcTemplate.execute("ALTER TABLE instrumento_dinamica_config ADD COLUMN IF NOT EXISTS ordem INTEGER");
        PermissaoAcaoDosSeeds.prepararEsquemaAntigo(jdbcTemplate);
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            for (String changelog : SEED_CHANGELOGS) {
                // Liquibase#update está deprecated em favor da CommandScope API; mantido por ser só o arranque do teste.
                @SuppressWarnings("deprecation")
                Liquibase liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), database);
                liquibase.update("");
            }
        }
        PermissaoAcaoDosSeeds.converterParaTipoEntidade(jdbcTemplate);
    }

    /** 2 = PP, 3 = PU. */
    @ParameterizedTest
    @ValueSource(longs = {2L, 3L})
    void should_seedExactRowCounts_when_tipoIsPuOrPp(long tipoInstrumentoId) {
        assertThat(contar("""
                SELECT count(*) FROM instrumento_dinamica_config
                WHERE tipo_instrumento_id = ?""", tipoInstrumentoId)).isEqualTo(13);
        assertThat(contar("""
                SELECT count(*) FROM instrumento_procedimento_config p
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                WHERE d.tipo_instrumento_id = ?""", tipoInstrumentoId)).isEqualTo(18);
        assertThat(contar("""
                SELECT count(*) FROM regra r
                JOIN instrumento_dinamica_config d ON d.id = r.instrumento_dinamica_config_id
                WHERE d.tipo_instrumento_id = ?""", tipoInstrumentoId)).isEqualTo(9);
        assertThat(contar("""
                SELECT count(*) FROM condicao c
                JOIN regra r ON r.id = c.regra_id
                JOIN instrumento_dinamica_config d ON d.id = r.instrumento_dinamica_config_id
                WHERE d.tipo_instrumento_id = ?""", tipoInstrumentoId)).isEqualTo(11);
        assertThat(contar("""
                SELECT count(*) FROM permissao_acao a
                JOIN instrumento_procedimento_config p ON p.id = a.instrumento_procedimento_config_id
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                WHERE d.tipo_instrumento_id = ?""", tipoInstrumentoId)).isEqualTo(36);
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 3L})
    void should_notHaveConstituicaoCc_when_tipoIsPuOrPp(long tipoInstrumentoId) {
        assertThat(contar("""
                SELECT count(*) FROM instrumento_procedimento_config p
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                JOIN tipo_procedimento tp ON tp.id = p.tipo_procedimento_id
                WHERE d.tipo_instrumento_id = ? AND tp.codigo = 'CONSTITUICAO_CC'""", tipoInstrumentoId)).isZero();
        assertThat(contar("""
                SELECT count(*) FROM instrumento_procedimento_config p
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                WHERE d.tipo_instrumento_id = ? AND p.serie_dr <> 'SEGUNDA_H'""", tipoInstrumentoId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 3L})
    void should_followDecisaoWithDiscussaoPublica_when_elaboracao(long tipoInstrumentoId) {
        List<String> elaboracao = jdbcTemplate.queryForList("""
                SELECT tp.codigo FROM instrumento_procedimento_config p
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                JOIN tipo_dinamica td ON td.id = d.tipo_dinamica_id
                JOIN tipo_procedimento tp ON tp.id = p.tipo_procedimento_id
                WHERE d.tipo_instrumento_id = ? AND td.codigo = 'ELABORACAO'
                ORDER BY p.ordem""", String.class, tipoInstrumentoId);

        assertThat(elaboracao).containsExactly("DECISAO_INICIO", "ABERTURA_DISCUSSAO_PUBLICA", "ELABORACAO");
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 3L})
    void should_numberOrdemWithoutGaps_when_tipoIsPuOrPp(long tipoInstrumentoId) {
        // Por dinâmica, ordem = 1..n: max(ordem) igual ao número de procedimentos e sem repetidos.
        assertThat(contar("""
                SELECT count(*) FROM (
                    SELECT p.instrumento_dinamica_config_id FROM instrumento_procedimento_config p
                    JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                    WHERE d.tipo_instrumento_id = ?
                    GROUP BY p.instrumento_dinamica_config_id
                    HAVING min(p.ordem) <> 1 OR max(p.ordem) <> count(*) OR count(DISTINCT p.ordem) <> count(*)
                ) buracos""", tipoInstrumentoId)).isZero();
    }

    @Test
    void should_keepPdmUnchanged_when_puPpSeeded() {
        assertThat(contar("""
                SELECT count(*) FROM instrumento_procedimento_config p
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                WHERE d.tipo_instrumento_id = ?""", PDM)).isEqualTo(20);
        assertThat(contar("""
                SELECT count(*) FROM instrumento_procedimento_config p
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                JOIN tipo_procedimento tp ON tp.id = p.tipo_procedimento_id
                WHERE d.tipo_instrumento_id = ? AND tp.codigo = 'CONSTITUICAO_CC'""", PDM)).isEqualTo(2);
    }

    private long contar(String sql, long tipoInstrumentoId) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, tipoInstrumentoId);
        return total == null ? 0 : total;
    }
}
