package dev.ahmet;

import dev.ahmet.agent.AgentGoal;

import java.util.Objects;

/** Completes terminal route-failure cleanup independently of policy/executor goal agreement. */
final class NavigationGiveUpRecovery {
  private NavigationGiveUpRecovery() {}

  enum Outcome { EXPLORATION, HOME_STEP, TASK_RETRY, PARTIAL_DELIVERY, DELIVERY_RETRY, NO_TASK }

  record Facts(boolean hasExplorationTarget, boolean hasHomeStepTarget, boolean taskActive,
               boolean readyToDeliver, boolean hasTreeTarget, boolean cargoAvailable,
               boolean productionGoalsEnabled, AgentGoal policyGoal, long nowMillis) {
    Facts {
      if (nowMillis < 0) throw new IllegalArgumentException("retry time must be non-negative");
    }
  }

  interface Effects {
    void clearExplorationTarget();
    void clearHomeStepTarget();
    void incrementHomeStepAttempt();
    void markTreeTargetFailed(long nowMillis);
    void clearTreeTarget();
    void setRetryAfter(long retryAtMillis);
    /** Returns true when the existing partial-delivery policy starts terminal delivery. */
    boolean beginPartialDelivery(boolean cargoAvailable);
    void requestHelp(String reason);
  }

  static Outcome apply(Facts facts, Effects effects) {
    Objects.requireNonNull(facts, "facts must not be null");
    Objects.requireNonNull(effects, "effects must not be null");
    boolean policyRequestsHelp = facts.productionGoalsEnabled()
        && facts.policyGoal() == AgentGoal.REQUEST_PLAYER_HELP;

    if (facts.hasExplorationTarget()) {
      effects.clearExplorationTarget();
      effects.setRetryAfter(facts.nowMillis() + 1_000);
      if (facts.taskActive() || policyRequestsHelp) {
        effects.requestHelp("Yol bulamadığım için kaynakları keşfedemiyorum; çevredeki engeli kontrol eder misin?");
      }
      return Outcome.EXPLORATION;
    }

    if (facts.hasHomeStepTarget()) {
      effects.clearHomeStepTarget();
      effects.incrementHomeStepAttempt();
      effects.setRetryAfter(facts.nowMillis() + 1_000);
      if (facts.taskActive() || policyRequestsHelp) effects.requestHelp("Eve giden yolu bulamadım; yolumu açabilir misin?");
      return Outcome.HOME_STEP;
    }

    if (!facts.taskActive()) return Outcome.NO_TASK;
    if (!facts.readyToDeliver()) {
      if (facts.hasTreeTarget()) effects.markTreeTargetFailed(facts.nowMillis());
      effects.clearTreeTarget();
      effects.setRetryAfter(facts.nowMillis() + 2_000);
      if (effects.beginPartialDelivery(facts.cargoAvailable())) return Outcome.PARTIAL_DELIVERY;
      effects.requestHelp("Ahmet üç kez yol bulamadı. Yolu açıp tekrar deneyebilirim.");
      return Outcome.TASK_RETRY;
    }

    effects.setRetryAfter(facts.nowMillis() + 30_000);
    effects.requestHelp("Sandığa giden yolu bulamadım. Sandık çevresindeki yolu açın.");
    return Outcome.DELIVERY_RETRY;
  }
}
