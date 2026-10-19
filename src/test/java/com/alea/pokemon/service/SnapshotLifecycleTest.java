package com.alea.pokemon.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.alea.pokemon.loader.SnapshotLoadException;

class SnapshotLifecycleTest {

    private final PokemonSnapshotService snapshots = mock(PokemonSnapshotService.class);

    @Test
    void initializerLoadsTheSnapshotAtStartup() {
        new SnapshotInitializer(snapshots).run(new DefaultApplicationArguments());

        verify(snapshots).refresh();
    }

    @Test
    void initializerFailsStartupWhenTheFirstLoadFails() {
        doThrow(new SnapshotLoadException("boom", null)).when(snapshots).refresh();

        assertThatThrownBy(() -> new SnapshotInitializer(snapshots).run(new DefaultApplicationArguments()))
                .isInstanceOf(SnapshotLoadException.class);
    }

    @Test
    void scheduledRefreshDelegatesToTheService() {
        new SnapshotRefreshJob(snapshots).refresh();

        verify(snapshots).refresh();
    }

    @Test
    void scheduledRefreshSwallowsFailuresSoThePreviousSnapshotKeepsBeingServed() {
        doThrow(new SnapshotLoadException("boom", null)).when(snapshots).refresh();

        assertThatCode(() -> new SnapshotRefreshJob(snapshots).refresh()).doesNotThrowAnyException();
    }
}
