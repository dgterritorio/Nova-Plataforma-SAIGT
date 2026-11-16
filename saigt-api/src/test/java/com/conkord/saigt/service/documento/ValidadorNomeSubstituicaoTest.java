package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.NomeSubstituicaoDiferenteException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidadorNomeSubstituicaoTest {

    private final PlantaRepository plantaRepository = mock(PlantaRepository.class);
    private final ValidadorNomeSubstituicao validador = new ValidadorNomeSubstituicao(plantaRepository);
    private final Processo processo = mock(Processo.class);
    private final ProcessoDocumento recusado = new ProcessoDocumento(UUID.randomUUID(), processo, null,
            CategoriaDocumento.PUBLICACAO, "zip", "PO_01.zip", "processos/1/PUBLICACAO/x", "hash", "v1", 10L, null);

    private void comFolhas(String... tiffs) {
        List<Planta> linhas = Arrays.stream(tiffs).map(tiff -> {
            Planta planta = new Planta(processo, recusado);
            planta.setNomeTiff(tiff);
            return planta;
        }).toList();
        when(plantaRepository.findByProcessoDocumento_Id(recusado.getId())).thenReturn(linhas);
    }

    @Test
    void deveAceitarOMesmoNomeNumaPlanta() {
        comFolhas("a.tif", "b.tif");

        assertThatCode(() -> validador.validarNome(recusado, "PO_01.zip")).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarUmNomeDiferenteNumaPlantaComONomeDoRecusado() {
        comFolhas("a.tif");

        assertThatThrownBy(() -> validador.validarNome(recusado, "PO_1.zip"))
                .isInstanceOfSatisfying(NomeSubstituicaoDiferenteException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("DOC_NOME_SUBSTITUICAO_DIFERENTE");
                    assertThat(e.getMessage()).isEqualTo(
                            "A planta de substituição tem de ter exatamente o mesmo nome do ficheiro recusado: PO_01.zip.");
                    assertThat(e.getCampos()).singleElement()
                            .satisfies(campo -> assertThat(campo.path()).isEqualTo("ficheiro"));
                });
    }

    @Test
    void deveDistinguirMaiusculasDeMinusculas() {
        comFolhas("a.tif");

        assertThatThrownBy(() -> validador.validarNome(recusado, "po_01.zip"))
                .isInstanceOf(NomeSubstituicaoDiferenteException.class);
    }

    @Test
    void deveDeixarONomeLivreQuandoNaoHaPlanta() {
        when(plantaRepository.findByProcessoDocumento_Id(recusado.getId())).thenReturn(List.of());

        assertThatCode(() -> validador.validarNome(recusado, "outro.zip")).doesNotThrowAnyException();
        assertThatCode(() -> validador.validarFolhas(recusado, List.of())).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarUmZipComTodasAsFolhas() {
        comFolhas("a.tif", "b.tif");

        assertThatCode(() -> validador.validarFolhas(recusado, List.of("a.tif", "b.tif", "c.tif")))
                .doesNotThrowAnyException();
    }

    @Test
    void deveRecusarUmZipSemUmaDasFolhasComONomeEmFalta() {
        comFolhas("a.tif", "b.tif");

        assertThatThrownBy(() -> validador.validarFolhas(recusado, List.of("a.tif")))
                .isInstanceOfSatisfying(NomeSubstituicaoDiferenteException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("DOC_NOME_SUBSTITUICAO_DIFERENTE");
                    assertThat(e.getMessage()).isEqualTo(
                            "As folhas de substituição têm de ter exatamente os mesmos nomes. Faltam: b.tif.");
                    assertThat(e.getCampos()).singleElement()
                            .satisfies(campo -> assertThat(campo.path()).isEqualTo("ficheiro"));
                });
    }

    @Test
    void deveRecusarUmZipComUmTiffDeOutroNomeEListarTodosOsEmFalta() {
        comFolhas("a.tif", "b.tif", "c.tif");

        assertThatThrownBy(() -> validador.validarFolhas(recusado, List.of("a.tif", "B.tif", "d.tif")))
                .hasMessage("As folhas de substituição têm de ter exatamente os mesmos nomes. Faltam: b.tif, c.tif.");
    }
}
