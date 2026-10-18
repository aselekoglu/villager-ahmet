package dev.ahmet;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroundedPerceptionTest {
  @Test
  void scansOnlyLoadedCardinalSurfaceCellsAndStopsBeforeOpaqueWalls() {
    FakeBlocks blocks = new FakeBlocks();
    blocks.cells.put("1,70,0", new GroundedPerception.Cell(Material.STONE, false));
    blocks.cells.put("2,70,0", new GroundedPerception.Cell(Material.DIAMOND_ORE, false));
    GroundedPerception.Snapshot snapshot = GroundedPerception.capture(
        1_800_000_000_000L, 7, GroundedPerception.Phase.WANDER,
        new GroundedPerception.Origin(0, 70, 0), blocks, null, null);

    assertTrue(snapshot.blocks().sampledCells() <= 51);
    assertFalse(blocks.reads.contains("2,70,0"), "must not read the hidden ore behind the wall");
    assertFalse(blocks.reads.contains("1,71,0"), "an opaque feet cell stops before its head is read");
    assertFalse(blocks.reads.contains("1,69,0"), "an opaque feet cell stops before its floor is read");
    assertFalse(blocks.reads.contains("2,70,0"), "the feet obstruction leaves the ray only partially observed");
    assertFalse(blocks.reads.stream().anyMatch(key -> {
      String[] xyz = key.split(","); return Integer.parseInt(xyz[0]) != 0 && Integer.parseInt(xyz[2]) != 0;
    }), "must not read diagonal cells");
    assertTrue(snapshot.blocks().types().stream().noneMatch(type -> type.material().equals("DIAMOND_ORE")));
    assertTrue(snapshot.blocks().sampledCells() <= 64);
  }

  @Test
  void opaqueHeadStopsBeforeThatStepsFloorOrDownstreamCells() {
    FakeBlocks blocks = new FakeBlocks();
    blocks.cells.put("1,71,0", new GroundedPerception.Cell(Material.STONE, false));
    GroundedPerception.capture(100, 0, GroundedPerception.Phase.WAKE,
        new GroundedPerception.Origin(0, 70, 0), blocks, null, null);
    assertTrue(blocks.reads.contains("1,70,0"), "the passable feet cell is observed first");
    assertTrue(blocks.reads.contains("1,71,0"), "the opaque head cell itself is observed");
    assertFalse(blocks.reads.contains("1,69,0"), "the floor below an opaque head is not sampled");
    assertFalse(blocks.reads.contains("2,70,0"), "the ray stops at the opaque head");
  }

  @Test
  void checksHeightAndChunkLoadingBeforeEveryCellRead() {
    FakeBlocks blocks = new FakeBlocks();
    blocks.loaded = false;
    GroundedPerception.Snapshot snapshot = GroundedPerception.capture(
        100, 0, GroundedPerception.Phase.WAKE, new GroundedPerception.Origin(0, 0, 0), blocks, null, null);
    assertTrue(blocks.reads.isEmpty());
    assertEquals(0, snapshot.blocks().sampledCells());
    assertEquals(0, blocks.outOfHeightChecks);
  }

  @Test
  void generationRetainsLongJobGenerationWithoutNarrowing() {
    long generation = (long) Integer.MAX_VALUE + 1;
    GroundedPerception.Snapshot snapshot = GroundedPerception.capture(
        100, generation, GroundedPerception.Phase.WAKE, new GroundedPerception.Origin(0, 70, 0),
        new FakeBlocks(), null, null);
    assertEquals(generation, snapshot.generation());
    assertTrue(snapshot.toJson().contains("\"generation\":2147483648"));
  }

  @Test
  void loadedRayStopsAtFirstUnloadedChunkWithoutReadingDownstreamCells() {
    FakeBlocks blocks = new FakeBlocks();
    blocks.loadedChunk = (x, z) -> Math.floorDiv(x, 16) == 0;
    GroundedPerception.capture(100, 0, GroundedPerception.Phase.WAKE,
        new GroundedPerception.Origin(15, 70, 0), blocks, null, null);
    assertTrue(blocks.reads.contains("15,70,0"));
    assertFalse(blocks.reads.stream().anyMatch(key -> Integer.parseInt(key.split(",")[0]) >= 16),
        "no cell on the far side of the unloaded chunk may be read");
  }

  @Test
  void raysStopAtWorldHeightBoundaryBeforeReadingHeadOrFloorOutsideBounds() {
    FakeBlocks blocks = new FakeBlocks();
    blocks.min = 70;
    blocks.max = 72;
    GroundedPerception.capture(100, 0, GroundedPerception.Phase.WAKE,
        new GroundedPerception.Origin(0, 71, 0), blocks, null, null);
    assertTrue(blocks.reads.stream().allMatch(key -> {
      int y = Integer.parseInt(key.split(",")[1]); return y >= 70 && y < 72;
    }));
    assertFalse(blocks.reads.stream().anyMatch(key -> Integer.parseInt(key.split(",")[1]) == 72));
  }

  @Test
  void disabledOrUnauthorizedInventoryNeverReadsSlots() {
    FakeInventory inventory = new FakeInventory();
    inventory.enabled = true;
    inventory.authorized = false;
    GroundedPerception.Snapshot snapshot = captureWith(inventory, null);
    assertFalse(snapshot.inventory().available());
    assertTrue(snapshot.inventory().items().isEmpty());
    assertEquals(0, inventory.reads);

    inventory.authorized = true;
    inventory.enabled = false;
    captureWith(inventory, null);
    assertEquals(0, inventory.reads);
  }

  @Test
  void missingWrongWorldUnloadedOrDistantChestNeverReadsSlots() {
    FakeInventory inventory = new FakeInventory();
    inventory.enabled = true;
    inventory.authorized = true;
    inventory.bound = false;
    assertUnavailableWithoutSlotReads(inventory);
    inventory.bound = true;
    inventory.sameWorldValue = false;
    assertUnavailableWithoutSlotReads(inventory);
    inventory.sameWorldValue = true;
    inventory.loadedValue = false;
    assertUnavailableWithoutSlotReads(inventory);
    inventory.loadedValue = true;
    inventory.distance = 8.01;
    assertUnavailableWithoutSlotReads(inventory);
  }

  private static void assertUnavailableWithoutSlotReads(FakeInventory inventory) {
    GroundedPerception.Snapshot snapshot = captureWith(inventory, null);
    assertFalse(snapshot.inventory().available());
    assertTrue(snapshot.inventory().items().isEmpty());
    assertEquals(0, inventory.reads);
  }

  @Test
  void inventoryReadsAtMost54SlotsAndEmitsOnlyEightAggregatedMaterials() {
    FakeInventory inventory = new FakeInventory();
    inventory.enabled = true;
    inventory.authorized = true;
    inventory.size = 60;
    GroundedPerception.Snapshot tooLarge = captureWith(inventory, null);
    assertFalse(tooLarge.inventory().available());
    assertEquals(0, inventory.reads);

    inventory.size = 54;
    inventory.slots = new ArrayList<>();
    for (int i = 0; i < 54; i++) inventory.slots.add(new GroundedPerception.ItemSlot(Material.values()[i], 64));
    GroundedPerception.Snapshot bounded = captureWith(inventory, null);
    assertTrue(bounded.inventory().available());
    assertEquals(54, inventory.reads);
    assertEquals(8, bounded.inventory().items().size());
    assertTrue(bounded.toJson().length() < 16_384);
  }

  @Test
  void playerIteratorAndEmittedBandsAreBoundedAndAnonymous() {
    FakePlayers players = new FakePlayers();
    for (int i = 0; i < 40; i++) players.candidates.add(new GroundedPerception.PlayerView(true, i % 9, false, i != 0));
    GroundedPerception.Snapshot snapshot = GroundedPerception.capture(
        100, 1, GroundedPerception.Phase.WORK, new GroundedPerception.Origin(0, 70, 0),
        new FakeBlocks(), null, players);
    assertEquals(9, players.yielded, "scan stops once the eight visible observations are collected");
    assertEquals(8, snapshot.players().bands().size());
    assertTrue(snapshot.toJson().contains("\"near\""));
    assertFalse(snapshot.toJson().contains("uuid"));
    assertFalse(snapshot.toJson().contains("Alice"));
  }

  @Test
  void playerIteratorNeverRequestsMoreThan32Candidates() {
    FakePlayers players = new FakePlayers();
    for (int i = 0; i < 40; i++) players.candidates.add(new GroundedPerception.PlayerView(true, 9, false, true));
    GroundedPerception.Snapshot snapshot = GroundedPerception.capture(
        100, 1, GroundedPerception.Phase.WORK, new GroundedPerception.Origin(0, 70, 0),
        new FakeBlocks(), null, players);
    assertEquals(32, players.yielded);
    assertTrue(snapshot.players().bands().isEmpty());
  }

  private static GroundedPerception.Snapshot captureWith(FakeInventory inventory, FakePlayers players) {
    return GroundedPerception.capture(100, 0, GroundedPerception.Phase.UNKNOWN,
        new GroundedPerception.Origin(0, 70, 0), new FakeBlocks(), inventory, players);
  }

  private static final class FakeBlocks implements GroundedPerception.BlockAccess {
    final Set<String> reads = new HashSet<>();
    final java.util.Map<String, GroundedPerception.Cell> cells = new java.util.HashMap<>();
    boolean loaded = true;
    int min = 1, max = 255;
    java.util.function.BiPredicate<Integer, Integer> loadedChunk = (x, z) -> loaded;
    int outOfHeightChecks;
    @Override public int minHeight() { return min; }
    @Override public int maxHeight() { return max; }
    @Override public boolean isCellChunkLoaded(int x, int z) { return loadedChunk.test(x, z); }
    @Override public GroundedPerception.Cell readCell(int x, int y, int z) {
      if (y < min || y >= max) {
        outOfHeightChecks++;
        throw new AssertionError("read outside world height");
      }
      reads.add(x + "," + y + "," + z);
      return cells.getOrDefault(x + "," + y + "," + z,
          new GroundedPerception.Cell(Material.AIR, true));
    }
  }

  private static final class FakeInventory implements GroundedPerception.InventoryAccess {
    boolean enabled, authorized;
    boolean bound = true, sameWorldValue = true, loadedValue = true;
    double distance = 2;
    int size = 27, reads;
    List<GroundedPerception.ItemSlot> slots = List.of();
    @Override public boolean enabled() { return enabled; }
    @Override public boolean authorized() { return authorized; }
    @Override public boolean boundChestPresent() { return bound; }
    @Override public boolean sameWorld() { return sameWorldValue; }
    @Override public boolean loaded() { return loadedValue; }
    @Override public double distanceBlocks() { return distance; }
    @Override public int size() { return size; }
    @Override public GroundedPerception.ItemSlot readSlot(int slot) { reads++; return slots.get(slot); }
  }

  private static final class FakePlayers implements GroundedPerception.PlayerAccess {
    final List<GroundedPerception.PlayerView> candidates = new ArrayList<>();
    int yielded;
    @Override public boolean enabled() { return true; }
    @Override public boolean viewerContextAvailable() { return true; }
    @Override public Iterable<GroundedPerception.PlayerView> onlineCandidates() {
      return () -> new java.util.Iterator<>() {
        int index;
        @Override public boolean hasNext() { return index < candidates.size(); }
        @Override public GroundedPerception.PlayerView next() { yielded++; return candidates.get(index++); }
      };
    }
  }
}
