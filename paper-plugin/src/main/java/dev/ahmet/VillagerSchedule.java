package dev.ahmet;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.function.Predicate;

/** Pure world-time schedule policy and bounded daytime waypoint candidate planner. */
public final class VillagerSchedule {
    private static final long DAY_TICKS = 24_000L;
    private static final int[][] COMPASS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };
    private static final int[] RADII = {4, 6};
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1};

    private VillagerSchedule() {}

    public enum Phase { WAKE, WORK, WANDER, SLEEP }

    public static Phase phaseAt(long worldTime) {
        long time = Math.floorMod(worldTime, DAY_TICKS);
        if (time < 1_000L) return Phase.WAKE;
        if (time < 9_000L) return Phase.WORK;
        if (time < 12_000L) return Phase.WANDER;
        return Phase.SLEEP;
    }

    public static boolean allowed(boolean taskActive, int cargo, boolean recoveryBlocked, boolean journalBusy) {
        return !taskActive && cargo == 0 && !recoveryBlocked && !journalBusy;
    }

    /**
     * Selects the first safe location from at most 48 candidates (28 survive the
     * full three-dimensional home-radius bound). Does not inspect the world.
     */
    public static Location findWanderWaypoint(Location current, Location home, int attempt,
                                               Predicate<Location> isSafe) {
        Objects.requireNonNull(isSafe, "isSafe");
        if (current == null || home == null || current.getWorld() == null || home.getWorld() == null
                || current.getWorld() != home.getWorld()) return null;

        int rotation = Math.floorMod(attempt, COMPASS.length);
        World world = home.getWorld();
        for (int radius : RADII) {
            for (int offset : VERTICAL_OFFSETS) {
                for (int index = 0; index < COMPASS.length; index++) {
                    int[] direction = COMPASS[(index + rotation) % COMPASS.length];
                    Location candidate = new Location(world,
                            home.getX() + direction[0] * radius,
                            home.getY() + offset,
                            home.getZ() + direction[1] * radius);
                    if (candidate.distanceSquared(home) > 36.0) continue;
                    if (isSafe.test(candidate)) return candidate;
                }
            }
        }
        return null;
    }
}
