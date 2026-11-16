package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import com.conkord.saigt.persistence.operacional.DocumentoDescargaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Limpa o IP das descargas mais antigas do que o prazo de retenção; o resto da linha de auditoria mantém-se. */
@Service
public class PurgaIpDescargaJob {

    private static final Logger log = LoggerFactory.getLogger(PurgaIpDescargaJob.class);

    private final DocumentoDescargaRepository repository;
    private final DescargaProperties propriedades;
    private final Clock relogio;

    public PurgaIpDescargaJob(DocumentoDescargaRepository repository, DescargaProperties propriedades, Clock relogio) {
        this.repository = repository;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Transactional
    public int executar() {
        Instant limite = relogio.instant().atZone(ZoneOffset.UTC).minus(propriedades.retencaoIp()).toInstant();
        int limpas = repository.limparIpAnteriorA(limite);
        log.info("Purga do IP das descargas: {} linha(s) limpa(s).", limpas);
        return limpas;
    }
}
