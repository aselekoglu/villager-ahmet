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

  /**
   * Evaluates the appropriate goal for the agent based on current observation.
   */
  public AgentGoal evaluateGoal(AgentObservation obs) {
    if (!obs.taskActive()) {
      if (obs.cargo() > 0) {
        currentGoal = AgentGoal.DELIVER_CARGO;
      } else {
        currentGoal = AgentGoal.REST_IDLE;
      }
      return currentGoal;
    }

    if (obs.cargo() >= obs.requested() || obs.collected() >= obs.requested()) {
      currentGoal = AgentGoal.DELIVER_CARGO;
      return currentGoal;
    }

    if (obs.routeClears() >= budget().maxRouteClears()) {
      currentGoal = AgentGoal.REQUEST_PLAYER_HELP;
      return currentGoal;
    }

    currentGoal = AgentGoal.HARVEST_RESOURCE;
    return currentGoal;
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
