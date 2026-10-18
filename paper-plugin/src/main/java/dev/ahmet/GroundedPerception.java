package dev.ahmet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds a bounded, read-only observation from injected access seams. */
public final class GroundedPerception {
  private static final int RADIUS = 4;
  private static final int MAX_CELLS = 64;
  private static final int MAX_TYPES = 8;
  private static final int MAX_CHEST_SLOTS = 54;
  private static final int MAX_ITEMS = 3456;
  private static final int MAX_PLAYER_CANDIDATES = 32;
  private static final int MAX_PLAYERS = 8;
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private GroundedPerception() {}

  public enum Phase { WAKE, WORK, WANDER, SLEEP, UNKNOWN }

  public record Origin(int x, int y, int z) {}
  public record Cell(Material material, boolean passable) {
    public Cell { Objects.requireNonNull(material, "material"); }
  }
  public record ItemSlot(Material material, int amount) {}
  public record PlayerView(boolean sameWorld, double distanceBlocks, boolean invisible, boolean visibleToViewer) {}
  public record TypeCount(String material, int count) {}
  public record BlockSnapshot(int sampledCells, List<TypeCount> types) {
    public BlockSnapshot { types = List.copyOf(types); }
  }
  public record InventorySnapshot(boolean enabled, boolean available, List<TypeCount> items) {
    public InventorySnapshot { items = List.copyOf(items); }
  }
  public record PlayerSnapshot(boolean enabled, boolean available, List<String> bands) {
    public PlayerSnapshot { bands = List.copyOf(bands); }
  }
  public record Snapshot(int version, boolean available, long capturedAt, long generation, Phase phase,
                         BlockSnapshot blocks, InventorySnapshot inventory, PlayerSnapshot players) {
    public Snapshot {
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(blocks, "blocks");
      Objects.requireNonNull(inventory, "inventory");
      Objects.requireNonNull(players, "players");
    }
    public String toJson() { return GSON.toJson(this); }
  }

  /** These interfaces expose only the facts the snapshot needs; implementations must not load chunks. */
  public interface BlockAccess {
    int minHeight();
    int maxHeight();
    /** Accepts block x/z coordinates; the implementation converts them to chunk coordinates. */
    boolean isCellChunkLoaded(int blockX, int blockZ);
    Cell readCell(int x, int y, int z);
  }
  public interface InventoryAccess {
    boolean enabled();
    boolean authorized();
    boolean boundChestPresent();
    boolean sameWorld();
    boolean loaded();
    double distanceBlocks();
    int size();
    ItemSlot readSlot(int slot);
  }
  public interface PlayerAccess {
    boolean enabled();
    boolean viewerContextAvailable();
    Iterable<PlayerView> onlineCandidates();
  }

  public static Snapshot capture(long capturedAt, long generation, Phase phase, Origin origin,
                                 BlockAccess blocks, InventoryAccess inventory, PlayerAccess players) {
    if (generation < 0) throw new IllegalArgumentException("generation must be nonnegative");
    Objects.requireNonNull(origin, "origin");
    Phase safePhase = phase == null ? Phase.UNKNOWN : phase;
    BlockSnapshot blockSnapshot = scanBlocks(origin, blocks);
    InventorySnapshot inventorySnapshot = scanInventory(inventory);
    PlayerSnapshot playerSnapshot = scanPlayers(players);
    return new Snapshot(1, blocks != null, capturedAt, generation, safePhase,
        blockSnapshot, inventorySnapshot, playerSnapshot);
  }

  private static BlockSnapshot scanBlocks(Origin origin, BlockAccess access) {
    if (access == null) return new BlockSnapshot(0, List.of());
    Map<Material, Integer> counts = new HashMap<>();
    Set<CellPosition> read = new HashSet<>();
    int sampled = 0;
    int min = access.minHeight();
    int max = access.maxHeight();
    if (min >= max) return new BlockSnapshot(0, List.of());

    int[] originYs = {origin.y() - 1, origin.y(), origin.y() + 1};
    for (int y : originYs) {
      Cell cell = readCell(access, read, origin.x(), y, origin.z(), min, max);
      if (cell == null) continue;
      sampled++;
      count(counts, cell.material());
    }

    int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    for (int[] direction : directions) {
      for (int distance = 1; distance <= RADIUS && sampled < MAX_CELLS; distance++) {
        Integer x = offset(origin.x(), direction[0] * distance);
        Integer z = offset(origin.z(), direction[1] * distance);
        if (x == null || z == null) break;
        Cell feet = readCell(access, read, x, origin.y(), z, min, max);
        if (feet == null) break;
        sampled++;
        count(counts, feet.material());
        if (!feet.passable()) break;

        Cell head = readCell(access, read, x, origin.y() + 1, z, min, max);
        if (head == null) break;
        sampled++;
        count(counts, head.material());
        if (!head.passable()) break;

        Cell floor = readCell(access, read, x, origin.y() - 1, z, min, max);
        if (floor == null) break;
        sampled++;
        count(counts, floor.material());
      }
    }
    return new BlockSnapshot(sampled, topTypes(counts));
  }

  private static Cell readCell(BlockAccess access, Set<CellPosition> read, int x, int y, int z, int min, int max) {
    if (y < min || y >= max || read.size() >= MAX_CELLS) return null;
    CellPosition position = new CellPosition(x, y, z);
    if (read.contains(position)) return null;
    if (!access.isCellChunkLoaded(x, z)) return null;
    Cell cell = access.readCell(x, y, z);
    read.add(position);
    return cell;
  }

  private static Integer offset(int value, int delta) {
    long result = (long) value + delta;
    return result < Integer.MIN_VALUE || result > Integer.MAX_VALUE ? null : (int) result;
  }

  private static void count(Map<Material, Integer> counts, Material material) {
    if (!isAir(material)) counts.merge(material, 1, Integer::sum);
  }

  private static boolean isAir(Material material) {
    String name = material.name();
    return name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR");
  }

  private static InventorySnapshot scanInventory(InventoryAccess access) {
    if (access == null || !access.enabled()) return new InventorySnapshot(false, false, List.of());
    if (!access.authorized() || !access.boundChestPresent() || !access.sameWorld() || !access.loaded()) {
      return new InventorySnapshot(true, false, List.of());
    }
    double distance = access.distanceBlocks();
    if (!Double.isFinite(distance) || distance < 0 || distance > 8) return new InventorySnapshot(true, false, List.of());
    int size = access.size();
    if (size < 0 || size > MAX_CHEST_SLOTS) return new InventorySnapshot(true, false, List.of());
    Map<Material, Integer> counts = new HashMap<>();
    int total = 0;
    for (int slot = 0; slot < size; slot++) {
      ItemSlot item = access.readSlot(slot);
      if (item == null || item.amount() < 0 || item.amount() > 64) return new InventorySnapshot(true, false, List.of());
      if (item.amount() == 0) continue;
      if (item.material() == null) return new InventorySnapshot(true, false, List.of());
      if (isAir(item.material())) continue;
      total += item.amount();
      if (total > MAX_ITEMS) return new InventorySnapshot(true, false, List.of());
      counts.merge(item.material(), item.amount(), Integer::sum);
    }
    return new InventorySnapshot(true, true, topTypes(counts));
  }

  private static PlayerSnapshot scanPlayers(PlayerAccess access) {
    if (access == null || !access.enabled()) return new PlayerSnapshot(false, false, List.of());
    if (!access.viewerContextAvailable()) return new PlayerSnapshot(true, false, List.of());
    Iterable<PlayerView> iterable = access.onlineCandidates();
    if (iterable == null) return new PlayerSnapshot(true, false, List.of());
    List<String> bands = new ArrayList<>();
    int candidates = 0;
    java.util.Iterator<PlayerView> iterator = iterable.iterator();
    while (candidates < MAX_PLAYER_CANDIDATES && bands.size() < MAX_PLAYERS && iterator.hasNext()) {
      PlayerView player = iterator.next();
      candidates++;
      if (player == null || !player.sameWorld() || player.invisible() || !player.visibleToViewer()) continue;
      double distance = player.distanceBlocks();
      if (!Double.isFinite(distance) || distance < 0 || distance > 8) continue;
      bands.add(distance <= 4 ? "near" : "nearby");
    }
    return new PlayerSnapshot(true, true, bands);
  }

  private static List<TypeCount> topTypes(Map<Material, Integer> counts) {
    return counts.entrySet().stream()
        .sorted(Comparator.<Map.Entry<Material, Integer>>comparingInt(Map.Entry::getValue).reversed()
            .thenComparing(entry -> entry.getKey().name()))
        .limit(MAX_TYPES)
        .map(entry -> new TypeCount(entry.getKey().name(), entry.getValue()))
        .toList();
  }

  private record CellPosition(int x, int y, int z) {}
}
