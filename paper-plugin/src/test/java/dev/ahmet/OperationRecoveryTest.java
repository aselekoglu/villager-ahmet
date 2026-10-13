package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
  }
}

