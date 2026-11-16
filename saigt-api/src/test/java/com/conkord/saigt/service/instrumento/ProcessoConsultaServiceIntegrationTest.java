package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.RegrasFormularioRen;
import com.conkord.saigt.service.instrumento.formulario.RegrasRedesDefesaSgifr;
import com.conkord.saigt.service.instrumento.formulario.RegrasTerritorioSgifr;
import com.conkord.saigt.dto.instrumento.ProcessoDetalheResponse;
import com.conkord.saigt.dto.instrumento.ProcessoFiltro;
import com.conkord.saigt.dto.instrumento.ProcessoListagemResponse;
import com.conkord.saigt.dto.instrumento.UltimaRejeicaoDgtResponse;
import com.conkord.saigt.dto.instrumento.UltimaRejeicaoIncmResponse;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItemRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.conkord.saigt.dto.instrumento.EstatisticasProcessoResponse;
import com.conkord.saigt.dto.instrumento.ItemRecusaResponse;
import com.conkord.saigt.dto.instrumento.RecusaResponse;
import com.conkord.saigt.dto.instrumento.TipoItemValidacao;
import com.conkord.saigt.dto.instrumento.ValorListaResponse;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItem;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Só um {@code @DataJpaTest} com estatísticas do Hibernate chega à {@code Specification} real e ao
 * fetch join, e prova que `autorNome` não custa uma query por Processo.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProcessoConsultaServiceIntegrationTest {

    private static final AtomicInteger CONTADOR_NIF = new AtomicInteger(0);

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private ProcessoRejeicaoRepository processoRejeicaoRepository;

    @Autowired
    private ListaValoresRepository listaValoresRepository;

    @Autowired
    private ProcessoRejeicaoItemRepository processoRejeicaoItemRepository;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

    @Autowired
    private InstrumentoConcelhoRepository instrumentoConcelhoRepository;

    // A consulta nativa sobre jsonb só existe em PostgreSQL; o repositório real tem o seu próprio teste.
    private final SnitReferenciaRepository snitReferenciaRepository = mock(SnitReferenciaRepository.class);

    private InstrumentoProcedimentoConfig configPartilhada() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        return procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
    }

    private Processo criarProcesso(InstrumentoProcedimentoConfig config, String nomeAutor) {
        Entidade entidade = entidadeRepository.saveAndFlush(
                EntidadeTeste.nova("5012345" + CONTADOR_NIF.incrementAndGet(), "Câmara Municipal"));
        Utilizador autor = new Utilizador(nomeAutor, nomeAutor.toLowerCase().replace(" ", ".") + "@cm.pt");
        autor.setPerfilInterno(PerfilInterno.MASTER);
        autor = utilizadorRepository.saveAndFlush(autor);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(new Instrumento(
                config.getInstrumentoDinamicaConfig().getTipoInstrumento(), "1", "2026/PDM/" + CONTADOR_NIF.get(), autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, config.getInstrumentoDinamicaConfig(), "Alteração do PDM", autor));
        return processoRepository.saveAndFlush(new Processo(dinamica, config, autor, entidade));
    }

    private ProcessoConsultaService serviceComoMaster() {
        ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
        when(contextoUtilizadorResolver.resolver())
                .thenReturn(new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, null, null, null, null, null, null, null));
        return new ProcessoConsultaService(processoRepository, auditoriaRepository, mock(AutorizacaoService.class),
                contextoUtilizadorResolver, new ObjectMapper(), listaValoresRepository, utilizadorRepository,
                mock(ProcessoService.class), mock(ProcessoValidacaoService.class), new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper()),
                mock(CompletudeDocumentalService.class), new CatalogoFormularios(),
                mock(TerritorioInstrumentoService.class), processoRejeicaoRepository, processoRejeicaoItemRepository,
                idusProcessoRepository, snitReferenciaRepository, mock(AssociacaoInstrumentosService.class),
                mock(DinamicaAssociadaService.class), mock(DestinoProcessoService.class), mock(RegrasRedesDefesaSgifr.class),
                new RegrasTerritorioSgifr(mock(ListaValoresRepository.class), new ObjectMapper()), mock(RegrasFormularioRen.class));
    }

    /** Replica a auditoria que `ProcessoService#rejeitar` grava. */
    private void registarRejeicao(Processo processo, ListaValores motivo) {
        auditoriaRepository.save(Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia(processo.getId().toString())
                .acao("REJEITAR")
                .estadoAntes("EM_VALIDACAO_DGT")
                .estadoDepois("REJEITADO_DGT")
                .utilizadorId(processo.getCriadoPor().getId())
                .dadosAlteracao("{\"motivoId\":" + motivo.getId() + ",\"motivoCodigo\":\"" + motivo.getCodigo() + "\"}")
                .build());
        entityManager.flush();
    }

    @Test
    void detalheDevolveOIdusGravadoEIdusNuloQuandoOProcessoNaoTem() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo comIdus = criarProcesso(config, "Ana Ferreira");
        Processo semIdus = criarProcesso(config, "Rui Costa");
        idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                .processo(comIdus).idus("01.1111.01.9" + comIdus.getNumeroProcesso() + ".01.90001.01.029.00")
                .sistemaOrigem("01").idIgt("9" + comIdus.getNumeroProcesso()).ordemDinamica("90001")
                .tentativa((short) 1).procedimento("029").atualizadoEm(Instant.now()).build());
        entityManager.clear();

        ProcessoConsultaService service = serviceComoMaster();

        assertThat(service.detalhe(comIdus.getId()).idus())
                .isEqualTo("01.1111.01.9" + comIdus.getNumeroProcesso() + ".01.90001.01.029.00");
        assertThat(service.detalhe(semIdus.getId()).idus()).isNull();
    }

    @Test
    void semIdusASubmissaoDoDetalheContaSoAsTransicoesParaEnviadoDgt() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        for (String[] transicao : new String[][] {{"SUBMETER", "EM_SUBMISSAO", "ENVIADO_DGT"},
                {"CAPTURAR", "ENVIADO_DGT", "EM_VALIDACAO_DGT"}}) {
            auditoriaRepository.save(Auditoria.builder()
                    .tipoOcorrencia("PROCESSO")
                    .idOcorrencia(processo.getId().toString())
                    .acao(transicao[0])
                    .estadoAntes(transicao[1])
                    .estadoDepois(transicao[2])
                    .utilizadorId(processo.getCriadoPor().getId())
                    .build());
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(serviceComoMaster().detalhe(processo.getId()).submissao()).isEqualTo(1);
    }

    @Test
    void ultimaRejeicaoDgtENulaSemRejeicao() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        // Outras ações da DGT não contam como rejeição.
        auditoriaRepository.save(Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia(processo.getId().toString())
                .acao("CAPTURAR")
                .estadoAntes("ENVIADO_DGT")
                .estadoDepois("EM_VALIDACAO_DGT")
                .utilizadorId(processo.getCriadoPor().getId())
                .build());
        entityManager.flush();
        entityManager.clear();

        ProcessoDetalheResponse detalhe = serviceComoMaster().detalhe(processo.getId());

        assertThat(detalhe.ultimaRejeicaoDgt()).isNull();
    }

    @Test
    void detalheDeveTrazerOCodigoEONomeDaDinamicaEDoProcedimento() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        entityManager.clear();

        ProcessoDetalheResponse detalhe = serviceComoMaster().detalhe(processo.getId());

        assertThat(detalhe.tipoDinamica()).isEqualTo("ELABORACAO");
        assertThat(detalhe.nomeDinamica()).isEqualTo("Elaboração");
        assertThat(detalhe.tipoProcedimento()).isEqualTo("DECISAO_INICIO");
        assertThat(detalhe.nomeProcedimento()).isEqualTo("Decisão de Início");
    }

    @Test
    void detalheDeveTrazerADescricaoIncmComoNomeDaEntidade() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        entityManager.clear();

        ProcessoDetalheResponse detalhe = serviceComoMaster().detalhe(processo.getId());

        assertThat(detalhe.entidadeNome()).isEqualTo("Câmara Municipal");
    }

    @Test
    void ultimaRejeicaoDgtComUmaRejeicao() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        ListaValores motivo = listaValoresRepository.saveAndFlush(
                new ListaValores("MOTIVO_REJEICAO_DGT", "DOC_INCOMPLETA_T", "Documentação incompleta", 1));
        registarRejeicao(processo, motivo);
        entityManager.clear();

        UltimaRejeicaoDgtResponse rejeicao = serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoDgt();

        assertThat(rejeicao).isNotNull();
        assertThat(rejeicao.motivoCodigo()).isEqualTo("DOC_INCOMPLETA_T");
        assertThat(rejeicao.motivoDescricao()).isEqualTo("Documentação incompleta");
        assertThat(rejeicao.data()).isNotNull();
    }

    @Test
    void ultimaRejeicaoDgtComDuasRejeicoesDevolveAMaisRecente() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        ListaValores primeiro = listaValoresRepository.saveAndFlush(
                new ListaValores("MOTIVO_REJEICAO_DGT", "DOC_INCOMPLETA_T", "Documentação incompleta", 1));
        ListaValores segundo = listaValoresRepository.saveAndFlush(
                new ListaValores("MOTIVO_REJEICAO_DGT", "PRAZO_EXPIRADO_T", "Incumprimento de prazo legal", 2));
        registarRejeicao(processo, primeiro);
        registarRejeicao(processo, segundo);
        entityManager.clear();

        UltimaRejeicaoDgtResponse rejeicao = serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoDgt();

        assertThat(rejeicao.motivoCodigo()).isEqualTo("PRAZO_EXPIRADO_T");
        assertThat(rejeicao.motivoDescricao()).isEqualTo("Incumprimento de prazo legal");
    }

    private void registarRecusaIncm(Processo processo, String acao, String dadosAlteracao) {
        auditoriaRepository.save(Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia(processo.getId().toString())
                .acao(acao)
                .estadoAntes("ENVIADO_INCM")
                .estadoDepois("ANULADO_INCM")
                .dadosAlteracao(dadosAlteracao)
                .build());
        entityManager.flush();
    }

    @Test
    void ultimaRejeicaoIncmENulaSemRecusaDaIncm() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        registarRejeicao(processo, listaValoresRepository.saveAndFlush(
                new ListaValores("MOTIVO_REJEICAO_DGT", "DOC_INCOMPLETA_T", "Documentação incompleta", 1)));
        entityManager.clear();

        assertThat(serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoIncm()).isNull();
    }

    @Test
    void ultimaRejeicaoIncmDoEnvioTrazMensagensEData() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        registarRecusaIncm(processo, "SUBMISSAO_INCM_REJEITADA", "{\"mensagens\":[\"ImageURLList cannot be empty.\"]}");
        entityManager.clear();

        UltimaRejeicaoIncmResponse recusa = serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoIncm();

        assertThat(recusa.mensagens()).containsExactly("ImageURLList cannot be empty.");
        assertThat(recusa.data()).isNotNull();
    }

    @Test
    void ultimaRejeicaoIncmDaConsultaTrazAsMensagensDeValidacao() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        registarRecusaIncm(processo, "REJEICAO_INCM", "{\"mensagens\":[\"Primeira\",\"Segunda\"]}");
        entityManager.clear();

        assertThat(serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoIncm().mensagens())
                .containsExactly("Primeira", "Segunda");
    }

    @Test
    void ultimaRejeicaoIncmComVariasRecusasDevolveAMaisRecente() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        registarRecusaIncm(processo, "SUBMISSAO_INCM_REJEITADA", "{\"mensagens\":[\"antiga\"]}");
        registarRecusaIncm(processo, "REJEICAO_INCM", "{\"mensagens\":[\"recente\"]}");
        entityManager.clear();

        assertThat(serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoIncm().mensagens())
                .containsExactly("recente");
    }

    @Test
    void ultimaRejeicaoIncmSemMensagensTemListaVazia() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo semDados = criarProcesso(config, "Ana Ferreira");
        registarRecusaIncm(semDados, "REJEICAO_INCM", null);
        Processo semCampo = criarProcesso(config, "Rui Costa");
        registarRecusaIncm(semCampo, "REJEICAO_INCM", "{\"outro\":1}");
        Processo mensagensNaoLista = criarProcesso(config, "Rita Sousa");
        registarRecusaIncm(mensagensNaoLista, "REJEICAO_INCM", "{\"mensagens\":\"texto solto\"}");
        entityManager.clear();

        ProcessoConsultaService service = serviceComoMaster();

        assertThat(service.detalhe(semDados.getId()).ultimaRejeicaoIncm().mensagens()).isEmpty();
        assertThat(service.detalhe(semCampo.getId()).ultimaRejeicaoIncm().mensagens()).isEmpty();
        assertThat(service.detalhe(mensagensNaoLista.getId()).ultimaRejeicaoIncm().mensagens()).isEmpty();
    }

    @Test
    void ultimaRejeicaoIncmLidaDaTabelaDeRecusasComAsMensagensEAData() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        registarRecusaIncm(processo, "SUBMISSAO_INCM_REJEITADA", "{\"mensagens\":[\"da auditoria\"]}");
        registarRecusaIncmNaTabela(processo, 1, "antiga");
        ProcessoRejeicao recente = registarRecusaIncmNaTabela(processo, 2, "Primeira\nSegunda");
        entityManager.clear();

        UltimaRejeicaoIncmResponse recusa = serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoIncm();

        assertThat(recusa.mensagens()).containsExactly("Primeira", "Segunda");
        assertThat(recusa.data()).isEqualTo(recente.getCriadoEm());
    }

    @Test
    void ultimaRejeicaoIncmAntigaSoNaAuditoriaContinuaPreenchida() {
        Processo processo = criarProcesso(configPartilhada(), "Ana Ferreira");
        registarRecusaIncm(processo, "REJEICAO_INCM", "{\"mensagens\":[\"ImageURLList cannot be empty.\"]}");
        entityManager.clear();

        assertThat(processoRejeicaoRepository.count()).isZero();
        assertThat(serviceComoMaster().detalhe(processo.getId()).ultimaRejeicaoIncm().mensagens())
                .containsExactly("ImageURLList cannot be empty.");
    }

    private ProcessoRejeicao registarRecusaIncmNaTabela(Processo processo, int sequencia, String motivo) {
        ProcessoRejeicao rejeicao = processoRejeicaoRepository.saveAndFlush(ProcessoRejeicao.builder()
                .processo(processo).sequencia(sequencia).origem(OrigemRejeicao.INCM).motivoTexto(motivo).build());
        entityManager.flush();
        return rejeicao;
    }

    @Test
    void naoDeveConsultarUtilizadorUmaVezPorProcesso() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        criarProcesso(config, "Ana Ferreira");
        criarProcesso(config, "Bruno Costa");
        criarProcesso(config, "Carla Nunes");
        entityManager.clear();

        AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
        ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
        when(contextoUtilizadorResolver.resolver())
                .thenReturn(new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, null, null, null, null, null, null, null));
        ProcessoConsultaService service = new ProcessoConsultaService(processoRepository, auditoriaRepository,
                autorizacaoService, contextoUtilizadorResolver, new ObjectMapper(), listaValoresRepository, utilizadorRepository,
                mock(ProcessoService.class), mock(ProcessoValidacaoService.class), new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper()),
                mock(CompletudeDocumentalService.class), new CatalogoFormularios(),
                mock(TerritorioInstrumentoService.class), processoRejeicaoRepository, processoRejeicaoItemRepository,
                idusProcessoRepository, snitReferenciaRepository, mock(AssociacaoInstrumentosService.class),
                mock(DinamicaAssociadaService.class), mock(DestinoProcessoService.class), mock(RegrasRedesDefesaSgifr.class),
                new RegrasTerritorioSgifr(mock(ListaValoresRepository.class), new ObjectMapper()), mock(RegrasFormularioRen.class));

        SessionFactory sessionFactory = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        // Filtra pelo tipo criado aqui: a base H2 é partilhada e outros testes deixam Processos gravados.
        Page<ProcessoListagemResponse> pagina = service.listar(
                new ProcessoFiltro(null, null, null, config.getInstrumentoDinamicaConfig().getTipoInstrumento().getId(), null, null, null, null, null,
                        null, null, null, null, null), PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(ProcessoListagemResponse::autorNome)
                .containsExactlyInAnyOrder("Ana Ferreira", "Bruno Costa", "Carla Nunes");
        assertThat(pagina.getContent()).allSatisfy(linha -> {
            assertThat(linha.nomeDinamica()).isEqualTo("Elaboração");
            assertThat(linha.nomeProcedimento()).isEqualTo("Decisão de Início");
            assertThat(linha.entidadeNome()).isEqualTo("Câmara Municipal");
        });
        // O nome vem dos mesmos tipos já carregados para o código: um carregamento por tipo, nunca um por Processo.
        assertThat(statistics.getEntityStatistics(TipoDinamica.class.getName()).getFetchCount()).isLessThanOrEqualTo(1);
        assertThat(statistics.getEntityStatistics(TipoProcedimento.class.getName()).getFetchCount()).isLessThanOrEqualTo(1);
        // fetchCount conta inicializações de proxy (N+1): com o fetch join tem de ficar a zero.
        EntityStatistics utilizadorStats = statistics.getEntityStatistics(Utilizador.class.getName());
        assertThat(utilizadorStats.getFetchCount()).isZero();
    }

    private Processo processoComConcelhos(InstrumentoProcedimentoConfig config, String nomeAutor, String... concelhos) {
        Processo processo = criarProcesso(config, nomeAutor);
        Instrumento instrumento = processo.getDinamica().getInstrumento();
        for (String concelho : concelhos) {
            instrumentoConcelhoRepository.saveAndFlush(new InstrumentoConcelho(instrumento, concelho));
        }
        entityManager.clear();
        return processo;
    }

    private List<UUID> listarPorTerritorio(String concelho, String nutsII, Processo... candidatos) {
        ProcessoFiltro filtro = new ProcessoFiltro(null, null, null, null, null, null, null, null, null, null, null, null,
                concelho, nutsII);
        Set<UUID> candidatosIds = Arrays.stream(candidatos).map(Processo::getId).collect(Collectors.toSet());
        return serviceComoMaster().listar(filtro, PageRequest.of(0, 50)).getContent().stream()
                .map(ProcessoListagemResponse::id)
                .filter(candidatosIds::contains)
                .toList();
    }

    @Test
    void filtraPorConcelhoSoOsProcessosDoInstrumentoComEsseConcelho() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo lisboa = processoComConcelhos(config, "Ana Ferreira", "1106");
        Processo porto = processoComConcelhos(config, "Rui Costa", "1312");

        assertThat(listarPorTerritorio("1106", null, lisboa, porto)).containsExactly(lisboa.getId());
    }

    @Test
    void filtraPorQualquerConcelhoDeUmInstrumentoIntermunicipal() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo intermunicipal = processoComConcelhos(config, "Ana Ferreira", "1106", "1110");
        Processo outro = processoComConcelhos(config, "Rui Costa", "1312");

        assertThat(listarPorTerritorio("1110", null, intermunicipal, outro)).containsExactly(intermunicipal.getId());
    }

    @Test
    void filtraPorNutsIIPelosMunicipiosDaReplica() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo lisboa = processoComConcelhos(config, "Ana Ferreira", "1106");
        Processo intermunicipal = processoComConcelhos(config, "Bruno Costa", "1110", "1312");
        Processo porto = processoComConcelhos(config, "Rui Costa", "1312");
        when(snitReferenciaRepository.findCodigosMunicipiosDaNut2("LISBOA")).thenReturn(List.of("1106", "1110"));

        assertThat(listarPorTerritorio(null, "LISBOA", lisboa, intermunicipal, porto))
                .containsExactlyInAnyOrder(lisboa.getId(), intermunicipal.getId());
    }

    @Test
    void concelhoENutsIICombinamPorE() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo lisboa = processoComConcelhos(config, "Ana Ferreira", "1106");
        Processo porto = processoComConcelhos(config, "Rui Costa", "1312");
        when(snitReferenciaRepository.findCodigosMunicipiosDaNut2("LISBOA")).thenReturn(List.of("1106", "1110"));

        assertThat(listarPorTerritorio("1312", "LISBOA", lisboa, porto)).isEmpty();
        assertThat(listarPorTerritorio("1106", "LISBOA", lisboa, porto)).containsExactly(lisboa.getId());
    }

    @Test
    void codigoSemCorrespondenciaDaPaginaVaziaSemErro() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo lisboa = processoComConcelhos(config, "Ana Ferreira", "1106");
        when(snitReferenciaRepository.findCodigosMunicipiosDaNut2("INEXISTENTE")).thenReturn(List.of());

        assertThat(listarPorTerritorio("9999", null, lisboa)).isEmpty();
        assertThat(listarPorTerritorio(null, "INEXISTENTE", lisboa)).isEmpty();
    }

    @Test
    void instrumentoSemConcelhosNaoApareceComOsFiltrosMasApareceSemEles() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo semConcelhos = processoComConcelhos(config, "Ana Ferreira");
        when(snitReferenciaRepository.findCodigosMunicipiosDaNut2("LISBOA")).thenReturn(List.of("1106"));

        assertThat(listarPorTerritorio("1106", null, semConcelhos)).isEmpty();
        assertThat(listarPorTerritorio(null, "LISBOA", semConcelhos)).isEmpty();
        assertThat(listarPorTerritorio(null, null, semConcelhos)).containsExactly(semConcelhos.getId());
    }

    @Test
    void nutsIISemMunicipiosNaReplicaDaPaginaVazia() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo lisboa = processoComConcelhos(config, "Ana Ferreira", "1106");
        when(snitReferenciaRepository.findCodigosMunicipiosDaNut2("NORTE")).thenReturn(List.of());

        assertThat(listarPorTerritorio(null, "NORTE", lisboa)).isEmpty();
    }

    private Processo processoComRecusas(InstrumentoProcedimentoConfig config, String nomeAutor, int recusas) {
        Processo processo = criarProcesso(config, nomeAutor);
        Utilizador autor = processo.getCriadoPor();
        DocumentoConfig documentoConfig = entityManager.persist(new DocumentoConfig(config, "REG_" + nomeAutor.length() + recusas,
                "Regulamento", "Alteração ao Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA,
                FormatoDocumento.EDITAVEL, "[\"pdf\"]", 1));
        ProcessoDocumento documento = entityManager.persist(new ProcessoDocumento(processo, documentoConfig,
                CategoriaDocumento.PUBLICACAO, "pdf", "regulamento.pdf", "privado/regulamento.pdf", "a".repeat(64), "v1", 10L,
                autor));
        ProcessoInformacaoBase deliberacao = entityManager.persist(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));
        ListaValores categoria = listaValoresRepository.saveAndFlush(
                new ListaValores("CATEGORIA_REJEICAO_DGT", "FMT" + recusas, "Formato", 1));
        ListaValores motivo = listaValoresRepository.saveAndFlush(
                new ListaValores("MOTIVO_REJEICAO_DGT", "NED" + recusas, "Ficheiro em formato não editável", 1));
        for (int sequencia = 1; sequencia <= recusas; sequencia++) {
            ProcessoRejeicao recusa = entityManager.persist(ProcessoRejeicao.builder().processo(processo)
                    .sequencia(sequencia).origem(OrigemRejeicao.DGT).criadoPor(autor).build());
            entityManager.persist(new ProcessoRejeicaoItem(recusa, documento, null, categoria, motivo, "Carregar em PDF/A."));
            entityManager.persist(new ProcessoRejeicaoItem(recusa, null, deliberacao, categoria, motivo, "Falta a data."));
        }
        entityManager.flush();
        return processo;
    }

    private long consultasAoLerEstatisticas(ProcessoConsultaService service, Processo processo, Statistics statistics) {
        entityManager.clear();
        statistics.clear();
        service.estatisticas(processo.getId(), null, null, null);
        return statistics.getPrepareStatementCount();
    }

    @Test
    void estatisticasLeemAsRecusasEOsItensSemUmaConsultaPorRecusa() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo comUmaRecusa = processoComRecusas(config, "Ana Ferreira", 1);
        Processo comCincoRecusas = processoComRecusas(config, "Bruno Costa", 5);
        ProcessoConsultaService service = serviceComoMaster();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();

        long comUma = consultasAoLerEstatisticas(service, comUmaRecusa, statistics);
        long comCinco = consultasAoLerEstatisticas(service, comCincoRecusas, statistics);

        assertThat(comCinco).isEqualTo(comUma);
        for (Class<?> associada : List.of(ProcessoDocumento.class, DocumentoConfig.class, ProcessoInformacaoBase.class,
                ListaValores.class)) {
            assertThat(statistics.getEntityStatistics(associada.getName()).getFetchCount()).as(associada.getSimpleName()).isZero();
        }

        entityManager.clear();
        EstatisticasProcessoResponse resposta = service.estatisticas(comCincoRecusas.getId(), null, null, null);
        assertThat(resposta.recusas()).extracting(RecusaResponse::sequencia).containsExactly(1, 2, 3, 4, 5);
        assertThat(resposta.recusas().get(4).itens()).extracting(ItemRecusaResponse::tipo, ItemRecusaResponse::nomeFicheiro,
                        ItemRecusaResponse::separador, ItemRecusaResponse::elemento, ItemRecusaResponse::notas)
                .containsExactly(
                        tuple(TipoItemValidacao.DOCUMENTO, "regulamento.pdf", "Alteração ao Regulamento", null, "Carregar em PDF/A."),
                        tuple(TipoItemValidacao.INFORMACAO_BASE, null, null, ElementoInformacaoBase.DELIBERACAO, "Falta a data."));
        assertThat(resposta.recusas().get(4).itens()).allSatisfy(item -> {
            assertThat(item.categoria()).isEqualTo(new ValorListaResponse("FMT5", "Formato"));
            assertThat(item.motivo().descricao()).isEqualTo("Ficheiro em formato não editável");
        });
    }

    // --- Concelhos na listagem ---

    private ProcessoConsultaService serviceComTerritorio() {
        ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
        when(contextoUtilizadorResolver.resolver())
                .thenReturn(new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, null, null, null, null, null, null, null));
        TerritorioInstrumentoService territorio = new TerritorioInstrumentoService(instrumentoConcelhoRepository,
                snitReferenciaRepository, new ObjectMapper(), mock(TerritorioLocalService.class),
                new ReferenciaSnitConsultaService(snitReferenciaRepository, new ObjectMapper()));
        return new ProcessoConsultaService(processoRepository, auditoriaRepository, mock(AutorizacaoService.class),
                contextoUtilizadorResolver, new ObjectMapper(), listaValoresRepository, utilizadorRepository,
                mock(ProcessoService.class), mock(ProcessoValidacaoService.class), new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper()),
                mock(CompletudeDocumentalService.class), new CatalogoFormularios(),
                territorio, processoRejeicaoRepository, processoRejeicaoItemRepository,
                idusProcessoRepository, snitReferenciaRepository, mock(AssociacaoInstrumentosService.class),
                mock(DinamicaAssociadaService.class), mock(DestinoProcessoService.class), mock(RegrasRedesDefesaSgifr.class),
                new RegrasTerritorioSgifr(mock(ListaValoresRepository.class), new ObjectMapper()), mock(RegrasFormularioRen.class));
    }

    private void replicaComMunicipios(String... codigoENome) {
        List<SnitReferencia> municipios = new ArrayList<>();
        for (int i = 0; i < codigoENome.length; i += 2) {
            municipios.add(new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, codigoENome[i], codigoENome[i + 1],
                    "{\"dtcc\":\"" + codigoENome[i] + "\"}", Instant.parse("2026-11-16T10:00:00Z")));
        }
        when(snitReferenciaRepository.findByListaAndCodigoIn(eq(ListaReferenciaSnit.MUNICIPALITIES), any()))
                .thenReturn(municipios);
    }

    private Page<ProcessoListagemResponse> listarDoTipo(ProcessoConsultaService service, InstrumentoProcedimentoConfig config) {
        return listarDoTipo(service, config, 20);
    }

    private Page<ProcessoListagemResponse> listarDoTipo(ProcessoConsultaService service, InstrumentoProcedimentoConfig config,
                                                        int tamanho) {
        return service.listar(new ProcessoFiltro(null, null, null,
                config.getInstrumentoDinamicaConfig().getTipoInstrumento().getId(), null, null, null, null, null,
                null, null, null, null, null), PageRequest.of(0, tamanho));
    }

    @Test
    void listaOsNomesDosConcelhosDoInstrumentoPelaOrdemDosCodigos() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo sintra = processoComConcelhos(config, "Ana Ferreira", "1111");
        Processo intermunicipal = processoComConcelhos(config, "Rui Costa", "1111", "1105", "1110", "1106", "1109");
        replicaComMunicipios("1105", "Cascais", "1106", "Lisboa", "1109", "Loures", "1110", "Oeiras", "1111", "Sintra");

        Map<UUID, List<String>> concelhos = listarDoTipo(serviceComTerritorio(), config).getContent().stream()
                .collect(Collectors.toMap(ProcessoListagemResponse::id, ProcessoListagemResponse::concelhos));

        assertThat(concelhos.get(sintra.getId())).containsExactly("Sintra");
        assertThat(concelhos.get(intermunicipal.getId()))
                .containsExactly("Cascais", "Lisboa", "Loures", "Oeiras", "Sintra");
    }

    @Test
    void semInstrumentoOuSemConcelhosOsConcelhosVemVazios() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        Processo semConcelhos = criarProcesso(config, "Ana Ferreira");
        Utilizador autor = semConcelhos.getCriadoPor();
        Dinamica dinamicaSemIgt = dinamicaRepository.saveAndFlush(
                new Dinamica(config.getInstrumentoDinamicaConfig(), "Alteração sem IGT", autor));
        Processo semIgt = processoRepository.saveAndFlush(new Processo(dinamicaSemIgt, config, autor, semConcelhos.getEntidade()));
        entityManager.clear();

        Map<UUID, List<String>> concelhos = listarDoTipo(serviceComTerritorio(), config).getContent().stream()
                .collect(Collectors.toMap(ProcessoListagemResponse::id, ProcessoListagemResponse::concelhos));

        assertThat(concelhos.get(semConcelhos.getId())).isEmpty();
        assertThat(concelhos.get(semIgt.getId())).isEmpty();
    }

    @Test
    void osConcelhosDaPaginaLeemSeSemUmaConsultaPorProcesso() {
        InstrumentoProcedimentoConfig config = configPartilhada();
        processoComConcelhos(config, "Ana Ferreira", "1111");
        processoComConcelhos(config, "Rui Costa", "1105");
        replicaComMunicipios("1105", "Cascais", "1111", "Sintra");
        ProcessoConsultaService service = serviceComTerritorio();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

        // Página de um com mais linhas a seguir: conta também a consulta do total, como a de 20 abaixo.
        statistics.clear();
        listarDoTipo(service, config, 1);
        long consultasComUmProcesso = statistics.getQueryExecutionCount();

        for (int i = 0; i < 19; i++) {
            processoComConcelhos(config, "Autor " + i, i % 2 == 0 ? "1111" : "1105");
        }
        clearInvocations(snitReferenciaRepository);
        statistics.clear();
        Page<ProcessoListagemResponse> pagina = listarDoTipo(service, config);

        assertThat(pagina.getContent()).hasSize(20).allSatisfy(linha -> assertThat(linha.concelhos()).hasSize(1));
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(consultasComUmProcesso);
        verify(snitReferenciaRepository, times(1)).findByListaAndCodigoIn(eq(ListaReferenciaSnit.MUNICIPALITIES), any());
    }
}
