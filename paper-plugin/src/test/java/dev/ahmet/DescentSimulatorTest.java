package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class DescentSimulatorTest {
  private static final int MIN_HEIGHT = -64;

  private static DescentSimulator.BlockView solid(Material m) {
    return new DescentSimulator.BlockView(m, true, false, false, SafeGroundPolicy.isHazard(m));
  }

  private static DescentSimulator.BlockView air() {
    return new DescentSimulator.BlockView(Material.AIR, false, false, true, false);
  }

  private static DescentSimulator.BlockView liquid(Material m) {
    return new DescentSimulator.BlockView(m, false, true, false, SafeGroundPolicy.isHazard(m));
  }

  private static DescentSimulator.BlockView hazard(Material m) {
    return new DescentSimulator.BlockView(m, true, false, false, true);
  }

  @Test
  void oneBlockFallToSolidNonHazardIsSafe() {
    // NPC feet at Y = 70. Support leaf at Y = 69 is removed.
    // Floor at Y = 68 is solid dirt.
    Map<Integer, DescentSimulator.BlockView> col = new HashMap<>();
    col.put(70, air()); // head space
    col.put(69, air()); // feet space after leaf removal
    col.put(68, solid(Material.DIRT)); // landing floor

    assertTrue(DescentSimulator.isSafeDescent(70, MIN_HEIGHT, col::get));
  }

  @Test
  void twoBlockFallToSolidNonHazardIsSafe() {
    // NPC feet at Y = 70. Support leaf at Y = 69 is removed.
    // Air at Y = 68. Floor at Y = 67 is solid stone.
    Map<Integer, DescentSimulator.BlockView> col = new HashMap<>();
    col.put(70, air());
    col.put(69, air());
    col.put(68, air());
    col.put(67, solid(Material.STONE));

    assertTrue(DescentSimulator.isSafeDescent(70, MIN_HEIGHT, col::get));
  }

  @Test
  void threeOrMoreBlockFallIsUnsafe() {
    // NPC feet at Y = 70. Air at 68, 67, 66 (fall >= 3 blocks).
    Map<Integer, DescentSimulator.BlockView> col = new HashMap<>();
    col.put(70, air());
    col.put(69, air());
    col.put(68, air());
    col.put(67, air());
    col.put(66, solid(Material.STONE));

    assertFalse(DescentSimulator.isSafeDescent(70, MIN_HEIGHT, col::get));
  }

  @Test
  void fallOntoHazardIsUnsafe() {
    Map<Integer, DescentSimulator.BlockView> col = new HashMap<>();
    col.put(70, air());
    col.put(69, air());
    col.put(68, hazard(Material.MAGMA_BLOCK));

    assertFalse(DescentSimulator.isSafeDescent(70, MIN_HEIGHT, col::get));
  }

  @Test
  void fallIntoLavaOrWaterIsUnsafe() {
    Map<Integer, DescentSimulator.BlockView> col = new HashMap<>();
    col.put(70, air());
    col.put(69, air());
    col.put(68, liquid(Material.LAVA));

    assertFalse(DescentSimulator.isSafeDescent(70, MIN_HEIGHT, col::get));

    col.put(68, liquid(Material.WATER));
    assertFalse(DescentSimulator.isSafeDescent(70, MIN_HEIGHT, col::get));
  }

  @Test
  void fallThroughHazardIsUnsafe() {
    // Fall 2 blocks, but intermediate space Y = 68 contains fire or sweet berry bush
    Map<Integer, DescentSimulator.BlockView> col = new HashMap<>();
    col.put(70, air());
    col.put(69, air());
    col.put(68, hazard(Material.FIRE));
    col.put(67, solid(Material.DIRT));

    assertFalse(DescentSimulator.isSafeDescent(70, MIN_HEIGHT, col::get));
  }

  @Test
  void fallBelowMinHeightIsUnsafe() {
    Map<Integer, DescentSimulator.BlockView> col = new HashMap<>();
    col.put(MIN_HEIGHT + 1, air());
    col.put(MIN_HEIGHT, air());
    col.put(MIN_HEIGHT - 1, solid(Material.BEDROCK));

    assertFalse(DescentSimulator.isSafeDescent(MIN_HEIGHT + 1, MIN_HEIGHT, col::get));
  }
}
