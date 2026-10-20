package dev.ahmet.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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

    AgentObservation obsDeliver = new AgentObservation(
        null, true, null, null, null, null, false, "oak_log", 10, 5, 5, 0, 0, 0, 0, List.of(), 1000L
    );
    var inactiveCargo = ProductionGoalPolicy.SafetyContext.builder(0, null, null, 1)
        .taskActive(false).cargo(5).build();
    assertEquals(AgentGoal.REST_IDLE, evaluateAndApply(controller, obsDeliver, inactiveCargo));

    // Goal evaluation when active and cargo < requested -> HARVEST_RESOURCE
    AgentObservation obsHarvest = new AgentObservation(
        null, true, null, null, null, "task-1", true, "oak_log", 10, 2, 2, 0, 0, 0, 0, List.of(), 1000L
    );
    var activeTask = ProductionGoalPolicy.SafetyContext.builder(0, "task-1",
        UUID.fromString("088b2d3a-a941-4a46-b670-5cbdb4ddfb69"), 2).taskActive(true).targetAvailable(true).build();
    assertEquals(AgentGoal.HARVEST_RESOURCE, evaluateAndApply(controller, obsHarvest, activeTask));

    // A blocked route with no allowed clear or retry requests help.
    AgentObservation obsHelp = new AgentObservation(
        null, true, null, null, null, "task-1", true, "oak_log", 10, 2, 2, 0, 0, 3, 0, List.of(), 1000L
    );
    var blocked = ProductionGoalPolicy.SafetyContext.builder(0, "task-1",
        UUID.fromString("088b2d3a-a941-4a46-b670-5cbdb4ddfb69"), 3).taskActive(true).routeBlocked(true).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, evaluateAndApply(controller, obsHelp, blocked));
  }

  private static AgentGoal evaluateAndApply(AgentController controller, AgentObservation observation,
                                             ProductionGoalPolicy.SafetyContext context) {
    var selection = controller.evaluateGoal(observation, context);
    assertTrue(controller.applyGoal(selection, context));
    return selection.goal();
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

  @Test
  void sequenceAwareHelpClearCannotEraseAReplacementRequest() {
    AgentController controller = new AgentController(
        new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT));
    assertTrue(controller.recordHelpRequest(10_000));
    long firstRequest = controller.helpRequestSequence();
    assertTrue(controller.recordHelpRequest(41_000));
    long replacementRequest = controller.helpRequestSequence();

    assertFalse(controller.clearHelp(firstRequest));
    assertEquals(AgentState.BLOCKED_AWAITING_HELP, controller.state());
    assertTrue(controller.clearHelp(replacementRequest));
    assertEquals(AgentState.IDLE, controller.state());
  }
}
