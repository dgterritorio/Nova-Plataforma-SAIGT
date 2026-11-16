package com.conkord.saigt.controller.notificacao;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificacaoEstadoConcorrenciaIntegrationTest extends PostgresTestcontainersBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Test
    void doisMasterADescartarOMesmoAlertaAoMesmoTempoUmRecebe200EOOutro409() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Utilizador master1 = master("m1" + sufixo);
        Utilizador master2 = master("m2" + sufixo);
        Notificacao alerta = new Notificacao(TipoNotificacao.ALERTA, "Serviço indisponível", "Falha", null);
        alerta.setPerfilDestino(PerfilInterno.MASTER);
        Notificacao gravado = notificacaoRepository.saveAndFlush(alerta);
        CyclicBarrier partida = new CyclicBarrier(2);

        List<CompletableFuture<Integer>> pedidos = new ArrayList<>();
        for (Utilizador master : List.of(master1, master2)) {
            pedidos.add(CompletableFuture.supplyAsync(() -> {
                try {
                    partida.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(patch("/api/v1/notificacoes/" + gravado.getId() + "/remover")
                                    .with(jwt().jwt(b -> b.claim(JwtClaimNames.SUB, master.getId().toString())
                                            .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"))))
                            .andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }

        List<Integer> estados = new ArrayList<>();
        for (CompletableFuture<Integer> pedido : pedidos) {
            estados.add(pedido.get(30, TimeUnit.SECONDS));
        }
        assertThat(estados).containsExactlyInAnyOrder(200, 409);
        assertThat(notificacaoRepository.findById(gravado.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoNotificacao.REMOVIDA);
    }

    private Utilizador master(String prefixo) {
        Utilizador utilizador = new Utilizador("Master " + prefixo, prefixo + "@dgt.pt");
        utilizador.setPerfilInterno(PerfilInterno.MASTER);
        return utilizadorRepository.saveAndFlush(utilizador);
    }
}
