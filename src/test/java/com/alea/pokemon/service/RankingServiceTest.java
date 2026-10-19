package com.alea.pokemon.service;

import static com.alea.pokemon.TestData.form;
import static com.alea.pokemon.TestData.pokemon;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alea.pokemon.domain.FormsMode;
import com.alea.pokemon.domain.Metric;
import com.alea.pokemon.domain.Pokemon;
import com.alea.pokemon.domain.PokemonSnapshot;

class RankingServiceTest {

    private final PokemonSnapshotService snapshots = mock(PokemonSnapshotService.class);
    private final RankingService ranking = new RankingService(snapshots);

    private void givenCatalogue(Pokemon... pokemon) {
        when(snapshots.current()).thenReturn(new PokemonSnapshot(List.of(pokemon), Instant.now()));
    }

    private static List<String> names(List<Pokemon> pokemon) {
        return pokemon.stream().map(Pokemon::name).toList();
    }

    @Test
    void defaultOnlyIgnoresAlternativeForms() {
        givenCatalogue(
                pokemon(1, "a", 10, 100, 50),
                pokemon(2, "b", 20, 200, 60),
                form(10001, "a-mega", "a", 30, 500, 70));

        assertThat(names(ranking.top(Metric.WEIGHT, FormsMode.DEFAULT_ONLY))).containsExactly("b", "a");
    }

    @Test
    void allKeepsEveryFormEvenWhenTheyBelongToTheSameSpecies() {
        givenCatalogue(
                pokemon(1, "a", 10, 100, 50),
                pokemon(2, "b", 20, 200, 60),
                form(10001, "a-mega", "a", 30, 500, 70),
                form(10002, "a-gmax", "a", 40, 400, 80));

        assertThat(names(ranking.top(Metric.WEIGHT, FormsMode.ALL)))
                .containsExactly("a-mega", "a-gmax", "b", "a");
    }

    @Test
    void bestPerSpeciesKeepsOnlyTheBestFormOfEachSpecies() {
        givenCatalogue(
                pokemon(1, "a", 10, 100, 50),
                pokemon(2, "b", 20, 200, 60),
                form(10001, "a-mega", "a", 30, 500, 70),
                form(10002, "a-gmax", "a", 40, 400, 80));

        assertThat(names(ranking.top(Metric.WEIGHT, FormsMode.BEST_PER_SPECIES)))
                .containsExactly("a-mega", "b");
    }

    @Test
    void bestPerSpeciesDeduplicatesBeforeCuttingToFive() {
        givenCatalogue(
                pokemon(1, "a", 1, 10, 1),
                form(10001, "a-1", "a", 1, 90, 1),
                form(10002, "a-2", "a", 1, 80, 1),
                form(10003, "a-3", "a", 1, 70, 1),
                form(10004, "a-4", "a", 1, 60, 1),
                form(10005, "a-5", "a", 1, 50, 1),
                pokemon(2, "b", 1, 40, 1),
                pokemon(3, "c", 1, 30, 1));

        assertThat(names(ranking.top(Metric.WEIGHT, FormsMode.BEST_PER_SPECIES)))
                .containsExactly("a-1", "b", "c");
    }

    @Test
    void returnsAtMostFiveEntries() {
        givenCatalogue(
                pokemon(1, "p1", 1, 1, 1),
                pokemon(2, "p2", 1, 2, 1),
                pokemon(3, "p3", 1, 3, 1),
                pokemon(4, "p4", 1, 4, 1),
                pokemon(5, "p5", 1, 5, 1),
                pokemon(6, "p6", 1, 6, 1),
                pokemon(7, "p7", 1, 7, 1));

        assertThat(names(ranking.top(Metric.WEIGHT, FormsMode.DEFAULT_ONLY)))
                .containsExactly("p7", "p6", "p5", "p4", "p3");
    }

    @Test
    void tiesAreBrokenByLowestId() {
        givenCatalogue(
                pokemon(9, "nine", 1, 100, 1),
                pokemon(3, "three", 1, 100, 1),
                pokemon(5, "five", 1, 100, 1));

        assertThat(names(ranking.top(Metric.WEIGHT, FormsMode.DEFAULT_ONLY)))
                .containsExactly("three", "five", "nine");
    }

    @Test
    void bestPerSpeciesBreaksTiesInsideASpeciesByLowestId() {
        givenCatalogue(
                form(10010, "a-late", "a", 1, 100, 1),
                pokemon(4, "a", 1, 100, 1));

        assertThat(names(ranking.top(Metric.WEIGHT, FormsMode.BEST_PER_SPECIES))).containsExactly("a");
    }

    @Test
    void entriesWithoutBaseExperienceAreExcludedFromThatRanking() {
        givenCatalogue(
                pokemon(1, "has-value", 1, 1, 10),
                pokemon(2, "no-value", 1, 1, null));

        assertThat(names(ranking.top(Metric.BASE_EXPERIENCE, FormsMode.DEFAULT_ONLY))).containsExactly("has-value");
        assertThat(names(ranking.top(Metric.HEIGHT, FormsMode.DEFAULT_ONLY))).containsExactly("has-value", "no-value");
    }

    @Test
    void heightMetricRanksByHeight() {
        givenCatalogue(
                pokemon(1, "short", 5, 900, 1),
                pokemon(2, "tall", 50, 1, 1));

        assertThat(names(ranking.top(Metric.HEIGHT, FormsMode.DEFAULT_ONLY))).containsExactly("tall", "short");
    }

    @Test
    void emptyCatalogueYieldsEmptyRanking() {
        givenCatalogue();

        assertThat(ranking.top(Metric.WEIGHT, FormsMode.ALL)).isEmpty();
    }

    @Test
    void failsWhileTheCatalogueIsNotLoaded() {
        when(snapshots.current()).thenThrow(new SnapshotNotReadyException());

        assertThatThrownBy(() -> ranking.top(Metric.WEIGHT, FormsMode.ALL))
                .isInstanceOf(SnapshotNotReadyException.class);
    }
}
