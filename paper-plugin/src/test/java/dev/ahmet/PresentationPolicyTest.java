package dev.ahmet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PresentationPolicyTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final AtomicLong now = new AtomicLong(10_000L);
    private final PresentationPolicy policy = new PresentationPolicy(now::get);

    @Test
    void lookSelectsNearestVisibleOnlineSameWorldPlayerWithUuidTieBreakAndTwentyTickCadence() {
        UUID lower = uuid(2), higher = uuid(3);
        var candidates = List.of(
                player(higher, WORLD, 4, true, true),
                player(uuid(4), uuid(9), 1, true, true),
                player(uuid(5), WORLD, 1, false, true),
                player(uuid(6), WORLD, 1, true, false),
                player(lower, WORLD, 4, true, true),
                player(uuid(7), WORLD, 37, true, true));

        assertTrue(policy.reserveLookScan(100));
        assertEquals(lower, policy.lookTarget(candidates, WORLD).orElseThrow());
        assertFalse(policy.reserveLookScan(119));
        assertTrue(policy.reserveLookScan(120));
        assertEquals(lower, policy.lookTarget(candidates, WORLD).orElseThrow());
    }

    @Test
    void speechCooldownIsIndependentPerRecipientAndEvictsOldEntriesAfterRetention() {
        UUID first = uuid(11), second = uuid(12);
        assertEquals(List.of(first), policy.speechRecipients(
                List.of(player(first, WORLD, 4, true, true)), WORLD));
        assertEquals(List.of(second), policy.speechRecipients(
                List.of(player(first, WORLD, 4, true, true), player(second, WORLD, 1, true, true)), WORLD));
        now.addAndGet(PresentationPolicy.RECIPIENT_RETENTION_MILLIS + 1);
        assertEquals(List.of(second, first), policy.speechRecipients(
                List.of(player(first, WORLD, 4, true, true), player(second, WORLD, 1, true, true)), WORLD));
    }

    @Test
    void speechExcludesOtherWorldAndPlayersBeyondEighteenBlocks() {
        assertTrue(policy.speechRecipients(List.of(
                player(uuid(20), uuid(9), 0, true, true),
                player(uuid(21), WORLD, 18 * 18 + 0.1, true, true)), WORLD).isEmpty());
    }

    @Test
    void recipientRetentionNeverExceedsOneHundredTwentyEightAndEvictsOldestUuidDeterministically() {
        for (int batch = 0; batch < 8; batch++) {
            List<PresentationPolicy.PlayerFact> facts = new java.util.ArrayList<>();
            for (int index = batch * 16 + 1; index <= (batch + 1) * 16; index++) {
                facts.add(player(uuid(index), WORLD, 1, true, true));
            }
            assertEquals(16, policy.speechRecipients(facts, WORLD).size());
        }
        assertEquals(List.of(uuid(129)), policy.speechRecipients(
                List.of(player(uuid(129), WORLD, 1, true, true)), WORLD));
        assertEquals(List.of(uuid(1)), policy.speechRecipients(
                List.of(player(uuid(1), WORLD, 1, true, true)), WORLD));
    }

    @Test
    void workCueRequiresCommittedSuccessAndEnforcesTwoHundredFiftyMillisecondCadence() {
        assertFalse(policy.allowWorkCue(false));
        assertTrue(policy.allowWorkCue(true));
        assertFalse(policy.allowWorkCue(true));
        now.addAndGet(PresentationPolicy.WORK_CUE_CADENCE_MILLIS);
        assertTrue(policy.allowWorkCue(true));
    }

    private static PresentationPolicy.PlayerFact player(UUID id, UUID world, double distanceSquared,
                                                          boolean online, boolean visible) {
        return new PresentationPolicy.PlayerFact(id, world, distanceSquared, online, visible);
    }

    private static UUID uuid(long least) {
        return new UUID(0L, least);
    }
}
