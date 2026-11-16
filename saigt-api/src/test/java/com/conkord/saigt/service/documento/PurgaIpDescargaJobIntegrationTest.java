package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.ConsumidorDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescargaRepository;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PurgaIpDescargaJobIntegrationTest {

    @Autowired
    private PurgaIpDescargaJob job;

    @Autowired
    private DocumentoDescargaRepository repository;

    private DocumentoDescarga guardar(Instant iniciadaEm, String ip) {
        DocumentoDescarga descarga = new DocumentoDescarga(ConsumidorDescarga.UTILIZADOR, null, null,
                UUID.randomUUID(), ip, iniciadaEm, ResultadoDescarga.CONCLUIDA);
        descarga.setBytesEnviados(1234);
        return repository.saveAndFlush(descarga);
    }

    @Test
    void deveLimparSoOIpDasLinhasMaisAntigasQueOPrazoEManterORestante() {
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        DocumentoDescarga antiga = guardar(agora.atZone(ZoneOffset.UTC).minusMonths(13).toInstant(), "203.0.113.7");
        DocumentoDescarga recente = guardar(agora.atZone(ZoneOffset.UTC).minusMonths(11).toInstant(), "203.0.113.8");

        job.executar();

        DocumentoDescarga antigaDepois = repository.findById(antiga.getId()).orElseThrow();
        assertThat(antigaDepois.getIp()).isNull();
        assertThat(antigaDepois.getBytesEnviados()).isEqualTo(1234);
        assertThat(antigaDepois.getResultado()).isEqualTo(ResultadoDescarga.CONCLUIDA);
        assertThat(antigaDepois.getUtilizadorId()).isEqualTo(antiga.getUtilizadorId());
        assertThat(antigaDepois.getIniciadaEm()).isEqualTo(antiga.getIniciadaEm());
        assertThat(repository.findById(recente.getId()).orElseThrow().getIp()).isEqualTo("203.0.113.8");
    }
}
