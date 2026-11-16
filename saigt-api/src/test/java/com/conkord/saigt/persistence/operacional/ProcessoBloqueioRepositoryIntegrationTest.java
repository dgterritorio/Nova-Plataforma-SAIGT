package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.db.PostgresTestcontainersBase;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProcessoBloqueioRepositoryIntegrationTest extends PostgresTestcontainersBase {

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

    private UUID criarProcesso() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return new TransactionTemplate(transactionManager).execute(estado -> {
            TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.save(
                    new TipoInstrumento("BLQ-" + sufixo, "Plano Diretor Municipal", "PLANO"));
            TipoDinamica tipoDinamica = tipoDinamicaRepository.save(new TipoDinamica("BLQ-" + sufixo, "Alteração"));
            InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.save(
                    new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
            TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.save(
                    new TipoProcedimento("BLQ-" + sufixo, "Procedimento"));
            InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.save(
                    new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
            Entidade entidade = entidadeRepository.save(EntidadeTeste.nova(
                    "5" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)), "Câmara " + sufixo));
            Utilizador autor = utilizadorRepository.save(new Utilizador("Autor " + sufixo, sufixo + "@cm-teste.pt", "INCM"));
            Instrumento instrumento = instrumentoRepository.save(
                    new Instrumento(tipoInstrumento, "1", "2026/BLQ/" + sufixo, autor, autor));
            Dinamica dinamica = dinamicaRepository.save(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
            return processoRepository.save(new Processo(dinamica, procedimentoConfig, autor, entidade)).getId();
        });
    }

    @Test
    void aSegundaTransacaoDeveEsperarPeloCommitDaPrimeira() throws Exception {
        UUID processoId = criarProcesso();
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        CountDownLatch primeiraComOBloqueio = new CountDownLatch(1);
        AtomicBoolean primeiraTerminou = new AtomicBoolean(false);

        CompletableFuture<Void> primeira = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(estado -> {
            assertThat(processoRepository.findByIdComBloqueio(processoId)).isPresent();
            primeiraComOBloqueio.countDown();
            dormir(500);
            primeiraTerminou.set(true);
        }));
        assertThat(primeiraComOBloqueio.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Boolean> segunda = CompletableFuture.supplyAsync(() -> transacao.execute(estado -> {
            processoRepository.findByIdComBloqueio(processoId).orElseThrow();
            return primeiraTerminou.get();
        }));

        primeira.get(10, TimeUnit.SECONDS);
        assertThat(segunda.get(10, TimeUnit.SECONDS)).as("a segunda só obtém o Processo depois do commit da primeira")
                .isTrue();
    }

    @Test
    void deveDevolverVazioQuandoOProcessoNaoExiste() {
        Optional<Processo> encontrado = new TransactionTemplate(transactionManager)
                .execute(estado -> processoRepository.findByIdComBloqueio(UUID.randomUUID()));

        assertThat(encontrado).isEmpty();
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
