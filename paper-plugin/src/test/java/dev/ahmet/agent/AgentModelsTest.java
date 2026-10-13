package dev.ahmet.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AgentModelsTest {

  @Test
  void testOfferedActionValidation() {
    OfferedAction valid = OfferedAction.of("target_a", ActionType.GATHER_TARGET, "Walk to tree");
    assertEquals("target_a", valid.id());
    assertEquals(ActionType.GATHER_TARGET, valid.type());
    assertNull(valid.targetData());

    OfferedAction withData = OfferedAction.of("clear_b", ActionType.CLEAR_OBSTRUCTION, "Clear leaf", "extra");
    assertEquals("extra", withData.targetData());

    // Invalid action IDs
    assertThrows(IllegalArgumentException.class, () -> OfferedAction.of("Target_A", ActionType.GATHER_TARGET, "desc"));
    assertThrows(IllegalArgumentException.class, () -> OfferedAction.of("target-a", ActionType.GATHER_TARGET, "desc"));
    assertThrows(IllegalArgumentException.class, () -> OfferedAction.of("", ActionType.GATHER_TARGET, "desc"));
    assertThrows(IllegalArgumentException.class, () -> OfferedAction.of("this_id_is_way_too_long_to_be_allowed_under_the_thirty_two_char_limit", ActionType.WAIT, "desc"));
    assertThrows(NullPointerException.class, () -> OfferedAction.of(null, ActionType.WAIT, "desc"));
    assertThrows(NullPointerException.class, () -> OfferedAction.of("wait", null, "desc"));
    assertThrows(NullPointerException.class, () -> OfferedAction.of("wait", ActionType.WAIT, null));
  }

  @Test
  void testActionBudgetEnforcement() {
    ActionBudget budget = ActionBudget.DEFAULT;
    assertEquals(3, budget.maxRouteClears());
    assertEquals(12, budget.maxJobClears());
    assertEquals(3, budget.maxRouteRetries());
    assertEquals(4, budget.maxExplorationAttempts());
    assertEquals(2, budget.maxConsecutiveEmptyScans());
    assertEquals(30_000L, budget.helpCooldownMs());

    assertTrue(budget.canClearObstruction(0, 0));
    assertTrue(budget.canClearObstruction(2, 11));
    assertFalse(budget.canClearObstruction(3, 5));
    assertFalse(budget.canClearObstruction(1, 12));

    assertTrue(budget.canRetryRoute(2));
    assertFalse(budget.canRetryRoute(3));

    assertTrue(budget.canExplore(3));
    assertFalse(budget.canExplore(4));

    assertFalse(budget.isScanExhausted(1));
    assertTrue(budget.isScanExhausted(2));

    assertFalse(budget.isWaitExhausted(2));
    assertTrue(budget.isWaitExhausted(3));

    assertTrue(budget.canRequestHelp(0L, 30_000L));
    assertFalse(budget.canRequestHelp(10_000L, 35_000L));

    assertThrows(IllegalArgumentException.class, () -> new ActionBudget(0, 12, 3, 4, 2, 3, 30_000L));
    assertThrows(IllegalArgumentException.class, () -> new ActionBudget(3, 0, 3, 4, 2, 3, 30_000L));
  }

  @Test
  void testExecutionResultFactories() {
    ExecutionResult success = ExecutionResult.success("Harvested oak", "10,64,5", 1);
    assertTrue(success.isSuccess());
    assertEquals(ExecutionResult.Status.SUCCESS, success.status());
    assertEquals("Harvested oak", success.message());
    assertEquals("10,64,5", success.mutatedTarget());
    assertEquals(1, success.cargoDelta());

    ExecutionResult precondition = ExecutionResult.preconditionFailed("Target block changed");
    assertFalse(precondition.isSuccess());
    assertEquals(ExecutionResult.Status.PRECONDITION_FAILED, precondition.status());

    ExecutionResult retry = ExecutionResult.retryNeeded("Navigation stalled");
    assertFalse(retry.isSuccess());
    assertEquals(ExecutionResult.Status.RETRY_NEEDED, retry.status());

    ExecutionResult blocked = ExecutionResult.blocked("Path completely obstructed");
    assertFalse(blocked.isSuccess());
    assertEquals(ExecutionResult.Status.BLOCKED, blocked.status());

    ExecutionResult critical = ExecutionResult.criticalError("Journal write failed");
    assertFalse(critical.isSuccess());
    assertEquals(ExecutionResult.Status.CRITICAL_ERROR, critical.status());
  }

  @Test
  void testAgentObservationImmutability() {
    List<OfferedAction> actions = new ArrayList<>();
    actions.add(OfferedAction.of("target_a", ActionType.GATHER_TARGET, "Walk to oak"));

    AgentObservation obs = new AgentObservation(
        null, false, null, null, null,
        "task-1", true, "oak_log", 10, 2, 2, 0, 1, 0, 0,
        actions, 1000L
    );

    assertEquals(1, obs.candidateActions().size());
    actions.add(OfferedAction.of("wait", ActionType.WAIT, "Wait"));
    assertEquals(1, obs.candidateActions().size());
    assertThrows(UnsupportedOperationException.class, () -> obs.candidateActions().add(OfferedAction.of("retry", ActionType.RETRY_ROUTE, "Retry")));
  }
}
