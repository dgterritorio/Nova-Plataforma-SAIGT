package com.conkord.saigt.controller.notificacao;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Em PostgreSQL, porque o bloqueio de escrita só se comprova com transações concorrentes reais. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificacaoMensagemConcorrenciaIntegrationTest extends PostgresTestcontainersBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private NotificacaoMensagemRepository mensagemRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void doisMasterAoMesmoTempoNoMesmoPedidoDeApoioUmResolveEOOutroRecebe409() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor", sufixo + "@cm-teste.pt", "INCM"));
        Utilizador master1 = master("m1" + sufixo);
        Utilizador master2 = master("m2" + sufixo);
        Notificacao pedido = pedidoDeApoio(autor);
        CyclicBarrier partida = new CyclicBarrier(2);

        List<CompletableFuture<Integer>> pedidos = new ArrayList<>();
        for (Utilizador master : List.of(master1, master2)) {
            pedidos.add(CompletableFuture.supplyAsync(() -> {
                try {
                    partida.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/v1/notificacoes/" + pedido.getId() + "/mensagens")
                                    .with(jwt().jwt(b -> b.claim(JwtClaimNames.SUB, master.getId().toString())
                                            .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER")))
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Resposta\"}"))
                            .andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }

        List<Integer> estados = new ArrayList<>();
        for (CompletableFuture<Integer> pedidoHttp : pedidos) {
            estados.add(pedidoHttp.get(30, TimeUnit.SECONDS));
        }
        assertThat(estados).containsExactlyInAnyOrder(201, 409);
        assertThat(mensagemRepository.findByNotificacao_IdOrderByCriadoEmAsc(pedido.getId())).hasSize(1);
        assertThat(notificacaoRepository.findById(pedido.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoNotificacao.RESOLVIDA);
    }

    @Test
    void oAutorReabreDuasVezesAoMesmoTempoUmPedidoReabreEOOutroRecebe409() throws Exception {
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor", UUID.randomUUID() + "@cm-teste.pt", "INCM"));
        Notificacao pedido = pedidoDeApoio(autor);
        pedido.resolver(Instant.now());
        notificacaoRepository.saveAndFlush(pedido);
        CyclicBarrier partida = new CyclicBarrier(2);

        List<CompletableFuture<Integer>> pedidos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            pedidos.add(CompletableFuture.supplyAsync(() -> {
                try {
                    partida.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(patch("/api/v1/notificacoes/" + pedido.getId() + "/reabrir")
                                    .with(jwt().jwt(b -> b.claim(JwtClaimNames.SUB, autor.getId().toString())
                                            .claim("origem", "INCM"))))
                            .andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }

        List<Integer> estados = new ArrayList<>();
        for (CompletableFuture<Integer> pedidoHttp : pedidos) {
            estados.add(pedidoHttp.get(30, TimeUnit.SECONDS));
        }
        assertThat(estados).containsExactlyInAnyOrder(200, 409);
        Notificacao reaberto = notificacaoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(reaberto.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(reaberto.getResolvidoEm()).isNull();
    }

    @Test
    void findComBloqueioByIdBloqueiaALinhaAteAoFimDaTransacao() throws Exception {
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor", UUID.randomUUID() + "@cm-teste.pt", "INCM"));
        UUID id = pedidoDeApoio(autor).getId();
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        CountDownLatch primeiraComOBloqueio = new CountDownLatch(1);
        AtomicBoolean primeiraTerminou = new AtomicBoolean(false);

        CompletableFuture<Void> primeira = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(estado -> {
            assertThat(notificacaoRepository.findComBloqueioById(id)).isPresent();
            primeiraComOBloqueio.countDown();
            dormir(500);
            primeiraTerminou.set(true);
        }));
        assertThat(primeiraComOBloqueio.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Boolean> segunda = CompletableFuture.supplyAsync(() -> transacao.execute(estado -> {
            notificacaoRepository.findComBloqueioById(id).orElseThrow();
            return primeiraTerminou.get();
        }));

        primeira.get(10, TimeUnit.SECONDS);
        assertThat(segunda.get(10, TimeUnit.SECONDS)).as("a segunda só lê a linha depois do commit da primeira")
                .isTrue();
        Optional<Notificacao> inexistente = transacao.execute(estado ->
                notificacaoRepository.findComBloqueioById(UUID.randomUUID()));
        assertThat(inexistente).isEmpty();
    }

    private Utilizador master(String prefixo) {
        Utilizador utilizador = new Utilizador("Master " + prefixo, prefixo + "@dgt.pt");
        utilizador.setPerfilInterno(PerfilInterno.MASTER);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    private Notificacao pedidoDeApoio(Utilizador autor) {
        Notificacao pedido = new Notificacao(TipoNotificacao.PEDIDO_APOIO, "Pedido", "Preciso de ajuda", autor);
        pedido.setPerfilDestino(PerfilInterno.MASTER);
        return notificacaoRepository.saveAndFlush(pedido);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
