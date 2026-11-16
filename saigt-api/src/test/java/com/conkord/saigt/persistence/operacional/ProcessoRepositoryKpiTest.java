package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.operacional.ProcessoRepository.ContagemPorTipoInstrumentoProjecao;
import com.conkord.saigt.persistence.operacional.ProcessoRepository.PublicadosMensalProjecao;
import com.conkord.saigt.service.relatorio.ProcessosKpiFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessoRepositoryKpiTest extends PostgresTestcontainersBase {

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ApplicationContext contexto;

    private ProcessosKpiFixture fixture;

    @BeforeEach
    void esvaziar() {
        fixture = new ProcessosKpiFixture(contexto);
        fixture.esvaziar();
    }

    @Test
    void contarPublicadosPorMesContaSoOsPublicadosComDataDePublicacaoPorMes() {
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 5));
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 31));
        fixture.criar("PU", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 4, 1));
        fixture.criar("PU", EstadoProcesso.PUBLICADO, null);
        fixture.criar("PDM", EstadoProcesso.EM_VALIDACAO_INCM, LocalDate.of(2026, 3, 15));
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);

        assertThat(processoRepository.contarPublicadosPorMes())
                .extracting(PublicadosMensalProjecao::getMes, PublicadosMensalProjecao::getTotal)
                .containsExactly(tuple(LocalDate.of(2026, 3, 1), 2L), tuple(LocalDate.of(2026, 4, 1), 1L));
    }

    @Test
    void contarPorTipoInstrumentoContaTodosOsProcessosPorCodigoIncluindoDinamicasSemIgt() {
        fixture.criar("PDM", EstadoProcesso.EM_SUBMISSAO);
        fixture.criar("PDM", EstadoProcesso.PUBLICADO, LocalDate.of(2026, 3, 5));
        fixture.criar("PU", EstadoProcesso.ANULADO);
        fixture.criarSemIgt("PU", EstadoProcesso.EM_SUBMISSAO);

        assertThat(processoRepository.contarPorTipoInstrumento())
                .extracting(ContagemPorTipoInstrumentoProjecao::getCodigo, ContagemPorTipoInstrumentoProjecao::getTotal)
                .containsExactly(tuple("PDM", 2L), tuple("PU", 2L));
    }

    @Test
    void numaBaseSemProcessosAsDuasConsultasDevolvemListasVazias() {
        assertThat(processoRepository.contarPublicadosPorMes()).isEmpty();
        assertThat(processoRepository.contarPorTipoInstrumento()).isEmpty();
    }
}
