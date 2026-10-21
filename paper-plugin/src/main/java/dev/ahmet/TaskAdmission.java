package dev.ahmet;

/** Resolves user-facing task admission reasons before a request is accepted. */
final class TaskAdmission {
  private TaskAdmission() {}
  enum Result { ACCEPT, UNSUPPORTED, RECOVERY_REQUIRED, BUSY, CHEST_REQUIRED }

  static Result evaluate(boolean validIntent, boolean recoveryBlocked, boolean journalBusy,
      boolean taskActive, int cargo, boolean chestBound) {
    if(!validIntent)return Result.UNSUPPORTED;
    if(recoveryBlocked)return Result.RECOVERY_REQUIRED;
    if(taskActive||cargo>0)return Result.BUSY;
    if(journalBusy)return Result.RECOVERY_REQUIRED;
    if(!chestBound)return Result.CHEST_REQUIRED;
    return Result.ACCEPT;
  }

  static boolean canCancelOrReturnHome(boolean taskStateValid, boolean recoveryBlocked, boolean pendingOperation) {
    return taskStateValid && !recoveryBlocked && !pendingOperation;
  }

  static boolean canExecuteGatherStep(WoodTask task) {
    return task != null && task.active && task.isGather();
  }

  static boolean canExecuteCraftStep(WoodTask task) {
    return task != null && task.active && task.isCraft();
  }
}
