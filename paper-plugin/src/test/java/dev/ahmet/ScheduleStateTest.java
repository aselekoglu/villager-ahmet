package dev.ahmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Proxy;

class ScheduleStateTest {
    private static org.bukkit.World fakeWorld(String name) {
        return (org.bukkit.World) Proxy.newProxyInstance(org.bukkit.World.class.getClassLoader(), new Class<?>[]{org.bukkit.World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> name;
                    default -> null;
                });
    }
    @Test
    void phaseTransitionDropsOnlyScheduleRouteAndResetsRetryAndWaypoint() {
        ScheduleState state = new ScheduleState();
        assertTrue(state.changePhase(VillagerSchedule.Phase.WANDER));
        state.target(new org.bukkit.Location(fakeWorld("world"), 2, 64, 2));
        state.navigationStarted();
        assertTrue(state.changePhase(VillagerSchedule.Phase.SLEEP));
        assertTrue(state.ownsNavigation(), "phase update must preserve ownership until route cancellation");
        assertNotNull(state.target());
        state.navigationStopped(() -> assertTrue(state.ownsNavigation()));
        state.navigationStarted();
        state.retry(100, "stalled");
        assertTrue(state.changePhase(VillagerSchedule.Phase.WAKE));
        assertFalse(state.ownsNavigation());
        assertFalse(state.retryPending());
        assertEquals(0, state.waypointAttempt());
        assertEquals("phase wake", state.reason());
    }

    @Test
    void arrivalAndRetryAreBoundedAndObservable() {
        ScheduleState state = new ScheduleState();
        state.changePhase(VillagerSchedule.Phase.WANDER);
        state.navigationStarted();
        state.arrived("wandered");
        assertFalse(state.ownsNavigation());
        assertEquals(1, state.waypointAttempt());
        state.retry(500, "route stalled");
        assertTrue(state.retryPending());
        assertFalse(state.retryDue(2_499));
        assertTrue(state.retryDue(2_500));
        state.retryConsumed();
        assertFalse(state.retryPending());
        assertEquals(2, state.waypointAttempt());
        assertEquals("route stalled", state.reason());
    }

    @Test
    void samePhaseDoesNotResetActiveScheduleRoute() {
        ScheduleState state = new ScheduleState();
        state.changePhase(VillagerSchedule.Phase.WORK);
        state.navigationStarted();
        assertFalse(state.changePhase(VillagerSchedule.Phase.WORK));
        assertTrue(state.ownsNavigation());
    }

    @Test
    void idleCannotDiscardRouteBeforeOwnedNavigationIsCancelled() {
        ScheduleState state = new ScheduleState();
        state.changePhase(VillagerSchedule.Phase.WANDER);
        state.target(new org.bukkit.Location(fakeWorld("world"), 1, 64, 1));
        state.navigationStarted();
        state.idle("fallback");
        assertTrue(state.ownsNavigation());
        assertNotNull(state.target());
        AtomicBoolean cancelled = new AtomicBoolean();
        state.navigationStopped(() -> {
            assertTrue(state.ownsNavigation());
            assertNotNull(state.target());
            cancelled.set(true);
        });
        assertTrue(cancelled.get());
        assertFalse(state.ownsNavigation());
        assertNull(state.target());
    }

    @Test
    void sleepOwnershipKeepsScheduleActiveAndWakesBeforeReleasingOwnership() {
        ScheduleState state=new ScheduleState();
        state.changePhase(VillagerSchedule.Phase.SLEEP);
        state.sleepStarted();
        state.idle("sleeping");
        assertTrue(state.ownsSleep());
        assertTrue(state.ownsTurn());
        AtomicBoolean woke=new AtomicBoolean();
        state.wakeOwned(()->{
            assertTrue(state.ownsSleep());
            woke.set(true);
        });
        assertTrue(woke.get());
        assertFalse(state.ownsSleep());
    }
}
