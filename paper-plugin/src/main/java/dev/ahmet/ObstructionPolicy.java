package dev.ahmet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;

/**
 * Strict material and provenance policy for identifying safe obstruction candidates
 * to clear when navigation is stuck.
 */
final class ObstructionPolicy {
  private ObstructionPolicy() {}

  record CandidateObstruction(Block block, boolean isSupportLeaf, double distance) {}

  static boolean isLeaf(Material material) {
    if (material == null) return false;
    if (material.name().endsWith("_LEAVES")) return true;
    try {
      return Tag.LEAVES.isTagged(material);
    } catch (Throwable t) {
      return false;
    }
  }

  static boolean isEligibleLeaf(Block block, ProtectedHomeArea protectedHome, PlayerPlacedWoodRegistry placedRegistry) {
    if (block == null) return false;
    World world = block.getWorld();
    if (world == null || !world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;
    if (!isLeaf(block.getType())) return false;
    if (SafeGroundPolicy.isHazard(block.getType())) return false;
    if (block.getBlockData() instanceof Leaves leaves && leaves.isPersistent()) return false;
    if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) return false;
    if (block.getState() instanceof TileState) return false;
    if (placedRegistry != null && placedRegistry.contains(world.getName(), block.getX(), block.getY(), block.getZ())) return false;
    if (protectedHome != null && protectedHome.contains(world.getName(), block.getX(), block.getY(), block.getZ())) return false;
    return true;
  }


  static List<CandidateObstruction> findCandidates(Location npcLoc, Location goalLoc,
      ProtectedHomeArea protectedHome, PlayerPlacedWoodRegistry placedRegistry) {
    if (npcLoc == null || npcLoc.getWorld() == null) return List.of();
    World world = npcLoc.getWorld();
    int bx = npcLoc.getBlockX(), by = npcLoc.getBlockY(), bz = npcLoc.getBlockZ();

    List<CandidateObstruction> sideObstructions = new ArrayList<>();
    CandidateObstruction supportCandidate = null;

    // Check support leaf under feet
    Block floorBlock = world.getBlockAt(bx, by - 1, bz);
    if (isEligibleLeaf(floorBlock, protectedHome, placedRegistry) && DescentSimulator.isSafeDescent(world, bx, by, bz)) {
      supportCandidate = new CandidateObstruction(floorBlock, true, 1.0);
    }

    // Check horizontal neighbors at feet level and head level, plus directly above head
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        for (int dy = 0; dy <= 1; dy++) {
          if (dx == 0 && dz == 0 && dy == 0) continue; // NPC feet position
          int x = bx + dx, y = by + dy, z = bz + dz;
          if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
          if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

          Block b = world.getBlockAt(x, y, z);
          if (isEligibleLeaf(b, protectedHome, placedRegistry)) {
            double distToNpc = Math.sqrt(dx * dx + dy * dy + dz * dz);
            sideObstructions.add(new CandidateObstruction(b, false, distToNpc));
          }
        }
      }
    }

    // Sort side obstructions: prioritize those closer to goalLoc (if provided)
    if (goalLoc != null && goalLoc.getWorld() != null && goalLoc.getWorld().equals(world)) {
      sideObstructions.sort(Comparator.comparingDouble((CandidateObstruction c) -> {
        double gx = c.block().getX() + 0.5 - goalLoc.getX();
        double gy = c.block().getY() + 0.5 - goalLoc.getY();
        double gz = c.block().getZ() + 0.5 - goalLoc.getZ();
        return gx * gx + gy * gy + gz * gz;
      }).thenComparingDouble(CandidateObstruction::distance));
    } else {
      sideObstructions.sort(Comparator.comparingDouble(CandidateObstruction::distance));
    }

    List<CandidateObstruction> result = new ArrayList<>();
    for (CandidateObstruction side : sideObstructions) {
      if (result.size() >= 2) break;
      result.add(side);
    }

    if (result.size() < 2 && supportCandidate != null) {
      result.add(supportCandidate);
    }

    return List.copyOf(result);
  }
}
