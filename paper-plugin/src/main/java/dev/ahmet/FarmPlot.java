package dev.ahmet;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Operator-approved bounded rectangular farm plot where mature allowlisted crops may be harvested and replanted.
 */
public record FarmPlot(String world, int centerX, int centerY, int centerZ, int radius) {
  public static final int MIN_RADIUS = 1;
  public static final int MAX_RADIUS = 16;

  public FarmPlot {
    if (world == null || world.isBlank()) {
      throw new IllegalArgumentException("Farm plot world is required");
    }
    if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
      throw new IllegalArgumentException("Farm plot radius must be between 1 and 16");
    }
  }

  public boolean contains(String candidateWorld, int x, int y, int z) {
    if (!world.equals(candidateWorld)) return false;
    return Math.abs((long) x - centerX) <= radius
        && Math.abs((long) z - centerZ) <= radius
        && Math.abs((long) y - centerY) <= 1;
  }

  public static FarmPlot load(FileConfiguration config) {
    if (config == null || !config.contains("farm.plot")) return null;
    Object rawSection = config.get("farm.plot");
    if (rawSection == null) return null;
    Object rawWorld = config.get("farm.plot.world");
    Object rawX = config.get("farm.plot.x");
    Object rawY = config.get("farm.plot.y");
    Object rawZ = config.get("farm.plot.z");
    Object rawRadius = config.get("farm.plot.radius");
    if (!(rawWorld instanceof String worldName) || worldName.isBlank()
        || !(rawX instanceof Integer x)
        || !(rawY instanceof Integer y)
        || !(rawZ instanceof Integer z)
        || !(rawRadius instanceof Integer r)) {
      throw new IllegalStateException("Saved farm plot configuration has invalid field types");
    }
    try {
      return new FarmPlot(worldName, x, y, z, r);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Saved farm plot configuration is out of bounds", e);
    }
  }

  public static void save(FileConfiguration config, FarmPlot plot) {
    if (config == null) return;
    config.set("farm.plot", null);
    if (plot == null) return;
    config.set("farm.plot.world", plot.world());
    config.set("farm.plot.x", plot.centerX());
    config.set("farm.plot.y", plot.centerY());
    config.set("farm.plot.z", plot.centerZ());
    config.set("farm.plot.radius", plot.radius());
  }
}
