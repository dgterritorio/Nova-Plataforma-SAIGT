package com.conkord.saigt.integracao.incm;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ServidorIncmTeste implements AutoCloseable {

    /** `consulta` é a query string tal como chegou, sem descodificar. */
    public record PedidoRecebido(String caminho, Headers cabecalhos, String corpo, String consulta, String metodo) {
    }

    private record Resposta(int status, String corpoJson, boolean reter, Map<String, String> cabecalhos) {

        Resposta(int status, String corpoJson, boolean reter) {
            this(status, corpoJson, reter, Map.of());
        }
    }

    private final HttpServer servidor;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Resposta> respostas = new ConcurrentHashMap<>();
    private final Map<String, Queue<Resposta>> sequencias = new ConcurrentHashMap<>();
    private final List<PedidoRecebido> pedidos = new CopyOnWriteArrayList<>();
    private final CountDownLatch fecho = new CountDownLatch(1);

    private ServidorIncmTeste() {
        try {
            servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/", this::tratar);
        servidor.setExecutor(executor);
        servidor.start();
    }

    public static ServidorIncmTeste iniciar() {
        return new ServidorIncmTeste();
    }

    public static String urlInacessivel() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "http://" + socket.getInetAddress().getHostAddress() + ":" + socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String url() {
        return "http://" + servidor.getAddress().getAddress().getHostAddress() + ":" + servidor.getAddress().getPort();
    }

    /** Um caminho sem resposta definida dá 404, como o WireMock. */
    public void responder(String caminho, int status, String corpoJson) {
        respostas.put(caminho, new Resposta(status, corpoJson, false));
    }

    /** Como {@link #responder}, com cabeçalhos de resposta; um `Content-Type` neles substitui o `application/json`. */
    public void responder(String caminho, int status, String corpo, Map<String, String> cabecalhos) {
        respostas.put(caminho, new Resposta(status, corpo, false, Map.copyOf(cabecalhos)));
    }

    /** Como {@link #responderUmaVez}, com cabeçalhos de resposta. */
    public void responderUmaVez(String caminho, int status, String corpo, Map<String, String> cabecalhos) {
        sequencias.computeIfAbsent(caminho, c -> new ConcurrentLinkedQueue<>())
                .add(new Resposta(status, corpo, false, Map.copyOf(cabecalhos)));
    }

    /** Responde `status` e `corpoJson` uma vez, antes da resposta de `responder` (as sequências acumulam por ordem). */
    public void responderUmaVez(String caminho, int status, String corpoJson) {
        sequencias.computeIfAbsent(caminho, c -> new ConcurrentLinkedQueue<>()).add(new Resposta(status, corpoJson, false));
    }

    /** Só responde quando o servidor fecha: provoca o read-timeout. */
    public void reterResposta(String caminho) {
        respostas.put(caminho, new Resposta(0, null, true));
    }

    public List<PedidoRecebido> pedidos() {
        return pedidos;
    }

    @Override
    public void close() {
        fecho.countDown();
        servidor.stop(0);
        executor.shutdownNow();
    }

    private void tratar(HttpExchange troca) throws IOException {
        try (troca) {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String caminho = troca.getRequestURI().getPath();
            pedidos.add(new PedidoRecebido(caminho, new Headers(troca.getRequestHeaders()), corpo,
                    troca.getRequestURI().getRawQuery(), troca.getRequestMethod()));
            Queue<Resposta> sequencia = sequencias.get(caminho);
            Resposta unica = sequencia == null ? null : sequencia.poll();
            Resposta resposta = unica != null ? unica : respostas.get(caminho);
            if (resposta == null) {
                troca.sendResponseHeaders(404, -1);
                return;
            }
            if (resposta.reter()) {
                fecho.await(30, TimeUnit.SECONDS);
                return;
            }
            resposta.cabecalhos().forEach((nome, valor) -> troca.getResponseHeaders().add(nome, valor));
            if (resposta.corpoJson() == null) {
                troca.sendResponseHeaders(resposta.status(), -1);
                return;
            }
            byte[] bytes = resposta.corpoJson().getBytes(StandardCharsets.UTF_8);
            if (!troca.getResponseHeaders().containsKey("Content-Type")) {
                troca.getResponseHeaders().add("Content-Type", "application/json");
            }
            troca.sendResponseHeaders(resposta.status(), bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
