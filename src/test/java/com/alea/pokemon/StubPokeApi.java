package com.alea.pokemon;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal in-process stand-in for PokéAPI (JDK {@link HttpServer}, no extra dependencies).
 *
 * <p>Catalogue (heights in dm, weights in hg), designed so every ranking has a known answer:
 * <pre>
 * id     name         species  h    w     baseExp  default
 * 1      alpha        alpha    10   100   50       yes
 * 2      bravo        bravo    20   200   60       yes
 * 3      charlie      charlie  30   300   70       yes
 * 4      delta        delta    40   400   80       yes
 * 5      echo         echo     50   500   90       yes
 * 6      foxtrot      foxtrot  60   600   100      yes
 * 7      golf         golf     70   700   null     yes
 * 10001  alpha-mega   alpha    100  1000  200      no
 * 10002  bravo-gmax   bravo    200  2000  300      no
 * 10003  bravo-mega   bravo    150  1500  250      no
 * </pre>
 */
final class StubPokeApi {

    record Entry(int id, String name, String species, int height, int weight, Integer baseExperience, boolean isDefault) {
    }

    static final List<Entry> CATALOGUE = List.of(
            new Entry(1, "alpha", "alpha", 10, 100, 50, true),
            new Entry(2, "bravo", "bravo", 20, 200, 60, true),
            new Entry(3, "charlie", "charlie", 30, 300, 70, true),
            new Entry(4, "delta", "delta", 40, 400, 80, true),
            new Entry(5, "echo", "echo", 50, 500, 90, true),
            new Entry(6, "foxtrot", "foxtrot", 60, 600, 100, true),
            new Entry(7, "golf", "golf", 70, 700, null, true),
            new Entry(10001, "alpha-mega", "alpha", 100, 1000, 200, false),
            new Entry(10002, "bravo-gmax", "bravo", 200, 2000, 300, false),
            new Entry(10003, "bravo-mega", "bravo", 150, 1500, 250, false));

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger listRequests = new AtomicInteger();
    private final AtomicInteger detailRequests = new AtomicInteger();

    private StubPokeApi(HttpServer server) {
        this.server = server;
    }

    static StubPokeApi start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            StubPokeApi stub = new StubPokeApi(server);
            server.createContext("/api/v2/pokemon", stub::handle);
            server.setExecutor(stub.executor);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the PokéAPI stub", e);
        }
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/api/v2";
    }

    int listRequests() {
        return listRequests.get();
    }

    int detailRequests() {
        return detailRequests.get();
    }

    void stop() {
        server.stop(0);
        executor.close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/v2/pokemon")) {
            listRequests.incrementAndGet();
            respond(exchange, 200, listJson());
            return;
        }
        detailRequests.incrementAndGet();
        String id = path.substring("/api/v2/pokemon/".length()).replace("/", "");
        CATALOGUE.stream()
                .filter(entry -> String.valueOf(entry.id()).equals(id))
                .findFirst()
                .ifPresentOrElse(
                        entry -> send(exchange, 200, detailJson(entry)),
                        () -> send(exchange, 404, "{\"detail\":\"Not found.\"}"));
    }

    private String listJson() {
        String results = CATALOGUE.stream()
                .map(entry -> "{\"name\":\"%s\",\"url\":\"%s/pokemon/%d/\"}".formatted(entry.name(), baseUrl(), entry.id()))
                .collect(Collectors.joining(","));
        return "{\"count\":%d,\"next\":null,\"previous\":null,\"results\":[%s]}".formatted(CATALOGUE.size(), results);
    }

    private static String detailJson(Entry entry) {
        return ("{\"id\":%d,\"name\":\"%s\",\"height\":%d,\"weight\":%d,\"base_experience\":%s,"
                + "\"is_default\":%s,\"species\":{\"name\":\"%s\"}}")
                .formatted(entry.id(), entry.name(), entry.height(), entry.weight(),
                        entry.baseExperience() == null ? "null" : entry.baseExperience().toString(),
                        entry.isDefault(), entry.species());
    }

    private static void send(HttpExchange exchange, int status, String body) {
        try {
            respond(exchange, status, body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
