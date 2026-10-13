package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProtectedHomeAreaTest {
  @Test
  void protectsOnlyConfiguredNearbyBlocksInTheSelectedWorld() {
    ProtectedHomeArea area = new ProtectedHomeArea("world", 0, 64, 0, 10);
    assertTrue(area.contains("world", 8, 64, 0));
    assertFalse(area.contains("world", 12, 64, 0));
    assertFalse(area.contains("world_nether", 0, 64, 0));
  }

  @Test
  void zeroRadiusDisablesTheHomeArea() {
    ProtectedHomeArea area = new ProtectedHomeArea("world", 0, 64, 0, 0);
    assertFalse(area.contains("world", 0, 64, 0));
  }
}
