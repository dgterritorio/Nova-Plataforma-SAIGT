package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.DocumentosObrigatoriosEmFaltaException;
import com.conkord.saigt.exception.PlantasMetadadosEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.documento.EstruturaDocumental.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompletudeDocumentalServiceTest {

    private final EstruturaDocumentalService estruturaDocumentalService = mock(EstruturaDocumentalService.class);
    private final PlantasService plantasService = mock(PlantasService.class);
    private final MetadadosService metadadosService = mock(MetadadosService.class);
    private final MetadadosMotuService metadadosMotuService = mock(MetadadosMotuService.class);
    private final CompletudeDocumentalService service = new CompletudeDocumentalService(estruturaDocumentalService,
            plantasService, metadadosService, metadadosMotuService);
    private final Processo processo = mock(Processo.class);

    private static Item pendente(String codigo, String nome, CategoriaDocumento categoria) {
        DocumentoConfig config = new DocumentoConfig(null, codigo, nome, "Separador", categoria, TipoElemento.PECA_ESCRITA,
                FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1);
        config.setObrigatorio(true);
        return new Item(config, List.of());
    }

    private void estruturaComPendencias(Item... pendencias) {
        when(estruturaDocumentalService.montar(processo))
                .thenReturn(new EstruturaDocumental(List.of(), List.of(), List.of(pendencias)));
    }

    @Test
    void deveDevolverListaVaziaQuandoNaoFaltaNenhumFicheiro() {
        estruturaComPendencias();

        assertThat(service.pendencias(processo)).isEmpty();
    }

    @Test
    void deveDevolverAsPendenciasDaEstruturaDocumental() {
        Item deliberacao = pendente("DELIBERACAO_PUB", "Deliberação", CategoriaDocumento.PUBLICACAO);
        estruturaComPendencias(deliberacao);

        assertThat(service.pendencias(processo)).containsExactly(deliberacao);
    }

    @Test
    void naoDeveLancarQuandoNaoHaPendencias() {
        estruturaComPendencias();

        assertThatCode(() -> service.verificar(processo)).doesNotThrowAnyException();
    }

    @Test
    void deveLancarComUmCampoPorPendenciaQuandoFaltamFicheiros() {
        estruturaComPendencias(
                pendente("DELIBERACAO_PUB", "Deliberação do início", CategoriaDocumento.PUBLICACAO),
                pendente("TEXTO_ATO_PUB", "Texto do ato", CategoriaDocumento.PUBLICACAO));

        assertThatThrownBy(() -> service.verificar(processo))
                .isInstanceOfSatisfying(DocumentosObrigatoriosEmFaltaException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("DOCUMENTOS_OBRIGATORIOS_EM_FALTA");
                    assertThat(ex.getCampos()).containsExactly(
                            new ValidacaoException.CampoErro("documentos.DELIBERACAO_PUB",
                                    "Falta carregar: Deliberação do início (PUBLICACAO)."),
                            new ValidacaoException.CampoErro("documentos.TEXTO_ATO_PUB",
                                    "Falta carregar: Texto do ato (PUBLICACAO)."));
                });
    }

    @Test
    void ondeSeAplicaAFichaMotuUsaAsPendenciasMotuEIgnoraAFichaUnica() {
        ValidacaoException.CampoErro serie = new ValidacaoException.CampoErro("metadados.serie", "Preencha a Série de Dados Geográficos.");
        estruturaComPendencias();
        when(metadadosMotuService.aplicaFichaMotu(processo)).thenReturn(true);
        when(estruturaDocumentalService.temPecaGraficaDePublicacaoAplicavel(processo)).thenReturn(true);
        when(metadadosMotuService.pendencias(processo)).thenReturn(List.of(serie));

        assertThatThrownBy(() -> service.verificar(processo))
                .isInstanceOfSatisfying(PlantasMetadadosEmFaltaException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("PLANTAS_METADADOS_EM_FALTA");
                    assertThat(ex.getCampos()).containsExactly(serie);
                });
        verify(metadadosService, never()).pendencias(processo);
    }

    @Test
    void semFichaMotuMantemAsPendenciasDaFichaUnica() {
        ValidacaoException.CampoErro resumo = new ValidacaoException.CampoErro("metadados.resumo", "Preencha este campo da ficha de metadados.");
        when(metadadosMotuService.aplicaFichaMotu(processo)).thenReturn(false);
        when(metadadosService.pendencias(processo)).thenReturn(List.of(resumo));

        assertThat(service.pendenciasPlantasEMetadados(processo)).containsExactly(resumo);
        verify(metadadosMotuService, never()).pendencias(processo);
    }

    @Test
    void ondeSeAplicaAFichaMotuSemPendenciasNaoLanca() {
        estruturaComPendencias();
        when(metadadosMotuService.aplicaFichaMotu(processo)).thenReturn(true);
        when(estruturaDocumentalService.temPecaGraficaDePublicacaoAplicavel(processo)).thenReturn(true);
        when(metadadosMotuService.pendencias(processo)).thenReturn(List.of());

        assertThatCode(() -> service.verificar(processo)).doesNotThrowAnyException();
    }

    @Test
    void semPecaGraficaDePublicacaoAplicavelNaoExigeAFichaMotuNemAFichaUnica() {
        estruturaComPendencias();
        when(metadadosMotuService.aplicaFichaMotu(processo)).thenReturn(true);
        when(estruturaDocumentalService.temPecaGraficaDePublicacaoAplicavel(processo)).thenReturn(false);

        assertThatCode(() -> service.verificar(processo)).doesNotThrowAnyException();
        verify(metadadosMotuService, never()).pendencias(processo);
    }
}
