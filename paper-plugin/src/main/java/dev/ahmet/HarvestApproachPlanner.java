package dev.ahmet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Location;

/**
 * Finds a safe standing position within harvest reach of a target block
 * before falling back to navigating directly into the target block itself.
 */
final class HarvestApproachPlanner {
  private static final double CARDINAL_HARVEST_DIST_SQ = 1.01;
  private static final double INNER_HARVEST_DIST_SQ = 1.5 * 1.5;
  private static final double MAX_HARVEST_DIST_SQ = 2.9 * 2.9;

  private HarvestApproachPlanner() {}

  static Location findApproach(Location npcLoc, Location targetBlockLoc, Predicate<Location> safeGround) {
    if (targetBlockLoc == null || safeGround == null) return null;

    Location targetReference = targetBlockLoc.clone().add(0.5, 0, 0.5);
    List<Location> candidates = new ArrayList<>();

    for (int dx = -2; dx <= 2; dx++) {
      for (int dz = -2; dz <= 2; dz++) {
        for (int dy = -2; dy <= 2; dy++) {
          Location candidate = targetBlockLoc.clone().add(dx + 0.5, dy, dz + 0.5);
          if (distSq(candidate, targetReference) <= MAX_HARVEST_DIST_SQ && safeGround.test(candidate)) {
            candidates.add(candidate);
          }
        }
      }
    }

    if (candidates.isEmpty()) return null;

    Comparator<Location> comparator = Comparator.comparingInt((Location loc) -> {
      double dSq = distSq(loc, targetReference);
      if (dSq <= CARDINAL_HARVEST_DIST_SQ) return 0;
      if (dSq <= INNER_HARVEST_DIST_SQ) return 1;
      return 2;
    });
    if (npcLoc != null) {
      comparator = comparator.thenComparingDouble(loc -> distSq(npcLoc, loc));
    } else {
      comparator = comparator.thenComparingDouble(loc -> distSq(loc, targetReference));
    }
    candidates.sort(comparator
        .thenComparingInt(Location::getBlockY)
        .thenComparingInt(Location::getBlockX)
        .thenComparingInt(Location::getBlockZ));

    return candidates.getFirst();
  }

  private static double distSq(Location a, Location b) {
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    double dz = a.getZ() - b.getZ();
    return dx * dx + dy * dy + dz * dz;
  }
}

