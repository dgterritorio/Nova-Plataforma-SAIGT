package com.conkord.saigt.service.snit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.snit.DepositoSnit;
import com.conkord.saigt.integracao.snit.FicheiroDepositoSnit;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.integracao.snit.PublicacaoSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MontadorPedidoSnitTest {

    private static final UUID INSTRUMENTO_ID = UUID.randomUUID();
    private static final UUID PROCESSO_ID = UUID.randomUUID();
    private static final String URL_BASE = "https://saigt.exemplo.pt";
    private static final Instant AGORA = Instant.parse("2026-11-10T10:00:00Z");

    private final InstrumentoConcelhoRepository concelhoRepository = mock(InstrumentoConcelhoRepository.class);
    private final SnitReferenciaRepository referenciaRepository = mock(SnitReferenciaRepository.class);
    private final IdusProcessoRepository idusRepository = mock(IdusProcessoRepository.class);
    private final ProcessoDocumentoRepository documentoRepository = mock(ProcessoDocumentoRepository.class);
    private final MontadorPedidoSnit montador = new MontadorPedidoSnit(concelhoRepository,
            new ReferenciaSnitConsultaService(referenciaRepository, new ObjectMapper()), idusRepository,
            documentoRepository, new DepositoSnitProperties(Set.of(CategoriaDocumento.DEPOSITO)), URL_BASE + "/");

    private final Processo processo = mock(Processo.class);
    private final Instrumento instrumento = mock(Instrumento.class);
    private final TipoInstrumento tipoInstrumento = mock(TipoInstrumento.class);
    private final TipoDinamica tipoDinamica = mock(TipoDinamica.class);
    private final InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MontadorPedidoSnit.class);

    @BeforeEach
    void processoPublicadoDeUmPdmDeSintraNaElaboracao() {
        logs.start();
        logger.addAppender(logs);

        Dinamica dinamica = mock(Dinamica.class);
        InstrumentoDinamicaConfig dinamicaConfig = mock(InstrumentoDinamicaConfig.class);
        TipoAto tipoAto = mock(TipoAto.class);
        when(instrumento.getId()).thenReturn(INSTRUMENTO_ID);
        when(instrumento.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(instrumento.getDesignacao()).thenReturn("Plano Diretor Municipal de Sintra");
        when(tipoInstrumento.getSnitCodigo()).thenReturn("6");
        when(tipoDinamica.getSnitCodigo()).thenReturn("1");
        when(dinamicaConfig.getTipoDinamica()).thenReturn(tipoDinamica);
        when(dinamica.getInstrumento()).thenReturn(instrumento);
        when(dinamica.getInstrumentoDinamicaConfig()).thenReturn(dinamicaConfig);
        when(procedimento.getSerieDr()).thenReturn("SEGUNDA_H");
        when(tipoAto.getNome()).thenReturn("Aviso");
        when(processo.getId()).thenReturn(PROCESSO_ID);
        when(processo.getNumeroProcesso()).thenReturn("00042");
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        when(processo.serieDrEfetiva()).thenCallRealMethod();
        when(processo.getTipoAto()).thenReturn(tipoAto);
        when(processo.getSumario()).thenReturn("Elaboração do PDM de Sintra");
        when(processo.getNumeroDr()).thenReturn("206");
        when(processo.getDataPublicacaoDr()).thenReturn(LocalDate.of(2026, 10, 24));
        when(processo.getLinkDr()).thenReturn("https://diariodarepublica.pt/dr/detalhe/aviso/1-2026");
        when(processo.getNumeroDeposito()).thenReturn("03.11.11/PDM/01/2026/1");
        concelhos("1111");
        referencia(ListaReferenciaSnit.INSTRUMENT_TYPES, "6", "{\"id\": 6, \"classId\": 3}");
        referencia(ListaReferenciaSnit.MUNICIPALITIES, "1111", "{\"dtcc\": \"1111\", \"nut2Code\": \"PT1B\"}");
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private void concelhos(String... dtccs) {
        List<InstrumentoConcelho> lista = Arrays.stream(dtccs).map(dtcc -> new InstrumentoConcelho(instrumento, dtcc))
                .toList();
        when(concelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(INSTRUMENTO_ID)).thenReturn(lista);
    }

    private void referencia(ListaReferenciaSnit lista, String codigo, String dadosOrigem) {
        when(referenciaRepository.findByListaAndCodigo(lista, codigo))
                .thenReturn(Optional.of(new SnitReferencia(lista, codigo, codigo, dadosOrigem, AGORA)));
    }

    @Test
    void deveMontarAPublicacaoComOsDadosDoProcessoDoInstrumentoEDaReplicaDoSnit() {
        PublicacaoSnit pedido = montador.montarPublicacao(processo).orElseThrow();

        assertThat(pedido.ssaigtId()).isEqualTo(42L);
        assertThat(pedido.typeId()).isEqualTo(6);
        assertThat(pedido.procedureId()).isEqualTo(1);
        assertThat(pedido.classId()).isEqualTo(3);
        assertThat(pedido.nut2Code()).isEqualTo("PT1B");
        assertThat(pedido.municipalityDtcc()).isEqualTo("1111");
        assertThat(pedido.designation()).isEqualTo("Plano Diretor Municipal de Sintra");
        assertThat(pedido.summary()).isEqualTo("Elaboração do PDM de Sintra");
        assertThat(pedido.publicationStatus()).isEqualTo("00");
        assertThat(pedido.verification()).isNull();
        assertThat(pedido.publication().diploma()).isEqualTo("Aviso");
        assertThat(pedido.publication().series()).isEqualTo("2");
        assertThat(pedido.publication().number()).isEqualTo("206");
        assertThat(pedido.publication().publicationDate()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(pedido.publication().hyperlink()).isEqualTo("https://diariodarepublica.pt/dr/detalhe/aviso/1-2026");
    }

    @Test
    void numProcedimentoSemSerieUsaASerieEscolhidaNoProcesso() {
        when(procedimento.getSerieDr()).thenReturn(null);
        when(processo.getSerieDr()).thenReturn("PRIMEIRA");
        assertThat(montador.montarPublicacao(processo).orElseThrow().publication().series()).isEqualTo("1");

        when(processo.getSerieDr()).thenReturn("SEGUNDA_C");
        assertThat(montador.montarPublicacao(processo).orElseThrow().publication().series()).isEqualTo("2");
    }

    @Test
    void deveUsarOProcedimentoDoisNumaAlteracao() {
        when(tipoDinamica.getSnitCodigo()).thenReturn("2");

        assertThat(montador.montarPublicacao(processo).orElseThrow().procedureId()).isEqualTo(2);
    }

    @Test
    void deveMapearAPrimeiraSerieParaUmEOmitirOSumarioVazio() {
        when(procedimento.getSerieDr()).thenReturn("PRIMEIRA");
        when(processo.getSumario()).thenReturn(" ");

        PublicacaoSnit pedido = montador.montarPublicacao(processo).orElseThrow();

        assertThat(pedido.publication().series()).isEqualTo("1");
        assertThat(pedido.summary()).isNull();
    }

    @Test
    void deveCortarOSumarioAosTrezentosCaracteresDoContrato() {
        when(processo.getSumario()).thenReturn("Alteração ".repeat(40));

        String sumario = montador.montarPublicacao(processo).orElseThrow().summary();

        assertThat(sumario).hasSizeLessThanOrEqualTo(300).startsWith("Alteração Alteração").doesNotEndWith(" ");
        assertThat(sumario.length()).isGreaterThanOrEqualTo(299);
    }

    @Test
    void naoDeveCortarUmSumarioComExatamenteTrezentosCaracteres() {
        String trezentos = "a".repeat(300);
        when(processo.getSumario()).thenReturn(trezentos);

        assertThat(montador.montarPublicacao(processo).orElseThrow().summary()).isEqualTo(trezentos);
    }

    @Test
    void naoDeveMontarAPublicacaoSemSnitCodigoDoTipoDeInstrumento() {
        when(tipoInstrumento.getSnitCodigo()).thenReturn(null);

        assertThat(montador.montarPublicacao(processo)).isEmpty();
        assertThat(aviso()).contains("tipo_instrumento.snit_codigo").contains("processoId=" + PROCESSO_ID);
    }

    @Test
    void naoDeveMontarAPublicacaoSemClassIdNaReplica() {
        when(referenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.INSTRUMENT_TYPES, "6"))
                .thenReturn(Optional.empty());

        assertThat(montador.montarPublicacao(processo)).isEmpty();
        assertThat(aviso()).contains("classId");
    }

    @Test
    void naoDeveMontarAPublicacaoSemNut2DoConcelhoNaReplica() {
        referencia(ListaReferenciaSnit.MUNICIPALITIES, "1111", "{\"dtcc\": \"1111\"}");

        assertThat(montador.montarPublicacao(processo)).isEmpty();
        assertThat(aviso()).contains("nut2Code");
    }

    @Test
    void naoDeveMontarAPublicacaoDeUmInstrumentoComVariosConcelhos() {
        concelhos("1105", "1111");

        assertThat(montador.montarPublicacao(processo)).isEmpty();
        assertThat(aviso()).contains("2 concelhos");
    }

    @Test
    void naoDeveMontarAPublicacaoSemTipoDeAto() {
        when(processo.getTipoAto()).thenReturn(null);

        assertThat(montador.montarPublicacao(processo)).isEmpty();
        assertThat(aviso()).contains("processo.tipo_ato");
    }

    @Test
    void deveMontarODepositoComONumeroOIdusEALigacaoDaPastaDeDeposito() {
        IdusProcesso idus = mock(IdusProcesso.class);
        when(idus.getIdus()).thenReturn("PDM1111-0001-01-031-01");
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(idus));

        DepositoSnit deposito = montador.montarDeposito(processo, AGORA).orElseThrow();

        assertThat(deposito.ssaigtId()).isEqualTo(42L);
        assertThat(deposito.depositNumber()).isEqualTo("03.11.11/PDM/01/2026/1");
        assertThat(deposito.depositedAt()).isEqualTo(AGORA);
        assertThat(deposito.idus()).isEqualTo("PDM1111-0001-01-031-01");
        assertThat(deposito.downloadUrl()).isEqualTo(URL_BASE + "/api/v1/integracoes/snit/depositos/00042");
    }

    @Test
    void deveOmitirOIdusQuandoOProcessoNaoTem() {
        when(idusRepository.findById(PROCESSO_ID)).thenReturn(Optional.empty());

        assertThat(montador.montarDeposito(processo, AGORA).orElseThrow().idus()).isNull();
    }

    @Test
    void naoDeveMontarODepositoSemNumeroDeDeposito() {
        when(processo.getNumeroDeposito()).thenReturn(null);

        assertThat(montador.montarDeposito(processo, AGORA)).isEmpty();
        assertThat(aviso()).contains("processo.numero_deposito");
    }

    @Test
    void deveComunicarSoOsZipAtuaisDePecasGraficasDasCaixasDoDeposito() {
        ProcessoDocumento zip1 = documento("Planta_Ordenamento.zip", "zip", TipoElemento.PECA_GRAFICA,
                CategoriaDocumento.DEPOSITO, "processos/00042/DEPOSITO/a-Planta_Ordenamento.zip");
        ProcessoDocumento zip2 = documento("Planta_Condicionantes.ZIP", "ZIP", TipoElemento.PECA_GRAFICA,
                CategoriaDocumento.DEPOSITO, "processos/00042/DEPOSITO/b-Planta_Condicionantes.ZIP");
        ProcessoDocumento pdf = documento("Regulamento.pdf", "pdf", TipoElemento.PECA_ESCRITA,
                CategoriaDocumento.DEPOSITO, "processos/00042/DEPOSITO/c-Regulamento.pdf");
        ProcessoDocumento substituido = documento("Planta_Antiga.zip", "zip", TipoElemento.PECA_GRAFICA,
                CategoriaDocumento.DEPOSITO, "processos/00042/SUBSTITUIDOS/d-Planta_Antiga.zip");
        ProcessoDocumento daPublicacao = documento("Planta_Publicacao.zip", "zip", TipoElemento.PECA_GRAFICA,
                CategoriaDocumento.PUBLICACAO, "processos/00042/PUBLICACAO/e-Planta_Publicacao.zip");
        when(documentoRepository.findByProcesso_IdOrderByCriadoEmAsc(PROCESSO_ID))
                .thenReturn(List.of(zip1, pdf, substituido, zip2, daPublicacao));

        List<FicheiroDepositoSnit> ficheiros = montador.montarFicheiros(processo);

        assertThat(ficheiros).extracting(FicheiroDepositoSnit::fileId)
                .containsExactly(zip1.getId().toString(), zip2.getId().toString());
        FicheiroDepositoSnit primeiro = ficheiros.get(0);
        assertThat(primeiro.fileName()).isEqualTo("Planta_Ordenamento.zip");
        assertThat(primeiro.downloadUrl())
                .isEqualTo(URL_BASE + "/api/v1/integracoes/snit/depositos/00042/ficheiros/" + zip1.getId());
        assertThat(primeiro.isGraphicPiece()).isTrue();
        assertThat(primeiro.createdAt()).isEqualTo(AGORA);
    }

    @Test
    void deveDevolverListaVaziaSemZipDePecasGraficas() {
        ProcessoDocumento pdf = documento("Regulamento.pdf", "pdf", TipoElemento.PECA_ESCRITA,
                CategoriaDocumento.DEPOSITO, "processos/00042/DEPOSITO/c-Regulamento.pdf");
        when(documentoRepository.findByProcesso_IdOrderByCriadoEmAsc(PROCESSO_ID)).thenReturn(List.of(pdf));

        assertThat(montador.montarFicheiros(processo)).isEmpty();
    }

    private static ProcessoDocumento documento(String nome, String extensao, TipoElemento tipoElemento,
                                               CategoriaDocumento categoria, String caminho) {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getTipoElemento()).thenReturn(tipoElemento);
        ProcessoDocumento documento = mock(ProcessoDocumento.class);
        when(documento.getId()).thenReturn(UUID.randomUUID());
        when(documento.getNomeFicheiro()).thenReturn(nome);
        when(documento.getExtensao()).thenReturn(extensao);
        when(documento.getCategoria()).thenReturn(categoria);
        when(documento.getCaminhoFicheiro()).thenReturn(caminho);
        when(documento.getDocumentoConfig()).thenReturn(item);
        when(documento.getCriadoEm()).thenReturn(AGORA);
        return documento;
    }

    @Test
    void naoDeveMontarARegistoDeUmaDinamicaIniciadaSemIgt() {
        when(processo.getDinamica().getInstrumento()).thenReturn(null);

        assertThat(montador.montarPublicacao(processo)).isEmpty();
        assertThat(aviso()).contains("Dinâmica iniciada sem IGT").contains(PROCESSO_ID.toString());
    }

    private String aviso() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage)
                .findFirst().orElseThrow();
    }
}
