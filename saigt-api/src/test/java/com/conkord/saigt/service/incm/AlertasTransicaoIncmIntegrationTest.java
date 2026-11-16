package com.conkord.saigt.service.incm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.GetActDetailsResponse;
import com.conkord.saigt.integracao.incm.ResultadoChamadaIncm;
import com.conkord.saigt.integracao.incm.SubmitNewActResponse;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import com.conkord.saigt.service.notificacao.AlertasTransicaoListener;
import com.conkord.saigt.service.notificacao.NotificacaoCriadaEvento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Envio e consulta à INCM reais, com o {@link ClienteIncm} simulado: os avisos nascem depois do commit da transação de
 * cada operação. V é da entidade do processo; D é o utilizador DGT que faz o envio.
 */
@SpringBootTest(properties = "app.publico.base-url=https://saigt.teste")
@ActiveProfiles("test")
@RecordApplicationEvents
class AlertasTransicaoIncmIntegrationTest extends EnvioIncmTesteBase {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final long ACT_IDENTIFIER = 900000000000213L;
    private static final String REJEITADO_INCM = "Processo rejeitado pela INCM";
    private static final String ALTERACAO_ESTADO = "Alteração do estado do processo";

    @MockBean
    private ClienteIncm clienteIncm;

    @Autowired private ConsultaIncmService consultaIncmService;
    @Autowired private ApplicationEvents eventos;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    @Autowired private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertasTransicaoListener.class);

    private Cenario cenario;
    private Processo processo;
    private Utilizador colegaV;
    private Utilizador dgtD;

    private record Linha(UUID utilizadorId, String titulo, String texto, UUID loteId, Long entidadeId,
                         String estado) {
    }

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        int n = SEQUENCIA.incrementAndGet();
        cenario = criarCenario("Aviso de início " + n);
        processo = cenario.processo();
        colegaV = utilizadorRepository.saveAndFlush(new Utilizador("Vera Fictícia", "v" + n + "@cm-incm.pt", "INCM"));
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(colegaV, processo.getEntidade(), Instant.now()));
        Utilizador dgt = new Utilizador("Dora Fictícia", "d" + n + "@dgt.pt", "AD_DGT");
        dgt.setPerfilInterno(PerfilInterno.TECNICO);
        dgtD = utilizadorRepository.saveAndFlush(dgt);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains("@")
                || m.contains("Fictíci") || m.contains("cannot be empty") || m.contains("Processo "));
    }

    private void incmResponde(boolean sucesso, List<String> mensagens) {
        SubmitNewActResponse resposta = new SubmitNewActResponse(sucesso, mensagens, sucesso ? ACT_IDENTIFIER : null);
        when(clienteIncm.submeterAto(eq(processo.getId()), any()))
                .thenReturn(new ResultadoChamadaIncm<>(200, resposta, null, 5L));
    }

    private void consultaDevolve(GetActDetailsResponse.Publication publicacao, List<String> erros) {
        GetActDetailsResponse resposta = new GetActDetailsResponse(true, null,
                publicacao == null ? "SubmissionRejected" : "SubmissionAccepted", null, null,
                publicacao == null ? null : "DR-2026-000213", null, null, publicacao == null ? "Anulado" : "Publicado",
                erros, null, publicacao);
        when(clienteIncm.obterDetalhesAto(eq(processo.getId()), anyLong()))
                .thenReturn(new ResultadoChamadaIncm<>(200, resposta, null, 5L));
    }

    private Processo enviarComo(Utilizador ator) {
        return transactionTemplate.execute(estado -> service.enviar(processo.getId(), ator.getId()));
    }

    private List<Linha> linhas() {
        return jdbc.query("SELECT utilizador_id, titulo, texto, lote_id, entidade_id, estado FROM notificacao "
                        + "WHERE processo_id = ? ORDER BY criado_em, id",
                (rs, i) -> new Linha(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getObject(5, Long.class), rs.getString(6)),
                processo.getId());
    }

    private List<Linha> comTitulo(String titulo) {
        return linhas().stream().filter(l -> l.titulo().equals(titulo)).toList();
    }

    private String textoRejeicao(String motivos) {
        String numeroETipo = jdbc.queryForObject("SELECT p.numero_processo || ' (' || tp.nome || ')' FROM processo p "
                + "JOIN instrumento_procedimento_config c ON c.id = p.instrumento_procedimento_config_id "
                + "JOIN tipo_procedimento tp ON tp.id = c.tipo_procedimento_id WHERE p.id = ?", String.class,
                processo.getId());
        return "O processo " + numeroETipo + " foi rejeitado pela INCM com o motivo " + motivos + ".";
    }

    private List<ProcessoTransitouEvento> transicoes() {
        return eventos.stream(ProcessoTransitouEvento.class).toList();
    }

    @Test
    void envioRecusadoAvisaAutorEntidadeEOUtilizadorDgtUmaVezESemAvisoDeEnviadoIncm() {
        List<String> mensagens = List.of("ImageURLList cannot be empty.", "ActTypeId is invalid.");
        incmResponde(false, mensagens);

        enviarComo(dgtD);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        String texto = textoRejeicao("ImageURLList cannot be empty.; ActTypeId is invalid");
        List<Linha> rejeicao = comTitulo(REJEITADO_INCM);
        assertThat(rejeicao).extracting(Linha::utilizadorId)
                .containsExactlyInAnyOrder(cenario.utilizadorId(), colegaV.getId(), dgtD.getId());
        assertThat(rejeicao).extracting(Linha::texto).containsOnly(texto);
        UUID lote = rejeicao.stream().filter(l -> l.utilizadorId().equals(cenario.utilizadorId())).findFirst()
                .orElseThrow().loteId();
        assertThat(lote).isNotNull();
        assertThat(rejeicao).filteredOn(l -> l.utilizadorId().equals(colegaV.getId())).extracting(Linha::loteId)
                .containsExactly(lote);
        assertThat(rejeicao).filteredOn(l -> l.utilizadorId().equals(dgtD.getId())).singleElement().satisfies(l -> {
            assertThat(l.loteId()).isNull();
            assertThat(l.entidadeId()).isNull();
            assertThat(l.estado()).isEqualTo("ATIVA");
        });
        assertThat(comTitulo(ALTERACAO_ESTADO)).isEmpty();
        assertThat(transicoes()).containsExactly(new ProcessoTransitouEvento(processo.getId(),
                EstadoProcesso.VALIDADO_DGT, EstadoProcesso.ANULADO_INCM, null, null, mensagens));
        List<NotificacaoCriadaEvento> notificacoes = eventos.stream(NotificacaoCriadaEvento.class).toList();
        assertThat(notificacoes).hasSize(2);
        assertThat(notificacoes).filteredOn(e -> e.loteId() == null).singleElement()
                .satisfies(e -> assertThat(e.emailContactoTramitacao()).isFalse());
        assertThat(notificacoes).filteredOn(e -> e.loteId() != null).singleElement()
                .satisfies(e -> assertThat(e.emailContactoTramitacao()).isTrue());
    }

    @Test
    void envioAceiteAvisaSoOAutorComEmValidacaoIncm() {
        incmResponde(true, List.of());

        enviarComo(dgtD);

        Processo atual = recarregar(processo);
        assertThat(atual.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(linhas()).singleElement().satisfies(l -> {
            assertThat(l.utilizadorId()).isEqualTo(cenario.utilizadorId());
            assertThat(l.titulo()).isEqualTo(ALTERACAO_ESTADO);
            assertThat(l.texto()).endsWith("passou ao estado Em Validação INCM.");
            assertThat(l.loteId()).isNull();
        });
        assertThat(transicoes()).containsExactly(ProcessoTransitouEvento.de(processo.getId(),
                EstadoProcesso.VALIDADO_DGT, EstadoProcesso.EM_VALIDACAO_INCM));
    }

    @Test
    void recusaNaConsultaDiariaAvisaAutorEntidadeEOUtilizadorDgtQueEnviou() {
        incmResponde(true, List.of());
        enviarComo(dgtD);
        consultaDevolve(null, List.of("Sumário em falta.", "Data inválida."));

        assertThat(consultaIncmService.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.ANULADO_INCM);

        List<Linha> rejeicao = comTitulo(REJEITADO_INCM);
        assertThat(rejeicao).extracting(Linha::utilizadorId)
                .containsExactlyInAnyOrder(cenario.utilizadorId(), colegaV.getId(), dgtD.getId());
        assertThat(rejeicao).extracting(Linha::texto).containsOnly(textoRejeicao("Sumário em falta.; Data inválida"));
        assertThat(transicoes()).hasSize(2).last().isEqualTo(new ProcessoTransitouEvento(processo.getId(),
                EstadoProcesso.EM_VALIDACAO_INCM, EstadoProcesso.ANULADO_INCM, null, null,
                List.of("Sumário em falta.", "Data inválida.")));
    }

    @Test
    void recusaSemLinhaEnviarIncmAvisaSoAutorEEntidadeEDeixaWarn() {
        Processo atual = recarregar(processo);
        atual.setEstado(EstadoProcesso.EM_VALIDACAO_INCM);
        atual.setIncmActIdentifier(String.valueOf(ACT_IDENTIFIER));
        processoRepository.saveAndFlush(atual);
        consultaDevolve(null, List.of());

        consultaIncmService.consultar(processo.getId());

        List<Linha> rejeicao = comTitulo(REJEITADO_INCM);
        assertThat(rejeicao).extracting(Linha::utilizadorId)
                .containsExactlyInAnyOrder(cenario.utilizadorId(), colegaV.getId());
        assertThat(rejeicao).extracting(Linha::texto).containsOnly(textoRejeicao("motivo não indicado pela INCM"));
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains("processoId=" + processo.getId()));
    }

    @Test
    void publicacaoNaConsultaAvisaAutorEEntidadeComALigacaoEUmSoEvento() {
        Processo atual = recarregar(processo);
        atual.setEstado(EstadoProcesso.ENVIADO_INCM);
        atual.setIncmActIdentifier(String.valueOf(ACT_IDENTIFIER));
        processoRepository.saveAndFlush(atual);
        consultaDevolve(new GetActDetailsResponse.Publication("https://exemplo.pt/dr/2026/000213.pdf",
                "Aviso n.º 213/2026", "https://exemplo.pt/dr/2026/000213", "Diário da República n.º 213/2026",
                LocalDate.of(2026, 11, 12)), List.of());

        assertThat(consultaIncmService.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.PUBLICADO);

        List<Linha> publicado = comTitulo("Processo publicado");
        assertThat(publicado).extracting(Linha::utilizadorId)
                .containsExactlyInAnyOrder(cenario.utilizadorId(), colegaV.getId());
        assertThat(publicado).extracting(Linha::texto).allMatch(t -> t.endsWith(
                "foi publicado em Diário da República: https://exemplo.pt/dr/2026/000213."));
        assertThat(comTitulo(ALTERACAO_ESTADO)).as("o EM_VALIDACAO_INCM intermédio não avisa").isEmpty();
        assertThat(transicoes()).containsExactly(ProcessoTransitouEvento.de(processo.getId(),
                EstadoProcesso.ENVIADO_INCM, EstadoProcesso.PUBLICADO));
    }
}
