package com.alea.pokemon.service;

import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.alea.pokemon.domain.FormsMode;
import com.alea.pokemon.domain.Metric;
import com.alea.pokemon.domain.Pokemon;

@Service
public class RankingService {

    static final int LIMIT = 5;

    private final PokemonSnapshotService snapshots;

    public RankingService(PokemonSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    /**
     * Top {@value #LIMIT} entries by {@code metric}, highest value first. Entries without a value
     * for the metric are excluded. Ties are broken by lowest id so results are deterministic.
     */
    public List<Pokemon> top(Metric metric, FormsMode forms) {
        Comparator<Pokemon> bestFirst = Comparator
                .comparingInt((Pokemon pokemon) -> metric.extract(pokemon))
                .reversed()
                .thenComparingInt(Pokemon::id);

        Stream<Pokemon> candidates = snapshots.current().pokemon().stream()
                .filter(pokemon -> metric.extract(pokemon) != null);

        Stream<Pokemon> selected = switch (forms) {
            case DEFAULT_ONLY -> candidates.filter(Pokemon::defaultForm);
            case ALL -> candidates;
            case BEST_PER_SPECIES -> candidates
                    .collect(Collectors.toMap(Pokemon::species, Function.identity(), BinaryOperator.minBy(bestFirst)))
                    .values()
                    .stream();
        };

        return selected.sorted(bestFirst).limit(LIMIT).toList();
    }
}
