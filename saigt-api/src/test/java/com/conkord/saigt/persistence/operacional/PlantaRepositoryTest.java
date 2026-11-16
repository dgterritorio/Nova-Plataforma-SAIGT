package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlantaRepositoryTest {

    @Autowired
    private PlantaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Processo processo;
    private Processo outroProcesso;
    private Utilizador autor;
    private InstrumentoProcedimentoConfig config;

    @BeforeEach
    void criarProcessos() {
        TipoInstrumento pdm = entityManager.persist(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = entityManager.persist(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = entityManager.persist(new InstrumentoDinamicaConfig(pdm, elaboracao));
        TipoProcedimento deposito = entityManager.persist(new TipoProcedimento("DEPOSITO", "Depósito"));
        config = entityManager.persist(
                new InstrumentoProcedimentoConfig(dinamicaConfig, deposito, 1, "SEGUNDA_H"));
        Entidade entidade = entityManager.persist(EntidadeTeste.nova("501234501", "Câmara Municipal"));
        autor = entityManager.persist(new Utilizador("Ana Ferreira", "ana.ferreira@cm.pt", "INCM"));
        Instrumento instrumento = entityManager.persist(new Instrumento(pdm, "1", "2026/PDM/1", autor, autor));
        Dinamica dinamica = entityManager.persist(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        processo = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        outroProcesso = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        entityManager.flush();
    }

    private ProcessoDocumento documento(Processo dono, String nome) {
        return entityManager.persist(new ProcessoDocumento(dono, null, CategoriaDocumento.DEPOSITO, "zip", nome,
                "privado/" + nome, "a".repeat(64), "v1", 10, autor));
    }

    @Test
    void gravaELeTodasAsColunas() {
        ProcessoDocumento documento = documento(processo, "planta1.zip");
        Planta planta = new Planta(processo, documento);
        planta.setNomeRegulamento("Planta de Ordenamento — Qualificação do Solo");
        planta.setNumeroFolha("1A");
        planta.setNomeTiff("planta1.tif");
        planta.setNomeZip("planta1.zip");
        planta.setLinkJpeg("https://saigt.pt/api/v1/publico/token/planta1.jpg");
        planta.setEmVigor(true);
        planta.setAtualizadoPor(autor);
        repository.saveAndFlush(planta);
        entityManager.clear();

        Planta lida = repository.findByProcessoDocumento_Id(documento.getId()).getFirst();

        assertThat(lida.getId()).isNotNull();
        assertThat(lida.getProcesso().getId()).isEqualTo(processo.getId());
        assertThat(lida.getProcessoDocumento().getId()).isEqualTo(documento.getId());
        assertThat(lida.getNomeRegulamento()).isEqualTo("Planta de Ordenamento — Qualificação do Solo");
        assertThat(lida.getNumeroFolha()).isEqualTo("1A");
        assertThat(lida.getNomeTiff()).isEqualTo("planta1.tif");
        assertThat(lida.getNomeZip()).isEqualTo("planta1.zip");
        assertThat(lida.getLinkJpeg()).isEqualTo("https://saigt.pt/api/v1/publico/token/planta1.jpg");
        assertThat(lida.isEmVigor()).isTrue();
        assertThat(lida.getAtualizadoPor().getId()).isEqualTo(autor.getId());
        assertThat(lida.getCriadoEm()).isNotNull();
        assertThat(lida.getAtualizadoEm()).isNotNull();
    }

    @Test
    void novaPlantaNaoEstaEmVigorENaoTemCamposDoBackOffice() {
        ProcessoDocumento documento = documento(processo, "planta1.zip");
        repository.saveAndFlush(new Planta(processo, documento));
        entityManager.clear();

        Planta lida = repository.findByProcessoDocumento_Id(documento.getId()).getFirst();

        assertThat(lida.isEmVigor()).isFalse();
        assertThat(lida.getNomeRegulamento()).isNull();
        assertThat(lida.getNumeroFolha()).isNull();
        assertThat(lida.getLinkJpeg()).isNull();
        assertThat(lida.getAtualizadoPor()).isNull();
    }

    @Test
    void aceitaVariasPlantasDoMesmoDocumento() {
        ProcessoDocumento zip = documento(processo, "planta1.zip");
        repository.save(planta(zip, "1", "planta1.tif"));
        repository.save(planta(zip, "2", "planta2.tif"));
        entityManager.flush();

        assertThat(repository.findByProcessoDocumento_Id(zip.getId())).hasSize(2);
    }

    private Planta planta(ProcessoDocumento zip, String folha, String tiff) {
        Planta planta = new Planta(zip.getProcesso(), zip);
        planta.setNumeroFolha(folha);
        planta.setNomeTiff(tiff);
        return planta;
    }

    @Test
    void recusaApagarODocumentoEnquantoTemPlanta() {
        ProcessoDocumento documento = documento(processo, "planta1.zip");
        repository.saveAndFlush(new Planta(processo, documento));
        entityManager.clear();

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM processo_documento WHERE id = :id")
                .setParameter("id", documento.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void apagaSoAsLinhasDosFicheirosDaPaginaDasPlantasDoProcesso() {
        DocumentoConfig itemDasPlantas = new DocumentoConfig(config, "ZIP_PLANTAS", "Planta de Ordenamento",
                "Planta de Ordenamento", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", 1);
        itemDasPlantas.setPagina(PaginaDocumento.PLANTAS);
        entityManager.persist(itemDasPlantas);
        ProcessoDocumento zip = entityManager.persist(new ProcessoDocumento(processo, itemDasPlantas,
                CategoriaDocumento.DEPOSITO, "zip", "ordenamento.zip", "privado/ord", "a".repeat(64), "v1", 10, autor));
        ProcessoDocumento zipDoOutro = entityManager.persist(new ProcessoDocumento(outroProcesso, itemDasPlantas,
                CategoriaDocumento.DEPOSITO, "zip", "outro.zip", "privado/outro", "a".repeat(64), "v1", 10, autor));
        ProcessoDocumento foraDaPagina = documento(processo, "area.zip");
        repository.save(planta(zip, "1", "o1.tif"));
        repository.save(planta(zip, "2", "o2.tif"));
        repository.save(planta(zipDoOutro, "1", "x1.tif"));
        repository.save(planta(foraDaPagina, null, null));
        entityManager.flush();

        repository.apagarDaPagina(processo.getId(), PaginaDocumento.PLANTAS);

        assertThat(repository.findByProcesso_Id(processo.getId())).extracting(planta -> planta.getProcessoDocumento().getId())
                .containsExactly(foraDaPagina.getId());
        assertThat(repository.findByProcesso_Id(outroProcesso.getId())).hasSize(1);
    }

    @Test
    void listaEApagaPorDocumentoEPorProcesso() {
        ProcessoDocumento primeira = documento(processo, "planta1.zip");
        ProcessoDocumento segunda = documento(processo, "planta2.zip");
        ProcessoDocumento doOutro = documento(outroProcesso, "planta3.zip");
        repository.save(new Planta(processo, primeira));
        repository.save(new Planta(processo, segunda));
        repository.save(new Planta(outroProcesso, doOutro));
        entityManager.flush();

        assertThat(repository.findByProcesso_Id(processo.getId())).hasSize(2);

        repository.deleteByProcessoDocumento_Id(primeira.getId());
        entityManager.flush();
        assertThat(repository.findByProcessoDocumento_Id(primeira.getId())).isEmpty();
        assertThat(repository.findByProcesso_Id(processo.getId())).hasSize(1);

        repository.deleteByProcesso_Id(processo.getId());
        entityManager.flush();
        assertThat(repository.findByProcesso_Id(processo.getId())).isEmpty();
        assertThat(repository.findByProcesso_Id(outroProcesso.getId())).hasSize(1);
    }
}
