package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class OperationRecoveryTest {
  private final OperationJournal.Harvest operation =
      new OperationJournal.Harvest("task-1", "world", 4, 65, -2, 2, 2);

  @Test
  void distinguishesUnchangedBlockFromMutationBeforeTaskSave() {
    assertEquals(OperationRecovery.HarvestDisposition.NOT_APPLIED,
        OperationRecovery.classifyHarvest(operation, "task-1", 2, 2, "OAK_LOG"));
    assertEquals(OperationRecovery.HarvestDisposition.APPLY,
        OperationRecovery.classifyHarvest(operation, "task-1", 2, 2, "AIR"));
  }

  @Test
  void recognizesCommittedHarvestOnlyWhenBlockAndTaskAgree() {
    assertEquals(OperationRecovery.HarvestDisposition.ALREADY_APPLIED,
        OperationRecovery.classifyHarvest(operation, "task-1", 3, 3, 3, "AIR"));
    assertEquals(OperationRecovery.HarvestDisposition.CONFLICT,
        OperationRecovery.classifyHarvest(operation, "task-1", 3, 3, 3, "OAK_LOG"));
    assertEquals(OperationRecovery.HarvestDisposition.CONFLICT,
        OperationRecovery.classifyHarvest(operation, "task-1", 3, 3, 2, "AIR"));
    assertEquals(OperationRecovery.HarvestDisposition.CONFLICT,
        OperationRecovery.classifyHarvest(operation, "different-task", 3, 3, 3, "AIR"));
  }

  @Test
  void harvestReplayRequiresPerMaterialCargoBaselineToAgree() {
    var birch = new OperationJournal.Harvest("task-3", "world", 1, 64, 1, 0, 2,
        "BIRCH_LOG", "BIRCH_LOG", 1);
    assertEquals(OperationRecovery.HarvestDisposition.NOT_APPLIED,
        OperationRecovery.classifyHarvest(birch, "task-3", 0, 2, 1, "BIRCH_LOG"));
    assertEquals(OperationRecovery.HarvestDisposition.ALREADY_APPLIED,
        OperationRecovery.classifyHarvest(birch, "task-3", 1, 3, 2, "AIR"));
    assertEquals(OperationRecovery.HarvestDisposition.CONFLICT,
        OperationRecovery.classifyHarvest(birch, "task-3", 0, 2, 2, "BIRCH_LOG"));
  }

  @Test
  void depositReplayRequiresTaskCountersToAgreeExactly() {
    var deposit = new OperationJournal.Deposit("task-1", "world", 0, 64, 0, 5, 2, 2,
        java.util.List.of(new CargoDepositPlanner.Change(0, null, 0, 2)), "OAK_LOG");
    assertEquals(OperationRecovery.DepositDisposition.APPLY, OperationRecovery.classifyDeposit(deposit, "task-1", 5, 2));
    assertEquals(OperationRecovery.DepositDisposition.ALREADY_APPLIED, OperationRecovery.classifyDeposit(deposit, "task-1", 3, 4));
    assertEquals(OperationRecovery.DepositDisposition.CONFLICT, OperationRecovery.classifyDeposit(deposit, "task-1", 3, 2));
  }

  @Test
  void recoversHarvestsForTheSpecificSelectedLogMaterial() {
    var birch = new OperationJournal.Harvest("task-2", "world", 4, 65, -2, 0, 0, "BIRCH_LOG");
    assertEquals(OperationRecovery.HarvestDisposition.NOT_APPLIED,
        OperationRecovery.classifyHarvest(birch, "task-2", 0, 0, "BIRCH_LOG"));
    assertEquals(OperationRecovery.HarvestDisposition.APPLY,
        OperationRecovery.classifyHarvest(birch, "task-2", 0, 0, "AIR"));
    assertEquals(OperationRecovery.HarvestDisposition.CONFLICT,
        OperationRecovery.classifyHarvest(birch, "task-2", 0, 0, "OAK_LOG"));
  }

  @Test
  void clearRecoveryClassifiesBlockStateAccurately() {
    var clear = new OperationJournal.Clear("task-c", "world", 2, 70, 3, "OAK_LEAVES");
    assertEquals(OperationRecovery.ClearDisposition.NOT_APPLIED,
        OperationRecovery.classifyClear(clear, "task-c", "OAK_LEAVES"));
    assertEquals(OperationRecovery.ClearDisposition.ALREADY_APPLIED,
        OperationRecovery.classifyClear(clear, "task-c", "AIR"));
    assertEquals(OperationRecovery.ClearDisposition.CONFLICT,
        OperationRecovery.classifyClear(clear, "task-c", "STONE"));
    assertEquals(OperationRecovery.ClearDisposition.CONFLICT,
        OperationRecovery.classifyClear(clear, "different-task", "AIR"));
    assertEquals(OperationRecovery.ClearDisposition.CONFLICT,
        OperationRecovery.classifyClear(clear, "task-c", "other-world", "AIR"));
  }

  private static List<CraftingPlanner.Slot> chestWithOakLogs(int size, int count) {
    List<CraftingPlanner.Slot> slots = new ArrayList<>();
    slots.add(new CraftingPlanner.Slot("OAK_LOG", count, 64, true));
    for (int i = 1; i < size; i++) {
      slots.add(CraftingPlanner.Slot.empty(64));
    }
    return List.copyOf(slots);
  }

  private static WoodTask newCraftTask(UUID owner, String taskId, CraftRecipe recipe, int count) {
    WoodTask task = new WoodTask();
    task.startCraft(owner, recipe, count);
    task.taskId = taskId;
    return task;
  }

  static class TestCraftRecoveryEffects implements OperationRecovery.CraftRecoveryEffects {
    final AtomicBoolean appliedInventory = new AtomicBoolean(false);
    final AtomicBoolean persistedTask = new AtomicBoolean(false);
    final AtomicBoolean savedWorld = new AtomicBoolean(false);
    final AtomicBoolean retiredJournal = new AtomicBoolean(false);
    boolean throwOnSaveWorld = false;
    boolean throwOnRetireJournal = false;

    @Override
    public void applyInventory(CraftingPlanner.Plan plan) {
      appliedInventory.set(true);
    }

    @Override
    public void persistTask() throws IOException {
      persistedTask.set(true);
    }

    @Override
    public void saveWorld() throws IOException {
      if (throwOnSaveWorld) throw new IOException("World save failed");
      savedWorld.set(true);
    }

    @Override
    public void retireJournal() throws IOException {
      if (throwOnRetireJournal) throw new IOException("Retire journal failed");
      retiredJournal.set(true);
    }
  }

  @Test
  void classifyCraftAllValidDispositions() {
    UUID owner = UUID.randomUUID();
    var result = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var plan = result.plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);

    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    // 1. Chest is at before snapshot, task is at before
    assertEquals(OperationRecovery.CraftDisposition.APPLY_INVENTORY_AND_TASK,
        OperationRecovery.classifyCraft(craft, task, plan.before()));

    // 2. Chest is at after snapshot, task is still at before
    assertEquals(OperationRecovery.CraftDisposition.COMPLETE_TASK_ONLY,
        OperationRecovery.classifyCraft(craft, task, plan.after()));

    // 3. Chest is at after snapshot, task is at after
    task.completeCraft(4);
    assertEquals(OperationRecovery.CraftDisposition.ALREADY_APPLIED,
        OperationRecovery.classifyCraft(craft, task, plan.after()));
  }

  @Test
  void classifyCraftConflictConditions() {
    UUID owner = UUID.randomUUID();
    var result = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var plan = result.plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    // Null inputs
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(null, task, plan.before()));
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, null, plan.before()));
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, task, null));

    // Gather task instead of Craft
    WoodTask gatherTask = new WoodTask();
    gatherTask.start(owner, 4, "any");
    gatherTask.taskId = "task-c";
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, gatherTask, plan.before()));

    // Recipe mismatch
    WoodTask stickTask = newCraftTask(owner, "task-c", CraftRecipe.STICK, 4);
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, stickTask, plan.before()));

    // TaskId mismatch
    WoodTask otherIdTask = newCraftTask(owner, "other-task", CraftRecipe.OAK_PLANKS, 4);
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, otherIdTask, plan.before()));

    // Owner mismatch
    WoodTask otherOwnerTask = newCraftTask(UUID.randomUUID(), "task-c", CraftRecipe.OAK_PLANKS, 4);
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, otherOwnerTask, plan.before()));

    // Requested mismatch
    WoodTask countMismatchTask = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 8);
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, countMismatchTask, plan.before()));

    // Task dirty with cargo, cleared, or partialDelivery
    WoodTask cargoTask = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);
    cargoTask.cargo = 1;
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, cargoTask, plan.before()));

    WoodTask clearedTask = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);
    clearedTask.cleared = 1;
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, clearedTask, plan.before()));

    WoodTask partialTask = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);
    partialTask.partialDelivery = true;
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, OperationRecovery.classifyCraft(craft, partialTask, plan.before()));

    // Inventory shape mismatch (54 slots passed instead of 27)
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT,
        OperationRecovery.classifyCraft(craft, task, chestWithOakLogs(54, 1)));

    // Inventory corrupted / unrelated
    List<CraftingPlanner.Slot> unrelatedSlots = new ArrayList<>();
    unrelatedSlots.add(new CraftingPlanner.Slot("STONE", 1, 64, true));
    for (int i = 1; i < 27; i++) unrelatedSlots.add(CraftingPlanner.Slot.empty(64));
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT,
        OperationRecovery.classifyCraft(craft, task, unrelatedSlots));

    // Inventory before, but task already complete (impossible / conflict)
    WoodTask finishedTask = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);
    finishedTask.completeCraft(4);
    assertEquals(OperationRecovery.CraftDisposition.CONFLICT,
        OperationRecovery.classifyCraft(craft, finishedTask, plan.before()));
  }

  @Test
  void applyCraftChangesExecutesSlotWriterAndValidatesPreState() {
    // 1. Single slot change: 1 log -> 4 planks
    var result1 = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var plan1 = result1.plan();

    Map<Integer, String> writtenMaterials = new HashMap<>();
    Map<Integer, Integer> writtenAmounts = new HashMap<>();
    OperationRecovery.applyCraftChanges(plan1.before(), plan1, (slot, material, amount) -> {
      writtenMaterials.put(slot, material);
      writtenAmounts.put(slot, amount);
    });

    assertEquals(Map.of(0, "OAK_PLANKS"), writtenMaterials);
    assertEquals(Map.of(0, 4), writtenAmounts);

    // 2. Multi-slot change where a slot is cleared (2 logs in separate slots -> 8 planks)
    List<CraftingPlanner.Slot> multiSlots = new ArrayList<>();
    multiSlots.add(new CraftingPlanner.Slot("OAK_LOG", 1, 64, true));
    multiSlots.add(new CraftingPlanner.Slot("OAK_LOG", 1, 64, true));
    for (int i = 2; i < 27; i++) multiSlots.add(CraftingPlanner.Slot.empty(64));
    var plan2 = CraftingPlanner.plan(multiSlots, CraftRecipe.OAK_PLANKS, 8).plan();

    Map<Integer, String> multiWrittenMaterials = new HashMap<>();
    Map<Integer, Integer> multiWrittenAmounts = new HashMap<>();
    OperationRecovery.applyCraftChanges(plan2.before(), plan2, (slot, material, amount) -> {
      multiWrittenMaterials.put(slot, material);
      multiWrittenAmounts.put(slot, amount);
    });

    assertEquals("OAK_PLANKS", multiWrittenMaterials.get(0));
    assertEquals(8, multiWrittenAmounts.get(0));
    // Slot 1 was cleared
    assertEquals(null, multiWrittenMaterials.get(1));
    assertEquals(0, multiWrittenAmounts.get(1));

    // 3. Pre-state mismatch throws IllegalStateException
    assertThrows(IllegalStateException.class, () ->
        OperationRecovery.applyCraftChanges(plan1.after(), plan1, (s, m, a) -> {}));

    // 4. Inventory size mismatch throws IllegalStateException
    assertThrows(IllegalStateException.class, () ->
        OperationRecovery.applyCraftChanges(chestWithOakLogs(54, 1), plan1, (s, m, a) -> {}));
  }

  @Test
  void recoverCraftRejectsUnmatchedChestIdentityImmediately() throws Exception {
    UUID owner = UUID.randomUUID();
    var plan = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4).plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    TestCraftRecoveryEffects effects = new TestCraftRecoveryEffects();
    var disposition = OperationRecovery.recoverCraft(craft, task, false, plan.before(), effects);

    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, disposition);
    assertFalse(effects.appliedInventory.get());
    assertFalse(effects.persistedTask.get());
    assertFalse(effects.savedWorld.get());
    assertFalse(effects.retiredJournal.get());
    assertEquals(0, task.collected);
    assertEquals(0, task.deposited);
  }

  @Test
  void recoverCraftApplyInventoryAndTaskWorkflow() throws Exception {
    UUID owner = UUID.randomUUID();
    var plan = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4).plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    TestCraftRecoveryEffects effects = new TestCraftRecoveryEffects();
    var disposition = OperationRecovery.recoverCraft(craft, task, true, plan.before(), effects);

    assertEquals(OperationRecovery.CraftDisposition.APPLY_INVENTORY_AND_TASK, disposition);
    assertTrue(effects.appliedInventory.get());
    assertTrue(effects.persistedTask.get());
    assertTrue(effects.savedWorld.get());
    assertTrue(effects.retiredJournal.get());
    assertEquals(4, task.collected);
    assertEquals(4, task.deposited);
    assertTrue(task.readyToFinish());
  }

  @Test
  void recoverCraftCompleteTaskOnlyWorkflow() throws Exception {
    UUID owner = UUID.randomUUID();
    var plan = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4).plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    TestCraftRecoveryEffects effects = new TestCraftRecoveryEffects();
    var disposition = OperationRecovery.recoverCraft(craft, task, true, plan.after(), effects);

    assertEquals(OperationRecovery.CraftDisposition.COMPLETE_TASK_ONLY, disposition);
    assertFalse(effects.appliedInventory.get());
    assertTrue(effects.persistedTask.get());
    assertTrue(effects.savedWorld.get());
    assertTrue(effects.retiredJournal.get());
    assertEquals(4, task.collected);
    assertEquals(4, task.deposited);
    assertTrue(task.readyToFinish());
  }

  @Test
  void recoverCraftAlreadyAppliedWorkflow() throws Exception {
    UUID owner = UUID.randomUUID();
    var plan = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4).plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);
    task.completeCraft(4);

    TestCraftRecoveryEffects effects = new TestCraftRecoveryEffects();
    var disposition = OperationRecovery.recoverCraft(craft, task, true, plan.after(), effects);

    assertEquals(OperationRecovery.CraftDisposition.ALREADY_APPLIED, disposition);
    assertFalse(effects.appliedInventory.get());
    assertTrue(effects.persistedTask.get());
    assertTrue(effects.savedWorld.get());
    assertTrue(effects.retiredJournal.get());
    assertEquals(4, task.collected);
    assertEquals(4, task.deposited);
  }

  @Test
  void recoverCraftConflictDoesNotMutateOrInvokeEffects() throws Exception {
    UUID owner = UUID.randomUUID();
    var plan = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4).plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    TestCraftRecoveryEffects effects = new TestCraftRecoveryEffects();
    // Pass corrupted slots
    List<CraftingPlanner.Slot> emptySlots = new ArrayList<>();
    for (int i = 0; i < 27; i++) emptySlots.add(CraftingPlanner.Slot.empty(64));

    var disposition = OperationRecovery.recoverCraft(craft, task, true, emptySlots, effects);

    assertEquals(OperationRecovery.CraftDisposition.CONFLICT, disposition);
    assertFalse(effects.appliedInventory.get());
    assertFalse(effects.persistedTask.get());
    assertFalse(effects.savedWorld.get());
    assertFalse(effects.retiredJournal.get());
    assertEquals(0, task.collected);
    assertEquals(0, task.deposited);
  }

  @Test
  void recoverCraftPropagatesIoExceptionOnWorldSave() {
    UUID owner = UUID.randomUUID();
    var plan = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4).plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    TestCraftRecoveryEffects effects = new TestCraftRecoveryEffects();
    effects.throwOnSaveWorld = true;

    IOException ex = assertThrows(IOException.class, () ->
        OperationRecovery.recoverCraft(craft, task, true, plan.before(), effects));
    assertEquals("World save failed", ex.getMessage());
    assertTrue(effects.appliedInventory.get());
    assertTrue(effects.persistedTask.get());
    assertFalse(effects.retiredJournal.get());
  }

  @Test
  void recoverCraftPropagatesIoExceptionOnRetireJournal() {
    UUID owner = UUID.randomUUID();
    var plan = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4).plan();
    var craft = OperationJournal.Craft.fromPlan("task-c", owner.toString(), "world", 10, 64, 20, plan);
    WoodTask task = newCraftTask(owner, "task-c", CraftRecipe.OAK_PLANKS, 4);

    TestCraftRecoveryEffects effects = new TestCraftRecoveryEffects();
    effects.throwOnRetireJournal = true;

    IOException ex = assertThrows(IOException.class, () ->
        OperationRecovery.recoverCraft(craft, task, true, plan.before(), effects));
    assertEquals("Retire journal failed", ex.getMessage());
    assertTrue(effects.appliedInventory.get());
    assertTrue(effects.persistedTask.get());
    assertTrue(effects.savedWorld.get());
  }
}

