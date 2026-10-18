package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class NavigationWatchdogTest {
  @Test
  void scheduleSpecificThresholdCanDisableBroadArrivalSoAdapterChecksCenteredCell() {
    NavigationWatchdog watchdog = new NavigationWatchdog(0.0);
    assertEquals(NavigationWatchdog.Outcome.PROGRESS, watchdog.observe(1.0, 0));
  }

  @Test
  void progressResetsTheStallDeadlineAndNearbyTargetsComplete() {
    NavigationWatchdog watchdog = new NavigationWatchdog();

    assertEquals(NavigationWatchdog.Outcome.PROGRESS, watchdog.observe(100, 1_000));
    assertEquals(NavigationWatchdog.Outcome.PROGRESS, watchdog.observe(90, 12_000));
    assertEquals(NavigationWatchdog.Outcome.WAIT, watchdog.observe(90, 26_999));
    assertEquals(NavigationWatchdog.Outcome.RETRY, watchdog.observe(90, 27_000));
    assertEquals(NavigationWatchdog.Outcome.REACHED, watchdog.observe(4, 28_000));
  }

  @Test
  void repeatedStallsAreBoundedAndRequestAbandonment() {
    NavigationWatchdog watchdog = new NavigationWatchdog();

    assertEquals(NavigationWatchdog.Outcome.PROGRESS, watchdog.observe(100, 0));
    assertEquals(NavigationWatchdog.Outcome.RETRY, watchdog.observe(100, 15_000));
    assertEquals(NavigationWatchdog.Outcome.RETRY, watchdog.observe(100, 30_000));
    assertEquals(NavigationWatchdog.Outcome.GIVE_UP, watchdog.observe(100, 45_000));
  }
}
