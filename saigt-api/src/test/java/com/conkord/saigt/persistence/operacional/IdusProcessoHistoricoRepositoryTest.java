package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdusProcessoHistoricoRepositoryTest {

    private static final Instant REGISTO = Instant.parse("2026-11-03T10:00:00Z");

    @Autowired
    private IdusProcessoHistoricoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deveGravarELerTodasAsColunas() {
        UUID processoId = UUID.randomUUID();
        IdusProcessoHistorico gravado = repository.saveAndFlush(IdusProcessoHistorico.builder()
                .processoId(processoId)
                .segmento(SegmentoIdus.SUBMISSAO)
                .idusAnterior("SAIGT-1001-T001-1-031-00")
                .idusNovo("SAIGT-1001-T001-1-031-01")
                .evento(EventoIdus.SUBMISSAO)
                .registadoEm(REGISTO)
                .correlationId("corr-1")
                .build());
        entityManager.clear();

        IdusProcessoHistorico lido = repository.findById(gravado.getId()).orElseThrow();

        assertThat(lido.getProcessoId()).isEqualTo(processoId);
        assertThat(lido.getSegmento()).isEqualTo(SegmentoIdus.SUBMISSAO);
        assertThat(lido.getIdusAnterior()).isEqualTo("SAIGT-1001-T001-1-031-00");
        assertThat(lido.getIdusNovo()).isEqualTo("SAIGT-1001-T001-1-031-01");
        assertThat(lido.getEvento()).isEqualTo(EventoIdus.SUBMISSAO);
        assertThat(lido.getRegistadoEm()).isEqualTo(REGISTO);
        assertThat(lido.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void deveAceitarAPrimeiraLinhaSemIdusAnterior() {
        IdusProcessoHistorico gravado = repository.saveAndFlush(linha(UUID.randomUUID(), SegmentoIdus.CRIACAO,
                EventoIdus.PROCESSO_CRIADO, REGISTO));
        entityManager.clear();

        assertThat(repository.findById(gravado.getId()).orElseThrow().getIdusAnterior()).isNull();
    }

    @Test
    void deveDevolverOHistoricoDoProcessoPorOrdemDeRegisto() {
        UUID processoId = UUID.randomUUID();
        repository.save(linha(processoId, SegmentoIdus.PUB_INCM, EventoIdus.PUBLICACAO_INCM, REGISTO.plusSeconds(120)));
        repository.save(linha(processoId, SegmentoIdus.CRIACAO, EventoIdus.PROCESSO_CRIADO, REGISTO));
        repository.save(linha(processoId, SegmentoIdus.SUBMISSAO, EventoIdus.SUBMISSAO, REGISTO.plusSeconds(60)));
        repository.save(linha(UUID.randomUUID(), SegmentoIdus.CRIACAO, EventoIdus.PROCESSO_CRIADO, REGISTO.minusSeconds(60)));

        List<IdusProcessoHistorico> historico = repository.findByProcessoIdOrderByRegistadoEmAsc(processoId);

        assertThat(historico).extracting(IdusProcessoHistorico::getSegmento)
                .containsExactly(SegmentoIdus.CRIACAO, SegmentoIdus.SUBMISSAO, SegmentoIdus.PUB_INCM);
        assertThat(repository.findByProcessoIdOrderByRegistadoEmAsc(UUID.randomUUID())).isEmpty();
    }

    @Test
    void entidadeNaoTemSettersNemColunasAtualizaveis() {
        assertThat(Arrays.stream(IdusProcessoHistorico.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(nome -> nome.startsWith("set"));

        List<Field> campos = Arrays.stream(IdusProcessoHistorico.class.getDeclaredFields())
                .filter(campo -> campo.isAnnotationPresent(Column.class))
                .toList();
        assertThat(campos).hasSize(8);
        assertThat(campos).allSatisfy(campo -> assertThat(campo.getAnnotation(Column.class).updatable())
                .as(campo.getName()).isFalse());
    }

    private static IdusProcessoHistorico linha(UUID processoId, SegmentoIdus segmento, EventoIdus evento, Instant registadoEm) {
        return IdusProcessoHistorico.builder()
                .processoId(processoId)
                .segmento(segmento)
                .idusNovo("SAIGT-1001-T001-1-031-00")
                .evento(evento)
                .registadoEm(registadoEm)
                .correlationId("corr-1")
                .build();
    }
}
