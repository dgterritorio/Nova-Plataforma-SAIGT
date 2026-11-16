package com.conkord.saigt.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;

@Configuration
@EnableAsync
@EnableConfigurationProperties(NotificacoesEmailProperties.class)
public class NotificacoesAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificacoesAsyncConfig.class);

    public static final String EXECUTOR_EMAIL = "notificacoesEmailExecutor";

    @Bean(name = EXECUTOR_EMAIL)
    public ThreadPoolTaskExecutor notificacoesEmailExecutor(NotificacoesEmailProperties propriedades) {
        NotificacoesEmailProperties.Executor config = propriedades.executor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notif-email-");
        executor.setCorePoolSize(config.tamanhoBase());
        executor.setMaxPoolSize(Math.max(config.tamanhoBase(), config.tamanhoMaximo()));
        executor.setQueueCapacity(config.fila());
        executor.setRejectedExecutionHandler(recusaComAviso(config.fila()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * Fila cheia: a cópia perde-se com um WARN. Lançar a rejeição chegaria ao commit do pedido, que já confirmou a
     * notificação, e trocava a resposta por um erro.
     */
    static RejectedExecutionHandler recusaComAviso(int fila) {
        return (tarefa, executor) -> log.warn(
                "Cópia por email recusada: fila do executor de email cheia | fila={} ativas={}", fila,
                executor.getActiveCount());
    }
}
