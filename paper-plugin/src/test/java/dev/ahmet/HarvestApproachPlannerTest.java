package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

final class HarvestApproachPlannerTest {
  @Test
  void selectsClosestSafeStandingCellWithinHarvestReach() {
    Location targetBlock = new Location(null, 10, 64, 10);
    Location npc = new Location(null, 10.5, 64, 8.5);

    // Standing cells around target:
    // (10.5, 64, 8.5) is at distance 1.5 blocks from target (10.5, 64, 10.5).
    // (10.5, 64, 12.5) is at distance 2.0 blocks from target.
    Location cellNearNpc = new Location(null, 10.5, 64, 9.5);
    Location cellFarFromNpc = new Location(null, 10.5, 64, 11.5);

    Set<Location> safeCells = Set.of(cellNearNpc, cellFarFromNpc);

    Location chosen = HarvestApproachPlanner.findApproach(npc, targetBlock, safeCells::contains);
    assertEquals(cellNearNpc, chosen);

    Location targetLoc = NavigationTarget.approachFor(targetBlock, npc, safeCells::contains);
    assertEquals(cellNearNpc, targetLoc);
  }

  @Test
  void fallsBackToNullWhenNoCellIsSafe() {
    Location targetBlock = new Location(null, 10, 64, 10);
    Location npc = new Location(null, 5, 64, 5);

    Location chosen = HarvestApproachPlanner.findApproach(npc, targetBlock, loc -> false);
    assertNull(chosen);

    // NavigationTarget.approachFor falls back to fromBlock(targetBlock)
    Location targetLoc = NavigationTarget.approachFor(targetBlock, npc, loc -> false);
    assertEquals(NavigationTarget.fromBlock(targetBlock), targetLoc);
  }
}
