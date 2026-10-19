package com.alea.pokemon.api;

import static com.alea.pokemon.TestData.form;
import static com.alea.pokemon.TestData.pokemon;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.alea.pokemon.domain.FormsMode;
import com.alea.pokemon.domain.Metric;
import com.alea.pokemon.service.RankingService;
import com.alea.pokemon.service.SnapshotNotReadyException;

class PokemonRankingControllerTest {

    private final RankingService rankingService = mock(RankingService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new FormsModeConverter());
        mockMvc = MockMvcBuilders.standaloneSetup(new PokemonRankingController(rankingService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setConversionService(conversionService)
                .build();
    }

    @Test
    void heaviestDefaultsToTheMainFormOfEachSpecies() throws Exception {
        when(rankingService.top(Metric.WEIGHT, FormsMode.DEFAULT_ONLY))
                .thenReturn(List.of(pokemon(6, "charizard", 17, 905, 267)));

        mockMvc.perform(get("/api/v1/pokemon/heaviest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("WEIGHT"))
                .andExpect(jsonPath("$.forms").value("DEFAULT_ONLY"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(6))
                .andExpect(jsonPath("$.results[0].name").value("charizard"))
                .andExpect(jsonPath("$.results[0].species").value("charizard"))
                .andExpect(jsonPath("$.results[0].defaultForm").value(true))
                .andExpect(jsonPath("$.results[0].heightMeters").value(1.7))
                .andExpect(jsonPath("$.results[0].weightKilograms").value(90.5))
                .andExpect(jsonPath("$.results[0].baseExperience").value(267));
    }

    @Test
    void highestHonoursTheFormsParameterCaseInsensitively() throws Exception {
        when(rankingService.top(Metric.HEIGHT, FormsMode.ALL))
                .thenReturn(List.of(form(10195, "eternatus-eternamax", "eternatus", 1000, 9500, null)));

        mockMvc.perform(get("/api/v1/pokemon/highest").param("forms", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("HEIGHT"))
                .andExpect(jsonPath("$.forms").value("ALL"))
                .andExpect(jsonPath("$.results[0].name").value("eternatus-eternamax"))
                .andExpect(jsonPath("$.results[0].defaultForm").value(false))
                .andExpect(jsonPath("$.results[0].heightMeters").value(100.0))
                .andExpect(jsonPath("$.results[0].baseExperience").doesNotExist());
    }

    @Test
    void mostExperiencedAcceptsBestPerSpecies() throws Exception {
        when(rankingService.top(Metric.BASE_EXPERIENCE, FormsMode.BEST_PER_SPECIES)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/pokemon/most-experienced").param("forms", "BEST_PER_SPECIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("BASE_EXPERIENCE"))
                .andExpect(jsonPath("$.forms").value("BEST_PER_SPECIES"))
                .andExpect(jsonPath("$.results.length()").value(0));

        verify(rankingService).top(Metric.BASE_EXPERIENCE, FormsMode.BEST_PER_SPECIES);
    }

    @Test
    void invalidFormsValueIsABadRequestListingTheAllowedValues() throws Exception {
        mockMvc.perform(get("/api/v1/pokemon/heaviest").param("forms", "everything"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"))
                .andExpect(jsonPath("$.detail").value(
                        "Invalid value 'everything' for parameter 'forms'. Allowed values: [DEFAULT_ONLY, ALL, BEST_PER_SPECIES]"));
    }

    @Test
    void answers503WhileTheCatalogueIsNotLoaded() throws Exception {
        when(rankingService.top(Metric.WEIGHT, FormsMode.DEFAULT_ONLY)).thenThrow(new SnapshotNotReadyException());

        mockMvc.perform(get("/api/v1/pokemon/heaviest"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service not ready"));
    }
}
