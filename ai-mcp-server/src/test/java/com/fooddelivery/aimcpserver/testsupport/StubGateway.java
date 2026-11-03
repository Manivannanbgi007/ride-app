package com.fooddelivery.aimcpserver.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server that plays the API Gateway in MOCKED tests: routes answer canned JSON, every request is recorded
 * (method, path, query, headers, body) so tests can assert what was (not) called and with which identity.
 */
public final class StubGateway implements AutoCloseable {

    /** One recorded request. */
    public record Call(String method, String path, String query, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }
    }

    /** What a route answers. */
    public record Reply(int status, String body) {
    }

    private final HttpServer server;
    private final Map<String, Function<Call, Reply>> routes = new HashMap<>();
    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    public StubGateway() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::serve);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public StubGateway on(String method, String path, int status, String body) {
        return on(method, path, call -> new Reply(status, body));
    }

    public StubGateway on(String method, String path, Function<Call, Reply> handler) {
        routes.put(method + " " + path, handler);
        return this;
    }

    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    public long count(String method, String path) {
        return calls().stream().filter(c -> c.method().equals(method) && c.path().equals(path)).count();
    }

    public long countPathStartingWith(String prefix) {
        return calls().stream().filter(c -> c.path().startsWith(prefix)).count();
    }

    public void reset() {
        calls.clear();
        routes.clear();
    }

    private void serve(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Call call = new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), Map.copyOf(exchange.getRequestHeaders()), body);
        calls.add(call);
        Function<Call, Reply> handler = routes.get(call.method() + " " + call.path());
        Reply reply = handler == null
                ? new Reply(404, "{\"title\":\"Not Found\",\"detail\":\"no stub route\"}") : handler.apply(call);
        byte[] bytes = reply.body() == null ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), reply.status() == 204 || bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0 && reply.status() != 204) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
