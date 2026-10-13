package dev.ahmet;

import java.util.function.Predicate;
import org.bukkit.Location;

/** Converts block origins once; planner-produced safe coordinates pass through unchanged. */
final class NavigationTarget {
  private NavigationTarget() {}
  static Location fromBlock(Location block) { return block == null ? null : block.clone().add(0.5, 0, 0.5); }
  static Location fromPlanner(Location safeTarget) { return safeTarget == null ? null : safeTarget.clone(); }

  static Location approachFor(Location targetBlock, Location npcLoc, Predicate<Location> safeGround) {
    if (targetBlock == null) return null;
    Location approach = HarvestApproachPlanner.findApproach(npcLoc, targetBlock, safeGround);
    return approach != null ? fromPlanner(approach) : fromBlock(targetBlock);
  }
}

