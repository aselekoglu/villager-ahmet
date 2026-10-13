package dev.ahmet;

import org.bukkit.Location;

/** Converts block origins once; planner-produced safe coordinates pass through unchanged. */
final class NavigationTarget {
  private NavigationTarget() {}
  static Location fromBlock(Location block) { return block == null ? null : block.clone().add(0.5, 0, 0.5); }
  static Location fromPlanner(Location safeTarget) { return safeTarget == null ? null : safeTarget.clone(); }
}
