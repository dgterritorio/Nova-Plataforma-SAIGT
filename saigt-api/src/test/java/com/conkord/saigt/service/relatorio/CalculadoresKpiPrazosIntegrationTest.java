package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.documento.IntencaoArmazenamentoService;
import com.conkord.saigt.service.instrumento.InatividadeProcessoProperties;
import com.conkord.saigt.service.instrumento.InatividadeProcessoService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Os cinco KPIs de prazos, tempos, ficheiros e recusas em PostgreSQL, cada um com uma só consulta. Os processos dos
 * outros testes saem com {@link ProcessosKpiFixture#esvaziar()} e voltam no rollback.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "app.processos.inatividade.primeiro-aviso=P3M", "app.processos.inatividade.segundo-aviso=P3M15D",
        "app.processos.inatividade.anulacao=P4M"})
@Import({ACaducarCalculador.class, AInativarCalculador.class, TempoMedioInstrucaoCalculador.class,
        PadroesTamanhoFicheirosCalculador.class, TaxaRejeicaoCalculador.class, InatividadeProcessoService.class})
class CalculadoresKpiPrazosIntegrationTest extends PostgresTestcontainersBase {

    private static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");
    private static final Clock RELOGIO = Clock.systemUTC();

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock relogio() {
            return RELOGIO;
        }
    }

    @MockBean
    private IntencaoArmazenamentoService intencaoArmazenamentoService;

    @MockBean
    private AuditoriaService auditoriaService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ACaducarCalculador aCaducar;

    @Autowired
    private AInativarCalculador aInativar;

    @Autowired
    private TempoMedioInstrucaoCalculador tempoMedio;

    @Autowired
    private PadroesTamanhoFicheirosCalculador padroesTamanho;

    @Autowired
    private TaxaRejeicaoCalculador taxaRejeicao;

    private ProcessosKpiFixture fixture;

    @BeforeEach
    void esvaziar() {
        fixture = new ProcessosKpiFixture(contexto);
        fixture.esvaziar();
    }

    private record Medicao(ValorKpi valor, long consultas) {
    }

    private Medicao medir(Supplier<ValorKpi> calculo) {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        ValorKpi valor = calculo.get();
        return new Medicao(valor, statistics.getPrepareStatementCount());
    }

    private static Instant ha(Period periodo) {
        return RELOGIO.instant().atZone(LISBOA).minus(periodo).toInstant();
    }

    private static Instant as10h(String data) {
        return LocalDate.parse(data).atTime(10, 0).atZone(LISBOA).toInstant();
    }

    private Processo publicado(String submetidoA, String publicadoA) {
        Processo processo = fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.parse(publicadoA));
        fixture.registarSubmissao(processo, as10h(submetidoA));
        return processo;
    }

    private Processo submetido(String tipo, EstadoProcesso estado) {
        Processo processo = fixture.criar(tipo, estado);
        fixture.registarSubmissao(processo, as10h("2026-03-01"));
        return processo;
    }

    // A_CADUCAR

    @Test
    void aCaducarContaOsProcessosDaDinamicaEmCursoComAvisoENaoAsDinamicas() {
        Processo comAviso = fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.alterarDinamica(comAviso, EstadoDinamica.EM_CURSO, "[\"UM_MES\"]");
        fixture.alterarDinamica(fixture.criar("PU", EstadoProcesso.EM_SUBMISSAO), EstadoDinamica.EM_CURSO, "[]");
        fixture.alterarDinamica(fixture.criar("PP", EstadoProcesso.EM_SUBMISSAO), EstadoDinamica.CADUCADA,
                "[\"UM_MES\", \"QUINZE_DIAS\", \"DATA\"]");

        Medicao medicao = medir(aCaducar::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isEqualTo(2L);
        assertThat(medicao.valor().serie()).isNull();
    }

    @Test
    void aCaducarNaoContaPublicadosNemAnuladosNemDinamicasTerminadasOuSemAvisos() {
        Processo emCurso = fixture.criar("PDM", EstadoProcesso.REJEITADO_DGT);
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 1));
        fixture.criar("PDM", EstadoProcesso.ANULADO);
        // ANULADO_INCM não é terminal: corrige-se na mesma tentativa, por isso conta.
        fixture.criar("PDM", EstadoProcesso.ANULADO_INCM);
        fixture.alterarDinamica(emCurso, EstadoDinamica.EM_CURSO, "[\"UM_MES\", \"QUINZE_DIAS\"]");
        fixture.alterarDinamica(fixture.criar("PU", EstadoProcesso.EM_SUBMISSAO), EstadoDinamica.TERMINADA,
                "[\"UM_MES\"]");
        fixture.alterarDinamica(fixture.criar("PP", EstadoProcesso.EM_SUBMISSAO), EstadoDinamica.EM_CURSO, null);

        assertThat(medir(aCaducar::calcular).valor().valor()).isEqualTo(2L);
    }

    // A_INATIVAR

    private void parados() {
        fixture.alterarAtualizadoEm(fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO), ha(Period.ofMonths(4)));
        fixture.alterarAtualizadoEm(fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO), ha(Period.ofMonths(4)));
        fixture.alterarAtualizadoEm(fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO), ha(Period.ofMonths(2)));
        fixture.alterarAtualizadoEm(fixture.criar("PDM", EstadoProcesso.ENVIADO_DGT), ha(Period.ofMonths(5)));
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void aInativarContaOsEmSubmissaoParadosDesdeOPrimeiroAvisoComUmaConsulta() {
        parados();

        Medicao medicao = medir(aInativar::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isEqualTo(2L);
        assertThat(medicao.valor().serie()).isNull();
    }

    @Test
    void aInativarSegueOPrazoDoPrimeiroAvisoConfigurado() {
        parados();
        InatividadeProcessoService umMes = new InatividadeProcessoService(processoRepository,
                mock(ProcessoDocumentoRepository.class),
                mock(IntencaoArmazenamentoService.class), mock(AuditoriaService.class),
                mock(ApplicationEventPublisher.class),
                new InatividadeProcessoProperties(Period.ofMonths(1), Period.ofMonths(3), Period.ofMonths(4)));

        Medicao medicao = medir(new AInativarCalculador(processoRepository, umMes, RELOGIO)::calcular);

        assertThat(medicao.valor().valor()).isEqualTo(3L);
    }

    // TEMPO_MEDIO_INSTRUCAO

    @Test
    void tempoMedioContaDaPrimeiraSubmissaoEnaoDaUltima() {
        publicado("2026-03-01", "2026-03-11");
        Processo resubmetido = publicado("2026-03-01", "2026-03-21");
        fixture.rejeitar(resubmetido, OrigemRejeicao.DGT);
        fixture.registarSubmissao(resubmetido, as10h("2026-03-20"));

        Medicao medicao = medir(tempoMedio::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isEqualTo(15L);
        assertThat(medicao.valor().serie()).isNull();
    }

    @Test
    void tempoMedioArredondaOMeioParaCima() {
        publicado("2026-03-01", "2026-03-11");
        publicado("2026-03-01", "2026-03-12");

        assertThat(medir(tempoMedio::calcular).valor().valor()).isEqualTo(11L);
    }

    @Test
    void tempoMedioDeixaDeForaSemDataDePublicacaoSemSubmissaoENaoPublicados() {
        Processo soDeposito = fixture.criar("PDM", EstadoProcesso.PUBLICADO);
        fixture.registarSubmissao(soDeposito, as10h("2026-03-01"));
        fixture.criar("PU", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 20));
        Processo naIncm = fixture.criar("PP", EstadoProcesso.EM_VALIDACAO_INCM, LocalDate.of(2026, 3, 20));
        fixture.registarSubmissao(naIncm, as10h("2026-03-01"));

        Medicao medicao = medir(tempoMedio::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isNull();
    }

    // PADROES_TAMANHO_FICHEIROS

    @Test
    void padroesTamanhoDaOTotalEAMediaPorItemDocumentalDosSubmetidos() {
        Processo primeiro = submetido("PDM", EstadoProcesso.ENVIADO_DGT);
        Processo segundo = submetido("PDM", EstadoProcesso.PUBLICADO);
        fixture.carregarDocumento(primeiro, "Texto do ato", 100);
        fixture.carregarDocumento(primeiro, "Relatório", 600);
        fixture.carregarDocumento(segundo, "Texto do ato", 300);

        Medicao medicao = medir(padroesTamanho::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isEqualTo(1000L);
        assertThat(medicao.valor().serie()).containsExactlyInAnyOrder(
                new PontoSerieResponse("Texto do ato", 200), new PontoSerieResponse("Relatório", 600));
    }

    @Test
    void padroesTamanhoNaoContaProcessosNuncaSubmetidosNemDocumentosExtraEArredondaAMedia() {
        Processo submetido = submetido("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.carregarDocumento(submetido, "Texto do ato", 100);
        fixture.carregarDocumento(submetido, "Texto do ato", 301);
        fixture.carregarDocumentoExtra(submetido, 7000);
        Processo nuncaSubmetido = fixture.criar("PU", EstadoProcesso.EM_SUBMISSAO);
        fixture.carregarDocumento(nuncaSubmetido, "Texto do ato", 5000);
        fixture.carregarDocumento(nuncaSubmetido, "Planta", 9000);

        ValorKpi valor = medir(padroesTamanho::calcular).valor();

        assertThat(valor.valor()).isEqualTo(401L);
        assertThat(valor.serie()).containsExactly(new PontoSerieResponse("Texto do ato", 201));
    }

    // TAXA_REJEICAO

    @Test
    void taxaRejeicaoDivideAsRecusasDaDgtPelasSubmissoesSemAsDaIncm() {
        Processo processo = submetido("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.registarSubmissao(processo, as10h("2026-03-10"));
        submetido("PU", EstadoProcesso.ENVIADO_DGT);
        submetido("PP", EstadoProcesso.EM_VALIDACAO_INCM);
        fixture.rejeitar(processo, OrigemRejeicao.DGT);
        fixture.rejeitar(processo, OrigemRejeicao.INCM);

        Medicao medicao = medir(taxaRejeicao::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isEqualTo(25L);
        assertThat(medicao.valor().serie()).isNull();
    }

    @Test
    void taxaRejeicaoArredondaAsUnidadesComOMeioParaCima() {
        Processo processo = submetido("PDM", EstadoProcesso.EM_SUBMISSAO);
        submetido("PU", EstadoProcesso.ENVIADO_DGT);
        submetido("PP", EstadoProcesso.ENVIADO_DGT);
        fixture.rejeitar(processo, OrigemRejeicao.DGT);
        assertThat(taxaRejeicao.calcular().valor()).isEqualTo(33L);

        fixture.rejeitar(processo, OrigemRejeicao.DGT);
        assertThat(taxaRejeicao.calcular().valor()).isEqualTo(67L);
    }

    @Test
    void taxaRejeicaoSemSubmissoesENula() {
        fixture.rejeitar(fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO), OrigemRejeicao.INCM);

        assertThat(medir(taxaRejeicao::calcular).valor().valor()).isNull();
    }

    @Test
    void numaBaseVaziaDaOsValoresDosCasosLimite() {
        assertThat(aCaducar.calcular().valor()).isZero();
        assertThat(aInativar.calcular().valor()).isZero();
        assertThat(tempoMedio.calcular().valor()).isNull();
        assertThat(padroesTamanho.calcular().valor()).isZero();
        assertThat(padroesTamanho.calcular().serie()).isEmpty();
        assertThat(taxaRejeicao.calcular().valor()).isNull();
    }
}
