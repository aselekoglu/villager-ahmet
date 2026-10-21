package dev.ahmet;

/** Resolves user-facing task admission reasons before a request is accepted. */
final class TaskAdmission {
  private TaskAdmission() {}

  enum Result {
    ACCEPT,
    UNSUPPORTED,
    RECOVERY_REQUIRED,
    BUSY,
    CHEST_REQUIRED,
    PLOT_REQUIRED,
    ROLE_MISMATCH,
    CHEST_LOCKED
  }

  static Result evaluate(boolean validIntent, boolean recoveryBlocked, boolean journalBusy,
      boolean taskActive, int cargo, boolean chestBound) {
    if (!validIntent) return Result.UNSUPPORTED;
    if (recoveryBlocked) return Result.RECOVERY_REQUIRED;
    if (taskActive || cargo > 0) return Result.BUSY;
    if (journalBusy) return Result.RECOVERY_REQUIRED;
    if (!chestBound) return Result.CHEST_REQUIRED;
    return Result.ACCEPT;
  }

  static Result evaluate(WoodTask.Kind kind, VillagerRole role, boolean validIntent, boolean recoveryBlocked, boolean journalBusy,
      boolean taskActive, int cargo, boolean chestBound, boolean chestLocked) {
    if (!validIntent) return Result.UNSUPPORTED;
    if (role != null && kind != null && !role.canExecute(kind)) return Result.ROLE_MISMATCH;
    if (recoveryBlocked) return Result.RECOVERY_REQUIRED;
    if (taskActive || cargo > 0) return Result.BUSY;
    if (journalBusy) return Result.RECOVERY_REQUIRED;
    if (!chestBound) return Result.CHEST_REQUIRED;
    if (chestLocked) return Result.CHEST_LOCKED;
    return Result.ACCEPT;
  }

  static Result evaluateFarm(boolean validIntent, boolean recoveryBlocked, boolean journalBusy,
      boolean taskActive, int cargo, boolean chestBound, boolean plotBound) {
    if (!validIntent) return Result.UNSUPPORTED;
    if (recoveryBlocked) return Result.RECOVERY_REQUIRED;
    if (taskActive || cargo > 0) return Result.BUSY;
    if (journalBusy) return Result.RECOVERY_REQUIRED;
    if (!chestBound) return Result.CHEST_REQUIRED;
    if (!plotBound) return Result.PLOT_REQUIRED;
    return Result.ACCEPT;
  }

  static Result evaluateFarm(VillagerRole role, boolean validIntent, boolean recoveryBlocked, boolean journalBusy,
      boolean taskActive, int cargo, boolean chestBound, boolean plotBound, boolean chestLocked) {
    if (!validIntent) return Result.UNSUPPORTED;
    if (role != null && !role.canExecute(WoodTask.Kind.FARM)) return Result.ROLE_MISMATCH;
    if (recoveryBlocked) return Result.RECOVERY_REQUIRED;
    if (taskActive || cargo > 0) return Result.BUSY;
    if (journalBusy) return Result.RECOVERY_REQUIRED;
    if (!chestBound) return Result.CHEST_REQUIRED;
    if (chestLocked) return Result.CHEST_LOCKED;
    if (!plotBound) return Result.PLOT_REQUIRED;
    return Result.ACCEPT;
  }

  static boolean canCancelOrReturnHome(boolean taskStateValid, boolean recoveryBlocked, boolean pendingOperation) {
    return taskStateValid && !recoveryBlocked && !pendingOperation;
  }

  static boolean canExecuteGatherStep(WoodTask task) {
    return task != null && task.active && task.isGather();
  }

  static boolean canExecuteGatherStep(WoodTask task, VillagerRole role) {
    return canExecuteGatherStep(task) && (role == null || role.canExecute(WoodTask.Kind.GATHER));
  }

  static boolean canExecuteFarmStep(WoodTask task) {
    return task != null && task.active && task.isFarm();
  }

  static boolean canExecuteFarmStep(WoodTask task, VillagerRole role) {
    return canExecuteFarmStep(task) && (role == null || role.canExecute(WoodTask.Kind.FARM));
  }

  static boolean canExecuteHarvestOrDeliverStep(WoodTask task) {
    return task != null && task.active && (task.isGather() || task.isFarm());
  }

  static boolean canExecuteHarvestOrDeliverStep(WoodTask task, VillagerRole role) {
    return canExecuteHarvestOrDeliverStep(task) && (role == null
        || (task.isGather() && role.canExecute(WoodTask.Kind.GATHER))
        || (task.isFarm() && role.canExecute(WoodTask.Kind.FARM)));
  }

  static boolean canExecuteCraftStep(WoodTask task) {
    return task != null && task.active && task.isCraft();
  }

  static boolean canExecuteCraftStep(WoodTask task, VillagerRole role) {
    return canExecuteCraftStep(task) && (role == null || role.canExecute(WoodTask.Kind.CRAFT));
  }
}
