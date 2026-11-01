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

/** Computes the top-N rankings over the current snapshot. */
@Service
public class RankingService {

    // Number of entries returned by every ranking.
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
        // Highest value first; ties are broken by the lowest id for deterministic output.
        Comparator<Pokemon> bestFirst = Comparator
                .comparingInt((Pokemon pokemon) -> metric.extract(pokemon))
                .reversed()
                .thenComparingInt(Pokemon::id);

        // Drop entries without a value for this metric (for example a missing base experience).
        Stream<Pokemon> candidates = snapshots.current().pokemon().stream()
                .filter(pokemon -> metric.extract(pokemon) != null);

        // The forms mode decides which entries compete.
        Stream<Pokemon> selected = switch (forms) {
            case DEFAULT_ONLY -> candidates.filter(Pokemon::defaultForm);
            case ALL -> candidates;
            // Group by species and keep the entry that sorts first under bestFirst.
            case BEST_PER_SPECIES -> candidates
                    .collect(Collectors.toMap(Pokemon::species, Function.identity(), BinaryOperator.minBy(bestFirst)))
                    .values()
                    .stream();
        };

        // Sort and keep only the top entries.
        return selected.sorted(bestFirst).limit(LIMIT).toList();
    }
}
