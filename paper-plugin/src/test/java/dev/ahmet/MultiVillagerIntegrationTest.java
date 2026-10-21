package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class MultiVillagerIntegrationTest {

  @TempDir
  Path tempDir;

  @Test
  void multiVillagerRoleBasedTaskDelegation() {
    List<TaskDelegator.Candidate> candidates = List.of(
        new TaskDelegator.Candidate("crafter1", "Fatma", VillagerRole.CRAFTER, true, true, 10.0, TaskAdmission.Result.ACCEPT, true),
        new TaskDelegator.Candidate("farmer1", "Mehmet", VillagerRole.FARMER, true, true, 12.0, TaskAdmission.Result.ACCEPT, false),
        new TaskDelegator.Candidate("worker1", "Ahmet", VillagerRole.WORKER, true, true, 8.0, TaskAdmission.Result.ACCEPT, false),
        new TaskDelegator.Candidate("general1", "Ali", VillagerRole.ALL_ROUNDER, true, true, 5.0, TaskAdmission.Result.ACCEPT, true)
    );

    // FARM task should select Mehmet (specialist FARMER) over ALL_ROUNDER (Ali), even though Ali is closer
    TaskDelegator.Decision farmDecision = TaskDelegator.select(WoodTask.Kind.FARM, candidates, 32.0 * 32.0);
    assertEquals(TaskDelegator.Status.SELECTED, farmDecision.status());
    assertNotNull(farmDecision.selected());
    assertEquals("farmer1", farmDecision.selected().villagerId());

    // CRAFT task should select Fatma (specialist CRAFTER)
    TaskDelegator.Decision craftDecision = TaskDelegator.select(WoodTask.Kind.CRAFT, candidates, 32.0 * 32.0);
    assertEquals(TaskDelegator.Status.SELECTED, craftDecision.status());
    assertNotNull(craftDecision.selected());
    assertEquals("crafter1", craftDecision.selected().villagerId());

    // GATHER task should select Ahmet (specialist WORKER)
    TaskDelegator.Decision gatherDecision = TaskDelegator.select(WoodTask.Kind.GATHER, candidates, 32.0 * 32.0);
    assertEquals(TaskDelegator.Status.SELECTED, gatherDecision.status());
    assertNotNull(gatherDecision.selected());
    assertEquals("worker1", gatherDecision.selected().villagerId());
  }

  @Test
  void concurrentResourceLockingBetweenVillagers() {
    ResourceLockRegistry locks = new ResourceLockRegistry();

    // Ahmet acquires target block
    assertTrue(locks.tryAcquireTargetBlock("ahmet", "task-1", "world", 10, 64, 20));
    assertFalse(locks.isBlockLockedByOther("ahmet", "world", 10, 64, 20));
    assertTrue(locks.isBlockLockedByOther("mehmet", "world", 10, 64, 20));

    // Mehmet cannot acquire the same block
    assertFalse(locks.tryAcquireTargetBlock("mehmet", "task-2", "world", 10, 64, 20));

    // Ahmet finishes harvest and releases block
    locks.releaseBlock("ahmet", "world", 10, 64, 20);
    assertFalse(locks.isBlockLockedByOther("mehmet", "world", 10, 64, 20));

    // Mehmet can now acquire it
    assertTrue(locks.tryAcquireTargetBlock("mehmet", "task-2", "world", 10, 64, 20));

    // Chest lock concurrency (e.g. double chest halves)
    List<ResourceLockRegistry.CoordKey> doubleChest = List.of(
        new ResourceLockRegistry.CoordKey("world", 100, 64, 200),
        new ResourceLockRegistry.CoordKey("world", 101, 64, 200)
    );

    assertTrue(locks.tryAcquireChest("ahmet", "task-1", doubleChest));
    assertTrue(locks.isChestLockedByOther("mehmet", doubleChest));
    assertFalse(locks.tryAcquireChest("mehmet", "task-2", doubleChest));

    // Releasing chests for Ahmet frees it for Mehmet
    locks.releaseChestsForVillager("ahmet");
    assertFalse(locks.isChestLockedByOther("mehmet", doubleChest));
    assertTrue(locks.tryAcquireChest("mehmet", "task-2", doubleChest));
  }

  @Test
  void recoveryPinnedLocksBlockOtherVillagersUntilCleared() {
    ResourceLockRegistry locks = new ResourceLockRegistry();

    // Ahmet enters crash recovery on a block
    locks.pinRecoveryBlock("ahmet", "task-crash", "world", 5, 70, 5);
    assertTrue(locks.isBlockLockedByOther("mehmet", "world", 5, 70, 5));

    // Mehmet cannot steal or target the pinned recovery block
    assertFalse(locks.tryAcquireTargetBlock("mehmet", "task-clean", "world", 5, 70, 5));

    // Once Ahmet completes recovery and releases all locks
    locks.releaseAllForVillager("ahmet");
    assertFalse(locks.isBlockLockedByOther("mehmet", "world", 5, 70, 5));
    assertTrue(locks.tryAcquireTargetBlock("mehmet", "task-clean", "world", 5, 70, 5));
  }

  @Test
  void multiVillagerEconomyLedgerTracking() throws IOException {
    Path ledgerFile = tempDir.resolve("economy-ledger.json");
    LocalEconomyLedger ledger = new LocalEconomyLedger(ledgerFile);
    ledger.load();

    // 1. Ahmet (WORKER) gathers and deposits 4 OAK_LOG
    assertTrue(ledger.recordDeposit("dep-1", "ahmet", VillagerRole.WORKER, "task-1", WoodTask.Kind.GATHER, "OAK_LOG", 4));

    // 2. Mehmet (FARMER) harvests and deposits 8 WHEAT
    assertTrue(ledger.recordDeposit("dep-2", "mehmet", VillagerRole.FARMER, "task-2", WoodTask.Kind.FARM, "WHEAT", 8));

    // 3. Ayse (CRAFTER) crafts 16 OAK_PLANKS using 4 OAK_LOG
    assertTrue(ledger.recordCraft("craft-1", "ayse", VillagerRole.CRAFTER, "task-3", "OAK_LOG", 4, "OAK_PLANKS", 16));

    // Check individual villager metrics
    LocalEconomyLedger.VillagerSummary sAhmet = ledger.villagerSummary("ahmet");
    assertEquals(4, sAhmet.credits());
    assertEquals(4, sAhmet.gatheredItems());
    assertEquals(0, sAhmet.farmedItems());
    assertEquals(0, sAhmet.craftedItems());
    assertEquals(1, sAhmet.tasksContributed());

    LocalEconomyLedger.VillagerSummary sMehmet = ledger.villagerSummary("mehmet");
    assertEquals(8, sMehmet.credits());
    assertEquals(0, sMehmet.gatheredItems());
    assertEquals(8, sMehmet.farmedItems());
    assertEquals(0, sMehmet.craftedItems());
    assertEquals(1, sMehmet.tasksContributed());

    LocalEconomyLedger.VillagerSummary sAyse = ledger.villagerSummary("ayse");
    assertEquals(16, sAyse.credits());
    assertEquals(0, sAyse.gatheredItems());
    assertEquals(0, sAyse.farmedItems());
    assertEquals(16, sAyse.craftedItems());
    assertEquals(1, sAyse.tasksContributed());

    // Check net village inventory balances:
    // OAK_LOG: 4 produced - 4 consumed = 0
    // WHEAT: 8 produced = 8
    // OAK_PLANKS: 16 produced = 16
    assertEquals(Map.of("OAK_LOG", 0, "WHEAT", 8, "OAK_PLANKS", 16), ledger.netBalances());
    assertEquals(Map.of("OAK_LOG", 4, "WHEAT", 8, "OAK_PLANKS", 16), ledger.producedTotals());
    assertEquals(Map.of("OAK_LOG", 4), ledger.consumedTotals());

    // Verify ledger persistence across reloads
    LocalEconomyLedger reloaded = new LocalEconomyLedger(ledgerFile);
    reloaded.load();
    assertEquals(4, reloaded.villagerCredits("ahmet"));
    assertEquals(8, reloaded.villagerCredits("mehmet"));
    assertEquals(16, reloaded.villagerCredits("ayse"));
    assertEquals(3, reloaded.recentEntries().size());
  }

  @Test
  void isolatedJournalsDoNotInterfereAcrossVillagers() throws IOException {
    Path jAhmet = tempDir.resolve("operations-ahmet.json");
    Path jMehmet = tempDir.resolve("operations-mehmet.json");

    OperationJournal journalAhmet = new OperationJournal(jAhmet);
    OperationJournal journalMehmet = new OperationJournal(jMehmet);

    // Mehmet has a pending harvest operation
    OperationJournal.Harvest op = new OperationJournal.Harvest(
        "task-m", "world", 10, 64, 20, 0, 0, "OAK_LOG", "OAK_LOG", 0
    );
    journalMehmet.prepareHarvest(op);

    assertTrue(journalMehmet.hasPendingOperation());
    assertTrue(journalMehmet.pendingHarvest().isPresent());

    // Ahmet's journal remains completely clear and has no operations
    assertFalse(journalAhmet.hasPendingOperation());
    assertFalse(journalAhmet.hasOperations());
    assertTrue(journalAhmet.pendingHarvest().isEmpty());
  }

  @Test
  void rosterValidationFailsOnDuplicateNpcIdOrInvalidId() {
    // Duplicate NPC ID between ahmet and mehmet
    VillagerRoster.ValidationResult resDup = VillagerRoster.validate(
        java.util.Set.of("mehmet"),
        java.util.Map.of("ahmet", 10, "mehmet", 10)
    );
    assertFalse(resDup.valid());
    assertTrue(resDup.reason().contains("Duplicate Citizens NPC ID"));

    // Invalid secondary ID
    VillagerRoster.ValidationResult resInv = VillagerRoster.validate(
        java.util.Set.of("Invalid_Name!"),
        java.util.Map.of("ahmet", 1, "Invalid_Name!", 2)
    );
    assertFalse(resInv.valid());
    assertTrue(resInv.reason().contains("Invalid secondary villager id"));
  }
}
