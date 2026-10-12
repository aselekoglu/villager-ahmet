package dev.ahmet;

import java.util.Objects;

final class OperationRecovery {
  private OperationRecovery() {}

  enum HarvestDisposition { NOT_APPLIED, APPLY, ALREADY_APPLIED, CONFLICT }
  enum DepositDisposition { APPLY, ALREADY_APPLIED, CONFLICT }

  static HarvestDisposition classifyHarvest(OperationJournal.Harvest operation, String taskId,
      int collected, int cargo, String blockMaterial) {
    if (operation == null || !Objects.equals(operation.taskId(), taskId) || blockMaterial == null) {
      return HarvestDisposition.CONFLICT;
    }
    if (collected >= operation.afterCollected()) {
      return "AIR".equals(blockMaterial) ? HarvestDisposition.ALREADY_APPLIED : HarvestDisposition.CONFLICT;
    }
    if (collected != operation.beforeCollected() || cargo != operation.beforeCargo()) {
      return HarvestDisposition.CONFLICT;
    }
    if ("OAK_LOG".equals(blockMaterial)) return HarvestDisposition.NOT_APPLIED;
    if ("AIR".equals(blockMaterial)) return HarvestDisposition.APPLY;
    return HarvestDisposition.CONFLICT;
  }

  static DepositDisposition classifyDeposit(OperationJournal.Deposit operation, String taskId,
      int cargo, int deposited) {
    if (operation == null || !Objects.equals(operation.taskId(), taskId)) return DepositDisposition.CONFLICT;
    if (cargo == operation.afterCargo() && deposited == operation.afterDeposited()) {
      return DepositDisposition.ALREADY_APPLIED;
    }
    if (cargo == operation.beforeCargo() && deposited == operation.beforeDeposited()) {
      return DepositDisposition.APPLY;
    }
    return DepositDisposition.CONFLICT;
  }
}
