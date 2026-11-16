package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstrumentoRepositoryTest {

    @Autowired
    private InstrumentoRepository repository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Test
    void deveGuardarELerInstrumentoComIdUuidEOrigemDadosDefault() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);

        Instrumento novo = new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor);
        Instrumento guardado = repository.saveAndFlush(novo);

        assertThat(guardado.getId()).isInstanceOf(UUID.class);

        Optional<Instrumento> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getTipoInstrumento().getId()).isEqualTo(pdm.getId());
        assertThat(lido.get().getNumero()).isEqualTo("1");
        assertThat(lido.get().getDesignacao()).isEqualTo("2026/PDM/001");
        assertThat(lido.get().isEmVigor()).isFalse();
        assertThat(lido.get().getOrigemDados()).isEqualTo("NATIVO");
        assertThat(lido.get().getResponsavel().getId()).isEqualTo(criadoPor.getId());
        assertThat(lido.get().getCriadoPor().getId()).isEqualTo(criadoPor.getId());
    }

    @Test
    void deveAceitarDesignacaoDuplicadaComNumeroDiferente() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        repository.saveAndFlush(new Instrumento(pdm, "1", "PDM de Lisboa", criadoPor, criadoPor));

        Instrumento repetido = repository.saveAndFlush(
                new Instrumento(pdm, "2", "PDM de Lisboa", criadoPor, criadoPor));

        assertThat(repetido.getId()).isNotNull();
    }

    @Test
    void proximoNumeroSequencialDeveDevolverValoresDistintosECrescentes() {
        long primeiro = repository.proximoNumeroSequencial();
        long segundo = repository.proximoNumeroSequencial();

        assertThat(segundo).isGreaterThan(primeiro);
    }

    @Test
    void deveListarTodosOsInstrumentosSemFiltroDeEntidade() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        repository.saveAndFlush(new Instrumento(pdm, "10", "2026/PDM/010", criadoPor, criadoPor));
        repository.saveAndFlush(new Instrumento(pdm, "11", "2026/PDM/011", criadoPor, criadoPor));

        var pagina = repository.findAll(org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(2);
    }
}
