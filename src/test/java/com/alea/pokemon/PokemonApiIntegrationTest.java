package com.alea.pokemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.jayway.jsonpath.JsonPath;

/**
 * Starts the whole application against an in-process PokéAPI stub and calls it over HTTP.
 * Nothing here touches the real PokéAPI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PokemonApiIntegrationTest {

    // Started when the class loads, i.e. before Spring builds the context that loads the catalogue.
    private static final StubPokeApi POKE_API = StubPokeApi.start();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void pokeApiProperties(DynamicPropertyRegistry registry) {
        registry.add("pokeapi.base-url", POKE_API::baseUrl);
        registry.add("pokeapi.max-attempts", () -> 1);
        registry.add("pokeapi.initial-backoff", () -> "PT0S");
    }

    @AfterAll
    static void stopStub() {
        POKE_API.stop();
    }

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static List<String> names(HttpResponse<String> response) {
        return JsonPath.read(response.body(), "$.results[*].name");
    }

    @Test
    void heaviestUsesTheMainFormOfEachSpeciesByDefault() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.metric")).isEqualTo("WEIGHT");
        assertThat(JsonPath.<String>read(response.body(), "$.forms")).isEqualTo("DEFAULT_ONLY");
        assertThat(names(response)).containsExactly("golf", "foxtrot", "echo", "delta", "charlie");
    }

    @Test
    void heaviestWithAllFormsMayRepeatASpecies() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest?forms=ALL");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(names(response)).containsExactly("bravo-gmax", "bravo-mega", "alpha-mega", "golf", "foxtrot");
    }

    @Test
    void highestWithBestPerSpeciesKeepsOneFormPerSpecies() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/highest?forms=best_per_species");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(names(response)).containsExactly("bravo-gmax", "alpha-mega", "golf", "foxtrot", "echo");
    }

    @Test
    void mostExperiencedSkipsEntriesWithoutBaseExperience() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/most-experienced");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(names(response)).containsExactly("foxtrot", "echo", "delta", "charlie", "bravo");
    }

    @Test
    void responseCarriesConvertedUnits() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest");

        assertThat(JsonPath.<Double>read(response.body(), "$.results[0].weightKilograms")).isEqualTo(70.0);
        assertThat(JsonPath.<Double>read(response.body(), "$.results[0].heightMeters")).isEqualTo(7.0);
    }

    @Test
    void invalidFormsValueIsRejected() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest?forms=everything");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).contains("Allowed values");
    }

    @Test
    void readinessAndLivenessProbesAreUp() throws Exception {
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
    }

    @Test
    void thePokeApiIsQueriedOnceAtStartupAndNeverPerRequest() throws Exception {
        get("/api/v1/pokemon/heaviest");
        get("/api/v1/pokemon/highest?forms=ALL");
        get("/api/v1/pokemon/most-experienced");

        assertThat(POKE_API.listRequests()).isEqualTo(1);
        assertThat(POKE_API.detailRequests()).isEqualTo(StubPokeApi.CATALOGUE.size());
    }
}
