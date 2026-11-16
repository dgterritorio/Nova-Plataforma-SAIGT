package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.dto.relatorio.PontoSerieResponse;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({ProcessosPorEstadoCalculador.class, GraficoEstadoCalculador.class, GraficoMesCalculador.class,
        TotalPorTipologiaCalculador.class})
class CalculadoresKpiIntegrationTest extends PostgresTestcontainersBase {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private ProcessosPorEstadoCalculador processosPorEstado;

    @Autowired
    private GraficoEstadoCalculador graficoEstado;

    @Autowired
    private GraficoMesCalculador graficoMes;

    @Autowired
    private TotalPorTipologiaCalculador totalPorTipologia;

    private ProcessosKpiFixture fixture;

    @BeforeEach
    void esvaziar() {
        fixture = new ProcessosKpiFixture(contexto);
        fixture.esvaziar();
    }

    private void criarBaseComPdmEPu() {
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criarSemIgt("PU", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 10));
        fixture.criar("PU", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 4, 20));
        fixture.criar("PU", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 4, 2));
        fixture.criar("PU", EstadoProcesso.EM_VALIDACAO_INCM, LocalDate.of(2026, 3, 1));
    }

    /** Valor calculado e número de instruções SQL preparadas durante o cálculo. */
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

    private static List<PontoSerieResponse> serieDeEstados(long emSubmissao, long emValidacaoIncm, long publicado) {
        return Arrays.stream(EstadoProcesso.values())
                .map(estado -> new PontoSerieResponse(estado.name(), switch (estado) {
                    case EM_SUBMISSAO -> emSubmissao;
                    case EM_VALIDACAO_INCM -> emValidacaoIncm;
                    case PUBLICADO -> publicado;
                    default -> 0L;
                }))
                .toList();
    }

    @Test
    void processosPorEstadoFazUmaConsultaEDaOTotalEUmPontoPorEstado() {
        criarBaseComPdmEPu();

        Medicao medicao = medir(processosPorEstado::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isEqualTo(7L);
        assertThat(medicao.valor().serie()).containsExactlyElementsOf(serieDeEstados(3, 1, 3));
    }

    @Test
    void graficoEstadoFazUmaConsultaComValorNuloEAMesmaSerie() {
        criarBaseComPdmEPu();

        Medicao medicao = medir(graficoEstado::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isNull();
        assertThat(medicao.valor().serie()).containsExactlyElementsOf(serieDeEstados(3, 1, 3));
    }

    @Test
    void graficoMesFazUmaConsultaEContaSoOsPublicadosPorMes() {
        criarBaseComPdmEPu();

        Medicao medicao = medir(graficoMes::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().valor()).isNull();
        assertThat(medicao.valor().serie())
                .containsExactly(new PontoSerieResponse("2026-03", 1), new PontoSerieResponse("2026-04", 2));
    }

    @Test
    void totalPorTipologiaFazUmaConsultaEOValorEASomaDaSerie() {
        criarBaseComPdmEPu();

        Medicao medicao = medir(totalPorTipologia::calcular);

        assertThat(medicao.consultas()).isEqualTo(1);
        assertThat(medicao.valor().serie())
                .containsExactly(new PontoSerieResponse("PDM", 3), new PontoSerieResponse("PU", 4));
        assertThat(medicao.valor().valor()).isEqualTo(7L);
    }

    @Test
    void numaBaseVaziaDaZerosESeriesVazias() {
        assertThat(processosPorEstado.calcular().valor()).isZero();
        assertThat(processosPorEstado.calcular().serie()).hasSize(EstadoProcesso.values().length)
                .allMatch(ponto -> ponto.valor() == 0);
        assertThat(graficoEstado.calcular().serie()).allMatch(ponto -> ponto.valor() == 0);
        assertThat(graficoMes.calcular().serie()).isEmpty();
        assertThat(totalPorTipologia.calcular().valor()).isZero();
        assertThat(totalPorTipologia.calcular().serie()).isEmpty();
    }
}
