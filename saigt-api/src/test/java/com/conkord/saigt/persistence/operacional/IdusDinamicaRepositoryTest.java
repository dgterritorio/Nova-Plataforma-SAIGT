package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdusDinamicaRepositoryTest {

    private static final Instant GERADO_EM = Instant.parse("2026-11-03T10:00:00Z");

    @Autowired
    private IdusDinamicaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private Dinamica criarDinamica() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade cmLisboa = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        return dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadoPor));
    }

    @Test
    void deveGravarELerTodasAsColunas() {
        Dinamica dinamica = criarDinamica();
        repository.saveAndFlush(IdusDinamica.builder()
                .dinamica(dinamica)
                .idus("SNIT-1001-T001")
                .idIgtSnit("1001")
                .ordemSnit("T001")
                .geradoEm(GERADO_EM)
                .build());
        entityManager.clear();

        IdusDinamica lido = repository.findById(dinamica.getId()).orElseThrow();

        assertThat(lido.getDinamicaId()).isEqualTo(dinamica.getId());
        assertThat(lido.getDinamica().getId()).isEqualTo(dinamica.getId());
        assertThat(lido.getIdus()).isEqualTo("SNIT-1001-T001");
        assertThat(lido.getIdIgtSnit()).isEqualTo("1001");
        assertThat(lido.getOrdemSnit()).isEqualTo("T001");
        assertThat(lido.getGeradoEm()).isEqualTo(GERADO_EM);
    }
}
