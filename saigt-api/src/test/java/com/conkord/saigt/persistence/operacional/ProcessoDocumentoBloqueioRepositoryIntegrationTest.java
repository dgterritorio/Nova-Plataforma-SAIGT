package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** O `case when` e o `CURRENT_INSTANT` dos updates em lote, contra o PostgreSQL e o esquema do Liquibase. */
@SpringBootTest
@ActiveProfiles("test")
class ProcessoDocumentoBloqueioRepositoryIntegrationTest extends PostgresTestcontainersBase {

    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private ProcessoRepository processoRepository;

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

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Processo criarProcesso() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return new TransactionTemplate(transactionManager).execute(estado -> {
            TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.save(
                    new TipoInstrumento("BDC-" + sufixo, "Plano Diretor Municipal", "PLANO"));
            TipoDinamica tipoDinamica = tipoDinamicaRepository.save(new TipoDinamica("BDC-" + sufixo, "Alteração"));
            InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.save(
                    new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
            TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.save(
                    new TipoProcedimento("BDC-" + sufixo, "Procedimento"));
            InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.save(
                    new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
            Entidade entidade = entidadeRepository.save(EntidadeTeste.nova(
                    "5" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)), "Câmara " + sufixo));
            Utilizador autor = utilizadorRepository.save(new Utilizador("Autor " + sufixo, sufixo + "@cm-teste.pt", "INCM"));
            Instrumento instrumento = instrumentoRepository.save(
                    new Instrumento(tipoInstrumento, "1", "2026/BDC/" + sufixo, autor, autor));
            Dinamica dinamica = dinamicaRepository.save(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
            return processoRepository.save(new Processo(dinamica, procedimentoConfig, autor, entidade));
        });
    }

    private ProcessoDocumento documento(Processo dono, EstadoValidacao estado, boolean bloqueado) {
        ProcessoDocumento documento = new ProcessoDocumento(dono, null, CategoriaDocumento.DEPOSITO, "pdf", "f.pdf",
                "privado/p/" + UUID.randomUUID(), "a".repeat(64), "v1", 10L, dono.getCriadoPor());
        documento.setEstadoValidacao(estado);
        documento.setBloqueadoEdicao(bloqueado);
        return processoDocumentoRepository.saveAndFlush(documento);
    }

    private boolean bloqueado(ProcessoDocumento documento) {
        return processoDocumentoRepository.findById(documento.getId()).orElseThrow().isBloqueadoEdicao();
    }

    private void emTransacao(Runnable acao) {
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> acao.run());
    }

    @Test
    void deveBloquearSoOsValidadosNoPostgres() {
        Processo processo = criarProcesso();
        Processo outro = criarProcesso();
        ProcessoDocumento validado = documento(processo, EstadoValidacao.VALIDADO, false);
        ProcessoDocumento rejeitado = documento(processo, EstadoValidacao.REJEITADO, true);
        ProcessoDocumento porValidar = documento(processo, EstadoValidacao.POR_VALIDAR, false);
        ProcessoDocumento doOutro = documento(outro, EstadoValidacao.VALIDADO, false);

        emTransacao(() -> processoDocumentoRepository.bloquearSoValidados(processo.getId(), EstadoValidacao.VALIDADO));

        assertThat(bloqueado(validado)).isTrue();
        assertThat(bloqueado(rejeitado)).isFalse();
        assertThat(bloqueado(porValidar)).isFalse();
        assertThat(bloqueado(doOutro)).isFalse();
        assertThat(processoDocumentoRepository.contarBloqueadosAtuais(processo.getId())).isEqualTo(1L);
    }

    @Test
    void deveBloquearTodosNoPostgres() {
        Processo processo = criarProcesso();
        Processo outro = criarProcesso();
        ProcessoDocumento validado = documento(processo, EstadoValidacao.VALIDADO, false);
        ProcessoDocumento rejeitado = documento(processo, EstadoValidacao.REJEITADO, false);
        ProcessoDocumento doOutro = documento(outro, EstadoValidacao.REJEITADO, false);

        emTransacao(() -> processoDocumentoRepository.bloquearTodos(processo.getId()));

        assertThat(bloqueado(validado)).isTrue();
        assertThat(bloqueado(rejeitado)).isTrue();
        assertThat(bloqueado(doOutro)).isFalse();
    }
}
