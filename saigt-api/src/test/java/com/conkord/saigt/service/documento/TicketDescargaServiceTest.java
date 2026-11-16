package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketDescargaServiceTest {

    private static final String SEGREDO_A = "segredo-a-com-mais-de-trinta-e-dois-bytes";
    private static final String SEGREDO_B = "segredo-b-com-mais-de-trinta-e-dois-bytes";
    private static final Instant AGORA = Instant.parse("2026-11-06T12:00:00Z");
    private static final UUID UTILIZADOR = UUID.randomUUID();
    private static final UUID DOCUMENTO = UUID.randomUUID();

    private static DescargaProperties propriedades(List<String> chaves, String ativa) {
        return new DescargaProperties(chaves, ativa, Duration.ofSeconds(60), 50, 3, Period.ofMonths(12),
                Duration.ofSeconds(5), 5);
    }

    private static TicketDescargaService servico(List<String> chaves, String ativa, Instant instante) {
        return new TicketDescargaService(propriedades(chaves, ativa), Clock.fixed(instante, ZoneOffset.UTC));
    }

    @Test
    void deveValidarUmTicketRecemEmitido() {
        TicketDescargaService servico = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA);

        TicketDescarga ticket = servico.validar(
                servico.emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, DOCUMENTO)).orElseThrow();

        assertThat(ticket.utilizadorId()).isEqualTo(UTILIZADOR);
        assertThat(ticket.documentoId()).isEqualTo(DOCUMENTO);
        assertThat(ticket.tipo()).isEqualTo(TipoTicketDescarga.DOCUMENTO_PROCESSO);
        assertThat(ticket.expiraEm()).isEqualTo(AGORA.plusSeconds(60));
    }

    @Test
    void deveValidarEmOutraInstanciaComAsMesmasChaves() {
        TicketDescargaService primeira = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA);
        TicketDescargaService segunda = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA.plusSeconds(30));

        String ticket = primeira.emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, DOCUMENTO);

        assertThat(segunda.validar(ticket)).isPresent();
    }

    @Test
    void deveRecusarTicketExpiradoNoInstanteDeExpiracao() {
        String ticket = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA)
                .emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, DOCUMENTO);

        assertThat(servico(List.of("k1:" + SEGREDO_A), "k1", AGORA.plusSeconds(59)).validar(ticket)).isPresent();
        assertThat(servico(List.of("k1:" + SEGREDO_A), "k1", AGORA.plusSeconds(60)).validar(ticket)).isEmpty();
    }

    @Test
    void deveRecusarTicketAdulteradoOuDeOutraChave() {
        TicketDescargaService servico = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA);
        String ticket = servico.emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, DOCUMENTO);
        String[] partes = ticket.split("\\.");
        String assinaturaAdulterada = partes[1].substring(0, partes[1].length() - 1)
                + (partes[1].endsWith("A") ? "B" : "A");
        String outroDocumento = servico.emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, UUID.randomUUID())
                .split("\\.")[0];

        assertThat(servico.validar(partes[0] + "." + assinaturaAdulterada)).isEmpty();
        assertThat(servico.validar(outroDocumento + "." + partes[1])).isEmpty();
        assertThat(servico(List.of("k1:" + SEGREDO_B), "k1", AGORA).validar(ticket)).isEmpty();
        assertThat(servico(List.of("k2:" + SEGREDO_A), "k2", AGORA).validar(ticket)).isEmpty();
    }

    @Test
    void deveRecusarLixoSemLancarExcecao() {
        TicketDescargaService servico = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA);

        for (String lixo : new String[] {null, "", ".", "a", "a.b", "a.b.c", "!!!.???", "YQ.YQ"}) {
            assertThat(servico.validar(lixo)).as("ticket %s", lixo).isEmpty();
        }
    }

    @Test
    void deveManterValidosOsTicketsDaChaveAnteriorAposARotacao() {
        String antigo = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA)
                .emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, DOCUMENTO);
        TicketDescargaService rodado = servico(List.of("k1:" + SEGREDO_A, "k2:" + SEGREDO_B), "k2", AGORA);

        assertThat(rodado.validar(antigo)).isPresent();
        String novo = rodado.emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, DOCUMENTO);
        assertThat(servico(List.of("k1:" + SEGREDO_A), "k1", AGORA).validar(novo)).isEmpty();
    }

    @Test
    void deveRecusarConfiguracaoInvalida() {
        assertThatThrownBy(() -> servico(List.of("k1:curto"), "k1", AGORA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> servico(List.of("semseparador"), "k1", AGORA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> servico(List.of("k1:" + SEGREDO_A), "k9", AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ticketNaoDeveConterOSegredo() {
        String ticket = servico(List.of("k1:" + SEGREDO_A), "k1", AGORA)
                .emitir(TipoTicketDescarga.DOCUMENTO_PROCESSO, UTILIZADOR, DOCUMENTO);

        assertThat(ticket).doesNotContain(SEGREDO_A).matches("[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+");
    }
}
