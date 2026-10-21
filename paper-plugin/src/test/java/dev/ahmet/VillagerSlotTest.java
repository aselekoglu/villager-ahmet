package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ahmet.protection.CompositeProtectionHook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

final class VillagerSlotTest {

  @TempDir
  Path tempDir;

  @Test
  void createDefaultInitializesExpectedFields() {
    Path journalPath = tempDir.resolve("operations-mehmet.json");
    VillagerSlot slot = VillagerSlot.createDefault(
        "mehmet",
        "Mehmet",
        VillagerRole.FARMER,
        journalPath,
        new CompositeProtectionHook(),
        () -> {},
        () -> {}
    );

    assertEquals("mehmet", slot.id);
    assertEquals("Mehmet", slot.displayName);
    assertEquals(VillagerRole.FARMER, slot.role);
    assertTrue(slot.roleStateValid);
    assertTrue(slot.taskStateValid);
    assertNotNull(slot.task);
    assertNotNull(slot.journal);
    assertNotNull(slot.controller);
    assertNotNull(slot.productionGoalAdapter);
    assertNotNull(slot.scheduleAdapter);
    assertNotNull(slot.navigationWatchdog);
    assertNotNull(slot.navigationRetry);
    assertNotNull(slot.foreignNavigationWait);
    assertNotNull(slot.taskNavigationRoute);
    assertNotNull(slot.failedTargets);
    assertNotNull(slot.decisionRetry);
    assertNotNull(slot.partialDeliveryPolicy);
    assertNotNull(slot.targetScanner);
  }

  @Test
  void slotsAreCompletelyIsolated() {
    Path j1 = tempDir.resolve("operations-ahmet.json");
    Path j2 = tempDir.resolve("operations-ayse.json");

    VillagerSlot slot1 = VillagerSlot.createDefault("ahmet", "Ahmet", VillagerRole.WORKER, j1, new CompositeProtectionHook(), () -> {}, () -> {});
    VillagerSlot slot2 = VillagerSlot.createDefault("ayse", "Ayse", VillagerRole.CRAFTER, j2, new CompositeProtectionHook(), () -> {}, () -> {});

    assertNotEquals(slot1.id, slot2.id);
    assertNotEquals(slot1.role, slot2.role);
    assertNotEquals(slot1.task, slot2.task);
    assertNotEquals(slot1.journal, slot2.journal);
    assertNotEquals(slot1.controller, slot2.controller);

    // Mutate slot1 task
    slot1.task.startFarm(java.util.UUID.randomUUID(), 5, FarmCatalog.Crop.WHEAT.preference());
    assertTrue(slot1.task.active);
    assertTrue(slot1.task.isFarm());
    assertEquals(5, slot1.task.requested);

    // slot2 remains completely unchanged and inactive
    assertEquals(false, slot2.task.active);
    assertEquals(0, slot2.task.requested);
  }
}
