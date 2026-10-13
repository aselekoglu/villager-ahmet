package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PlayerPlacedWoodRegistryTest {
  @Test
  void tracksExactWorldAndBlockCoordinatesAndPersistsThem() {
    PlayerPlacedWoodRegistry registry = new PlayerPlacedWoodRegistry();
    assertTrue(registry.record("world", 12, 70, -4));
    assertTrue(registry.contains("world", 12, 70, -4));
    assertFalse(registry.contains("world", 12, 71, -4));
    assertFalse(registry.contains("world_nether", 12, 70, -4));

    PlayerPlacedWoodRegistry restored = new PlayerPlacedWoodRegistry();
    restored.load(registry.serialize());
    assertTrue(restored.contains("world", 12, 70, -4));
    assertFalse(restored.record("world", 12, 70, -4));
  }

  @Test
  void invalidPersistedMarkerFailsClosed() {
    PlayerPlacedWoodRegistry registry = new PlayerPlacedWoodRegistry();
    registry.record("world", 2, 64, 3);
    assertThrows(IllegalStateException.class, () -> registry.load(java.util.List.of("not-a-coordinate")));
    assertTrue(registry.contains("world", 2, 64, 3), "invalid loads must not discard previously trusted markers");
  }
}
