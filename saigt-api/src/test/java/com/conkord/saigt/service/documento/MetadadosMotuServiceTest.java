package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.MetadadosMotuResponse.Registo;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadadosMotuServiceTest {

    private final MetadadosMotuRepository metadadosMotuRepository = mock(MetadadosMotuRepository.class);
    private final PlantaRepository plantaRepository = mock(PlantaRepository.class);
    private final InstrumentoProcedimentoConfigRepository procedimentoConfigRepository =
            mock(InstrumentoProcedimentoConfigRepository.class);
    private final DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
    private final MetadadosMotuService service = new MetadadosMotuService(metadadosMotuRepository, plantaRepository,
            procedimentoConfigRepository, documentoConfigRepository, mock(ProcessoRepository.class),
            mock(ProcessoDocumentoRepository.class), mock(AutorizacaoService.class), mock(ContextoUtilizadorResolver.class), mock(ProcessoAlteravel.class),
            new ObjectMapper(), mock(Validator.class));

    private final InstrumentoDinamicaConfig dinamicaConfig = mock(InstrumentoDinamicaConfig.class);
    private final InstrumentoProcedimentoConfig decisaoInicio = procedimento(1L);
    private final InstrumentoProcedimentoConfig publicacao = procedimento(2L);
    private final UUID processoId = UUID.randomUUID();

    private Processo processo;

    @BeforeEach
    void configurarDinamica() {
        when(procedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(
                dinamicaConfig)).thenReturn(Optional.of(publicacao));
        when(metadadosMotuRepository.save(any(MetadadosMotu.class))).thenAnswer(invocacao -> {
            MetadadosMotu ficha = invocacao.getArgument(0);
            ReflectionTestUtils.setField(ficha, "id", UUID.randomUUID());
            return ficha;
        });
        processo = processo(publicacao);
    }

    private InstrumentoProcedimentoConfig procedimento(long id) {
        InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);
        when(procedimento.getId()).thenReturn(id);
        when(procedimento.getInstrumentoDinamicaConfig()).thenReturn(dinamicaConfig);
        return procedimento;
    }

    private Processo processo(InstrumentoProcedimentoConfig procedimento) {
        Processo novo = mock(Processo.class);
        when(novo.getId()).thenReturn(processoId);
        when(novo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        return novo;
    }

    private static DocumentoConfig item(TipoElemento tipoElemento, CategoriaDocumento categoria) {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getTipoElemento()).thenReturn(tipoElemento);
        when(item.getCategoria()).thenReturn(categoria);
        return item;
    }

    private void itensDoProcedimento(InstrumentoProcedimentoConfig procedimento, DocumentoConfig... itens) {
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(procedimento))
                .thenReturn(List.of(itens));
    }

    private ProcessoDocumento documento(Processo dono, DocumentoConfig item) {
        return new ProcessoDocumento(UUID.randomUUID(), dono, item, item == null ? CategoriaDocumento.DEPOSITO
                : item.getCategoria(), "zip", "planta.zip", "privado/x", "hash", "v1", 10L, null);
    }

    private List<MetadadosMotu> gravadas() {
        ArgumentCaptor<MetadadosMotu> captor = ArgumentCaptor.forClass(MetadadosMotu.class);
        verify(metadadosMotuRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private void comSerie() {
        when(metadadosMotuRepository.findByProcesso_IdAndTipo(processoId, TipoMetadadosMotu.SERIE))
                .thenReturn(Optional.of(MetadadosMotu.serie(processo)));
    }

    @Test
    void criaASerieNoProcedimentoFinalComPecaGraficaDePublicacao() {
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_ESCRITA, CategoriaDocumento.PUBLICACAO),
                item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));

        service.criarSerie(processo);

        assertThat(gravadas()).singleElement().satisfies(serie -> {
            assertThat(serie.getTipo()).isEqualTo(TipoMetadadosMotu.SERIE);
            assertThat(serie.getProcesso()).isSameAs(processo);
            assertThat(serie.getProcessoDocumento()).isNull();
            assertThat(serie.isOpcional()).isFalse();
            assertThat(serie.isCompleto()).isFalse();
        });
    }

    @Test
    void naoCriaASerieNoProcedimentoFinalSemPecaGraficaDePublicacao() {
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.DEPOSITO),
                item(TipoElemento.PECA_ESCRITA, CategoriaDocumento.PUBLICACAO));

        service.criarSerie(processo);

        verify(metadadosMotuRepository, never()).save(any());
    }

    @Test
    void naoCriaASerieNumProcedimentoQueNaoEOFinalMesmoComPecaGraficaDePublicacao() {
        itensDoProcedimento(decisaoInicio, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));

        service.criarSerie(processo(decisaoInicio));

        verify(metadadosMotuRepository, never()).save(any());
    }

    @Test
    void naoCriaASerieQuandoADinamicaNaoTemProcedimentoFinal() {
        when(procedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(
                dinamicaConfig)).thenReturn(Optional.empty());
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));

        service.criarSerie(processo);

        verify(metadadosMotuRepository, never()).save(any());
    }

    @Test
    void criaOConjuntoReaproveitandoASerieELigaTodasAsPlantasDoDocumento() {
        DocumentoConfig item = item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO);
        itensDoProcedimento(publicacao, item);
        comSerie();
        ProcessoDocumento documento = documento(processo, item);
        Planta folha1 = new Planta(processo, documento);
        Planta folha2 = new Planta(processo, documento);
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of(folha1, folha2));

        service.registarPecaGrafica(documento);

        MetadadosMotu conjunto = gravadas().getFirst();
        verify(metadadosMotuRepository, times(1)).save(any());
        assertThat(conjunto.getTipo()).isEqualTo(TipoMetadadosMotu.CDG);
        assertThat(conjunto.getProcesso()).isSameAs(processo);
        assertThat(conjunto.getProcessoDocumento()).isSameAs(documento);
        assertThat(conjunto.isOpcional()).isFalse();
        assertThat(conjunto.isCompleto()).isFalse();
        assertThat(folha1.getMetadadosMotu()).isSameAs(conjunto);
        assertThat(folha2.getMetadadosMotu()).isSameAs(conjunto);
    }

    @Test
    void criaOConjuntoMesmoSemPlantaNoDocumento() {
        DocumentoConfig item = item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO);
        itensDoProcedimento(publicacao, item);
        comSerie();
        ProcessoDocumento documento = documento(processo, item);
        when(plantaRepository.findByProcessoDocumento_Id(documento.getId())).thenReturn(List.of());

        service.registarPecaGrafica(documento);

        assertThat(gravadas()).extracting(MetadadosMotu::getTipo).containsExactly(TipoMetadadosMotu.CDG);
    }

    @Test
    void criaASerieEOConjuntoNumProcessoAntigoSemSerie() {
        DocumentoConfig item = item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO);
        itensDoProcedimento(publicacao, item);
        when(metadadosMotuRepository.findByProcesso_IdAndTipo(processoId, TipoMetadadosMotu.SERIE))
                .thenReturn(Optional.empty());

        service.registarPecaGrafica(documento(processo, item));

        assertThat(gravadas()).extracting(MetadadosMotu::getTipo)
                .containsExactly(TipoMetadadosMotu.SERIE, TipoMetadadosMotu.CDG);
    }

    @ParameterizedTest
    @CsvSource({"PECA_GRAFICA, DEPOSITO", "PECA_ESCRITA, PUBLICACAO", "PECA_ESCRITA, DEPOSITO", "OUTROS, PUBLICACAO",
            "OUTROS, DEPOSITO"})
    void naoCriaFichasParaUmItemQueNaoEPecaGraficaDePublicacao(TipoElemento tipoElemento, CategoriaDocumento categoria) {
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));

        service.registarPecaGrafica(documento(processo, item(tipoElemento, categoria)));

        verify(metadadosMotuRepository, never()).save(any());
        verify(plantaRepository, never()).findByProcessoDocumento_Id(any());
    }

    @Test
    void naoCriaFichasParaUmDocumentoSemItem() {
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));

        service.registarPecaGrafica(documento(processo, null));

        verify(metadadosMotuRepository, never()).save(any());
    }

    @Test
    void naoCriaFichasParaUmaPecaGraficaDePublicacaoForaDoProcedimentoFinal() {
        DocumentoConfig plantaAreaIntervencao = item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO);
        itensDoProcedimento(decisaoInicio, plantaAreaIntervencao);

        service.registarPecaGrafica(documento(processo(decisaoInicio), plantaAreaIntervencao));

        verify(metadadosMotuRepository, never()).save(any());
    }

    @Test
    void removeOConjuntoDoDocumentoDesligandoAsPlantasEMantemASerie() {
        ProcessoDocumento documento = documento(processo, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));
        MetadadosMotu conjunto = MetadadosMotu.conjunto(processo, documento);
        ReflectionTestUtils.setField(conjunto, "id", UUID.randomUUID());
        Planta planta = new Planta(processo, documento);
        planta.setMetadadosMotu(conjunto);
        when(metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(documento.getId(),
                TipoMetadadosMotu.CDG)).thenReturn(Optional.of(conjunto));
        when(plantaRepository.findByMetadadosMotu_Id(conjunto.getId())).thenReturn(List.of(planta));

        service.removerDoDocumento(documento.getId());

        assertThat(planta.getMetadadosMotu()).isNull();
        verify(metadadosMotuRepository).delete(conjunto);
        verify(metadadosMotuRepository, never()).deleteByProcesso_Id(any());
    }

    @Test
    void naoApagaNadaAoRemoverUmDocumentoSemConjunto() {
        UUID documentoId = UUID.randomUUID();
        when(metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(documentoId,
                TipoMetadadosMotu.CDG)).thenReturn(Optional.empty());

        service.removerDoDocumento(documentoId);

        verify(metadadosMotuRepository, never()).delete(any());
    }

    @Test
    void removeSoOsConjuntosDoProcessoEMantemASerie() {
        MetadadosMotu serie = MetadadosMotu.serie(processo);
        MetadadosMotu conjunto1 = MetadadosMotu.conjunto(processo, documento(processo, null));
        MetadadosMotu conjunto2 = MetadadosMotu.conjunto(processo, documento(processo, null));
        when(metadadosMotuRepository.findByProcesso_Id(processoId)).thenReturn(List.of(serie, conjunto1, conjunto2));

        service.removerConjuntosDoProcesso(processoId);

        verify(metadadosMotuRepository).deleteAll(List.of(conjunto1, conjunto2));
    }

    private MetadadosMotu serieComCompleto(boolean completo) {
        MetadadosMotu serie = MetadadosMotu.serie(processo);
        serie.setCompleto(completo);
        return serie;
    }

    private MetadadosMotu conjuntoComCompleto(String nomeDoItem, int ordem, boolean completo) {
        DocumentoConfig item = item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO);
        when(item.getNome()).thenReturn(nomeDoItem);
        when(item.getOrdem()).thenReturn(ordem);
        MetadadosMotu conjunto = MetadadosMotu.conjunto(processo, documento(processo, item));
        ReflectionTestUtils.setField(conjunto, "id", UUID.randomUUID());
        conjunto.setCompleto(completo);
        return conjunto;
    }

    private void comRegistos(MetadadosMotu... registos) {
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));
        when(metadadosMotuRepository.findByProcesso_Id(processoId)).thenReturn(List.of(registos));
    }

    @Test
    void pendenciasPedemASerieIncompleta() {
        comRegistos(serieComCompleto(false), conjuntoComCompleto("Planta A", 1, true));

        assertThat(service.pendencias(processo)).containsExactly(
                new CampoErro("metadados.serie", "Preencha a Série de Dados Geográficos."));
    }

    @Test
    void pendenciasPedemCadaConjuntoIncompletoPelaOrdemDoItem() {
        MetadadosMotu b = conjuntoComCompleto("Planta B", 2, false);
        MetadadosMotu a = conjuntoComCompleto("Planta A", 1, false);
        comRegistos(serieComCompleto(true), b, a, conjuntoComCompleto("Planta C", 3, true));

        assertThat(service.pendencias(processo)).containsExactly(
                new CampoErro("metadados.conjunto." + a.getId(),
                        "Planta A: faltam «Título Alternativo», «Resumo», «Palavras-chave» e mais 12 campos."),
                new CampoErro("metadados.conjunto." + b.getId(),
                        "Planta B: faltam «Título Alternativo», «Resumo», «Palavras-chave» e mais 12 campos."));
    }

    private static final String CONJUNTO_PREENCHIDO = "{\"tituloAlternativo\":\"T\",\"resumo\":\"R\",\"palavrasChave\":\"P\","
            + "\"denominadorEscala\":\"10000\",\"sistemaReferencia\":\"PT-TM06\",\"cartografiaTipo\":\"OUTRO\","
            + "\"dataEdicao\":\"2026-01-01\",\"atualizacaoComplemento\":\"C\",\"entidadeProprietaria\":\"E\","
            + "\"entidadeProdutora\":\"E\",\"sistemaReferenciaNome\":\"N\",\"sistemaReferenciaCodigo\":\"3763\","
            + "\"exatidaoPosicional\":\"1 m\",\"exatidaoTematica\":\"Alta\"}";

    private Planta folhaDaPaginaDasPlantas(MetadadosMotu conjunto, String nomeRegulamento, String numeroFolha) {
        DocumentoConfig itemDasPlantas = item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.DEPOSITO);
        when(itemDasPlantas.getPagina()).thenReturn(PaginaDocumento.PLANTAS);
        Planta folha = new Planta(processo, documento(processo, itemDasPlantas));
        ReflectionTestUtils.setField(folha, "id", UUID.randomUUID());
        folha.setMetadadosMotu(conjunto);
        folha.setNomeRegulamento(nomeRegulamento);
        folha.setNumeroFolha(numeroFolha);
        return folha;
    }

    private MetadadosMotuService servicoQueLeDatas() {
        return new MetadadosMotuService(metadadosMotuRepository, plantaRepository, procedimentoConfigRepository,
                documentoConfigRepository, mock(ProcessoRepository.class), mock(ProcessoDocumentoRepository.class),
                mock(AutorizacaoService.class), mock(ContextoUtilizadorResolver.class), mock(ProcessoAlteravel.class),
                new ObjectMapper().findAndRegisterModules(), mock(Validator.class));
    }

    /** O caso do ensaio: tudo preenchido menos a «Folha N» de uma folha; a pendência diz a ficha e o campo. */
    @Test
    void umConjuntoSemAFolhaNDeUmaFolhaDizAFichaEOCampo() {
        MetadadosMotu conjunto = conjuntoComCompleto("Planta de condicionantes", 1, false);
        conjunto.setDados(CONJUNTO_PREENCHIDO);
        comRegistos(serieComCompleto(true), conjunto);
        Planta comFolha = folhaDaPaginaDasPlantas(conjunto, "Planta de Condicionantes", "1");
        Planta semFolha = folhaDaPaginaDasPlantas(conjunto, "Planta de Condicionantes", null);
        when(plantaRepository.findByProcesso_Id(processoId)).thenReturn(List.of(comFolha, semFolha));

        assertThat(servicoQueLeDatas().pendencias(processo)).containsExactly(new CampoErro(
                "metadados.conjunto." + conjunto.getId(), "Planta de condicionantes: falta a Folha N na tabela Plantas SAIGT SNIT."));
    }

    @Test
    void umConjuntoSemFolhasESemResumoDizOsDoisEmFalta() {
        MetadadosMotu conjunto = conjuntoComCompleto("Planta de ordenamento", 1, false);
        conjunto.setDados(CONJUNTO_PREENCHIDO.replace("\"resumo\":\"R\",", ""));
        comRegistos(serieComCompleto(true), conjunto);

        assertThat(servicoQueLeDatas().pendencias(processo)).extracting(CampoErro::erro).containsExactly(
                "Planta de ordenamento: faltam «Resumo» e uma folha na tabela Plantas SAIGT SNIT.");
    }

    @Test
    void pendenciasIgnoramAsFichasOpcionaisIncompletas() {
        MetadadosMotu opcional = MetadadosMotu.conjunto(processo, documento(processo, null));
        ReflectionTestUtils.setField(opcional, "opcional", true);
        comRegistos(serieComCompleto(true), conjuntoComCompleto("Planta A", 1, true), opcional);

        assertThat(service.pendencias(processo)).isEmpty();
    }

    @Test
    void pendenciasPedemASerieQuandoNaoExisteRegisto() {
        comRegistos();

        assertThat(service.pendencias(processo)).extracting(CampoErro::path).containsExactly("metadados.serie");
    }

    @Test
    void semFichaMotuNaoHaPendencias() {
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_ESCRITA, CategoriaDocumento.PUBLICACAO));

        assertThat(service.pendencias(processo)).isEmpty();
        verify(metadadosMotuRepository, never()).findByProcesso_Id(any());
    }

    private static final String DADOS_DO_CONJUNTO = "{\"tituloAlternativo\":\"T\",\"resumo\":\"R\",\"palavrasChave\":\"P\","
            + "\"denominadorEscala\":\"10000\",\"sistemaReferencia\":\"PT-TM06/ETRS89\",\"cartografiaTipo\":\"OUTRO\","
            + "\"dataEdicao\":\"2026-01-15\",\"atualizacaoComplemento\":\"A\",\"entidadeProprietaria\":\"E\","
            + "\"entidadeProdutora\":\"E\",\"sistemaReferenciaNome\":\"PT-TM06/ETRS89\",\"sistemaReferenciaCodigo\":\"EPSG:3763\","
            + "\"exatidaoPosicional\":\"1 m\",\"exatidaoTematica\":\"95%\"}";

    /** Um Conjunto com os dados do perfil completos e sem folhas, num processo com a resposta dada ao GeoConform. */
    private MetadadosMotu conjuntoSemFolhas(Boolean comGeoconform, DocumentoConfig... itens) {
        ProcessoRepository processos = mock(ProcessoRepository.class);
        MetadadosMotuService comDatas = new MetadadosMotuService(metadadosMotuRepository, plantaRepository,
                procedimentoConfigRepository, documentoConfigRepository, processos, mock(ProcessoDocumentoRepository.class),
                mock(AutorizacaoService.class), mock(ContextoUtilizadorResolver.class), mock(ProcessoAlteravel.class),
                new ObjectMapper().findAndRegisterModules(), mock(Validator.class));
        Dinamica dinamica = mock(Dinamica.class);
        when(dinamica.getComGeoconform()).thenReturn(comGeoconform);
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processos.findById(processoId)).thenReturn(Optional.of(processo));
        itensDoProcedimento(publicacao, itens);
        MetadadosMotu conjunto = conjuntoComCompleto("Planta", 1, false);
        conjunto.setDados(DADOS_DO_CONJUNTO);
        when(metadadosMotuRepository.findByProcesso_Id(processoId)).thenReturn(List.of(conjunto));
        when(plantaRepository.findByProcesso_Id(processoId)).thenReturn(List.of());
        comDatas.ligarFolhas(processoId);
        return conjunto;
    }

    private static DocumentoConfig itemDaPaginaDasPlantas() {
        DocumentoConfig item = item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.DEPOSITO);
        when(item.getPagina()).thenReturn(PaginaDocumento.PLANTAS);
        return item;
    }

    @Test
    void semPaginaDasPlantasOConjuntoCompletaSeComOsDadosDoPerfil() {
        MetadadosMotu conjunto = conjuntoSemFolhas(null, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO));

        assertThat(conjunto.isCompleto()).isTrue();
    }

    @Test
    void comAPaginaDasPlantasOConjuntoSemFolhasContinuaIncompleto() {
        MetadadosMotu conjunto = conjuntoSemFolhas(false, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO),
                itemDaPaginaDasPlantas());

        assertThat(conjunto.isCompleto()).isFalse();
    }

    @Test
    void comGeoconformAPaginaDasPlantasNaoSeAplicaEOConjuntoCompletaSeComOsDados() {
        MetadadosMotu conjunto = conjuntoSemFolhas(true, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO),
                itemDaPaginaDasPlantas());

        assertThat(conjunto.isCompleto()).isTrue();
    }

    private static DocumentoConfig cartogramaDePublicacao() {
        DocumentoConfig cartograma = item(TipoElemento.OUTROS, CategoriaDocumento.PUBLICACAO);
        when(cartograma.getNome()).thenReturn("Cartograma");
        when(cartograma.getOrdem()).thenReturn(13);
        return cartograma;
    }

    @Test
    void umPngNoCartogramaDePublicacaoNaoCriaFichaDeConjunto() {
        DocumentoConfig cartograma = cartogramaDePublicacao();
        itensDoProcedimento(publicacao, item(TipoElemento.PECA_GRAFICA, CategoriaDocumento.PUBLICACAO), cartograma);
        ProcessoDocumento png = new ProcessoDocumento(UUID.randomUUID(), processo, cartograma,
                CategoriaDocumento.PUBLICACAO, "png", "cartograma.png", "privado/x", "hash", "v1", 10L, null);

        service.registarPecaGrafica(png);

        verify(metadadosMotuRepository, never()).save(any());
        assertThat(service.aplicaFichaMotu(processo)).isTrue();
    }

    /** Processo em curso: o Conjunto gerado pelo ZIP do Cartograma antes de o item passar a OUTROS já não se pede. */
    private MetadadosMotu conjuntoDoCartogramaAntigo() {
        MetadadosMotu conjunto = MetadadosMotu.conjunto(processo, documento(processo, cartogramaDePublicacao()));
        ReflectionTestUtils.setField(conjunto, "id", UUID.randomUUID());
        conjunto.setCompleto(false);
        return conjunto;
    }

    @Test
    void oConjuntoDeUmCartogramaQueDeixouDeSerPecaGraficaNaoContaNasPendencias() {
        comRegistos(serieComCompleto(true), conjuntoComCompleto("Planta de ordenamento", 7, true),
                conjuntoDoCartogramaAntigo());

        assertThat(service.pendencias(processo)).isEmpty();
    }

    @Test
    void aPaginaDosMetadadosJaNaoMostraOConjuntoDeUmCartogramaQueDeixouDeSerPecaGrafica() {
        ProcessoRepository processos = mock(ProcessoRepository.class);
        MetadadosMotuService comProcessos = new MetadadosMotuService(metadadosMotuRepository, plantaRepository,
                procedimentoConfigRepository, documentoConfigRepository, processos, mock(ProcessoDocumentoRepository.class),
                mock(AutorizacaoService.class), mock(ContextoUtilizadorResolver.class), mock(ProcessoAlteravel.class),
                new ObjectMapper().findAndRegisterModules(), mock(Validator.class));
        when(processos.findById(processoId)).thenReturn(Optional.of(processo));
        when(processo.getEntidade()).thenReturn(mock(Entidade.class));
        when(processo.getDinamica()).thenReturn(mock(Dinamica.class));
        MetadadosMotu serie = serieComCompleto(true);
        serie.setDados("{}");
        MetadadosMotu planta = conjuntoComCompleto("Planta de ordenamento", 7, true);
        planta.setDados(DADOS_DO_CONJUNTO);
        MetadadosMotu cartograma = conjuntoDoCartogramaAntigo();
        cartograma.setDados(DADOS_DO_CONJUNTO);
        comRegistos(serie, planta, cartograma);

        assertThat(comProcessos.obter(processoId).registos()).extracting(Registo::titulo)
                .containsExactly(null, "Planta de ordenamento");
    }
}
