package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MetadadosMotuRepositoryTest {

    @Autowired
    private MetadadosMotuRepository repository;

    @Autowired
    private PlantaRepository plantaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Processo processo;
    private Processo outroProcesso;
    private Utilizador autor;

    @BeforeEach
    void criarProcessos() {
        TipoInstrumento pdm = entityManager.persist(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica alteracao = entityManager.persist(new TipoDinamica("ALTERACAO", "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = entityManager.persist(new InstrumentoDinamicaConfig(pdm, alteracao));
        TipoProcedimento publicacao = entityManager.persist(new TipoProcedimento("PUBLICACAO", "Publicação"));
        InstrumentoProcedimentoConfig config = entityManager.persist(
                new InstrumentoProcedimentoConfig(dinamicaConfig, publicacao, 1, "SEGUNDA_H"));
        Entidade entidade = entityManager.persist(EntidadeTeste.nova("501234502", "Câmara Municipal"));
        autor = entityManager.persist(new Utilizador("Ana Ferreira", "ana.ferreira@cm.pt", "INCM"));
        Instrumento instrumento = entityManager.persist(new Instrumento(pdm, "1", "2026/PDM/2", autor, autor));
        Dinamica dinamica = entityManager.persist(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        processo = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        outroProcesso = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        entityManager.flush();
    }

    private ProcessoDocumento documento(Processo dono, String nome) {
        return entityManager.persist(new ProcessoDocumento(dono, null, CategoriaDocumento.PUBLICACAO, "zip", nome,
                "privado/" + nome, "a".repeat(64), "v1", 10, autor));
    }

    @Test
    void gravaELeTodasAsColunasDaSerieEDoConjunto() {
        ProcessoDocumento documento = documento(processo, "ordenamento.zip");
        UUID serieId = repository.saveAndFlush(MetadadosMotu.serie(processo)).getId();
        MetadadosMotu conjunto = MetadadosMotu.conjunto(processo, documento);
        conjunto.setCompleto(true);
        UUID conjuntoId = repository.saveAndFlush(conjunto).getId();
        entityManager.clear();

        MetadadosMotu serie = repository.findById(serieId).orElseThrow();
        assertThat(serie.getProcesso().getId()).isEqualTo(processo.getId());
        assertThat(serie.getTipo()).isEqualTo(TipoMetadadosMotu.SERIE);
        assertThat(serie.isOpcional()).isFalse();
        assertThat(serie.getProcessoDocumento()).isNull();
        assertThat(serie.isCompleto()).isFalse();
        assertThat(serie.getCriadoEm()).isNotNull();
        assertThat(serie.getAtualizadoEm()).isNotNull();

        MetadadosMotu lido = repository.findById(conjuntoId).orElseThrow();
        assertThat(lido.getTipo()).isEqualTo(TipoMetadadosMotu.CDG);
        assertThat(lido.isOpcional()).isFalse();
        assertThat(lido.getProcessoDocumento().getId()).isEqualTo(documento.getId());
        assertThat(lido.isCompleto()).isTrue();
    }

    @Test
    void encontraASerieEOConjuntoNaoOpcionalDoDocumento() {
        ProcessoDocumento documento = documento(processo, "ordenamento.zip");
        MetadadosMotu serie = repository.save(MetadadosMotu.serie(processo));
        MetadadosMotu conjunto = repository.save(MetadadosMotu.conjunto(processo, documento));
        repository.save(MetadadosMotu.serie(outroProcesso));
        entityManager.flush();

        assertThat(repository.findByProcesso_IdAndTipo(processo.getId(), TipoMetadadosMotu.SERIE))
                .get().extracting(MetadadosMotu::getId).isEqualTo(serie.getId());
        assertThat(repository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(documento.getId(), TipoMetadadosMotu.CDG))
                .get().extracting(MetadadosMotu::getId).isEqualTo(conjunto.getId());
        assertThat(repository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(documento.getId(),
                TipoMetadadosMotu.SERIE)).isEmpty();
    }

    @Test
    void listaEApagaAsFichasPorProcesso() {
        repository.save(MetadadosMotu.serie(processo));
        repository.save(MetadadosMotu.conjunto(processo, documento(processo, "a.zip")));
        repository.save(MetadadosMotu.serie(outroProcesso));
        entityManager.flush();

        assertThat(repository.findByProcesso_Id(processo.getId())).hasSize(2);

        repository.deleteByProcesso_Id(processo.getId());
        entityManager.flush();

        assertThat(repository.findByProcesso_Id(processo.getId())).isEmpty();
        assertThat(repository.findByProcesso_Id(outroProcesso.getId())).hasSize(1);
    }

    @Test
    void ligaAPlantaAoConjunto() {
        ProcessoDocumento documento = documento(processo, "ordenamento.zip");
        MetadadosMotu conjunto = repository.save(MetadadosMotu.conjunto(processo, documento));
        Planta planta = new Planta(processo, documento);
        planta.setMetadadosMotu(conjunto);
        plantaRepository.saveAndFlush(planta);
        entityManager.clear();

        assertThat(plantaRepository.findByMetadadosMotu_Id(conjunto.getId())).singleElement()
                .satisfies(lida -> assertThat(lida.getMetadadosMotu().getId()).isEqualTo(conjunto.getId()));
    }

    @Test
    void recusaUmaSerieComDocumento() {
        UUID documentoId = documento(processo, "ordenamento.zip").getId();
        entityManager.flush();

        assertThatThrownBy(() -> inserir("SERIE", false, documentoId)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void recusaUmaSerieOpcional() {
        assertThatThrownBy(() -> inserir("SERIE", true, null)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void recusaUmConjuntoNaoOpcionalSemDocumento() {
        assertThatThrownBy(() -> inserir("CDG", false, null)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aceitaUmConjuntoOpcionalSemDocumento() {
        assertThat(inserir("CDG", true, null)).isEqualTo(1);
    }

    private int inserir(String tipo, boolean opcional, UUID documentoId) {
        String documento = documentoId == null ? "NULL" : "'" + documentoId + "'";
        return entityManager.getEntityManager().createNativeQuery("INSERT INTO metadados_motu (id, processo_id, tipo, "
                        + "opcional, processo_documento_id, completo, dados, criado_em, atualizado_em) "
                        + "VALUES (:id, :processo, :tipo, :opcional, " + documento
                        + ", FALSE, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("processo", processo.getId())
                .setParameter("tipo", tipo)
                .setParameter("opcional", opcional)
                .executeUpdate();
    }
}
