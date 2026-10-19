package dev.ahmet;

import dev.ahmet.agent.ActionBudget;
import dev.ahmet.agent.ActionPreconditions;
import dev.ahmet.agent.AgentController;
import dev.ahmet.agent.AgentGoal;
import dev.ahmet.agent.AgentObservation;
import dev.ahmet.agent.ProductionGoalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ProductionGoalAdapterTest {
  private final ProductionGoalAdapter adapter = new ProductionGoalAdapter();
  private final AgentController controller = new AgentController(new ActionPreconditions((block, location) -> false, ActionBudget.DEFAULT));

  @Test void optOutDoesNotReadThreadStateOrFacts() {
    AtomicInteger reads = new AtomicInteger();
    var result = adapter.authorize(false, controller,
        () -> { reads.incrementAndGet(); return true; },
        () -> { reads.incrementAndGet(); return null; }, AgentGoal.HARVEST_RESOURCE);

    assertEquals(ProductionGoalAdapter.Status.DISABLED, result.status());
    assertEquals(0, reads.get());
    assertEquals(0, controller.productiveTransitionCount());
  }

  @Test void homeCommandAdmissionRejectsActiveCargoBeforeInvokingCancellation() {
    WoodTask task = new WoodTask();
    UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000002");
    task.start(owner, 8, "any");
    task.harvestOne("OAK_LOG");
    String taskId = task.taskId;
    String taskOwner = task.owner;
    int collected = task.collected;
    int cargo = task.cargo;
    long generation = controller.jobGeneration();
    AgentGoal currentGoal = controller.currentGoal();
    Object target = new Object();
    Object navigation = new Object();
    AtomicReference<Object> activeTarget = new AtomicReference<>(target);
    AtomicReference<Object> activeNavigation = new AtomicReference<>(navigation);
    AtomicInteger cancellationCalls = new AtomicInteger();

    boolean admitted = adapter.admitReturnHome(true, task.active, task.cargo, () -> {
      cancellationCalls.incrementAndGet();
      task.cancel();
      controller.nextGeneration();
      activeTarget.set(null);
      activeNavigation.set(null);
    });

    assertFalse(admitted);
    assertEquals(0, cancellationCalls.get());
    assertTrue(task.active);
    assertEquals(taskId, task.taskId);
    assertEquals(taskOwner, task.owner);
    assertEquals(collected, task.collected);
    assertEquals(cargo, task.cargo);
    assertEquals(generation, controller.jobGeneration());
    assertEquals(currentGoal, controller.currentGoal());
    assertSame(target, activeTarget.get());
    assertSame(navigation, activeNavigation.get());
  }

  @Test void legacyHomeCommandStillCancelsActiveTaskWhenGoalsAreDisabled() {
    WoodTask task = new WoodTask();
    task.start(UUID.fromString("00000000-0000-0000-0000-000000000003"), 8, "any");
    AtomicInteger cancellationCalls = new AtomicInteger();

    boolean admitted = adapter.admitReturnHome(false, task.active, task.cargo, () -> {
      cancellationCalls.incrementAndGet();
      task.cancel();
    });

    assertTrue(admitted);
    assertEquals(1, cancellationCalls.get());
    assertFalse(task.active);
  }

  @Test void homeBoundaryUsesTaskLimitAndIgnoresStickyIdleReturnForActiveJobs() {
    assertTrue(adapter.outsideHomeBoundary(true, 1025, 32));
    assertFalse(adapter.outsideHomeBoundary(true, 64, 32));
    assertTrue(adapter.outsideHomeBoundary(false, 17, 32));
    assertFalse(adapter.outsideHomeBoundary(false, 9, 32));
    assertTrue(adapter.requiresSafeReturn(false, true, false, false));
    assertTrue(adapter.requiresSafeReturn(false, false, true, false));
    assertFalse(adapter.requiresSafeReturn(true, true, true, false));
    assertTrue(adapter.requiresSafeReturn(true, true, true, true));
  }

  @Test void crossedHomeBoundaryCancelsOutwardRouteAndInstallsHomeTarget() {
    for (AgentGoal oldPurpose : List.of(AgentGoal.HARVEST_RESOURCE, AgentGoal.EXPLORE_RESOURCES)) {
      boolean outsideBoundary = adapter.outsideHomeBoundary(true, 33.0 * 33.0, 32);
      assertTrue(outsideBoundary);
      AtomicReference<Object> citizensTarget = new AtomicReference<>(new Object());
      AtomicReference<Object> selectedResource = new AtomicReference<>(new Object());
      AtomicReference<AgentGoal> purpose = new AtomicReference<>(oldPurpose);
      AtomicInteger cancellations = new AtomicInteger();
      AtomicInteger plans = new AtomicInteger();

      var action = adapter.ensureSafeHomeRoute(true, outsideBoundary, false, purpose.get(), true,
          () -> { cancellations.incrementAndGet(); citizensTarget.set(null); purpose.set(AgentGoal.REST_IDLE); },
          () -> { plans.incrementAndGet(); selectedResource.set(null); citizensTarget.set("home-waypoint"); purpose.set(AgentGoal.RETURN_HOME); });

      assertEquals(ProductionGoalAdapter.SafeHomeAction.PREEMPTED_AND_PLANNED, action);
      assertEquals(1, cancellations.get());
      assertEquals(1, plans.get());
      assertEquals("home-waypoint", citizensTarget.get());
      assertNull(selectedResource.get());
      assertEquals(AgentGoal.RETURN_HOME, purpose.get());
    }
  }

  @Test void safeHomeRouteContinuesAndReadyDeliveryPreemptsReturn() {
    AtomicInteger routeChanges = new AtomicInteger();

    var safeRoute = adapter.ensureSafeHomeRoute(true, true, false, AgentGoal.RETURN_HOME, true,
        routeChanges::incrementAndGet, routeChanges::incrementAndGet);
    assertEquals(ProductionGoalAdapter.SafeHomeAction.CONTINUE_HOME_ROUTE, safeRoute);

    var readyDelivery = adapter.ensureSafeHomeRoute(true, true, true, AgentGoal.HARVEST_RESOURCE, true,
        routeChanges::incrementAndGet, routeChanges::incrementAndGet);
    assertEquals(ProductionGoalAdapter.SafeHomeAction.NONE, readyDelivery);

    var legacy = adapter.ensureSafeHomeRoute(false, true, false, AgentGoal.HARVEST_RESOURCE, true,
        routeChanges::incrementAndGet, routeChanges::incrementAndGet);
    assertEquals(ProductionGoalAdapter.SafeHomeAction.NONE, legacy);
    assertEquals(0, routeChanges.get());
  }

  @Test void acceptedCommandAndChatAdmissionsClearPriorHomeRouteBeforeStartingNewTask() {
    for (String source : List.of("command", "chat")) {
      AtomicReference<Object> routeTarget = new AtomicReference<>("prior-home-waypoint");
      AtomicReference<AgentGoal> purpose = new AtomicReference<>(AgentGoal.RETURN_HOME);
      AtomicInteger clearCalls = new AtomicInteger();

      adapter.prepareTaskAdmission(true, true, () -> {
        clearCalls.incrementAndGet();
        routeTarget.set(null);
        purpose.set(AgentGoal.REST_IDLE);
      });
      routeTarget.set("new-resource-target");
      purpose.set(AgentGoal.HARVEST_RESOURCE);

      assertEquals(1, clearCalls.get(), source);
      assertEquals("new-resource-target", routeTarget.get(), source);
      assertEquals(AgentGoal.HARVEST_RESOURCE, purpose.get(), source);
    }
  }

  @Test void rejectedTaskAdmissionAndDefaultOffDoNotClearPriorRoute() {
    AtomicInteger clears = new AtomicInteger();
    adapter.prepareTaskAdmission(false, true, clears::incrementAndGet);
    adapter.prepareTaskAdmission(true, false, clears::incrementAndGet);
    assertEquals(0, clears.get());
  }

  @Test void mainThreadGuardRunsBeforeTheSingleSnapshotRead() {
    AtomicInteger reads = new AtomicInteger();
    var result = adapter.authorize(true, controller, () -> false,
        () -> { reads.incrementAndGet(); return facts(); }, AgentGoal.HARVEST_RESOURCE);

    assertEquals(ProductionGoalAdapter.Status.WRONG_THREAD, result.status());
    assertEquals(0, reads.get());
  }

  @Test void oneMainThreadSnapshotIsAppliedAndDrivesTheRequestedExecutorBranch() {
    AtomicInteger reads = new AtomicInteger();
    AtomicReference<ProductionGoalAdapter.Facts> captured = new AtomicReference<>();
    Supplier<ProductionGoalAdapter.Facts> source = () -> {
      reads.incrementAndGet();
      var value = facts();
      captured.set(value);
      return value;
    };

    var result = adapter.authorize(true, controller, () -> true, source, AgentGoal.HARVEST_RESOURCE);

    assertEquals(ProductionGoalAdapter.Status.APPLIED, result.status());
    assertEquals(AgentGoal.HARVEST_RESOURCE, result.goal());
    assertEquals(1, reads.get());
    assertSame(captured.get().safetyContext(), result.safetyContext());
    assertEquals(1, controller.productiveTransitionCount());
    assertEquals(AgentGoal.HARVEST_RESOURCE, controller.currentGoal());
  }

  @Test void branchMismatchDoesNotApplyOrSpendProductiveBudget() {
    var result = adapter.authorize(true, controller, () -> true,
        () -> facts(), AgentGoal.DELIVER_CARGO);

    assertEquals(ProductionGoalAdapter.Status.GOAL_MISMATCH, result.status());
    assertEquals(0, controller.productiveTransitionCount());
    assertEquals(AgentGoal.REST_IDLE, controller.currentGoal());
  }

  @Test void pendingTransactionBlocksButCommittedHistoryWithActiveCargoDoesNot() {
    var blocked = adapter.authorize(true, controller, () -> true,
        () -> facts(context -> context.transactionPending(true)), AgentGoal.REST_IDLE);
    assertEquals(ProductionGoalAdapter.Status.APPLIED, blocked.status());
    assertEquals(AgentGoal.REST_IDLE, blocked.goal());

    controller.nextGeneration();
    var committed = adapter.authorize(true, controller, () -> true,
        () -> facts(2, context -> context.committedJournalHistory(true)), AgentGoal.HARVEST_RESOURCE);
    assertEquals(ProductionGoalAdapter.Status.APPLIED, committed.status());
    assertEquals(AgentGoal.HARVEST_RESOURCE, committed.goal());
  }

  @Test void cancelledCargoIsHeldAndNeverAuthorizesDelivery() {
    var result = adapter.authorize(true, controller, () -> true,
        () -> facts(context -> context.taskActive(false).cargo(2).readyToDeliver(true)), AgentGoal.REST_IDLE);

    assertEquals(ProductionGoalAdapter.Status.APPLIED, result.status());
    assertEquals(AgentGoal.REST_IDLE, result.goal());
  }

  @Test void pendingScanCanAdvanceWithoutTreatingUnknownSourcesAsEmpty() {
    var pending = adapter.authorize(true, controller, () -> true,
        () -> facts(1, context -> context.targetAvailable(false).scanPending(true)), AgentGoal.HARVEST_RESOURCE);
    assertEquals(ProductionGoalAdapter.Status.APPLIED, pending.status());
    assertEquals(AgentGoal.HARVEST_RESOURCE, pending.goal());

    var unknown = adapter.authorize(true, controller, () -> true,
        () -> facts(2, context -> context.targetAvailable(false).scanPending(false).scanExhausted(false)), AgentGoal.REST_IDLE);
    assertEquals(ProductionGoalAdapter.Status.APPLIED, unknown.status());
    assertEquals(AgentGoal.REST_IDLE, unknown.goal());
  }

  @Test void missingOwnerRequestsHelpAndNeverSynthesizesAuthority() {
    var result = adapter.authorize(true, controller, () -> true,
        () -> facts(context -> context.ownerId(null)), AgentGoal.REQUEST_PLAYER_HELP);

    assertEquals(ProductionGoalAdapter.Status.APPLIED, result.status());
    assertEquals(null, result.safetyContext().ownerId());
    assertEquals(0, controller.productiveTransitionCount());
  }

  @Test void mismatchedTaskSnapshotIsRejectedBeforeControllerStateChanges() {
    String wrongTaskId = "other-task";
    var context = ProductionGoalPolicy.SafetyContext.builder(controller.jobGeneration(), wrongTaskId,
        UUID.fromString("00000000-0000-0000-0000-000000000001"), 1)
        .taskActive(true).cargo(1).targetAvailable(true).build();
    var observation = new AgentObservation(null, true, null, null, null, "task-1", true,
        "any", 4, 1, 1, 0, 0, 0, 0, List.of(), 0);

    var result = adapter.authorize(true, controller, () -> true,
        () -> new ProductionGoalAdapter.Facts(observation, context), AgentGoal.HARVEST_RESOURCE);

    assertEquals(ProductionGoalAdapter.Status.GOAL_MISMATCH, result.status());
    assertEquals(0, controller.productiveTransitionCount());
    assertEquals(AgentGoal.REST_IDLE, controller.currentGoal());
  }

  @Test void readyPartialCargoAuthorizesDeliveryEvenWhenProductiveBudgetIsFull() {
    for (int index = 0; index < ProductionGoalPolicy.MAX_PRODUCTIVE_TRANSITIONS; index++) {
      long step = index + 1L;
      boolean explore = (index & 1) == 1;
      var facts = facts(step, context -> context.targetAvailable(!explore)
          .explorationAllowed(explore).scanExhausted(explore).explorationAttempts(0));
      assertEquals(ProductionGoalAdapter.Status.APPLIED,
          adapter.authorize(true, controller, () -> true, () -> facts,
              explore ? AgentGoal.EXPLORE_RESOURCES : AgentGoal.HARVEST_RESOURCE).status());
    }

    var result = adapter.authorize(true, controller, () -> true,
        () -> facts(controllerStepAfterBudget(), context -> context.readyToDeliver(true)), AgentGoal.DELIVER_CARGO);
    assertEquals(ProductionGoalAdapter.Status.APPLIED, result.status());
    assertEquals(AgentGoal.DELIVER_CARGO, result.goal());
    assertEquals(ProductionGoalPolicy.MAX_PRODUCTIVE_TRANSITIONS, controller.productiveTransitionCount());
  }

  @Test void sameGlobalStepCannotApplyTwiceEvenAfterGenerationChanges() {
    var first = adapter.authorize(true, controller, () -> true,
        () -> facts(80, ignored -> { }), AgentGoal.HARVEST_RESOURCE);
    assertEquals(ProductionGoalAdapter.Status.APPLIED, first.status());
    controller.nextGeneration();

    var replay = adapter.authorize(true, controller, () -> true,
        () -> facts(80, ignored -> { }), AgentGoal.HARVEST_RESOURCE);
    assertEquals(ProductionGoalAdapter.Status.APPLY_REJECTED, replay.status());
    assertEquals(0, controller.productiveTransitionCount());
  }

  @Test void paperTickExtensionRemainsMonotonicAcrossSignedAndUnsignedWraps() {
    var steps = new ProductionGoalAdapter();
    long beforeSignedBoundary = steps.globalStepId(Integer.MAX_VALUE);
    long afterSignedBoundary = steps.globalStepId(Integer.MIN_VALUE);
    long beforeWrap = steps.globalStepId(-1);
    long afterWrap = steps.globalStepId(0);

    assertTrue(beforeSignedBoundary < afterSignedBoundary);
    assertTrue(afterSignedBoundary < beforeWrap);
    assertTrue(beforeWrap < afterWrap);
    assertEquals(afterWrap, steps.globalStepId(0));
  }

  private long controllerStepAfterBudget() { return 1000; }

  private ProductionGoalAdapter.Facts facts() {
    return facts(1, builder -> {
      builder.taskActive(true).cargo(1).targetAvailable(true);
    });
  }

  private ProductionGoalAdapter.Facts facts(java.util.function.Consumer<ProductionGoalPolicy.SafetyContext.Builder> amend) {
    return facts(1, amend);
  }

  private ProductionGoalAdapter.Facts facts(long step,
      java.util.function.Consumer<ProductionGoalPolicy.SafetyContext.Builder> amend) {
    String taskId = "task-1";
    UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var builder = ProductionGoalPolicy.SafetyContext.builder(controller.jobGeneration(), taskId, owner, step)
        .taskActive(true).cargo(1).targetAvailable(true);
    amend.accept(builder);
    var context = builder.build();
    var observation = new AgentObservation(null, true, null, null, null, taskId, context.taskActive(),
        "any", 4, 1, context.cargo(), 0, 0, 0, 0, List.of(), 0);
    return new ProductionGoalAdapter.Facts(observation, context);
  }
}
