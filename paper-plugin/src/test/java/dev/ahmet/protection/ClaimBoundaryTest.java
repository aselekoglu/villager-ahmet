package dev.ahmet.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClaimBoundaryTest {

  @Test
  void testNormalizesInvertedCoordinates() {
    ClaimBoundary boundary = new ClaimBoundary("world", 100, 70, 200, 10, 60, 50);

    assertEquals("world", boundary.world());
    assertEquals(10, boundary.minX());
    assertEquals(100, boundary.maxX());
    assertEquals(60, boundary.minY());
    assertEquals(70, boundary.maxY());
    assertEquals(50, boundary.minZ());
    assertEquals(200, boundary.maxZ());
  }

  @Test
  void testNegativeCoordinates() {
    ClaimBoundary boundary = new ClaimBoundary("world_nether", -50, -64, -100, -10, 128, -20);

    assertEquals(-50, boundary.minX());
    assertEquals(-10, boundary.maxX());
    assertEquals(-64, boundary.minY());
    assertEquals(128, boundary.maxY());
    assertEquals(-100, boundary.minZ());
    assertEquals(-20, boundary.maxZ());

    assertTrue(boundary.contains("world_nether", -30, 0, -50));
    assertTrue(boundary.contains("world_nether", -50, -64, -100)); // Min corner
    assertTrue(boundary.contains("world_nether", -10, 128, -20)); // Max corner

    assertFalse(boundary.contains("world_nether", -51, 0, -50));
    assertFalse(boundary.contains("world_nether", -9, 0, -50));
    assertFalse(boundary.contains("world_nether", -30, -65, -50));
    assertFalse(boundary.contains("world_nether", -30, 129, -50));
    assertFalse(boundary.contains("world_nether", -30, 0, -101));
    assertFalse(boundary.contains("world_nether", -30, 0, -19));
  }

  @Test
  void testContainmentWorldChecks() {
    ClaimBoundary boundary = new ClaimBoundary("world", 0, 0, 0, 10, 10, 10);

    assertTrue(boundary.contains("world", 5, 5, 5));
    assertFalse(boundary.contains("world_nether", 5, 5, 5));
    assertFalse(boundary.contains(null, 5, 5, 5));
  }

  @Test
  void testVolumeCalculation() {
    ClaimBoundary singleBlock = new ClaimBoundary("world", 5, 5, 5, 5, 5, 5);
    assertEquals(1, singleBlock.volume());

    ClaimBoundary box = new ClaimBoundary("world", 0, 0, 0, 9, 9, 9);
    assertEquals(1000, box.volume());

    ClaimBoundary nonSquare = new ClaimBoundary("world", 1, 10, 20, 3, 13, 24);
    // (3 - 1 + 1) * (13 - 10 + 1) * (24 - 20 + 1) = 3 * 4 * 5 = 60
    assertEquals(60, nonSquare.volume());
  }

  @Test
  void testRejectsNullOrBlankWorld() {
    assertThrows(IllegalArgumentException.class, () -> new ClaimBoundary(null, 0, 0, 0, 10, 10, 10));
    assertThrows(IllegalArgumentException.class, () -> new ClaimBoundary("", 0, 0, 0, 10, 10, 10));
    assertThrows(IllegalArgumentException.class, () -> new ClaimBoundary("   ", 0, 0, 0, 10, 10, 10));
  }
}
