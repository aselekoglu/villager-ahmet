package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

final class ResourceLockRegistryTest {

  private ResourceLockRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ResourceLockRegistry();
  }

  @Test
  void recordValidations() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceLockRegistry.CoordKey(null, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new ResourceLockRegistry.CoordKey("", 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new ResourceLockRegistry.CoordKey("   ", 0, 0, 0));

    assertThrows(IllegalArgumentException.class, () -> new ResourceLockRegistry.LockOwner(null, "task1"));
    assertThrows(IllegalArgumentException.class, () -> new ResourceLockRegistry.LockOwner("", "task1"));
    assertThrows(IllegalArgumentException.class, () -> new ResourceLockRegistry.LockOwner("ahmet", null));
    assertThrows(IllegalArgumentException.class, () -> new ResourceLockRegistry.LockOwner("ahmet", ""));
  }

  @Test
  void targetBlockAcquisitionAndReleaseOfPreviousTarget() {
    assertTrue(registry.tryAcquireTargetBlock("ahmet", "taskA", "world", 10, 64, 20));
    assertEquals("ahmet", registry.blockOwner("world", 10, 64, 20).villagerId());

    // Another villager cannot acquire it
    assertFalse(registry.tryAcquireTargetBlock("mehmet", "taskB", "world", 10, 64, 20));
    assertTrue(registry.isBlockLockedByOther("mehmet", "world", 10, 64, 20));
    assertFalse(registry.isBlockLockedByOther("ahmet", "world", 10, 64, 20));

    // Ahmet acquires a new target block -> previous block (10, 64, 20) is released
    assertTrue(registry.tryAcquireTargetBlock("ahmet", "taskA", "world", 11, 64, 21));
    assertNull(registry.blockOwner("world", 10, 64, 20));
    assertNotNull(registry.blockOwner("world", 11, 64, 21));

    // Now Mehmet can acquire the released block (10, 64, 20)
    assertTrue(registry.tryAcquireTargetBlock("mehmet", "taskB", "world", 10, 64, 20));
  }

  @Test
  void pinnedRecoveryBlockIsNotReleasedByTargetSwitch() {
    registry.pinRecoveryBlock("ahmet", "taskA", "world", 5, 64, 5);
    assertTrue(registry.isBlockLockedByOther("mehmet", "world", 5, 64, 5));

    // Ahmet acquires another target block
    assertTrue(registry.tryAcquireTargetBlock("ahmet", "taskA", "world", 6, 64, 6));

    // Pinned block (5, 64, 5) remains locked!
    assertNotNull(registry.blockOwner("world", 5, 64, 5));
    assertEquals("ahmet", registry.blockOwner("world", 5, 64, 5).villagerId());
  }

  @Test
  void tryAcquireBlockWithoutTargetRelease() {
    assertTrue(registry.tryAcquireBlock("ahmet", "taskA", "world", 1, 64, 1));
    assertTrue(registry.tryAcquireBlock("ahmet", "taskA", "world", 2, 64, 2));

    // Both remain locked
    assertNotNull(registry.blockOwner("world", 1, 64, 1));
    assertNotNull(registry.blockOwner("world", 2, 64, 2));

    // Conflict for another villager
    assertFalse(registry.tryAcquireBlock("mehmet", "taskB", "world", 1, 64, 1));

    // Releasing block 1
    registry.releaseBlock("ahmet", "world", 1, 64, 1);
    assertNull(registry.blockOwner("world", 1, 64, 1));
    assertNotNull(registry.blockOwner("world", 2, 64, 2));
  }

  @Test
  void chestLockingSingleAndDoubleChestAtomic() {
    var c1 = new ResourceLockRegistry.CoordKey("world", 100, 64, 100);
    var c2 = new ResourceLockRegistry.CoordKey("world", 101, 64, 100);

    // Mehmet locks c2
    assertTrue(registry.tryAcquireChest("mehmet", "taskM", List.of(c2)));
    assertTrue(registry.isChestLockedByOther("ahmet", List.of(c2)));
    assertTrue(registry.isChestLockedByOther("ahmet", List.of(c1, c2)));
    assertFalse(registry.isChestLockedByOther("ahmet", List.of(c1)));

    // Ahmet attempts double chest [c1, c2] -> fails atomically, c1 is NOT locked
    assertFalse(registry.tryAcquireChest("ahmet", "taskA", List.of(c1, c2)));
    assertFalse(registry.isChestLockedByOther("mehmet", List.of(c1)));

    // Mehmet releases chests
    registry.releaseChestsForVillager("mehmet");
    assertFalse(registry.isChestLockedByOther("ahmet", List.of(c1, c2)));

    // Now Ahmet can acquire both [c1, c2]
    assertTrue(registry.tryAcquireChest("ahmet", "taskA", List.of(c1, c2)));
    assertTrue(registry.isChestLockedByOther("mehmet", List.of(c1, c2)));
  }

  @Test
  void releaseAllAndClear() {
    registry.tryAcquireTargetBlock("ahmet", "taskA", "world", 1, 64, 1);
    registry.pinRecoveryBlock("ahmet", "taskA", "world", 2, 64, 2);
    registry.tryAcquireChest("ahmet", "taskA", List.of(new ResourceLockRegistry.CoordKey("world", 10, 64, 10)));

    registry.tryAcquireTargetBlock("mehmet", "taskB", "world", 3, 64, 3);

    // Release all for Ahmet
    registry.releaseAllForVillager("ahmet");
    assertNull(registry.blockOwner("world", 1, 64, 1));
    assertNull(registry.blockOwner("world", 2, 64, 2));
    assertFalse(registry.isChestLockedByOther("mehmet", List.of(new ResourceLockRegistry.CoordKey("world", 10, 64, 10))));

    // Mehmet's block is still locked
    assertNotNull(registry.blockOwner("world", 3, 64, 3));

    // Clear everything
    registry.clear();
    assertNull(registry.blockOwner("world", 3, 64, 3));
  }
}
