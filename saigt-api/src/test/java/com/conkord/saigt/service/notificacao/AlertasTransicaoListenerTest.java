package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItem;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItemRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.ProcessoEnviadoPcmEvento;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertasTransicaoListenerTest {

    private static final String NUMERO = "00042";
    private static final String TIPO = "Discussão pública";
    private static final String NOME_AUTOR = "Ulisses Fictício";
    private static final String EMAIL_AUTOR = "ulisses.ficticio@cm-teste.pt";
    private static final String NOME_DGT = "Dora Fictícia";
    private static final String EMAIL_DGT = "dora.ficticia@dgt.pt";

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final ProcessoRejeicaoItemRepository itemRepository = mock(ProcessoRejeicaoItemRepository.class);
    private final AuditoriaRepository auditoriaRepository = mock(AuditoriaRepository.class);
    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);
    private final AlertaProcessoService alertaProcessoService = mock(AlertaProcessoService.class);
    private final AlertasTransicaoListener listener = new AlertasTransicaoListener(processoRepository, itemRepository,
            auditoriaRepository, utilizadorRepository, alertaProcessoService);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertasTransicaoListener.class);
    private final List<String> textosCriados = new ArrayList<>();

    private Utilizador autor;
    private Utilizador colega;
    private Utilizador dgt;
    private Processo processo;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        autor = utilizador(NOME_AUTOR, EMAIL_AUTOR);
        colega = utilizador("Vera Fictícia", "vera.ficticia@cm-teste.pt");
        dgt = utilizador(NOME_DGT, EMAIL_DGT);
        Entidade entidade = EntidadeTeste.nova("500000099", "Câmara de teste");
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(new InstrumentoDinamicaConfig(null, null),
                new TipoProcedimento("DISCUSSAO", TIPO), 1, "SEGUNDA_H");
        processo = new Processo(null, config, autor, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(processo, "numeroProcesso", NUMERO);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(alertaProcessoService.criarParaAutorEEntidade(any(), anyString(), anyString())).thenAnswer(chamada -> {
            textosCriados.add(chamada.getArgument(1) + " | " + chamada.getArgument(2));
            UUID lote = UUID.randomUUID();
            return List.of(linha(autor, lote), linha(colega, lote));
        });
        when(alertaProcessoService.criarParaUtilizador(any(), any(), anyString(), anyString())).thenAnswer(chamada -> {
            textosCriados.add(chamada.getArgument(2) + " | " + chamada.getArgument(3));
            return linha(chamada.getArgument(1), null);
        });
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        // Nenhum texto leva nomes nem emails, e nenhum log leva textos, títulos, nomes ou emails.
        assertThat(textosCriados).noneMatch(t -> t.contains(NOME_AUTOR) || t.contains(EMAIL_AUTOR)
                || t.contains(NOME_DGT) || t.contains(EMAIL_DGT) || t.contains("@"));
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains(NUMERO)
                || m.contains(TIPO) || m.contains("Processo ") || m.contains("@") || m.contains("Fictíci")
                || m.contains("Duplicado"));
    }

    private static Utilizador utilizador(String nome, String email) {
        Utilizador utilizador = new Utilizador(nome, email);
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());
        return utilizador;
    }

    private Notificacao linha(Utilizador destinatario, UUID lote) {
        Notificacao notificacao = new Notificacao(TipoNotificacao.TEMPORARIA, "t", "t", null);
        notificacao.setUtilizador(destinatario);
        notificacao.setLoteId(lote);
        return notificacao;
    }

    private void noEstado(EstadoProcesso estado) {
        processo.setEstado(estado);
    }

    private List<ILoggingEvent> logsDoNivel(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).toList();
    }

    @Test
    void submissaoAvisaAutorEEntidade() {
        noEstado(EstadoProcesso.ENVIADO_DGT);

        listener.aoTransitar(ProcessoTransitouEvento.de(processo.getId(), EstadoProcesso.EM_SUBMISSAO,
                EstadoProcesso.ENVIADO_DGT));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo submetido",
                "O processo 00042 (Discussão pública) foi submetido.");
        verify(alertaProcessoService, never()).criarParaUtilizador(any(), any(), any(), any());
        assertThat(logsDoNivel(Level.INFO)).singleElement().satisfies(log -> assertThat(log.getFormattedMessage())
                .contains("processoId=" + processo.getId(), "destino=ENVIADO_DGT", "linhas=2", "loteId="));
    }

    @Test
    void rejeicaoDgtLevaOsNomesDosItensEOMotivoSemRepetir() {
        noEstado(EstadoProcesso.REJEITADO_DGT);
        ListaValores motivo = new ListaValores("MOTIVO_REJEICAO_DGT", "M1", "Ficheiro ilegível", 1);
        ProcessoDocumento documento = new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "Regulamento.pdf", "privado/x", "a".repeat(64), "v1", 10L, autor);
        ProcessoInformacaoBase deliberacao = new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO);
        when(itemRepository.findByRejeicao_IdOrderByIdAsc(7L)).thenReturn(List.of(
                new ProcessoRejeicaoItem(null, documento, null, null, motivo, "Notas de " + NOME_AUTOR),
                new ProcessoRejeicaoItem(null, null, deliberacao, null, motivo, "Outra nota.")));

        listener.aoTransitar(ProcessoTransitouEvento.rejeicaoDgt(processo.getId(), 7L));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo rejeitado pela DGT",
                "O processo 00042 (Discussão pública) foi rejeitado pela DGT nos itens Regulamento.pdf, "
                        + "Informação Base da Deliberação com o motivo Ficheiro ilegível.");
    }

    @Test
    void rejeicaoDgtComMotivosDiferentesESemMotivoNumItem() {
        noEstado(EstadoProcesso.REJEITADO_DGT);
        ProcessoInformacaoBase texto = new ProcessoInformacaoBase(processo, ElementoInformacaoBase.TEXTO_ATO);
        ProcessoDocumento planta = new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "Planta.pdf", "privado/y", "a".repeat(64), "v1", 10L, autor);
        when(itemRepository.findByRejeicao_IdOrderByIdAsc(8L)).thenReturn(List.of(
                new ProcessoRejeicaoItem(null, null, texto, null,
                        new ListaValores("MOTIVO_REJEICAO_DGT", "M2", "Dados em falta", 1), "Nota."),
                new ProcessoRejeicaoItem(null, planta, null, null,
                        new ListaValores("MOTIVO_REJEICAO_DGT", "M3", "Formato inválido", 2), "Nota."),
                new ProcessoRejeicaoItem(null, planta, null, null, null, "Nota.")));

        listener.aoTransitar(ProcessoTransitouEvento.rejeicaoDgt(processo.getId(), 8L));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo rejeitado pela DGT",
                "O processo 00042 (Discussão pública) foi rejeitado pela DGT nos itens Informação Base do Texto do Ato, "
                        + "Planta.pdf, Planta.pdf com o motivo Dados em falta, Formato inválido.");
    }

    @Test
    void rejeicaoDgtSemItensNemMotivoOmiteOsDoisTrechos() {
        noEstado(EstadoProcesso.REJEITADO_DGT);
        when(itemRepository.findByRejeicao_IdOrderByIdAsc(9L)).thenReturn(List.of());

        listener.aoTransitar(ProcessoTransitouEvento.rejeicaoDgt(processo.getId(), 9L));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo rejeitado pela DGT",
                "O processo 00042 (Discussão pública) foi rejeitado pela DGT.");
    }

    @Test
    void anulacaoDgtLevaOMotivoDoPedidoSemRepetirOPontoFinal() {
        noEstado(EstadoProcesso.ANULADO);

        listener.aoTransitar(ProcessoTransitouEvento.anulacaoDgt(processo.getId(), EstadoProcesso.VALIDADO_DGT,
                "  Duplicado.  "));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo anulado pela DGT",
                "O processo 00042 (Discussão pública) foi anulado pela DGT com o motivo Duplicado.");
    }

    @Test
    void rejeicaoIncmAvisaAutorEntidadeEOUtilizadorDgtDoEnvioComOMesmoTexto() {
        noEstado(EstadoProcesso.ANULADO_INCM);
        when(auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "PROCESSO", processo.getId().toString(), "ENVIAR_INCM"))
                .thenReturn(Optional.of(Auditoria.builder().utilizadorId(dgt.getId()).build()));
        when(utilizadorRepository.findById(dgt.getId())).thenReturn(Optional.of(dgt));
        String texto = "O processo 00042 (Discussão pública) foi rejeitado pela INCM com o motivo "
                + "ImageURLList cannot be empty.; ActTypeId is invalid.";

        listener.aoTransitar(ProcessoTransitouEvento.rejeicaoIncm(processo.getId(), EstadoProcesso.VALIDADO_DGT,
                List.of("ImageURLList cannot be empty.", " ActTypeId is invalid. ")));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo rejeitado pela INCM", texto);
        verify(alertaProcessoService).criarParaUtilizador(processo, dgt, "Processo rejeitado pela INCM", texto);
        assertThat(logsDoNivel(Level.WARN)).isEmpty();
        assertThat(logsDoNivel(Level.INFO)).singleElement().satisfies(log -> assertThat(log.getFormattedMessage())
                .contains("destino=ANULADO_INCM", "avisoDgt=true"));
    }

    @Test
    void rejeicaoIncmSemLinhaEnviarIncmAvisaSoAutorEEntidadeEDeixaWarn() {
        noEstado(EstadoProcesso.ANULADO_INCM);
        when(auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        listener.aoTransitar(ProcessoTransitouEvento.rejeicaoIncm(processo.getId(), EstadoProcesso.EM_VALIDACAO_INCM,
                List.of()));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo rejeitado pela INCM",
                "O processo 00042 (Discussão pública) foi rejeitado pela INCM com o motivo motivo não indicado pela INCM.");
        verify(alertaProcessoService, never()).criarParaUtilizador(any(), any(), any(), any());
        assertThat(logsDoNivel(Level.WARN)).singleElement().satisfies(log ->
                assertThat(log.getFormattedMessage()).contains("processoId=" + processo.getId()));
    }

    @Test
    void publicacaoLevaALigacaoAoDr() {
        noEstado(EstadoProcesso.PUBLICADO);
        processo.setLinkDr("https://exemplo.pt/dr/2026/000001");

        listener.aoTransitar(ProcessoTransitouEvento.de(processo.getId(), EstadoProcesso.EM_VALIDACAO_INCM,
                EstadoProcesso.PUBLICADO));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo publicado",
                "O processo 00042 (Discussão pública) foi publicado em Diário da República: "
                        + "https://exemplo.pt/dr/2026/000001.");
    }

    @Test
    void publicacaoSemLigacaoTerminaNoDiarioDaRepublica() {
        noEstado(EstadoProcesso.PUBLICADO);

        listener.aoTransitar(ProcessoTransitouEvento.de(processo.getId(), EstadoProcesso.EM_VALIDACAO_INCM,
                EstadoProcesso.PUBLICADO));

        verify(alertaProcessoService).criarParaAutorEEntidade(processo, "Processo publicado",
                "O processo 00042 (Discussão pública) foi publicado em Diário da República.");
    }

    @ParameterizedTest
    @CsvSource({"ENVIADO_DGT,EM_SUBMISSAO,Em Submissão", "REJEITADO_DGT,EM_SUBMISSAO,Em Submissão",
            "ENVIADO_DGT,EM_VALIDACAO_DGT,Em Validação DGT", "EM_VALIDACAO_DGT,VALIDADO_DGT,Validado DGT",
            "VALIDADO_DGT,EM_VALIDACAO_INCM,Em Validação INCM"})
    void alteracaoDeEstadoAvisaSoOAutor(EstadoProcesso origem, EstadoProcesso destino, String nomeEstado) {
        noEstado(destino);

        listener.aoTransitar(ProcessoTransitouEvento.de(processo.getId(), origem, destino));

        verify(alertaProcessoService).criarParaUtilizador(processo, autor, "Alteração do estado do processo",
                "O processo 00042 (Discussão pública) passou ao estado " + nomeEstado + ".");
        verify(alertaProcessoService, never()).criarParaAutorEEntidade(any(), any(), any());
    }

    @Test
    void envioPcmAvisaOUtilizadorPcm() {
        noEstado(EstadoProcesso.VALIDADO_DGT);
        Utilizador pcm = utilizador("Paula Fictícia", "paula@pcm.gov.pt");
        processo.setPcmAtribuidoA(pcm);

        listener.aoEnviarPcm(new ProcessoEnviadoPcmEvento(processo.getId(), pcm.getId()));

        verify(alertaProcessoService).criarParaUtilizador(processo, pcm, "Processo para aprovação da PCM",
                "O processo 00042 (Discussão pública) aguarda aprovação da PCM.");
    }

    @Test
    void envioPcmJaDevolvidoOuReatribuidoNaoAvisa() {
        noEstado(EstadoProcesso.VALIDADO_DGT);
        processo.setPcmAtribuidoA(utilizador("Outra", "outra@pcm.gov.pt"));

        listener.aoEnviarPcm(new ProcessoEnviadoPcmEvento(processo.getId(), UUID.randomUUID()));

        verifyNoInteractions(alertaProcessoService);
        assertThat(logsDoNivel(Level.INFO)).singleElement().satisfies(log ->
                assertThat(log.getFormattedMessage()).contains("estado mudou entretanto", "processoId="));
    }

    @Test
    void estadoMudadoEntretantoNaoCriaNadaEDeixaInfo() {
        noEstado(EstadoProcesso.EM_VALIDACAO_DGT);

        listener.aoTransitar(ProcessoTransitouEvento.de(processo.getId(), EstadoProcesso.EM_SUBMISSAO,
                EstadoProcesso.ENVIADO_DGT));

        verifyNoInteractions(alertaProcessoService);
        assertThat(logsDoNivel(Level.INFO)).singleElement().satisfies(log -> assertThat(log.getFormattedMessage())
                .contains("estado mudou entretanto", "processoId=" + processo.getId(), "destino=ENVIADO_DGT"));
    }

    @Test
    void excecaoNaCriacaoFicaEmErrorComOProcessoEODestinoSemSairDoListener() {
        noEstado(EstadoProcesso.ENVIADO_DGT);
        when(alertaProcessoService.criarParaAutorEEntidade(any(), any(), any()))
                .thenThrow(new IllegalStateException("falha simulada"));

        assertThatCode(() -> listener.aoTransitar(ProcessoTransitouEvento.de(processo.getId(),
                EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT))).doesNotThrowAnyException();

        assertThat(logsDoNivel(Level.ERROR)).singleElement().satisfies(log -> assertThat(log.getFormattedMessage())
                .contains("processoId=" + processo.getId(), "destino=ENVIADO_DGT"));
    }

    @Test
    void eventoDeTransicaoNormalizaAsMensagensNulas() {
        ProcessoTransitouEvento evento = new ProcessoTransitouEvento(UUID.randomUUID(), EstadoProcesso.VALIDADO_DGT,
                EstadoProcesso.ANULADO_INCM, null, null, null);

        assertThat(evento.mensagensIncm()).isEmpty();
    }
}
