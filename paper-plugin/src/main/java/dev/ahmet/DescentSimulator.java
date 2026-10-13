package dev.ahmet;

import java.util.function.IntFunction;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Bounded simulation for safe descent when considering removing a support block.
 * A support block (floor under NPC) may only be removed if the NPC falls at most
 * two blocks to a solid, non-liquid, non-hazard landing floor with safe clearance.
 */
public final class DescentSimulator {
  private DescentSimulator() {}

  record BlockView(Material material, boolean isSolid, boolean isLiquid, boolean isPassable, boolean isHazard) {
    static BlockView fromBlock(Block block) {
      if (block == null) return new BlockView(Material.AIR, false, false, true, false);
      return new BlockView(
          block.getType(),
          block.getType().isSolid(),
          block.isLiquid(),
          block.isPassable(),
          SafeGroundPolicy.isHazard(block.getType())
      );
    }
  }

  /**
   * Simulates dropping from npcFeetY when the support block at (npcFeetY - 1) is removed.
   *
   * @param npcFeetY The Y coordinate of the NPC's feet (standing block)
   * @param minHeight World minimum height
   * @param column Function providing BlockView at a given Y coordinate
   * @return true if simulated landing is safe within 2 blocks fall; false otherwise
   */
  static boolean isSafeDescent(int npcFeetY, int minHeight, IntFunction<BlockView> column) {
    // Drop 1: Landing floor would be at npcFeetY - 2.
    int floor1Y = npcFeetY - 2;
    if (floor1Y < minHeight) return false;

    BlockView floor1 = column.apply(floor1Y);
    if (floor1 == null) return false;

    if (floor1.isSolid()) {
      // Lands on floor1Y: feet at (npcFeetY - 1), head at npcFeetY.
      if (floor1.isLiquid() || floor1.isHazard()) return false;

      // Feet space was the removed support leaf, must now be passable/safe
      BlockView headSpace = column.apply(npcFeetY);
      if (headSpace != null && (!headSpace.isPassable() || headSpace.isLiquid() || headSpace.isHazard())) {
        return false;
      }
      return true;
    }

    // floor1 is not solid. For fall to continue, floor1 space must be passable and safe (no lava/hazard).
    if (!floor1.isPassable() || floor1.isLiquid() || floor1.isHazard()) {
      return false;
    }

    // Drop 2: Landing floor would be at npcFeetY - 3.
    int floor2Y = npcFeetY - 3;
    if (floor2Y < minHeight) return false;

    BlockView floor2 = column.apply(floor2Y);
    if (floor2 == null) return false;

    if (floor2.isSolid()) {
      // Lands on floor2Y: feet at (npcFeetY - 2), head at (npcFeetY - 1), space above at npcFeetY.
      if (floor2.isLiquid() || floor2.isHazard()) return false;

      BlockView feetSpace = column.apply(npcFeetY - 2);
      if (feetSpace != null && (!feetSpace.isPassable() || feetSpace.isLiquid() || feetSpace.isHazard())) {
        return false;
      }

      BlockView headSpace = column.apply(npcFeetY - 1);
      if (headSpace != null && (!headSpace.isPassable() || headSpace.isLiquid() || headSpace.isHazard())) {
        return false;
      }

      BlockView aboveSpace = column.apply(npcFeetY);
      if (aboveSpace != null && (!aboveSpace.isPassable() || aboveSpace.isLiquid() || aboveSpace.isHazard())) {
        return false;
      }

      return true;
    }

    // If floor2Y is also not solid, fall distance would be >= 3 blocks (unsafe).
    return false;
  }

  public static boolean isSafeDescent(World world, int x, int npcFeetY, int z) {
    if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return false;
    return isSafeDescent(npcFeetY, world.getMinHeight(), y -> {
      if (y < world.getMinHeight() || y >= world.getMaxHeight()) return null;
      return BlockView.fromBlock(world.getBlockAt(x, y, z));
    });
  }
}
