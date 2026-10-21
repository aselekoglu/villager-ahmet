package dev.ahmet.protection;

/**
 * Normalized 3D cuboid bounding box for a land claim in a specific world.
 */
public record ClaimBoundary(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

  public ClaimBoundary {
    if (world == null || world.isBlank()) {
      throw new IllegalArgumentException("Claim world is required");
    }
    int normMinX = Math.min(minX, maxX);
    int normMaxX = Math.max(minX, maxX);
    int normMinY = Math.min(minY, maxY);
    int normMaxY = Math.max(minY, maxY);
    int normMinZ = Math.min(minZ, maxZ);
    int normMaxZ = Math.max(minZ, maxZ);

    minX = normMinX;
    maxX = normMaxX;
    minY = normMinY;
    maxY = normMaxY;
    minZ = normMinZ;
    maxZ = normMaxZ;
  }

  /**
   * Checks if the given world and block coordinates fall within this claim boundary.
   */
  public boolean contains(String worldName, int x, int y, int z) {
    if (worldName == null || !world.equals(worldName)) {
      return false;
    }
    return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
  }

  /**
   * Calculates the total block volume encompassed by this boundary.
   */
  public int volume() {
    return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
  }
}
