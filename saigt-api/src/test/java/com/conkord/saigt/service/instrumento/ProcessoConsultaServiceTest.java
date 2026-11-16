package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.RegrasFormularioRen;
import com.conkord.saigt.service.instrumento.formulario.RegrasRedesDefesaSgifr;
import com.conkord.saigt.service.instrumento.formulario.RegrasTerritorioSgifr;
import com.conkord.saigt.dto.instrumento.EstatisticasProcessoResponse;
import com.conkord.saigt.dto.instrumento.HistoricoProcessoResponse;
import com.conkord.saigt.dto.instrumento.GrupoPainelResponse;
import com.conkord.saigt.dto.instrumento.PainelProcessosResponse;
import com.conkord.saigt.dto.instrumento.InformacaoBaseResponse;
import com.conkord.saigt.dto.instrumento.PendenciaSubmissaoResponse;
import com.conkord.saigt.dto.instrumento.PendenciaSubmissaoResponse.Pagina;
import com.conkord.saigt.dto.instrumento.ProcessoDetalheResponse;
import com.conkord.saigt.dto.instrumento.TipoAtoDisponivelResponse;
import com.conkord.saigt.dto.instrumento.ProcessoFiltro;
import com.conkord.saigt.dto.instrumento.ProcessoListagemResponse;
import com.conkord.saigt.dto.instrumento.RecusaResponse;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItemRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.EstruturaDocumental.Item;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessoConsultaServiceTest {

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final AuditoriaRepository auditoriaRepository = mock(AuditoriaRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);

    private final ProcessoService processoService = mock(ProcessoService.class);
    private final ProcessoValidacaoService processoValidacaoService = mock(ProcessoValidacaoService.class);
    private final CompletudeDocumentalService completudeDocumentalService = mock(CompletudeDocumentalService.class);
    private final TerritorioInstrumentoService territorioInstrumentoService = mock(TerritorioInstrumentoService.class);
    private final ProcessoRejeicaoRepository processoRejeicaoRepository = mock(ProcessoRejeicaoRepository.class);
    private final ProcessoRejeicaoItemRepository processoRejeicaoItemRepository = mock(ProcessoRejeicaoItemRepository.class);
    private final IdusProcessoRepository idusProcessoRepository = mock(IdusProcessoRepository.class);
    private final SnitReferenciaRepository snitReferenciaRepository = mock(SnitReferenciaRepository.class);

    private final ProcessoConsultaService service = new ProcessoConsultaService(processoRepository, auditoriaRepository,
            autorizacaoService, contextoUtilizadorResolver, objectMapper, mock(ListaValoresRepository.class), utilizadorRepository,
            processoService, processoValidacaoService, new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper()), completudeDocumentalService,
            new CatalogoFormularios(), territorioInstrumentoService, processoRejeicaoRepository, processoRejeicaoItemRepository,
            idusProcessoRepository, snitReferenciaRepository, mock(AssociacaoInstrumentosService.class),
                mock(DinamicaAssociadaService.class), mock(DestinoProcessoService.class), mock(RegrasRedesDefesaSgifr.class),
                new RegrasTerritorioSgifr(mock(ListaValoresRepository.class), new ObjectMapper()), mock(RegrasFormularioRen.class));

    private Instrumento instrumento;
    private Dinamica dinamica;
    private InstrumentoProcedimentoConfig config;
    private Utilizador utilizador;
    private ContextoUtilizador contexto;
    private Entidade entidade;

    @BeforeEach
    void setUp() {
        TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        entidade = EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa");
        ReflectionTestUtils.setField(entidade, "id", 1L);
        instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", pdm);
        ReflectionTestUtils.setField(instrumento, "designacao", "PDM de Lisboa");
        ReflectionTestUtils.setField(instrumento, "numero", "900");

        utilizador = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM");
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());

        InstrumentoDinamicaConfig dinamicaConfig = new InstrumentoDinamicaConfig(pdm, new TipoDinamica("ALTERACAO", "Alteração"));
        dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizador);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());

        config = new InstrumentoProcedimentoConfig(dinamicaConfig, new TipoProcedimento("DECISAO", "Decisão"), 1, "SEGUNDA_H");
        ReflectionTestUtils.setField(config, "id", 10L);

        contexto = new ContextoUtilizador(utilizador.getId(), "INCM", null, entidade.getNipc(), "ENTIDADE", null, null, null, null, TipoEntidade.CM, entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
    }

    private Processo processo(EstadoProcesso estado) {
        Processo processo = new Processo(dinamica, config, utilizador, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setEstado(estado);
        return processo;
    }

    @Test
    void deveDevolver404QuandoProcessoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(processoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obterComVisibilidade(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devePropagarA404DaVisibilidadeQuandoOutraEntidade() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        doThrow(new RecursoNaoEncontradoException("Recurso não encontrado."))
                .when(autorizacaoService).verificarVisibilidade(contexto, processo);

        UUID id = processo.getId();

        assertThatThrownBy(() -> service.obterComVisibilidade(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolverDetalheComContextoParaReabrirORascunho() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        processo.setDadosFormulario("{\"concelho\":\"Sintra\"}");
        processo.setTipoAto(new TipoAto("PORT", "Portaria", 2));
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.id()).isEqualTo(processo.getId());
        assertThat(detalhe.estado()).isEqualTo("EM_SUBMISSAO");
        assertThat(detalhe.dadosFormulario()).isEqualTo("{\"concelho\":\"Sintra\"}");
        assertThat(detalhe.instrumentoId()).isEqualTo(instrumento.getId());
        assertThat(detalhe.designacaoInstrumento()).isEqualTo("PDM de Lisboa");
        assertThat(detalhe.numeroInstrumento()).isEqualTo("900");
        assertThat(detalhe.entidadeId()).isEqualTo(1L);
        assertThat(detalhe.tipoInstrumento()).isEqualTo("PDM");
        assertThat(detalhe.tipoDinamica()).isEqualTo("ALTERACAO");
        assertThat(detalhe.tipoProcedimento()).isEqualTo("DECISAO");
        assertThat(detalhe.serieDr()).isEqualTo("SEGUNDA_H");
        assertThat(detalhe.tipoAtoNome()).isEqualTo("Portaria");
        assertThat(detalhe.autorNome()).isEqualTo("Ana Ferreira");
    }

    @Test
    void deveDevolverNoDetalheOTerritorioDoInstrumento() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        TerritorioInstrumentoResponse territorio = new TerritorioInstrumentoResponse(
                List.of(new ItemTerritorio("1106", "Lisboa")), List.of(new ItemTerritorio("AML", "Área Metropolitana de Lisboa")));
        when(territorioInstrumentoService.territorio(processo.getDinamica())).thenReturn(territorio);

        assertThat(service.detalhe(processo.getId()).territorio()).isEqualTo(territorio);
    }

    @Test
    void deveDevolverNoDetalheOIdusDoProcesso() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        IdusProcesso idus = IdusProcesso.builder().processo(processo).idus("01.1111.01.001001.02.90001.01.031.00").build();
        when(idusProcessoRepository.findById(processo.getId())).thenReturn(Optional.of(idus));

        assertThat(service.detalhe(processo.getId()).idus()).isEqualTo("01.1111.01.001001.02.90001.01.031.00");
    }

    @Test
    void deveDevolverIdusNuloNoDetalheQuandoOProcessoNaoTemIdus() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(idusProcessoRepository.findById(processo.getId())).thenReturn(Optional.empty());

        assertThat(service.detalhe(processo.getId()).idus()).isNull();
    }

    @Test
    void aSubmissaoDoDetalheEADoIdusEnaoAOcorrencia() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        processo.setOcorrencia(2);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        IdusProcesso idus = IdusProcesso.builder().processo(processo).idus("01.1111.01.001001.02.90001.01.031.01")
                .submissao((short) 1).build();
        when(idusProcessoRepository.findById(processo.getId())).thenReturn(Optional.of(idus));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.submissao()).isEqualTo(1);
        assertThat(detalhe.ocorrencia()).isEqualTo(2);
        verify(auditoriaRepository, never()).countByTipoOcorrenciaAndIdOcorrenciaAndAcao(any(), any(), any());
    }

    @Test
    void depoisDeUmaRecusaASubmissaoDoDetalheEADoIdus() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        IdusProcesso idus = IdusProcesso.builder().processo(processo).idus("01.1111.01.001001.02.90001.01.031.02")
                .submissao((short) 2).build();
        when(idusProcessoRepository.findById(processo.getId())).thenReturn(Optional.of(idus));

        assertThat(service.detalhe(processo.getId()).submissao()).isEqualTo(2);
    }

    @Test
    void antesDaPrimeiraSubmissaoASubmissaoDoDetalheEZero() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        IdusProcesso idus = IdusProcesso.builder().processo(processo).idus("01.1111.01.001001.02.90001.01.031.00").build();
        when(idusProcessoRepository.findById(processo.getId())).thenReturn(Optional.of(idus));

        assertThat(service.detalhe(processo.getId()).submissao()).isZero();
    }

    @Test
    void semIdusASubmissaoDoDetalheContaAsSubmissoesDaAuditoria() {
        Processo processo = processo(EstadoProcesso.ENVIADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(idusProcessoRepository.findById(processo.getId())).thenReturn(Optional.empty());
        when(auditoriaRepository.countByTipoOcorrenciaAndIdOcorrenciaAndAcao("PROCESSO", processo.getId().toString(),
                "SUBMETER")).thenReturn(1L);

        assertThat(service.detalhe(processo.getId()).submissao()).isEqualTo(1);
    }

    @Test
    void deveDevolverListaVaziaNaRecusaDaIncmQuandoOsDadosDaAuditoriaSaoIlegiveis() {
        Processo processo = processo(EstadoProcesso.ANULADO_INCM);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        Auditoria auditoria = Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia(processo.getId().toString())
                .acao("REJEICAO_INCM")
                .dadosAlteracao("{isto não é json")
                .build();
        when(auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoInOrderByCriadoEmDescIdDesc(
                eq("PROCESSO"), eq(processo.getId().toString()), any())).thenReturn(Optional.of(auditoria));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.ultimaRejeicaoIncm()).isNotNull();
        assertThat(detalhe.ultimaRejeicaoIncm().mensagens()).isEmpty();
    }

    @Test
    void deveLerARecusaDaIncmDaTabelaDeRecusasSemIrAAuditoria() {
        Processo processo = processo(EstadoProcesso.ANULADO_INCM);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        Instant criadoEm = Instant.parse("2026-11-04T10:15:00Z");
        ProcessoRejeicao rejeicao = ProcessoRejeicao.builder().processo(processo).sequencia(2)
                .origem(OrigemRejeicao.INCM).motivoTexto("Primeira\nSegunda").criadoEm(criadoEm).build();
        when(processoRejeicaoRepository.findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(processo.getId(),
                OrigemRejeicao.INCM)).thenReturn(Optional.of(rejeicao));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.ultimaRejeicaoIncm().mensagens()).containsExactly("Primeira", "Segunda");
        assertThat(detalhe.ultimaRejeicaoIncm().data()).isEqualTo(criadoEm);
        verify(auditoriaRepository, never()).findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoInOrderByCriadoEmDescIdDesc(
                any(), any(), any());
    }

    @Test
    void deveDevolverNoDetalheAInformacaoBaseEOResultadoDaRegraDeItensRevistos() {
        Processo processo = processo(EstadoProcesso.EM_VALIDACAO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        List<InformacaoBaseResponse> informacaoBase = List.of(
                new InformacaoBaseResponse(UUID.randomUUID(), ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.VALIDADO),
                new InformacaoBaseResponse(UUID.randomUUID(), ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.POR_VALIDAR));
        when(processoValidacaoService.informacaoBase(processo)).thenReturn(informacaoBase);
        when(processoValidacaoService.todosRevistos(processo)).thenReturn(false);

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.informacaoBase()).isEqualTo(informacaoBase);
        assertThat(detalhe.todosItensRevistos()).isFalse();

        when(processoValidacaoService.todosRevistos(processo)).thenReturn(true);

        assertThat(service.detalhe(processo.getId()).todosItensRevistos()).isTrue();
    }

    @Test
    void deveDevolverNoDetalheAsAcoesDisponiveisEOEditavelDoChamador() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(processoService.editavel(processo, contexto)).thenReturn(true);
        when(processoService.acoesDisponiveis(processo, contexto)).thenReturn(List.of("submeter"));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.editavel()).isTrue();
        assertThat(detalhe.acoesDisponiveis()).containsExactly("submeter");
    }

    private void autorDaSessao(ClaimsUtilizador.AtosAutorizados atosAutorizados, Long drParteId) {
        ContextoUtilizador autor = new ContextoUtilizador(utilizador.getId(), "INCM", null, "501234567", "ENTIDADE",
                atosAutorizados, null, "2015482", drParteId, TipoEntidade.CM, entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(autor);
    }

    private Processo processoComDadosIncmCompletos(InstrumentoProcedimentoConfig configDoProcesso) {
        Processo processo = new Processo(dinamica, configDoProcesso, utilizador, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setEstado(EstadoProcesso.EM_SUBMISSAO);
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.of(2026, 9, 1));
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        processo.setTipoAto(new TipoAto("AVISO", "Aviso", 1));
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(processoService.editavel(eq(processo), any())).thenReturn(true);
        return processo;
    }

    @Test
    void deveDevolverPodeSubmeterFalsoSemTipoAto() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setTipoAto(null);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isFalse();
    }

    private InstrumentoProcedimentoConfig configComSerie(String serieDr) {
        InstrumentoProcedimentoConfig outra = new InstrumentoProcedimentoConfig(dinamica.getInstrumentoDinamicaConfig(),
                new TipoProcedimento("DECISAO", "Decisão"), 1, serieDr);
        ReflectionTestUtils.setField(outra, "id", 11L);
        return outra;
    }

    @Test
    void deveDevolverPodeSubmeterVerdadeiroComOsDadosIncmDaSegundaSerieCompletos() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isTrue();
    }

    @Test
    void deveDevolverPodeSubmeterFalsoSemNormaLegalNaSegundaSerie() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setNormaLegal(null);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isFalse();
    }

    @Test
    void deveDevolverPodeSubmeterFalsoSemDataDeAssinatura() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setDataAssinatura(null);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isFalse();
    }

    @Test
    void deveDevolverPodeSubmeterFalsoQuandoASessaoNaoTemDrParteIdNaSegundaSerie() {
        autorDaSessao(null, null);
        Processo processo = processoComDadosIncmCompletos(config);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isFalse();
    }

    @Test
    void deveDevolverPodeSubmeterFalsoNaPrimeiraSerieComSuplementoSemData() {
        autorDaSessao(null, null);
        Processo processo = processoComDadosIncmCompletos(configComSerie("PRIMEIRA"));
        processo.setPublicarSuplemento(true);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isFalse();

        processo.setDataSuplemento(LocalDate.of(2026, 9, 2));

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isTrue();
    }

    @Test
    void deveDevolverPodeSubmeterFalsoQuandoOChamadorNaoPodeEditar() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        when(processoService.editavel(eq(processo), any())).thenReturn(false);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isFalse();
    }

    @Test
    void deveDevolverPodeSubmeterFalsoQuandoOExtratoNaoEPermitidoPeloTipoDeAto() {
        TipoAto tipoAto = new TipoAto("COD-ATO", "Ato de Teste", 1);
        tipoAto.setIncmActTypeIdSerie2(701L);
        autorDaSessao(new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(701L, null, false))), 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setTipoAto(tipoAto);
        processo.setTipoInformacao("EXTRATO");

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isFalse();

        autorDaSessao(new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(701L, null, true))), 1234567891234567L);

        assertThat(service.detalhe(processo.getId()).podeSubmeter()).isTrue();
    }

    @Test
    void deveDevolverPodeSubmeterFalsoQuandoFaltamDocumentosObrigatorios() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        DocumentoConfig textoDoAto = itemDocumental("TEXTO_ATO_PUB", "Texto do Ato");
        when(completudeDocumentalService.pendencias(processo)).thenReturn(List.of(new Item(textoDoAto, List.of())));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.podeSubmeter()).isFalse();
        assertThat(detalhe.pendenciasSubmissao()).containsExactly(
                new PendenciaSubmissaoResponse(Pagina.DOCUMENTOS, "TEXTO_ATO_PUB", "Falta carregar: Texto do Ato."));
    }

    private static DocumentoConfig itemDocumental(String codigo, String nome) {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getCodigo()).thenReturn(codigo);
        when(item.getNome()).thenReturn(nome);
        return item;
    }

    @Test
    void semPendenciasOProcessoEditavelPodeSerSubmetido() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.pendenciasSubmissao()).isEmpty();
        assertThat(detalhe.podeSubmeter()).isTrue();
    }

    @Test
    void aDataDeAssinaturaEmFaltaEDosDocumentosComAMensagemDaSubmissao() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setDataAssinatura(null);

        assertThat(service.detalhe(processo.getId()).pendenciasSubmissao()).containsExactly(new PendenciaSubmissaoResponse(
                Pagina.DOCUMENTOS, "dataAssinatura", "Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato."));
    }

    private ProcessoConsultaService serviceComItensDocumentais(InstrumentoProcedimentoConfig configDoProcesso,
                                                              DocumentoConfig... itens) {
        DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(configDoProcesso))
                .thenReturn(List.of(itens));
        return serviceComDocumentoConfigs(documentoConfigRepository);
    }

    private ProcessoConsultaService serviceComDocumentoConfigs(DocumentoConfigRepository documentoConfigRepository) {
        ValidadorSubmissaoProcesso validador = new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class),
                new ObjectMapper(), new CatalogoFormularios(), null, null, documentoConfigRepository, null);
        return new ProcessoConsultaService(processoRepository, auditoriaRepository, autorizacaoService,
                contextoUtilizadorResolver, objectMapper, mock(ListaValoresRepository.class), utilizadorRepository,
                processoService, processoValidacaoService, validador, completudeDocumentalService, new CatalogoFormularios(),
                territorioInstrumentoService, processoRejeicaoRepository, processoRejeicaoItemRepository,
                idusProcessoRepository, snitReferenciaRepository, mock(AssociacaoInstrumentosService.class),
                mock(DinamicaAssociadaService.class), mock(DestinoProcessoService.class), mock(RegrasRedesDefesaSgifr.class),
                new RegrasTerritorioSgifr(mock(ListaValoresRepository.class), new ObjectMapper()), mock(RegrasFormularioRen.class));
    }

    private static DocumentoConfig itemInformacaoBase(ElementoInformacaoBase elemento) {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getPagina()).thenReturn(PaginaDocumento.DOCUMENTOS);
        when(item.getCategoria()).thenReturn(CategoriaDocumento.PUBLICACAO);
        when(item.getElementoInformacaoBase()).thenReturn(elemento);
        return item;
    }

    /** Procedimento com a Deliberação e o Texto do Ato; a data do Texto do Ato é `data_assinatura` (2026-09-01). */
    private ProcessoDetalheResponse detalheComInformacaoBase(String tipoProcedimento, String informacaoBase) {
        autorDaSessao(null, 1234567891234567L);
        InstrumentoProcedimentoConfig configDoProcesso = new InstrumentoProcedimentoConfig(
                dinamica.getInstrumentoDinamicaConfig(), new TipoProcedimento(tipoProcedimento, tipoProcedimento), 1, "SEGUNDA_H");
        Processo processo = processoComDadosIncmCompletos(configDoProcesso);
        processo.setDadosFormulario("{\"informacaoBase\":" + informacaoBase + "}");
        List<DocumentoConfig> itens = List.of(itemInformacaoBase(ElementoInformacaoBase.DELIBERACAO),
                itemInformacaoBase(ElementoInformacaoBase.TEXTO_ATO));
        DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(configDoProcesso))
                .thenReturn(itens);
        when(documentoConfigRepository.findElementosInformacaoBaseAtivos(configDoProcesso))
                .thenReturn(Set.of(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO));
        return serviceComDocumentoConfigs(documentoConfigRepository).detalhe(processo.getId());
    }

    @Test
    void naDecisaoDeInicioOPrazoDiferenteDaDeliberacaoEPendenciaDosDocumentos() {
        ProcessoDetalheResponse detalhe = detalheComInformacaoBase("DECISAO_INICIO",
                "{\"deliberacao\":{\"prazoMeses\":12},\"textoAto\":{\"prazoMeses\":24}}");

        assertThat(detalhe.pendenciasSubmissao()).containsExactly(new PendenciaSubmissaoResponse(Pagina.DOCUMENTOS,
                "informacaoBase.textoAto.prazoAnos", "O prazo tem de ser o mesmo da Deliberação."));
        assertThat(detalhe.podeSubmeter()).isFalse();
    }

    @Test
    void aAvaliacaoAmbientalDiferenteEADataDaDeliberacaoDepoisDoTextoDoAtoSaoPendenciasDosDocumentos() {
        ProcessoDetalheResponse detalhe = detalheComInformacaoBase("DECISAO_INICIO",
                "{\"deliberacao\":{\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2026-09-02\"},"
                        + "\"textoAto\":{\"avaliacaoAmbiental\":false}}");

        assertThat(detalhe.pendenciasSubmissao()).containsExactly(
                new PendenciaSubmissaoResponse(Pagina.DOCUMENTOS, "informacaoBase.textoAto.avaliacaoAmbiental",
                        "A avaliação ambiental tem de ser a mesma da Deliberação."),
                new PendenciaSubmissaoResponse(Pagina.DOCUMENTOS, "informacaoBase.deliberacao.dataDeliberacao",
                        "A data da deliberação tem de ser igual ou anterior à data do texto do ato."));
        assertThat(detalhe.podeSubmeter()).isFalse();
    }

    @Test
    void naAberturaDaDiscussaoPublicaADataDaDeliberacaoDepoisDaDataLimiteEPendenciaDosDocumentos() {
        dinamica.setDataCaducidade(LocalDate.of(2026, 8, 31));

        ProcessoDetalheResponse detalhe = detalheComInformacaoBase("ABERTURA_DISCUSSAO_PUBLICA",
                "{\"deliberacao\":{\"dataDeliberacao\":\"2026-09-01\"}}");

        assertThat(detalhe.pendenciasSubmissao()).containsExactly(new PendenciaSubmissaoResponse(Pagina.DOCUMENTOS,
                "informacaoBase.deliberacao.dataDeliberacao", "Posterior à data limite da dinâmica (31/08/2026)."));
        assertThat(detalhe.podeSubmeter()).isFalse();
    }

    @Test
    void naDecisaoDeInicioNaoHaDataLimiteNemPendenciaDela() {
        dinamica.setDataCaducidade(LocalDate.of(2026, 8, 31));

        ProcessoDetalheResponse detalhe = detalheComInformacaoBase("DECISAO_INICIO",
                "{\"deliberacao\":{\"dataDeliberacao\":\"2026-09-01\"}}");

        assertThat(detalhe.pendenciasSubmissao()).isEmpty();
        assertThat(detalhe.podeSubmeter()).isTrue();
    }

    @Test
    void comUmDosLadosPorPreencherOuOsValoresCoerentesNaoHaPendenciaDeCoerencia() {
        assertThat(detalheComInformacaoBase("DECISAO_INICIO",
                "{\"deliberacao\":{\"prazoMeses\":12,\"avaliacaoAmbiental\":true},\"textoAto\":{}}").pendenciasSubmissao())
                .isEmpty();
        ProcessoDetalheResponse coerente = detalheComInformacaoBase("DECISAO_INICIO",
                "{\"deliberacao\":{\"prazoMeses\":12,\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2026-09-01\"},"
                        + "\"textoAto\":{\"prazoMeses\":12,\"avaliacaoAmbiental\":true}}");
        assertThat(coerente.pendenciasSubmissao()).isEmpty();
        assertThat(coerente.podeSubmeter()).isTrue();
    }

    @Test
    void semInformacaoBaseDoTextoDoAtoADataDeAssinaturaEmFaltaEDoFormulario() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setDataAssinatura(null);

        ProcessoDetalheResponse detalhe = serviceComItensDocumentais(config,
                itemInformacaoBase(ElementoInformacaoBase.DELIBERACAO)).detalhe(processo.getId());

        assertThat(detalhe.dataAssinaturaNoFormulario()).isTrue();
        assertThat(detalhe.pendenciasSubmissao()).containsExactly(new PendenciaSubmissaoResponse(
                Pagina.FORMULARIO, "dataAssinatura", "Indique a data de assinatura do ato."));
        assertThat(detalhe.podeSubmeter()).isFalse();
    }

    @Test
    void comInformacaoBaseDoTextoDoAtoADataDeAssinaturaContinuaNosDocumentos() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setDataAssinatura(null);

        ProcessoDetalheResponse detalhe = serviceComItensDocumentais(config,
                itemInformacaoBase(ElementoInformacaoBase.TEXTO_ATO)).detalhe(processo.getId());

        assertThat(detalhe.dataAssinaturaNoFormulario()).isFalse();
        assertThat(detalhe.pendenciasSubmissao()).containsExactly(new PendenciaSubmissaoResponse(Pagina.DOCUMENTOS,
                "dataAssinatura", "Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato."));
    }

    @Test
    void naRatificacaoSemInformacaoBaseADataDeAssinaturaEmFaltaApareceUmaSoVez() {
        autorDaSessao(null, 1234567891234567L);
        InstrumentoProcedimentoConfig ratificacao = new InstrumentoProcedimentoConfig(dinamica.getInstrumentoDinamicaConfig(),
                new TipoProcedimento("RATIFICACAO", "Ratificação"), 1, "SEGUNDA_H");
        Processo processo = processoComDadosIncmCompletos(ratificacao);
        processo.setDataAssinatura(null);

        ProcessoDetalheResponse detalhe = serviceComItensDocumentais(ratificacao).detalhe(processo.getId());

        assertThat(detalhe.dataAssinaturaNoFormulario()).isTrue();
        assertThat(detalhe.pendenciasSubmissao())
                .filteredOn(pendencia -> "dataAssinatura".equals(pendencia.campo()))
                .containsExactly(new PendenciaSubmissaoResponse(
                        Pagina.FORMULARIO, "dataAssinatura", "Indique a data de assinatura do ato."));
    }

    @Test
    void osDadosDaSessaoINCMEmFaltaFicamNoFormularioComAMensagemAtual() {
        autorDaSessao(null, null);
        Processo processo = processoComDadosIncmCompletos(config);

        assertThat(service.detalhe(processo.getId()).pendenciasSubmissao()).containsExactly(new PendenciaSubmissaoResponse(
                Pagina.FORMULARIO, "drParteId", "Sessão sem PartId da adesão INCM, não é possível submeter."));
    }

    @Test
    void osElementosAfetadosEmFaltaFicamNoFormulario() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setElementosAfetados(List.of());

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.pendenciasSubmissao()).containsExactly(
                new PendenciaSubmissaoResponse(Pagina.FORMULARIO, "elementosAfetados", "Indique os elementos fundamentais afetados."));
        assertThat(detalhe.podeSubmeter()).isFalse();
    }

    @Test
    void asPendenciasDasPlantasEDosMetadadosFicamNasSuasPaginasPelaOrdemDoAssistente() {
        autorDaSessao(null, 1234567891234567L);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setDataAssinatura(null);
        CampoErro conjunto = new CampoErro("metadados.conjunto.1", "Planta de condicionantes: falta a Folha N na tabela Plantas SAIGT SNIT.");
        CampoErro linha = new CampoErro("plantas.linhas[0]", "Associe um TIFF a «Planta de Ordenamento – folha A».");
        when(completudeDocumentalService.pendenciasMetadados(processo)).thenReturn(List.of(conjunto));
        when(completudeDocumentalService.pendenciasPlantas(processo)).thenReturn(List.of(linha));
        DocumentoConfig deliberacao = itemDocumental("DELIBERACAO_PUB", "Deliberação");
        when(completudeDocumentalService.pendencias(processo)).thenReturn(List.of(new Item(deliberacao, List.of())));
        processo.setElementosAfetados(null);

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.pendenciasSubmissao())
                .extracting(PendenciaSubmissaoResponse::pagina, PendenciaSubmissaoResponse::campo, PendenciaSubmissaoResponse::erro)
                .containsExactly(
                        tuple(Pagina.FORMULARIO, "elementosAfetados", "Indique os elementos fundamentais afetados."),
                        tuple(Pagina.PLANTAS, linha.path(), linha.erro()),
                        tuple(Pagina.DOCUMENTOS, "dataAssinatura", "Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato."),
                        tuple(Pagina.DOCUMENTOS, "DELIBERACAO_PUB", "Falta carregar: Deliberação."),
                        tuple(Pagina.METADADOS, conjunto.path(), conjunto.erro()));
        assertThat(detalhe.podeSubmeter()).isFalse();
    }

    @Test
    void quemNaoPodeEditarNaoRecebePendenciasNemPodeSubmeter() {
        autorDaSessao(null, null);
        Processo processo = processoComDadosIncmCompletos(config);
        processo.setDataAssinatura(null);
        when(processoService.editavel(eq(processo), any())).thenReturn(false);
        DocumentoConfig textoDoAto = itemDocumental("TEXTO_ATO_PUB", "Texto do Ato");
        when(completudeDocumentalService.pendencias(processo)).thenReturn(List.of(new Item(textoDoAto, List.of())));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.pendenciasSubmissao()).isEmpty();
        assertThat(detalhe.podeSubmeter()).isFalse();
        verify(completudeDocumentalService, never()).pendencias(processo);
    }

    @Test
    void deveDevolverDetalheSemTipoAto() {
        Processo processo = processo(EstadoProcesso.PUBLICADO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.tipoAtoId()).isNull();
        assertThat(detalhe.tipoAtoNome()).isNull();
        assertThat(detalhe.tiposAtoDisponiveis()).isEmpty();
    }

    @Test
    void deveDevolverNoDetalheOsTiposAtoDisponiveisDaSessao() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        TipoAto aviso = new TipoAto("AVISO", "Aviso", 1);
        ReflectionTestUtils.setField(aviso, "id", 7L);
        when(processoService.tiposAtoDisponiveis(processo, contexto)).thenReturn(List.of(aviso));

        ProcessoDetalheResponse detalhe = service.detalhe(processo.getId());

        assertThat(detalhe.tiposAtoDisponiveis()).containsExactly(new TipoAtoDisponivelResponse(7L, "AVISO", "Aviso"));
        assertThat(detalhe.camposBloqueados()).doesNotContain("tipoAtoId");
    }

    @Test
    void oDetalheTrazASerieEfetivaEAsSeriesEscolhiveisSoSemSerieNoProcedimento() {
        Processo comSerieFixa = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(comSerieFixa.getId())).thenReturn(Optional.of(comSerieFixa));
        ProcessoDetalheResponse fixa = service.detalhe(comSerieFixa.getId());
        assertThat(fixa.serieDr()).isEqualTo("SEGUNDA_H");
        assertThat(fixa.seriesDrEscolhiveis()).isNull();

        Processo semSerie = new Processo(dinamica, configComSerie(null), utilizador, entidade);
        ReflectionTestUtils.setField(semSerie, "id", UUID.randomUUID());
        when(processoRepository.findById(semSerie.getId())).thenReturn(Optional.of(semSerie));
        ProcessoDetalheResponse porEscolher = service.detalhe(semSerie.getId());
        assertThat(porEscolher.serieDr()).isNull();
        assertThat(porEscolher.seriesDrEscolhiveis()).containsExactly("PRIMEIRA", "SEGUNDA_H", "SEGUNDA_C");

        semSerie.setSerieDr("SEGUNDA_C");
        assertThat(service.detalhe(semSerie.getId()).serieDr()).isEqualTo("SEGUNDA_C");
    }

    @Test
    void deveDevolver404NoDetalheQuandoSemVisibilidade() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        doThrow(new RecursoNaoEncontradoException("Recurso não encontrado."))
                .when(autorizacaoService).verificarVisibilidade(contexto, processo);

        UUID id = processo.getId();

        assertThatThrownBy(() -> service.detalhe(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolverHistoricoPaginadoDaAuditoria() {
        Processo processo = processo(EstadoProcesso.ENVIADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        Auditoria a1 = Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia(processo.getId().toString())
                .acao("SUBMETER")
                .estadoAntes("EM_SUBMISSAO")
                .estadoDepois("ENVIADO_DGT")
                .utilizadorId(utilizador.getId())
                .entidadeId(1L)
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaOrderByCriadoEmDescIdDesc("PROCESSO", processo.getId().toString(), pageable))
                .thenReturn(new PageImpl<>(List.of(a1), pageable, 1));

        Page<HistoricoProcessoResponse> resultado = service.historico(processo.getId(), pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).acao()).isEqualTo("SUBMETER");
    }

    @Test
    void deveResolverNomeDoUtilizadorNoHistoricoESoDeixarNuloNasAcoesDoSistema() {
        Processo processo = processo(EstadoProcesso.ENVIADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        Auditoria doUtilizador = Auditoria.builder().tipoOcorrencia("PROCESSO").idOcorrencia(processo.getId().toString())
                .acao("SUBMETER").utilizadorId(utilizador.getId()).entidadeId(1L).build();
        Auditoria doSistema = Auditoria.builder().tipoOcorrencia("PROCESSO").idOcorrencia(processo.getId().toString())
                .acao("EXPIRAR").entidadeId(1L).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaOrderByCriadoEmDescIdDesc("PROCESSO", processo.getId().toString(), pageable))
                .thenReturn(new PageImpl<>(List.of(doUtilizador, doSistema), pageable, 2));
        when(utilizadorRepository.findAllById(Set.of(utilizador.getId()))).thenReturn(List.of(utilizador));

        Page<HistoricoProcessoResponse> resultado = service.historico(processo.getId(), pageable);

        assertThat(resultado.getContent()).extracting(HistoricoProcessoResponse::utilizadorNome)
                .containsExactly("Ana Ferreira", null);
    }

    @Test
    void deveDevolver403NoHistoricoQuandoSemVisibilidade() {
        Processo processo = processo(EstadoProcesso.ENVIADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        doThrow(new AcaoNaoAutorizadaException("sem visibilidade"))
                .when(autorizacaoService).verificarVisibilidade(any(), any());

        UUID id = processo.getId();
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> service.historico(id, pageable))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    private static final Instant T0 = Instant.parse("2026-03-02T10:00:00Z");

    private void submissoesEm(Processo processo, Instant... momentos) {
        when(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "PROCESSO", processo.getId().toString(), "SUBMETER"))
                .thenReturn(Arrays.stream(momentos)
                        .map(momento -> Auditoria.builder().acao("SUBMETER").criadoEm(momento).build())
                        .toList());
    }

    private static ProcessoRejeicao recusa(long id, int sequencia, OrigemRejeicao origem, Instant criadoEm, String motivoTexto) {
        return ProcessoRejeicao.builder().id(id).sequencia(sequencia).origem(origem).criadoEm(criadoEm)
                .motivoTexto(motivoTexto).build();
    }

    @Test
    void deveDarACadaRecusaASubmissaoRecusadaEPartirOMotivoIncmPorLinhas() {
        Processo processo = processo(EstadoProcesso.REJEITADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        submissoesEm(processo, T0, T0.plusSeconds(20), T0.plusSeconds(40));
        when(processoRejeicaoRepository.findByProcesso_IdOrderBySequenciaAsc(processo.getId())).thenReturn(List.of(
                recusa(11, 1, OrigemRejeicao.DGT, T0.plusSeconds(10), null),
                recusa(12, 2, OrigemRejeicao.INCM, T0.plusSeconds(30), "ImageURLList cannot be empty.\nActDate inválida."),
                recusa(13, 3, OrigemRejeicao.DGT, T0.plusSeconds(50), null)));
        when(processoRejeicaoItemRepository.findByRejeicao_IdInOrderByIdAsc(List.of(11L, 12L, 13L))).thenReturn(List.of());

        EstatisticasProcessoResponse resposta = service.estatisticas(processo.getId(), null, null, null);

        assertThat(resposta.numeroSubmissoes()).isEqualTo(3);
        assertThat(resposta.recusas()).extracting(RecusaResponse::sequencia, RecusaResponse::origem, RecusaResponse::submissao)
                .containsExactly(tuple(1, OrigemRejeicao.DGT, 1), tuple(2, OrigemRejeicao.INCM, 2), tuple(3, OrigemRejeicao.DGT, 3));
        assertThat(resposta.recusas().get(1).motivoIncm()).containsExactly("ImageURLList cannot be empty.", "ActDate inválida.");
        assertThat(resposta.recusas().get(0).motivoIncm()).isEmpty();
        assertThat(resposta.recusas()).allSatisfy(recusa -> assertThat(recusa.itens()).isEmpty());
        verify(processoRejeicaoItemRepository, times(1)).findByRejeicao_IdInOrderByIdAsc(any());
    }

    @Test
    void deveFiltrarPelaSubmissaoEPelaDataDaRecusaNoFusoDeLisboa() {
        Processo processo = processo(EstadoProcesso.REJEITADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        Instant primeira = Instant.parse("2026-03-02T23:30:00Z");
        Instant segunda = Instant.parse("2026-07-10T23:30:00Z");
        submissoesEm(processo, primeira.minusSeconds(60), segunda.minusSeconds(60));
        when(processoRejeicaoRepository.findByProcesso_IdOrderBySequenciaAsc(processo.getId())).thenReturn(List.of(
                recusa(21, 1, OrigemRejeicao.DGT, primeira, null),
                recusa(22, 2, OrigemRejeicao.DGT, segunda, null)));
        when(processoRejeicaoItemRepository.findByRejeicao_IdInOrderByIdAsc(any())).thenReturn(List.of());

        assertThat(service.estatisticas(processo.getId(), 2, null, null).recusas())
                .extracting(RecusaResponse::sequencia).containsExactly(2);
        // 10/07 às 23:30 UTC já é dia 11 em Lisboa (hora de verão).
        assertThat(service.estatisticas(processo.getId(), null, LocalDate.of(2026, 7, 11), null).recusas())
                .extracting(RecusaResponse::sequencia).containsExactly(2);
        assertThat(service.estatisticas(processo.getId(), null, null, LocalDate.of(2026, 3, 2)).recusas())
                .extracting(RecusaResponse::sequencia).containsExactly(1);
        EstatisticasProcessoResponse depoisDeTodas = service.estatisticas(processo.getId(), null, LocalDate.of(2026, 7, 12), null);
        assertThat(depoisDeTodas.recusas()).isEmpty();
        assertThat(depoisDeTodas.numeroSubmissoes()).isEqualTo(2);
    }

    @Test
    void semRecusasNaoConsultaOsItens() {
        Processo processo = processo(EstadoProcesso.ENVIADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        submissoesEm(processo, T0);
        when(processoRejeicaoRepository.findByProcesso_IdOrderBySequenciaAsc(processo.getId())).thenReturn(List.of());

        EstatisticasProcessoResponse resposta = service.estatisticas(processo.getId(), null, null, null);

        assertThat(resposta.numeroSubmissoes()).isEqualTo(1);
        assertThat(resposta.recusas()).isEmpty();
        verify(processoRejeicaoItemRepository, never()).findByRejeicao_IdInOrderByIdAsc(any());
    }

    @Test
    void naoLeAsRecusasDeUmProcessoForaDaVisibilidade() {
        Processo processo = processo(EstadoProcesso.REJEITADO_DGT);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        doThrow(new RecursoNaoEncontradoException("Processo não encontrado.")).when(autorizacaoService)
                .verificarVisibilidade(contexto, processo);

        assertThatThrownBy(() -> service.estatisticas(processo.getId(), null, null, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(processoRejeicaoRepository, never()).findByProcesso_IdOrderBySequenciaAsc(any());
    }

    @Test
    void deveListarSemLerODadosFormularioAntigo() {
        Processo processo = processo(EstadoProcesso.PUBLICADO);
        processo.setDadosFormulario("{\"designacao\":\"PDM de Lisboa 2026\",\"concelho\":\"Lisboa\"}");
        Pageable pageable = PageRequest.of(0, 20);
        when(processoRepository.findAll(ArgumentMatchers.<Specification<Processo>>any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(processo), pageable, 1));

        Page<ProcessoListagemResponse> pagina = service.listar(
                new ProcessoFiltro(null, null, null, null, null, null, null, null, null, null, null, null, null, null), pageable);

        assertThat(pagina.getContent().get(0).designacaoInstrumento()).isEqualTo("PDM de Lisboa");
        assertThat(ProcessoListagemResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("designacao", "concelho");
    }

    @Test
    void deveRecusarComValidacaoOGrupoQueNaoSeAplicaAoPerfilInterno() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(
                new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, null, null, null, null, null, null, null));
        ProcessoFiltro filtro = new ProcessoFiltro(null, null, null, null, null, null, null, GrupoProcesso.EM_CURSO,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.listar(filtro, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(ValidacaoException.class, excecao ->
                        assertThat(excecao.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("grupo"));
        verify(processoRepository, never()).findAll(ArgumentMatchers.<Specification<Processo>>any(), any(Pageable.class));
    }

    @Test
    void deveListarComGrupoAplicavelAoContexto() {
        Pageable pageable = PageRequest.of(0, 20);
        when(processoRepository.findAll(ArgumentMatchers.<Specification<Processo>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(processo(EstadoProcesso.EM_SUBMISSAO)), pageable, 1));

        Page<ProcessoListagemResponse> pagina = service.listar(
                new ProcessoFiltro(null, null, null, null, null, null, null, GrupoProcesso.EM_CURSO, null, null, null, null, null, null), pageable);

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).entidadeId()).isEqualTo(1L);
    }

    @Test
    void deveDevolverOsGruposDoUtilizadorExternoComContagemEPagina() {
        Pageable pagina = PageRequest.of(0, 5);
        when(processoRepository.findAll(ArgumentMatchers.<Specification<Processo>>any(), eq(pagina)))
                .thenReturn(new PageImpl<>(List.of(processo(EstadoProcesso.PUBLICADO)), pagina, 8));

        PainelProcessosResponse resposta = service.painel(null, pagina);

        assertThat(resposta.grupos()).extracting(GrupoPainelResponse::grupo).containsExactly(
                GrupoProcesso.PUBLICADOS, GrupoProcesso.EM_CURSO, GrupoProcesso.PARA_CORRECAO, GrupoProcesso.EM_VALIDACAO);
        assertThat(resposta.grupos()).allSatisfy(grupo -> {
            assertThat(grupo.contagem()).isEqualTo(8L);
            assertThat(grupo.processos()).hasSize(1);
        });
        verify(processoRepository, times(4)).findAll(ArgumentMatchers.<Specification<Processo>>any(), eq(pagina));
    }

    @Test
    void deveDevolverSoPublicadosEEmValidacaoAoPerfilInterno() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(
                new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, null, null, null, null, null, null, null));
        Pageable pagina = PageRequest.of(0, 5);
        when(processoRepository.findAll(ArgumentMatchers.<Specification<Processo>>any(), eq(pagina)))
                .thenReturn(new PageImpl<>(List.of(), pagina, 0));

        PainelProcessosResponse resposta = service.painel(null, pagina);

        assertThat(resposta.grupos()).extracting(GrupoPainelResponse::grupo)
                .containsExactly(GrupoProcesso.PUBLICADOS, GrupoProcesso.EM_VALIDACAO);
        verify(processoRepository, times(2)).findAll(ArgumentMatchers.<Specification<Processo>>any(), eq(pagina));
    }

    @Test
    void deveDevolverSoOGrupoPedidoNoPainel() {
        Pageable pagina = PageRequest.of(1, 5);
        when(processoRepository.findAll(ArgumentMatchers.<Specification<Processo>>any(), eq(pagina)))
                .thenReturn(new PageImpl<>(List.of(), pagina, 6));

        PainelProcessosResponse resposta = service.painel(GrupoProcesso.EM_VALIDACAO, pagina);

        assertThat(resposta.grupos()).singleElement().satisfies(grupo -> {
            assertThat(grupo.grupo()).isEqualTo(GrupoProcesso.EM_VALIDACAO);
            assertThat(grupo.contagem()).isEqualTo(6L);
        });
    }

    @Test
    void deveRecusarComValidacaoNoPainelOGrupoQueNaoSeAplicaAoPerfilInterno() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(
                new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.painel(GrupoProcesso.PARA_CORRECAO, PageRequest.of(0, 5)))
                .isInstanceOfSatisfying(ValidacaoException.class, excecao ->
                        assertThat(excecao.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("grupo"));
        verify(processoRepository, never()).findAll(ArgumentMatchers.<Specification<Processo>>any(), any(Pageable.class));
    }

    @Test
    void deveDevolverNoDetalheADataLimiteDaDinamicaOuNula() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));

        assertThat(service.detalhe(processo.getId()).dataCaducidadeDinamica()).isNull();

        processo.getDinamica().setDataCaducidade(java.time.LocalDate.of(2027, 3, 31));
        assertThat(service.detalhe(processo.getId()).dataCaducidadeDinamica()).isEqualTo(java.time.LocalDate.of(2027, 3, 31));
    }
}
