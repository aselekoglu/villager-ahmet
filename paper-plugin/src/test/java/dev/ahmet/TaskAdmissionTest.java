package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

final class TaskAdmissionTest {
  @TempDir Path temp;
  @Test void committedClearJournalBlocksAdmissionUntilWorldCheckpointRetiresIt() throws Exception {
    var path=temp.resolve("operations.json");
    var journal=new OperationJournal(path);
    journal.load();
    journal.prepareClear(new OperationJournal.Clear("task","world",1,64,1,"OAK_LEAVES"));
    journal.completeClear();
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,
        TaskAdmission.evaluate(true,false,journal.hasOperations(),false,0,true));
    journal.clearAfterWorldSave();
    assertEquals(TaskAdmission.Result.ACCEPT,
        TaskAdmission.evaluate(true,false,journal.hasOperations(),false,0,true));
  }

  @Test void recoveryAndBusyReasonsAreNotMisreportedAsUnsupported() throws Exception {
    var path=temp.resolve("harvest-operations.json");
    var journal=new OperationJournal(path);
    journal.load();
    journal.prepareHarvest(new OperationJournal.Harvest("task","world",1,64,1,0,0));
    journal.completeHarvest();
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,journal.hasOperations(),true,1,true));
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,journal.hasOperations(),false,1,true));
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,TaskAdmission.evaluate(true,true,journal.hasOperations(),true,1,true));
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,TaskAdmission.evaluate(true,true,false,false,0,true));
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,false,true,0,true));
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,false,false,2,true));
    assertEquals(TaskAdmission.Result.CHEST_REQUIRED,TaskAdmission.evaluate(true,false,false,false,0,false));
    assertEquals(TaskAdmission.Result.UNSUPPORTED,TaskAdmission.evaluate(false,false,false,false,0,true));
    assertEquals(TaskAdmission.Result.ACCEPT,TaskAdmission.evaluate(true,false,false,false,0,true));
  }

  @Test
  void canCancelOrReturnHomeGuardsPendingOperationRecoveryAndTaskValidity() throws Exception {
    var path = temp.resolve("cancel-guard.json");
    var journal = new OperationJournal(path);
    journal.load();

    // Valid state, no recovery blocked, no pending operation -> allowed
    assertTrue(TaskAdmission.canCancelOrReturnHome(true, false, journal.hasPendingOperation()));

    // Task state invalid -> refused
    assertFalse(TaskAdmission.canCancelOrReturnHome(false, false, journal.hasPendingOperation()));

    // Recovery blocked -> refused
    assertFalse(TaskAdmission.canCancelOrReturnHome(true, true, journal.hasPendingOperation()));

    // Pending operation in journal -> refused
    journal.prepareHarvest(new OperationJournal.Harvest("task", "world", 1, 64, 1, 0, 0));
    assertTrue(journal.hasPendingOperation());
    assertFalse(TaskAdmission.canCancelOrReturnHome(true, false, journal.hasPendingOperation()));

    // Committed harvest history exists (journal.hasOperations() is true, but hasPendingOperation() is false) -> allowed!
    journal.completeHarvest();
    assertTrue(journal.hasOperations());
    assertFalse(journal.hasPendingOperation());
    assertTrue(TaskAdmission.canCancelOrReturnHome(true, false, journal.hasPendingOperation()));
  }

  @Test
  void canExecuteGatherAndCraftStepsOnlyAllowMatchingActiveTasks() {
    assertFalse(TaskAdmission.canExecuteGatherStep(null));
    assertFalse(TaskAdmission.canExecuteCraftStep(null));

    WoodTask inactiveGather = new WoodTask();
    assertFalse(TaskAdmission.canExecuteGatherStep(inactiveGather));
    assertFalse(TaskAdmission.canExecuteCraftStep(inactiveGather));

    WoodTask activeGather = new WoodTask();
    activeGather.start(java.util.UUID.randomUUID(), 4);
    assertTrue(TaskAdmission.canExecuteGatherStep(activeGather));
    assertFalse(TaskAdmission.canExecuteCraftStep(activeGather));

    WoodTask activeCraft = new WoodTask();
    activeCraft.startCraft(java.util.UUID.randomUUID(), CraftRecipe.OAK_PLANKS, 4);
    assertFalse(TaskAdmission.canExecuteGatherStep(activeCraft));
    assertTrue(TaskAdmission.canExecuteCraftStep(activeCraft));

    WoodTask inactiveCraft = new WoodTask();
    inactiveCraft.startCraft(java.util.UUID.randomUUID(), CraftRecipe.OAK_PLANKS, 4);
    inactiveCraft.cancel();
    assertFalse(TaskAdmission.canExecuteGatherStep(inactiveCraft));
    assertFalse(TaskAdmission.canExecuteCraftStep(inactiveCraft));
  }

  @Test
  void evaluateFarmChecksPlotBoundAndPrecedence() {
    // 1. Invalid intent
    assertEquals(TaskAdmission.Result.UNSUPPORTED,
        TaskAdmission.evaluateFarm(false, false, false, false, 0, true, true));

    // 2. Recovery blocked
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,
        TaskAdmission.evaluateFarm(true, true, false, false, 0, true, true));

    // 3. Busy
    assertEquals(TaskAdmission.Result.BUSY,
        TaskAdmission.evaluateFarm(true, false, false, true, 0, true, true));
    assertEquals(TaskAdmission.Result.BUSY,
        TaskAdmission.evaluateFarm(true, false, false, false, 1, true, true));

    // 4. Journal busy
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,
        TaskAdmission.evaluateFarm(true, false, true, false, 0, true, true));

    // 5. Chest required before plot
    assertEquals(TaskAdmission.Result.CHEST_REQUIRED,
        TaskAdmission.evaluateFarm(true, false, false, false, 0, false, false));

    // 6. Plot required when chest is bound but plot is not
    assertEquals(TaskAdmission.Result.PLOT_REQUIRED,
        TaskAdmission.evaluateFarm(true, false, false, false, 0, true, false));

    // 7. Accept when all conditions met
    assertEquals(TaskAdmission.Result.ACCEPT,
        TaskAdmission.evaluateFarm(true, false, false, false, 0, true, true));
  }

  @Test
  void canExecuteFarmAndHarvestOrDeliverStepsOnlyAllowMatchingActiveTasks() {
    assertFalse(TaskAdmission.canExecuteFarmStep(null));
    assertFalse(TaskAdmission.canExecuteHarvestOrDeliverStep(null));

    WoodTask farm = new WoodTask();
    farm.startFarm(java.util.UUID.randomUUID(), 5);
    assertTrue(TaskAdmission.canExecuteFarmStep(farm));
    assertFalse(TaskAdmission.canExecuteGatherStep(farm));
    assertFalse(TaskAdmission.canExecuteCraftStep(farm));
    assertTrue(TaskAdmission.canExecuteHarvestOrDeliverStep(farm));

    farm.cancel();
    assertFalse(TaskAdmission.canExecuteFarmStep(farm));
    assertFalse(TaskAdmission.canExecuteHarvestOrDeliverStep(farm));

    WoodTask gather = new WoodTask();
    gather.start(java.util.UUID.randomUUID(), 4);
    assertFalse(TaskAdmission.canExecuteFarmStep(gather));
    assertTrue(TaskAdmission.canExecuteGatherStep(gather));
    assertTrue(TaskAdmission.canExecuteHarvestOrDeliverStep(gather));

    WoodTask craft = new WoodTask();
    craft.startCraft(java.util.UUID.randomUUID(), CraftRecipe.OAK_PLANKS, 4);
    assertFalse(TaskAdmission.canExecuteFarmStep(craft));
    assertFalse(TaskAdmission.canExecuteGatherStep(craft));
    assertFalse(TaskAdmission.canExecuteHarvestOrDeliverStep(craft));
  }

  @Test
  void evaluateRoleAndLockPrecedence() {
    // 1. Invalid intent
    assertEquals(TaskAdmission.Result.UNSUPPORTED,
        TaskAdmission.evaluate(WoodTask.Kind.GATHER, VillagerRole.WORKER, false, false, false, false, 0, true, false));

    // 2. Role mismatch (FARMER cannot GATHER)
    assertEquals(TaskAdmission.Result.ROLE_MISMATCH,
        TaskAdmission.evaluate(WoodTask.Kind.GATHER, VillagerRole.FARMER, true, true, true, true, 5, false, true));

    // 3. Recovery blocked
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,
        TaskAdmission.evaluate(WoodTask.Kind.GATHER, VillagerRole.WORKER, true, true, false, false, 0, true, false));

    // 4. Busy
    assertEquals(TaskAdmission.Result.BUSY,
        TaskAdmission.evaluate(WoodTask.Kind.GATHER, VillagerRole.WORKER, true, false, false, true, 0, true, false));

    // 5. Chest required
    assertEquals(TaskAdmission.Result.CHEST_REQUIRED,
        TaskAdmission.evaluate(WoodTask.Kind.GATHER, VillagerRole.WORKER, true, false, false, false, 0, false, false));

    // 6. Chest locked
    assertEquals(TaskAdmission.Result.CHEST_LOCKED,
        TaskAdmission.evaluate(WoodTask.Kind.GATHER, VillagerRole.WORKER, true, false, false, false, 0, true, true));

    // 7. Accept
    assertEquals(TaskAdmission.Result.ACCEPT,
        TaskAdmission.evaluate(WoodTask.Kind.GATHER, VillagerRole.WORKER, true, false, false, false, 0, true, false));
    assertEquals(TaskAdmission.Result.ACCEPT,
        TaskAdmission.evaluate(WoodTask.Kind.CRAFT, VillagerRole.ALL_ROUNDER, true, false, false, false, 0, true, false));
  }

  @Test
  void evaluateFarmRoleAndLockPrecedence() {
    // Role mismatch (WORKER cannot FARM)
    assertEquals(TaskAdmission.Result.ROLE_MISMATCH,
        TaskAdmission.evaluateFarm(VillagerRole.WORKER, true, true, true, true, 5, false, false, true));

    // Chest locked checked before plot
    assertEquals(TaskAdmission.Result.CHEST_LOCKED,
        TaskAdmission.evaluateFarm(VillagerRole.FARMER, true, false, false, false, 0, true, false, true));

    // Plot required checked after chest not locked
    assertEquals(TaskAdmission.Result.PLOT_REQUIRED,
        TaskAdmission.evaluateFarm(VillagerRole.FARMER, true, false, false, false, 0, true, false, false));

    // Accept when all met
    assertEquals(TaskAdmission.Result.ACCEPT,
        TaskAdmission.evaluateFarm(VillagerRole.FARMER, true, false, false, false, 0, true, true, false));
  }

  @Test
  void roleAwareStepExecutionChecks() {
    WoodTask gather = new WoodTask();
    gather.start(java.util.UUID.randomUUID(), 4);

    // null role defaults to allowing the matching task kind
    assertTrue(TaskAdmission.canExecuteGatherStep(gather, null));
    // WORKER & ALL_ROUNDER can gather
    assertTrue(TaskAdmission.canExecuteGatherStep(gather, VillagerRole.WORKER));
    assertTrue(TaskAdmission.canExecuteGatherStep(gather, VillagerRole.ALL_ROUNDER));
    // FARMER & CRAFTER cannot gather
    assertFalse(TaskAdmission.canExecuteGatherStep(gather, VillagerRole.FARMER));
    assertFalse(TaskAdmission.canExecuteGatherStep(gather, VillagerRole.CRAFTER));

    WoodTask farm = new WoodTask();
    farm.startFarm(java.util.UUID.randomUUID(), 4);
    assertTrue(TaskAdmission.canExecuteFarmStep(farm, VillagerRole.FARMER));
    assertTrue(TaskAdmission.canExecuteFarmStep(farm, VillagerRole.ALL_ROUNDER));
    assertFalse(TaskAdmission.canExecuteFarmStep(farm, VillagerRole.WORKER));
    assertFalse(TaskAdmission.canExecuteFarmStep(farm, VillagerRole.CRAFTER));

    WoodTask craft = new WoodTask();
    craft.startCraft(java.util.UUID.randomUUID(), CraftRecipe.OAK_PLANKS, 4);
    assertTrue(TaskAdmission.canExecuteCraftStep(craft, VillagerRole.CRAFTER));
    assertTrue(TaskAdmission.canExecuteCraftStep(craft, VillagerRole.ALL_ROUNDER));
    assertFalse(TaskAdmission.canExecuteCraftStep(craft, VillagerRole.WORKER));
    assertFalse(TaskAdmission.canExecuteCraftStep(craft, VillagerRole.FARMER));

    // Harvest or deliver step
    assertTrue(TaskAdmission.canExecuteHarvestOrDeliverStep(gather, VillagerRole.WORKER));
    assertFalse(TaskAdmission.canExecuteHarvestOrDeliverStep(gather, VillagerRole.FARMER));
    assertTrue(TaskAdmission.canExecuteHarvestOrDeliverStep(farm, VillagerRole.FARMER));
    assertFalse(TaskAdmission.canExecuteHarvestOrDeliverStep(farm, VillagerRole.WORKER));
  }
}
