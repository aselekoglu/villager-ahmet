package dev.ahmet;

import dev.ahmet.agent.AgentController;
import dev.ahmet.agent.AgentGoal;
import dev.ahmet.agent.AgentObservation;
import dev.ahmet.agent.ProductionGoalPolicy;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Thin main-thread boundary between authoritative plugin facts and the guarded goal policy. */
final class ProductionGoalAdapter {
  enum SafeHomeAction { NONE, PREEMPTED_AND_PLANNED, PLANNED, CONTINUE_HOME_ROUTE }

  private long tickEra = 0;
  private long lastUnsignedTick = -1;

  enum Status { DISABLED, WRONG_THREAD, GOAL_MISMATCH, APPLY_REJECTED, APPLIED }

  record Facts(AgentObservation observation, ProductionGoalPolicy.SafetyContext safetyContext) {
    Facts {
      Objects.requireNonNull(observation, "observation must not be null");
      Objects.requireNonNull(safetyContext, "safety context must not be null");
    }
  }

  record Result(Status status, AgentGoal goal, ProductionGoalPolicy.SafetyContext safetyContext) {
    boolean applied() { return status == Status.APPLIED; }
  }

  /** Extends Paper's unsigned 32-bit global tick to a monotonic controller-wide step id. */
  synchronized long globalStepId(int paperTick) {
    long unsignedTick = Integer.toUnsignedLong(paperTick);
    if (lastUnsignedTick >= 0 && unsignedTick < lastUnsignedTick) tickEra++;
    lastUnsignedTick = unsignedTick;
    return (tickEra << Integer.SIZE) | unsignedTick;
  }

  boolean admitReturnHome(boolean enabled, boolean taskActive, int cargo, Runnable cancelActiveTask) {
    if (cargo < 0) throw new IllegalArgumentException("cargo must be non-negative");
    Objects.requireNonNull(cancelActiveTask, "task cancellation must not be null");
    if (enabled && cargo > 0) return false;
    if (taskActive) cancelActiveTask.run();
    return true;
  }

  boolean outsideHomeBoundary(boolean taskActive, double distanceSquared, int maxDistance) {
    if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return false;
    double limit = taskActive ? Math.pow(maxDistance, 2) : 16.0;
    return distanceSquared > limit;
  }

  boolean requiresSafeReturn(boolean taskActive, boolean returningHome, boolean homeStepPending,
                             boolean outsideHomeBoundary) {
    return outsideHomeBoundary || (!taskActive && (returningHome || homeStepPending));
  }

  SafeHomeAction ensureSafeHomeRoute(boolean enabled, boolean requiredSafeReturn, boolean readyToDeliver,
      AgentGoal navigationPurpose, boolean safeHomeRouteExists, Runnable cancelOldRoute, Runnable planSafeHome) {
    if (!enabled || !requiredSafeReturn || readyToDeliver) return SafeHomeAction.NONE;
    Objects.requireNonNull(navigationPurpose, "navigation purpose must not be null");
    Objects.requireNonNull(cancelOldRoute, "route cancellation must not be null");
    Objects.requireNonNull(planSafeHome, "safe-home planner must not be null");
    if (navigationPurpose == AgentGoal.RETURN_HOME && safeHomeRouteExists) return SafeHomeAction.CONTINUE_HOME_ROUTE;
    if (navigationPurpose != AgentGoal.RETURN_HOME) {
      cancelOldRoute.run();
      planSafeHome.run();
      return SafeHomeAction.PREEMPTED_AND_PLANNED;
    }
    planSafeHome.run();
    return SafeHomeAction.PLANNED;
  }

  void prepareTaskAdmission(boolean accepted, boolean enabled, Runnable clearPriorRoute) {
    Objects.requireNonNull(clearPriorRoute, "route clearing callback must not be null");
    if (accepted && enabled) clearPriorRoute.run();
  }

  Result authorize(boolean enabled, AgentController controller, BooleanSupplier isMainThread,
                   Supplier<Facts> snapshot, AgentGoal executorBranch) {
    if (!enabled) return new Result(Status.DISABLED, null, null);
    Objects.requireNonNull(controller, "controller must not be null");
    Objects.requireNonNull(isMainThread, "thread check must not be null");
    Objects.requireNonNull(snapshot, "snapshot source must not be null");
    Objects.requireNonNull(executorBranch, "executor branch must not be null");
    if (!isMainThread.getAsBoolean()) return new Result(Status.WRONG_THREAD, null, null);

    Facts facts = snapshot.get();
    ProductionGoalPolicy.Selection selection = controller.evaluateGoal(facts.observation(), facts.safetyContext());
    if (selection == null) return new Result(Status.GOAL_MISMATCH, null, facts.safetyContext());
    if (selection.goal() != executorBranch) {
      return new Result(Status.GOAL_MISMATCH, selection.goal(), facts.safetyContext());
    }
    if (!controller.applyGoal(selection, facts.safetyContext())) {
      return new Result(Status.APPLY_REJECTED, selection.goal(), facts.safetyContext());
    }
    return new Result(Status.APPLIED, selection.goal(), facts.safetyContext());
  }
}
