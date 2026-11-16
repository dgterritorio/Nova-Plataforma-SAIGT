package com.conkord.saigt.service.documento;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.exception.TiffInvalidoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.CompressaoTiff;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.ModeloCorTiff;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.service.documento.CaracteristicasTiff.BandaTiff;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificadorTiffTest {

    private static final RegrasTiff GERAL = RegrasTiff.inicialGeral();
    private static final RegrasTiff SGIFR = RegrasTiff.inicialSgifr();
    private static final UUID PROCESSO_ID = UUID.fromString("7a1e0c4e-1111-4b8e-9d5e-000000000241");
    private static final String CHAVE = "processos/2026-000241/DEPOSITO/0b7d-plantas.zip";
    private static final BandaTiff R = new BandaTiff("Byte", "Red");
    private static final BandaTiff G = new BandaTiff("Byte", "Green");
    private static final BandaTiff B = new BandaTiff("Byte", "Blue");
    private static final BandaTiff A = new BandaTiff("Byte", "Alpha");

    private final InspetorTiff inspetor = mock(InspetorTiff.class);
    private final VerificadorTiff verificador = new VerificadorTiff(inspetor);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(VerificadorTiff.class);

    @BeforeEach
    void capturarLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void largarLogs() {
        logger.detachAppender(logs);
    }

    /** As amostras 1303_PO_I_0993/0994: LZW, RGB com alfa, 300 dpi, EPSG:3763. */
    private static CaracteristicasTiff amostra() {
        return new CaracteristicasTiff("GTiff", 3763, "LZW", List.of(R, G, B, A), 300.0, 300.0, 2, true);
    }

    /** Cumpre as duas configurações iniciais. */
    private static CaracteristicasTiff conforme() {
        return new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), 300.0, 300.0, 2, true);
    }

    @Test
    void deveAceitarAsAmostrasComAConfiguracaoInicialGeral() {
        assertThat(VerificadorTiff.motivos(amostra(), GERAL)).isEmpty();
        assertThat(VerificadorTiff.motivos(conforme(), GERAL)).isEmpty();
    }

    @Test
    void deveRecusarAsAmostrasNoSgifrPelaCompressaoEPelaBandaAlfa() {
        assertThat(VerificadorTiff.motivos(amostra(), SGIFR))
                .containsExactly("compressão LZW não aceite", "bandas adicionais não aceites");
        assertThat(VerificadorTiff.motivos(conforme(), SGIFR)).isEmpty();
    }

    @Test
    void deveRecusarComoIlegivelOQueNaoEGeoTiff() {
        CaracteristicasTiff png = new CaracteristicasTiff("PNG", null, "NONE", List.of(R, G, B), null, null, null, false);

        assertThat(VerificadorTiff.motivos(png, GERAL)).containsExactly("ficheiro TIFF ilegível");
        assertThat(VerificadorTiff.motivos(null, SGIFR)).containsExactly("ficheiro TIFF ilegível");
    }

    @Test
    void deveAceitarUmTifSimplesComTfwSemSistemaDeReferenciaNasDuasConfiguracoes() {
        CaracteristicasTiff tifComTfw = new CaracteristicasTiff("GTiff", null, "NONE", List.of(R, G, B), 300.0,
                300.0, 2, true);

        assertThat(VerificadorTiff.motivos(tifComTfw, GERAL)).isEmpty();
        assertThat(VerificadorTiff.motivos(tifComTfw, SGIFR)).isEmpty();
    }

    @Test
    void deveRecusarSemGeorreferenciacaoUmTifSemSistemaDeReferenciaNemTfw() {
        CaracteristicasTiff semCrs = new CaracteristicasTiff("GTiff", null, "NONE", List.of(R, G, B), null, null, null,
                false);
        CaracteristicasTiff semGeoTransform = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), null,
                null, null, false);

        assertThat(VerificadorTiff.motivos(semCrs, GERAL))
                .containsExactly("sem georreferenciação: falta o sistema de referência no TIFF ou o ficheiro TFW");
        assertThat(VerificadorTiff.motivos(semGeoTransform, SGIFR))
                .contains("sem georreferenciação: falta o sistema de referência no TIFF ou o ficheiro TFW");
        assertThat(VerificadorTiff.motivos(semCrs, regras(List.of(), null, null))).isEmpty();
    }

    @Test
    void deveRecusarUmTifComTfwComCompressaoDeflateSoPelaCompressao() {
        CaracteristicasTiff tifComTfw = new CaracteristicasTiff("GTiff", null, "DEFLATE", List.of(R, G, B), null, null,
                null, true);

        assertThat(VerificadorTiff.motivos(tifComTfw, GERAL)).containsExactly("compressão DEFLATE não aceite");
    }

    @Test
    void deveRecusarUmSistemaDeReferenciaForaDaLista() {
        CaracteristicasTiff wgs84 = new CaracteristicasTiff("GTiff", 4326, "NONE", List.of(R, G, B), null, null, null,
                true);

        assertThat(VerificadorTiff.motivos(wgs84, GERAL))
                .containsExactly("sistema de referência EPSG:4326 não aceite");
        assertThat(VerificadorTiff.motivos(wgs84, SGIFR)).contains("sistema de referência EPSG:4326 não aceite");
        for (int epsg : new int[]{3763, 5014, 5015, 5016}) {
            assertThat(VerificadorTiff.motivos(new CaracteristicasTiff("GTiff", epsg, "NONE", List.of(R, G, B), null,
                    null, null, true), GERAL)).as("EPSG:" + epsg).isEmpty();
        }
    }

    @Test
    void deveRecusarUmaCompressaoForaDasAceites() {
        CaracteristicasTiff deflate = new CaracteristicasTiff("GTiff", 3763, "DEFLATE", List.of(R, G, B), null, null,
                null, true);

        assertThat(VerificadorTiff.motivos(deflate, GERAL)).containsExactly("compressão DEFLATE não aceite");
        assertThat(VerificadorTiff.motivos(deflate, SGIFR)).contains("compressão DEFLATE não aceite");
    }

    @Test
    void deveExigirRgbComAsTresPrimeirasBandasQuandoOModeloECor() {
        CaracteristicasTiff cinzento = new CaracteristicasTiff("GTiff", 3763, "NONE",
                List.of(new BandaTiff("Byte", "Gray")), 300.0, 300.0, 2, true);
        CaracteristicasTiff bgr = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(B, G, R), 300.0, 300.0, 2,
                true);

        assertThat(VerificadorTiff.motivos(cinzento, SGIFR)).containsExactly("modelo de cor diferente de RGB");
        assertThat(VerificadorTiff.motivos(bgr, SGIFR)).containsExactly("modelo de cor diferente de RGB");
        assertThat(VerificadorTiff.motivos(cinzento, GERAL)).isEmpty();
    }

    @Test
    void deveAceitarSoUmaQuartaBandaAlfaETolerada() {
        RegrasTiff rgbComAlfa = new RegrasTiff(List.of(), List.of(CompressaoTiff.NONE), true, ModeloCorTiff.RGB, null,
                null, List.of());
        CaracteristicasTiff rgba = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B, A), null, null,
                null, true);
        CaracteristicasTiff cincoBandas = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B, A, A), null,
                null, null, true);
        CaracteristicasTiff rgbMaisCinzento = new CaracteristicasTiff("GTiff", 3763, "NONE",
                List.of(R, G, B, new BandaTiff("Byte", "Gray")), null, null, null, true);

        assertThat(VerificadorTiff.motivos(rgba, rgbComAlfa)).isEmpty();
        assertThat(VerificadorTiff.motivos(cincoBandas, rgbComAlfa)).containsExactly("bandas adicionais não aceites");
        assertThat(VerificadorTiff.motivos(rgbMaisCinzento, rgbComAlfa))
                .containsExactly("bandas adicionais não aceites");
    }

    @Test
    void deveVerificarSoABandaAlfaSemModeloDeCor() {
        RegrasTiff semAlfa = new RegrasTiff(List.of(), List.of(CompressaoTiff.NONE), false, null, null, null, List.of());
        CaracteristicasTiff rgba = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B, A), null, null,
                null, true);

        assertThat(VerificadorTiff.motivos(rgba, semAlfa)).containsExactly("banda alfa não aceite");
        assertThat(VerificadorTiff.motivos(rgba, GERAL)).isEmpty();
    }

    @Test
    void deveExigirOsBitsPorBandaQuandoPreenchidos() {
        BandaTiff r16 = new BandaTiff("UInt16", "Red");
        BandaTiff g16 = new BandaTiff("UInt16", "Green");
        BandaTiff b16 = new BandaTiff("UInt16", "Blue");
        CaracteristicasTiff dezasseis = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(r16, g16, b16), 300.0,
                300.0, 2, true);
        RegrasTiff dezasseisBits = new RegrasTiff(List.of(), List.of(CompressaoTiff.NONE), false, null, 16, null,
                List.of());

        assertThat(VerificadorTiff.motivos(dezasseis, SGIFR)).containsExactly("16 bits por banda em vez de 8");
        assertThat(VerificadorTiff.motivos(conforme(), dezasseisBits)).containsExactly("8 bits por banda em vez de 16");
        assertThat(VerificadorTiff.motivos(dezasseis, dezasseisBits)).isEmpty();
        assertThat(VerificadorTiff.motivos(dezasseis, GERAL)).isEmpty();
    }

    @Test
    void deveExigirAResolucaoEmDpiQuandoPreenchida() {
        CaracteristicasTiff a150 = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), 150.0, 150.0, 2,
                true);
        CaracteristicasTiff emCentimetros = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), 118.11,
                118.11, 3, true);
        CaracteristicasTiff dentroDaTolerancia = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), 300.4,
                299.6, 2, true);
        CaracteristicasTiff semEtiquetas = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), null, null,
                null, true);
        CaracteristicasTiff semUnidadeAbsoluta = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), 300.0,
                300.0, 1, true);
        CaracteristicasTiff decimal = new CaracteristicasTiff("GTiff", 3763, "NONE", List.of(R, G, B), 72.5, 72.5, 2,
                true);

        assertThat(VerificadorTiff.motivos(a150, SGIFR)).containsExactly("resolução de 150 dpi em vez de 300");
        assertThat(VerificadorTiff.motivos(a150, GERAL)).isEmpty();
        assertThat(VerificadorTiff.motivos(emCentimetros, SGIFR)).isEmpty();
        assertThat(VerificadorTiff.motivos(dentroDaTolerancia, SGIFR)).isEmpty();
        assertThat(VerificadorTiff.motivos(semEtiquetas, SGIFR)).containsExactly("resolução não indicada");
        assertThat(VerificadorTiff.motivos(semUnidadeAbsoluta, SGIFR)).containsExactly("resolução não indicada");
        assertThat(VerificadorTiff.motivos(decimal, SGIFR)).containsExactly("resolução de 72,5 dpi em vez de 300");
    }

    @Test
    void deveJuntarTodosOsMotivosDeUmTiffComVariosIncumprimentos() {
        CaracteristicasTiff varios = new CaracteristicasTiff("GTiff", 4326, "DEFLATE",
                List.of(new BandaTiff("UInt16", "Red"), new BandaTiff("UInt16", "Green"),
                        new BandaTiff("UInt16", "Blue"), new BandaTiff("UInt16", "Alpha")), 150.0, 150.0, 2, true);

        assertThat(VerificadorTiff.motivos(varios, SGIFR)).containsExactly(
                "sistema de referência EPSG:4326 não aceite",
                "compressão DEFLATE não aceite",
                "bandas adicionais não aceites",
                "16 bits por banda em vez de 8",
                "resolução de 150 dpi em vez de 300");
    }

    @Test
    void deveFalharUmaSoVezComOsMotivosDeTodosOsTiffDoZipSemRepetir() {
        when(inspetor.inspecionar(CHAVE, "folha_1.tif")).thenReturn(amostra());
        when(inspetor.inspecionar(CHAVE, "FOLHA_2.TIFF")).thenReturn(amostra());
        when(inspetor.inspecionar(CHAVE, "folha_3.tif")).thenThrow(new TiffIlegivelException());

        assertThatThrownBy(() -> verificador.verificar(PROCESSO_ID, CHAVE,
                List.of("folha_1.tif", "folha_1.tfw", "legenda.pdf", "FOLHA_2.TIFF", "folha_3.tif"), SGIFR))
                .isInstanceOfSatisfying(TiffInvalidoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("DOC_TIFF_INVALIDO");
                    assertThat(e.getCampos()).containsExactly(new ValidacaoException.CampoErro("ficheiro",
                            "A peça gráfica não cumpre a especificação TIFF: compressão LZW não aceite; "
                                    + "bandas adicionais não aceites; ficheiro TIFF ilegível."));
                });
        verify(inspetor, never()).inspecionar(anyString(), eq("folha_1.tfw"));
        verify(inspetor, never()).inspecionar(anyString(), eq("legenda.pdf"));
    }

    @Test
    void deveAceitarUmZipSemTiffSemInspecionar() {
        verificador.verificar(PROCESSO_ID, CHAVE, List.of("ordenamento.gpkg", "legenda.pdf"), SGIFR);

        verify(inspetor, never()).inspecionar(any(), any());
    }

    @Test
    void deveDeixarSairAFalhaTecnicaComLogErrorSemChaveNemNomeDoFicheiro() {
        when(inspetor.inspecionar(CHAVE, "folha_1.tif"))
                .thenThrow(new InspecaoTiffIndisponivelException("gdalinfo excedeu o tempo máximo de PT60S"));

        assertThatThrownBy(() -> verificador.verificar(PROCESSO_ID, CHAVE, List.of("folha_1.tif"), GERAL))
                .isInstanceOf(InspecaoTiffIndisponivelException.class);

        assertThat(logs.list).singleElement().satisfies(evento -> {
            assertThat(evento.getLevel().toString()).isEqualTo("ERROR");
            assertThat(evento.getFormattedMessage()).contains(PROCESSO_ID.toString()).contains("extensao=tif")
                    .contains("tempo máximo");
        });
        assertSemChaveNemNomeNosLogs();
    }

    @Test
    void deveRegistarCadaInspecaoComOResultadoSemChaveNemNomeDoFicheiro() {
        when(inspetor.inspecionar(CHAVE, "folha_1.tif")).thenReturn(amostra());
        when(inspetor.inspecionar(CHAVE, "folha_2.tif")).thenReturn(conforme());

        assertThatThrownBy(() -> verificador.verificar(PROCESSO_ID, CHAVE, List.of("folha_1.tif", "folha_2.tif"),
                SGIFR)).isInstanceOf(TiffInvalidoException.class);

        assertThat(logs.list).hasSize(2).allSatisfy(evento -> assertThat(evento.getLevel().toString()).isEqualTo("INFO"));
        assertThat(logs.list.get(0).getFormattedMessage()).contains("processoId=" + PROCESSO_ID)
                .contains("resultado=RECUSADO").contains("motivos=2").contains("duracaoMs=");
        assertThat(logs.list.get(1).getFormattedMessage()).contains("resultado=ACEITE").contains("motivos=0");
        assertSemChaveNemNomeNosLogs();
    }

    @Test
    void deveAplicarSeSoNaCaixaDeDepositoDeUmItemDePecasGraficas() {
        DocumentoConfig pecaGrafica = item(CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA);
        DocumentoConfig pecaGraficaPublicacao = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA);
        DocumentoConfig pecaEscrita = item(CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA);

        assertThat(VerificadorTiff.regrasAplicaveis(CategoriaDocumento.DEPOSITO, pecaGrafica)).contains(GERAL);
        assertThat(VerificadorTiff.regrasAplicaveis(CategoriaDocumento.PUBLICACAO, pecaGraficaPublicacao)).isEmpty();
        assertThat(VerificadorTiff.regrasAplicaveis(CategoriaDocumento.DEPOSITO, pecaEscrita)).isEmpty();
        assertThat(VerificadorTiff.regrasAplicaveis(CategoriaDocumento.DEPOSITO, null)).isEmpty();
    }

    @Test
    void deveReconhecerTifETiffSemDistinguirMaiusculas() {
        assertThat(List.of("a.tif", "B.TIF", "c.tiff", "D.Tiff")).allMatch(VerificadorTiff::ehTiff);
        assertThat(List.of("a.tfw", "b.tif.zip", "c", "tif")).noneMatch(VerificadorTiff::ehTiff);
    }

    private void assertSemChaveNemNomeNosLogs() {
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).allSatisfy(mensagem ->
                assertThat(mensagem).doesNotContain(CHAVE).doesNotContain("folha_").doesNotContain("FOLHA_"));
    }

    private static RegrasTiff regras(List<String> sistemas, ModeloCorTiff modeloCor, Integer resolucaoDpi) {
        return new RegrasTiff(sistemas, List.of(CompressaoTiff.NONE, CompressaoTiff.LZW), true, modeloCor, null,
                resolucaoDpi, List.of());
    }

    private static DocumentoConfig item(CategoriaDocumento categoria, TipoElemento tipoElemento) {
        return new DocumentoConfig(null, "PLANTAS", "Plantas", "Plantas", categoria, tipoElemento,
                FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", 1);
    }
}
