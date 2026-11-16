package com.conkord.saigt.db;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import com.conkord.saigt.service.instrumento.ValidadorSubmissaoProcesso;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * As validações da Informação Base na submissão de PU, PP, PDIM, PUIM e PPIM, contra o PostgreSQL migrado pelo
 * {@code master.xml} (itens da lista da DGT de 04/11). Nada se semeia: o Procedimento, os elementos com Informação Base
 * e o procedimento final vêm da configuração real; o teste só cria o Instrumento, a Dinâmica e o Processo e chama a
 * mesma validação que o {@code ProcessoService.submeter}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("docker")
class InformacaoBasePlanosPostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String CODIGO_CAMARA_DE_SINTRA = "5245";
    private static final List<String> PLANOS = List.of("PU", "PP", "PDIM", "PUIM", "PPIM");
    private static final List<String> DINAMICAS = List.of("ELABORACAO", "REVISAO", "ALTERACAO");

    private static final String CAMPO_PRAZO = "informacaoBase.textoAto.prazoAnos";
    private static final String CAMPO_AVALIACAO = "informacaoBase.textoAto.avaliacaoAmbiental";
    private static final String CAMPO_DATA = "informacaoBase.deliberacao.dataDeliberacao";
    private static final String MSG_INCOERENTE = "A Informação Base tem campos incoerentes.";
    private static final String MSG_DATA_LIMITE = "A data da deliberação tem de ser igual ou anterior à data limite da dinâmica.";

    private static final LocalDate DATA_TEXTO_ATO = LocalDate.of(2026, 10, 10);
    private static final LocalDate DATA_LIMITE = LocalDate.of(2026, 9, 30);

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
    private TransactionTemplate transacao;

    @Autowired
    private ValidadorSubmissaoProcesso validador;

    @Autowired
    private CatalogoFormularios catalogoFormularios;

    @Autowired
    private NumeroDepositoService numeroDepositoService;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    static Stream<Arguments> procedimentosComOsDoisElementos() {
        List<Arguments> casos = new ArrayList<>();
        for (String plano : PLANOS) {
            for (String dinamica : DINAMICAS) {
                casos.add(Arguments.of(plano, dinamica, "DECISAO_INICIO"));
                casos.add(Arguments.of(plano, dinamica, "ABERTURA_DISCUSSAO_PUBLICA"));
                casos.add(Arguments.of(plano, dinamica, dinamica));
            }
        }
        return casos.stream();
    }

    static Stream<Arguments> procedimentosFinais() {
        return PLANOS.stream().flatMap(plano -> DINAMICAS.stream().map(dinamica -> Arguments.of(plano, dinamica)));
    }

    static Stream<Arguments> discussoesPublicas() {
        return procedimentosFinais();
    }

    static Stream<Arguments> decisoesDeInicio() {
        return procedimentosFinais();
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("procedimentosComOsDoisElementos")
    void comOsDoisElementosIncoerentesRecusaNumaSoRecusaComOsTresCaminhos(String plano, String dinamica,
                                                                         String procedimento) {
        long config = procedimentoConfigId(plano, dinamica, procedimento);
        assertThat(elementosAtivos(config))
                .contains(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        UUID processo = processo(config, informacaoBase(1, true, "2026-10-20", 2, false), null);

        ValidacaoException erro = validar(processo);

        assertThat(erro).as("400 VALIDACAO na submissão").isNotNull();
        assertThat(erro.getCodigo()).isEqualTo(ValidacaoException.CODIGO);
        assertThat(erro.getMessage()).isEqualTo(MSG_INCOERENTE);
        assertThat(erro.getCampos()).extracting(CampoErro::path)
                .containsExactlyInAnyOrder(CAMPO_PRAZO, CAMPO_AVALIACAO, CAMPO_DATA);
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("procedimentosComOsDoisElementos")
    void comOsDoisElementosCoerentesPassa(String plano, String dinamica, String procedimento) {
        long config = procedimentoConfigId(plano, dinamica, procedimento);
        // Data da deliberação igual à do texto do ato e prazo com nulos (= 0) iguais.
        UUID processo = processo(config, informacaoBase(1, true, DATA_TEXTO_ATO.toString(), 1, true), null);

        assertThat(validar(processo)).isNull();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("procedimentosFinais")
    void noProcedimentoFinalADataDepoisDaDataLimiteERecusadaEAIgualPassa(String plano, String dinamica) {
        long config = procedimentoConfigId(plano, dinamica, dinamica);
        assertThat(formulario(config)).isIn(FormularioFixo.INFORMACAO_GERAL, FormularioFixo.PUBLICACAO_ALTERACAO);
        UUID depois = processo(config, informacaoBase(1, true, "2026-10-01", 1, true), DATA_LIMITE);
        assertThat(doProcedimentoFinal(depois)).as("é o procedimento final da Dinâmica").isTrue();

        ValidacaoException erro = validar(depois);

        assertThat(erro).isNotNull();
        assertThat(erro.getMessage()).isEqualTo(MSG_DATA_LIMITE);
        assertThat(erro.getCampos()).containsExactly(
                new CampoErro(CAMPO_DATA, "Posterior à data limite da dinâmica (30/09/2026)."));

        UUID igual = processo(config, informacaoBase(1, true, DATA_LIMITE.toString(), 1, true), DATA_LIMITE);
        assertThat(validar(igual)).isNull();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("procedimentosFinais")
    void noProcedimentoFinalSemDataLimiteARegraNaoSeAplica(String plano, String dinamica) {
        long config = procedimentoConfigId(plano, dinamica, dinamica);
        UUID processo = processo(config, informacaoBase(1, true, "2026-10-01", 1, true), null);

        assertThat(validar(processo)).isNull();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("discussoesPublicas")
    void naDiscussaoPublicaADataDepoisDaDataLimiteContinuaRecusada(String plano, String dinamica) {
        long config = procedimentoConfigId(plano, dinamica, "ABERTURA_DISCUSSAO_PUBLICA");
        UUID processo = processo(config, informacaoBase(1, true, "2026-10-01", 1, true), DATA_LIMITE);

        ValidacaoException erro = validar(processo);

        assertThat(erro).isNotNull();
        assertThat(erro.getMessage()).isEqualTo(MSG_DATA_LIMITE);
        assertThat(erro.getCampos()).extracting(CampoErro::path).containsExactly(CAMPO_DATA);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("decisoesDeInicio")
    void naDecisaoDeInicioADataLimiteNaoSeAplica(String plano, String dinamica) {
        long config = procedimentoConfigId(plano, dinamica, "DECISAO_INICIO");
        UUID processo = processo(config, informacaoBase(1, true, "2026-10-01", 1, true), DATA_LIMITE);
        assertThat(doProcedimentoFinal(processo)).isFalse();

        assertThat(validar(processo)).isNull();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("procedimentosFinais")
    void numProcedimentoSoComOTextoDoAtoNaoHaCoerenciaAVerificar(String plano, String dinamica) {
        // A Prorrogação do ato de cada Dinâmica: o único item com Informação Base é o Texto do ato.
        long config = procedimentoConfigId(plano, dinamica, "PRORROGACAO_ATO");
        assertThat(elementosAtivos(config)).containsExactly(ElementoInformacaoBase.TEXTO_ATO);
        UUID processo = processo(config, informacaoBase(1, true, "2026-10-20", 2, false), null);

        assertThat(validar(processo)).isNull();
    }

    private ValidacaoException validar(UUID processoId) {
        return transacao.execute(estado -> {
            Processo processo = processoRepository.findById(processoId).orElseThrow();
            FormularioFixo formulario = catalogoFormularios
                    .formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento()).orElseThrow();
            return catchThrowableOfType(() -> validador.verificarInformacaoBase(processo, formulario),
                    ValidacaoException.class);
        });
    }

    private boolean doProcedimentoFinal(UUID processoId) {
        return Boolean.TRUE.equals(transacao.execute(estado ->
                numeroDepositoService.doProcedimentoFinal(processoRepository.findById(processoId).orElseThrow())));
    }

    private FormularioFixo formulario(long config) {
        return transacao.execute(estado -> catalogoFormularios.formularioDe(
                procedimentoConfigRepository.findById(config).orElseThrow().getTipoProcedimento()).orElseThrow());
    }

    private Set<ElementoInformacaoBase> elementosAtivos(long config) {
        return transacao.execute(estado -> documentoConfigRepository.findElementosInformacaoBaseAtivos(
                procedimentoConfigRepository.findById(config).orElseThrow()));
    }

    /** Um Processo `EM_SUBMISSAO` do Procedimento, com a Informação Base no formulário e a data de assinatura do ato. */
    private UUID processo(long config, String dadosFormulario, LocalDate dataLimite) {
        return transacao.execute(estado -> {
            InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.findById(config).orElseThrow();
            String sufixo = UUID.randomUUID().toString().substring(0, 8);
            Utilizador autor = utilizadorRepository.saveAndFlush(
                    new Utilizador("Autor " + sufixo, "autor-" + sufixo + "@teste-informacao-base.pt", "INCM"));
            Instrumento instrumento = instrumentoRepository.saveAndFlush(new Instrumento(
                    procedimento.getInstrumentoDinamicaConfig().getTipoInstrumento(), "IB-" + sufixo,
                    "Plano de teste " + sufixo, autor, autor));
            Dinamica dinamica = new Dinamica(instrumento, procedimento.getInstrumentoDinamicaConfig(), "Teste " + sufixo, autor);
            dinamica.setDataCaducidade(dataLimite);
            dinamica = dinamicaRepository.saveAndFlush(dinamica);
            Processo processo = new Processo(dinamica, procedimento, autor,
                    entidadeRepository.findByCodigo(CODIGO_CAMARA_DE_SINTRA).orElseThrow());
            processo.setDadosFormulario(dadosFormulario);
            processo.setDataAssinatura(DATA_TEXTO_ATO);
            return processoRepository.saveAndFlush(processo).getId();
        });
    }

    private long procedimentoConfigId(String plano, String dinamica, String procedimento) {
        return jdbcTemplate.queryForObject("SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                + "WHERE ti.codigo = ? AND td.codigo = ? AND tp.codigo = ?", Long.class, plano, dinamica, procedimento);
    }

    /** Deliberação com prazo em anos (meses e dias nulos) e data; Texto do ato com prazo em anos e meses a 0. */
    private static String informacaoBase(int anosDeliberacao, boolean avaliacaoDeliberacao, String dataDeliberacao,
                                         int anosTextoAto, boolean avaliacaoTextoAto) {
        return "{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":" + anosDeliberacao
                + ",\"prazoMeses\":null,\"prazoDias\":null,\"avaliacaoAmbiental\":" + avaliacaoDeliberacao
                + ",\"dataDeliberacao\":\"" + dataDeliberacao + "\"},\"textoAto\":{\"prazoAnos\":" + anosTextoAto
                + ",\"prazoMeses\":0,\"avaliacaoAmbiental\":" + avaliacaoTextoAto + "}}}";
    }
}
