package com.alea.pokemon.api;

import java.util.List;

import com.alea.pokemon.domain.FormsMode;
import com.alea.pokemon.domain.Metric;

/** A ranking together with the parameters it was computed with. */
public record RankingResponse(Metric metric, FormsMode forms, List<PokemonResponse> results) {
}
