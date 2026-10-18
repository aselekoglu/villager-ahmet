package dev.ahmet;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.function.Supplier;

/** Main-thread-only bridge from Bukkit/Citizens into the bounded, read-only perception DTO. */
final class BukkitPerception {
  private BukkitPerception() {}

  static GroundedPerception.Snapshot capture(NPC npc, Player viewer, Location boundChest,
      boolean includeInventory, boolean includePlayers, long generation) {
    return capture(Bukkit.isPrimaryThread(), npc, viewer, boundChest, true, includeInventory, includePlayers,
        generation, System.currentTimeMillis());
  }

  static GroundedPerception.Snapshot capture(NPC npc, Player viewer, Location boundChest,
      boolean enabled, boolean includeInventory, boolean includePlayers, long generation) {
    return capture(Bukkit.isPrimaryThread(), npc, viewer, boundChest, enabled, includeInventory, includePlayers,
        generation, System.currentTimeMillis());
  }

  /** The boolean overload is a narrow seam for proving that off-thread calls touch no Bukkit objects. */
  static GroundedPerception.Snapshot capture(boolean primaryThread, NPC npc, Player viewer, Location boundChest,
      boolean includeInventory, boolean includePlayers, long generation, long capturedAt) {
    return capture(primaryThread, npc, viewer, boundChest, true, includeInventory, includePlayers, generation, capturedAt);
  }

  static GroundedPerception.Snapshot capture(boolean primaryThread, NPC npc, Player viewer, Location boundChest,
      boolean enabled, boolean includeInventory, boolean includePlayers, long generation, long capturedAt) {
    return capture(primaryThread, npc, viewer, boundChest, enabled, includeInventory, includePlayers,
        generation, capturedAt, Bukkit::getOnlinePlayers);
  }

  static GroundedPerception.Snapshot capture(boolean primaryThread, NPC npc, Player viewer, Location boundChest,
      boolean enabled, boolean includeInventory, boolean includePlayers, long generation, long capturedAt,
      Supplier<? extends Iterable<? extends Player>> onlinePlayers) {
    if (!primaryThread || !enabled || npc == null || !npc.isSpawned()) return unavailable(generation, capturedAt);
    var entity = npc.getEntity();
    if (entity == null || entity.getWorld() == null) return unavailable(generation, capturedAt);
    Location origin = entity.getLocation();
    if (origin == null || origin.getWorld() == null) return unavailable(generation, capturedAt);
    World world = origin.getWorld();
    int x = origin.getBlockX(), y = origin.getBlockY(), z = origin.getBlockZ();
    GroundedPerception.Phase phase = phase(world.getTime());
    GroundedPerception.BlockAccess blocks = enabled ? new GroundedPerception.BlockAccess() {
      @Override public int minHeight() { return world.getMinHeight(); }
      @Override public int maxHeight() { return world.getMaxHeight(); }
      @Override public boolean isCellChunkLoaded(int blockX, int blockZ) {
        return world.isChunkLoaded(blockToChunk(blockX), blockToChunk(blockZ));
      }
      @Override public GroundedPerception.Cell readCell(int bx, int by, int bz) {
        Block block = world.getBlockAt(bx, by, bz);
        return new GroundedPerception.Cell(block.getType(), block.isPassable());
      }
    } : null;
    GroundedPerception.InventoryAccess inventory = inventoryAccess(world, origin, viewer, boundChest, enabled && includeInventory);
    GroundedPerception.PlayerAccess players = playerAccess(world, origin, viewer, enabled && includePlayers, onlinePlayers);
    return GroundedPerception.capture(capturedAt, generation, phase, new GroundedPerception.Origin(x, y, z),
        blocks, inventory, players);
  }

  static int blockToChunk(int coordinate) { return coordinate >> 4; }

  private static GroundedPerception.Snapshot unavailable(long generation, long capturedAt) {
    return GroundedPerception.capture(capturedAt, Math.max(0, generation), GroundedPerception.Phase.UNKNOWN,
        new GroundedPerception.Origin(0, 0, 0), null, null, null);
  }

  private static GroundedPerception.Phase phase(long worldTime) {
    return switch (VillagerSchedule.phaseAt(worldTime)) {
      case WAKE -> GroundedPerception.Phase.WAKE;
      case WORK -> GroundedPerception.Phase.WORK;
      case WANDER -> GroundedPerception.Phase.WANDER;
      case SLEEP -> GroundedPerception.Phase.SLEEP;
    };
  }

  private static GroundedPerception.InventoryAccess inventoryAccess(World world, Location origin,
      Player viewer, Location boundChest, boolean enabled) {
    return new GroundedPerception.InventoryAccess() {
      private Inventory resolved;
      @Override public boolean enabled() { return enabled; }
      @Override public boolean authorized() {
        return enabled && viewer != null && viewer.isOnline() && viewer.getWorld().equals(world)
            && viewer.hasPermission("ahmet.admin");
      }
      @Override public boolean boundChestPresent() {
        return authorized() && boundChest != null && boundChest.getWorld() != null;
      }
      @Override public boolean sameWorld() { return boundChestPresent() && boundChest.getWorld().equals(world); }
      @Override public boolean loaded() {
        if (!sameWorld() || distanceBlocks() > 8) return false;
        int cx = boundChest.getBlockX(), cz = boundChest.getBlockZ();
        if (!world.isChunkLoaded(cx >> 4, cz >> 4)) return false;
        // A double chest can ask Bukkit for a neighboring half; preflight all cardinal partner chunks.
        return world.isChunkLoaded((cx - 1) >> 4, cz >> 4)
            && world.isChunkLoaded((cx + 1) >> 4, cz >> 4)
            && world.isChunkLoaded(cx >> 4, (cz - 1) >> 4)
            && world.isChunkLoaded(cx >> 4, (cz + 1) >> 4);
      }
      @Override public double distanceBlocks() { return sameWorld() ? origin.distance(boundChest) : Double.POSITIVE_INFINITY; }
      @Override public int size() {
        if (!loaded() || distanceBlocks() > 8) return -1;
        int bx = boundChest.getBlockX(), by = boundChest.getBlockY(), bz = boundChest.getBlockZ();
        if (by < world.getMinHeight() || by >= world.getMaxHeight()) return -1;
        Block block = world.getBlockAt(bx, by, bz);
        if (!(block.getState() instanceof Chest chest)) return -1;
        resolved = chest.getInventory();
        return resolved.getSize();
      }
      @Override public GroundedPerception.ItemSlot readSlot(int slot) {
        if (resolved == null || slot < 0 || slot >= resolved.getSize()) return null;
        return itemSlot(resolved.getItem(slot));
      }
    };
  }

  static GroundedPerception.ItemSlot itemSlot(ItemStack item) {
    return item == null ? new GroundedPerception.ItemSlot(Material.AIR, 0)
        : new GroundedPerception.ItemSlot(item.getType(), item.getAmount());
  }

  private static GroundedPerception.PlayerAccess playerAccess(World world, Location origin,
      Player viewer, boolean enabled, Supplier<? extends Iterable<? extends Player>> onlinePlayers) {
    return new GroundedPerception.PlayerAccess() {
      @Override public boolean enabled() { return enabled; }
      @Override public boolean viewerContextAvailable() {
        return enabled && viewer != null && viewer.isOnline() && viewer.getWorld().equals(world);
      }
      @Override public Iterable<GroundedPerception.PlayerView> onlineCandidates() {
        if (!viewerContextAvailable()) return null;
        return () -> new Iterator<>() {
          private final Iterator<? extends Player> candidates = onlinePlayers.get().iterator();
          @Override public boolean hasNext() { return candidates.hasNext(); }
          @Override public GroundedPerception.PlayerView next() {
            Player candidate = candidates.next();
            if (candidate == viewer) return new GroundedPerception.PlayerView(false, 0, true, false);
            boolean sameWorld = candidate.getWorld().equals(world);
            if (!sameWorld) return new GroundedPerception.PlayerView(false, Double.POSITIVE_INFINITY, false, false);
            boolean invisible = candidate.isInvisible();
            boolean visible = viewer.canSee(candidate);
            if (invisible || !visible) return new GroundedPerception.PlayerView(true, Double.POSITIVE_INFINITY, invisible, visible);
            double distance = candidate.getLocation().distance(origin);
            return new GroundedPerception.PlayerView(sameWorld, distance, invisible, visible);
          }
        };
      }
    };
  }
}
