package dev.ahmet;

import java.util.LinkedHashMap;
import java.util.Map;

/** Temporarily suppresses blocks whose pathfinding failed so JEV can consider other targets. */
final class FailedTargets {
  private static final long COOLDOWN_MILLIS = 60_000;
  private static final int MAX_ENTRIES = 64;
  private final Map<Key, Long> failedUntil = new LinkedHashMap<>();

  void mark(String world, int x, int y, int z, long nowMillis) {
    if (world == null || world.isBlank() || nowMillis < 0) throw new IllegalArgumentException("Invalid failed target");
    prune(nowMillis);
    Key key = new Key(world, x, y, z);
    if (!failedUntil.containsKey(key) && failedUntil.size() >= MAX_ENTRIES) {
      failedUntil.remove(failedUntil.keySet().iterator().next());
    }
    failedUntil.put(key, nowMillis + COOLDOWN_MILLIS);
  }

  boolean contains(String world, int x, int y, int z, long nowMillis) {
    if (world == null || world.isBlank() || nowMillis < 0) return false;
    prune(nowMillis);
    return failedUntil.containsKey(new Key(world, x, y, z));
  }

  void clear() {
    failedUntil.clear();
  }

  private void prune(long nowMillis) {
    failedUntil.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
  }

  private record Key(String world, int x, int y, int z) {}
}
