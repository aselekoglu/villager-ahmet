package dev.ahmet;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExplorationPlannerTest {

  @Test
  void explorationWaypointStaysWithinMaxDistanceAndOutsideProtectedHome() {
    Location home = new Location(null, 0.0, 70.0, 0.0);
    Location protectedHome = new Location(null, 0.0, 70.0, 0.0);
    Location current = new Location(null, 10.0, 70.0, 0.0);

    Location waypoint = ExplorationPlanner.findExplorationWaypoint(
        current, home, protectedHome, 8, 32, 0, loc -> loc.getBlockY() == 70);

    assertNotNull(waypoint);
    assertEquals(70.0, waypoint.getY());
    double distFromHome = Math.sqrt(Math.pow(waypoint.getX() - home.getX(), 2) + Math.pow(waypoint.getZ() - home.getZ(), 2));
    double distFromProtected = Math.sqrt(Math.pow(waypoint.getX() - protectedHome.getX(), 2) + Math.pow(waypoint.getZ() - protectedHome.getZ(), 2));

    assertTrue(distFromHome <= 32.0, "Waypoint must remain within max distance from home (32)");
    assertTrue(distFromProtected >= 10.0, "Waypoint must remain outside protected radius + 2 (8 + 2 = 10)");
  }

  @Test
  void explorationWaypointReturnsNullWhenNoCandidateIsSafe() {
    Location home = new Location(null, 0.0, 70.0, 0.0);
    Location current = new Location(null, 5.0, 70.0, 5.0);

    Location waypoint = ExplorationPlanner.findExplorationWaypoint(
        current, home, null, 0, 32, 0, loc -> false);

    assertNull(waypoint);
  }

  @Test
  void explorationWaypointRotatesDirectionAcrossAttempts() {
    Location home = new Location(null, 0.0, 70.0, 0.0);
    Location current = new Location(null, 0.0, 70.0, 0.0);

    Location attempt0 = ExplorationPlanner.findExplorationWaypoint(
        current, home, null, 0, 32, 0, loc -> true);
    Location attempt2 = ExplorationPlanner.findExplorationWaypoint(
        current, home, null, 0, 32, 2, loc -> true);

    assertNotNull(attempt0);
    assertNotNull(attempt2);
    // Attempt 0 base angle 0 (cos=1, sin=0 -> X increases) vs Attempt 2 base angle pi/2 (cos=0, sin=1 -> Z increases)
    assertNotEquals(attempt0.getBlockX(), attempt2.getBlockX());
  }

  @Test
  void stepTowardsHomeReturnsHomeDirectlyWhenClose() {
    Location current = new Location(null, 1.0, 70.0, 1.0);
    Location home = new Location(null, 0.0, 70.0, 0.0);

    Location step = ExplorationPlanner.findStepTowardsHome(current, home, 16.0, 0, loc -> true);

    assertNotNull(step);
    assertEquals(home, step);
  }

  @Test
  void stepTowardsHomeReturnsHomeDirectlyWhenWithinStepDistance() {
    Location current = new Location(null, 10.0, 70.0, 0.0);
    Location home = new Location(null, 0.0, 70.0, 0.0);

    Location step = ExplorationPlanner.findStepTowardsHome(current, home, 16.0, 0, loc -> true);

    assertNotNull(step);
    assertEquals(home, step);
  }

  @Test
  void stepTowardsHomeAdvancesAlongVectorWhenFarAway() {
    Location current = new Location(null, 100.0, 70.0, 0.0);
    Location home = new Location(null, 0.0, 70.0, 0.0);

    Location step = ExplorationPlanner.findStepTowardsHome(current, home, 16.0, 0, loc -> true);

    assertNotNull(step);
    // dx = -100, advance 16 -> targetX = 100 - 16 = 84
    assertEquals(84.5, step.getX());
    assertEquals(0.5, step.getZ());
    assertEquals(70.0, step.getY());
    assertTrue(step.getX() < current.getX());
    assertTrue(step.getX() > home.getX());
  }

  @Test
  void stepTowardsHomeFindsElevatedGround() {
    Location current = new Location(null, 100.0, 70.0, 0.0);
    Location home = new Location(null, 0.0, 70.0, 0.0);

    // Only Y=72 is safe (hill)
    Location step = ExplorationPlanner.findStepTowardsHome(current, home, 16.0, 0, loc -> loc.getBlockY() == 72);

    assertNotNull(step);
    assertEquals(72.0, step.getY());
    assertEquals(84.5, step.getX());
  }

  @Test
  void stepTowardsHomeAppliesAngleOffsetWhenAttemptGreaterThanZero() {
    Location current = new Location(null, 100.0, 70.0, 0.0);
    Location home = new Location(null, 0.0, 70.0, 0.0);

    Location direct = ExplorationPlanner.findStepTowardsHome(current, home, 16.0, 0, loc -> true);
    Location angled = ExplorationPlanner.findStepTowardsHome(current, home, 16.0, 1, loc -> true);

    assertNotNull(direct);
    assertNotNull(angled);
    assertEquals(0.5, direct.getZ());
    assertNotEquals(direct.getZ(), angled.getZ(), "Angled attempt must deviate along Z");
  }

  @Test
  void stepTowardsHomeReturnsNullWhenNoSafeGround() {
    Location current = new Location(null, 100.0, 70.0, 0.0);
    Location home = new Location(null, 0.0, 70.0, 0.0);

    Location step = ExplorationPlanner.findStepTowardsHome(current, home, 16.0, 0, loc -> false);

    assertNull(step);
  }
}
