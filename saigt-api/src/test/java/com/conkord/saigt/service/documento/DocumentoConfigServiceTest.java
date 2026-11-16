package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.AtualizarDocumentoConfigRequest;
import com.conkord.saigt.dto.documento.CriarDocumentoConfigRequest;
import com.conkord.saigt.dto.documento.DocumentoConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ConfigExtensaoNaoAceiteException;
import com.conkord.saigt.exception.ConfigFormatoIncoerenteException;
import com.conkord.saigt.exception.ConfiguracaoEmUsoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoConfigServiceTest {

    private static final UUID MASTER_ID = UUID.randomUUID();
    private static final Long PROCEDIMENTO_ID = 209L;

    @Mock
    private DocumentoConfigRepository documentoConfigRepository;
    @Mock
    private InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    @Mock
    private ProcessoDocumentoRepository processoDocumentoRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private AutorizacaoService autorizacaoService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;
    @Spy
    private ValidadorRegrasTiff validadorRegrasTiff = new ValidadorRegrasTiff();

    @InjectMocks
    private DocumentoConfigService service;

    private InstrumentoProcedimentoConfig procedimento;

    @BeforeEach
    void contextoDoMaster() {
        lenient().when(contextoUtilizadorResolver.resolver()).thenReturn(
                new ContextoUtilizador(MASTER_ID, "AD_DGT", PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null));
        procedimento = mock(InstrumentoProcedimentoConfig.class);
        lenient().when(procedimento.getId()).thenReturn(PROCEDIMENTO_ID);
        lenient().when(instrumentoProcedimentoConfigRepository.findById(PROCEDIMENTO_ID))
                .thenReturn(Optional.of(procedimento));
        lenient().when(documentoConfigRepository.save(any(DocumentoConfig.class))).thenAnswer(invocacao -> {
            DocumentoConfig gravado = invocacao.getArgument(0);
            if (gravado.getId() == null) {
                ReflectionTestUtils.setField(gravado, "id", 77L);
            }
            return gravado;
        });
    }

    private static CriarDocumentoConfigRequest criacao(String codigo, CategoriaDocumento categoria, TipoElemento tipo,
                                                       FormatoDocumento formato, List<String> extensoes,
                                                       ElementoInformacaoBase informacaoBase, boolean ativo) {
        return new CriarDocumentoConfigRequest(PROCEDIMENTO_ID, codigo, "Deliberação", "Deliberação", null,
                categoria, tipo, formato, extensoes, true, 1, informacaoBase, null, null, null, true, ativo, null, null);
    }

    private static CriarDocumentoConfigRequest criacaoValida() {
        return criacao("DELIBERACAO_PUB", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA,
                FormatoDocumento.EDITAVEL, List.of(".PDF", "docx", "pdf"), ElementoInformacaoBase.DELIBERACAO, true);
    }

    private static AtualizarDocumentoConfigRequest atualizacao(String nome, CategoriaDocumento categoria,
                                                               TipoElemento tipo, FormatoDocumento formato,
                                                               boolean ativo) {
        return new AtualizarDocumentoConfigRequest(nome, "Planta", null, categoria, tipo, formato, List.of("zip"),
                false, 2, null, null, null, null, true, ativo, null, null);
    }

    private DocumentoConfig itemGravado(Long id, CategoriaDocumento categoria, TipoElemento tipo,
                                        FormatoDocumento formato) {
        DocumentoConfig item = new DocumentoConfig(procedimento, "PLANTA_LOCALIZACAO_PUB", "Planta", "Planta",
                categoria, tipo, formato, "[\"zip\"]", 2);
        item.setLinkPublicoPermitido(true);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static DocumentoConfig outroDoSeparador(ElementoInformacaoBase informacaoBase) {
        DocumentoConfig outro = mock(DocumentoConfig.class);
        lenient().when(outro.getId()).thenReturn(5L);
        lenient().when(outro.getElementoInformacaoBase()).thenReturn(informacaoBase);
        return outro;
    }

    @Test
    void naoMasterNaoDeveTocarNosRepositoriosNemAuditar() {
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação.")).when(autorizacaoService)
                .verificarPerfilMaster(any());

        assertThatThrownBy(() -> service.criar(criacaoValida())).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.atualizar(1L, atualizacao("N", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, true)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(documentoConfigRepository, processoDocumentoRepository, auditoriaService);
    }

    @Test
    void criarDeveNormalizarAsExtensoesGravarEAuditar() {
        DocumentoConfigResponse resposta = service.criar(criacaoValida());

        assertThat(resposta.id()).isEqualTo(77L);
        assertThat(resposta.extensoesAceites()).containsExactly("pdf", "docx");
        assertThat(resposta.linkPublicoPermitido()).isTrue();
        assertThat(resposta.instrumentoProcedimentoConfigId()).isEqualTo(PROCEDIMENTO_ID);
        ArgumentCaptor<DocumentoConfig> gravado = ArgumentCaptor.forClass(DocumentoConfig.class);
        verify(documentoConfigRepository).save(gravado.capture());
        assertThat(gravado.getValue().getExtensoesAceites()).isEqualTo("[\"pdf\",\"docx\"]");

        ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
        verify(auditoriaService).registar(eq("CONFIGURACAO"), eq("documento-config:77"), eq("CRIAR"), eq(MASTER_ID),
                dados.capture());
        assertThat(dados.getValue()).isInstanceOfSatisfying(Map.class, mapa -> {
            assertThat(mapa).containsEntry("codigo", "DELIBERACAO_PUB").containsEntry("ativo", "true");
        });
    }

    @Test
    void procedimentoInexistenteDeveDar400NoCampoSemGravar() {
        when(instrumentoProcedimentoConfigRepository.findById(PROCEDIMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(criacaoValida())).isInstanceOfSatisfying(ValidacaoException.class,
                e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                        .containsExactly("instrumentoProcedimentoConfigId"));
        verify(documentoConfigRepository, never()).save(any());
    }

    @Test
    void codigoRepetidoNoProcedimentoDeveDar400NoCampoCodigo() {
        when(documentoConfigRepository.existsByInstrumentoProcedimentoConfig_IdAndCodigo(PROCEDIMENTO_ID,
                "DELIBERACAO_PUB")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(criacaoValida())).isInstanceOfSatisfying(ValidacaoException.class,
                e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("codigo"));
        verify(documentoConfigRepository, never()).save(any());
    }

    @Test
    void extensaoForaDaTabelaDaEfDeveDar400SemGravar() {
        CriarDocumentoConfigRequest pedido = criacao("DELIBERACAO_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, List.of("jpg"), null, true);

        assertThatThrownBy(() -> service.criar(pedido)).isInstanceOf(ConfigExtensaoNaoAceiteException.class);
        verify(documentoConfigRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void itemOutrosNaoTemTabelaDeExtensoes() {
        CriarDocumentoConfigRequest pedido = criacao("RELATORIO", CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS,
                FormatoDocumento.NAO_EDITAVEL, List.of("jpg", "xyz"), null, true);

        assertThat(service.criar(pedido).extensoesAceites()).containsExactly("jpg", "xyz");
    }

    @Test
    void pecaEscritaDeDepositoEditavelDeveDar400FormatoIncoerente() {
        CriarDocumentoConfigRequest pedido = criacao("TEXTO_ATO_DEP", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, List.of("pdf"), null, true);

        assertThatThrownBy(() -> service.criar(pedido)).isInstanceOfSatisfying(ConfigFormatoIncoerenteException.class,
                e -> {
                    assertThat(e.getCodigo()).isEqualTo("CONFIG_FORMATO_INCOERENTE");
                    assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("formato");
                });
    }

    @Test
    void pecaEscritaDePublicacaoNaoEditavelDeveDar400FormatoIncoerente() {
        CriarDocumentoConfigRequest pedido = criacao("TEXTO_ATO_PUB", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.NAO_EDITAVEL, List.of("pdf"), null, true);

        assertThatThrownBy(() -> service.criar(pedido)).isInstanceOf(ConfigFormatoIncoerenteException.class);
    }

    @Test
    void pecaGraficaNaoSofreARegraDoFormato() {
        CriarDocumentoConfigRequest pedido = criacao("PLANTA_DEP", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_GRAFICA, FormatoDocumento.EDITAVEL, List.of("zip"), null, true);

        assertThat(service.criar(pedido).formato()).isEqualTo(FormatoDocumento.EDITAVEL);
    }

    @Test
    void informacaoBaseDiferenteDaDosItensAtivosDoSeparadorDeveDar400() {
        DocumentoConfig outro = outroDoSeparador(ElementoInformacaoBase.DELIBERACAO);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfig_IdAndSeparadorAndAtivoTrue(PROCEDIMENTO_ID,
                "Deliberação")).thenReturn(List.of(outro));
        CriarDocumentoConfigRequest pedido = criacao("DELIBERACAO_DEP", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, List.of("pdf"), ElementoInformacaoBase.TEXTO_ATO,
                true);

        assertThatThrownBy(() -> service.criar(pedido)).isInstanceOfSatisfying(ValidacaoException.class,
                e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                        .containsExactly("elementoInformacaoBase"));
        verify(documentoConfigRepository, never()).save(any());
    }

    @Test
    void itemInativoNaoEntraNaRegraDaInformacaoBase() {
        CriarDocumentoConfigRequest pedido = criacao("DELIBERACAO_DEP", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, List.of("pdf"), ElementoInformacaoBase.TEXTO_ATO,
                false);

        assertThat(service.criar(pedido).ativo()).isFalse();
        verify(documentoConfigRepository, never())
                .findByInstrumentoProcedimentoConfig_IdAndSeparadorAndAtivoTrue(anyLong(), anyString());
    }

    @Test
    void mesmaInformacaoBaseNoSeparadorEAceite() {
        DocumentoConfig outro = outroDoSeparador(ElementoInformacaoBase.DELIBERACAO);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfig_IdAndSeparadorAndAtivoTrue(PROCEDIMENTO_ID,
                "Deliberação")).thenReturn(List.of(outro));

        assertThat(service.criar(criacaoValida()).elementoInformacaoBase())
                .isEqualTo(ElementoInformacaoBase.DELIBERACAO);
    }

    @Test
    void atualizarInexistenteDeveDar404() {
        when(documentoConfigRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(9L, atualizacao("N", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, true)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void mudarACategoriaComFicheirosDeveDar409SemGravarNemAuditar() {
        DocumentoConfig item = itemGravado(7L, CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL);
        when(documentoConfigRepository.findById(7L)).thenReturn(Optional.of(item));
        when(processoDocumentoRepository.existsByDocumentoConfig_Id(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(7L, atualizacao("Planta", CategoriaDocumento.DEPOSITO,
                TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, true)))
                .isInstanceOf(ConfiguracaoEmUsoException.class);

        verify(documentoConfigRepository, never()).save(any());
        verify(auditoriaService, never()).registar(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void mudarOTipoDeElementoComFicheirosDeveDar409() {
        DocumentoConfig item = itemGravado(7L, CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL);
        when(documentoConfigRepository.findById(7L)).thenReturn(Optional.of(item));
        when(processoDocumentoRepository.existsByDocumentoConfig_Id(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(7L, new AtualizarDocumentoConfigRequest("Planta", "Planta", null,
                CategoriaDocumento.PUBLICACAO, TipoElemento.OUTROS, FormatoDocumento.NAO_EDITAVEL, List.of("zip"),
                false, 2, null, null, null, null, true, true, null, null))).isInstanceOf(ConfiguracaoEmUsoException.class);
    }

    @Test
    void mudarOutrosCamposComFicheirosEAceiteEAuditaSoOQueMudou() {
        DocumentoConfig item = itemGravado(7L, CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL);
        when(documentoConfigRepository.findById(7L)).thenReturn(Optional.of(item));

        DocumentoConfigResponse resposta = service.atualizar(7L, new AtualizarDocumentoConfigRequest("Novo nome",
                "Planta", null, CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                List.of("zip"), true, 2, null, 1000L, null, null, false, true, null, null));

        assertThat(resposta.nome()).isEqualTo("Novo nome");
        assertThat(resposta.codigo()).isEqualTo("PLANTA_LOCALIZACAO_PUB");
        assertThat(resposta.linkPublicoPermitido()).isFalse();
        verify(processoDocumentoRepository, never()).existsByDocumentoConfig_Id(anyLong());
        ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
        verify(auditoriaService).registar(eq("CONFIGURACAO"), eq("documento-config:7"), eq("ATUALIZAR"), eq(MASTER_ID),
                dados.capture());
        assertThat(dados.getValue()).isInstanceOfSatisfying(Map.class, mapa -> assertThat(mapa.keySet())
                .containsExactlyInAnyOrder("nome", "obrigatorio", "tamanhoMaximoFicheiro", "linkPublicoPermitido"));
    }

    @Test
    void desativarDeveGravarAtivoFalso() {
        DocumentoConfig item = itemGravado(7L, CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL);
        when(documentoConfigRepository.findById(7L)).thenReturn(Optional.of(item));

        DocumentoConfigResponse resposta = service.atualizar(7L, atualizacao("Planta", CategoriaDocumento.PUBLICACAO,
                TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL, false));

        assertThat(resposta.ativo()).isFalse();
        assertThat(item.isAtivo()).isFalse();
    }

    @Test
    void listarSemProcedimentoDeveDar400() {
        assertThatThrownBy(() -> service.listar(null, null, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("instrumentoProcedimentoConfigId"));
        verifyNoInteractions(documentoConfigRepository);
    }
}
