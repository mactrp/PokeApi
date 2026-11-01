package com.alea.pokemon.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.alea.pokemon.loader.SnapshotLoadException;

/** Tests for the startup initializer and the scheduled refresh job. */
class SnapshotLifecycleTest {

    private final PokemonSnapshotService snapshots = mock(PokemonSnapshotService.class);

    /** The startup runner triggers one refresh. */
    @Test
    void initializerLoadsTheSnapshotAtStartup() {
        new SnapshotInitializer(snapshots).run(new DefaultApplicationArguments());

        verify(snapshots).refresh();
    }

    /** A failing first load propagates and aborts startup. */
    @Test
    void initializerFailsStartupWhenTheFirstLoadFails() {
        doThrow(new SnapshotLoadException("boom", null)).when(snapshots).refresh();

        assertThatThrownBy(() -> new SnapshotInitializer(snapshots).run(new DefaultApplicationArguments()))
                .isInstanceOf(SnapshotLoadException.class);
    }

    /** The scheduled job calls refresh on the service. */
    @Test
    void scheduledRefreshDelegatesToTheService() {
        new SnapshotRefreshJob(snapshots).refresh();

        verify(snapshots).refresh();
    }

    /** The job swallows failures instead of throwing. */
    @Test
    void scheduledRefreshSwallowsFailuresSoThePreviousSnapshotKeepsBeingServed() {
        doThrow(new SnapshotLoadException("boom", null)).when(snapshots).refresh();

        assertThatCode(() -> new SnapshotRefreshJob(snapshots).refresh()).doesNotThrowAnyException();
    }
}
