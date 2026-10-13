package dev.ahmet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Main-thread-friendly coordinate cursor; callers inspect at most the requested block budget per tick. */
final class IncrementalTargetScanner {
  private String world, preference;
  private long generation;
  private int ox, oy, oz, radius, minY, maxY, x, z, y;
  private boolean complete;
  private List<Point> results = List.of();
  private final List<Point> visitedPoints = new ArrayList<>();

  void begin(String world, String preference, long generation, int ox, int oy, int oz,
      int radius, int minY, int maxY) {
    if (Objects.equals(this.world, world) && Objects.equals(this.preference, preference)
        && this.generation == generation && this.radius == radius && this.minY == minY && this.maxY == maxY) return;
    this.world = world; this.preference = preference; this.generation = generation;
    this.ox = ox; this.oy = oy; this.oz = oz; this.radius = radius; this.minY = minY; this.maxY = maxY;
    x = -radius; z = -radius; y = minY; complete = false; results = List.of(); visitedPoints.clear();
  }

  List<Point> advance(int budget) {
    if (budget < 1) throw new IllegalArgumentException("Scan budget must be positive");
    List<Point> batch = new ArrayList<>(budget);
    while (!complete && batch.size() < budget) {
      if (x * x + z * z <= radius * radius) batch.add(new Point(ox + x, oy + y, oz + z));
      if (++y > maxY) {
        y = minY;
        if (++z > radius) { z = -radius; if (++x > radius) complete = true; }
      }
    }
    visitedPoints.addAll(batch);
    if (complete) results = List.copyOf(visitedPoints);
    return List.copyOf(batch);
  }

  void invalidate() { world = null; preference = null; complete = false; visitedPoints.clear(); results = List.of(); }
  boolean complete() { return complete; }
  int visited() { return visitedPoints.size(); }
  List<Point> resultsIfComplete() { return complete ? results : null; }

  record Point(int x, int y, int z) {}
}
