package com.conkord.saigt.db;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.RegraRepository;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Corre o Liquibase real só sobre os changesets de seed, contra o schema H2 gerado pelo Hibernate:
 * o {@code master.xml} completo não corre em H2 (changesets com SQL só de Postgres).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:saigt_seed_pdm-${random.uuid};MODE=PostgreSQL")
class SeedPdmLiquibaseIntegrationTest {

    private static final String TIPO_MOTIVO_REJEICAO_DGT = "MOTIVO_REJEICAO_DGT";

    private static final String[] SEED_CHANGELOGS = {
            "db/changelog/data/2026-09-10-2-seed-catalogos-globais-pdm.xml",
            "db/changelog/data/2026-09-10-3-seed-motivos-rejeicao.xml",
            "db/changelog/data/2026-09-10-4-seed-config-estrutura-pdm.xml",
            "db/changelog/data/2026-09-10-5-seed-permissao-acao-pdm.xml",
            "db/changelog/data/2026-09-10-6-seed-motor-regras-pdm.xml",
            "db/changelog/data/2026-09-10-7-seed-tipos-instrumento-adicionais.xml",
    };

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired
    private InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    @Autowired
    private InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    @Autowired
    private PermissaoAcaoRepository permissaoAcaoRepository;
    @Autowired
    private RegraRepository regraRepository;
    @Autowired
    private CondicaoRepository condicaoRepository;
    @Autowired
    private ListaValoresRepository listaValoresRepository;

    @BeforeEach
    void seedPdm() throws Exception {
        runSeedChangelogsOnce();
    }

    // Liquibase#update está deprecated em favor da CommandScope API; mantido por ser só o arranque do teste.
    @SuppressWarnings("deprecation")
    private void runSeedChangelogsOnce() throws Exception {
        // Os seeds ainda inserem `ordem`, que a migração seguinte retira e o esquema do Hibernate já não tem.
        jdbcTemplate.execute("ALTER TABLE instrumento_dinamica_config ADD COLUMN IF NOT EXISTS ordem INTEGER");
        PermissaoAcaoDosSeeds.prepararEsquemaAntigo(jdbcTemplate);
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            for (String changelog : SEED_CHANGELOGS) {
                new Liquibase(changelog, new ClassLoaderResourceAccessor(), database).update("");
            }
        }
        PermissaoAcaoDosSeeds.converterParaTipoEntidade(jdbcTemplate);
    }

    @Test
    void should_seedExactRowCounts_when_liquibaseRuns() {
        assertThat(tipoInstrumentoRepository.findByAtivoTrueOrderByCodigoAsc())
                .extracting(TipoInstrumento::getCodigo)
                .containsExactly("PDM", "PEOT", "PP", "PROT", "PU");
        assertThat(tipoDinamicaRepository.count()).isEqualTo(13);
        assertThat(tipoProcedimentoRepository.count()).isEqualTo(25);
        assertThat(instrumentoDinamicaConfigRepository.count()).isEqualTo(13);
        assertThat(instrumentoProcedimentoConfigRepository.count()).isEqualTo(20);
        assertThat(permissaoAcaoRepository.count()).isEqualTo(40);
        assertThat(regraRepository.count()).isEqualTo(9);
        assertThat(condicaoRepository.count()).isEqualTo(11);

        List<ListaValores> motivos = listaValoresRepository.findByTipoOrderByOrdemAsc(TIPO_MOTIVO_REJEICAO_DGT);
        assertThat(motivos).extracting(ListaValores::getCodigo)
                .containsExactly("DOC_INCOMPLETA", "PRAZO_EXPIRADO");
    }

    @Test
    void should_useLldCodes_when_tiposSeeded() {
        assertThat(jdbcTemplate.queryForList("SELECT codigo FROM tipo_dinamica", String.class))
                .contains("ALTERACAO_SIMPLIFICADA_ART123", "ALTERACAO_SIMPLIFICADA_DL10_2024", "SUSPENSAO");
        assertThat(jdbcTemplate.queryForList("SELECT codigo FROM tipo_procedimento", String.class))
                .contains("DECISAO_INICIO", "ALTERACAO_ATO_ANEXOS", "RETIFICACAO_ATO_ANEXOS")
                .doesNotContain("DECISAO_ELABORAR", "DECISAO_REVER", "DECISAO_ALTERAR");
    }

    @Test
    void should_startWithDecisaoInicio_when_elaboracaoRevisaoOrAlteracao() {
        List<String> primeiros = jdbcTemplate.queryForList("""
                SELECT tp.codigo FROM instrumento_procedimento_config p
                JOIN instrumento_dinamica_config d ON d.id = p.instrumento_dinamica_config_id
                JOIN tipo_dinamica td ON td.id = d.tipo_dinamica_id
                JOIN tipo_procedimento tp ON tp.id = p.tipo_procedimento_id
                WHERE d.tipo_instrumento_id = 1 AND p.ordem = 1 AND td.codigo IN ('ELABORACAO', 'REVISAO', 'ALTERACAO')
                ORDER BY td.id""", String.class);

        assertThat(primeiros).containsExactly("DECISAO_INICIO", "DECISAO_INICIO", "DECISAO_INICIO");
    }

    @Test
    void should_acceptFortyCharacterCodigo_when_tipoProcedimentoSeeded() {
        // Ids 9 e 12: os dois códigos que não cabiam em VARCHAR(30).
        TipoProcedimento rerae = tipoProcedimentoRepository.findById(9L).orElseThrow();
        TipoProcedimento dl10 = tipoProcedimentoRepository.findById(12L).orElseThrow();

        assertThat(rerae.getCodigo()).isEqualTo("ABERTURA_DISCUSSAO_PUBLICA_RERAE").hasSize(32);
        assertThat(dl10.getCodigo()).isEqualTo("ABERTURA_DISCUSSAO_PUBLICA_DL10").hasSize(31);
    }

    @Test
    void should_notDuplicateRows_when_liquibaseReexecuted() throws Exception {
        runSeedChangelogsOnce();
        runSeedChangelogsOnce();

        assertThat(tipoInstrumentoRepository.count()).isEqualTo(5);
        assertThat(tipoProcedimentoRepository.count()).isEqualTo(25);
        assertThat(permissaoAcaoRepository.count()).isEqualTo(40);
    }
}
