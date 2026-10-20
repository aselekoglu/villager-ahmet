package dev.ahmet.agent;

import java.util.Objects;
import java.util.UUID;

/** Pure policy for choosing a bounded production goal from server-authored facts. */
public final class ProductionGoalPolicy {
  public static final int MAX_PRODUCTIVE_TRANSITIONS = 32;

  private ProductionGoalPolicy() {}

  static Selection select(AgentObservation observation, SafetyContext context, AgentGoal currentGoal,
                          int productiveTransitions, ActionBudget budget) {
    Objects.requireNonNull(observation, "observation must not be null");
    Objects.requireNonNull(context, "safety context must not be null");
    Objects.requireNonNull(currentGoal, "current goal must not be null");
    Objects.requireNonNull(budget, "budget must not be null");
    if (context.recoveryBlocked() || context.transactionPending()
        || (!context.taskActive() && context.cargo() > 0)) {
      return selection(AgentGoal.REST_IDLE, context);
    }
    if (observation.taskActive() != context.taskActive()
        || !Objects.equals(observation.activeTaskId(), context.taskId())) return null;

    AgentGoal goal = choose(context, observation, budget, currentGoal, productiveTransitions);
    return selection(goal, context);
  }

  private static Selection selection(AgentGoal goal, SafetyContext context) {
    return new Selection(goal, context.jobGeneration(), context.taskId(), context.ownerId(),
        context.serverStepId(), context);
  }

  private static AgentGoal choose(SafetyContext context, AgentObservation observation, ActionBudget budget,
                                 AgentGoal currentGoal, int productiveTransitions) {
    if (context.recoveryBlocked() || context.transactionPending()) return AgentGoal.REST_IDLE;

    if (!context.taskActive()) {
      if (context.cargo() > 0) return AgentGoal.REST_IDLE;
      return context.returningHome() || context.requiredSafeReturn() ? AgentGoal.RETURN_HOME : AgentGoal.REST_IDLE;
    }

    if (context.taskId() == null || context.ownerId() == null) return AgentGoal.REQUEST_PLAYER_HELP;
    if (context.readyToDeliver()) return AgentGoal.DELIVER_CARGO;
    if (context.requiredSafeReturn()) return AgentGoal.RETURN_HOME;
    if (context.foreignNavigationWaitExpired()) return AgentGoal.REQUEST_PLAYER_HELP;

    if (context.routeBlocked()) {
      if (context.clearOfferedByLiveJev()
          && budget.canClearObstruction(context.routeClears(), context.jobClears())) {
        return productive(AgentGoal.CLEAR_OBSTRUCTION, currentGoal, productiveTransitions);
      }
      if (context.routeRetryAvailable()) return productive(retryGoal(context), currentGoal, productiveTransitions);
      return AgentGoal.REQUEST_PLAYER_HELP;
    }

    if (context.navigationInProgress()) {
      return productive(retryGoal(context), currentGoal, productiveTransitions);
    }
    if (context.targetAvailable() || context.scanPending()) {
      return productive(AgentGoal.HARVEST_RESOURCE, currentGoal, productiveTransitions);
    }
    if (context.scanExhausted() && context.explorationAllowed()
        && budget.canExplore(context.explorationAttempts())) {
      return productive(AgentGoal.EXPLORE_RESOURCES, currentGoal, productiveTransitions);
    }
    if (!context.scanExhausted()) return AgentGoal.REST_IDLE;
    return AgentGoal.REQUEST_PLAYER_HELP;
  }

  private static AgentGoal retryGoal(SafetyContext context) {
    if (context.navigationPurpose() == AgentGoal.EXPLORE_RESOURCES) return AgentGoal.EXPLORE_RESOURCES;
    if (context.navigationPurpose() == AgentGoal.RETURN_HOME) return AgentGoal.RETURN_HOME;
    return AgentGoal.HARVEST_RESOURCE;
  }

  private static AgentGoal productive(AgentGoal candidate, AgentGoal currentGoal, int transitions) {
    if (candidate == currentGoal || transitions < MAX_PRODUCTIVE_TRANSITIONS) return candidate;
    return AgentGoal.REQUEST_PLAYER_HELP;
  }

  public static final class Selection {
    private final AgentGoal goal;
    private final long jobGeneration;
    private final String taskId;
    private final UUID ownerId;
    private final long serverStepId;
    private final SafetyContext evaluatedContext;

    private Selection(AgentGoal goal, long jobGeneration, String taskId, UUID ownerId,
                      long serverStepId, SafetyContext evaluatedContext) {
      this.goal = Objects.requireNonNull(goal, "goal must not be null");
      this.jobGeneration = jobGeneration;
      this.taskId = taskId;
      this.ownerId = ownerId;
      this.serverStepId = serverStepId;
      this.evaluatedContext = Objects.requireNonNull(evaluatedContext, "evaluated context must not be null");
    }

    public AgentGoal goal() { return goal; }
    public long jobGeneration() { return jobGeneration; }
    public String taskId() { return taskId; }
    public UUID ownerId() { return ownerId; }
    public long serverStepId() { return serverStepId; }
    public SafetyContext evaluatedContext() { return evaluatedContext; }
  }

  /** Immutable server-authored facts required to select and apply a production goal. */
  public record SafetyContext(
      long jobGeneration,
      String taskId,
      UUID ownerId,
      long serverStepId,
      boolean taskActive,
      int cargo,
      boolean readyToDeliver,
      boolean recoveryBlocked,
      boolean transactionPending,
      boolean committedJournalHistory,
      boolean navigationInProgress,
      boolean routeBlocked,
      boolean routeRetryAvailable,
      boolean foreignNavigationWaitExpired,
      boolean clearOfferedByLiveJev,
      boolean targetAvailable,
      boolean scanPending,
      boolean scanExhausted,
      boolean explorationAllowed,
      AgentGoal navigationPurpose,
      boolean returningHome,
      boolean requiredSafeReturn,
      int routeClears,
      int jobClears,
      int explorationAttempts
  ) {
    public SafetyContext {
      if (jobGeneration < 0) throw new IllegalArgumentException("job generation must be non-negative");
      if (serverStepId < 0) throw new IllegalArgumentException("server step identity must be non-negative");
      if (cargo < 0) throw new IllegalArgumentException("cargo must be non-negative");
      if (routeClears < 0 || jobClears < 0 || explorationAttempts < 0) {
        throw new IllegalArgumentException("action counters must be non-negative");
      }
      Objects.requireNonNull(navigationPurpose, "navigation purpose must not be null");
    }

    public static Builder builder(long jobGeneration, String taskId, UUID ownerId, long serverStepId) {
      return new Builder(jobGeneration, taskId, ownerId, serverStepId);
    }

    public SafetyContext withTaskId(String value) { return copy().taskId(value).build(); }
    public SafetyContext withOwner(UUID value) { return copy().ownerId(value).build(); }
    public SafetyContext withGeneration(long value) { return copy().jobGeneration(value).build(); }
    public SafetyContext withServerStep(long value) { return copy().serverStepId(value).build(); }
    public SafetyContext withTargetAvailable(boolean value) { return copy().targetAvailable(value).build(); }
    public SafetyContext withExplorationAllowed(boolean value) { return copy().explorationAllowed(value).build(); }
    public SafetyContext withScanExhausted(boolean value) { return copy().scanExhausted(value).build(); }
    public SafetyContext withCargo(int value) { return copy().cargo(value).build(); }
    public SafetyContext withReadyToDeliver(boolean value) { return copy().readyToDeliver(value).build(); }
    public SafetyContext withScanPending(boolean value) { return copy().scanPending(value).build(); }
    public SafetyContext withNavigationPurpose(AgentGoal value) { return copy().navigationPurpose(value).build(); }

    private Builder copy() {
      return new Builder(jobGeneration, taskId, ownerId, serverStepId)
          .taskActive(taskActive).cargo(cargo).readyToDeliver(readyToDeliver)
          .recoveryBlocked(recoveryBlocked).transactionPending(transactionPending)
          .committedJournalHistory(committedJournalHistory).navigationInProgress(navigationInProgress)
          .routeBlocked(routeBlocked).routeRetryAvailable(routeRetryAvailable)
          .foreignNavigationWaitExpired(foreignNavigationWaitExpired)
          .clearOfferedByLiveJev(clearOfferedByLiveJev).targetAvailable(targetAvailable)
          .scanPending(scanPending).scanExhausted(scanExhausted).explorationAllowed(explorationAllowed)
          .navigationPurpose(navigationPurpose).returningHome(returningHome).requiredSafeReturn(requiredSafeReturn)
          .routeClears(routeClears).jobClears(jobClears).explorationAttempts(explorationAttempts);
    }

    public static final class Builder {
      private long jobGeneration;
      private String taskId;
      private UUID ownerId;
      private long serverStepId;
      private boolean taskActive;
      private int cargo;
      private boolean readyToDeliver;
      private boolean recoveryBlocked;
      private boolean transactionPending;
      private boolean committedJournalHistory;
      private boolean navigationInProgress;
      private boolean routeBlocked;
      private boolean routeRetryAvailable;
      private boolean foreignNavigationWaitExpired;
      private boolean clearOfferedByLiveJev;
      private boolean targetAvailable;
      private boolean scanPending;
      private boolean scanExhausted;
      private boolean explorationAllowed;
      private AgentGoal navigationPurpose = AgentGoal.REST_IDLE;
      private boolean returningHome;
      private boolean requiredSafeReturn;
      private int routeClears;
      private int jobClears;
      private int explorationAttempts;

      private Builder(long jobGeneration, String taskId, UUID ownerId, long serverStepId) {
        this.jobGeneration = jobGeneration;
        this.taskId = taskId;
        this.ownerId = ownerId;
        this.serverStepId = serverStepId;
      }

      public Builder jobGeneration(long value) { jobGeneration = value; return this; }
      public Builder taskId(String value) { taskId = value; return this; }
      public Builder ownerId(UUID value) { ownerId = value; return this; }
      public Builder serverStepId(long value) { serverStepId = value; return this; }
      public Builder taskActive(boolean value) { taskActive = value; return this; }
      public Builder cargo(int value) { cargo = value; return this; }
      public Builder readyToDeliver(boolean value) { readyToDeliver = value; return this; }
      public Builder recoveryBlocked(boolean value) { recoveryBlocked = value; return this; }
      public Builder transactionPending(boolean value) { transactionPending = value; return this; }
      public Builder committedJournalHistory(boolean value) { committedJournalHistory = value; return this; }
      public Builder navigationInProgress(boolean value) { navigationInProgress = value; return this; }
      public Builder routeBlocked(boolean value) { routeBlocked = value; return this; }
      public Builder routeRetryAvailable(boolean value) { routeRetryAvailable = value; return this; }
      public Builder foreignNavigationWaitExpired(boolean value) { foreignNavigationWaitExpired = value; return this; }
      public Builder clearOfferedByLiveJev(boolean value) { clearOfferedByLiveJev = value; return this; }
      public Builder targetAvailable(boolean value) { targetAvailable = value; return this; }
      public Builder scanPending(boolean value) { scanPending = value; return this; }
      public Builder scanExhausted(boolean value) { scanExhausted = value; return this; }
      public Builder explorationAllowed(boolean value) { explorationAllowed = value; return this; }
      public Builder navigationPurpose(AgentGoal value) { navigationPurpose = value; return this; }
      public Builder returningHome(boolean value) { returningHome = value; return this; }
      public Builder requiredSafeReturn(boolean value) { requiredSafeReturn = value; return this; }
      public Builder routeClears(int value) { routeClears = value; return this; }
      public Builder jobClears(int value) { jobClears = value; return this; }
      public Builder explorationAttempts(int value) { explorationAttempts = value; return this; }
      public SafetyContext build() {
        return new SafetyContext(jobGeneration, taskId, ownerId, serverStepId, taskActive, cargo,
            readyToDeliver, recoveryBlocked, transactionPending, committedJournalHistory,
            navigationInProgress, routeBlocked, routeRetryAvailable, foreignNavigationWaitExpired, clearOfferedByLiveJev,
            targetAvailable, scanPending, scanExhausted, explorationAllowed, navigationPurpose, returningHome, requiredSafeReturn,
            routeClears, jobClears, explorationAttempts);
      }
    }
  }
}
