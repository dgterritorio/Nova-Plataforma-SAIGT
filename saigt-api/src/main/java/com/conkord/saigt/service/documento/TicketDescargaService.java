package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ticket sem estado: `{conteúdo}.{assinatura}`, ambos em base64url, assinado com HMAC-SHA256. O conteúdo é
 * `idChave|tipo|utilizador|documento|expiração`; qualquer instância com as mesmas chaves valida o ticket de outra.
 */
@Service
public class TicketDescargaService {

    private static final String ALGORITMO = "HmacSHA256";
    private static final int TAMANHO_MINIMO_SEGREDO = 32;
    private static final String SEPARADOR = "|";
    private static final Pattern PADRAO_SEPARADOR = Pattern.compile(Pattern.quote(SEPARADOR));

    private final Map<String, byte[]> chaves;
    private final String chaveAtiva;
    private final Duration validade;
    private final Clock relogio;

    public TicketDescargaService(DescargaProperties propriedades, Clock relogio) {
        this.chaves = lerChaves(propriedades);
        this.chaveAtiva = propriedades.chaveAtiva();
        this.validade = propriedades.ticketValidade();
        this.relogio = relogio;
        if (!chaves.containsKey(chaveAtiva)) {
            throw new IllegalStateException("A chave ativa de descarga não está entre as chaves configuradas.");
        }
    }

    public String emitir(TipoTicketDescarga tipo, UUID utilizadorId, UUID documentoId) {
        long expiracao = relogio.instant().plus(validade).getEpochSecond();
        String conteudo = String.join(SEPARADOR, chaveAtiva, String.valueOf(tipo.codigo()),
                utilizadorId.toString(), documentoId.toString(), String.valueOf(expiracao));
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        return codificar(bytes) + "." + codificar(assinar(chaves.get(chaveAtiva), bytes));
    }

    /** Vazio para qualquer ticket inválido, adulterado, expirado ou de outra chave: quem chama não os distingue. */
    public Optional<TicketDescarga> validar(String ticket) {
        try {
            int ponto = ticket == null ? -1 : ticket.indexOf('.');
            if (ponto < 1 || ponto != ticket.lastIndexOf('.')) {
                return Optional.empty();
            }
            byte[] conteudo = Base64.getUrlDecoder().decode(ticket.substring(0, ponto));
            byte[] assinatura = Base64.getUrlDecoder().decode(ticket.substring(ponto + 1));
            String[] partes = PADRAO_SEPARADOR.split(new String(conteudo, StandardCharsets.UTF_8), -1);
            if (partes.length != 5 || partes[1].length() != 1) {
                return Optional.empty();
            }
            byte[] chave = chaves.get(partes[0]);
            if (chave == null || !MessageDigest.isEqual(assinar(chave, conteudo), assinatura)) {
                return Optional.empty();
            }
            Instant expiraEm = Instant.ofEpochSecond(Long.parseLong(partes[4]));
            if (!relogio.instant().isBefore(expiraEm)) {
                return Optional.empty();
            }
            return Optional.of(new TicketDescarga(TipoTicketDescarga.de(partes[1].charAt(0)),
                    UUID.fromString(partes[2]), UUID.fromString(partes[3]), expiraEm));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static byte[] assinar(byte[] chave, byte[] conteudo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(chave, ALGORITMO));
            return mac.doFinal(conteudo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível na JVM.", e);
        }
    }

    private static String codificar(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Map<String, byte[]> lerChaves(DescargaProperties propriedades) {
        Map<String, byte[]> resultado = new HashMap<>();
        for (String entrada : propriedades.chaves()) {
            int separador = entrada.indexOf(':');
            if (separador < 1 || separador == entrada.length() - 1) {
                throw new IllegalStateException("As chaves de descarga têm o formato id:segredo.");
            }
            byte[] segredo = entrada.substring(separador + 1).getBytes(StandardCharsets.UTF_8);
            if (segredo.length < TAMANHO_MINIMO_SEGREDO) {
                throw new IllegalStateException(
                        "O segredo de cada chave de descarga tem de ter pelo menos " + TAMANHO_MINIMO_SEGREDO + " bytes.");
            }
            resultado.put(entrada.substring(0, separador), segredo);
        }
        return resultado;
    }
}
