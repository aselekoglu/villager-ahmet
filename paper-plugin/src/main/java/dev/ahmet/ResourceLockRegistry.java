package dev.ahmet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Main-thread deterministic lock registry for block targets and chest transactions
 * preventing conflicting concurrent mutations across multiple villagers.
 */
public final class ResourceLockRegistry {

  public record CoordKey(String world, int x, int y, int z) {
    public CoordKey {
      if (world == null || world.isBlank()) {
        throw new IllegalArgumentException("world is required");
      }
    }
  }

  public record LockOwner(String villagerId, String taskId) {
    public LockOwner {
      if (villagerId == null || villagerId.isBlank()) {
        throw new IllegalArgumentException("villagerId is required");
      }
      if (taskId == null || taskId.isBlank()) {
        throw new IllegalArgumentException("taskId is required");
      }
    }
  }

  private final Map<CoordKey, LockOwner> blockLocks = new HashMap<>();
  private final Map<CoordKey, LockOwner> chestLocks = new HashMap<>();
  private final Map<String, CoordKey> villagerTargetBlocks = new HashMap<>();
  private final Set<CoordKey> pinnedBlocks = new HashSet<>();

  public synchronized boolean tryAcquireTargetBlock(String villagerId, String taskId, String world, int x, int y, int z) {
    CoordKey target = new CoordKey(world, x, y, z);
    LockOwner newOwner = new LockOwner(villagerId, taskId);

    LockOwner existing = blockLocks.get(target);
    if (existing != null && !existing.villagerId().equals(villagerId)) {
      return false;
    }

    CoordKey previous = villagerTargetBlocks.get(villagerId);
    if (previous != null && !previous.equals(target) && !pinnedBlocks.contains(previous)) {
      LockOwner prevOwner = blockLocks.get(previous);
      if (prevOwner != null && prevOwner.villagerId().equals(villagerId)) {
        blockLocks.remove(previous);
      }
    }

    villagerTargetBlocks.put(villagerId, target);
    blockLocks.put(target, newOwner);
    return true;
  }

  public synchronized boolean tryAcquireBlock(String villagerId, String taskId, String world, int x, int y, int z) {
    CoordKey key = new CoordKey(world, x, y, z);
    LockOwner newOwner = new LockOwner(villagerId, taskId);

    LockOwner existing = blockLocks.get(key);
    if (existing != null && !existing.villagerId().equals(villagerId)) {
      return false;
    }

    blockLocks.put(key, newOwner);
    return true;
  }

  public synchronized void pinRecoveryBlock(String villagerId, String taskId, String world, int x, int y, int z) {
    CoordKey key = new CoordKey(world, x, y, z);
    LockOwner owner = new LockOwner(villagerId, taskId);
    blockLocks.put(key, owner);
    pinnedBlocks.add(key);
  }

  public synchronized boolean isBlockLockedByOther(String villagerId, String world, int x, int y, int z) {
    CoordKey key = new CoordKey(world, x, y, z);
    LockOwner existing = blockLocks.get(key);
    return existing != null && !existing.villagerId().equals(villagerId);
  }

  public synchronized LockOwner blockOwner(String world, int x, int y, int z) {
    return blockLocks.get(new CoordKey(world, x, y, z));
  }

  public synchronized void releaseBlock(String villagerId, String world, int x, int y, int z) {
    CoordKey key = new CoordKey(world, x, y, z);
    LockOwner existing = blockLocks.get(key);
    if (existing != null && existing.villagerId().equals(villagerId)) {
      blockLocks.remove(key);
      pinnedBlocks.remove(key);
      CoordKey target = villagerTargetBlocks.get(villagerId);
      if (key.equals(target)) {
        villagerTargetBlocks.remove(villagerId);
      }
    }
  }

  public synchronized boolean tryAcquireChest(String villagerId, String taskId, List<CoordKey> chestCoords) {
    if (villagerId == null || villagerId.isBlank()) {
      throw new IllegalArgumentException("villagerId is required");
    }
    if (taskId == null || taskId.isBlank()) {
      throw new IllegalArgumentException("taskId is required");
    }
    if (chestCoords == null || chestCoords.isEmpty()) {
      throw new IllegalArgumentException("chestCoords must not be empty");
    }

    for (CoordKey coord : chestCoords) {
      LockOwner owner = chestLocks.get(coord);
      if (owner != null && !owner.villagerId().equals(villagerId)) {
        return false;
      }
    }

    LockOwner owner = new LockOwner(villagerId, taskId);
    for (CoordKey coord : chestCoords) {
      chestLocks.put(coord, owner);
    }
    return true;
  }

  public synchronized boolean isChestLockedByOther(String villagerId, List<CoordKey> chestCoords) {
    if (chestCoords == null || chestCoords.isEmpty()) {
      return false;
    }
    for (CoordKey coord : chestCoords) {
      LockOwner owner = chestLocks.get(coord);
      if (owner != null && !owner.villagerId().equals(villagerId)) {
        return true;
      }
    }
    return false;
  }

  public synchronized void releaseChestsForVillager(String villagerId) {
    if (villagerId == null) {
      return;
    }
    chestLocks.entrySet().removeIf(e -> e.getValue().villagerId().equals(villagerId));
  }

  public synchronized void releaseAllForVillager(String villagerId) {
    if (villagerId == null) {
      return;
    }
    releaseChestsForVillager(villagerId);
    blockLocks.entrySet().removeIf(e -> {
      if (e.getValue().villagerId().equals(villagerId)) {
        pinnedBlocks.remove(e.getKey());
        return true;
      }
      return false;
    });
    villagerTargetBlocks.remove(villagerId);
  }

  public synchronized void clear() {
    blockLocks.clear();
    chestLocks.clear();
    villagerTargetBlocks.clear();
    pinnedBlocks.clear();
  }
}
