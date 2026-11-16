package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessoRejeicaoRepositoryTest {

    @Autowired
    private ProcessoRejeicaoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Processo processo;
    private Processo outroProcesso;

    @BeforeEach
    void criarProcessos() {
        TipoInstrumento pdm = entityManager.persist(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = entityManager.persist(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = entityManager.persist(new InstrumentoDinamicaConfig(pdm, elaboracao));
        TipoProcedimento decisao = entityManager.persist(new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig config = entityManager.persist(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        Entidade entidade = entityManager.persist(EntidadeTeste.nova("501234500", "Câmara Municipal"));
        Utilizador autor = entityManager.persist(new Utilizador("Ana Ferreira", "ana.ferreira@cm.pt", "INCM"));
        Instrumento instrumento = entityManager.persist(
                new Instrumento(pdm, "1", "2026/PDM/1", autor, autor));
        Dinamica dinamica = entityManager.persist(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        processo = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        outroProcesso = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        entityManager.flush();
    }

    @Test
    void ultimaRecusaIncmEADeMaiorSequenciaComEssaOrigem() {
        repository.save(rejeicao(processo, 1, OrigemRejeicao.INCM, "primeira"));
        repository.save(rejeicao(processo, 3, OrigemRejeicao.INCM, "terceira"));
        repository.save(rejeicao(processo, 2, OrigemRejeicao.INCM, "segunda"));
        repository.save(rejeicao(processo, 4, OrigemRejeicao.DGT, null));
        repository.save(rejeicao(outroProcesso, 9, OrigemRejeicao.INCM, "de outro processo"));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(processo.getId(), OrigemRejeicao.INCM))
                .map(ProcessoRejeicao::getMotivoTexto)
                .contains("terceira");
    }

    @Test
    void semRecusaIncmDevolveVazio() {
        repository.save(rejeicao(processo, 1, OrigemRejeicao.DGT, null));
        entityManager.flush();

        assertThat(repository.findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(processo.getId(), OrigemRejeicao.INCM))
                .isEmpty();
    }

    @Test
    void maiorSequenciaEPorProcessoEVaziaSemRecusas() {
        repository.save(rejeicao(processo, 1, OrigemRejeicao.INCM, "primeira"));
        repository.save(rejeicao(processo, 2, OrigemRejeicao.DGT, null));
        entityManager.flush();

        assertThat(repository.maiorSequencia(processo.getId())).contains(2);
        assertThat(repository.maiorSequencia(outroProcesso.getId())).isEmpty();
        assertThat(repository.maiorSequencia(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deveGravarADataDeCriacaoSemAutorNaRecusaDaIncm() {
        ProcessoRejeicao gravada = repository.saveAndFlush(rejeicao(processo, 1, OrigemRejeicao.INCM, "motivo"));

        assertThat(gravada.getId()).isNotNull();
        assertThat(gravada.getCriadoEm()).isNotNull();
        assertThat(gravada.getCriadoPor()).isNull();
    }

    @Test
    void entidadeNaoTemSettersNemColunasAtualizaveis() {
        assertThat(Arrays.stream(ProcessoRejeicao.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(nome -> nome.startsWith("set"));

        assertThat(Arrays.stream(ProcessoRejeicao.class.getDeclaredFields())
                .filter(campo -> campo.isAnnotationPresent(Column.class)))
                .hasSize(6)
                .allSatisfy(campo -> assertThat(campo.getAnnotation(Column.class).updatable())
                        .as(campo.getName()).isFalse());
        assertThat(Arrays.stream(ProcessoRejeicao.class.getDeclaredFields())
                .filter(campo -> campo.isAnnotationPresent(JoinColumn.class)))
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("processo", "criadoPor")
                .allSatisfy(nome -> assertThat(campoJoin(nome).updatable()).as(nome).isFalse());
    }

    private static JoinColumn campoJoin(String nome) {
        try {
            return ProcessoRejeicao.class.getDeclaredField(nome).getAnnotation(JoinColumn.class);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ProcessoRejeicao rejeicao(Processo processo, int sequencia, OrigemRejeicao origem, String motivo) {
        return ProcessoRejeicao.builder()
                .processo(processo)
                .sequencia(sequencia)
                .origem(origem)
                .motivoTexto(motivo)
                .build();
    }
}
