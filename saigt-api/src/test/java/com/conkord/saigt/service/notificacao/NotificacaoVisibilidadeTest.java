package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificacaoVisibilidade.class)
class NotificacaoVisibilidadeTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T12:00:00Z");

    @Autowired
    private NotificacaoVisibilidade visibilidade;
    @Autowired
    private NotificacaoRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    private Entidade entidadeA;
    private Entidade entidadeB;
    private Utilizador utilizadorA;
    private Utilizador outroDeA;
    private Utilizador master;

    @BeforeEach
    void criarContexto() {
        entidadeA = entityManager.persist(EntidadeTeste.nova("511000001", "Câmara A"));
        entidadeB = entityManager.persist(EntidadeTeste.nova("511000002", "Câmara B"));
        utilizadorA = entityManager.persist(new Utilizador("Utilizador A", "a@exemplo.pt", "INCM"));
        outroDeA = entityManager.persist(new Utilizador("Outro de A", "outro@exemplo.pt", "INCM"));
        master = new Utilizador("Master", "master@exemplo.pt");
        master.setPerfilInterno(PerfilInterno.MASTER);
        master = entityManager.persist(master);
        entityManager.flush();
    }

    @Test
    void condicao1DestinatarioEOProprioUtilizador() {
        Notificacao minha = gravar(TipoNotificacao.TEMPORARIA, null, n -> n.setUtilizador(utilizadorA));
        Notificacao deOutro = gravar(TipoNotificacao.TEMPORARIA, null, n -> n.setUtilizador(outroDeA));

        assertThat(visiveis(externo(utilizadorA, entidadeA))).contains(minha.getId()).doesNotContain(deOutro.getId());
    }

    @Test
    void condicao2LinhaDaEntidadeDaSessao() {
        Notificacao deA = gravar(TipoNotificacao.ESTATICA, null, n -> n.setEntidade(entidadeA));
        Notificacao deB = gravar(TipoNotificacao.ESTATICA, null, n -> n.setEntidade(entidadeB));

        assertThat(visiveis(externo(outroDeA, entidadeA))).contains(deA.getId()).doesNotContain(deB.getId());
        assertThat(visiveis(externo(outroDeA, entidadeB))).contains(deB.getId()).doesNotContain(deA.getId());
        assertThat(visiveis(interno(master, PerfilInterno.MASTER))).doesNotContain(deA.getId(), deB.getId());
    }

    @Test
    void condicao2DistingueEntidadesComOMesmoNipc() {
        Entidade gabinete1 = entityManager.persist(EntidadeTeste.nova("511000009", "Gabinete 1"));
        Entidade gabinete2 = entityManager.persist(EntidadeTeste.nova("511000009", "Gabinete 2"));
        Notificacao doGabinete1 = gravar(TipoNotificacao.ESTATICA, null, n -> n.setEntidade(gabinete1));
        Notificacao doGabinete2 = gravar(TipoNotificacao.ESTATICA, null, n -> n.setEntidade(gabinete2));

        assertThat(visiveis(externo(utilizadorA, gabinete1))).contains(doGabinete1.getId())
                .doesNotContain(doGabinete2.getId());
        assertThat(visiveis(externo(utilizadorA, gabinete2))).contains(doGabinete2.getId())
                .doesNotContain(doGabinete1.getId());
    }

    @Test
    void condicao2NaoSeAplicaSemEntidadeNaSessao() {
        Notificacao deA = gravar(TipoNotificacao.ESTATICA, null, n -> n.setEntidade(entidadeA));
        Notificacao minha = gravar(TipoNotificacao.TEMPORARIA, null, n -> n.setUtilizador(utilizadorA));
        ContextoUtilizador semEntidade = new ContextoUtilizador(utilizadorA.getId(), "INCM", null,
                entidadeA.getNipc(), "ENTIDADE", null, null, null, null, null, null);

        assertThat(visiveis(semEntidade)).contains(minha.getId()).doesNotContain(deA.getId());
    }

    @Test
    void condicao3PerfilDestinoMasterSoParaOMaster() {
        Notificacao alerta = gravar(TipoNotificacao.ALERTA, null, n -> n.setPerfilDestino(PerfilInterno.MASTER));

        assertThat(visiveis(interno(master, PerfilInterno.MASTER))).contains(alerta.getId());
        assertThat(visiveis(externo(utilizadorA, entidadeA))).doesNotContain(alerta.getId());
        assertThat(visiveis(interno(outroDeA, PerfilInterno.TECNICO))).doesNotContain(alerta.getId());
    }

    @Test
    void condicao4SemDestinoEParaTodos() {
        Notificacao paraTodos = gravar(TipoNotificacao.ESTATICA, null, n -> { });

        assertThat(visiveis(externo(utilizadorA, entidadeA))).contains(paraTodos.getId());
        assertThat(visiveis(externo(outroDeA, entidadeB))).contains(paraTodos.getId());
        assertThat(visiveis(interno(master, PerfilInterno.MASTER))).contains(paraTodos.getId());
    }

    @Test
    void condicao5OAutorAcompanhaOQueCriou() {
        Notificacao pedido = gravar(TipoNotificacao.PEDIDO_APOIO, utilizadorA,
                n -> n.setPerfilDestino(PerfilInterno.MASTER));

        assertThat(visiveis(externo(utilizadorA, entidadeA))).contains(pedido.getId());
        assertThat(visiveis(externo(outroDeA, entidadeA))).doesNotContain(pedido.getId());
    }

    @Test
    void semNenhumaCondicaoALinhaNaoEVisivel() {
        Notificacao alheia = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, n -> {
            n.setEntidade(entidadeB);
            n.setUtilizador(outroDeA);
        });

        assertThat(visiveis(externo(utilizadorA, entidadeA))).doesNotContain(alheia.getId());
        assertThat(visiveis(interno(UUID.randomUUID(), PerfilInterno.MASTER))).doesNotContain(alheia.getId());
    }

    @Test
    void noticiaAtivaComPrazoPassadoSaiDeVigencia() {
        Notificacao expirada = gravar(TipoNotificacao.ESTATICA, null, n -> n.setPrazo(AGORA.minus(Duration.ofDays(1))));
        Notificacao vigente = gravar(TipoNotificacao.ESTATICA, null, n -> n.setPrazo(AGORA.plus(Duration.ofDays(1))));
        Notificacao semPrazo = gravar(TipoNotificacao.ESTATICA, null, n -> { });
        Notificacao respostaComPrazoPassado = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, null,
                n -> n.setPrazo(AGORA.minus(Duration.ofDays(1))));
        Notificacao removidaComPrazoPassado = gravar(TipoNotificacao.ESTATICA, null, n -> {
            n.setPrazo(AGORA.minus(Duration.ofDays(1)));
            n.setEstado(EstadoNotificacao.REMOVIDA);
        });

        List<UUID> emVigencia = repository.findAll(visibilidade.emVigencia(AGORA)).stream().map(Notificacao::getId).toList();

        assertThat(emVigencia).doesNotContain(expirada.getId())
                .contains(vigente.getId(), semPrazo.getId(), respostaComPrazoPassado.getId(),
                        removidaComPrazoPassado.getId());
    }

    private Notificacao gravar(TipoNotificacao tipo, Utilizador criadoPor, java.util.function.Consumer<Notificacao> destino) {
        Notificacao notificacao = new Notificacao(tipo, "Título", "Texto", criadoPor);
        destino.accept(notificacao);
        Notificacao gravada = repository.saveAndFlush(notificacao);
        entityManager.clear();
        return gravada;
    }

    private List<UUID> visiveis(ContextoUtilizador contexto) {
        return repository.findAll(visibilidade.visiveisPara(contexto)).stream().map(Notificacao::getId).toList();
    }

    private static ContextoUtilizador externo(Utilizador utilizador, Entidade entidade) {
        return new ContextoUtilizador(utilizador.getId(), "INCM", null, entidade.getNipc(), "ENTIDADE", null, null,
                null, null, TipoEntidade.CM, entidade.getId());
    }

    private static ContextoUtilizador interno(Utilizador utilizador, PerfilInterno perfil) {
        return interno(utilizador.getId(), perfil);
    }

    private static ContextoUtilizador interno(UUID utilizadorId, PerfilInterno perfil) {
        return new ContextoUtilizador(utilizadorId, "AD_DGT", perfil, null, "DGT", null, null, null, null, null, null);
    }
}
