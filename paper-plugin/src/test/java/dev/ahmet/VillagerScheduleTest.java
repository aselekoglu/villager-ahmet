package dev.ahmet;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VillagerScheduleTest {
    @Test
    void phaseBoundariesUseWorldTimeModulo24000IncludingNegativeAndExtremeValues() {
        assertEquals(VillagerSchedule.Phase.WAKE, VillagerSchedule.phaseAt(0));
        assertEquals(VillagerSchedule.Phase.WAKE, VillagerSchedule.phaseAt(999));
        assertEquals(VillagerSchedule.Phase.WORK, VillagerSchedule.phaseAt(1_000));
        assertEquals(VillagerSchedule.Phase.WORK, VillagerSchedule.phaseAt(8_999));
        assertEquals(VillagerSchedule.Phase.WANDER, VillagerSchedule.phaseAt(9_000));
        assertEquals(VillagerSchedule.Phase.WANDER, VillagerSchedule.phaseAt(11_999));
        assertEquals(VillagerSchedule.Phase.SLEEP, VillagerSchedule.phaseAt(12_000));
        assertEquals(VillagerSchedule.Phase.SLEEP, VillagerSchedule.phaseAt(23_999));
        assertEquals(VillagerSchedule.Phase.WAKE, VillagerSchedule.phaseAt(24_000));
        assertEquals(VillagerSchedule.Phase.SLEEP, VillagerSchedule.phaseAt(-1));
        assertEquals(VillagerSchedule.Phase.SLEEP, VillagerSchedule.phaseAt(Long.MIN_VALUE));
        assertEquals(VillagerSchedule.Phase.WAKE, VillagerSchedule.phaseAt(Long.MAX_VALUE - Math.floorMod(Long.MAX_VALUE, 24_000)));
    }

    @Test
    void navigationIsAllowedOnlyWhenNoTaskCargoRecoveryOrJournalWorkExists() {
        assertTrue(VillagerSchedule.allowed(false, 0, false, false));
        assertFalse(VillagerSchedule.allowed(true, 0, false, false));
        assertFalse(VillagerSchedule.allowed(false, 1, false, false));
        assertFalse(VillagerSchedule.allowed(false, 0, true, false));
        assertFalse(VillagerSchedule.allowed(false, 0, false, true));
        assertFalse(VillagerSchedule.allowed(false, -1, false, false));
    }

    @Test
    void waypointSearchIsDeterministicBoundedAndWithinSixThreeDimensionalBlocksOfHome() {
        World world = fakeWorld("world");
        Location home = new Location(world, 10, 64, 10);
        Location current = new Location(world, 10, 64, 10);
        List<Location> seen = new ArrayList<>();
        Location result = VillagerSchedule.findWanderWaypoint(current, home, 0, candidate -> {
            seen.add(candidate.clone());
            return true;
        });
        assertNotNull(result);
        assertEquals(1, seen.size());
        assertEquals(result, seen.getFirst());
        assertTrue(result.distance(home) <= 6.0);
        assertSame(world, result.getWorld());

        List<Location> secondRun = new ArrayList<>();
        Location repeated = VillagerSchedule.findWanderWaypoint(current, home, 0, candidate -> {
            secondRun.add(candidate.clone());
            return true;
        });
        assertEquals(result, repeated);
        assertEquals(seen, secondRun);

        AtomicInteger calls = new AtomicInteger();
        Location rotated = VillagerSchedule.findWanderWaypoint(current, home, 1, candidate -> {
            calls.incrementAndGet();
            return true;
        });
        assertNotEquals(result, rotated);
        assertEquals(1, calls.get());
    }

    @Test
    void unsafeCandidatesAreSkippedAndNoSafeCandidateReturnsNullWithinBound() {
        World world = fakeWorld("world");
        Location home = new Location(world, 0, 64, 0);
        AtomicInteger calls = new AtomicInteger();
        Location accepted = VillagerSchedule.findWanderWaypoint(home, home, 0, candidate -> {
            calls.incrementAndGet();
            return calls.get() == 17;
        });
        assertNotNull(accepted);
        assertEquals(17, calls.get());
        assertTrue(calls.get() <= 48);

        calls.set(0);
        assertNull(VillagerSchedule.findWanderWaypoint(home, home, 0, candidate -> {
            calls.incrementAndGet();
            return false;
        }));
        assertEquals(28, calls.get());
    }

    @Test
    void waypointSearchRejectsDifferentWorldsWithoutCallingSafetyPredicate() {
        Location current = new Location(fakeWorld("one"), 0, 64, 0);
        Location home = new Location(fakeWorld("two"), 0, 64, 0);
        AtomicInteger calls = new AtomicInteger();
        assertNull(VillagerSchedule.findWanderWaypoint(current, home, 0, candidate -> {
            calls.incrementAndGet();
            return true;
        }));
        assertEquals(0, calls.get());
    }

    private static World fakeWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> name;
                    default -> null;
                });
    }
}
