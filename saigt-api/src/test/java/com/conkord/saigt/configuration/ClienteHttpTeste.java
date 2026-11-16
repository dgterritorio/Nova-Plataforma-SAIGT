package com.conkord.saigt.configuration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HttpClient do JDK em vez do TestRestTemplate: o HttpURLConnection deste falha a ler respostas 401 a um POST com
 * corpo, que é o caso do endpoint de tokens.
 */
public final class ClienteHttpTeste {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String base;

    public ClienteHttpTeste(int porta) {
        this.base = "http://localhost:" + porta;
    }

    public HttpResponse<String> pedirToken(String query, String clientId, String clientSecret, String corpo) {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + "/oauth2/token" + query))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        if (clientId != null) {
            pedido.header("Authorization", basic(clientId, clientSecret));
        }
        return enviar(pedido.build());
    }

    public HttpResponse<String> get(String caminho, String bearer) {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + caminho)).GET();
        if (bearer != null) {
            pedido.header("Authorization", "Bearer " + bearer);
        }
        return enviar(pedido.build());
    }

    HttpResponse<String> postComBasic(String caminho, String clientId, String clientSecret, String corpo) {
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", basic(clientId, clientSecret))
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build());
    }

    HttpResponse<String> postForm(String caminho, String corpo) {
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build());
    }

    HttpResponse<String> patchJson(String caminho, String bearer, String corpo) {
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(corpo))
                .build());
    }

    private static String basic(String clientId, String clientSecret) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> enviar(HttpRequest pedido) {
        try {
            return httpClient.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
