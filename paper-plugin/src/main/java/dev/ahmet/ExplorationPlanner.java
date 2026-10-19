package dev.ahmet;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.Objects;
import java.util.function.Predicate;

final class ExplorationPlanner {
  private ExplorationPlanner() {}

  /**
   * Plans an exploration waypoint around current position while remaining within the home
   * boundary and outside the protected home build area.
   */
  static Location findExplorationWaypoint(Location current, Location home, Location protectedHome,
      int protectHomeRadius, int maxDistanceFromHome, int attempt, Predicate<Location> isSafe) {
    if (current == null || isSafe == null) return null;
    Location effectiveHome = protectedHome != null ? protectedHome : home;
    World world = current.getWorld();

    int cleanAttempt = Math.abs(attempt);
    double baseAngle = ((cleanAttempt % 8) * (Math.PI / 4.0));
    double[] distances = { 14.0, 10.0, 18.0, 8.0 };

    for (double distance : distances) {
      for (int i = 0; i < 8; i++) {
        double angle = baseAngle + (i * (Math.PI / 4.0));
        double targetX = current.getX() + (distance * Math.cos(angle));
        double targetZ = current.getZ() + (distance * Math.sin(angle));

        int blockX = (int) Math.floor(targetX);
        int blockZ = (int) Math.floor(targetZ);
        int currentY = current.getBlockY();

        for (int dy : new int[]{ 0, 1, -1, 2, -2, 3, -3, 4, -4 }) {
          int y = currentY + dy;
          Location candidate = new Location(world, blockX + 0.5, y, blockZ + 0.5);
          // Check the actual block-center waypoint, including its selected height, against the home boundary.
          if (effectiveHome != null && Objects.equals(effectiveHome.getWorld(), world)) {
            double dx = candidate.getX() - effectiveHome.getX();
            double dyFromHome = candidate.getY() - effectiveHome.getY();
            double dz = candidate.getZ() - effectiveHome.getZ();
            if (dx * dx + dyFromHome * dyFromHome + dz * dz > Math.pow(maxDistanceFromHome, 2)) continue;
          }

          // Apply the same protected-home margin to the snapped candidate, not its unsnapped ray.
          if (protectedHome != null && protectHomeRadius > 0 && Objects.equals(protectedHome.getWorld(), world)) {
            double dx = candidate.getX() - protectedHome.getX();
            double dz = candidate.getZ() - protectedHome.getZ();
            if (dx * dx + dz * dz < Math.pow(protectHomeRadius + 2, 2)) continue;
          }

          if (isSafe.test(candidate)) {
            return candidate;
          }
        }
      }
    }
    return null;
  }

  static Location findStepTowardsHome(Location current, Location home, double stepDistance, Predicate<Location> isSafe) {
    return findStepTowardsHome(current, home, stepDistance, 0, isSafe);
  }

  /**
   * Calculates an intermediate navigation waypoint from current towards home,
   * advancing along the vector towards home on safe ground.
   */
  static Location findStepTowardsHome(Location current, Location home, double stepDistance, int attempt, Predicate<Location> isSafe) {
    if (current == null || home == null || !Objects.equals(current.getWorld(), home.getWorld()) || isSafe == null) {
      return null;
    }
    double dx = home.getX() - current.getX();
    double dy = home.getY() - current.getY();
    double dz = home.getZ() - current.getZ();
    double horizontalDist = Math.sqrt(dx * dx + dz * dz);
    double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

    if (totalDist <= 2.5) {
      return home;
    }
    World world = current.getWorld();
    if (totalDist <= stepDistance && isSafe.test(home)) {
      return home;
    }
    if (horizontalDist < 1.0e-3) {
      return isSafe.test(home) ? home : null;
    }

    double baseAngle = Math.atan2(dz, dx);
    int cleanAttempt = Math.abs(attempt);
    double offsetAngle = switch (cleanAttempt % 5) {
      case 1 -> 0.44;  // ~25 deg
      case 2 -> -0.44;
      case 3 -> 0.78;  // ~45 deg
      case 4 -> -0.78;
      default -> 0.0;
    };
    double finalAngle = baseAngle + offsetAngle;

    double maxAdvance = Math.min(stepDistance, horizontalDist);
    double[] fractions = { 1.0, 0.75, 0.5, 0.25 };

    for (double frac : fractions) {
      double step = maxAdvance * frac;
      double targetX = current.getX() + (step * Math.cos(finalAngle));
      double targetZ = current.getZ() + (step * Math.sin(finalAngle));

      int blockX = (int) Math.floor(targetX);
      int blockZ = (int) Math.floor(targetZ);
      int currentY = current.getBlockY();

      for (int dyOffset : new int[]{ 0, 1, -1, 2, -2, 3, -3, 4, -4 }) {
        int y = currentY + dyOffset;
        Location candidate = new Location(world, blockX + 0.5, y, blockZ + 0.5);
        if (isSafe.test(candidate)) {
          return candidate;
        }
      }
    }
    return null;
  }
}
