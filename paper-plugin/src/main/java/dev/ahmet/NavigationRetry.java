package dev.ahmet;

final class NavigationRetry {
  private static final long RETRY_DELAY_MILLIS = 2_000;
  private long restartAtMillis = -1;

  void schedule(long nowMillis) {
    if (nowMillis < 0) throw new IllegalArgumentException("Invalid navigation retry time");
    restartAtMillis = nowMillis + RETRY_DELAY_MILLIS;
  }

  boolean pending() {
    return restartAtMillis >= 0;
  }

  boolean consumeIfDue(long nowMillis) {
    if (nowMillis < 0) throw new IllegalArgumentException("Invalid navigation retry time");
    if (!pending() || nowMillis < restartAtMillis) return false;
    clear();
    return true;
  }

  void clear() {
    restartAtMillis = -1;
  }
}
