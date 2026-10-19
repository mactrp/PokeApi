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

class RestClientPokeApiGatewayTest {

    private static final String BASE = "http://pokeapi.test/api/v2";

    private MockRestServiceServer server;
    private RestClientPokeApiGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        PokeApiProperties properties = new PokeApiProperties(BASE, 2000, 4, 1, Duration.ZERO,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        gateway = new RestClientPokeApiGateway(builder.build(), properties);
    }

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

    @Test
    void rejectsAnEmptyListingResponse() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000")).andRespond(withNoContent());

        assertThatThrownBy(gateway::listPokemonUrls).isInstanceOf(PokeApiException.class);
    }

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

    @Test
    void propagatesServerErrors() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void rejectsAListingWithoutResults() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000"))
                .andRespond(withSuccess("{\"count\":0}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(gateway::listPokemonUrls)
                .isInstanceOf(PokeApiException.class)
                .hasMessageContaining("empty catalogue");
    }

    @Test
    void rejectsAnEmptyDetailResponse() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withNoContent());

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(PokeApiException.class)
                .hasMessageContaining("Unexpected PokéAPI response");
    }

    @Test
    void flagsAMissingDetailAsPermanent() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/99999/")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/99999/"))
                .isInstanceOf(PermanentPokeApiException.class)
                .hasMessageContaining("HTTP 404")
                .hasCauseInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void flagsAClientErrorWhileListingAsPermanent() {
        server.expect(requestTo(BASE + "/pokemon?limit=2000")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(gateway::listPokemonUrls)
                .isInstanceOf(PermanentPokeApiException.class)
                .hasMessageContaining("HTTP 400");
    }

    @Test
    void rateLimitingStaysRetryable() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(HttpClientErrorException.class)
                .isNotInstanceOf(PermanentPokeApiException.class);
    }

    @Test
    void requestTimeoutStaysRetryable() {
        server.expect(requestTo("http://pokeapi.test/api/v2/pokemon/1/")).andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));

        assertThatThrownBy(() -> gateway.fetchPokemon("http://pokeapi.test/api/v2/pokemon/1/"))
                .isInstanceOf(HttpClientErrorException.class)
                .isNotInstanceOf(PermanentPokeApiException.class);
    }
}
