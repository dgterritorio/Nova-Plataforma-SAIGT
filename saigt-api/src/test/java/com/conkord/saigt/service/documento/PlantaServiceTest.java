package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlantaServiceTest {

    private final PlantaRepository plantaRepository = mock(PlantaRepository.class);
    private final PlantaService service = new PlantaService(plantaRepository);
    private final Processo processo = mock(Processo.class);

    private ProcessoDocumento documento(TipoElemento tipoElemento, String nome) {
        DocumentoConfig item = null;
        if (tipoElemento != null) {
            item = mock(DocumentoConfig.class);
            when(item.getTipoElemento()).thenReturn(tipoElemento);
        }
        return new ProcessoDocumento(UUID.randomUUID(), processo, item, CategoriaDocumento.DEPOSITO,
                CaminhoDocumento.extensao(nome), nome, "privado/x", "hash", "v1", 10L, null);
    }

    private ProcessoDocumento zipDaPaginaDasPlantas(String nome) {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getTipoElemento()).thenReturn(TipoElemento.PECA_GRAFICA);
        when(item.getPagina()).thenReturn(PaginaDocumento.PLANTAS);
        return new ProcessoDocumento(UUID.randomUUID(), processo, item, CategoriaDocumento.DEPOSITO,
                CaminhoDocumento.extensao(nome), nome, "privado/x", "hash", "v1", 10L, null);
    }

    private Planta registada() {
        ArgumentCaptor<Planta> captor = ArgumentCaptor.forClass(Planta.class);
        verify(plantaRepository).save(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest
    @CsvSource({"planta1.tif", "planta1.tiff", "PLANTA1.TIF"})
    void registaUmTiffDePecaGraficaComONomeTiff(String nome) {
        ProcessoDocumento documento = documento(TipoElemento.PECA_GRAFICA, nome);

        service.registarPecaGrafica(documento);

        Planta planta = registada();
        assertThat(planta.getProcesso()).isSameAs(processo);
        assertThat(planta.getProcessoDocumento()).isSameAs(documento);
        assertThat(planta.getNomeTiff()).isEqualTo(nome);
        assertThat(planta.getNomeZip()).isNull();
        assertThat(planta.isEmVigor()).isFalse();
        assertThat(planta.getLinkJpeg()).isNull();
    }

    @Test
    void registaUmZipDePecaGraficaComONomeZip() {
        service.registarPecaGrafica(documento(TipoElemento.PECA_GRAFICA, "planta1.zip"));

        Planta planta = registada();
        assertThat(planta.getNomeZip()).isEqualTo("planta1.zip");
        assertThat(planta.getNomeTiff()).isNull();
        assertThat(planta.isEmVigor()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PECA_GRAFICA, planta1.tfw", "PECA_GRAFICA, planta1.gpkg", "PECA_GRAFICA, planta1.pdf",
            "PECA_ESCRITA, regulamento.zip", "PECA_ESCRITA, planta1.tif",
            "OUTROS, anexo.zip", "OUTROS, anexo.tiff"})
    void naoRegistaQuandoNaoEPlanta(TipoElemento tipoElemento, String nome) {
        service.registarPecaGrafica(documento(tipoElemento, nome));

        verify(plantaRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"extra.zip", "extra.tif"})
    void naoRegistaUmDocumentoSemItem(String nome) {
        service.registarPecaGrafica(documento(null, nome));

        verify(plantaRepository, never()).save(any());
    }

    @Test
    void naSubstituicaoAtualizaONomeDaLinhaExistente() {
        ProcessoDocumento documento = documento(TipoElemento.PECA_GRAFICA, "planta1b.tif");
        Planta existente = new Planta(processo, documento);
        existente.setNomeTiff("planta1.tif");
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of(existente));

        service.atualizarAposSubstituicao(documento);

        assertThat(existente.getNomeTiff()).isEqualTo("planta1b.tif");
        assertThat(existente.getNomeZip()).isNull();
        verify(plantaRepository, never()).save(any());
        verify(plantaRepository, never()).delete(any(Planta.class));
    }

    @Test
    void naSubstituicaoDeTiffPorZipLimpaONomeTiff() {
        ProcessoDocumento documento = documento(TipoElemento.PECA_GRAFICA, "planta1.zip");
        Planta existente = new Planta(processo, documento);
        existente.setNomeTiff("planta1.tif");
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of(existente));

        service.atualizarAposSubstituicao(documento);

        assertThat(existente.getNomeZip()).isEqualTo("planta1.zip");
        assertThat(existente.getNomeTiff()).isNull();
    }

    @Test
    void naSubstituicaoCriaALinhaQuandoNaoExistiaEOFicheiroNovoEPlanta() {
        ProcessoDocumento documento = documento(TipoElemento.PECA_GRAFICA, "planta1.zip");
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of());

        service.atualizarAposSubstituicao(documento);

        assertThat(registada().getNomeZip()).isEqualTo("planta1.zip");
    }

    @Test
    void naSubstituicaoApagaALinhaQuandoOFicheiroNovoNaoEPlanta() {
        ProcessoDocumento documento = documento(TipoElemento.PECA_GRAFICA, "planta1.tfw");
        Planta existente = new Planta(processo, documento);
        existente.setNomeTiff("planta1.tif");
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of(existente));

        service.atualizarAposSubstituicao(documento);

        verify(plantaRepository).delete(existente);
        verify(plantaRepository, never()).save(any());
    }

    @Test
    void naSubstituicaoNaoFazNadaQuandoNaoHaLinhaNemPlanta() {
        ProcessoDocumento documento = documento(TipoElemento.PECA_ESCRITA, "regulamento.pdf");
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of());

        service.atualizarAposSubstituicao(documento);

        verify(plantaRepository, never()).save(any());
        verify(plantaRepository, never()).delete(any(Planta.class));
    }

    @Test
    void naSubstituicaoDeUmaPlantaRecusadaTodasAsLinhasPassamAoDocumentoNovoComOsOutrosCamposIguais() {
        ProcessoDocumento recusado = zipDaPaginaDasPlantas("ordenamento.zip");
        ProcessoDocumento novo = zipDaPaginaDasPlantas("ordenamento.zip");
        MetadadosMotu ficha = mock(MetadadosMotu.class);
        List<Planta> linhas = List.of(linhaDaFolha(recusado, "o1.tif", "1", ficha),
                linhaDaFolha(recusado, "o2.tif", "2", ficha), linhaDaFolha(recusado, "o3.tif", "3", ficha));
        when(plantaRepository.findByProcessoDocumento_Id(recusado.getId())).thenReturn(linhas);

        service.substituirRecusada(recusado, novo);

        assertThat(linhas).allSatisfy(linha -> {
            assertThat(linha.getProcessoDocumento()).isSameAs(novo);
            assertThat(linha.getNomeZip()).isEqualTo("ordenamento.zip");
            assertThat(linha.getNomeRegulamento()).isEqualTo("Qualificação do Solo");
            assertThat(linha.getMetadadosMotu()).isSameAs(ficha);
        });
        assertThat(linhas).extracting(Planta::getNomeTiff).containsExactly("o1.tif", "o2.tif", "o3.tif");
        assertThat(linhas).extracting(Planta::getNumeroFolha).containsExactly("1", "2", "3");
        verify(plantaRepository).saveAll(linhas);
        verify(plantaRepository, never()).delete(any(Planta.class));
        verify(plantaRepository, never()).deleteByProcessoDocumento_Id(any());
    }

    @Test
    void naSubstituicaoDeUmaPlantaRecusadaDeTiffONomeZipFicaNulo() {
        ProcessoDocumento recusado = documento(TipoElemento.PECA_GRAFICA, "planta1.tif");
        ProcessoDocumento novo = documento(TipoElemento.PECA_GRAFICA, "planta1.tif");
        Planta linha = new Planta(processo, recusado);
        linha.setNomeTiff("planta1.tif");
        when(plantaRepository.findByProcessoDocumento_Id(recusado.getId())).thenReturn(List.of(linha));

        service.substituirRecusada(recusado, novo);

        assertThat(linha.getProcessoDocumento()).isSameAs(novo);
        assertThat(linha.getNomeTiff()).isEqualTo("planta1.tif");
        assertThat(linha.getNomeZip()).isNull();
    }

    private Planta linhaDaFolha(ProcessoDocumento zip, String tiff, String folha, MetadadosMotu ficha) {
        Planta linha = new Planta(processo, zip);
        linha.setNomeRegulamento("Qualificação do Solo");
        linha.setNumeroFolha(folha);
        linha.setNomeTiff(tiff);
        linha.setNomeZip(zip.getNomeFicheiro());
        linha.setMetadadosMotu(ficha);
        return linha;
    }

    @Test
    void naoRegistaOZipDaPaginaDasPlantas() {
        service.registarPecaGrafica(zipDaPaginaDasPlantas("ordenamento.zip"));

        verifyNoInteractions(plantaRepository);
    }

    @Test
    void naSubstituicaoDoZipDaPaginaDasPlantasApagaTodasAsSuasLinhas() {
        ProcessoDocumento zip = zipDaPaginaDasPlantas("ordenamento.zip");

        service.atualizarAposSubstituicao(zip);

        verify(plantaRepository).deleteByProcessoDocumento_Id(zip.getId());
        verify(plantaRepository, never()).save(any());
    }

    @Test
    void naSubstituicaoAtualizaTodasAsLinhasDeUmDocumentoForaDaPaginaDasPlantas() {
        ProcessoDocumento documento = documento(TipoElemento.PECA_GRAFICA, "planta1b.zip");
        Planta primeira = new Planta(processo, documento);
        Planta segunda = new Planta(processo, documento);
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of(primeira, segunda));

        service.atualizarAposSubstituicao(documento);

        assertThat(primeira.getNomeZip()).isEqualTo("planta1b.zip");
        assertThat(segunda.getNomeZip()).isEqualTo("planta1b.zip");
    }

    private ProcessoDocumento txtDosNomes() {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getTipoElemento()).thenReturn(TipoElemento.OUTROS);
        when(item.getPagina()).thenReturn(PaginaDocumento.PLANTAS);
        when(item.getCodigo()).thenReturn("PLANTAS_TXT_NOMES_DEP");
        return new ProcessoDocumento(UUID.randomUUID(), processo, item, CategoriaDocumento.DEPOSITO, "txt", "nomes.txt",
                "privado/x", "hash", "v1", 10L, null);
    }

    private Planta gravada(ProcessoDocumento zip, Integer linhaTxt, String nome, String folha, String tiff) {
        Planta planta = new Planta(processo, zip);
        planta.setLinhaTxt(linhaTxt);
        planta.setNomeRegulamento(nome);
        planta.setNumeroFolha(folha);
        planta.setNomeTiff(tiff);
        return planta;
    }

    private List<Planta> gravadasPor(List<Planta> existentes, List<PlantaService.LinhaDaPagina> linhas) {
        when(processo.getId()).thenReturn(UUID.randomUUID());
        when(plantaRepository.findByProcesso_Id(processo.getId())).thenReturn(existentes);
        service.substituirDaPaginaDasPlantas(processo, linhas);
        ArgumentCaptor<List<Planta>> captor = ArgumentCaptor.forClass(List.class);
        verify(plantaRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void criaUmaLinhaPorAssociacaoComALinhaDoTxt() {
        ProcessoDocumento zip = zipDaPaginaDasPlantas("ordenamento.zip");

        List<Planta> gravadas = gravadasPor(List.of(), List.of(
                new PlantaService.LinhaDaPagina(zip, 1, "Qualificação do Solo", "1", "o1.tif"),
                new PlantaService.LinhaDaPagina(zip, 2, "Qualificação do Solo", "2", "o2.tif")));

        assertThat(gravadas).extracting(Planta::getLinhaTxt, Planta::getNomeRegulamento, Planta::getNumeroFolha,
                Planta::getNomeTiff, Planta::getNomeZip).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "Qualificação do Solo", "1", "o1.tif", "ordenamento.zip"),
                org.assertj.core.groups.Tuple.tuple(2, "Qualificação do Solo", "2", "o2.tif", "ordenamento.zip"));
        assertThat(gravadas).allSatisfy(planta -> assertThat(planta.getProcessoDocumento()).isSameAs(zip));
        verify(plantaRepository).deleteAll(List.of());
    }

    @Test
    void mantemALinhaComAsCorrecoesQuandoAAssociacaoNaoMudaEApagaAsQueDeixaramDeExistir() {
        ProcessoDocumento zip = zipDaPaginaDasPlantas("ordenamento.zip");
        MetadadosMotu conjunto = mock(MetadadosMotu.class);
        Planta corrigida = gravada(zip, 1, "Nome corrigido", "1A", "o1.tif");
        corrigida.setMetadadosMotu(conjunto);
        Planta desassociada = gravada(zip, 2, "Qualificação do Solo", "2", "o2.tif");

        List<Planta> gravadas = gravadasPor(new ArrayList<>(List.of(corrigida, desassociada)), List.of(
                new PlantaService.LinhaDaPagina(zip, 1, "Qualificação do Solo", "1", "o1.tif")));

        assertThat(gravadas).containsExactly(corrigida);
        assertThat(corrigida.getNomeRegulamento()).isEqualTo("Nome corrigido");
        assertThat(corrigida.getNumeroFolha()).isEqualTo("1A");
        assertThat(corrigida.getMetadadosMotu()).isSameAs(conjunto);
        verify(plantaRepository).deleteAll(List.of(desassociada));
    }

    @Test
    void aLinhaDoTxtAssociadaAOutroTiffCriaUmaLinhaNovaComONomeEAFolhaDoTxt() {
        ProcessoDocumento zip = zipDaPaginaDasPlantas("ordenamento.zip");
        Planta corrigida = gravada(zip, 1, "Nome corrigido", "1A", "o1.tif");

        List<Planta> gravadas = gravadasPor(new ArrayList<>(List.of(corrigida)), List.of(
                new PlantaService.LinhaDaPagina(zip, 1, "Qualificação do Solo", "1", "o9.tif")));

        assertThat(gravadas).hasSize(1).doesNotContain(corrigida);
        assertThat(gravadas.get(0).getNomeRegulamento()).isEqualTo("Qualificação do Solo");
        assertThat(gravadas.get(0).getNumeroFolha()).isEqualTo("1");
        assertThat(gravadas.get(0).getNomeTiff()).isEqualTo("o9.tif");
        verify(plantaRepository).deleteAll(List.of(corrigida));
    }

    @Test
    void aLinhaAnteriorALinhaTxtReconheceSePeloNomeEPelaFolhaEPassaATerALinha() {
        ProcessoDocumento zip = zipDaPaginaDasPlantas("ordenamento.zip");
        Planta antiga = gravada(zip, null, "Qualificação do Solo", "1", "o1.tif");

        List<Planta> gravadas = gravadasPor(new ArrayList<>(List.of(antiga)), List.of(
                new PlantaService.LinhaDaPagina(zip, 3, "Qualificação do Solo", "1", "o1.tif")));

        assertThat(gravadas).containsExactly(antiga);
        assertThat(antiga.getLinhaTxt()).isEqualTo(3);
    }

    @Test
    void naSubstituicaoDoTxtApagaAsLinhasDaPaginaDasPlantas() {
        ProcessoDocumento txt = txtDosNomes();
        UUID processoId = UUID.randomUUID();
        when(processo.getId()).thenReturn(processoId);

        service.atualizarAposSubstituicao(txt);

        verify(plantaRepository).apagarDaPagina(processoId, PaginaDocumento.PLANTAS);
        verify(plantaRepository, never()).deleteByProcessoDocumento_Id(any());
    }

    @Test
    void naRemocaoDoTxtApagaAsLinhasDaPaginaDasPlantas() {
        ProcessoDocumento txt = txtDosNomes();
        UUID processoId = UUID.randomUUID();
        when(processo.getId()).thenReturn(processoId);

        service.removerDoDocumento(txt);

        verify(plantaRepository).apagarDaPagina(processoId, PaginaDocumento.PLANTAS);
    }

    @Test
    void naRemocaoDeUmZipApagaSoAsLinhasDesseDocumento() {
        ProcessoDocumento zip = zipDaPaginaDasPlantas("ordenamento.zip");

        service.removerDoDocumento(zip);

        verify(plantaRepository).deleteByProcessoDocumento_Id(zip.getId());
        verify(plantaRepository, never()).apagarDaPagina(any(), any());
    }

    @Test
    void removeAsLinhasPorDocumentoEPorProcesso() {
        UUID documentoId = UUID.randomUUID();
        UUID processoId = UUID.randomUUID();

        service.removerDoDocumento(documentoId);
        service.removerDoProcesso(processoId);

        verify(plantaRepository).deleteByProcessoDocumento_Id(documentoId);
        verify(plantaRepository).deleteByProcesso_Id(processoId);
    }
}
