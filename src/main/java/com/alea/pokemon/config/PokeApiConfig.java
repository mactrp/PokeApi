package com.alea.pokemon.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Spring configuration that builds the HTTP client used to call PokéAPI. */
@Configuration(proxyBeanMethods = false)
public class PokeApiConfig {

    /**
     * Client backed by the JDK {@link HttpClient}, which negotiates HTTP/2 and therefore
     * multiplexes the ~1,350 detail requests over a few connections.
     */
    @Bean
    RestClient pokeApiRestClient(PokeApiProperties properties) {
        // The connect timeout lives on the JDK client...
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // ...while the per-request read timeout lives on the request factory.
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
