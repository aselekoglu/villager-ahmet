package dev.ahmet.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Deterministic controller and policy model used by production validation and the test harness.
 * Observation-based goal evaluation is not wired into the production AhmetPlugin task loop.
 */
public final class AgentController {
  private static final Logger LOGGER = Logger.getLogger(AgentController.class.getName());

  private final ActionPreconditions preconditions;
  private AgentState state = AgentState.IDLE;
  private AgentGoal currentGoal = AgentGoal.REST_IDLE;
  private long jobGeneration = 0;
  private int productiveTransitionCount = 0;
  private long lastAppliedServerStep = -1;
  private final Map<String, OfferedAction> currentOfferedActions = new LinkedHashMap<>();
  private long lastHelpRequestedMs = 0;

  public AgentController(ActionPreconditions preconditions) {
    this.preconditions = Objects.requireNonNull(preconditions, "preconditions must not be null");
  }

  public AgentState state() {
    return state;
  }

  public AgentGoal currentGoal() {
    return currentGoal;
  }

  public long jobGeneration() {
    return jobGeneration;
  }

  public long nextGeneration() {
    jobGeneration++;
    productiveTransitionCount = 0;
    currentGoal = AgentGoal.REST_IDLE;
    currentOfferedActions.clear();
    return jobGeneration;
  }

  public ActionPreconditions preconditions() {
    return preconditions;
  }

  public ActionBudget budget() {
    return preconditions.budget();
  }

  public void transitionTo(AgentState newState, String reason) {
    Objects.requireNonNull(newState, "newState must not be null");
    if (this.state != newState) {
      LOGGER.info("AgentState transition: " + this.state + " -> " + newState + " (" + reason + ")");
      this.state = newState;
    }
  }

  /** Selects a production goal only when all required server-authored safety facts are supplied. */
  public ProductionGoalPolicy.Selection evaluateGoal(
      AgentObservation observation, ProductionGoalPolicy.SafetyContext safetyContext) {
    return ProductionGoalPolicy.select(observation, safetyContext, currentGoal, productiveTransitionCount, budget());
  }

  /** Applies a selection only to the exact current safety snapshot and at most once per server step. */
  public boolean applyGoal(ProductionGoalPolicy.Selection selection,
                           ProductionGoalPolicy.SafetyContext currentSafetyContext) {
    if (selection == null || currentSafetyContext == null) return false;
    if (selection.evaluatedContext() != currentSafetyContext
        && !selection.evaluatedContext().equals(currentSafetyContext)) return false;
    if (selection.jobGeneration() != jobGeneration || currentSafetyContext.jobGeneration() != jobGeneration) return false;
    if (!Objects.equals(selection.taskId(), currentSafetyContext.taskId())
        || !Objects.equals(selection.ownerId(), currentSafetyContext.ownerId())
        || selection.serverStepId() != currentSafetyContext.serverStepId()) return false;
    if (selection.serverStepId() <= lastAppliedServerStep) return false;

    AgentGoal selectedGoal = selection.goal();
    if (isProductiveGoal(selectedGoal) && selectedGoal != currentGoal
        && productiveTransitionCount >= ProductionGoalPolicy.MAX_PRODUCTIVE_TRANSITIONS) return false;

    if (isProductiveGoal(selectedGoal) && selectedGoal != currentGoal) productiveTransitionCount++;
    currentGoal = selectedGoal;
    lastAppliedServerStep = selection.serverStepId();
    return true;
  }

  public int productiveTransitionCount() {
    return productiveTransitionCount;
  }

  private static boolean isProductiveGoal(AgentGoal goal) {
    return goal == AgentGoal.HARVEST_RESOURCE || goal == AgentGoal.CLEAR_OBSTRUCTION
        || goal == AgentGoal.EXPLORE_RESOURCES;
  }

  /**
   * Prepares and bounds the offered actions for a decision cycle.
   */
  public void registerOfferedActions(List<OfferedAction> actions) {
    currentOfferedActions.clear();
    if (actions != null) {
      for (OfferedAction action : actions) {
        currentOfferedActions.put(action.id(), action);
      }
    }
  }

  public Map<String, OfferedAction> offeredActions() {
    return Collections.unmodifiableMap(currentOfferedActions);
  }

  /**
   * Validates an async model decision against the offered actions and active generation.
   *
   * @param chosenId the action ID selected by the model
   * @param source the decision source (jev, heuristic, forced, fallback)
   * @param generation the job generation token of the request
   * @return the matched OfferedAction, or null if invalid / stale / disallowed
   */
  public OfferedAction validateDecision(String chosenId, String source, long generation) {
    if (generation != this.jobGeneration) {
      LOGGER.info("Discarded stale decision response (generation " + generation + " != current " + this.jobGeneration + ")");
      return null;
    }

    if (chosenId == null || !currentOfferedActions.containsKey(chosenId)) {
      LOGGER.warning("Model selected unoffered or null action ID: " + chosenId);
      return null;
    }

    OfferedAction action = currentOfferedActions.get(chosenId);

    // Security Invariant: World mutations like clear_obstruction can ONLY be selected by live JEV.
    // Fallback, heuristic, or mock modes must never trigger block deletions.
    if (action.type() == ActionType.CLEAR_OBSTRUCTION && !"jev".equals(source)) {
      LOGGER.warning("Clear obstruction action rejected due to non-JEV source: " + source);
      return null;
    }

    return action;
  }

  /**
   * Records a player help request if permitted by cooldown.
   */
  public boolean recordHelpRequest(long nowMs) {
    ExecutionResult result = preconditions.validateHelpRequest(lastHelpRequestedMs, nowMs);
    if (!result.isSuccess()) {
      return false;
    }
    lastHelpRequestedMs = nowMs;
    transitionTo(AgentState.BLOCKED_AWAITING_HELP, "Help requested from player");
    return true;
  }

  public void clearHelp() {
    if (state == AgentState.BLOCKED_AWAITING_HELP) {
      transitionTo(AgentState.IDLE, "Help cleared / path resumed");
    }
  }
}
