package com.alea.pokemon.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alea.pokemon.domain.FormsMode;
import com.alea.pokemon.domain.Metric;
import com.alea.pokemon.service.RankingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/pokemon")
@Tag(name = "Pokémon rankings")
public class PokemonRankingController {

    private static final String FORMS_DESCRIPTION =
            "forms: DEFAULT_ONLY (default) = main form of each species; ALL = every PokéAPI entry; "
                    + "BEST_PER_SPECIES = best form of each species";

    private final RankingService rankingService;

    public PokemonRankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/heaviest")
    @Operation(summary = "The 5 heaviest Pokémon", description = FORMS_DESCRIPTION)
    public RankingResponse heaviest(
            @RequestParam(name = "forms", defaultValue = "DEFAULT_ONLY") FormsMode forms) {
        return ranking(Metric.WEIGHT, forms);
    }

    @GetMapping("/highest")
    @Operation(summary = "The 5 highest Pokémon", description = FORMS_DESCRIPTION)
    public RankingResponse highest(
            @RequestParam(name = "forms", defaultValue = "DEFAULT_ONLY") FormsMode forms) {
        return ranking(Metric.HEIGHT, forms);
    }

    @GetMapping("/most-experienced")
    @Operation(summary = "The 5 Pokémon with the most base experience", description = FORMS_DESCRIPTION)
    public RankingResponse mostExperienced(
            @RequestParam(name = "forms", defaultValue = "DEFAULT_ONLY") FormsMode forms) {
        return ranking(Metric.BASE_EXPERIENCE, forms);
    }

    private RankingResponse ranking(Metric metric, FormsMode forms) {
        List<PokemonResponse> results = rankingService.top(metric, forms).stream()
                .map(PokemonResponse::from)
                .toList();
        return new RankingResponse(metric, forms, results);
    }
}
