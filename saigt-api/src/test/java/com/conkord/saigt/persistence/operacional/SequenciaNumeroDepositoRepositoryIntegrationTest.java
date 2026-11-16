package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.core.SerieNumeroDeposito;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SequenciaNumeroDepositoRepositoryIntegrationTest extends PostgresTestcontainersBase {

    @Autowired
    private SequenciaNumeroDepositoRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private int proximo(SerieNumeroDeposito serie, int ano) {
        Integer sequencial = new TransactionTemplate(transactionManager)
                .execute(estado -> repository.proximoSequencial(serie, ano));
        return sequencial == null ? -1 : sequencial;
    }

    @Test
    void deveCriarALinhaDoParSerieAnoEDevolverUmEDepoisDois() {
        assertThat(proximo(SerieNumeroDeposito.GERAL, 2101)).isEqualTo(1);
        assertThat(proximo(SerieNumeroDeposito.GERAL, 2101)).isEqualTo(2);
    }

    @Test
    void deveContarCadaSerieECadaAnoAParte() {
        assertThat(proximo(SerieNumeroDeposito.GERAL, 2102)).isEqualTo(1);
        assertThat(proximo(SerieNumeroDeposito.REN, 2102)).isEqualTo(1);
        assertThat(proximo(SerieNumeroDeposito.GERAL, 2103)).isEqualTo(1);
        assertThat(proximo(SerieNumeroDeposito.GERAL, 2102)).isEqualTo(2);
    }

    @Test
    void duasPublicacoesSimultaneasDevemReceberSequenciaisDiferentes() throws Exception {
        CountDownLatch primeiraComOBloqueio = new CountDownLatch(1);
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        CompletableFuture<Integer> primeira = CompletableFuture.supplyAsync(() -> transacao.execute(estado -> {
            int sequencial = repository.proximoSequencial(SerieNumeroDeposito.GERAL, 2104);
            primeiraComOBloqueio.countDown();
            dormir(500);
            return sequencial;
        }));
        assertThat(primeiraComOBloqueio.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Integer> segunda = CompletableFuture.supplyAsync(
                () -> transacao.execute(estado -> repository.proximoSequencial(SerieNumeroDeposito.GERAL, 2104)));

        assertThat(List.of(primeira.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void duasPublicacoesSimultaneasNoPrimeiroDoAnoDevemReceberSequenciaisDiferentes() throws Exception {
        CountDownLatch partida = new CountDownLatch(1);
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        List<CompletableFuture<Integer>> publicacoes = List.of(1, 2, 3, 4).stream()
                .map(i -> CompletableFuture.supplyAsync(() -> {
                    aguardar(partida);
                    return transacao.execute(estado -> repository.proximoSequencial(SerieNumeroDeposito.REN, 2105));
                }))
                .toList();

        partida.countDown();

        assertThat(publicacoes.stream().map(CompletableFuture::join).toList()).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
