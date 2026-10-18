package dev.ahmet;

import org.bukkit.Location;

/** Pure lifecycle for schedule-owned navigation, separate from task routes. */
final class ScheduleState {
    private VillagerSchedule.Phase phase;
    private boolean ownsNavigation;
    private boolean ownsSleep;
    private int waypointAttempt;
    private long retryAtMillis = -1;
    private String reason = "idle";
    private final NavigationWatchdog watchdog = new NavigationWatchdog(0.0);
    private Location target;

    VillagerSchedule.Phase phase() { return phase; }
    boolean ownsNavigation() { return ownsNavigation; }
    boolean ownsSleep() { return ownsSleep; }
    boolean ownsTurn() { return phase != null; }
    int waypointAttempt() { return waypointAttempt; }
    String reason() { return reason; }
    NavigationWatchdog watchdog() { return watchdog; }
    Location target() { return target; }
    void target(Location value) { target = value; }
    boolean retryPending() { return retryAtMillis >= 0; }
    boolean retryDue(long now) { return retryAtMillis >= 0 && now >= retryAtMillis; }

    boolean changePhase(VillagerSchedule.Phase next) {
        if (phase == next) return false;
        phase = next;
        retryAtMillis = -1;
        waypointAttempt = 0;
        watchdog.reset();
        reason = "phase " + next.name().toLowerCase(java.util.Locale.ROOT);
        return true;
    }

    void navigationStarted() { ownsNavigation = true; reason = "walking"; }
    void navigationStopped() { ownsNavigation = false; target = null; watchdog.reset(); }
    void navigationStopped(Runnable cancelOwnedRoute) {
        if (ownsNavigation) cancelOwnedRoute.run();
        navigationStopped();
    }
    void sleepStarted() { ownsSleep = true; }
    void wakeOwned(Runnable wake) { if (ownsSleep) wake.run(); ownsSleep = false; }
    void preempt(String nextReason) { idle(nextReason); }
    void arrived(String nextReason) { ownsNavigation = false; retryAtMillis = -1; waypointAttempt++; reason = nextReason; target = null; watchdog.reset(); }
    void retry(long nowMillis, String nextReason) { ownsNavigation = false; retryAtMillis = nowMillis + 2_000; waypointAttempt++; reason = nextReason; target = null; watchdog.reset(); }
    void retryConsumed() { retryAtMillis = -1; }
    void idle(String nextReason) { retryAtMillis = -1; reason = nextReason; }
}
