package dev.ahmet;

/** Bounded policy for delivering collected cargo after nearby routes are exhausted. */
final class PartialDeliveryPolicy {
  private static final int MAX_UNREACHABLE_ROUTES = 3;
  private static final int MAX_EMPTY_SCANS = 2;
  private int unreachableRoutes;
  private int emptyScans;

  boolean unreachableRoute(boolean carryingCargo) {
    if (!carryingCargo) {
      unreachableRoutes = 0;
      return false;
    }
    return ++unreachableRoutes >= MAX_UNREACHABLE_ROUTES;
  }

  boolean emptyScan(boolean carryingCargo) {
    if (!carryingCargo) {
      emptyScans = 0;
      return false;
    }
    return ++emptyScans >= MAX_EMPTY_SCANS;
  }

  void targetsFound() { emptyScans = 0; }

  void harvested() {
    unreachableRoutes = 0;
    emptyScans = 0;
  }

  void reset() {
    harvested();
  }
}
