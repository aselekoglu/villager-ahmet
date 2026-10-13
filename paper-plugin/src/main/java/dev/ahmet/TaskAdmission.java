package dev.ahmet;

/** Resolves user-facing task admission reasons before a request is accepted. */
final class TaskAdmission {
  private TaskAdmission() {}
  enum Result { ACCEPT, UNSUPPORTED, RECOVERY_REQUIRED, BUSY, CHEST_REQUIRED }

  static Result evaluate(boolean validIntent, boolean recoveryBlocked, boolean journalBusy,
      boolean taskActive, int cargo, boolean chestBound) {
    if(!validIntent)return Result.UNSUPPORTED;
    if(recoveryBlocked||journalBusy)return Result.RECOVERY_REQUIRED;
    if(taskActive||cargo>0)return Result.BUSY;
    if(!chestBound)return Result.CHEST_REQUIRED;
    return Result.ACCEPT;
  }
}
