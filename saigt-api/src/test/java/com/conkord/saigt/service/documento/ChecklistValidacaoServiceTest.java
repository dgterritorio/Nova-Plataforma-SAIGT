package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.VerificacaoDocumentalResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.exception.ZipInvalidoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.conkord.saigt.service.documento.ResultadoVerificacao.FALHA;
import static com.conkord.saigt.service.documento.ResultadoVerificacao.MANUAL;
import static com.conkord.saigt.service.documento.ResultadoVerificacao.OK;
import static com.conkord.saigt.service.documento.VerificacaoChecklist.CONSISTENCIA_VETORIAL_MATRICIAL;
import static com.conkord.saigt.service.documento.VerificacaoChecklist.CORRECAO_ZIP;
import static com.conkord.saigt.service.documento.VerificacaoChecklist.CORRESPONDENCIA_ATO_TEXTO;
import static com.conkord.saigt.service.documento.VerificacaoChecklist.FOLHA_NOME_TIF;
import static com.conkord.saigt.service.documento.VerificacaoChecklist.FORMATO_QUADRO_ANEXO;
import static com.conkord.saigt.service.documento.VerificacaoChecklist.GEORREFERENCIACAO_MATRICIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ChecklistValidacaoServiceTest {

    private static final String ZIP_ILEGIVEL = "O ZIP contém pastas ou ficheiros com nome inválido: ficheiro ZIP ilegível.";
    private static final List<CaracteristicasTiff.BandaTiff> RGB = List.of(
            new CaracteristicasTiff.BandaTiff("Byte", "Red"), new CaracteristicasTiff.BandaTiff("Byte", "Green"),
            new CaracteristicasTiff.BandaTiff("Byte", "Blue"));
    private static final CaracteristicasTiff GEOTIFF_3763 = tiff(3763, true);
    private static final CaracteristicasTiff TIF_COM_TFW = tiff(null, true);
    private static final CaracteristicasTiff SEM_GEORREFERENCIACAO = tiff(null, false);

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final ProcessoDocumentoRepository processoDocumentoRepository = mock(ProcessoDocumentoRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final VerificadorZip verificadorZip = mock(VerificadorZip.class);
    private final InspetorTiff inspetorTiff = mock(InspetorTiff.class);
    private final ChecklistValidacaoService service = new ChecklistValidacaoService(processoRepository,
            processoDocumentoRepository, autorizacaoService, contextoUtilizadorResolver, verificadorZip, inspetorTiff);

    private final UUID processoId = UUID.randomUUID();
    private final Processo processo = mock(Processo.class);
    private final List<ProcessoDocumento> documentos = new ArrayList<>();

    @BeforeEach
    void preparar() {
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(processo));
        when(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processoId)).thenReturn(documentos);
        when(inspetorTiff.inspecionar(anyString(), anyString())).thenReturn(GEOTIFF_3763);
    }

    @Test
    void deveDarOkAoFormatoDeUmDocxEmPublicacaoEDeUmPdfEmDeposito() {
        ProcessoDocumento docx = documento("Regulamento.docx", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA);
        ProcessoDocumento pdf = documento("Relatorio.pdf", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA);

        List<VerificacaoDocumentalResponse> linhas = linhasDe(service.verificar(processoId), FORMATO_QUADRO_ANEXO);

        assertThat(linhas).extracting(VerificacaoDocumentalResponse::documentoId,
                        VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(tuple(docx.getId(), "Regulamento.docx", OK, null),
                        tuple(pdf.getId(), "Relatorio.pdf", OK, null));
    }

    @Test
    void deveAceitarUmPdfEmPublicacaoERecusarUmJpgEmPublicacaoComOFormato() {
        documento("Regulamento.pdf", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA);
        documento("Quadro.jpg", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA);

        assertThat(linhasDe(service.verificar(processoId), FORMATO_QUADRO_ANEXO))
                .extracting(VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(tuple("Quadro.jpg", FALHA, "Formato jpg não é editável para publicação."),
                        tuple("Regulamento.pdf", OK, null));
    }

    @Test
    void deveRecusarNoDepositoUmFormatoEditavel() {
        documento("Regulamento.DOCX", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA);
        documento("Planta.png", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA);

        assertThat(linhasDe(service.verificar(processoId), FORMATO_QUADRO_ANEXO))
                .extracting(VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(tuple("Planta.png", OK, null),
                        tuple("Regulamento.DOCX", FALHA, "Formato docx não é não editável para depósito."));
    }

    @Test
    void deveAvaliarAGeorreferenciacaoDeCadaTifComAInspecaoDoCarregamentoEOTfwDoZip() {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        zipAceite(zip, "1303_PO_I_0993.tif", "1303_PO_I_0993.tfw", "1199_Ord_A.tif", "folha_antiga.tif",
                "FOLHA_ANTIGA.TFW", "folha_sem_tfw.tiff", "legenda.pdf");
        String chave = zip.getCaminhoFicheiro();
        when(inspetorTiff.inspecionar(chave, "folha_antiga.tif")).thenReturn(TIF_COM_TFW);
        when(inspetorTiff.inspecionar(chave, "folha_sem_tfw.tiff")).thenReturn(SEM_GEORREFERENCIACAO);

        List<VerificacaoDocumentalResponse> resultado = service.verificar(processoId);

        assertThat(linhasDe(resultado, GEORREFERENCIACAO_MATRICIAL))
                .extracting(VerificacaoDocumentalResponse::documentoId, VerificacaoDocumentalResponse::nomeFicheiro,
                        VerificacaoDocumentalResponse::resultado, VerificacaoDocumentalResponse::detalhe)
                .containsExactly(
                        tuple(zip.getId(), "1199_Ord_A.tif", OK, "GeoTIFF com sistema de referência EPSG:3763."),
                        tuple(zip.getId(), "1303_PO_I_0993.tif", OK, "GeoTIFF com sistema de referência EPSG:3763."),
                        tuple(zip.getId(), "folha_antiga.tif", MANUAL,
                                "TIF com TFW: confirme o sistema de referência, que o ficheiro não indica."),
                        tuple(zip.getId(), "folha_sem_tfw.tiff", FALHA,
                                "Sem georreferenciação: falta o sistema de referência no TIFF ou o ficheiro "
                                        + "folha_sem_tfw.tfw."));
        assertThat(linhasDe(resultado, FOLHA_NOME_TIF))
                .extracting(VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(
                        tuple("1199_Ord_A.tif", MANUAL, ChecklistValidacaoService.MSG_FOLHA),
                        tuple("1303_PO_I_0993.tif", MANUAL, ChecklistValidacaoService.MSG_FOLHA),
                        tuple("folha_antiga.tif", MANUAL, ChecklistValidacaoService.MSG_FOLHA),
                        tuple("folha_sem_tfw.tiff", MANUAL, ChecklistValidacaoService.MSG_FOLHA));
        assertThat(linhasDe(resultado, CORRECAO_ZIP)).extracting(VerificacaoDocumentalResponse::resultado)
                .containsExactly(OK);
        verify(inspetorTiff).inspecionar(chave, "1199_Ord_A.tif");
        verify(inspetorTiff, never()).inspecionar(anyString(), eq("1303_PO_I_0993.tfw"));
        verify(inspetorTiff, never()).inspecionar(anyString(), eq("legenda.pdf"));
        verify(verificadorZip, never()).entradas(any(), anyString(), any(), anyLong());
    }

    @Test
    void deveDarOkAUmGeoTiffComEpsgComOuSemTfwEManualAUmTifComTfwSemEpsg() {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        zipAceite(zip, "geotiff.tif", "simples.tif", "simples.tfw");
        when(inspetorTiff.inspecionar(anyString(), eq("geotiff.tif"))).thenReturn(tiff(5016, true));
        when(inspetorTiff.inspecionar(anyString(), eq("simples.tif"))).thenReturn(TIF_COM_TFW);

        assertThat(linhasDe(service.verificar(processoId), GEORREFERENCIACAO_MATRICIAL))
                .extracting(VerificacaoDocumentalResponse::resultado, VerificacaoDocumentalResponse::detalhe)
                .containsExactly(tuple(OK, "GeoTIFF com sistema de referência EPSG:5016."),
                        tuple(MANUAL, ChecklistValidacaoService.MSG_TIF_COM_TFW));
    }

    @Test
    void deveDarFalhaAUmTifIlegivelEManualAUmaInspecaoQueFalhaSemFalharOPedido(CapturedOutput saida) {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        zipAceite(zip, "folha_1.tif", "folha_1.tfw", "folha_2.tif");
        when(inspetorTiff.inspecionar(anyString(), eq("folha_1.tif")))
                .thenThrow(new InspecaoTiffIndisponivelException("gdalinfo excedeu o tempo máximo de PT60S"));
        when(inspetorTiff.inspecionar(anyString(), eq("folha_2.tif"))).thenThrow(new TiffIlegivelException());

        List<VerificacaoDocumentalResponse> resultado = service.verificar(processoId);

        assertThat(linhasDe(resultado, GEORREFERENCIACAO_MATRICIAL))
                .extracting(VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(
                        tuple("folha_1.tif", MANUAL, "Não foi possível verificar automaticamente; verifique o ZIP à mão."),
                        tuple("folha_2.tif", FALHA, "Ficheiro TIFF ilegível."));
        assertThat(linhasDe(resultado, CORRECAO_ZIP)).extracting(VerificacaoDocumentalResponse::resultado)
                .containsExactly(OK);
        assertThat(saida.getOut()).contains("WARN").contains("TIFF não inspecionado")
                .contains("processoDocumentoId=" + zip.getId())
                .contains("motivo=InspecaoTiffIndisponivelException")
                .doesNotContain("folha_").doesNotContain(zip.getCaminhoFicheiro());
    }

    @Test
    void deveDarOkAUmZipValidoEFalhaComONomeDaEntradaAUmZipComPastaGravadoAntesDaVerificacao() {
        ProcessoDocumento valido = documento("A_valido.zip", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA);
        ProcessoDocumento comPasta = documento("B_antigo.zip", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA);
        zipAceite(valido, "ordenamento.gpkg");
        zipRecusado(comPasta, "dados/plano.gpkg");

        assertThat(linhasDe(service.verificar(processoId), CORRECAO_ZIP))
                .extracting(VerificacaoDocumentalResponse::documentoId, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(tuple(valido.getId(), OK, null),
                        tuple(comPasta.getId(), FALHA,
                                "O ZIP contém pastas ou ficheiros com nome inválido: dados/plano.gpkg."));
        // Fora do depósito de peças gráficas não há linhas de TIF: as entradas de um ZIP recusado não se releem.
        verify(verificadorZip, never()).entradas(any(), anyString(), any(), anyLong());
    }

    @Test
    void deveListarOsTifsDeUmZipDeDepositoRecusadoParaAsLinhasDeGeorreferenciacao() {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        zipRecusado(zip, "dados/");
        when(verificadorZip.entradas(eq(zip.getId()), anyString(), any(), anyLong()))
                .thenReturn(List.of("dados/", "dados/1199_Ord_A.tif", "dados/1199_Ord_A.tfw"));

        List<VerificacaoDocumentalResponse> resultado = service.verificar(processoId);

        assertThat(linhasDe(resultado, CORRECAO_ZIP)).extracting(VerificacaoDocumentalResponse::resultado)
                .containsExactly(FALHA);
        assertThat(linhasDe(resultado, GEORREFERENCIACAO_MATRICIAL))
                .extracting(VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado)
                .containsExactly(tuple("dados/1199_Ord_A.tif", OK));
    }

    @Test
    void devePorEmManualAsLinhasDeUmZipComOArmazenamentoIndisponivelSemFalharOPedido(CapturedOutput saida) {
        ProcessoDocumento indisponivel = documento("Plantas_indisponivel.zip", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_GRAFICA);
        ProcessoDocumento valido = documento("Plantas_validas.zip", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA);
        when(verificadorZip.verificar(eq(indisponivel.getId()), anyString(), any(), anyLong()))
                .thenThrow(new StorageIndisponivelException("Armazenamento de documentos temporariamente indisponível.",
                        new IOException("processos/2026-0001/DEPOSITO/Plantas_indisponivel.zip")));
        zipAceite(valido, "ordenamento.gpkg");

        List<VerificacaoDocumentalResponse> resultado = service.verificar(processoId);

        assertThat(resultado).extracting(VerificacaoDocumentalResponse::verificacao,
                        VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .contains(
                        tuple(CORRECAO_ZIP, "Plantas_indisponivel.zip", MANUAL,
                                "Não foi possível verificar automaticamente; verifique o ZIP à mão."),
                        tuple(CORRECAO_ZIP, "Plantas_validas.zip", OK, null),
                        tuple(GEORREFERENCIACAO_MATRICIAL, "Plantas_indisponivel.zip", MANUAL,
                                "Não foi possível verificar automaticamente; verifique o ZIP à mão."),
                        tuple(FOLHA_NOME_TIF, "Plantas_indisponivel.zip", MANUAL, ChecklistValidacaoService.MSG_FOLHA));
        assertThat(saida.getOut()).contains("WARN")
                .contains("processoId=" + processoId)
                .contains("processoDocumentoId=" + indisponivel.getId())
                .contains("motivo=StorageIndisponivelException")
                .doesNotContain("Plantas_indisponivel");
    }

    @Test
    void devePorEmManualAsLinhasDosTifsDeUmZipIlegivelSemAvisoTecnico(CapturedOutput saida) {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        when(verificadorZip.verificar(eq(zip.getId()), anyString(), any(), anyLong()))
                .thenThrow(new ZipInvalidoException("ficheiro ZIP ilegível"));
        when(verificadorZip.entradas(eq(zip.getId()), anyString(), any(), anyLong()))
                .thenThrow(new ZipInvalidoException("ficheiro ZIP ilegível"));

        List<VerificacaoDocumentalResponse> resultado = service.verificar(processoId);

        assertThat(resultado).extracting(VerificacaoDocumentalResponse::verificacao,
                        VerificacaoDocumentalResponse::resultado, VerificacaoDocumentalResponse::detalhe)
                .contains(tuple(CORRECAO_ZIP, FALHA, ZIP_ILEGIVEL),
                        tuple(GEORREFERENCIACAO_MATRICIAL, MANUAL, ChecklistValidacaoService.MSG_VERIFICACAO_MANUAL),
                        tuple(FOLHA_NOME_TIF, MANUAL, ChecklistValidacaoService.MSG_FOLHA));
        assertThat(saida.getOut()).doesNotContain("ZIP não verificado");
    }

    @Test
    void devePorEmManualOsTifsQuandoOArmazenamentoFalhaAReLerUmZipRecusado(CapturedOutput saida) {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        zipRecusado(zip, "dados/");
        when(verificadorZip.entradas(eq(zip.getId()), anyString(), any(), anyLong()))
                .thenThrow(new StorageIndisponivelException("indisponível", new IOException()));

        List<VerificacaoDocumentalResponse> resultado = service.verificar(processoId);

        assertThat(resultado).extracting(VerificacaoDocumentalResponse::verificacao,
                        VerificacaoDocumentalResponse::resultado)
                .contains(tuple(CORRECAO_ZIP, FALHA), tuple(GEORREFERENCIACAO_MATRICIAL, MANUAL),
                        tuple(FOLHA_NOME_TIF, MANUAL));
        assertThat(saida.getOut()).contains("processoDocumentoId=" + zip.getId());
    }

    @Test
    void deveTerSempreACorrespondenciaAtoTextoManualSemDocumentoESemConsistenciaSemPecasGraficas() {
        documento("Regulamento.pdf", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA);

        assertThat(service.verificar(processoId))
                .extracting(VerificacaoDocumentalResponse::verificacao, VerificacaoDocumentalResponse::documentoId,
                        VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(
                        tuple(CORRESPONDENCIA_ATO_TEXTO, null, null, MANUAL,
                                "Confirme que o texto do ato corresponde ao diploma."),
                        tuple(FORMATO_QUADRO_ANEXO, documentos.get(0).getId(), "Regulamento.pdf", OK, null));
    }

    @Test
    void deveTerSoACorrespondenciaAtoTextoNumProcessoSemFicheiros() {
        assertThat(service.verificar(processoId))
                .extracting(VerificacaoDocumentalResponse::verificacao, VerificacaoDocumentalResponse::resultado)
                .containsExactly(tuple(CORRESPONDENCIA_ATO_TEXTO, MANUAL));
        verifyNoInteractions(verificadorZip);
    }

    @Test
    void deveJuntarAConsistenciaVetorialMatricialManualQuandoHaPecasGraficas() {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA);
        zipAceite(zip, "ordenamento.gpkg");

        assertThat(service.verificar(processoId))
                .extracting(VerificacaoDocumentalResponse::verificacao, VerificacaoDocumentalResponse::documentoId,
                        VerificacaoDocumentalResponse::nomeFicheiro, VerificacaoDocumentalResponse::resultado,
                        VerificacaoDocumentalResponse::detalhe)
                .containsExactly(
                        tuple(CORRESPONDENCIA_ATO_TEXTO, null, null, MANUAL, ChecklistValidacaoService.MSG_ATO_TEXTO),
                        tuple(CONSISTENCIA_VETORIAL_MATRICIAL, null, null, MANUAL,
                                "Confirme a consistência entre a informação vetorial e as plantas matriciais."),
                        tuple(CORRECAO_ZIP, zip.getId(), "Plantas.zip", OK, null));
    }

    @Test
    void devePorOsFicheirosDoBaldeOutrosSoNaCorrecaoDoZip() {
        ProcessoDocumento zip = documento("Outros.zip", CategoriaDocumento.DEPOSITO, null);
        documento("Outro.jpg", CategoriaDocumento.DEPOSITO, null);
        zipAceite(zip, "1199_Ord_A.tif");

        assertThat(service.verificar(processoId))
                .extracting(VerificacaoDocumentalResponse::verificacao, VerificacaoDocumentalResponse::nomeFicheiro)
                .containsExactly(tuple(CORRESPONDENCIA_ATO_TEXTO, null), tuple(CORRECAO_ZIP, "Outros.zip"));
    }

    @Test
    void naoDeveVerificarOsTifsDeUmZipDePublicacaoNemDeUmItemDeOutros() {
        ProcessoDocumento publicacao = documento("Plantas_pub.zip", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA);
        ProcessoDocumento outros = documento("Anexos.zip", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS);
        zipAceite(publicacao, "1199_Ord_A.tif");
        zipAceite(outros, "1199_Ord_A.tif");

        assertThat(service.verificar(processoId)).extracting(VerificacaoDocumentalResponse::verificacao)
                .doesNotContain(GEORREFERENCIACAO_MATRICIAL, FOLHA_NOME_TIF);
    }

    @Test
    void deveDeixarDeForaOsSubstituidosEAsCopiasEmSubstituidos() {
        ProcessoDocumento substituido = documento("Antigo.jpg", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA);
        when(substituido.getEstadoValidacao()).thenReturn(EstadoValidacao.SUBSTITUIDO);
        ProcessoDocumento copia = documento("Copia.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        String caminhoCopia = "processos/2026-0001/SUBSTITUIDOS/" + copia.getId() + "-Copia.zip";
        when(copia.getCaminhoFicheiro()).thenReturn(caminhoCopia);

        assertThat(service.verificar(processoId)).extracting(VerificacaoDocumentalResponse::verificacao)
                .containsExactly(CORRESPONDENCIA_ATO_TEXTO);
        verifyNoInteractions(verificadorZip);
    }

    @Test
    void deveOrdenarPelosSeisItensDoFr092EDepoisPeloNomeDoFicheiro() {
        ProcessoDocumento zipB = documento("b_plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        documento("Regulamento.pdf", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA);
        ProcessoDocumento zipA = documento("A_plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        documento("anexo.pdf", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA);
        zipAceite(zipB, "02.tif", "02.tfw");
        zipAceite(zipA, "01.tif");

        List<VerificacaoDocumentalResponse> resultado = service.verificar(processoId);

        assertThat(resultado).extracting(VerificacaoDocumentalResponse::verificacao,
                        VerificacaoDocumentalResponse::nomeFicheiro)
                .containsExactly(
                        tuple(CORRESPONDENCIA_ATO_TEXTO, null),
                        tuple(FORMATO_QUADRO_ANEXO, "anexo.pdf"),
                        tuple(FORMATO_QUADRO_ANEXO, "Regulamento.pdf"),
                        tuple(GEORREFERENCIACAO_MATRICIAL, "01.tif"),
                        tuple(GEORREFERENCIACAO_MATRICIAL, "02.tif"),
                        tuple(CONSISTENCIA_VETORIAL_MATRICIAL, null),
                        tuple(FOLHA_NOME_TIF, "01.tif"),
                        tuple(FOLHA_NOME_TIF, "02.tif"),
                        tuple(CORRECAO_ZIP, "A_plantas.zip"),
                        tuple(CORRECAO_ZIP, "b_plantas.zip"));
        assertThat(Arrays.asList(VerificacaoChecklist.values())).containsExactly(CORRESPONDENCIA_ATO_TEXTO,
                FORMATO_QUADRO_ANEXO, GEORREFERENCIACAO_MATRICIAL, CONSISTENCIA_VETORIAL_MATRICIAL, FOLHA_NOME_TIF,
                CORRECAO_ZIP);
    }

    @Test
    void deveVerificarAVersaoGravadaDoObjetoESemVersaoNumBucketSemVersionamento() {
        ProcessoDocumento versionado = documento("A.zip", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA);
        ProcessoDocumento semVersao = documento("B.zip", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA);
        when(semVersao.getVersaoObjeto()).thenReturn("null");
        zipAceite(versionado);
        zipAceite(semVersao);

        service.verificar(processoId);

        UUID idVersionado = versionado.getId();
        String caminhoVersionado = versionado.getCaminhoFicheiro();
        UUID idSemVersao = semVersao.getId();
        String caminhoSemVersao = semVersao.getCaminhoFicheiro();
        verify(verificadorZip).verificar(idVersionado, caminhoVersionado, "v-" + idVersionado, 1234L);
        verify(verificadorZip).verificar(eq(idSemVersao), eq(caminhoSemVersao), isNull(), eq(1234L));
    }

    @Test
    void deveResponder404SemLerOsDocumentosQuandoOProcessoNaoEVisivel() {
        doThrow(new RecursoNaoEncontradoException("Recurso não encontrado."))
                .when(autorizacaoService).verificarVisibilidade(any(), eq(processo));

        assertThatThrownBy(() -> service.verificar(processoId)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(processoDocumentoRepository, never()).findByProcesso_IdOrderByCriadoEmAsc(any());
    }

    @Test
    void deveResponder404QuandoOProcessoNaoExiste() {
        UUID inexistente = UUID.randomUUID();
        when(processoRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verificar(inexistente)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(autorizacaoService, verificadorZip);
    }

    @Test
    void deveRegistarOProcessoEAsContagensSemNomesDeFicheiro(CapturedOutput saida) {
        documento("Quadro.jpg", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA);
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        zipAceite(zip, "1199_Ord_A.tif");
        when(inspetorTiff.inspecionar(anyString(), eq("1199_Ord_A.tif"))).thenReturn(SEM_GEORREFERENCIACAO);

        service.verificar(processoId);

        assertThat(saida.getOut()).contains("INFO")
                .contains("processoId=" + processoId + " verificacoes=6 falhas=2")
                .doesNotContain("Quadro").doesNotContain("Plantas").doesNotContain("1199_Ord_A");
    }

    @Test
    void deveLimitarODetalheA500Caracteres() {
        ProcessoDocumento zip = documento("Plantas.zip", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA);
        zipRecusado(zip, "x".repeat(600) + ".gpkg");

        String detalhe = linhasDe(service.verificar(processoId), CORRECAO_ZIP).get(0).detalhe();

        assertThat(detalhe).hasSize(500).endsWith("…");
        assertThat(ChecklistValidacaoService.limitar("a".repeat(500))).hasSize(500).doesNotEndWith("…");
        assertThat(ChecklistValidacaoService.limitar(null)).isNull();
    }

    @Test
    void deveSerializarAVerificacaoEOResultadoComOsNomesDoEnum() throws Exception {
        UUID documentoId = UUID.randomUUID();
        JsonNode json = new ObjectMapper().valueToTree(new VerificacaoDocumentalResponse(GEORREFERENCIACAO_MATRICIAL,
                documentoId, "1199_Ord_A.tif", FALHA, "Ficheiro TIFF ilegível."));

        assertThat(json.get("verificacao").asText()).isEqualTo("GEORREFERENCIACAO_MATRICIAL");
        assertThat(json.get("resultado").asText()).isEqualTo("FALHA");
        assertThat(json.get("documentoId").asText()).isEqualTo(documentoId.toString());
        assertThat(json.get("nomeFicheiro").asText()).isEqualTo("1199_Ord_A.tif");
        assertThat(json.get("detalhe").asText()).isEqualTo("Ficheiro TIFF ilegível.");
        assertThat(new ObjectMapper().valueToTree(new VerificacaoDocumentalResponse(CORRESPONDENCIA_ATO_TEXTO, null,
                null, MANUAL, ChecklistValidacaoService.MSG_ATO_TEXTO)).get("resultado").asText()).isEqualTo("MANUAL");
    }

    private ProcessoDocumento documento(String nome, CategoriaDocumento categoria, TipoElemento tipoElemento) {
        ProcessoDocumento documento = mock(ProcessoDocumento.class);
        UUID id = UUID.randomUUID();
        when(documento.getId()).thenReturn(id);
        when(documento.getNomeFicheiro()).thenReturn(nome);
        when(documento.getCategoria()).thenReturn(categoria);
        when(documento.getEstadoValidacao()).thenReturn(EstadoValidacao.POR_VALIDAR);
        when(documento.getCaminhoFicheiro()).thenReturn("processos/2026-0001/" + categoria + "/" + id + "-" + nome);
        when(documento.getVersaoObjeto()).thenReturn("v-" + id);
        when(documento.getTamanhoBytes()).thenReturn(1234L);
        if (tipoElemento != null) {
            DocumentoConfig item = mock(DocumentoConfig.class);
            when(item.getTipoElemento()).thenReturn(tipoElemento);
            when(documento.getDocumentoConfig()).thenReturn(item);
        }
        documentos.add(documento);
        return documento;
    }

    private static CaracteristicasTiff tiff(Integer codigoEpsg, boolean georreferenciado) {
        return new CaracteristicasTiff("GTiff", codigoEpsg, "NONE", RGB, 300.0, 300.0, 2, georreferenciado);
    }

    private void zipAceite(ProcessoDocumento zip, String... entradas) {
        when(verificadorZip.verificar(eq(zip.getId()), anyString(), any(), anyLong())).thenReturn(List.of(entradas));
    }

    private void zipRecusado(ProcessoDocumento zip, String recusadas) {
        when(verificadorZip.verificar(eq(zip.getId()), anyString(), any(), anyLong()))
                .thenThrow(new ZipInvalidoException(recusadas));
    }

    private static List<VerificacaoDocumentalResponse> linhasDe(List<VerificacaoDocumentalResponse> linhas,
                                                                VerificacaoChecklist verificacao) {
        return linhas.stream().filter(linha -> linha.verificacao() == verificacao).toList();
    }
}
