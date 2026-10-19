package dev.ahmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActivationRangeGuardTest {
    @Test
    void ownsOnlyAnAbsentValueAndRemovesItsSentinelWhenTheRouteStops() {
        FakeNpc npc = new FakeNpc("ahmet");
        ActivationRangeGuard guard = new ActivationRangeGuard();

        guard.reconcile(npc, true);
        guard.reconcile(npc, true);
        assertEquals(-1, npc.range);
        assertEquals(1, npc.writes);

        guard.reconcile(npc, false);
        assertNull(npc.range);
        assertEquals(1, npc.removes);
    }

    @Test
    void preservesExistingOperatorValuesAndLaterExternalReplacement() {
        FakeNpc persistent = new FakeNpc("persistent");
        persistent.range = 32;
        FakeNpc transientValue = new FakeNpc("transient");
        transientValue.range = 0;
        ActivationRangeGuard guard = new ActivationRangeGuard();

        guard.reconcile(persistent, true);
        guard.reconcile(persistent, false);
        guard.reconcile(transientValue, true);
        guard.reconcile(transientValue, false);

        assertEquals(32, persistent.range);
        assertEquals(0, transientValue.range);
        assertEquals(0, persistent.writes + transientValue.writes);
        assertEquals(0, persistent.removes + transientValue.removes);

        FakeNpc owned = new FakeNpc("owned");
        guard.reconcile(owned, true);
        owned.range = 24;
        guard.reconcile(owned, false);
        assertEquals(24, owned.range);
        assertEquals(0, owned.removes);
    }

    @Test
    void releasesOldNpcOnReplacementOrDespawnAndDoesNotAcquireWithoutARoute() {
        FakeNpc first = new FakeNpc("first");
        FakeNpc second = new FakeNpc("second");
        ActivationRangeGuard guard = new ActivationRangeGuard();

        guard.reconcile(first, false);
        assertNull(first.range);
        guard.reconcile(first, true);
        guard.reconcile(second, true);
        assertNull(first.range);
        assertEquals(-1, second.range);

        second.spawned = false;
        guard.reconcile(second, true);
        assertNull(second.range);
        assertFalse(guard.ownsOverride());
    }

    @Test
    void taskRoutesDoNotKeepActivationDuringRecoveryJournalWaitModelWaitOrRetry() {
        assertTrue(ActivationRangeGuard.taskRouteEligible(true,false,false,false,true,false));
        assertFalse(ActivationRangeGuard.taskRouteEligible(true,true,false,false,true,false));
        assertFalse(ActivationRangeGuard.taskRouteEligible(true,false,true,false,true,false));
        assertFalse(ActivationRangeGuard.taskRouteEligible(true,false,false,true,true,false));
        assertFalse(ActivationRangeGuard.taskRouteEligible(true,false,false,false,false,false));
        assertFalse(ActivationRangeGuard.taskRouteEligible(true,false,false,false,true,true));
        assertFalse(ActivationRangeGuard.taskRouteEligible(false,false,false,false,true,false));
    }

    private static final class FakeNpc implements ActivationRangeGuard.MetadataAccess {
        private final Object identity;
        private Integer range;
        private boolean spawned = true;
        private int writes;
        private int removes;

        private FakeNpc(Object identity) { this.identity = identity; }
        @Override public Object identity() { return identity; }
        @Override public boolean spawned() { return spawned; }
        @Override public boolean hasActivationRange() { return range != null; }
        @Override public Integer activationRange() { return range; }
        @Override public void setTransientActivationRange(int value) { writes++; range = value; }
        @Override public void removeActivationRange() { removes++; range = null; }
    }
}
