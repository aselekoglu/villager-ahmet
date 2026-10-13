package dev.ahmet.agent;

public record ExecutionResult(
    Status status,
    String message,
    String mutatedTarget,
    int cargoDelta
) {
  public enum Status {
    SUCCESS,
    PRECONDITION_FAILED,
    RETRY_NEEDED,
    BLOCKED,
    CRITICAL_ERROR
  }

  public static ExecutionResult success(String message, String mutatedTarget, int cargoDelta) {
    return new ExecutionResult(Status.SUCCESS, message, mutatedTarget, cargoDelta);
  }

  public static ExecutionResult success(String message) {
    return new ExecutionResult(Status.SUCCESS, message, null, 0);
  }

  public static ExecutionResult preconditionFailed(String message) {
    return new ExecutionResult(Status.PRECONDITION_FAILED, message, null, 0);
  }

  public static ExecutionResult retryNeeded(String message) {
    return new ExecutionResult(Status.RETRY_NEEDED, message, null, 0);
  }

  public static ExecutionResult blocked(String message) {
    return new ExecutionResult(Status.BLOCKED, message, null, 0);
  }

  public static ExecutionResult criticalError(String message) {
    return new ExecutionResult(Status.CRITICAL_ERROR, message, null, 0);
  }

  public boolean isSuccess() {
    return status == Status.SUCCESS;
  }
}
