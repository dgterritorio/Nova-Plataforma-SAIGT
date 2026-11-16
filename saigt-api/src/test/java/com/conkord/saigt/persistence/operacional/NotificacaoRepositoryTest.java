package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificacaoRepositoryTest {

    private static final Instant PRAZO = Instant.parse("2026-12-31T23:59:59Z");

    @Autowired
    private NotificacaoRepository repository;

    @Autowired
    private NotificacaoMensagemRepository mensagemRepository;

    @Autowired
    private NotificacaoDocumentoRepository documentoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Utilizador autor;
    private Entidade entidade;
    private Processo processo;

    @BeforeEach
    void criarContexto() {
        TipoInstrumento pdm = entityManager.persist(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = entityManager.persist(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = entityManager.persist(new InstrumentoDinamicaConfig(pdm, elaboracao));
        TipoProcedimento decisao = entityManager.persist(new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig config = entityManager.persist(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        entidade = entityManager.persist(EntidadeTeste.nova("501234500", "Câmara Municipal"));
        autor = entityManager.persist(new Utilizador("Ana Ferreira", "ana.ferreira@cm.pt", "INCM"));
        Instrumento instrumento = entityManager.persist(new Instrumento(pdm, "1", "2026/PDM/1", autor, autor));
        Dinamica dinamica = entityManager.persist(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        processo = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        entityManager.flush();
    }

    @Test
    void deveGravarELerUmaNotificacaoDeCadaTipoComOsValoresPorOmissao() {
        Arrays.stream(TipoNotificacao.values())
                .forEach(tipo -> repository.save(new Notificacao(tipo, "Título " + tipo, "Texto", null)));
        entityManager.flush();
        entityManager.clear();

        List<Notificacao> lidas = repository.findAll();

        assertThat(lidas).extracting(Notificacao::getTipo).containsExactlyInAnyOrder(TipoNotificacao.values());
        assertThat(lidas).allSatisfy(notificacao -> {
            assertThat(notificacao.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
            assertThat(notificacao.isCanalEmail()).isFalse();
            assertThat(notificacao.getCriadoPor()).isNull();
        });
    }

    @Test
    void deveDevolverOContextoEOsDestinosGravados() {
        UUID loteId = UUID.randomUUID();
        Notificacao notificacao = new Notificacao(TipoNotificacao.RESPOSTA_OBRIGATORIA, "Pedido", "Responda", autor);
        notificacao.setProcesso(processo);
        notificacao.setUtilizador(autor);
        notificacao.setEntidade(entidade);
        notificacao.setPerfilDestino(PerfilInterno.MASTER);
        notificacao.setLoteId(loteId);
        notificacao.setPrazo(PRAZO);
        UUID id = repository.saveAndFlush(notificacao).getId();
        entityManager.clear();

        Notificacao lida = repository.findById(id).orElseThrow();

        assertThat(lida.getId()).isNotNull();
        assertThat(lida.getCriadoEm()).isNotNull();
        assertThat(lida.getAtualizadoEm()).isNotNull();
        assertThat(lida.getTitulo()).isEqualTo("Pedido");
        assertThat(lida.getTexto()).isEqualTo("Responda");
        assertThat(lida.getProcesso().getId()).isEqualTo(processo.getId());
        assertThat(lida.getUtilizador().getId()).isEqualTo(autor.getId());
        assertThat(lida.getEntidade().getId()).isEqualTo(entidade.getId());
        assertThat(lida.getCriadoPor().getId()).isEqualTo(autor.getId());
        assertThat(lida.getPerfilDestino()).isEqualTo(PerfilInterno.MASTER);
        assertThat(lida.getLoteId()).isEqualTo(loteId);
        assertThat(lida.getPrazo()).isEqualTo(PRAZO);
    }

    @Test
    void deveDevolverTodasAsNotificacoesDoMesmoLote() {
        UUID loteId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            Notificacao notificacao = new Notificacao(TipoNotificacao.TEMPORARIA, "Alerta", "Texto", null);
            notificacao.setLoteId(loteId);
            repository.save(notificacao);
        }
        Notificacao deOutroLote = new Notificacao(TipoNotificacao.TEMPORARIA, "Alerta", "Texto", null);
        deOutroLote.setLoteId(UUID.randomUUID());
        repository.save(deOutroLote);
        repository.save(new Notificacao(TipoNotificacao.TEMPORARIA, "Alerta", "Texto", null));
        entityManager.flush();

        assertThat(repository.findByLoteId(loteId))
                .hasSize(3)
                .allSatisfy(notificacao -> assertThat(notificacao.getLoteId()).isEqualTo(loteId));
    }

    @Test
    void deveDevolverAsMensagensDaNotificacaoPorOrdemDeCriacao() {
        Notificacao notificacao = repository.save(new Notificacao(TipoNotificacao.PEDIDO_APOIO, "Apoio", "Texto", autor));
        Notificacao outra = repository.save(new Notificacao(TipoNotificacao.PEDIDO_APOIO, "Apoio", "Texto", autor));
        NotificacaoMensagem segunda = guardarMensagem(notificacao, "segunda", "2026-11-11T10:05:00Z");
        NotificacaoMensagem primeira = guardarMensagem(notificacao, "primeira", "2026-11-11T10:00:00Z");
        guardarMensagem(outra, "de outra notificação", "2026-11-11T09:00:00Z");
        entityManager.clear();

        List<NotificacaoMensagem> mensagens = mensagemRepository.findByNotificacao_IdOrderByCriadoEmAsc(notificacao.getId());

        assertThat(mensagens).extracting(NotificacaoMensagem::getId).containsExactly(primeira.getId(), segunda.getId());
        assertThat(mensagens).extracting(NotificacaoMensagem::getTexto).containsExactly("primeira", "segunda");
    }

    @Test
    void deveDevolverOAnexoDaNotificacao() {
        Notificacao notificacao = repository.save(
                new Notificacao(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, "Regularização", "Texto", autor));
        String caminho = "notificacoes/" + notificacao.getId() + "/comprovativo.pdf";
        documentoRepository.saveAndFlush(new NotificacaoDocumento(notificacao, "comprovativo.pdf", caminho, 2048L, autor));
        entityManager.clear();

        List<NotificacaoDocumento> anexos = documentoRepository.findByNotificacao_IdOrderByCriadoEmAsc(notificacao.getId());

        assertThat(anexos).singleElement().satisfies(anexo -> {
            assertThat(anexo.getNomeFicheiro()).isEqualTo("comprovativo.pdf");
            assertThat(anexo.getCaminhoFicheiro()).isEqualTo(caminho);
            assertThat(anexo.getTamanhoBytes()).isEqualTo(2048L);
            assertThat(anexo.getCriadoPor().getId()).isEqualTo(autor.getId());
        });
    }

    @Test
    void deveDetetarSoATemporariaDaPlataformaDoProcessoCriadaDepoisDoInstante() {
        Instant limiar = Instant.parse("2026-11-01T00:00:00Z");
        guardarTemporaria(processo, null, "2026-10-31T23:59:59Z");
        guardarTemporaria(processo, autor, "2026-11-02T00:00:00Z");

        assertThat(repository.existsByProcesso_IdAndTipoAndCriadoPorIsNullAndCriadoEmGreaterThanEqual(
                processo.getId(), TipoNotificacao.TEMPORARIA, limiar)).isFalse();

        guardarTemporaria(processo, null, "2026-11-01T00:00:00Z");

        assertThat(repository.existsByProcesso_IdAndTipoAndCriadoPorIsNullAndCriadoEmGreaterThanEqual(
                processo.getId(), TipoNotificacao.TEMPORARIA, limiar)).isTrue();
        assertThat(repository.existsByProcesso_IdAndTipoAndCriadoPorIsNullAndCriadoEmGreaterThanEqual(
                processo.getId(), TipoNotificacao.ALERTA, limiar)).isFalse();
        assertThat(repository.existsByProcesso_IdAndTipoAndCriadoPorIsNullAndCriadoEmGreaterThanEqual(
                UUID.randomUUID(), TipoNotificacao.TEMPORARIA, limiar)).isFalse();
    }

    @Test
    void pedidoDeApoioDaPlataformaIgualSoContaEnquantoEstiverAtivo() {
        String texto = "O utilizador X tentou iniciar sessão na entidade «Y» (NIPC 509999990).";
        guardarPedidoApoio(texto, null, EstadoNotificacao.RESOLVIDA);
        guardarPedidoApoio(texto, autor, EstadoNotificacao.ATIVA);
        guardarPedidoApoio(texto + " Outro.", null, EstadoNotificacao.ATIVA);

        assertThat(repository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTexto(
                TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, texto)).isFalse();

        guardarPedidoApoio(texto, null, EstadoNotificacao.ATIVA);

        assertThat(repository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTexto(
                TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, texto)).isTrue();
        assertThat(repository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTexto(
                TipoNotificacao.ALERTA, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, texto)).isFalse();
    }

    @Test
    void alertaDaPlataformaComOMesmoTituloSoContaEnquantoEstiverAtivo() {
        String titulo = "Integração indisponível: INCM";
        guardarAlerta(titulo, EstadoNotificacao.LIDA);
        guardarAlerta(titulo, EstadoNotificacao.REMOVIDA);
        guardarAlerta("Integração indisponível: SNIT", EstadoNotificacao.ATIVA);

        assertThat(repository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTitulo(
                TipoNotificacao.ALERTA, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, titulo)).isFalse();

        guardarAlerta(titulo, EstadoNotificacao.ATIVA);

        assertThat(repository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTitulo(
                TipoNotificacao.ALERTA, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, titulo)).isTrue();
        assertThat(repository.existsByTipoAndEstadoAndCriadoPorIsNullAndPerfilDestinoAndTitulo(
                TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA, PerfilInterno.MASTER, titulo)).isFalse();
    }

    private void guardarAlerta(String titulo, EstadoNotificacao estado) {
        Notificacao alerta = new Notificacao(TipoNotificacao.ALERTA, titulo, "Texto", null);
        alerta.setPerfilDestino(PerfilInterno.MASTER);
        alerta.setEstado(estado);
        repository.saveAndFlush(alerta);
    }

    private void guardarPedidoApoio(String texto, Utilizador criadoPor, EstadoNotificacao estado) {
        Notificacao pedido = new Notificacao(TipoNotificacao.PEDIDO_APOIO, "Login recusado", texto, criadoPor);
        pedido.setPerfilDestino(PerfilInterno.MASTER);
        pedido.setEstado(estado);
        repository.saveAndFlush(pedido);
    }

    private void guardarTemporaria(Processo doProcesso, Utilizador criadoPor, String criadoEm) {
        Notificacao notificacao = new Notificacao(TipoNotificacao.TEMPORARIA, "Alerta", "Texto", criadoPor);
        notificacao.setProcesso(doProcesso);
        notificacao.setUtilizador(autor);
        UUID id = repository.saveAndFlush(notificacao).getId();
        jdbcTemplate.update("UPDATE notificacao SET criado_em = ? WHERE id = ?",
                Timestamp.from(Instant.parse(criadoEm)), id);
        entityManager.clear();
    }

    private NotificacaoMensagem guardarMensagem(Notificacao notificacao, String texto, String criadoEm) {
        NotificacaoMensagem mensagem = mensagemRepository.saveAndFlush(new NotificacaoMensagem(notificacao, autor, texto));
        jdbcTemplate.update("UPDATE notificacao_mensagem SET criado_em = ? WHERE id = ?",
                Timestamp.from(Instant.parse(criadoEm)), mensagem.getId());
        return mensagem;
    }
}
