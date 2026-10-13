package dev.ahmet.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AgentControllerTest {

  @Test
  void testStateTransitionsAndGoalEvaluation() {
    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    assertEquals(AgentState.IDLE, controller.state());
    assertEquals(AgentGoal.REST_IDLE, controller.currentGoal());

    controller.transitionTo(AgentState.PLANNING, "Scanning targets");
    assertEquals(AgentState.PLANNING, controller.state());

    // Goal evaluation when inactive with cargo -> DELIVER_CARGO
    AgentObservation obsDeliver = new AgentObservation(
        null, true, null, null, null, null, false, "oak_log", 10, 5, 5, 0, 0, 0, 0, List.of(), 1000L
    );
    assertEquals(AgentGoal.DELIVER_CARGO, controller.evaluateGoal(obsDeliver));

    // Goal evaluation when active and cargo < requested -> HARVEST_RESOURCE
    AgentObservation obsHarvest = new AgentObservation(
        null, true, null, null, null, "task-1", true, "oak_log", 10, 2, 2, 0, 0, 0, 0, List.of(), 1000L
    );
    assertEquals(AgentGoal.HARVEST_RESOURCE, controller.evaluateGoal(obsHarvest));

    // Goal evaluation when route clears exhausted -> REQUEST_PLAYER_HELP
    AgentObservation obsHelp = new AgentObservation(
        null, true, null, null, null, "task-1", true, "oak_log", 10, 2, 2, 0, 0, 3, 0, List.of(), 1000L
    );
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, controller.evaluateGoal(obsHelp));
  }

  @Test
  void testDecisionValidationSafetyInvariants() {
    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    long gen = controller.nextGeneration();

    OfferedAction targetA = OfferedAction.of("target_a", ActionType.GATHER_TARGET, "Harvest birch log");
    OfferedAction clearA = OfferedAction.of("clear_a", ActionType.CLEAR_OBSTRUCTION, "Clear oak leaf");
    OfferedAction wait = OfferedAction.of("wait", ActionType.WAIT, "Wait");

    controller.registerOfferedActions(List.of(targetA, clearA, wait));

    // Normal valid gather decision
    OfferedAction chosenGather = controller.validateDecision("target_a", "jev", gen);
    assertNotNull(chosenGather);
    assertEquals("target_a", chosenGather.id());

    // Stale generation is discarded
    assertNull(controller.validateDecision("target_a", "jev", gen - 1));

    // Unoffered ID is rejected
    assertNull(controller.validateDecision("unoffered_id", "jev", gen));

    // Clear obstruction selected by fallback/heuristic/mock MUST be rejected!
    assertNull(controller.validateDecision("clear_a", "fallback", gen));
    assertNull(controller.validateDecision("clear_a", "heuristic", gen));
    assertNull(controller.validateDecision("clear_a", "forced", gen));

    // Clear obstruction selected by live JEV is allowed
    OfferedAction chosenClear = controller.validateDecision("clear_a", "jev", gen);
    assertNotNull(chosenClear);
    assertEquals("clear_a", chosenClear.id());
  }

  @Test
  void testHelpRequestStateLifecycle() {
    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    assertTrue(controller.recordHelpRequest(10_000L));
    assertEquals(AgentState.BLOCKED_AWAITING_HELP, controller.state());

    // Cooldown prevents immediate repeat
    assertFalse(controller.recordHelpRequest(15_000L));
    assertEquals(AgentState.BLOCKED_AWAITING_HELP, controller.state());

    // Clear help
    controller.clearHelp();
    assertEquals(AgentState.IDLE, controller.state());
  }
}
