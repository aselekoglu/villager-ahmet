package dev.ahmet;

import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

final class NavigationTargetTest {
  @Test void blockOriginIsCenteredOnceAndPlannerTargetIsPreserved() {
    Location block = new Location(null, 4, 70, -3);
    assertEquals(new Location(null, 4.5, 70, -2.5), NavigationTarget.fromBlock(block));
    Location planned = new Location(null, 8.5, 72, 1.5);
    assertEquals(planned, NavigationTarget.fromPlanner(planned));
  }
}
