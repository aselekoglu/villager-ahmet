package dev.ahmet;

final class NavigationWatchdog {
  private static final long STALL_TIMEOUT_MILLIS = 15_000;
  private static final double PROGRESS_EPSILON_SQUARED = 0.25;
  private final double arrivalDistanceSquared;
  private static final int MAX_STALLED_ATTEMPTS = 3;

  enum Outcome { PROGRESS, WAIT, RETRY, GIVE_UP, REACHED }

  private double bestDistanceSquared;
  private long lastProgressAtMillis;
  private int stalledAttempts;
  private boolean initialized;

  NavigationWatchdog() { this(6.25); }

  NavigationWatchdog(double arrivalDistanceSquared) {
    if (!Double.isFinite(arrivalDistanceSquared) || arrivalDistanceSquared < 0) {
      throw new IllegalArgumentException("Invalid navigation arrival threshold");
    }
    this.arrivalDistanceSquared = arrivalDistanceSquared;
  }

  Outcome observe(double distanceSquared, long nowMillis) {
    if (!Double.isFinite(distanceSquared) || distanceSquared < 0 || nowMillis < 0) {
      throw new IllegalArgumentException("Invalid navigation observation");
    }
    if (distanceSquared <= arrivalDistanceSquared) {
      reset();
      return Outcome.REACHED;
    }
    if (!initialized) {
      initialized = true;
      bestDistanceSquared = distanceSquared;
      lastProgressAtMillis = nowMillis;
      return Outcome.PROGRESS;
    }
    if (distanceSquared + PROGRESS_EPSILON_SQUARED < bestDistanceSquared) {
      bestDistanceSquared = distanceSquared;
      lastProgressAtMillis = nowMillis;
      return Outcome.PROGRESS;
    }
    if (nowMillis - lastProgressAtMillis < STALL_TIMEOUT_MILLIS) return Outcome.WAIT;

    stalledAttempts++;
    bestDistanceSquared = distanceSquared;
    lastProgressAtMillis = nowMillis;
    if (stalledAttempts >= MAX_STALLED_ATTEMPTS) {
      stalledAttempts = 0;
      return Outcome.GIVE_UP;
    }
    return Outcome.RETRY;
  }

  void reset() {
    initialized = false;
    bestDistanceSquared = Double.POSITIVE_INFINITY;
    lastProgressAtMillis = 0;
    stalledAttempts = 0;
  }
}
