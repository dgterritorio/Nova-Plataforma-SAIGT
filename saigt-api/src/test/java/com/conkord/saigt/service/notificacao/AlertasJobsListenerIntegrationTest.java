package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.AlertaCaducidadeDinamicaEvento;
import com.conkord.saigt.service.instrumento.AvisoInatividadeProcessoEvento;
import com.conkord.saigt.service.instrumento.LimiarCaducidade;
import com.conkord.saigt.service.instrumento.LimiarInatividade;
import com.conkord.saigt.service.instrumento.ProcessoAnuladoPorInatividadeEvento;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Eventos publicados numa transação que confirma, como nos jobs, para o listener `AFTER_COMMIT` correr de verdade. */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class AlertasJobsListenerIntegrationTest {

    private static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String DECISAO_INICIO = "DECISAO_INICIO";

    @Autowired
    private ApplicationEventPublisher publicador;
    @Autowired
    private ApplicationEvents eventos;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NotificacaoVisibilidade visibilidade;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    @Autowired
    private ProcessoRepository processoRepository;
    @Autowired
    private DinamicaRepository dinamicaRepository;
    @Autowired
    private InstrumentoRepository instrumentoRepository;
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

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertasJobsListener.class);

    private Instrumento instrumento;
    private InstrumentoDinamicaConfig dinamicaConfig;
    private InstrumentoProcedimentoConfig configDecisaoInicio;
    private InstrumentoProcedimentoConfig configOutro;
    private Entidade entidadeA;
    private Entidade entidadeB;
    private Utilizador autorU;
    private Utilizador colegaV;
    private Utilizador deOutraW;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);

        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("ALERTA" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALERTA-D" + n, "Revisão"));
        dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        configDecisaoInicio = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisaoInicio(), 1, "SEGUNDA_H"));
        configOutro = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig,
                tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("ALERTA-P" + n, "Discussão pública")),
                2, "SEGUNDA_H"));

        entidadeA = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5185%05d".formatted(n), "Câmara A " + n));
        entidadeB = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5186%05d".formatted(n), "Câmara B " + n));
        autorU = utilizadorRepository.saveAndFlush(new Utilizador("Ulisses Fictício", "u" + n + "@cm-a.pt", "INCM"));
        colegaV = utilizadorRepository.saveAndFlush(new Utilizador("Vera Fictícia", "v" + n + "@cm-a.pt", "INCM"));
        deOutraW = utilizadorRepository.saveAndFlush(new Utilizador("Wanda Fictícia", "w" + n + "@cm-b.pt", "INCM"));
        vincular(autorU, entidadeA);
        vincular(colegaV, entidadeA);
        vincular(deOutraW, entidadeB);

        instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, String.valueOf(n), "2026/ALERTA/" + n, autorU, autorU));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private TipoProcedimento decisaoInicio() {
        return tipoProcedimentoRepository.findAll().stream()
                .filter(tipo -> DECISAO_INICIO.equals(tipo.getCodigo()))
                .findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento(DECISAO_INICIO, "Decisão de início")));
    }

    private void vincular(Utilizador utilizador, Entidade entidade) {
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(utilizador, entidade, Instant.now()));
    }

    private Dinamica dinamica(String descritivo) {
        return dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, descritivo, autorU));
    }

    private Processo processo(Dinamica dinamica, InstrumentoProcedimentoConfig config, EstadoProcesso estado,
                              LocalDate dataPublicacao) {
        Processo processo = new Processo(dinamica, config, autorU, entidadeA);
        processo.setEstado(estado);
        processo.setDataPublicacaoDr(dataPublicacao);
        return processoRepository.saveAndFlush(processo);
    }

    /** `atualizado_em` só se muda por SQL, porque cada gravação o repõe; relê-se para ter a precisão da BD. */
    private Processo parado(EstadoProcesso estado, Instant atualizadoEm) {
        Processo processo = processo(dinamica("Alteração"), configOutro, estado, null);
        jdbcTemplate.update("UPDATE processo SET atualizado_em = ? WHERE id = ?", Timestamp.from(atualizadoEm),
                processo.getId());
        return processoRepository.findById(processo.getId()).orElseThrow();
    }

    private void publicarComCommit(Object evento) {
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> publicador.publishEvent(evento));
    }

    private List<Map<String, Object>> linhasDe(Processo processo) {
        return jdbcTemplate.queryForList("SELECT * FROM notificacao WHERE processo_id = ? ORDER BY criado_em",
                processo.getId());
    }

    private static List<Object> coluna(List<Map<String, Object>> linhas, String nome) {
        return linhas.stream().map(linha -> linha.get(nome)).toList();
    }

    private static Instant ha(Period periodo) {
        return Instant.now().atZone(LISBOA).minus(periodo).toInstant();
    }

    private static Instant mais(Instant instante, Period periodo) {
        return instante.atZone(LISBOA).plus(periodo).toInstant();
    }

    private AvisoInatividadeProcessoEvento aviso(Processo processo, LimiarInatividade limiar, Instant momentoLimiar,
                                                 LocalDate dataAnulacao) {
        return new AvisoInatividadeProcessoEvento(processo.getId(), limiar, momentoLimiar, processo.getAtualizadoEm(),
                dataAnulacao);
    }

    private List<String> mensagens(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void caducidadeAUmMesNotificaOAutorEOColegaDaEntidadeNoProcessoDaDecisaoDeInicio() {
        Dinamica dinamica = dinamica("Revisão do PDM");
        processo(dinamica, configDecisaoInicio, EstadoProcesso.PUBLICADO, LocalDate.of(2024, 1, 10));
        Processo referencia = processo(dinamica, configDecisaoInicio, EstadoProcesso.PUBLICADO, LocalDate.of(2025, 3, 1));
        processo(dinamica, configDecisaoInicio, EstadoProcesso.EM_SUBMISSAO, null);

        publicarComCommit(new AlertaCaducidadeDinamicaEvento(dinamica.getId(), LimiarCaducidade.UM_MES,
                LocalDate.of(2027, 3, 15)));

        List<Map<String, Object>> linhas = linhasDe(referencia);
        assertThat(coluna(linhas, "utilizador_id")).containsExactlyInAnyOrder(autorU.getId(), colegaV.getId());
        assertThat(coluna(linhas, "lote_id")).doesNotContainNull().containsOnly(linhas.get(0).get("lote_id"));
        assertThat(coluna(linhas, "criado_por")).containsOnlyNulls();
        assertThat(coluna(linhas, "entidade_id")).containsOnlyNulls();
        assertThat(coluna(linhas, "perfil_destino")).containsOnlyNulls();
        assertThat(coluna(linhas, "tipo")).containsOnly("TEMPORARIA");
        assertThat(coluna(linhas, "estado")).containsOnly("ATIVA");
        assertThat(coluna(linhas, "titulo")).containsOnly("Dinâmica próxima da data de caducidade");
        String numero = dinamicaRepository.findById(dinamica.getId()).orElseThrow().getNumeroDinamica();
        assertThat(coluna(linhas, "texto")).containsOnly("A dinâmica " + numero
                + " — Revisão do PDM, com data limite de conclusão a 15/03/2027, ainda não foi terminada.");

        NotificacaoCriadaEvento evento = eventos.stream(NotificacaoCriadaEvento.class)
                .filter(e -> Objects.equals(e.loteId(), linhas.get(0).get("lote_id"))).findFirst().orElseThrow();
        assertThat(evento.notificacaoIds()).containsExactlyInAnyOrderElementsOf(
                linhas.stream().map(linha -> (UUID) linha.get("id")).toList());
    }

    @Test
    void caducidadeNaDataTemOTituloDeCaducadaEADataNoFormatoPortugues() {
        Dinamica dinamica = dinamica("Revisão do PDM");
        Processo referencia = processo(dinamica, configDecisaoInicio, EstadoProcesso.PUBLICADO, LocalDate.of(2025, 3, 1));

        publicarComCommit(new AlertaCaducidadeDinamicaEvento(dinamica.getId(), LimiarCaducidade.DATA,
                LocalDate.of(2026, 11, 2)));

        String numero = dinamicaRepository.findById(dinamica.getId()).orElseThrow().getNumeroDinamica();
        List<Map<String, Object>> linhas = linhasDe(referencia);
        assertThat(coluna(linhas, "titulo")).hasSize(2).containsOnly("Dinâmica caducada");
        assertThat(coluna(linhas, "texto")).containsOnly("A dinâmica " + numero
                + " — Revisão do PDM caducou a 02/11/2026. Pode continuar a dinâmica ou recomeçá-la do zero.");
    }

    @Test
    void caducidadeDeDinamicaSemDescritivoNaoTemTravessaoNemEspacoAMais() {
        Dinamica dinamica = dinamica("");
        Processo referencia = processo(dinamica, configDecisaoInicio, EstadoProcesso.PUBLICADO, LocalDate.of(2025, 3, 1));

        publicarComCommit(new AlertaCaducidadeDinamicaEvento(dinamica.getId(), LimiarCaducidade.QUINZE_DIAS,
                LocalDate.of(2027, 3, 15)));

        String numero = dinamicaRepository.findById(dinamica.getId()).orElseThrow().getNumeroDinamica();
        assertThat(coluna(linhasDe(referencia), "texto")).containsOnly("A dinâmica " + numero
                + ", com data limite de conclusão a 15/03/2027, ainda não foi terminada.");
    }

    @Test
    void caducidadeSemDecisaoDeInicioPublicadaNaoCriaNadaEDeixaUmWarn() {
        Dinamica dinamica = dinamica("Revisão do PDM");
        Processo emSubmissao = processo(dinamica, configDecisaoInicio, EstadoProcesso.EM_SUBMISSAO, null);
        Processo outroPublicado = processo(dinamica, configOutro, EstadoProcesso.PUBLICADO, LocalDate.of(2025, 3, 1));

        publicarComCommit(new AlertaCaducidadeDinamicaEvento(dinamica.getId(), LimiarCaducidade.UM_MES,
                LocalDate.of(2027, 3, 15)));

        assertThat(linhasDe(emSubmissao)).isEmpty();
        assertThat(linhasDe(outroPublicado)).isEmpty();
        assertThat(mensagens(Level.WARN)).anySatisfy(m -> assertThat(m)
                .contains("Caducidade sem processo de Decisão de início publicado")
                .contains("dinamicaId=" + dinamica.getId()));
    }

    @Test
    void primeiroAvisoDeInatividadeChegaAoAutorEAosUtilizadoresDaEntidadeComADataDeAnulacao() {
        Processo processo = parado(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 3, 1)));

        publicarComCommit(aviso(processo, LimiarInatividade.PRIMEIRO_AVISO,
                mais(processo.getAtualizadoEm(), Period.ofMonths(3)), LocalDate.of(2026, 12, 20)));

        List<Map<String, Object>> linhas = linhasDe(processo);
        assertThat(coluna(linhas, "utilizador_id")).containsExactlyInAnyOrder(autorU.getId(), colegaV.getId());
        assertThat(coluna(linhas, "titulo")).containsOnly("Processo em risco de anulação por inatividade");
        assertThat(coluna(linhas, "texto")).containsOnly("O processo " + processo.getNumeroProcesso()
                + " (Discussão pública) será anulado automaticamente a 20/12/2026 caso não haja alterações ao mesmo.");
    }

    @Test
    void oMesmoAvisoNoDiaSeguinteNaoCriaLinhasNovas() {
        Processo processo = parado(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 3, 1)));
        AvisoInatividadeProcessoEvento evento = aviso(processo, LimiarInatividade.PRIMEIRO_AVISO,
                mais(processo.getAtualizadoEm(), Period.ofMonths(3)), LocalDate.of(2026, 12, 20));
        publicarComCommit(evento);

        publicarComCommit(evento);

        assertThat(linhasDe(processo)).hasSize(2);
        assertThat(mensagens(Level.INFO)).anySatisfy(m -> assertThat(m).contains("já avisado")
                .contains("processoId=" + processo.getId()));
    }

    @Test
    void segundoAvisoDepoisDoPrimeiroCriaNovasLinhas() {
        Processo processo = parado(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 3, 16)));
        Instant aosTresMeses = mais(processo.getAtualizadoEm(), Period.ofMonths(3));
        Instant aosTresMesesEMeio = mais(processo.getAtualizadoEm(), Period.of(0, 3, 15));
        publicarComCommit(aviso(processo, LimiarInatividade.PRIMEIRO_AVISO, aosTresMeses, LocalDate.of(2026, 12, 20)));
        jdbcTemplate.update("UPDATE notificacao SET criado_em = ? WHERE processo_id = ?",
                Timestamp.from(mais(aosTresMeses, Period.ofDays(1))), processo.getId());

        publicarComCommit(aviso(processo, LimiarInatividade.SEGUNDO_AVISO, aosTresMesesEMeio,
                LocalDate.of(2026, 12, 20)));

        assertThat(linhasDe(processo)).hasSize(4);
    }

    @Test
    void avisoDeProcessoAlteradoDepoisDaAvaliacaoNaoCriaNada() {
        Processo processo = parado(EstadoProcesso.EM_SUBMISSAO, ha(Period.of(0, 3, 1)));
        Instant avaliadoAntes = processo.getAtualizadoEm().minusSeconds(86_400);

        publicarComCommit(new AvisoInatividadeProcessoEvento(processo.getId(), LimiarInatividade.PRIMEIRO_AVISO,
                mais(avaliadoAntes, Period.ofMonths(3)), avaliadoAntes, LocalDate.of(2026, 12, 20)));

        assertThat(linhasDe(processo)).isEmpty();
        assertThat(mensagens(Level.INFO)).anySatisfy(m -> assertThat(m).contains("processo alterado"));
    }

    @Test
    void avisoDeProcessoQueJaNaoEstaEmSubmissaoNaoCriaNada() {
        Processo processo = parado(EstadoProcesso.ENVIADO_DGT, ha(Period.of(0, 3, 1)));

        publicarComCommit(aviso(processo, LimiarInatividade.PRIMEIRO_AVISO,
                mais(processo.getAtualizadoEm(), Period.ofMonths(3)), LocalDate.of(2026, 12, 20)));

        assertThat(linhasDe(processo)).isEmpty();
    }

    @Test
    void anulacaoPorInatividadeNotificaOAutorEAEntidadeComONumeroEOTipoDeProcedimento() {
        Processo processo = parado(EstadoProcesso.ANULADO, ha(Period.ofMonths(4)));

        publicarComCommit(new ProcessoAnuladoPorInatividadeEvento(processo.getId()));

        List<Map<String, Object>> linhas = linhasDe(processo);
        assertThat(coluna(linhas, "utilizador_id")).containsExactlyInAnyOrder(autorU.getId(), colegaV.getId());
        assertThat(coluna(linhas, "titulo")).containsOnly("Processo anulado por inatividade");
        assertThat(coluna(linhas, "texto")).containsOnly("O processo " + processo.getNumeroProcesso()
                + " (Discussão pública) foi anulado de forma automática por inatividade.");
    }

    @Test
    void anulacaoDeProcessoQueNaoEstaAnuladoNaoCriaNada() {
        Processo processo = parado(EstadoProcesso.EM_SUBMISSAO, ha(Period.ofMonths(4)));

        publicarComCommit(new ProcessoAnuladoPorInatividadeEvento(processo.getId()));

        assertThat(linhasDe(processo)).isEmpty();
    }

    @Test
    void entidadeSoComOAutorRecebeUmaLinhaSemLote() {
        Entidade soDoAutor = entidadeRepository.saveAndFlush(EntidadeTeste.nova("518799999", "Junta X"));
        vincular(deOutraW, soDoAutor);
        Processo processo = new Processo(dinamica("Alteração"), configOutro, deOutraW, soDoAutor);
        processo.setEstado(EstadoProcesso.ANULADO);
        Processo gravado = processoRepository.saveAndFlush(processo);

        publicarComCommit(new ProcessoAnuladoPorInatividadeEvento(gravado.getId()));

        assertThat(linhasDe(gravado)).singleElement().satisfies(linha -> {
            assertThat(linha.get("utilizador_id")).isEqualTo(deOutraW.getId());
            assertThat(linha.get("lote_id")).isNull();
        });
    }

    @Test
    void cadaUtilizadorDaEntidadeVeASuaLinhaEOUtilizadorDeOutraEntidadeNaoVe() {
        Processo processo = parado(EstadoProcesso.ANULADO, ha(Period.ofMonths(4)));
        publicarComCommit(new ProcessoAnuladoPorInatividadeEvento(processo.getId()));
        List<UUID> criadas = linhasDe(processo).stream().map(linha -> (UUID) linha.get("id")).toList();

        assertThat(visiveis(autorU, entidadeA)).filteredOn(criadas::contains).hasSize(1);
        assertThat(visiveis(colegaV, entidadeA)).filteredOn(criadas::contains).hasSize(1);
        assertThat(visiveis(deOutraW, entidadeB)).doesNotContainAnyElementsOf(criadas);
    }

    @Test
    void textosELogsNaoTemNomesNemEmails() {
        Dinamica dinamica = dinamica("Revisão do PDM");
        Processo referencia = processo(dinamica, configDecisaoInicio, EstadoProcesso.PUBLICADO, LocalDate.of(2025, 3, 1));
        Processo parado = parado(EstadoProcesso.ANULADO, ha(Period.ofMonths(4)));

        publicarComCommit(new AlertaCaducidadeDinamicaEvento(dinamica.getId(), LimiarCaducidade.DATA,
                LocalDate.of(2026, 11, 2)));
        publicarComCommit(new ProcessoAnuladoPorInatividadeEvento(parado.getId()));

        List<String> textos = new java.util.ArrayList<>();
        textos.addAll(coluna(linhasDe(referencia), "texto").stream().map(String::valueOf).toList());
        textos.addAll(coluna(linhasDe(parado), "texto").stream().map(String::valueOf).toList());
        textos.addAll(logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertThat(textos).isNotEmpty().allSatisfy(texto -> assertThat(texto)
                .doesNotContain(autorU.getNome(), colegaV.getNome(), autorU.getEmail(), colegaV.getEmail(), "@"));
    }

    private List<UUID> visiveis(Utilizador utilizador, Entidade entidade) {
        ContextoUtilizador contexto = new ContextoUtilizador(utilizador.getId(), "INCM", null, entidade.getNipc(),
                "ENTIDADE", null, null, null, null, TipoEntidade.CM, entidade.getId());
        return notificacaoRepository.findAll(visibilidade.visiveisPara(contexto)).stream()
                .map(Notificacao::getId).toList();
    }
}
