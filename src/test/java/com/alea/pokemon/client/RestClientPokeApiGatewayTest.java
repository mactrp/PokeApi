package com.alea.pokemon.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.alea.pokemon.config.PokeApiProperties;
import com.alea.pokemon.domain.Pokemon;

/** Gateway tests against a mocked REST server; no network is used. */
class RestClientPokeApiGatewayTest {

    // Fake host: requests never leave the JVM.
    private static final String BASE = "http://pokeapi.test/api/v2";

    private MockRestServiceServer server;
    private RestClientPokeApiGateway gateway;

    /** Binds a mock server to the client builder and creates the gateway under test. */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        PokeApiProperties properties = new PokeApiProperties(BASE, 2000, 4, 1, Duration.ZERO,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        gateway = new RestClientPokeApiGateway(builder.build(), properties);
    }

    /** The listing call returns one detail URL per entry. */
    @Test
    void listsTheDetailUrlOfEveryEntry() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"count":2,"next":null,"previous":null,"results":[
                          {"name":"bulbasaur","url":"http://pokeapi.test/api/v2/pokemon/1/"},
                          {"name":"ivysaur","url":"http://pokeapi.test/api/v2/pokemon/2/"}]}
                        """, MediaType.APPLICATION_JSON));

        List<String> urls = gateway.listPokemonUrls();

        assertThat(urls).containsExactly(
                "http://pokeapi.test/api/v2/pokemon/1/",
                "http://pokeapi.test/api/v2/pokemon/2/");
        server.verify();
    }

    /** Fewer results than the reported count means the limit is too small, so it fails. */
    @Test
    void rejectsAnIncompleteListing() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000"))
                .andRespond(withSuccess("""
                        {"count":3,"results":[{"name":"a","url":"http://pokeapi.test/api/v2/pokemon/1/"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(gateway::listPokemonUrls)
                .isInstanceOf(PokeApiException.class)
                .hasMessageContaining("Incomplete catalogue");
    }

    /** An empty body for the listing is rejected. */
    @Test
    void rejectsAnEmptyListingResponse() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000")).andRespond(withNoContent());

        assertThatThrownBy(gateway::listPokemonUrls).isInstanceOf(PokeApiException.class);
    }

    /** Extra JSON fields are ignored and the known ones map to a Pokemon. */
    @Test
    void mapsTheDetailResponseIgnoringUnknownFields() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/6/"))
                .andRespond(withSuccess("""
                        {"id":6,"name":"charizard","height":17,"weight":905,"base_experience":267,
                         "is_default":true,"order":7,"abilities":[],
                         "species":{"name":"charizard","url":"http://pokeapi.test/api/v2/pokemon-species/6/"}}
                        """, MediaType.APPLICATION_JSON));

        Pokemon pokemon = gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/6/");

        assertThat(pokemon).isEqualTo(new Pokemon(6, "charizard", "charizard", 17, 905, 267, true));
    }

    /** A null base experience stays null and the species is kept for alternative forms. */
    @Test
    void mapsAMissingBaseExperienceToNullAndKeepsTheSpecies() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/10195/"))
                .andRespond(withSuccess("""
                        {"id":10195,"name":"eternatus-eternamax","height":1000,"weight":9500,"base_experience":null,
                         "is_default":false,"species":{"name":"eternatus"}}
                        """, MediaType.APPLICATION_JSON));

        Pokemon pokemon = gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/10195/");

        assertThat(pokemon.baseExperience()).isNull();
        assertThat(pokemon.defaultForm()).isFalse();
        assertThat(pokemon.species()).isEqualTo("eternatus");
    }

    /** A detail response without a species is unusable and rejected. */
    @Test
    void rejectsADetailWithoutSpecies() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/"))
                .andRespond(withSuccess("""
                        {"id":1,"name":"bulbasaur","height":7,"weight":69,"is_default":true}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(PokeApiException.class)
                .hasMessageContaining("Unexpected PokéAPI response");
    }

    /** 5xx errors propagate unchanged so the loader can retry them. */
    @Test
    void propagatesServerErrors() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(RestClientException.class);
    }

    /** A listing without a results array is rejected. */
    @Test
    void rejectsAListingWithoutResults() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000"))
                .andRespond(withSuccess("{\"count\":0}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(gateway::listPokemonUrls)
                .isInstanceOf(PokeApiException.class)
                .hasMessageContaining("empty catalogue");
    }

    /** An empty detail body is rejected. */
    @Test
    void rejectsAnEmptyDetailResponse() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withNoContent());

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(PokeApiException.class)
                .hasMessageContaining("Unexpected PokéAPI response");
    }

    /** A 404 is wrapped as a permanent failure, so it is not retried. */
    @Test
    void flagsAMissingDetailAsPermanent() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/99999/")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/99999/"))
                .isInstanceOf(PermanentPokeApiException.class)
                .hasMessageContaining("HTTP 404")
                .hasCauseInstanceOf(HttpClientErrorException.class);
    }

    /** A 4xx while listing is also permanent. */
    @Test
    void flagsAClientErrorWhileListingAsPermanent() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(gateway::listPokemonUrls)
                .isInstanceOf(PermanentPokeApiException.class)
                .hasMessageContaining("HTTP 400");
    }

    /** HTTP 429 is left as a retryable error. */
    @Test
    void rateLimitingStaysRetryable() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(HttpClientErrorException.class)
                .isNotInstanceOf(PermanentPokeApiException.class);
    }

    /** HTTP 408 is left as a retryable error. */
    @Test
    void requestTimeoutStaysRetryable() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(HttpClientErrorException.class)
                .isNotInstanceOf(PermanentPokeApiException.class);
    }
}
