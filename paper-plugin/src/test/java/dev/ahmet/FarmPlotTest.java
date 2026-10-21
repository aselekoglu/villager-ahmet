package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class FarmPlotTest {

  @Test
  void constructorRejectsInvalidInputs() {
    assertThrows(IllegalArgumentException.class, () -> new FarmPlot(null, 0, 64, 0, 8));
    assertThrows(IllegalArgumentException.class, () -> new FarmPlot("  ", 0, 64, 0, 8));
    assertThrows(IllegalArgumentException.class, () -> new FarmPlot("world", 0, 64, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new FarmPlot("world", 0, 64, 0, -1));
    assertThrows(IllegalArgumentException.class, () -> new FarmPlot("world", 0, 64, 0, 17));
  }

  @Test
  void constructorAcceptsValidRadiusBounds() {
    FarmPlot min = new FarmPlot("world", 10, 64, 20, 1);
    assertEquals("world", min.world());
    assertEquals(10, min.centerX());
    assertEquals(64, min.centerY());
    assertEquals(20, min.centerZ());
    assertEquals(1, min.radius());

    FarmPlot max = new FarmPlot("world_nether", -5, 70, 100, 16);
    assertEquals(16, max.radius());
  }

  @Test
  void containmentHorizontalAndVerticalChecks() {
    FarmPlot plot = new FarmPlot("world", 100, 65, 200, 5);

    // Center point
    assertTrue(plot.contains("world", 100, 65, 200));

    // Horizontal edges
    assertTrue(plot.contains("world", 105, 65, 200));
    assertTrue(plot.contains("world", 95, 65, 200));
    assertTrue(plot.contains("world", 100, 65, 205));
    assertTrue(plot.contains("world", 100, 65, 195));
    assertTrue(plot.contains("world", 105, 65, 205)); // diagonal corner

    // Horizontal outside
    assertFalse(plot.contains("world", 106, 65, 200));
    assertFalse(plot.contains("world", 94, 65, 200));
    assertFalse(plot.contains("world", 100, 65, 206));
    assertFalse(plot.contains("world", 100, 65, 194));

    // Vertical inside (+/- 1 Y)
    assertTrue(plot.contains("world", 100, 64, 200));
    assertTrue(plot.contains("world", 100, 66, 200));

    // Vertical outside
    assertFalse(plot.contains("world", 100, 63, 200));
    assertFalse(plot.contains("world", 100, 67, 200));

    // World mismatch or null
    assertFalse(plot.contains("other_world", 100, 65, 200));
    assertFalse(plot.contains(null, 100, 65, 200));
  }

  @Test
  void saveAndLoadRoundTrip() {
    YamlConfiguration config = new YamlConfiguration();
    FarmPlot original = new FarmPlot("world", 15, 63, -40, 12);

    FarmPlot.save(config, original);
    assertEquals("world", config.getString("farm.plot.world"));
    assertEquals(15, config.getInt("farm.plot.x"));
    assertEquals(63, config.getInt("farm.plot.y"));
    assertEquals(-40, config.getInt("farm.plot.z"));
    assertEquals(12, config.getInt("farm.plot.radius"));

    FarmPlot loaded = FarmPlot.load(config);
    assertNotNull(loaded);
    assertEquals(original, loaded);

    // Clear plot
    FarmPlot.save(config, null);
    assertFalse(config.contains("farm.plot"));
    assertNull(FarmPlot.load(config));
  }

  @Test
  void loadReturnsEmptyWhenSectionMissing() {
    YamlConfiguration config = new YamlConfiguration();
    assertNull(FarmPlot.load(config));
  }

  @Test
  void loadFailsClosedOnMalformedData() {
    // Missing keys
    YamlConfiguration c1 = new YamlConfiguration();
    c1.set("farm.plot.world", "world");
    c1.set("farm.plot.x", 10);
    // missing y, z, radius
    assertThrows(IllegalStateException.class, () -> FarmPlot.load(c1));

    // Non-integer coordinates
    YamlConfiguration c2 = new YamlConfiguration();
    c2.set("farm.plot.world", "world");
    c2.set("farm.plot.x", "not_a_number");
    c2.set("farm.plot.y", 64);
    c2.set("farm.plot.z", 0);
    c2.set("farm.plot.radius", 5);
    assertThrows(IllegalStateException.class, () -> FarmPlot.load(c2));

    // Non-string world
    YamlConfiguration c3 = new YamlConfiguration();
    c3.set("farm.plot.world", 12345);
    c3.set("farm.plot.x", 0);
    c3.set("farm.plot.y", 64);
    c3.set("farm.plot.z", 0);
    c3.set("farm.plot.radius", 5);
    assertThrows(IllegalStateException.class, () -> FarmPlot.load(c3));

    // Radius out of bounds
    YamlConfiguration c4 = new YamlConfiguration();
    c4.set("farm.plot.world", "world");
    c4.set("farm.plot.x", 0);
    c4.set("farm.plot.y", 64);
    c4.set("farm.plot.z", 0);
    c4.set("farm.plot.radius", 25);
    assertThrows(IllegalStateException.class, () -> FarmPlot.load(c4));

    // Blank world
    YamlConfiguration c5 = new YamlConfiguration();
    c5.set("farm.plot.world", "   ");
    c5.set("farm.plot.x", 0);
    c5.set("farm.plot.y", 64);
    c5.set("farm.plot.z", 0);
    c5.set("farm.plot.radius", 5);
    assertThrows(IllegalStateException.class, () -> FarmPlot.load(c5));
  }
}
