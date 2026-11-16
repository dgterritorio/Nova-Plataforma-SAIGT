package com.conkord.saigt.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NotificacoesAsyncConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(NotificacoesAsyncConfig.class);

    @Test
    void porOmissaoFicaDesligadoEArrancaSemRemetente() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            NotificacoesEmailProperties propriedades = ctx.getBean(NotificacoesEmailProperties.class);
            assertThat(propriedades.ativo()).isFalse();
            assertThat(propriedades.executor().tamanhoBase()).isEqualTo(1);
            assertThat(propriedades.executor().tamanhoMaximo()).isEqualTo(2);
            assertThat(propriedades.executor().fila()).isEqualTo(500);
        });
    }

    @Test
    void ativoSemRemetenteFalhaNoArranqueComMensagemQueNomeiaAPropriedade() {
        contexto.withPropertyValues("app.notificacoes.email.ativo=true", "app.notificacoes.email.remetente=")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(NestedExceptionUtils.getMostSpecificCause(ctx.getStartupFailure()).getMessage())
                            .contains("app.notificacoes.email.remetente");
                });
    }

    @Test
    void remetenteQueNaoEEmailFalhaNoArranque() {
        contexto.withPropertyValues("app.notificacoes.email.ativo=true", "app.notificacoes.email.remetente=nao-e-email")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void existeOExecutorComPrefixoNotifEmailEOsTamanhosDasPropriedades() {
        contexto.withPropertyValues("app.notificacoes.email.ativo=true",
                        "app.notificacoes.email.remetente=saigt@saigt-teste.invalid",
                        "app.notificacoes.email.executor.tamanho-base=2",
                        "app.notificacoes.email.executor.tamanho-maximo=3",
                        "app.notificacoes.email.executor.fila=7")
                .run(ctx -> {
                    ThreadPoolTaskExecutor executor = ctx.getBean(NotificacoesAsyncConfig.EXECUTOR_EMAIL,
                            ThreadPoolTaskExecutor.class);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("notif-email-");
                    assertThat(executor.getCorePoolSize()).isEqualTo(2);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(3);
                    assertThat(executor.getQueueCapacity()).isEqualTo(7);
                    String thread = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), executor)
                            .get(5, TimeUnit.SECONDS);
                    assertThat(thread).startsWith("notif-email-");
                });
    }

    @Test
    void comAFilaCheiaRecusaSemLancarParaQuemSubmete() {
        contexto.withPropertyValues("app.notificacoes.email.executor.tamanho-base=1",
                        "app.notificacoes.email.executor.tamanho-maximo=1",
                        "app.notificacoes.email.executor.fila=1")
                .run(ctx -> {
                    ThreadPoolTaskExecutor executor = ctx.getBean(NotificacoesAsyncConfig.EXECUTOR_EMAIL,
                            ThreadPoolTaskExecutor.class);
                    CountDownLatch libertar = new CountDownLatch(1);
                    Runnable preso = () -> {
                        try {
                            libertar.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    };
                    executor.execute(preso);
                    executor.execute(preso);
                    executor.execute(preso);
                    libertar.countDown();
                });
    }
}
