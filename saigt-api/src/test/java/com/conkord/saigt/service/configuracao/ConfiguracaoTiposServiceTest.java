package com.conkord.saigt.service.configuracao;

import com.conkord.saigt.dto.configuracao.TipoDinamicaConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoInstrumentoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoInstrumentoConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoProcedimentoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoProcedimentoConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ConfiguracaoEmUsoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoTiposServiceTest {

    private static final UUID MASTER_ID = UUID.randomUUID();

    @Mock
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Mock
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Mock
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Mock
    private InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    @Mock
    private InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    @Mock
    private InstrumentoRepository instrumentoRepository;
    @Mock
    private CondicaoRepository condicaoRepository;
    @Spy
    private CatalogoFormularios catalogoFormularios = new CatalogoFormularios();
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private AutorizacaoService autorizacaoService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private ConfiguracaoTiposService service;

    @BeforeEach
    void contextoDoMaster() {
        lenient().when(contextoUtilizadorResolver.resolver()).thenReturn(contexto());
    }

    private static ContextoUtilizador contexto() {
        return new ContextoUtilizador(MASTER_ID, "AD_DGT", PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null);
    }

    private static <T> T comId(T entidade, Long id) {
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }

    @Test
    void naoMasterNaoDeveTocarNosRepositoriosNemAuditar() {
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação.")).when(autorizacaoService)
                .verificarPerfilMaster(any());

        assertThatThrownBy(() -> service.listarTiposDinamica(null, PageRequest.of(0, 20)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.criarTipoDinamica(new TipoDinamicaConfigRequest("A", "A", true)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(tipoDinamicaRepository, auditoriaService);
    }

    @Test
    void mudarACategoriaComInstrumentosDeveDar409SemGravar() {
        TipoInstrumento tipo = comId(new TipoInstrumento("PDM", "Plano", "PLANO"), 1L);
        when(tipoInstrumentoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(instrumentoRepository.existsByTipoInstrumento_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarTipoInstrumento(1L,
                new TipoInstrumentoConfigRequest("PDM", "Plano", "PROGRAMA", true)))
                .isInstanceOf(ConfiguracaoEmUsoException.class);

        assertThat(tipo.getCategoria()).isEqualTo("PLANO");
        verify(tipoInstrumentoRepository, never()).save(any());
        verify(auditoriaService, never()).registar(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void mudarACategoriaSemInstrumentosDeveGravarEAuditarSoOQueMudou() {
        TipoInstrumento tipo = comId(new TipoInstrumento("PDM", "Plano", "PLANO"), 1L);
        when(tipoInstrumentoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(instrumentoRepository.existsByTipoInstrumento_Id(1L)).thenReturn(false);

        TipoInstrumentoConfigResponse resposta = service.atualizarTipoInstrumento(1L,
                new TipoInstrumentoConfigRequest("PDM", "Plano", "SRUP", true));

        assertThat(resposta.categoria()).isEqualTo("SRUP");
        verify(auditoriaService).registar("CONFIGURACAO", "tipos-instrumento:1", "ATUALIZAR", MASTER_ID,
                Map.of("categoria", "SRUP"));
    }

    @Test
    void mudarOCodigoDeUmTipoDeDinamicaCitadoEmCondicaoDeveDar409() {
        TipoDinamica tipo = comId(new TipoDinamica("ALTERACAO", "Alteração"), 2L);
        when(tipoDinamicaRepository.findById(2L)).thenReturn(Optional.of(tipo));
        when(tipoDinamicaRepository.existsByCodigoAndIdNot("NOVO", 2L)).thenReturn(false);
        when(instrumentoDinamicaConfigRepository.existsByTipoDinamica_Id(2L)).thenReturn(false);
        when(condicaoRepository.existeParametroCodigo("ALTERACAO")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarTipoDinamica(2L, new TipoDinamicaConfigRequest("NOVO", "Alteração", true)))
                .isInstanceOf(ConfiguracaoEmUsoException.class)
                .hasMessage("Este registo já está em uso e a sua chave não pode ser alterada.");
        assertThat(tipo.getCodigo()).isEqualTo("ALTERACAO");
    }

    @Test
    void codigoDeOutroRegistoDeveDar400AntesDeVerificarOUso() {
        TipoDinamica tipo = comId(new TipoDinamica("A", "A"), 3L);
        when(tipoDinamicaRepository.findById(3L)).thenReturn(Optional.of(tipo));
        when(tipoDinamicaRepository.existsByCodigoAndIdNot("B", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarTipoDinamica(3L, new TipoDinamicaConfigRequest("B", "A", true)))
                .isInstanceOfSatisfying(ValidacaoException.class,
                        e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("codigo"));

        verify(condicaoRepository, never()).existeParametroCodigo(anyString());
        verify(instrumentoDinamicaConfigRepository, never()).existsByTipoDinamica_Id(anyLong());
    }

    @Test
    void tipoDeProcedimentoSemFormularioDeveDevolverFormularioNulo() {
        when(tipoProcedimentoRepository.existsByCodigo("NOVO_PROC")).thenReturn(false);
        when(tipoProcedimentoRepository.save(any(TipoProcedimento.class)))
                .thenAnswer(invocacao -> comId(invocacao.getArgument(0), 9L));

        TipoProcedimentoConfigResponse resposta = service.criarTipoProcedimento(
                new TipoProcedimentoConfigRequest("NOVO_PROC", "Novo", true));

        assertThat(resposta.formulario()).isNull();
        verify(auditoriaService).registar(eq("CONFIGURACAO"), eq("tipos-procedimento:9"), eq("CRIAR"), eq(MASTER_ID), any());
    }

    @Test
    void tipoDeProcedimentoDoCatalogoDeveDevolverOFormulario() {
        TipoProcedimento tipo = comId(new TipoProcedimento("DECISAO_INICIO", "Decisão"), 4L);
        when(tipoProcedimentoRepository.findById(4L)).thenReturn(Optional.of(tipo));

        TipoProcedimentoConfigResponse resposta = service.atualizarTipoProcedimento(4L,
                new TipoProcedimentoConfigRequest("DECISAO_INICIO", "Decisão", false));

        assertThat(resposta.ativo()).isFalse();
        assertThat(resposta.formulario()).isEqualTo("INFORMACAO_GERAL");
    }
}
