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
        OperationRecovery.classifyHarvest(operation, "task-1", 3, 3, "AIR"));
    assertEquals(OperationRecovery.HarvestDisposition.CONFLICT,
        OperationRecovery.classifyHarvest(operation, "task-1", 3, 3, "OAK_LOG"));
    assertEquals(OperationRecovery.HarvestDisposition.CONFLICT,
        OperationRecovery.classifyHarvest(operation, "different-task", 3, 3, "AIR"));
  }
}
