package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.exception.DocumentosObrigatoriosEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacao;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.documento.EstruturaDocumental.Item;
import com.conkord.saigt.service.instrumento.ValidadorSubmissaoProcesso;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** O filtro dos itens pelos elementos afetados do processo, com e sem itens independentes dele. */
class EstruturaDocumentalServiceTest {

    private static final String ESCRITA = "REGULAMENTO_DEP";
    private static final String GRAFICA = "PLANTA_DEP";
    private static final String OUTROS = "DEMAIS_ELEMENTOS_DEP";
    private static final String PROVA_AM = "PROVA_NOTIFICACAO_AM_DEP";
    private static final String PROVA_CCDR = "PROVA_NOTIFICACAO_CCDR_DEP";

    private final DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
    private final ValidadorSubmissaoProcesso validadorSubmissaoProcesso = mock(ValidadorSubmissaoProcesso.class);
    private final InstrumentoProcedimentoConfig procedimento = mock(InstrumentoProcedimentoConfig.class);
    private final ProcessoDocumentoObservacaoRepository observacaoRepository = mock(ProcessoDocumentoObservacaoRepository.class);
    private final EstruturaDocumentalService service = new EstruturaDocumentalService(mock(ProcessoRepository.class),
            mock(ProcessoDocumentoRepository.class), documentoConfigRepository, observacaoRepository,
            mock(AutorizacaoService.class), mock(ContextoUtilizadorResolver.class), validadorSubmissaoProcesso);

    @BeforeEach
    void itensDoProcedimento() {
        when(validadorSubmissaoProcesso.exigeElementosAfetados(any())).thenReturn(true);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(procedimento)).thenReturn(List.of(
                item(ESCRITA, "Regulamento", TipoElemento.PECA_ESCRITA, false, 1),
                item(GRAFICA, "Planta", TipoElemento.PECA_GRAFICA, false, 2),
                item(OUTROS, "Outros", TipoElemento.OUTROS, false, 3),
                item(PROVA_AM, "Provas de notificação", TipoElemento.OUTROS, true, 90),
                item(PROVA_CCDR, "Provas de notificação", TipoElemento.OUTROS, true, 91)));
    }

    private DocumentoConfig item(String codigo, String separador, TipoElemento tipoElemento, boolean independente, int ordem) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, "Item " + codigo, separador,
                CategoriaDocumento.DEPOSITO, tipoElemento, FormatoDocumento.NAO_EDITAVEL, "[\"pdf\"]", ordem);
        item.setObrigatorio(true);
        item.setIndependenteElementosAfetados(independente);
        return item;
    }

    private Processo processo(List<String> elementosAfetados) {
        Dinamica dinamica = mock(Dinamica.class);
        when(dinamica.getComGeoconform()).thenReturn(Boolean.TRUE);
        Processo processo = mock(Processo.class);
        when(processo.getId()).thenReturn(UUID.randomUUID());
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimento);
        when(processo.getElementosAfetados()).thenReturn(elementosAfetados);
        return processo;
    }

    private static List<String> codigos(EstruturaDocumental estrutura) {
        return estrutura.separadores().stream()
                .flatMap(separador -> Stream.concat(separador.publicacao().stream(), separador.deposito().stream()))
                .map(item -> item.config().getCodigo())
                .toList();
    }

    private static List<String> pendentes(EstruturaDocumental estrutura) {
        return estrutura.pendencias().stream().map(Item::config).map(DocumentoConfig::getCodigo).toList();
    }

    static Stream<Arguments> combinacoesDeElementosAfetados() {
        List<String> todos = List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS", "OUTROS");
        List<Arguments> combinacoes = new ArrayList<>();
        for (int mascara = 1; mascara < 8; mascara++) {
            List<String> elementos = new ArrayList<>();
            for (int i = 0; i < todos.size(); i++) {
                if ((mascara & (1 << i)) != 0) {
                    elementos.add(todos.get(i));
                }
            }
            combinacoes.add(Arguments.of(List.copyOf(elementos)));
        }
        return combinacoes.stream();
    }

    @ParameterizedTest
    @MethodSource("combinacoesDeElementosAfetados")
    void oItemIndependenteEntraSempreEOsOutrosSeguemOsElementosAfetados(List<String> elementosAfetados) {
        EstruturaDocumental estrutura = service.montar(processo(elementosAfetados));

        List<String> esperados = new ArrayList<>();
        if (elementosAfetados.contains("PECAS_ESCRITAS")) {
            esperados.add(ESCRITA);
        }
        if (elementosAfetados.contains("PECAS_GRAFICAS")) {
            esperados.add(GRAFICA);
        }
        if (elementosAfetados.contains("OUTROS")) {
            esperados.add(OUTROS);
        }
        esperados.add(PROVA_AM);
        esperados.add(PROVA_CCDR);
        assertThat(codigos(estrutura)).containsExactlyElementsOf(esperados);
        assertThat(pendentes(estrutura)).containsExactlyElementsOf(esperados);
    }

    @Test
    void soComPecasGraficasAsProvasFicamNoSeuSeparadorComoObrigatoriasPendentes() {
        EstruturaDocumental estrutura = service.montar(processo(List.of("PECAS_GRAFICAS")));

        assertThat(estrutura.separadores()).extracting(EstruturaDocumental.Separador::nome)
                .containsExactly("Planta", "Provas de notificação");
        assertThat(estrutura.separadores().get(1).deposito()).extracting(item -> item.config().getCodigo())
                .containsExactly(PROVA_AM, PROVA_CCDR);
        assertThat(pendentes(estrutura)).containsExactly(GRAFICA, PROVA_AM, PROVA_CCDR);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void semElementosAfetadosNaoHaItensNemOsIndependentes(List<String> elementosAfetados) {
        EstruturaDocumental estrutura = service.montar(processo(elementosAfetados));

        assertThat(estrutura.separadores()).isEmpty();
        assertThat(estrutura.pendencias()).isEmpty();
    }

    @Test
    void semFiltroDosElementosAfetadosEntramTodosOsItens() {
        when(validadorSubmissaoProcesso.exigeElementosAfetados(any())).thenReturn(false);

        EstruturaDocumental estrutura = service.montar(processo(null));

        assertThat(codigos(estrutura)).containsExactly(ESCRITA, GRAFICA, OUTROS, PROVA_AM, PROVA_CCDR);
    }

    @Test
    void umItemOutrosQueNaoEIndependenteContinuaASeguirOFiltro() {
        EstruturaDocumental estrutura = service.montar(processo(List.of("PECAS_ESCRITAS")));

        assertThat(codigos(estrutura)).containsExactly(ESCRITA, PROVA_AM, PROVA_CCDR).doesNotContain(OUTROS, GRAFICA);
    }

    private static final String TEXTO_PUB = "REGULAMENTO_PUB";
    private static final String PLANTA_PUB = "PLANTA_PUB";

    private DocumentoConfig itemPublicacao(String codigo, TipoElemento tipoElemento, int ordem) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, "Item " + codigo, "Regulamento",
                CategoriaDocumento.PUBLICACAO, tipoElemento, FormatoDocumento.EDITAVEL, "[\"docx\"]", ordem);
        item.setObrigatorio(true);
        return item;
    }

    private Processo processoComDestino(DestinoProcesso destino) {
        when(validadorSubmissaoProcesso.exigeElementosAfetados(any())).thenReturn(false);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(procedimento)).thenReturn(List.of(
                itemPublicacao(TEXTO_PUB, TipoElemento.PECA_ESCRITA, 1),
                item(ESCRITA, "Regulamento", TipoElemento.PECA_ESCRITA, false, 2),
                itemPublicacao(PLANTA_PUB, TipoElemento.PECA_GRAFICA, 3),
                item(GRAFICA, "Planta", TipoElemento.PECA_GRAFICA, false, 4)));
        Processo processo = processo(null);
        when(processo.getDestino()).thenReturn(destino);
        return processo;
    }

    @Test
    void soParaDepositoSaemOsItensDePublicacaoDaEstruturaEDasPendencias() {
        EstruturaDocumental estrutura = service.montar(processoComDestino(DestinoProcesso.SO_DEPOSITO));

        assertThat(codigos(estrutura)).containsExactly(ESCRITA, GRAFICA);
        assertThat(estrutura.separadores()).allSatisfy(separador -> assertThat(separador.publicacao()).isEmpty());
        assertThat(pendentes(estrutura)).containsExactly(ESCRITA, GRAFICA);
    }

    @Test
    void paraPublicacaoEDepositoFicamOsItensDasDuasCaixas() {
        EstruturaDocumental estrutura = service.montar(processoComDestino(DestinoProcesso.PUBLICACAO_DEPOSITO));

        assertThat(codigos(estrutura)).containsExactlyInAnyOrder(TEXTO_PUB, ESCRITA, PLANTA_PUB, GRAFICA);
        assertThat(pendentes(estrutura)).containsExactly(TEXTO_PUB, ESCRITA, PLANTA_PUB, GRAFICA);
    }

    @Test
    void aCompletudeDeUmSoParaDepositoSoExigeOsObrigatoriosDeDeposito() {
        CompletudeDocumentalService completude = new CompletudeDocumentalService(service, mock(PlantasService.class),
                mock(MetadadosService.class), mock(MetadadosMotuService.class));
        Processo processo = processoComDestino(DestinoProcesso.SO_DEPOSITO);

        assertThatThrownBy(() -> completude.verificar(processo))
                .isInstanceOfSatisfying(DocumentosObrigatoriosEmFaltaException.class, erro -> assertThat(erro.getCampos())
                        .extracting(ValidacaoException.CampoErro::erro)
                        .containsExactly("Falta carregar: Item " + ESCRITA + " (DEPOSITO).",
                                "Falta carregar: Item " + GRAFICA + " (DEPOSITO)."));
    }

    @Test
    void oDestinoAplicaSeSoAosItensDePublicacao() {
        Processo soDeposito = processoComDestino(DestinoProcesso.SO_DEPOSITO);
        Processo publicacao = processoComDestino(DestinoProcesso.PUBLICACAO_DEPOSITO);
        DocumentoConfig dePublicacao = itemPublicacao(TEXTO_PUB, TipoElemento.PECA_ESCRITA, 1);
        DocumentoConfig deDeposito = item(ESCRITA, "Regulamento", TipoElemento.PECA_ESCRITA, false, 2);

        assertThat(EstruturaDocumentalService.aplicaAoDestino(soDeposito, dePublicacao)).isFalse();
        assertThat(EstruturaDocumentalService.aplicaAoDestino(soDeposito, deDeposito)).isTrue();
        assertThat(EstruturaDocumentalService.aplicaAoDestino(publicacao, dePublicacao)).isTrue();
        assertThat(EstruturaDocumentalService.aplicaAoDestino(publicacao, deDeposito)).isTrue();
    }

    private static final String CARTOGRAMA_PUB = "CARTOGRAMA_PUB";
    private static final String CARTOGRAMA_DEP = "CARTOGRAMA_DEP";
    private static final String ZIP_ORDENAMENTO = "PLANTAS_ZIP_ORDENAMENTO_DEP";
    private static final String CARTOGRAMA_DAS_FOLHAS = "PLANTAS_CARTOGRAMA_DEP";

    private DocumentoConfig itemConfigurado(long id, String codigo, String nome, String separador, CategoriaDocumento categoria,
                                            TipoElemento tipoElemento, PaginaDocumento pagina, boolean obrigatorio, int ordem) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, nome, separador, categoria, tipoElemento,
                categoria == CategoriaDocumento.PUBLICACAO ? FormatoDocumento.EDITAVEL : FormatoDocumento.NAO_EDITAVEL,
                "[\"pdf\", \"jpg\", \"png\"]", ordem);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "pagina", pagina);
        item.setObrigatorio(obrigatorio);
        return item;
    }

    /** O Cartograma como no procedimento final da Elaboração do PDM, com ou sem os itens da página das Plantas. */
    private Processo processoComCartograma(boolean comItensDaPaginaDasPlantas) {
        when(validadorSubmissaoProcesso.exigeElementosAfetados(any())).thenReturn(false);
        List<DocumentoConfig> itens = new ArrayList<>(List.of(
                itemConfigurado(13, CARTOGRAMA_PUB, "Cartograma", "Cartograma", CategoriaDocumento.PUBLICACAO,
                        TipoElemento.OUTROS, PaginaDocumento.DOCUMENTOS, true, 13),
                itemConfigurado(14, CARTOGRAMA_DEP, "Cartograma", "Cartograma", CategoriaDocumento.DEPOSITO,
                        TipoElemento.OUTROS, PaginaDocumento.DOCUMENTOS, true, 14)));
        if (comItensDaPaginaDasPlantas) {
            itens.add(itemConfigurado(28, ZIP_ORDENAMENTO, "Planta de Ordenamento", "Planta de Ordenamento",
                    CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, PaginaDocumento.PLANTAS, false, 28));
            itens.add(itemConfigurado(31, CARTOGRAMA_DAS_FOLHAS, "Cartograma das folhas", "Cartograma das folhas",
                    CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, PaginaDocumento.PLANTAS, false, 31));
        }
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(procedimento))
                .thenReturn(itens);
        Processo processo = processo(null);
        when(processo.getDinamica().getComGeoconform()).thenReturn(Boolean.FALSE);
        when(processo.getDestino()).thenReturn(DestinoProcesso.PUBLICACAO_DEPOSITO);
        return processo;
    }

    private static EstruturaDocumental.Separador separador(EstruturaDocumental estrutura, String nome) {
        return estrutura.separadores().stream().filter(separador -> separador.nome().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void comAPaginaDasPlantasOCartogramaSoTemACaixaDePublicacaoENaoPedeODeDeposito() {
        EstruturaDocumental estrutura = service.montar(processoComCartograma(true));

        EstruturaDocumental.Separador cartograma = separador(estrutura, "Cartograma");
        assertThat(cartograma.publicacao()).extracting(item -> item.config().getCodigo()).containsExactly(CARTOGRAMA_PUB);
        assertThat(cartograma.deposito()).isEmpty();
        assertThat(codigos(estrutura)).doesNotContain(CARTOGRAMA_DEP, CARTOGRAMA_DAS_FOLHAS);
        assertThat(pendentes(estrutura)).containsExactly(CARTOGRAMA_PUB);
    }

    @Test
    void semAPaginaDasPlantasOCartogramaTemAsDuasCaixas() {
        EstruturaDocumental estrutura = service.montar(processoComCartograma(false));

        EstruturaDocumental.Separador cartograma = separador(estrutura, "Cartograma");
        assertThat(cartograma.publicacao()).extracting(item -> item.config().getCodigo()).containsExactly(CARTOGRAMA_PUB);
        assertThat(cartograma.deposito()).extracting(item -> item.config().getCodigo()).containsExactly(CARTOGRAMA_DEP);
        assertThat(pendentes(estrutura)).containsExactly(CARTOGRAMA_PUB, CARTOGRAMA_DEP);
    }

    @Test
    void comGeoconformAPaginaDasPlantasNaoSeAplicaEOCartogramaTemAsDuasCaixas() {
        Processo processo = processoComCartograma(true);
        when(processo.getDinamica().getComGeoconform()).thenReturn(Boolean.TRUE);

        EstruturaDocumental estrutura = service.montar(processo);

        assertThat(separador(estrutura, "Cartograma").deposito()).extracting(item -> item.config().getCodigo())
                .containsExactly(CARTOGRAMA_DEP);
    }

    @Test
    void oCartogramaDePublicacaoSemFicheiroNemObservacoesTravaASubmissaoEComObservacoesSubmete() {
        CompletudeDocumentalService completude = new CompletudeDocumentalService(service, mock(PlantasService.class),
                mock(MetadadosService.class), mock(MetadadosMotuService.class));
        Processo processo = processoComCartograma(true);

        assertThatThrownBy(() -> completude.verificar(processo))
                .isInstanceOfSatisfying(DocumentosObrigatoriosEmFaltaException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("DOCUMENTOS_OBRIGATORIOS_EM_FALTA");
                    assertThat(erro.getCampos()).containsExactly(new ValidacaoException.CampoErro(
                            "documentos." + CARTOGRAMA_PUB, "Falta carregar: Cartograma (PUBLICACAO)."));
                });

        DocumentoConfig cartogramaPub = documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(procedimento).getFirst();
        ProcessoDocumentoObservacao observacao =
                new ProcessoDocumentoObservacao(processo, cartogramaPub, "O cartograma segue no ZIP das plantas.");
        when(observacaoRepository.findByIdProcessoId(processo.getId())).thenReturn(List.of(observacao));

        assertThatCode(() -> completude.verificar(processo)).doesNotThrowAnyException();
    }
}
