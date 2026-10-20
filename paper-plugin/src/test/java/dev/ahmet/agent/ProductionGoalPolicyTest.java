package dev.ahmet.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductionGoalPolicyTest {
  private static final UUID OWNER = UUID.fromString("088b2d3a-a941-4a46-b670-5cbdb4ddfb69");

  @Test
  void inactiveTaskWithHeldCargoRestsUntilExplicitDelivery() {
    AgentController controller = controller();
    var context = context(0, "task-1", OWNER, 1).taskActive(false).cargo(4).build();

    assertEquals(AgentGoal.REST_IDLE, selectAndApply(controller, observation(), context));
  }

  @Test
  void pendingTransactionBlocksWorkButCommittedHistoryDoesNot() {
    AgentController controller = controller();
    var pending = context(0, "task-1", OWNER, 1).transactionPending(true).build();
    assertEquals(AgentGoal.REST_IDLE, selectAndApply(controller, observation(), pending));

    controller.nextGeneration();
    var committed = context(1, "task-2", OWNER, 2).committedJournalHistory(true).targetAvailable(true).build();
    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(), committed));

    controller.nextGeneration();
    var recovering = context(2, "task-3", OWNER, 3).recoveryBlocked(true).targetAvailable(true).build();
    assertEquals(AgentGoal.REST_IDLE, selectAndApply(controller, observation(), recovering));
  }

  @Test
  void partialDeliveryReadinessPrecedesProductiveBudget() {
    AgentController controller = controller();
    exhaustProductiveBudget(controller);
    var ready = context(0, "task-1", OWNER, 80).readyToDeliver(true).cargo(2).build();

    assertEquals(AgentGoal.DELIVER_CARGO, selectAndApply(controller, observation(), ready));
  }

  @Test
  void activePartialCargoBelowReadinessContinuesHarvesting() {
    AgentController controller = controller();
    var partial = context(0, "task-1", OWNER, 1).cargo(2).targetAvailable(true).build();

    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(), partial));
  }

  @Test
  void validNavigationSurvivesExhaustedRouteClearBudget() {
    AgentController controller = controller();
    var navigating = context(0, "task-1", OWNER, 1).navigationInProgress(true)
        .routeBlocked(false).routeClears(3).build();

    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(navigating), navigating));

    var unknownAndStationary = context(0, "task-1", OWNER, 2).build();
    assertEquals(AgentGoal.REST_IDLE,
        selectAndApply(controller, observation(unknownAndStationary), unknownAndStationary));
  }

  @Test
  void fourthExplorationKeepsItsPurposeWhileMovingAndOnArrival() {
    AgentController controller = controller();
    var start = context(0, "task-1", OWNER, 1).scanExhausted(true).explorationAllowed(true)
        .explorationAttempts(3).build();
    assertEquals(AgentGoal.EXPLORE_RESOURCES, selectAndApply(controller, observation(start), start));
    assertEquals(1, controller.productiveTransitionCount());

    var moving = context(0, "task-1", OWNER, 2).navigationInProgress(true)
        .navigationPurpose(AgentGoal.EXPLORE_RESOURCES).explorationAllowed(false).explorationAttempts(4).build();
    assertEquals(AgentGoal.EXPLORE_RESOURCES, selectAndApply(controller, observation(moving), moving));
    assertEquals(1, controller.productiveTransitionCount());

    var retry = context(0, "task-1", OWNER, 3).navigationInProgress(true).routeBlocked(true)
        .routeRetryAvailable(true).navigationPurpose(AgentGoal.EXPLORE_RESOURCES)
        .explorationAllowed(false).explorationAttempts(4).build();
    assertEquals(AgentGoal.EXPLORE_RESOURCES, selectAndApply(controller, observation(retry), retry));
    assertEquals(1, controller.productiveTransitionCount());

    var arrival = context(0, "task-1", OWNER, 4).navigationInProgress(true)
        .navigationPurpose(AgentGoal.EXPLORE_RESOURCES).explorationAllowed(false).explorationAttempts(4).build();
    assertEquals(AgentGoal.EXPLORE_RESOURCES, selectAndApply(controller, observation(arrival), arrival));
    assertEquals(1, controller.productiveTransitionCount());
  }

  @Test
  void activeHomeBoundaryReturnPrecedesHarvestButReadyDeliveryStillWins() {
    AgentController controller = controller();
    var outside = context(0, "task-1", OWNER, 1).requiredSafeReturn(true).targetAvailable(true).build();
    assertEquals(AgentGoal.RETURN_HOME, selectAndApply(controller, observation(outside), outside));

    controller.nextGeneration();
    var readyOutside = context(1, "task-2", OWNER, 2).requiredSafeReturn(true).readyToDeliver(true)
        .cargo(1).build();
    assertEquals(AgentGoal.DELIVER_CARGO, selectAndApply(controller, observation(readyOutside), readyOutside));
  }

  @Test
  void activeReturnHomePurposePersistsAfterCrossingBackInsideTheBoundary() {
    AgentController controller = controller();
    var returning = context(0, "task-1", OWNER, 1).navigationInProgress(true)
        .navigationPurpose(AgentGoal.RETURN_HOME).requiredSafeReturn(false).targetAvailable(true).build();
    assertEquals(AgentGoal.RETURN_HOME, selectAndApply(controller, observation(returning), returning));

    controller.nextGeneration();
    var ready = context(1, "task-2", OWNER, 2).navigationInProgress(true)
        .navigationPurpose(AgentGoal.RETURN_HOME).readyToDeliver(true).cargo(1).build();
    assertEquals(AgentGoal.DELIVER_CARGO, selectAndApply(controller, observation(ready), ready));
  }

  @Test
  void staleIdleReturnIntentDoesNotOverrideANewActiveTask() {
    AgentController controller = controller();
    var activeTask = context(0, "task-1", OWNER, 1).returningHome(true).targetAvailable(true)
        .requiredSafeReturn(false).build();
    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(activeTask), activeTask));
  }

  @Test
  void authoritativeSafetyRestSupersedesAStaleObservation() {
    AgentController controller = controller();
    var working = context(0, "task-1", OWNER, 1).targetAvailable(true).build();
    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(working), working));

    var pending = context(0, "task-1", OWNER, 2).transactionPending(true).build();
    var staleInactiveObservation = observation(context(0, null, null, 2).taskActive(false).build());
    var pendingSelection = controller.evaluateGoal(staleInactiveObservation, pending);
    assertNotNull(pendingSelection);
    assertEquals(AgentGoal.REST_IDLE, pendingSelection.goal());
    assertTrue(controller.applyGoal(pendingSelection, pending));
    assertEquals(AgentGoal.REST_IDLE, controller.currentGoal());
    assertEquals(1, controller.productiveTransitionCount());

    var recoveryBlocked = context(0, "task-1", OWNER, 3).recoveryBlocked(true).build();
    var staleOtherTaskObservation = observation(context(0, "task-2", OWNER, 3).build());
    var recoverySelection = controller.evaluateGoal(staleOtherTaskObservation, recoveryBlocked);
    assertNotNull(recoverySelection);
    assertEquals(AgentGoal.REST_IDLE, recoverySelection.goal());
    assertTrue(controller.applyGoal(recoverySelection, recoveryBlocked));
    assertEquals(AgentGoal.REST_IDLE, controller.currentGoal());
  }

  @Test
  void blockedRouteNeedsLiveOfferedClearOrRequestsHelp() {
    AgentController controller = controller();
    var clearOffered = context(0, "task-1", OWNER, 1).routeBlocked(true).clearOfferedByLiveJev(true).build();
    assertEquals(AgentGoal.CLEAR_OBSTRUCTION, selectAndApply(controller, observation(), clearOffered));

    controller.nextGeneration();
    var noClear = context(1, "task-2", OWNER, 2).routeBlocked(true).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, selectAndApply(controller, observation(), noClear));
  }

  @Test
  void exhaustedWatchdogRetryBudgetRequestsHelpWhileActualRetryRemainsProductive() {
    AgentController controller = controller();
    var giveUp = context(0, "task-1", OWNER, 1).routeBlocked(true)
        .routeRetryAvailable(false).navigationPurpose(AgentGoal.HARVEST_RESOURCE).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, selectAndApply(controller, observation(), giveUp));

    controller.nextGeneration();
    var retry = context(1, "task-2", OWNER, 2).routeBlocked(true)
        .routeRetryAvailable(true).navigationPurpose(AgentGoal.HARVEST_RESOURCE).build();
    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(), retry));
  }

  @Test
  void criticalDeliveryAndSafeHomeGoalsPreemptExpiredForeignRouteWait() {
    AgentController controller = controller();
    var delivery = context(0, "task-1", OWNER, 1).foreignNavigationWaitExpired(true)
        .readyToDeliver(true).cargo(1).build();
    assertEquals(AgentGoal.DELIVER_CARGO, selectAndApply(controller, observation(), delivery));

    controller.nextGeneration();
    var safeHome = context(1, "task-2", OWNER, 2).foreignNavigationWaitExpired(true)
        .requiredSafeReturn(true).routeRetryAvailable(true).build();
    assertEquals(AgentGoal.RETURN_HOME, selectAndApply(controller, observation(), safeHome));

    controller.nextGeneration();
    var inBoundWait = context(2, "task-3", OWNER, 3).foreignNavigationWaitExpired(true).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, selectAndApply(controller, observation(), inBoundWait));
  }

  @Test
  void pendingAndExhaustedScansHaveDifferentGoals() {
    AgentController controller = controller();
    var pending = context(0, "task-1", OWNER, 1).scanPending(true).build();
    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(), pending));

    controller.nextGeneration();
    var exhausted = context(1, "task-2", OWNER, 2).scanExhausted(true).explorationAllowed(true).build();
    assertEquals(AgentGoal.EXPLORE_RESOURCES, selectAndApply(controller, observation(), exhausted));
  }

  @Test
  void missingTaskOrOwnerFailsClosed() {
    AgentController controller = controller();
    var noOwner = context(0, "task-1", null, 1).targetAvailable(true).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, selectAndApply(controller, observation(), noOwner));

    controller.nextGeneration();
    var noTask = context(1, null, OWNER, 2).targetAvailable(true).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, selectAndApply(controller, observation(), noTask));
  }

  @Test
  void staleTaskOwnerGenerationAndStepSelectionsAreRejectedWithoutBudgetUse() {
    AgentController controller = controller();
    var selectedContext = context(0, "task-1", OWNER, 1).targetAvailable(true).build();
    var selection = controller.evaluateGoal(observation(selectedContext), selectedContext);

    assertFalse(controller.applyGoal(selection, selectedContext.withTaskId("task-other")));
    assertFalse(controller.applyGoal(selection, selectedContext.withOwner(UUID.randomUUID())));
    assertFalse(controller.applyGoal(selection, selectedContext.withGeneration(9)));
    assertFalse(controller.applyGoal(selection, selectedContext.withServerStep(2)));
    assertEquals(0, controller.productiveTransitionCount());
    assertTrue(controller.applyGoal(selection, selectedContext));
    assertEquals(1, controller.productiveTransitionCount());
    assertFalse(controller.applyGoal(selection, selectedContext));
  }

  @Test
  void onlyOneSelectionCanBeAppliedForAServerStep() {
    AgentController controller = controller();
    var first = context(0, "task-1", OWNER, 1).targetAvailable(true).build();
    var second = context(0, "task-1", OWNER, 1).targetAvailable(false).scanPending(true).build();
    var firstSelection = controller.evaluateGoal(observation(first), first);
    var secondSelection = controller.evaluateGoal(observation(second), second);

    assertTrue(controller.applyGoal(firstSelection, first));
    assertFalse(controller.applyGoal(secondSelection, second));
  }

  @Test
  void selectionCannotBeConstructedByCallers() {
    assertEquals(0, ProductionGoalPolicy.Selection.class.getConstructors().length,
        "only the policy may issue an applicable selection");
    assertTrue(java.util.Arrays.stream(ProductionGoalPolicy.Selection.class.getDeclaredConstructors())
        .allMatch(constructor -> java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())));
  }

  @Test
  void mismatchedObservationTaskOrActivityIsRejected() {
    AgentController controller = controller();
    var taskTwo = context(0, "task-2", OWNER, 1).routeBlocked(true).clearOfferedByLiveJev(true).build();
    var staleTaskCounters = observation(context(0, "task-1", OWNER, 1).build(), 0, 0);
    assertNull(controller.evaluateGoal(staleTaskCounters, taskTwo));

    var inactive = context(0, "task-1", OWNER, 1).taskActive(false).build();
    var staleActivity = observation(context(0, "task-1", OWNER, 1).build());
    assertNull(controller.evaluateGoal(staleActivity, inactive));
  }

  @Test
  void matchedTaskUsesOnlyItsOwnClearAndExplorationCounters() {
    AgentController controller = controller();
    var blocked = context(0, "task-2", OWNER, 1).routeBlocked(true).clearOfferedByLiveJev(true)
        .routeClears(0).jobClears(0).build();
    assertNull(controller.evaluateGoal(observation(context(0, "task-1", OWNER, 1).build(), 3, 0), blocked));
    var clear = controller.evaluateGoal(observation(blocked, 3, 0), blocked);
    assertEquals(AgentGoal.CLEAR_OBSTRUCTION, clear.goal(), "matching task's fresh route count permits clear");

    var routeExhausted = context(0, "task-2", OWNER, 2).routeBlocked(true).clearOfferedByLiveJev(true)
        .routeClears(3).jobClears(0).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP,
        controller.evaluateGoal(observation(routeExhausted, 0, 0), routeExhausted).goal());

    controller = controller();
    var exhaustedScan = context(0, "task-3", OWNER, 3).scanExhausted(true).explorationAllowed(true)
        .explorationAttempts(4).build();
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP,
        controller.evaluateGoal(observation(exhaustedScan, 0, 0), exhaustedScan).goal());

    var freshScan = context(0, "task-3", OWNER, 4).scanExhausted(true).explorationAllowed(true).build();
    var explore = controller.evaluateGoal(observation(freshScan, 0, 4), freshScan);
    assertEquals(AgentGoal.EXPLORE_RESOURCES, explore.goal());
  }

  @Test
  void absentTargetPendingScanAndExhaustionSelectsSafeRest() {
    AgentController controller = controller();
    var unknownScan = context(0, "task-1", OWNER, 1).build();

    assertEquals(AgentGoal.REST_IDLE, controller.evaluateGoal(observation(unknownScan), unknownScan).goal());
  }

  @Test
  void oneApplicationPerServerStepIsGlobalAcrossGenerations() {
    AgentController controller = controller();
    var first = context(0, "task-1", OWNER, 1).targetAvailable(true).build();
    assertTrue(controller.applyGoal(controller.evaluateGoal(observation(first), first), first));

    controller.nextGeneration();
    var sameStepNewGeneration = context(1, "task-2", OWNER, 1).targetAvailable(true).build();
    assertFalse(controller.applyGoal(controller.evaluateGoal(observation(sameStepNewGeneration), sameStepNewGeneration),
        sameStepNewGeneration));
    assertEquals(0, controller.productiveTransitionCount());

    var nextStep = sameStepNewGeneration.withServerStep(2);
    assertTrue(controller.applyGoal(controller.evaluateGoal(observation(nextStep), nextStep), nextStep));
    assertEquals(1, controller.productiveTransitionCount());
  }

  @Test
  void sameProductiveGoalDoesNotConsumeTransitionBudgetAndThirtyThirdIsBlocked() {
    AgentController controller = controller();
    var context = context(0, "task-1", OWNER, 1).targetAvailable(true).build();
    for (int i = 0; i < 100; i++) {
      context = context.withServerStep(i + 1);
      assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(), context));
    }
    assertEquals(1, controller.productiveTransitionCount());

    controller.nextGeneration();
    var cycling = context(1, "task-2", OWNER, 101).scanExhausted(true).explorationAllowed(true).build();
    for (int i = 0; i < ProductionGoalPolicy.MAX_PRODUCTIVE_TRANSITIONS; i++) {
      cycling = cycling.withServerStep(102 + i);
      cycling = cycling.withTargetAvailable(i % 2 == 0).withExplorationAllowed(i % 2 != 0);
      assertTrue(controller.applyGoal(controller.evaluateGoal(observation(cycling), cycling), cycling));
    }
    assertEquals(ProductionGoalPolicy.MAX_PRODUCTIVE_TRANSITIONS, controller.productiveTransitionCount());
    var exhausted = cycling.withServerStep(500).withTargetAvailable(true);
    assertEquals(AgentGoal.REQUEST_PLAYER_HELP, selectAndApply(controller, observation(), exhausted));

    var delivery = exhausted.withServerStep(501).withReadyToDeliver(true).withCargo(1);
    assertEquals(AgentGoal.DELIVER_CARGO, selectAndApply(controller, observation(), delivery));
  }

  @Test
  void newGenerationResetsProductiveBudget() {
    AgentController controller = controller();
    exhaustProductiveBudget(controller);
    assertEquals(ProductionGoalPolicy.MAX_PRODUCTIVE_TRANSITIONS, controller.productiveTransitionCount());

    controller.nextGeneration();
    var context = context(1, "task-2", OWNER, 200).targetAvailable(true).build();
    assertEquals(AgentGoal.HARVEST_RESOURCE, selectAndApply(controller, observation(), context));
    assertEquals(1, controller.productiveTransitionCount());
  }

  @Test
  void returnHomeRequiresAnExplicitAuthoritativeRequest() {
    AgentController controller = controller();
    var idle = context(0, null, null, 1).taskActive(false).build();
    assertEquals(AgentGoal.REST_IDLE, selectAndApply(controller, observation(), idle));

    controller.nextGeneration();
    var returning = context(1, null, null, 2).taskActive(false).returningHome(true).build();
    assertEquals(AgentGoal.RETURN_HOME, selectAndApply(controller, observation(), returning));
  }

  private static AgentGoal selectAndApply(AgentController controller, AgentObservation observation,
                                          ProductionGoalPolicy.SafetyContext context) {
    var currentObservation = observation(context, observation.routeClears(), observation.explorationAttempts());
    var selection = controller.evaluateGoal(currentObservation, context);
    assertTrue(controller.applyGoal(selection, context));
    return selection.goal();
  }

  private static void exhaustProductiveBudget(AgentController controller) {
    var context = context(0, "task-1", OWNER, 1).targetAvailable(true).build();
    for (int i = 0; i < ProductionGoalPolicy.MAX_PRODUCTIVE_TRANSITIONS; i++) {
      context = context.withServerStep(i + 1).withTargetAvailable(i % 2 == 0)
          .withScanExhausted(i % 2 != 0).withExplorationAllowed(i % 2 != 0);
      assertTrue(controller.applyGoal(controller.evaluateGoal(observation(), context), context));
    }
  }

  private static AgentController controller() {
    return new AgentController(new ActionPreconditions((block, location) -> false, ActionBudget.DEFAULT));
  }

  private static AgentObservation observation() {
    return observationWithRouteClears(0);
  }

  private static AgentObservation observation(ProductionGoalPolicy.SafetyContext context) {
    return observation(context, 0, 0);
  }

  private static AgentObservation observation(ProductionGoalPolicy.SafetyContext context, int routeClears,
                                              int explorationAttempts) {
    return new AgentObservation(null, true, null, null, null, context.taskId(), context.taskActive(), null,
        8, 1, 1, 0, 0, routeClears, explorationAttempts, List.of(), 1000L);
  }

  private static AgentObservation observationWithRouteClears(int routeClears) {
    return new AgentObservation(null, true, null, null, null, "task-1", true, null,
        8, 1, 1, 0, 0, routeClears, 0, List.of(), 1000L);
  }

  private static ProductionGoalPolicy.SafetyContext.Builder context(long generation, String taskId, UUID ownerId, long step) {
    return ProductionGoalPolicy.SafetyContext.builder(generation, taskId, ownerId, step).taskActive(true);
  }
}
