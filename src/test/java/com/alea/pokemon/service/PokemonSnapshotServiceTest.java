package com.alea.pokemon.service;

import static com.alea.pokemon.TestData.pokemon;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alea.pokemon.domain.PokemonSnapshot;
import com.alea.pokemon.loader.PokemonLoader;
import com.alea.pokemon.loader.SnapshotLoadException;

class PokemonSnapshotServiceTest {

    private final PokemonLoader loader = mock(PokemonLoader.class);
    private final PokemonSnapshotService service = new PokemonSnapshotService(loader);

    private static PokemonSnapshot snapshotOf(String name) {
        return new PokemonSnapshot(List.of(pokemon(1, name, 1, 1, 1)), Instant.now());
    }

    @Test
    void isNotReadyBeforeTheFirstLoad() {
        assertThatThrownBy(service::current).isInstanceOf(SnapshotNotReadyException.class);
    }

    @Test
    void refreshPublishesTheLoadedSnapshot() {
        PokemonSnapshot loaded = snapshotOf("first");
        when(loader.load()).thenReturn(loaded);

        service.refresh();

        assertThat(service.current()).isSameAs(loaded);
    }

    @Test
    void failedRefreshKeepsThePreviousSnapshot() {
        PokemonSnapshot first = snapshotOf("first");
        when(loader.load()).thenReturn(first).thenThrow(new SnapshotLoadException("boom", null));
        service.refresh();

        assertThatThrownBy(service::refresh).isInstanceOf(SnapshotLoadException.class);

        assertThat(service.current()).isSameAs(first);
    }

    @Test
    void failedFirstLoadLeavesTheServiceNotReady() {
        when(loader.load()).thenThrow(new SnapshotLoadException("boom", null));

        assertThatThrownBy(service::refresh).isInstanceOf(SnapshotLoadException.class);
        assertThatThrownBy(service::current).isInstanceOf(SnapshotNotReadyException.class);
    }

    @Test
    void successfulRefreshReplacesThePreviousSnapshot() {
        PokemonSnapshot first = snapshotOf("first");
        PokemonSnapshot second = snapshotOf("second");
        when(loader.load()).thenReturn(first).thenReturn(second);

        service.refresh();
        service.refresh();

        assertThat(service.current()).isSameAs(second);
    }
}
