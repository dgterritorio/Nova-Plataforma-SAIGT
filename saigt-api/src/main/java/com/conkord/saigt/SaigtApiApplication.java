package com.conkord.saigt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling}: os jobs periódicos estão em {@code JobsAgendados}. */
@SpringBootApplication
@EnableScheduling
public class SaigtApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaigtApiApplication.class, args);
    }
}
