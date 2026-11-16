package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
class IncmComunicacaoRepositoryTest {

    private static final Instant ENVIO = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired
    private IncmComunicacaoRepository repository;

    @Test
    void deveDevolverAsLinhasDoProcessoPorOrdemDeEnvio() {
        UUID processoId = UUID.randomUUID();
        repository.save(comunicacao(processoId, "GET_ACT_DETAILS", 1, ENVIO.plusSeconds(60)));
        repository.save(comunicacao(processoId, "SUBMIT_NEW_ACT", 1, ENVIO));
        repository.save(comunicacao(UUID.randomUUID(), "SUBMIT_NEW_ACT", 1, ENVIO.minusSeconds(60)));

        List<IncmComunicacao> linhas = repository.findByProcessoIdOrderByEnviadoEmAsc(processoId);

        assertThat(linhas).extracting(IncmComunicacao::getOperacao).containsExactly("SUBMIT_NEW_ACT", "GET_ACT_DETAILS");
        assertThat(linhas.get(0).getId()).isNotEqualTo(linhas.get(1).getId());
    }

    @Test
    void deveContarSoAOperacaoPedida() {
        UUID processoId = UUID.randomUUID();
        repository.save(comunicacao(processoId, "SUBMIT_NEW_ACT", 1, ENVIO));
        repository.save(comunicacao(processoId, "GET_ACT_DETAILS", 1, ENVIO.plusSeconds(60)));
        repository.save(comunicacao(processoId, "GET_ACT_DETAILS", 1, ENVIO.plusSeconds(120)));

        assertThat(repository.countByProcessoIdAndOperacao(processoId, "SUBMIT_NEW_ACT")).isEqualTo(1);
        assertThat(repository.countByProcessoIdAndOperacao(processoId, "GET_ACT_DETAILS")).isEqualTo(2);
    }

    @Test
    void deveDevolverAUltimaLinhaDaOperacao() {
        UUID processoId = UUID.randomUUID();
        repository.save(comunicacao(processoId, "SUBMIT_NEW_ACT", 2, ENVIO.plusSeconds(60)));
        repository.save(comunicacao(processoId, "SUBMIT_NEW_ACT", 1, ENVIO));
        repository.save(comunicacao(processoId, "GET_ACT_DETAILS", 1, ENVIO.plusSeconds(120)));

        assertThat(repository.findFirstByProcessoIdAndOperacaoOrderByEnviadoEmDesc(processoId, "SUBMIT_NEW_ACT"))
                .map(IncmComunicacao::getSubmissao)
                .contains((short) 2);
    }

    @Test
    void entidadeNaoTemSettersNemColunasAtualizaveis() {
        assertThat(Arrays.stream(IncmComunicacao.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(nome -> nome.startsWith("set"));

        List<Field> campos = Arrays.stream(IncmComunicacao.class.getDeclaredFields())
                .filter(campo -> campo.isAnnotationPresent(Column.class))
                .toList();
        assertThat(campos).hasSize(17);
        assertThat(campos).allSatisfy(campo -> assertThat(campo.getAnnotation(Column.class).updatable())
                .as(campo.getName()).isFalse());
    }

    private static IncmComunicacao comunicacao(UUID processoId, String operacao, int submissao, Instant enviadoEm) {
        return IncmComunicacao.builder()
                .processoId(processoId)
                .operacao(operacao)
                .submissao((short) submissao)
                .pedido("{\"ActTitle\":\"Aviso\"}")
                .correlationId("corr-1")
                .enviadoEm(enviadoEm)
                .build();
    }
}
