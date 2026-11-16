package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.CriarNotificacaoRequest;
import com.conkord.saigt.dto.notificacao.CriarNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.DestinatarioNotificacaoRequest;
import com.conkord.saigt.exception.ConkordException;
import com.conkord.saigt.exception.NotificacaoDestinatarioInexistenteException;
import com.conkord.saigt.exception.NotificacaoDestinatariosInvalidosException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoTipoNaoCriavelException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.exception.NotificacaoPrazoNaoAplicavelException;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificacaoCriacaoServiceTest {

    private static final jakarta.validation.ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();

    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final EntidadeRepository entidadeRepository = mock(EntidadeRepository.class);
    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final ContextoUtilizadorResolver resolver = mock(ContextoUtilizadorResolver.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final Validator validator = FABRICA.getValidator();
    private final NotificacaoCriacaoService servico = new NotificacaoCriacaoService(notificacaoRepository,
            entidadeRepository, utilizadorRepository, processoRepository, resolver, autorizacaoService, validator,
            publicador);

    private final Utilizador master = utilizador("Marta Master");
    private final Utilizador destinatario = utilizador("Ana Teixeira");
    private final Utilizador externo = utilizador("Rui Matos");
    private final Entidade entidadeA = entidade(1L);
    private final Entidade entidadeB = entidade(2L);

    @AfterAll
    static void fecharFabrica() {
        FABRICA.close();
    }

    @BeforeEach
    void setUp() {
        sessao(PerfilInterno.MASTER);
        when(utilizadorRepository.getReferenceById(master.getId())).thenReturn(master);
        when(entidadeRepository.findAllById(any())).thenAnswer(chamada -> {
            Collection<Long> ids = chamada.getArgument(0);
            return List.of(entidadeA, entidadeB).stream().filter(e -> ids.contains(e.getId())).toList();
        });
        when(utilizadorRepository.findAllById(any())).thenAnswer(chamada -> {
            Collection<UUID> ids = chamada.getArgument(0);
            return ids.contains(destinatario.getId()) ? List.of(destinatario) : List.of();
        });
        when(notificacaoRepository.saveAll(anyList())).thenAnswer(chamada -> {
            List<Notificacao> linhas = new ArrayList<>(chamada.getArgument(0));
            linhas.forEach(linha -> ReflectionTestUtils.setField(linha, "id", UUID.randomUUID()));
            return linhas;
        });
    }

    @Test
    void estaticaSemDestinatariosGravaUmaLinhaParaTodosSemLote() {
        CriarNotificacaoResponse resposta = servico.criar(pedido(TipoNotificacao.ESTATICA, List.of()));

        List<Notificacao> linhas = gravadas();
        assertThat(linhas).singleElement().satisfies(linha -> {
            assertThat(linha.getEntidade()).isNull();
            assertThat(linha.getUtilizador()).isNull();
            assertThat(linha.getPerfilDestino()).isNull();
            assertThat(linha.getLoteId()).isNull();
            assertThat(linha.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
            assertThat(linha.getCriadoPor()).isSameAs(master);
            assertThat(linha.isCanalEmail()).isFalse();
        });
        assertThat(resposta.loteId()).isNull();
        assertThat(resposta.notificacoes()).hasSize(1);
        NotificacaoCriadaEvento evento = eventoPublicado();
        assertThat(evento.loteId()).isNull();
        assertThat(evento.emailContactoTramitacao()).as("a Notícia segue o destino de cada linha").isFalse();
    }

    @Test
    void estaticaComUmDestinatarioGravaUmaLinhaSemLote() {
        servico.criar(pedido(TipoNotificacao.ESTATICA, List.of(entidade(entidadeA))));

        assertThat(gravadas()).singleElement().satisfies(linha -> {
            assertThat(linha.getEntidade()).isSameAs(entidadeA);
            assertThat(linha.getLoteId()).isNull();
        });
    }

    @Test
    void estaticaParaDuasEntidadesEUmUtilizadorGravaTresLinhasComOMesmoLote() {
        CriarNotificacaoResponse resposta = servico.criar(pedido(TipoNotificacao.ESTATICA,
                List.of(entidade(entidadeA), entidade(entidadeB), utilizador(destinatario))));

        List<Notificacao> linhas = gravadas();
        assertThat(linhas).hasSize(3);
        assertThat(linhas).extracting(Notificacao::getLoteId).containsOnly(resposta.loteId()).doesNotContainNull();
        assertThat(linhas).extracting(Notificacao::getEntidade).containsExactly(entidadeA, entidadeB, null);
        assertThat(linhas).extracting(Notificacao::getUtilizador).containsExactly(null, null, destinatario);
        assertThat(resposta.notificacoes()).hasSize(3);

        NotificacaoCriadaEvento evento = eventoPublicado();
        assertThat(evento.loteId()).isEqualTo(resposta.loteId());
        assertThat(evento.notificacaoIds()).containsExactlyElementsOf(linhas.stream().map(Notificacao::getId).toList());
    }

    @Test
    void destinatarioRepetidoContaUmaVez() {
        servico.criar(pedido(TipoNotificacao.ESTATICA, List.of(entidade(entidadeA), entidade(entidadeA))));

        assertThat(gravadas()).singleElement().satisfies(linha -> assertThat(linha.getLoteId()).isNull());
    }

    @Test
    void respostaObrigatoriaComProcessoGravaUmaLinhaComEntidadeEProcesso() {
        Processo processo = new Processo(null, null, master, entidadeA);
        UUID processoId = UUID.randomUUID();
        ReflectionTestUtils.setField(processo, "id", processoId);
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(processo));
        OffsetDateTime prazo = OffsetDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

        CriarNotificacaoResponse resposta = servico.criar(new CriarNotificacaoRequest(
                TipoNotificacao.RESPOSTA_OBRIGATORIA, "  Regularização  ", " Texto ", processoId, null, null, prazo,
                List.of(entidade(entidadeA))));

        assertThat(gravadas()).singleElement().satisfies(linha -> {
            assertThat(linha.getTipo()).isEqualTo(TipoNotificacao.RESPOSTA_OBRIGATORIA);
            assertThat(linha.getEntidade()).isSameAs(entidadeA);
            assertThat(linha.getProcesso()).isSameAs(processo);
            assertThat(linha.getLoteId()).isNull();
            assertThat(linha.getTitulo()).isEqualTo("Regularização");
            assertThat(linha.getTexto()).isEqualTo("Texto");
            assertThat(linha.getPrazo()).isEqualTo(prazo.toInstant());
        });
        assertThat(resposta.notificacoes().get(0).processoId()).isEqualTo(processoId);
    }

    @Test
    void respostaObrigatoriaSemProcessoNemIdIgtEAceite() {
        servico.criar(pedido(TipoNotificacao.RESPOSTA_OBRIGATORIA, List.of(utilizador(destinatario))));

        assertThat(gravadas()).singleElement().satisfies(linha -> {
            assertThat(linha.getUtilizador()).isSameAs(destinatario);
            assertThat(linha.getProcesso()).isNull();
            assertThat(linha.getIdIgtSnit()).isNull();
        });
    }

    @Test
    void respostaObrigatoriaSoComIdIgtEDescricaoEAceite() {
        servico.criar(new CriarNotificacaoRequest(TipoNotificacao.RESPOSTA_OBRIGATORIA, "Título", "Texto", null,
                "IGT-123", "PDM de Cascais", null, List.of(entidade(entidadeA))));

        assertThat(gravadas()).singleElement().satisfies(linha -> {
            assertThat(linha.getIdIgtSnit()).isEqualTo("IGT-123");
            assertThat(linha.getDescricaoIgt()).isEqualTo("PDM de Cascais");
        });
    }

    @Test
    void respostaObrigatoriaSemDestinatarioOuComDoisERecusada() {
        assertRecusa(pedido(TipoNotificacao.RESPOSTA_OBRIGATORIA, List.of()),
                NotificacaoDestinatariosInvalidosException.CODIGO);
        assertRecusa(pedido(TipoNotificacao.RESPOSTA_OBRIGATORIA, List.of(entidade(entidadeA), entidade(entidadeB))),
                NotificacaoDestinatariosInvalidosException.CODIGO);
    }

    @Test
    void destinatarioComOsDoisCamposOuSemNenhumERecusado() {
        assertRecusa(pedido(TipoNotificacao.ESTATICA,
                        List.of(new DestinatarioNotificacaoRequest(entidadeA.getId(), destinatario.getId()))),
                NotificacaoDestinatariosInvalidosException.CODIGO);
        assertRecusa(pedido(TipoNotificacao.ESTATICA, List.of(new DestinatarioNotificacaoRequest(null, null))),
                NotificacaoDestinatariosInvalidosException.CODIGO);
    }

    @Test
    void entidadeUtilizadorOuProcessoInexistenteDa404ENadaEGravado() {
        assertRecusa(pedido(TipoNotificacao.ESTATICA, List.of(new DestinatarioNotificacaoRequest(99L, null))),
                NotificacaoDestinatarioInexistenteException.CODIGO);
        assertRecusa(pedido(TipoNotificacao.ESTATICA,
                        List.of(new DestinatarioNotificacaoRequest(null, UUID.randomUUID()))),
                NotificacaoDestinatarioInexistenteException.CODIGO);
        UUID processoInexistente = UUID.randomUUID();
        when(processoRepository.findById(processoInexistente)).thenReturn(Optional.empty());
        assertRecusa(new CriarNotificacaoRequest(TipoNotificacao.RESPOSTA_OBRIGATORIA, "Título", "Texto",
                        processoInexistente, null, null, null, List.of(entidade(entidadeA))),
                NotificacaoDestinatarioInexistenteException.CODIGO);
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"TEMPORARIA", "ALERTA"})
    void tiposNaoCriaveisSaoRecusadosAntesDoPerfil(TipoNotificacao tipo) {
        sessao(PerfilInterno.TECNICO);

        assertRecusa(pedido(tipo, List.of()), NotificacaoTipoNaoCriavelException.CODIGO);
        sessaoExterna();
        assertRecusa(pedido(tipo, List.of()), NotificacaoTipoNaoCriavelException.CODIGO);
    }

    @Test
    void pedidoDeApoioSoComTituloETextoGravaUmaLinhaParaOsMasterComOAutor() {
        sessaoExterna();

        CriarNotificacaoResponse resposta = servico.criar(new CriarNotificacaoRequest(TipoNotificacao.PEDIDO_APOIO,
                " Ajuda ", " Não consigo submeter ", null, null, null, null, null));

        assertThat(gravadas()).singleElement().satisfies(linha -> {
            assertThat(linha.getTipo()).isEqualTo(TipoNotificacao.PEDIDO_APOIO);
            assertThat(linha.getPerfilDestino()).isEqualTo(PerfilInterno.MASTER);
            assertThat(linha.getCriadoPor()).isSameAs(externo);
            assertThat(linha.getProcesso()).isNull();
            assertThat(linha.getUtilizador()).isNull();
            assertThat(linha.getEntidade()).isNull();
            assertThat(linha.getLoteId()).isNull();
            assertThat(linha.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
            assertThat(linha.isCanalEmail()).isFalse();
            assertThat(linha.getTitulo()).isEqualTo("Ajuda");
            assertThat(linha.getTexto()).isEqualTo("Não consigo submeter");
        });
        assertThat(resposta.loteId()).isNull();
        assertThat(resposta.notificacoes()).hasSize(1);
        NotificacaoCriadaEvento evento = eventoPublicado();
        assertThat(evento.loteId()).isNull();
        assertThat(evento.emailContactoTramitacao()).isFalse();
        assertThat(evento.notificacaoIds()).containsExactly(resposta.notificacoes().get(0).id());
    }

    @Test
    void pedidoDeApoioComProcessoVisivelFicaLigadoAoProcesso() {
        sessaoExterna();
        Processo processo = processo();
        when(autorizacaoService.processoVisivel(any(), any())).thenReturn(true);

        CriarNotificacaoResponse resposta = servico.criar(new CriarNotificacaoRequest(TipoNotificacao.PEDIDO_APOIO,
                "Ajuda", "Texto", processo.getId(), " IGT-9 ", "  ", null, List.of()));

        assertThat(gravadas()).singleElement().satisfies(linha -> {
            assertThat(linha.getProcesso()).isSameAs(processo);
            assertThat(linha.getIdIgtSnit()).isEqualTo("IGT-9");
            assertThat(linha.getDescricaoIgt()).isNull();
        });
        assertThat(resposta.notificacoes().get(0).processoId()).isEqualTo(processo.getId());
    }

    @Test
    void processoDeOutraEntidadeOuInexistenteDaOMesmo404ENadaEGravado() {
        sessaoExterna();
        Processo processo = processo();
        when(autorizacaoService.processoVisivel(any(), any())).thenReturn(false);
        UUID inexistente = UUID.randomUUID();
        when(processoRepository.findById(inexistente)).thenReturn(Optional.empty());

        for (UUID processoId : List.of(processo.getId(), inexistente)) {
            assertThatThrownBy(() -> servico.criar(new CriarNotificacaoRequest(TipoNotificacao.PEDIDO_APOIO, "Ajuda",
                    "Texto", processoId, null, null, null, null)))
                    .isInstanceOfSatisfying(NotificacaoDestinatarioInexistenteException.class, erro ->
                            assertThat(erro.getMessage()).isEqualTo("Destinatário inexistente."));
        }
        verify(notificacaoRepository, never()).saveAll(anyList());
        verify(publicador, never()).publishEvent(any());
    }

    @Test
    void regularizacaoComDescricaoIgtGravaUmaLinhaParaOsMaster() {
        sessaoExterna();

        servico.criar(new CriarNotificacaoRequest(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, "Regularização",
                "Publicado no DR n.º 12", null, null, " PDM de Teste ", null, null));

        assertThat(gravadas()).singleElement().satisfies(linha -> {
            assertThat(linha.getTipo()).isEqualTo(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO);
            assertThat(linha.getPerfilDestino()).isEqualTo(PerfilInterno.MASTER);
            assertThat(linha.getDescricaoIgt()).isEqualTo("PDM de Teste");
            assertThat(linha.getCriadoPor()).isSameAs(externo);
            assertThat(linha.isCanalEmail()).isFalse();
        });
        assertThat(eventoPublicado().notificacaoIds()).hasSize(1);
    }

    @Test
    void regularizacaoSemDescricaoIgtDa400ComOCampo() {
        sessaoExterna();

        for (String descricao : new String[]{null, "   "}) {
            assertThatThrownBy(() -> servico.criar(new CriarNotificacaoRequest(
                    TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, "Regularização", "Texto", null, "IGT-1", descricao,
                    null, null)))
                    .isInstanceOfSatisfying(ValidacaoException.class, erro -> {
                        assertThat(erro.getCodigo()).isEqualTo(ValidacaoException.CODIGO);
                        assertThat(erro.getCampos()).extracting(ValidacaoException.CampoErro::path)
                                .containsExactly("descricaoIgt");
                    });
        }
        verify(notificacaoRepository, never()).saveAll(anyList());
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"PEDIDO_APOIO", "REGULARIZACAO_PROCEDIMENTO"})
    void utilizadorInternoNaoCriaOsTiposDaEntidade(TipoNotificacao tipo) {
        for (PerfilInterno perfil : PerfilInterno.values()) {
            sessao(perfil);
            assertThatThrownBy(() -> servico.criar(new CriarNotificacaoRequest(tipo, "Ajuda", "Texto", null, null,
                    "PDM", null, null)))
                    .isInstanceOfSatisfying(NotificacaoPerfilNaoAutorizadoException.class, erro ->
                            assertThat(erro.getMessage()).isEqualTo("Este tipo de notificação é criado pela entidade."));
        }
        verify(notificacaoRepository, never()).saveAll(anyList());
        verify(publicador, never()).publishEvent(any());
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"PEDIDO_APOIO", "REGULARIZACAO_PROCEDIMENTO"})
    void tiposDaEntidadeComPrazoDao400PrazoNaoAplicavel(TipoNotificacao tipo) {
        sessaoExterna();

        assertRecusa(new CriarNotificacaoRequest(tipo, "Ajuda", "Texto", null, null, "PDM",
                OffsetDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC), null),
                NotificacaoPrazoNaoAplicavelException.CODIGO);
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacao.class, names = {"PEDIDO_APOIO", "REGULARIZACAO_PROCEDIMENTO"})
    void tiposDaEntidadeComDestinatariosDao400DestinatariosInvalidos(TipoNotificacao tipo) {
        sessaoExterna();

        assertRecusa(new CriarNotificacaoRequest(tipo, "Ajuda", "Texto", null, null, "PDM", null,
                List.of(entidade(entidadeA))), NotificacaoDestinatariosInvalidosException.CODIGO);
    }

    @Test
    void tiposDaEntidadeValidamOFormatoAntesDasRegrasDoTipo() {
        sessaoExterna();

        assertThatThrownBy(() -> servico.criar(new CriarNotificacaoRequest(TipoNotificacao.PEDIDO_APOIO, " ",
                "Texto", null, "x".repeat(51), null,
                OffsetDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC), null)))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo(ValidacaoException.CODIGO);
                    assertThat(erro.getCampos()).extracting(ValidacaoException.CampoErro::path)
                            .containsExactly("idIgtSnit", "titulo");
                });
    }

    @Test
    void quemNaoEMasterRecebeOPerfilNaoAutorizadoMesmoComCorpoInvalido() {
        for (PerfilInterno perfil : new PerfilInterno[]{PerfilInterno.TECNICO, PerfilInterno.VISITANTE, null}) {
            sessao(perfil);
            assertRecusa(pedido(TipoNotificacao.ESTATICA, List.of()), NotificacaoPerfilNaoAutorizadoException.CODIGO);
            assertRecusa(new CriarNotificacaoRequest(TipoNotificacao.ESTATICA, "", "", null, null, null, null, null),
                    NotificacaoPerfilNaoAutorizadoException.CODIGO);
        }
    }

    @Test
    void tituloEmBrancoOuLongoETextoLongoDao400ComOsCampos() {
        assertThatThrownBy(() -> servico.criar(new CriarNotificacaoRequest(TipoNotificacao.ESTATICA, "  ",
                "x".repeat(5001), null, null, null, null, null)))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo(ValidacaoException.CODIGO);
                    assertThat(erro.getCampos()).extracting(ValidacaoException.CampoErro::path)
                            .containsExactly("texto", "titulo");
                });
        assertThatThrownBy(() -> servico.criar(new CriarNotificacaoRequest(TipoNotificacao.ESTATICA,
                "x".repeat(201), "Texto", null, null, null, null, null)))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> assertThat(erro.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("titulo"));
        verify(notificacaoRepository, never()).saveAll(anyList());
    }

    @Test
    void pedidoDeApoioDoUtilizadorDaEntidadeVaiParaOMasterComOProcessoDaEntidade() {
        sessaoEntidade(entidadeA.getId());
        Processo processo = processoDe(entidadeA);
        when(autorizacaoService.processoVisivel(any(), any())).thenReturn(true);
        when(notificacaoRepository.saveAll(anyList())).thenAnswer(chamada -> {
            List<Notificacao> linhas = chamada.getArgument(0);
            linhas.forEach(linha -> ReflectionTestUtils.setField(linha, "id", UUID.randomUUID()));
            return linhas;
        });

        CriarNotificacaoResponse resposta = servico.criar(new CriarNotificacaoRequest(TipoNotificacao.PEDIDO_APOIO,
                " Dúvida na submissão ", " Não consigo carregar a planta. ", processo.getId(), null, null, null, List.of()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notificacao>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoRepository).saveAll(captor.capture());
        Notificacao linha = captor.getValue().getFirst();
        assertThat(linha.getTipo()).isEqualTo(TipoNotificacao.PEDIDO_APOIO);
        assertThat(linha.getPerfilDestino()).isEqualTo(PerfilInterno.MASTER);
        assertThat(linha.getProcesso()).isSameAs(processo);
        assertThat(linha.getCriadoPor()).isSameAs(master);
        assertThat(linha.getEntidade()).isNull();
        assertThat(linha.getUtilizador()).isNull();
        assertThat(linha.getTitulo()).isEqualTo("Dúvida na submissão");
        assertThat(linha.getTexto()).isEqualTo("Não consigo carregar a planta.");
        assertThat(resposta.notificacoes()).hasSize(1);
        assertThat(eventoPublicado().notificacaoIds()).containsExactly(linha.getId());
    }

    @Test
    void pedidoDeApoioSobreUmProcessoDeOutraEntidadeRespondeComoInexistente() {
        sessaoEntidade(entidadeB.getId());
        Processo processo = processoDe(entidadeA);

        assertThatThrownBy(() -> servico.criar(new CriarNotificacaoRequest(TipoNotificacao.PEDIDO_APOIO, "Dúvida",
                "Texto", processo.getId(), null, null, null, List.of())))
                .isInstanceOf(NotificacaoDestinatarioInexistenteException.class);
        verify(notificacaoRepository, never()).saveAll(anyList());
        verify(publicador, never()).publishEvent(any());
    }

    @ParameterizedTest
    @EnumSource(PerfilInterno.class)
    void osPerfisInternosNaoPedemApoioPorqueSaoQuemResponde(PerfilInterno perfil) {
        sessao(perfil);

        assertRecusa(pedido(TipoNotificacao.PEDIDO_APOIO, List.of()), NotificacaoPerfilNaoAutorizadoException.CODIGO);
        verify(notificacaoRepository, never()).saveAll(anyList());
    }

    private Processo processoDe(Entidade entidade) {
        Processo processo = new Processo(null, null, master, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        return processo;
    }

    private void sessaoEntidade(Long entidadeId) {
        when(resolver.resolver()).thenReturn(new ContextoUtilizador(master.getId(), "INCM", null, null, null,
                null, null, null, null, null, entidadeId));
    }

    private void assertRecusa(CriarNotificacaoRequest pedido, String codigo) {
        assertThatThrownBy(() -> servico.criar(pedido))
                .isInstanceOfSatisfying(ConkordException.class, erro -> assertThat(erro.getCodigo()).isEqualTo(codigo));
        verify(notificacaoRepository, never()).saveAll(anyList());
        verify(publicador, never()).publishEvent(any());
    }

    private List<Notificacao> gravadas() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notificacao>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoRepository, times(1)).saveAll(captor.capture());
        return captor.getValue();
    }

    private NotificacaoCriadaEvento eventoPublicado() {
        ArgumentCaptor<NotificacaoCriadaEvento> captor = ArgumentCaptor.forClass(NotificacaoCriadaEvento.class);
        verify(publicador, times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    private void sessao(PerfilInterno perfil) {
        when(resolver.resolver()).thenReturn(new ContextoUtilizador(master.getId(), "AD_DGT", perfil, null, null,
                null, null, null, null, null, null));
    }

    private void sessaoExterna() {
        when(resolver.resolver()).thenReturn(new ContextoUtilizador(externo.getId(), "INCM", null, "500000001",
                "ENTIDADE", null, null, null, null, null, entidadeA.getId()));
        when(utilizadorRepository.getReferenceById(externo.getId())).thenReturn(externo);
    }

    private Processo processo() {
        Processo processo = new Processo(null, null, externo, entidadeA);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        return processo;
    }

    private static CriarNotificacaoRequest pedido(TipoNotificacao tipo, List<DestinatarioNotificacaoRequest> destinos) {
        return new CriarNotificacaoRequest(tipo, "Título", "Texto", null, null, null, null, destinos);
    }

    private static DestinatarioNotificacaoRequest entidade(Entidade entidade) {
        return new DestinatarioNotificacaoRequest(entidade.getId(), null);
    }

    private static DestinatarioNotificacaoRequest utilizador(Utilizador utilizador) {
        return new DestinatarioNotificacaoRequest(null, utilizador.getId());
    }

    private static Utilizador utilizador(String nome) {
        Utilizador utilizador = new Utilizador(nome, "ficticio@exemplo.pt");
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());
        return utilizador;
    }

    private static Entidade entidade(long id) {
        Entidade entidade = EntidadeTeste.nova("50000000" + id, "Câmara de teste " + id);
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }
}
