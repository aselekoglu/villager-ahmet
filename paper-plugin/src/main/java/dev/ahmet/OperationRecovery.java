package dev.ahmet;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

final class OperationRecovery {
  private OperationRecovery() {}

  enum HarvestDisposition { NOT_APPLIED, APPLY, ALREADY_APPLIED, CONFLICT }
  enum DepositDisposition { APPLY, ALREADY_APPLIED, CONFLICT }
  enum ClearDisposition { NOT_APPLIED, ALREADY_APPLIED, CONFLICT }
  enum CraftDisposition { APPLY_INVENTORY_AND_TASK, COMPLETE_TASK_ONLY, ALREADY_APPLIED, CONFLICT }

  static ClearDisposition classifyClear(OperationJournal.Clear operation, String taskId, String blockMaterial) {
    return classifyClear(operation,taskId,operation==null?null:operation.world(),blockMaterial);
  }

  static ClearDisposition classifyClear(OperationJournal.Clear operation, String taskId,
      String worldName, String blockMaterial) {
    if (operation == null || !Objects.equals(operation.taskId(), taskId)
        || !Objects.equals(operation.world(),worldName) || blockMaterial == null) {
      return ClearDisposition.CONFLICT;
    }
    if (operation.material().equals(blockMaterial)) return ClearDisposition.NOT_APPLIED;
    if ("AIR".equals(blockMaterial)) return ClearDisposition.ALREADY_APPLIED;
    return ClearDisposition.CONFLICT;
  }

  static HarvestDisposition classifyHarvest(OperationJournal.Harvest operation, String taskId,
      int collected, int cargo, String blockMaterial) {
    return classifyHarvest(operation,taskId,collected,cargo,operation==null?-1:operation.beforeOutputCargoCount(),blockMaterial);
  }

  static HarvestDisposition classifyHarvest(OperationJournal.Harvest operation, String taskId,
      int collected, int cargo, int materialCargo, String blockMaterial) {
    if (operation == null || !Objects.equals(operation.taskId(), taskId) || blockMaterial == null) {
      return HarvestDisposition.CONFLICT;
    }
    int beforeMaterialCargo=operation.beforeOutputCargoCount();
    if(beforeMaterialCargo<0)return HarvestDisposition.CONFLICT;
    if (collected == operation.afterCollected() && cargo == operation.afterCargo() && materialCargo==beforeMaterialCargo+1) {
      return "AIR".equals(blockMaterial) ? HarvestDisposition.ALREADY_APPLIED : HarvestDisposition.CONFLICT;
    }
    if (collected != operation.beforeCollected() || cargo != operation.beforeCargo() || materialCargo!=beforeMaterialCargo) {
      return HarvestDisposition.CONFLICT;
    }
    if (operation.effectiveMaterial().equals(blockMaterial)) return HarvestDisposition.NOT_APPLIED;
    if ("AIR".equals(blockMaterial)) return HarvestDisposition.APPLY;
    return HarvestDisposition.CONFLICT;
  }

  static HarvestDisposition classifyCropHarvest(OperationJournal.Harvest operation, String taskId,
      int collected, int cargo, String blockMaterial, String belowMaterial, Integer cropAge) {
    return classifyCropHarvest(operation, taskId, collected, cargo,
        operation == null ? -1 : operation.beforeOutputCargoCount(),
        blockMaterial, belowMaterial, cropAge);
  }

  static HarvestDisposition classifyCropHarvest(OperationJournal.Harvest operation, String taskId,
      int collected, int cargo, int materialCargo, String blockMaterial, String belowMaterial, Integer cropAge) {
    if (operation == null || !Objects.equals(operation.taskId(), taskId) || !operation.isCropHarvest()) {
      return HarvestDisposition.CONFLICT;
    }
    if (blockMaterial == null || belowMaterial == null || cropAge == null) {
      return HarvestDisposition.CONFLICT;
    }
    if (!"FARMLAND".equals(belowMaterial)) {
      return HarvestDisposition.CONFLICT;
    }
    if (!operation.effectiveMaterial().equals(blockMaterial)) {
      return HarvestDisposition.CONFLICT;
    }
    int beforeMaterialCargo = operation.beforeOutputCargoCount();
    if (beforeMaterialCargo < 0) {
      return HarvestDisposition.CONFLICT;
    }
    if (collected == operation.afterCollected() && cargo == operation.afterCargo() && materialCargo == beforeMaterialCargo + 1) {
      return cropAge == 0 ? HarvestDisposition.ALREADY_APPLIED : HarvestDisposition.CONFLICT;
    }
    if (collected != operation.beforeCollected() || cargo != operation.beforeCargo() || materialCargo != beforeMaterialCargo) {
      return HarvestDisposition.CONFLICT;
    }
    if (cropAge == 7) return HarvestDisposition.NOT_APPLIED;
    if (cropAge == 0) return HarvestDisposition.APPLY;
    return HarvestDisposition.CONFLICT;
  }

  static boolean isCommittedCropHarvestValid(OperationJournal.Harvest harvest,
      String blockMaterial, String belowMaterial, Integer cropAge) {
    if (harvest == null || !harvest.isCropHarvest()) return false;
    if (blockMaterial == null || belowMaterial == null || cropAge == null) return false;
    if (!"FARMLAND".equals(belowMaterial)) return false;
    if (!harvest.effectiveMaterial().equals(blockMaterial)) return false;
    return cropAge >= 0 && cropAge < 7;
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

  static CraftDisposition classifyCraft(OperationJournal.Craft operation, WoodTask task, List<CraftingPlanner.Slot> currentSlots) {
    if (operation == null || task == null || currentSlots == null) return CraftDisposition.CONFLICT;
    CraftingPlanner.Plan plan;
    try {
      plan = operation.toPlan();
    } catch (RuntimeException e) {
      return CraftDisposition.CONFLICT;
    }

    if (!task.isCraft() || task.craftRecipe() != plan.recipe()) return CraftDisposition.CONFLICT;
    if (!Objects.equals(operation.taskId(), task.taskId) || !Objects.equals(operation.owner(), task.owner)) return CraftDisposition.CONFLICT;
    if (task.requested != operation.requested() || task.requested != plan.outputCount()) return CraftDisposition.CONFLICT;
    if (task.partialDelivery || task.cargo != 0 || !task.cargoItems().isEmpty() || task.cleared != 0) return CraftDisposition.CONFLICT;

    if (currentSlots.size() != operation.inventorySize() || currentSlots.size() != plan.inventorySize()) return CraftDisposition.CONFLICT;
    boolean matchesBefore = currentSlots.equals(plan.before());
    boolean matchesAfter = CraftingPlanner.fullyApplied(currentSlots, plan);

    boolean taskBefore = task.active && task.collected == operation.beforeCollected() && task.deposited == operation.beforeDeposited();
    boolean taskAfter = task.collected == operation.afterCollected() && task.deposited == operation.afterDeposited();

    if (matchesBefore && taskBefore) return CraftDisposition.APPLY_INVENTORY_AND_TASK;
    if (matchesAfter && taskBefore) return CraftDisposition.COMPLETE_TASK_ONLY;
    if (matchesAfter && taskAfter) return CraftDisposition.ALREADY_APPLIED;
    return CraftDisposition.CONFLICT;
  }

  interface SlotWriter {
    void setSlot(int slot, String material, int amount);
  }

  static void applyCraftChanges(List<CraftingPlanner.Slot> currentSlots, CraftingPlanner.Plan plan, SlotWriter writer) {
    Objects.requireNonNull(plan, "Plan is required");
    Objects.requireNonNull(writer, "SlotWriter is required");
    if (currentSlots == null || currentSlots.size() != plan.inventorySize() || !currentSlots.equals(plan.before())) {
      throw new IllegalStateException("Inventory pre-state does not match craft plan before snapshot");
    }
    CraftingPlanner.apply(currentSlots, plan);
    for (CraftingPlanner.Change change : plan.changes()) {
      String afterMat = change.afterMaterial();
      int afterAmt = change.afterAmount();
      if (afterMat == null || afterAmt == 0) {
        writer.setSlot(change.slot(), null, 0);
      } else {
        if (!plan.recipe().output().equals(afterMat) && !plan.recipe().ingredients().contains(afterMat)) {
          throw new IllegalStateException("Craft change references an unallowed material: " + afterMat);
        }
        writer.setSlot(change.slot(), afterMat, afterAmt);
      }
    }
  }

  interface CraftRecoveryEffects {
    void applyInventory(CraftingPlanner.Plan plan);
    void persistTask() throws IOException;
    void saveWorld() throws IOException;
    void retireJournal() throws IOException;
  }

  static CraftDisposition recoverCraft(
      OperationJournal.Craft operation,
      WoodTask task,
      boolean chestMatchesBoundIdentity,
      List<CraftingPlanner.Slot> currentSlots,
      CraftRecoveryEffects effects) throws IOException {
    Objects.requireNonNull(effects, "CraftRecoveryEffects is required");
    if (!chestMatchesBoundIdentity) {
      return CraftDisposition.CONFLICT;
    }
    CraftDisposition disposition = classifyCraft(operation, task, currentSlots);
    switch (disposition) {
      case CONFLICT -> {
        return CraftDisposition.CONFLICT;
      }
      case APPLY_INVENTORY_AND_TASK -> {
        effects.applyInventory(operation.toPlan());
        task.completeCraft(operation.requested());
        effects.persistTask();
        effects.saveWorld();
        effects.retireJournal();
        return disposition;
      }
      case COMPLETE_TASK_ONLY -> {
        task.completeCraft(operation.requested());
        effects.persistTask();
        effects.saveWorld();
        effects.retireJournal();
        return disposition;
      }
      case ALREADY_APPLIED -> {
        effects.persistTask();
        effects.saveWorld();
        effects.retireJournal();
        return disposition;
      }
      default -> {
        return CraftDisposition.CONFLICT;
      }
    }
  }
}
