package dev.ahmet;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SummonTargetTest {
  @Test void picksASafeAdjacentBlockRatherThanOverlappingThePlayer() {
    Location player = new Location(null, 10.25, 64, 5.75, 0, 0);

    Location target = SummonTarget.find(player, candidate ->
        candidate.getBlockX() == 10 && candidate.getBlockZ() == 4 && candidate.getBlockY() == 64);

    assertNotNull(target);
    assertEquals(10.5, target.getX());
    assertEquals(64, target.getY());
    assertEquals(4.5, target.getZ());
    assertTrue(target.getBlockX() != player.getBlockX() || target.getBlockZ() != player.getBlockZ());
  }

  @Test void returnsNoTargetWhenNoAdjacentBlockIsSafe() {
    Location player = new Location(null, 10.25, 64, 5.75, 0, 0);

    assertNull(SummonTarget.find(player, candidate -> false));
  }
}
