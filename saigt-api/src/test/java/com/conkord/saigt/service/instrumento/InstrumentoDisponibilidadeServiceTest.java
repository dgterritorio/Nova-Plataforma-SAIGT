package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.AtoReferivelResponse;
import com.conkord.saigt.dto.instrumento.DinamicaEmCursoResponse;
import com.conkord.saigt.dto.instrumento.InstrumentoDisponibilidadeResponse;
import com.conkord.saigt.dto.instrumento.ProcedimentoDisponivelResponse;
import com.conkord.saigt.dto.instrumento.TipoAtoDisponivelResponse;
import com.conkord.saigt.dto.instrumento.UltimaDinamicaPublicadaResponse;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.regras.MotorRegrasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstrumentoDisponibilidadeServiceTest {

    private final InstrumentoService instrumentoService = mock(InstrumentoService.class);
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository = mock(InstrumentoDinamicaConfigRepository.class);
    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository =
            mock(InstrumentoProcedimentoConfigRepository.class);
    private final PermissaoAcaoRepository permissaoAcaoRepository = mock(PermissaoAcaoRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
    private final TipoAtoRepository tipoAtoRepository = mock(TipoAtoRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final MotorRegrasService motorRegrasService = mock(MotorRegrasService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);

    private final InstrumentoDisponibilidadeService service = new InstrumentoDisponibilidadeService(instrumentoService,
            instrumentoDinamicaConfigRepository, dinamicaRepository, instrumentoProcedimentoConfigRepository, motorRegrasService,
            contextoUtilizadorResolver, new ProcedimentosDisponiveisService(instrumentoProcedimentoConfigRepository,
            permissaoAcaoRepository, processoRepository, documentoConfigRepository, tipoAtoRepository, autorizacaoService,
            new CatalogoFormularios(), new AtosReferiveis(processoRepository)));

    private TipoInstrumento pdm;
    private Entidade entidade;
    private Instrumento instrumento;
    private UUID instrumentoId;
    private Utilizador criadoPor;

    @BeforeEach
    void setUp() {
        when(autorizacaoService.tipoEntidadeDaSessao(any())).thenAnswer(invocacao -> {
            ContextoUtilizador contexto = invocacao.getArgument(0);
            return contexto.perfilInterno() != null ? Optional.empty() : Optional.ofNullable(contexto.tipoEntidade());
        });
        when(autorizacaoService.tipoEntidadeQueCria(any())).thenCallRealMethod();
        when(autorizacaoService.processoVisivel(any(), any())).thenReturn(true);
        pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ReflectionTestUtils.setField(pdm, "id", 1L);
        entidade = EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa");
        ReflectionTestUtils.setField(entidade, "id", 1L);
        criadoPor = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM");
        instrumentoId = UUID.randomUUID();
        instrumento = new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor);
        ReflectionTestUtils.setField(instrumento, "id", instrumentoId);

        when(instrumentoService.obter(instrumentoId)).thenReturn(instrumento);
        when(instrumentoDinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(pdm)).thenReturn(List.of());
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of());
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of());
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of());
    }

    private ContextoUtilizador contextoEntidade(String nif, ClaimsUtilizador.AtosAutorizados atos, String drParteLetra) {
        return new ContextoUtilizador(UUID.randomUUID(), "INCM", null, nif, "ENTIDADE", atos, drParteLetra, null, null, TipoEntidade.CM, null);
    }

    private InstrumentoDinamicaConfig criarDinamicaConfig(long id, String codigoTipoDinamica) {
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(pdm, new TipoDinamica(codigoTipoDinamica, codigoTipoDinamica));
        ReflectionTestUtils.setField(config, "id", id);
        return config;
    }

    private InstrumentoProcedimentoConfig criarProcedimentoConfig(long id, InstrumentoDinamicaConfig dinamicaConfig,
                                                                    String codigoTipoProcedimento, int ordem, String serieDr) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig,
                new TipoProcedimento(codigoTipoProcedimento, codigoTipoProcedimento), ordem, serieDr);
        ReflectionTestUtils.setField(config, "id", id);
        return config;
    }

    private Dinamica criarDinamica(InstrumentoDinamicaConfig dinamicaConfig, EstadoDinamica estado) {
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadoPor);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());
        dinamica.setEstado(estado);
        return dinamica;
    }

    private void semRegrasOuDocumentosPara(List<InstrumentoProcedimentoConfig> candidatos) {
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigInAndAtivoTrueOrderByOrdem(candidatos)).thenReturn(List.of());
    }

    @Test
    void naoDeveIncluirProcedimentoJaPublicadoMasDeveIncluirOSeguinteDisponivel() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig ordem1 = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig ordem2 = criarProcedimentoConfig(11L, dinamicaConfig, "DISCUSSAO_PUBLICA", 2, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        ContextoUtilizador contexto = contextoEntidade(entidade.getNipc(), null, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(ordem1, ordem2));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, ordem1), new PermissaoAcao(TipoEntidade.CM, ordem2)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(ordem1, ordem2)))
                .thenReturn(List.of(processo(dinamica, ordem1, 1, EstadoProcesso.PUBLICADO)));
        when(autorizacaoService.procedimentoDisponivel(dinamica, ordem2, List.of())).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(ordem2));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso()).hasSize(1);
        DinamicaEmCursoResponse emCurso = resposta.dinamicasEmCurso().get(0);
        assertThat(emCurso.procedimentosDisponiveis()).extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId)
                .containsExactly(11L);
    }

    @Test
    void naoDeveIncluirDinamicaCujaElegibilidadeNaoSeVerifica() {
        InstrumentoDinamicaConfig configElegivel = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoDinamicaConfig configNaoElegivel = criarDinamicaConfig(2L, "REVISAO");
        when(instrumentoDinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(pdm))
                .thenReturn(List.of(configElegivel, configNaoElegivel));
        when(motorRegrasService.avaliarElegibilidadeDinamica(configElegivel, instrumentoId)).thenReturn(true);
        when(motorRegrasService.avaliarElegibilidadeDinamica(configNaoElegivel, instrumentoId)).thenReturn(false);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        // O ator tem permissão nas duas Dinâmicas: o que exclui a segunda é só a elegibilidade.
        InstrumentoProcedimentoConfig inicialElegivel = criarProcedimentoConfig(10L, configElegivel, "DECISAO_INICIO", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig inicialNaoElegivel = criarProcedimentoConfig(11L, configNaoElegivel, "DECISAO_INICIO", 1, "SEGUNDA_H");
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(configElegivel))
                .thenReturn(Optional.of(inicialElegivel));
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(configNaoElegivel))
                .thenReturn(Optional.of(inicialNaoElegivel));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM)).thenReturn(List.of(
                new PermissaoAcao(TipoEntidade.CM, inicialElegivel), new PermissaoAcao(TipoEntidade.CM, inicialNaoElegivel)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasDisponiveis()).extracting("instrumentoDinamicaConfigId").containsExactly(1L);
    }

    @Test
    void deveTrazerOsDocumentosExigidos() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(config));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM)).thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, config)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(config))).thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(dinamica, config, List.of())).thenReturn(true);
        DocumentoConfig documento = new DocumentoConfig(config, "PLANTA_LOCALIZACAO", "Planta de Localização",
                "Planta de Localização", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL, "[\"pdf\"]", 1);
        documento.setObrigatorio(true);
        ReflectionTestUtils.setField(documento, "id", 300L);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigInAndAtivoTrueOrderByOrdem(List.of(config)))
                .thenReturn(List.of(documento));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        ProcedimentoDisponivelResponse procedimento = resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0);
        assertThat(procedimento.documentosExigidos()).hasSize(1);
        assertThat(procedimento.documentosExigidos().get(0).id()).isEqualTo(300L);
        assertThat(procedimento.documentosExigidos().get(0).categoria()).isEqualTo("PUBLICACAO");
        assertThat(procedimento.documentosExigidos().get(0).obrigatorio()).isTrue();
        assertThat(procedimento.documentosExigidos().get(0).extensoesAceites()).containsExactly("pdf");
    }

    @Test
    void tiposAtoDisponiveisDeveTrazerApenasOsAutorizadosParaSegundaSerie() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        List<TipoAto> cincoTipos = List.of(tipoAtoSerie2(1L), tipoAtoSerie2(2L), tipoAtoSerie2(3L), tipoAtoSerie2(4L), tipoAtoSerie2(5L));
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(cincoTipos);
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(2L, null, true), new ClaimsUtilizador.AtoAutorizado(4L, null, true)));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), atos, "H"));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        ProcedimentoDisponivelResponse procedimento = resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0);
        assertThat(procedimento.tiposAtoDisponiveis()).extracting(TipoAtoDisponivelResponse::id).containsExactlyInAnyOrder(2L, 4L);
    }

    @Test
    void tiposAtoDisponiveisDeveSerFiltradoPelaSeriePropriaDoProcedimentoNuncaPelaCategoriaDoInstrumento() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig ratificacao = criarProcedimentoConfig(10L, dinamicaConfig, "RATIFICACAO", 1, "PRIMEIRA");
        InstrumentoProcedimentoConfig outro = criarProcedimentoConfig(11L, dinamicaConfig, "OUTRO", 2, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        TipoAto tipoSerie1 = tipoAtoSerie1(100L);
        TipoAto tipoSerie2 = tipoAtoSerie2(200L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of(tipoSerie1));
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoSerie2));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(100L),
                List.of(new ClaimsUtilizador.AtoAutorizado(200L, null, true)));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), atos, "H"));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(ratificacao, outro));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, ratificacao), new PermissaoAcao(TipoEntidade.CM, outro)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(ratificacao, outro)))
                .thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(dinamica, ratificacao, List.of())).thenReturn(true);
        when(autorizacaoService.procedimentoDisponivel(dinamica, outro, List.of())).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(ratificacao, outro));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        List<ProcedimentoDisponivelResponse> procedimentos = resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis();
        ProcedimentoDisponivelResponse respostaRatificacao = procedimentos.stream()
                .filter(p -> p.instrumentoProcedimentoConfigId().equals(10L)).findFirst().orElseThrow();
        ProcedimentoDisponivelResponse respostaOutro = procedimentos.stream()
                .filter(p -> p.instrumentoProcedimentoConfigId().equals(11L)).findFirst().orElseThrow();
        assertThat(respostaRatificacao.tiposAtoDisponiveis()).extracting(TipoAtoDisponivelResponse::id).containsExactly(100L);
        assertThat(respostaOutro.tiposAtoDisponiveis()).extracting(TipoAtoDisponivelResponse::id).containsExactly(200L);
    }

    @Test
    void atoPontualDeveVirDaConfiguracaoDeCadaProcedimento() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig pontual = criarProcedimentoConfig(10L, dinamicaConfig, "REVOGACAO_ATO", 1, "SEGUNDA_H");
        pontual.setAtoPontual(true);
        InstrumentoProcedimentoConfig normal = criarProcedimentoConfig(11L, dinamicaConfig, "DECISAO_INICIO", 2, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        decisaoPublicada(dinamica, dinamicaConfig);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(pontual, normal));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, pontual), new PermissaoAcao(TipoEntidade.CM, normal)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(pontual, normal)))
                .thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(dinamica, pontual, List.of())).thenReturn(true);
        when(autorizacaoService.procedimentoDisponivel(dinamica, normal, List.of())).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(pontual, normal));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId,
                        ProcedimentoDisponivelResponse::atoPontual)
                .containsExactlyInAnyOrder(tuple(10L, true), tuple(11L, false));
    }

    @Test
    void procedimentoDeOutraParteDoDrNaoDeveSerOferecidoAEntidade() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "CONSTITUICAO_CC", 1, "SEGUNDA_C");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAtoSerie2(1L)));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(1L, null, true)));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), atos, "H"));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
    }

    @Test
    void procedimentoDaParteDaAdesaoDeveSerOferecidoComOsTiposDeAto() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "CONSTITUICAO_CC", 1, "SEGUNDA_C");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAtoSerie2(1L)));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(1L, null, true)));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), atos, "C"));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).singleElement()
                .satisfies(procedimento -> assertThat(procedimento.tiposAtoDisponiveis())
                        .extracting(TipoAtoDisponivelResponse::id).containsExactly(1L));
    }

    @Test
    void entidadeSemAtosAutorizadosNoTokenDeveVerOProcedimentoDeQualquerParteDoDr() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "CONSTITUICAO_CC", 1, "SEGUNDA_C");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAtoSerie2(1L)));
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "INCM",
                null, entidade.getNipc(), "ENTIDADE", null, null, null, null, TipoEntidade.DGT, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.DGT)).thenReturn(List.of(new PermissaoAcao(TipoEntidade.DGT, config)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId).containsExactly(10L);
    }

    @Test
    void utilizadorInternoNaoDeveVerProcedimentosMesmoComLinhaDgt() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "CONSTITUICAO_CC", 1, "SEGUNDA_C");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAtoSerie2(1L)));
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "AD",
                PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.DGT)).thenReturn(List.of(new PermissaoAcao(TipoEntidade.DGT, config)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasDisponiveis()).isEmpty();
        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void entidadeSemTipoNaoDeveVerDinamicasNemProcedimentos() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "INCM",
                null, entidade.getNipc(), "ENTIDADE", null, null, null, null, null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasDisponiveis()).isEmpty();
        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void aPcmNaoDeveVerDinamicasNemProcedimentosMesmoComALinhaPcmNaConfiguracao() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "PRIMEIRA");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(instrumentoDinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(pdm)).thenReturn(List.of(dinamicaConfig));
        when(motorRegrasService.avaliarElegibilidadeDinamica(dinamicaConfig, instrumentoId)).thenReturn(true);
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(dinamicaConfig))
                .thenReturn(Optional.of(config));
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "INCM",
                null, "600000001", "PCM", null, null, null, null, TipoEntidade.PCM, 9001L));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.PCM))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.PCM, config)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasDisponiveis()).isEmpty();
        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void procedimentoSemPermissaoAcaoNaoDeveAparecer() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(config));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM)).thenReturn(List.of());

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
    }

    @Test
    void deveLancarAExcecaoDoInstrumentoServiceQuandoInstrumentoNaoEVisivel() {
        UUID outroId = UUID.randomUUID();
        when(instrumentoService.obter(outroId))
                .thenThrow(new com.conkord.saigt.exception.RecursoNaoEncontradoException("Recurso não encontrado."));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.disponivel(outroId))
                .isInstanceOf(com.conkord.saigt.exception.RecursoNaoEncontradoException.class);
    }

    @Test
    void naoDeveConsultarProcessoFormularioOuDocumentoUmaVezPorProcedimento() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config1 = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig config2 = criarProcedimentoConfig(11L, dinamicaConfig, "DISCUSSAO_PUBLICA", 2, "SEGUNDA_H");
        InstrumentoProcedimentoConfig config3 = criarProcedimentoConfig(12L, dinamicaConfig, "DECISAO_FINAL", 3, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        List<InstrumentoProcedimentoConfig> todos = List.of(config1, config2, config3);
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(todos);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM)).thenReturn(todos.stream()
                .map(c -> new PermissaoAcao(TipoEntidade.CM, c)).toList());
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), todos))
                .thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(eq(dinamica), any(), any())).thenReturn(true);
        semRegrasOuDocumentosPara(todos);

        service.disponivel(instrumentoId);

        verify(processoRepository, times(1))
                .findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), todos);
        verify(autorizacaoService, times(1)).processosEmCurso(dinamica);
        verify(documentoConfigRepository, times(1)).findByInstrumentoProcedimentoConfigInAndAtivoTrueOrderByOrdem(todos);
    }

    @Test
    void devolverInstrumentoSrupComProcedimentoUnicoDisponivel() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "SUBMISSAO");
        InstrumentoProcedimentoConfig unico = criarProcedimentoConfig(10L, dinamicaConfig, "SUBMISSAO_SRUP", 1, null);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, unico, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso()).hasSize(1);
        ProcedimentoDisponivelResponse procedimento = resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0);
        assertThat(procedimento.instrumentoProcedimentoConfigId()).isEqualTo(10L);
        assertThat(procedimento.tiposAtoDisponiveis()).isEmpty();
    }

    @Test
    void deveDevolverSerieDrDoProcedimento() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0).serieDr()).isEqualTo("SEGUNDA_H");
    }

    @Test
    void deveDevolverSerieDrNulaQuandoProcedimentoNaoTemSerieConfigurada() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "SUBMISSAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "SUBMISSAO_SRUP", 1, null);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0).serieDr()).isNull();
    }

    @Test
    void semSerieNoProcedimentoTrazCadaTipoComAsSeriesEmQueExisteParaASessao() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "DELIMITACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "PUBLICACAO_SRUP", 1, null);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        TipoAto dasDuas = tipoAtoSerie1(1L);
        dasDuas.setIncmActTypeIdSerie2(1L);
        TipoAto soDaPrimeira = tipoAtoSerie1(2L);
        TipoAto soDaSegunda = tipoAtoSerie2(3L);
        TipoAto naoAutorizado = tipoAtoSerie2(4L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of(dasDuas, soDaPrimeira));
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(dasDuas, soDaSegunda, naoAutorizado));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(1L, 2L),
                List.of(new ClaimsUtilizador.AtoAutorizado(1L, null, true), new ClaimsUtilizador.AtoAutorizado(3L, null, true)));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), atos, "C"));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        ProcedimentoDisponivelResponse procedimento = service.disponivel(instrumentoId).dinamicasEmCurso().get(0)
                .procedimentosDisponiveis().get(0);

        assertThat(procedimento.serieDr()).isNull();
        assertThat(procedimento.tiposAtoDisponiveis()).containsExactly(
                new TipoAtoDisponivelResponse(1L, "COD1-1", "Ato 1", List.of("PRIMEIRA", "SEGUNDA_C")),
                new TipoAtoDisponivelResponse(2L, "COD1-2", "Ato 2", List.of("PRIMEIRA")),
                new TipoAtoDisponivelResponse(3L, "COD2-3", "Ato 3", List.of("SEGUNDA_C")));
    }

    @Test
    void naoDeveIncluirProcedimentoComTentativaSubmetida() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig submetido = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig livre = criarProcedimentoConfig(11L, dinamicaConfig, "DISCUSSAO_PUBLICA", 2, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(submetido, livre));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, submetido), new PermissaoAcao(TipoEntidade.CM, livre)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(submetido, livre)))
                .thenReturn(List.of(processo(dinamica, submetido, 1, EstadoProcesso.ENVIADO_DGT)));
        when(autorizacaoService.procedimentoDisponivel(dinamica, livre, List.of())).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(livre));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId).containsExactly(11L);
    }

    @Test
    void deveIncluirProcedimentoComTentativaEmSubmissaoMarcadaComoRetomavel() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        Processo rascunho = processo(dinamica, config, 2, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(config)))
                .thenReturn(List.of(rascunho));
        when(autorizacaoService.procedimentoDisponivel(dinamica, config, List.of())).thenReturn(true);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        ProcedimentoDisponivelResponse procedimento = resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0);
        assertThat(procedimento.instrumentoProcedimentoConfigId()).isEqualTo(10L);
        assertThat(procedimento.tentativaEmCurso()).isNotNull();
        assertThat(procedimento.tentativaEmCurso().processoId()).isEqualTo(rascunho.getId());
        assertThat(procedimento.tentativaEmCurso().numeroTentativa()).isEqualTo(2);
        assertThat(procedimento.tentativaEmCurso().estado()).isEqualTo("EM_SUBMISSAO");
    }

    @Test
    void naoDeveExporATentativaEmSubmissaoDeProcessoQueASessaoNaoVe() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        ContextoUtilizador contexto = contextoEntidade("501999999", null, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        Processo deOutraEntidade = processo(dinamica, config, 1, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(config)))
                .thenReturn(List.of(deOutraEntidade));
        when(autorizacaoService.procedimentoDisponivel(dinamica, config, List.of())).thenReturn(true);
        when(autorizacaoService.processoVisivel(contexto, deOutraEntidade)).thenReturn(false);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .singleElement().satisfies(p -> assertThat(p.tentativaEmCurso()).isNull());
        verify(autorizacaoService).processoVisivel(contexto, deOutraEntidade);
    }

    @Test
    void deveDevolverTentativaEmCursoNulaQuandoNaoHaTentativaNenhuma() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0).tentativaEmCurso()).isNull();
    }

    @Test
    void deveDevolverTentativaEmCursoNulaQuandoAUltimaTentativaFoiAnulada() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(config)))
                .thenReturn(List.of(processo(dinamica, config, 1, EstadoProcesso.ANULADO)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis().get(0).tentativaEmCurso()).isNull();
    }

    @Test
    void deveIncluirProcedimentoCujaUltimaTentativaFoiAnulada() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(config)))
                .thenReturn(List.of(processo(dinamica, config, 1, EstadoProcesso.ANULADO)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId).containsExactly(10L);
    }

    @Test
    void deveOlharSoParaAUltimaTentativaAoDecidirSeOProcedimentoAparece() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, config, dinamica);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(config)))
                .thenReturn(List.of(processo(dinamica, config, 1, EstadoProcesso.ANULADO), processo(dinamica, config, 2, EstadoProcesso.ENVIADO_DGT)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
    }

    @Test
    void naoDeveIncluirDinamicaSemPermissaoAcaoParaOAtor() {
        InstrumentoDinamicaConfig comPermissao = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoDinamicaConfig semPermissao = criarDinamicaConfig(2L, "MEDIDAS_PREVENTIVAS");
        InstrumentoProcedimentoConfig config = criarProcedimentoConfig(10L, comPermissao, "DECISAO_INICIO", 1, "SEGUNDA_H");
        when(instrumentoDinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(pdm))
                .thenReturn(List.of(comPermissao, semPermissao));
        when(motorRegrasService.avaliarElegibilidadeDinamica(comPermissao, instrumentoId)).thenReturn(true);
        when(motorRegrasService.avaliarElegibilidadeDinamica(semPermissao, instrumentoId)).thenReturn(true);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM)).thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, config)));
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(comPermissao))
                .thenReturn(Optional.of(config));
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(semPermissao))
                .thenReturn(Optional.of(criarProcedimentoConfig(12L, semPermissao, "DECISAO_INICIO", 1, "SEGUNDA_H")));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasDisponiveis()).extracting("instrumentoDinamicaConfigId").containsExactly(1L);
    }

    @Test
    void ccdrComLinhaSoNaConstituicaoDaCcNaoDeveVerDinamicasParaIniciarMasSoACcNaElaboracaoAberta() {
        InstrumentoDinamicaConfig elaboracao = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig decisaoInicio = criarProcedimentoConfig(10L, elaboracao, "DECISAO_INICIO", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig constituicaoCc = criarProcedimentoConfig(11L, elaboracao, "CONSTITUICAO_CC", 2, "SEGUNDA_C");
        Dinamica dinamica = criarDinamica(elaboracao, EstadoDinamica.EM_CURSO);
        when(instrumentoDinamicaConfigRepository.findByTipoInstrumentoAndAtivoTrue(pdm)).thenReturn(List.of(elaboracao));
        when(motorRegrasService.avaliarElegibilidadeDinamica(elaboracao, instrumentoId)).thenReturn(true);
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(elaboracao))
                .thenReturn(Optional.of(decisaoInicio));
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "INCM", null,
                entidade.getNipc(), "ENTIDADE", new ClaimsUtilizador.AtosAutorizados(List.of(), List.of(
                        new ClaimsUtilizador.AtoAutorizado(1L, "Despacho", false))), "C", null, null, TipoEntidade.CCDR_DR, 8001L));
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAtoSerie2(1L)));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA)))
                .thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(elaboracao))
                .thenReturn(List.of(decisaoInicio, constituicaoCc));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CCDR_DR))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.CCDR_DR, constituicaoCc)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(decisaoInicio, constituicaoCc)))
                .thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(dinamica, constituicaoCc, List.of())).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(constituicaoCc));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasDisponiveis()).isEmpty();
        assertThat(resposta.dinamicasEmCurso()).hasSize(1);
        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .extracting(ProcedimentoDisponivelResponse::tipoProcedimento).containsExactly("CONSTITUICAO_CC");
    }

    @Test
    void deveDevolverUltimaDinamicaPublicadaNulaQuandoNenhumaDinamicaConcluida() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.listarAbrangendoComEstadoPelaConclusaoMaisRecente(instrumentoId, EstadoDinamica.TERMINADA,
                Limit.of(1))).thenReturn(List.of());

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.ultimaDinamicaPublicada()).isNull();
    }

    @Test
    void deveDevolverUltimaDinamicaPublicadaComCamposMapeados() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ELABORACAO");
        Dinamica concluida = criarDinamica(dinamicaConfig, EstadoDinamica.TERMINADA);
        Instant dataConclusao = Instant.parse("2026-08-15T10:00:00Z");
        concluida.setDataConclusao(dataConclusao);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.listarAbrangendoComEstadoPelaConclusaoMaisRecente(instrumentoId, EstadoDinamica.TERMINADA,
                Limit.of(1))).thenReturn(List.of(concluida));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        UltimaDinamicaPublicadaResponse ultima = resposta.ultimaDinamicaPublicada();
        assertThat(ultima.dinamicaId()).isEqualTo(concluida.getId());
        assertThat(ultima.tipoDinamica()).isEqualTo("ELABORACAO");
        assertThat(ultima.dataConclusao()).isEqualTo(dataConclusao);
    }

    @Test
    void deveIncluirProcedimentoRepetivelCujaUltimaTentativaEstaPublicada() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig discussao = criarProcedimentoConfig(10L, dinamicaConfig, "ABERTURA_DISCUSSAO_PUBLICA", 1, "SEGUNDA_H");
        discussao.setRepetivel(true);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, discussao, dinamica);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(discussao)))
                .thenReturn(List.of(processo(dinamica, discussao, 1, EstadoProcesso.PUBLICADO)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        List<ProcedimentoDisponivelResponse> procedimentos = resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis();
        assertThat(procedimentos).extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId).containsExactly(10L);
        assertThat(procedimentos.get(0).tentativaEmCurso()).isNull();
    }

    @Test
    void naoDeveIncluirProcedimentoNaoRepetivelCujaUltimaTentativaEstaPublicada() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig decisao = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, decisao, dinamica);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(decisao)))
                .thenReturn(List.of(processo(dinamica, decisao, 1, EstadoProcesso.PUBLICADO)));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
    }

    @Test
    void comProcessoEmSubmissaoDeveTrazerSoOMesmoProcedimentoEOsAtosPontuais() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig discussao = criarProcedimentoConfig(10L, dinamicaConfig, "ABERTURA_DISCUSSAO_PUBLICA", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig publicacao = criarProcedimentoConfig(11L, dinamicaConfig, "PUBLICACAO_ALTERACAO", 2, "SEGUNDA_H");
        InstrumentoProcedimentoConfig pontual = criarProcedimentoConfig(12L, dinamicaConfig, "REVOGACAO_ATO", 3, "SEGUNDA_H");
        pontual.setAtoPontual(true);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        decisaoPublicada(dinamica, dinamicaConfig);
        Processo rascunho = processo(dinamica, discussao, 2, EstadoProcesso.EM_SUBMISSAO);
        List<InstrumentoProcedimentoConfig> todos = List.of(discussao, publicacao, pontual);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(todos);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(todos.stream().map(c -> new PermissaoAcao(TipoEntidade.CM, c)).toList());
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), todos)).thenReturn(List.of(rascunho));
        when(autorizacaoService.processosEmCurso(dinamica)).thenReturn(List.of(rascunho));
        // A mesma regra decide cada candidato: o Procedimento retomável não a sofre, o seguinte sofre-a, o ato pontual passa.
        when(autorizacaoService.procedimentoDisponivel(dinamica, discussao, List.of())).thenReturn(true);
        when(autorizacaoService.procedimentoDisponivel(dinamica, publicacao, List.of(rascunho))).thenReturn(false);
        when(autorizacaoService.procedimentoDisponivel(dinamica, pontual, List.of(rascunho))).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(discussao, pontual));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        List<ProcedimentoDisponivelResponse> procedimentos = resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis();
        assertThat(procedimentos).extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId)
                .containsExactlyInAnyOrder(10L, 12L);
        assertThat(procedimentos).filteredOn(p -> p.instrumentoProcedimentoConfigId().equals(10L))
                .singleElement().satisfies(p -> assertThat(p.tentativaEmCurso()).isNotNull());
    }

    @Test
    void comProcessoEnviadoADgtDeveTrazerSoOsAtosPontuais() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig discussao = criarProcedimentoConfig(10L, dinamicaConfig, "ABERTURA_DISCUSSAO_PUBLICA", 1, "SEGUNDA_H");
        discussao.setRepetivel(true);
        InstrumentoProcedimentoConfig publicacao = criarProcedimentoConfig(11L, dinamicaConfig, "PUBLICACAO_ALTERACAO", 2, "SEGUNDA_H");
        InstrumentoProcedimentoConfig pontual = criarProcedimentoConfig(12L, dinamicaConfig, "REVOGACAO_ATO", 3, "SEGUNDA_H");
        pontual.setAtoPontual(true);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        decisaoPublicada(dinamica, dinamicaConfig);
        Processo enviado = processo(dinamica, discussao, 1, EstadoProcesso.ENVIADO_DGT);
        List<InstrumentoProcedimentoConfig> todos = List.of(discussao, publicacao, pontual);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(todos);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(todos.stream().map(c -> new PermissaoAcao(TipoEntidade.CM, c)).toList());
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), todos)).thenReturn(List.of(enviado));
        when(autorizacaoService.processosEmCurso(dinamica)).thenReturn(List.of(enviado));
        when(autorizacaoService.procedimentoDisponivel(dinamica, publicacao, List.of(enviado))).thenReturn(false);
        when(autorizacaoService.procedimentoDisponivel(dinamica, pontual, List.of(enviado))).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(pontual));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId).containsExactly(12L);
    }

    @Test
    void oAtoPontualSoApareceComUmAtoPublicadoDaDecisaoDeInicioOuDaDiscussaoPublica() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig decisao = criarProcedimentoConfig(10L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig pontual = criarProcedimentoConfig(12L, dinamicaConfig, "RETIFICACAO_ATO_ANEXOS", 102, "SEGUNDA_H");
        pontual.setAtoPontual(true);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        List<InstrumentoProcedimentoConfig> todos = List.of(decisao, pontual);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig)).thenReturn(todos);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(todos.stream().map(c -> new PermissaoAcao(TipoEntidade.CM, c)).toList());
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), todos)).thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(dinamica, decisao, List.of())).thenReturn(true);
        when(autorizacaoService.procedimentoDisponivel(dinamica, pontual, List.of())).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(decisao));

        List<ProcedimentoDisponivelResponse> semAtoPublicado = service.disponivel(instrumentoId).dinamicasEmCurso().get(0)
                .procedimentosDisponiveis();

        assertThat(semAtoPublicado).extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId).containsExactly(10L);
        assertThat(semAtoPublicado.get(0).atosReferiveis()).isEmpty();
        verify(processoRepository).findByDinamicaAndEstadoAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoInOrderByDataPublicacaoDrAsc(
                dinamica, EstadoProcesso.PUBLICADO, AtosReferiveis.CODIGOS_PROCEDIMENTO_REFERIVEIS);

        Processo publicada = decisaoPublicada(dinamica, dinamicaConfig);
        semRegrasOuDocumentosPara(todos);

        List<ProcedimentoDisponivelResponse> comAtoPublicado = service.disponivel(instrumentoId).dinamicasEmCurso().get(0)
                .procedimentosDisponiveis();

        assertThat(comAtoPublicado).extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId,
                ProcedimentoDisponivelResponse::atoPontual).containsExactly(tuple(10L, false), tuple(12L, true));
        assertThat(comAtoPublicado.get(0).atosReferiveis()).isEmpty();
        assertThat(comAtoPublicado.get(1).atosReferiveis()).containsExactly(new AtoReferivelResponse(publicada.getId(), "00007",
                "DECISAO_INICIO", "123/2026", LocalDate.of(2026, 10, 1)));
    }

    @Test
    void oAtoPontualRepeteSeDepoisDoAnteriorPublicadoESaiComOAnteriorEnviadoADgt() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(1L, "ALTERACAO");
        InstrumentoProcedimentoConfig pontual = criarProcedimentoConfig(12L, dinamicaConfig, "RETIFICACAO_ATO_ANEXOS", 102, "SEGUNDA_H");
        pontual.setAtoPontual(true);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.EM_CURSO);
        decisaoPublicada(dinamica, dinamicaConfig);
        Processo anterior = processo(dinamica, pontual, 1, EstadoProcesso.PUBLICADO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, null));
        prepararProcedimentoDisponivel(dinamicaConfig, pontual, dinamica);
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(pontual)))
                .thenReturn(List.of(anterior));

        assertThat(service.disponivel(instrumentoId).dinamicasEmCurso().get(0).procedimentosDisponiveis())
                .extracting(ProcedimentoDisponivelResponse::instrumentoProcedimentoConfigId).containsExactly(12L);

        anterior.setEstado(EstadoProcesso.ENVIADO_DGT);

        assertThat(service.disponivel(instrumentoId).dinamicasEmCurso().get(0).procedimentosDisponiveis()).isEmpty();
    }

    /** Uma Decisão de início `PUBLICADO` da Dinâmica, devolvida como ato referível. */
    private Processo decisaoPublicada(Dinamica dinamica, InstrumentoDinamicaConfig dinamicaConfig) {
        InstrumentoProcedimentoConfig decisao = criarProcedimentoConfig(90L, dinamicaConfig, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Processo publicada = processo(dinamica, decisao, 1, EstadoProcesso.PUBLICADO);
        ReflectionTestUtils.setField(publicada, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(publicada, "numeroProcesso", "00007");
        publicada.setNumeroDr("123/2026");
        publicada.setDataPublicacaoDr(LocalDate.of(2026, 10, 1));
        when(processoRepository.findByDinamicaAndEstadoAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoInOrderByDataPublicacaoDrAsc(
                dinamica, EstadoProcesso.PUBLICADO, AtosReferiveis.CODIGOS_PROCEDIMENTO_REFERIVEIS)).thenReturn(List.of(publicada));
        return publicada;
    }

    private Processo processo(Dinamica dinamica, InstrumentoProcedimentoConfig config, int numeroTentativa, EstadoProcesso estado) {
        Processo processo = new Processo(dinamica, config, criadoPor, entidade);
        processo.setOcorrencia(numeroTentativa);
        processo.setEstado(estado);
        return processo;
    }

    private void prepararProcedimentoDisponivel(InstrumentoDinamicaConfig dinamicaConfig, InstrumentoProcedimentoConfig config,
                                                 Dinamica dinamica) {
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).thenReturn(List.of(dinamica));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(config));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM)).thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, config)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(), List.of(config))).thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(dinamica, config, List.of())).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(config));
    }

    private TipoAto tipoAtoSerie1(Long id) {
        TipoAto tipoAto = new TipoAto("COD1-" + id, "Ato " + id, 1);
        tipoAto.setIncmActTypeIdSerie1(id);
        ReflectionTestUtils.setField(tipoAto, "id", id);
        return tipoAto;
    }

    private TipoAto tipoAtoSerie2(Long id) {
        TipoAto tipoAto = new TipoAto("COD2-" + id, "Ato " + id, 1);
        tipoAto.setIncmActTypeIdSerie2(id);
        ReflectionTestUtils.setField(tipoAto, "id", id);
        return tipoAto;
    }

    /** Medidas Preventivas terminadas: a Publicação (final, publicada) e a Prorrogação, opcional e depois dela. */
    private record MedidasTerminadas(Dinamica dinamica, InstrumentoProcedimentoConfig publicacao, InstrumentoProcedimentoConfig prorrogacao) {
    }

    private MedidasTerminadas medidasTerminadas(boolean prorrogacaoPorFazer, boolean camaraComLinhaNaProrrogacao) {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig(5L, "MEDIDAS_PREVENTIVAS");
        InstrumentoProcedimentoConfig publicacao = criarProcedimentoConfig(50L, dinamicaConfig, "PUBLICACAO_MP", 1, "SEGUNDA_H");
        InstrumentoProcedimentoConfig prorrogacao = criarProcedimentoConfig(51L, dinamicaConfig, "PRORROGACAO_MP", 2, "SEGUNDA_H");
        prorrogacao.setObrigatorio(false);
        Dinamica dinamica = criarDinamica(dinamicaConfig, EstadoDinamica.TERMINADA);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoEntidade(entidade.getNipc(), null, "H"));
        when(dinamicaRepository.listarTerminadasComProcedimentosPosterioresAoFinal(instrumentoId)).thenReturn(List.of(dinamica));
        when(autorizacaoService.procedimentosPosterioresAoFinalPorDinamica(List.of(dinamica)))
                .thenReturn(prorrogacaoPorFazer ? Map.of(dinamica.getId(), List.of(prorrogacao)) : Map.of());
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig))
                .thenReturn(List.of(publicacao, prorrogacao));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM)).thenReturn(camaraComLinhaNaProrrogacao
                ? List.of(new PermissaoAcao(TipoEntidade.CM, publicacao), new PermissaoAcao(TipoEntidade.CM, prorrogacao))
                : List.of(new PermissaoAcao(TipoEntidade.CM, publicacao)));
        when(processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(dinamica, dinamica.getTentativa(),
                List.of(prorrogacao))).thenReturn(List.of());
        when(autorizacaoService.procedimentoDisponivel(eq(dinamica), any(), eq(List.of()))).thenReturn(true);
        semRegrasOuDocumentosPara(List.of(prorrogacao));
        return new MedidasTerminadas(dinamica, publicacao, prorrogacao);
    }

    @Test
    void aDinamicaTerminadaComAProrrogacaoPorFazerEntraNasEmCursoComEstadoTerminadaESoAProrrogacao() {
        MedidasTerminadas mp = medidasTerminadas(true, true);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso()).singleElement().satisfies(dinamica -> {
            assertThat(dinamica.dinamicaId()).isEqualTo(mp.dinamica().getId());
            assertThat(dinamica.estado()).isEqualTo("TERMINADA");
            assertThat(dinamica.tipoDinamica()).isEqualTo("MEDIDAS_PREVENTIVAS");
            assertThat(dinamica.procedimentosDisponiveis()).extracting(ProcedimentoDisponivelResponse::tipoProcedimento)
                    .containsExactly("PRORROGACAO_MP");
        });
        verify(autorizacaoService, times(0)).procedimentoDisponivel(eq(mp.dinamica()), eq(mp.publicacao()), any());
    }

    @Test
    void aDinamicaTerminadaSemProcedimentosPorFazerNaoEntraNasEmCurso() {
        MedidasTerminadas mp = medidasTerminadas(false, true);

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso()).isEmpty();
        verify(instrumentoProcedimentoConfigRepository, times(0))
                .findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(mp.dinamica().getInstrumentoDinamicaConfig());
    }

    @Test
    void semLinhaNaProrrogacaoADinamicaTerminadaNaoEntraNasEmCurso() {
        medidasTerminadas(true, false);

        assertThat(service.disponivel(instrumentoId).dinamicasEmCurso()).isEmpty();
    }

    @Test
    void aDinamicaTerminadaVemDepoisDasEmCursoEAUltimaPublicadaNaoMuda() {
        MedidasTerminadas mp = medidasTerminadas(true, true);
        InstrumentoDinamicaConfig elaboracao = criarDinamicaConfig(1L, "ELABORACAO");
        InstrumentoProcedimentoConfig decisao = criarProcedimentoConfig(10L, elaboracao, "DECISAO_INICIO", 1, "SEGUNDA_H");
        Dinamica emCurso = criarDinamica(elaboracao, EstadoDinamica.EM_CURSO);
        when(dinamicaRepository.findByInstrumento_IdAndEstadoIn(instrumentoId, List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA)))
                .thenReturn(List.of(emCurso));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(elaboracao))
                .thenReturn(List.of(decisao));
        mp.dinamica().setDataConclusao(Instant.parse("2026-11-10T10:00:00Z"));
        when(dinamicaRepository.listarAbrangendoComEstadoPelaConclusaoMaisRecente(instrumentoId, EstadoDinamica.TERMINADA, Limit.of(1)))
                .thenReturn(List.of(mp.dinamica()));

        InstrumentoDisponibilidadeResponse resposta = service.disponivel(instrumentoId);

        assertThat(resposta.dinamicasEmCurso()).extracting(DinamicaEmCursoResponse::estado).containsExactly("EM_CURSO", "TERMINADA");
        assertThat(resposta.ultimaDinamicaPublicada().dinamicaId()).isEqualTo(mp.dinamica().getId());
        verify(autorizacaoService, times(0)).procedimentosPosterioresAoFinal(emCurso);
    }
}
