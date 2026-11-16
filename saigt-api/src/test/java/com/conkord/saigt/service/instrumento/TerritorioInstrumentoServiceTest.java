package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TerritorioInstrumentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    private InstrumentoConcelhoRepository instrumentoConcelhoRepository;

    @Autowired
    private SnitReferenciaRepository snitReferenciaRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private final TerritorioLocalService territorioLocalService = mock(TerritorioLocalService.class);
    private TerritorioInstrumentoService service;
    private Instrumento instrumento;

    @BeforeEach
    void setUp() {
        service = new TerritorioInstrumentoService(instrumentoConcelhoRepository, snitReferenciaRepository, new ObjectMapper(),
                territorioLocalService, new ReferenciaSnitConsultaService(snitReferenciaRepository, new ObjectMapper()));
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal"));
        Utilizador autor = new Utilizador("Ana Ferreira", "ana.ferreira@cm.pt");
        autor.setPerfilInterno(PerfilInterno.MASTER);
        autor = utilizadorRepository.saveAndFlush(autor);
        instrumento = instrumentoRepository.saveAndFlush(new Instrumento(pdm, "1", "PDM de Sintra", autor, autor));

        snitReferenciaRepository.saveAndFlush(municipio("1111", "Sintra", "AML"));
        snitReferenciaRepository.saveAndFlush(municipio("1105", "Cascais", "AML"));
        snitReferenciaRepository.saveAndFlush(municipio("1312", "Porto", "NORTE"));
        snitReferenciaRepository.saveAndFlush(nutII("AML", "Área Metropolitana de Lisboa"));
        snitReferenciaRepository.saveAndFlush(nutII("NORTE", "Norte"));
    }

    private static SnitReferencia municipio(String dtcc, String nome, String nut2Code) {
        return new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, dtcc, nome,
                "{\"dtcc\":\"" + dtcc + "\",\"name\":\"" + nome + "\",\"nut2Code\":\"" + nut2Code + "\"}", AGORA);
    }

    private static SnitReferencia nutII(String codigo, String nome) {
        return new SnitReferencia(ListaReferenciaSnit.NUT2, codigo, nome, "{\"code\":\"" + codigo + "\",\"label\":\"" + nome + "\"}", AGORA);
    }

    private void comConcelhos(String... codigos) {
        for (String codigo : codigos) {
            instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumento, codigo));
        }
    }

    @Test
    void deveDevolverOConcelhoEASuaNutII() {
        comConcelhos("1111");

        TerritorioInstrumentoResponse territorio = service.territorio(instrumento.getId());

        assertThat(territorio.concelhos()).containsExactly(new ItemTerritorio("1111", "Sintra"));
        assertThat(territorio.nutsII()).containsExactly(new ItemTerritorio("AML", "Área Metropolitana de Lisboa"));
    }

    @Test
    void deveDevolverUmaSoNutIIParaDoisConcelhosDaMesmaNutII() {
        comConcelhos("1111", "1105");

        TerritorioInstrumentoResponse territorio = service.territorio(instrumento.getId());

        assertThat(territorio.concelhos()).containsExactly(
                new ItemTerritorio("1105", "Cascais"), new ItemTerritorio("1111", "Sintra"));
        assertThat(territorio.nutsII()).containsExactly(new ItemTerritorio("AML", "Área Metropolitana de Lisboa"));
    }

    @Test
    void deveDevolverAsNutsIIDeConcelhosDeNutsIIDiferentes() {
        comConcelhos("1111", "1312");

        assertThat(service.territorio(instrumento.getId()).nutsII()).containsExactlyInAnyOrder(
                new ItemTerritorio("AML", "Área Metropolitana de Lisboa"), new ItemTerritorio("NORTE", "Norte"));
    }

    @Test
    void deveDevolverListasVaziasQuandoOInstrumentoNaoTemConcelhos() {
        TerritorioInstrumentoResponse territorio = service.territorio(instrumento.getId());

        assertThat(territorio.concelhos()).isEmpty();
        assertThat(territorio.nutsII()).isEmpty();
    }

    @Test
    void deveMostrarOConcelhoSemNomeNemNutIIQuandoNaoEstaNaReplica() {
        comConcelhos("9999", "1111");

        TerritorioInstrumentoResponse territorio = service.territorio(instrumento.getId());

        assertThat(territorio.concelhos()).containsExactly(
                new ItemTerritorio("1111", "Sintra"), new ItemTerritorio("9999", null));
        assertThat(territorio.nutsII()).containsExactly(new ItemTerritorio("AML", "Área Metropolitana de Lisboa"));
    }

    @Test
    void deveUsarONomeLocalESemNutIIQuandoAReplicaNaoTemOConcelho() {
        when(territorioLocalService.nomeConcelho("9998")).thenReturn(Optional.of("Mafra"));
        comConcelhos("9998");

        TerritorioInstrumentoResponse territorio = service.territorio(instrumento.getId());

        assertThat(territorio.concelhos()).containsExactly(new ItemTerritorio("9998", "Mafra"));
        assertThat(territorio.nutsII()).isEmpty();
    }

    @Test
    void deveContinuarAMostrarOConcelhoInativoNaReplica() {
        snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, "1111").orElseThrow().setAtivo(false);
        comConcelhos("1111");

        TerritorioInstrumentoResponse territorio = service.territorio(instrumento.getId());

        assertThat(territorio.concelhos()).containsExactly(new ItemTerritorio("1111", "Sintra"));
        assertThat(territorio.nutsII()).containsExactly(new ItemTerritorio("AML", "Área Metropolitana de Lisboa"));
    }

    @Test
    void naoDeveDarNutIIAUmConcelhoSemNut2CodeNosDadosDeOrigem() {
        snitReferenciaRepository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, "0101", "Águeda",
                "{\"dtcc\":\"0101\",\"name\":\"ÁGUEDA\"}", AGORA));
        comConcelhos("0101");

        TerritorioInstrumentoResponse territorio = service.territorio(instrumento.getId());

        assertThat(territorio.concelhos()).containsExactly(new ItemTerritorio("0101", "Águeda"));
        assertThat(territorio.nutsII()).isEmpty();
    }

    @Test
    void osNomesDaListagemSaoOsDoTerritorioDoDetalhePelaOrdemDosCodigos() {
        comConcelhos("1312", "1111", "1105");
        Instrumento semConcelhos = instrumentoRepository.saveAndFlush(new Instrumento(instrumento.getTipoInstrumento(), "2",
                "PDM sem território", instrumento.getCriadoPor(), instrumento.getCriadoPor()));

        Map<UUID, List<String>> nomes = service.nomesConcelhos(List.of(instrumento.getId(), semConcelhos.getId()));

        assertThat(nomes.get(instrumento.getId())).containsExactly("Cascais", "Sintra", "Porto")
                .isEqualTo(service.territorio(instrumento.getId()).concelhos().stream().map(ItemTerritorio::nome).toList());
        assertThat(nomes).doesNotContainKey(semConcelhos.getId());
    }
}
