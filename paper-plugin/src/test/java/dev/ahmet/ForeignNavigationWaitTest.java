package dev.ahmet;

import dev.ahmet.agent.ActionBudget;
import dev.ahmet.agent.ActionPreconditions;
import dev.ahmet.agent.AgentController;
import dev.ahmet.agent.AgentState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ForeignNavigationWaitTest {
  private static final UUID OWNER = UUID.fromString("088b2d3a-a941-4a46-b670-5cbdb4ddfb69");

  @Test
  void waitsOneHundredDistinctTicksAndOffersOneAttemptPerTickUntilAccepted() {
    ForeignNavigationWait wait = new ForeignNavigationWait();
    assertEquals(ForeignNavigationWait.Status.WAITING, wait.observe(true, "job-a", OWNER, 4, 100, true).status());
    assertEquals(ForeignNavigationWait.Status.WAITING, wait.observe(true, "job-a", OWNER, 4, 100, true).status());
    for (int tick = 101; tick < 200; tick++) {
      assertEquals(ForeignNavigationWait.Status.WAITING,
          wait.observe(true, "job-a", OWNER, 4, tick, true).status());
    }
    assertEquals(ForeignNavigationWait.Status.HELP_DUE,
        wait.observe(true, "job-a", OWNER, 4, 200, true).status());
    assertTrue(wait.beginHelpAttempt(200));
    assertFalse(wait.beginHelpAttempt(200), "same-tick calls must not flood help admission");
    assertEquals(ForeignNavigationWait.Status.HELP_DUE,
        wait.observe(true, "job-a", OWNER, 4, 200, true).status());
    assertEquals(ForeignNavigationWait.Status.HELP_DUE,
        wait.observe(true, "job-a", OWNER, 4, 201, true).status());
    assertTrue(wait.beginHelpAttempt(201), "a rejected controller cooldown must remain eligible next tick");
  }

  @Test
  void skippedTicksCountAsElapsedButRepeatedTicksDoNot() {
    ForeignNavigationWait wait = new ForeignNavigationWait();
    wait.observe(true, "job-a", OWNER, 4, 1000, true);
    assertEquals(ForeignNavigationWait.Status.WAITING, wait.observe(true, "job-a", OWNER, 4, 1000, true).status());
    assertEquals(ForeignNavigationWait.Status.WAITING, wait.observe(true, "job-a", OWNER, 4, 1099, true).status());
    assertEquals(ForeignNavigationWait.Status.HELP_DUE, wait.observe(true, "job-a", OWNER, 4, 1100, true).status());
  }

  @Test
  void cooldownRejectionRetriesUntilControllerAcceptsAndAcknowledgesHelp() {
    AgentController controller = controller();
    assertTrue(controller.recordHelpRequest(10_000));
    long unrelatedSequence = controller.helpRequestSequence();
    ForeignNavigationWait wait = new ForeignNavigationWait();
    wait.observe(true, "job-a", OWNER, 4, 0, true);
    wait.observe(true, "job-a", OWNER, 4, 100, true);

    assertTrue(wait.beginHelpAttempt(100));
    assertFalse(controller.recordHelpRequest(15_000));
    assertEquals(unrelatedSequence, controller.helpRequestSequence());
    assertTrue(wait.beginHelpAttempt(101));
    assertTrue(controller.recordHelpRequest(40_000));
    long foreignSequence = controller.helpRequestSequence();
    wait.markHelpAccepted(foreignSequence);
    assertEquals(ForeignNavigationWait.Status.HELP_ACCEPTED,
        wait.observe(true, "job-a", OWNER, 4, 102, true).status());

    var released = wait.observe(true, "job-a", OWNER, 4, 103, false);
    assertEquals(foreignSequence, released.helpSequenceToClear());
    assertTrue(controller.clearHelp(released.helpSequenceToClear()));
    assertEquals(AgentState.IDLE, controller.state());
  }

  @Test
  void releaseCannotClearAHelpRequestThatSupersededTheForeignRequest() {
    AgentController controller = controller();
    ForeignNavigationWait wait = new ForeignNavigationWait();
    wait.observe(true, "job-a", OWNER, 4, 0, true);
    wait.observe(true, "job-a", OWNER, 4, 100, true);
    assertTrue(wait.beginHelpAttempt(100));
    assertTrue(controller.recordHelpRequest(40_000));
    long foreignSequence = controller.helpRequestSequence();
    wait.markHelpAccepted(foreignSequence);

    assertTrue(controller.recordHelpRequest(71_000));
    assertNotEquals(foreignSequence, controller.helpRequestSequence());
    var released = wait.observe(true, "job-a", OWNER, 4, 101, false);
    assertEquals(foreignSequence, released.helpSequenceToClear());
    assertFalse(controller.clearHelp(released.helpSequenceToClear()));
    assertEquals(AgentState.BLOCKED_AWAITING_HELP, controller.state());
  }

  @Test
  void safetyBlockRestartsElapsedWaitWithoutDroppingAcceptedHelpAndIdentityChangesResetIt() {
    ForeignNavigationWait wait = new ForeignNavigationWait();
    wait.observe(true, "job-a", OWNER, 4, 0, true);
    wait.observe(true, "job-a", OWNER, 4, 100, true);
    assertTrue(wait.beginHelpAttempt(100));
    wait.markHelpAccepted(1);
    wait.resetWaitForSafetyBlock();
    assertEquals(ForeignNavigationWait.Status.WAITING, wait.observe(true, "job-a", OWNER, 4, 500, true).status());
    assertEquals(ForeignNavigationWait.Status.WAITING, wait.observe(true, "job-a", OWNER, 4, 599, true).status());
    assertEquals(ForeignNavigationWait.Status.HELP_ACCEPTED, wait.observe(true, "job-a", OWNER, 4, 600, true).status());
    assertEquals(1L, wait.observe(true, "job-a", OWNER, 4, 601, false).helpSequenceToClear());

    wait.observe(true, "job-a", OWNER, 4, 700, true);
    assertEquals(ForeignNavigationWait.Status.WAITING, wait.observe(true, "job-b", OWNER, 5, 701, true).status());
    assertNull(wait.observe(false, "job-b", OWNER, 5, 702, false).helpSequenceToClear());
  }

  private static AgentController controller() {
    return new AgentController(new ActionPreconditions((block, location) -> false, ActionBudget.DEFAULT));
  }
}
