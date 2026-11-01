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

    // Shared client used to call the application under test.
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Points the app at the stub and disables retries so failures show up immediately. */
    @DynamicPropertySource
    static void pokeApiProperties(DynamicPropertyRegistry registry) {
        registry.add("pokeapi.base-url", POKE_API::baseUrl);
        registry.add("pokeapi.max-attempts", () -> 1);
        registry.add("pokeapi.initial-backoff", () -> "PT0S");
    }

    /** Shuts the stub server down after all tests. */
    @AfterAll
    static void stopStub() {
        POKE_API.stop();
    }

    // Random port assigned to the embedded server.
    @Value("${local.server.port}")
    private int port;

    /** Sends a GET request to the running application. */
    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Extracts the {@code name} of every ranked entry from the JSON body. */
    private static List<String> names(HttpResponse<String> response) {
        return JsonPath.read(response.body(), "$.results[*].name");
    }

    /** Without {@code forms}, only default forms compete, ordered by weight. */
    @Test
    void heaviestUsesTheMainFormOfEachSpeciesByDefault() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.metric")).isEqualTo("WEIGHT");
        assertThat(JsonPath.<String>read(response.body(), "$.forms")).isEqualTo("DEFAULT_ONLY");
        assertThat(names(response)).containsExactly("golf", "foxtrot", "echo", "delta", "charlie");
    }

    /** {@code forms=ALL} lets several forms of one species appear in the top 5. */
    @Test
    void heaviestWithAllFormsMayRepeatASpecies() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest?forms=ALL");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(names(response)).containsExactly("bravo-gmax", "bravo-mega", "alpha-mega", "golf", "foxtrot");
    }

    /** {@code best_per_species} (lower case) keeps one form per species. */
    @Test
    void highestWithBestPerSpeciesKeepsOneFormPerSpecies() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/highest?forms=best_per_species");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(names(response)).containsExactly("bravo-gmax", "alpha-mega", "golf", "foxtrot", "echo");
    }

    /** Entries with a null base experience never appear in that ranking. */
    @Test
    void mostExperiencedSkipsEntriesWithoutBaseExperience() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/most-experienced");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(names(response)).containsExactly("foxtrot", "echo", "delta", "charlie", "bravo");
    }

    /** The API reports metres and kilograms, not decimetres and hectograms. */
    @Test
    void responseCarriesConvertedUnits() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest");

        assertThat(JsonPath.<Double>read(response.body(), "$.results[0].weightKilograms")).isEqualTo(70.0);
        assertThat(JsonPath.<Double>read(response.body(), "$.results[0].heightMeters")).isEqualTo(7.0);
    }

    /** An unknown {@code forms} value returns 400 and lists the allowed values. */
    @Test
    void invalidFormsValueIsRejected() throws Exception {
        HttpResponse<String> response = get("/api/v1/pokemon/heaviest?forms=everything");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).contains("Allowed values");
    }

    /** Both Kubernetes health probes report UP once the data is loaded. */
    @Test
    void readinessAndLivenessProbesAreUp() throws Exception {
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
    }

    /** The external API is hit only while loading the snapshot, never once per request. */
    @Test
    void thePokeApiIsQueriedOnceAtStartupAndNeverPerRequest() throws Exception {
        get("/api/v1/pokemon/heaviest");
        get("/api/v1/pokemon/highest?forms=ALL");
        get("/api/v1/pokemon/most-experienced");

        assertThat(POKE_API.listRequests()).isEqualTo(1);
        assertThat(POKE_API.detailRequests()).isEqualTo(StubPokeApi.CATALOGUE.size());
    }
}
