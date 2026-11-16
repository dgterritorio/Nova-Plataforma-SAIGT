package com.conkord.saigt.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(DescargaProperties.class)
public class DescargaConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
