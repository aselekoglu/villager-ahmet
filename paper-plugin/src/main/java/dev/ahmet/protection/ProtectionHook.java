package dev.ahmet.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * SPI for evaluating whether a block is protected from agent harvesting or clearance.
 * Implementations must fail closed on errors (return true).
 */
@FunctionalInterface
public interface ProtectionHook {
  /**
   * Returns true if the block is protected against agent mutations.
   *
   * @param block the candidate block
   * @param actorLocation the location of Ahmet or null
   * @return true if protected (must not be altered), false if safe to mutate
   */
  boolean isProtected(Block block, Location actorLocation);

  default boolean isProtected(Block block) {
    return isProtected(block, null);
  }
}
