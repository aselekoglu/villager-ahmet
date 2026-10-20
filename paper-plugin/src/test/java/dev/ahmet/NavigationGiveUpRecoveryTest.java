package dev.ahmet;

import dev.ahmet.agent.AgentGoal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NavigationGiveUpRecoveryTest {
  private static final long NOW = 50_000;

  @Test
  void exhaustedHarvestCompletesTargetExclusionCooldownAndHelpAfterPolicyHelpSelection() {
    var effects = new RecordingEffects(false);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(false, false, true, false, true, false,
            true, AgentGoal.REQUEST_PLAYER_HELP, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.TASK_RETRY, result);
    assertEquals(List.of("fail-target@50000", "clear-tree", "retry@52000",
        "partial-policy:false", "help:Ahmet üç kez yol bulamadı. Yolu açıp tekrar deneyebilirim."), effects.events);
  }

  @Test
  void activeHarvestStillReportsTerminalFailureWhenPolicyPrefersSafeHome() {
    var effects = new RecordingEffects(false);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(false, false, true, false, true, false,
            true, AgentGoal.RETURN_HOME, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.TASK_RETRY, result);
    assertEquals(List.of("fail-target@50000", "clear-tree", "retry@52000",
        "partial-policy:false", "help:Ahmet üç kez yol bulamadı. Yolu açıp tekrar deneyebilirim."), effects.events);
  }

  @Test
  void exhaustedExplorationCompletesTargetAndCooldownBeforePolicyHelp() {
    var effects = new RecordingEffects(false);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(true, false, true, false, false, false,
            true, AgentGoal.REQUEST_PLAYER_HELP, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.EXPLORATION, result);
    assertEquals(List.of("clear-exploration", "retry@51000",
        "help:Yol bulamadığım için kaynakları keşfedemiyorum; çevredeki engeli kontrol eder misin?"), effects.events);
  }

  @Test
  void activeTaskExplorationFailureReportsHelpEvenWhenPolicyKeepsSafeHomePriority() {
    var effects = new RecordingEffects(false);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(true, false, true, false, false, false,
            true, AgentGoal.RETURN_HOME, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.EXPLORATION, result);
    assertEquals(List.of("clear-exploration", "retry@51000",
        "help:Yol bulamadığım için kaynakları keşfedemiyorum; çevredeki engeli kontrol eder misin?"), effects.events);
  }

  @Test
  void exhaustedHomeRouteStillClearsWaypointAndAdvancesAttemptWhenHomeHasPolicyPriority() {
    var effects = new RecordingEffects(false);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(false, true, false, false, false, false,
            true, AgentGoal.RETURN_HOME, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.HOME_STEP, result);
    assertEquals(List.of("clear-home-step", "home-attempt+1", "retry@51000"), effects.events);
  }

  @Test
  void activeTaskHomeFailureReportsHelpAfterAdvancingTheSafeHomeAttempt() {
    var effects = new RecordingEffects(false);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(false, true, true, false, false, false,
            true, AgentGoal.RETURN_HOME, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.HOME_STEP, result);
    assertEquals(List.of("clear-home-step", "home-attempt+1", "retry@51000",
        "help:Eve giden yolu bulamadım; yolumu açabilir misin?"), effects.events);
  }

  @Test
  void readyCargoKeepsDeliveryPriorityAndReportsTheFailedChestRoute() {
    var effects = new RecordingEffects(false);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(false, false, true, true, false, true,
            true, AgentGoal.DELIVER_CARGO, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.DELIVERY_RETRY, result);
    assertEquals(List.of("retry@80000", "help:Sandığa giden yolu bulamadım. Sandık çevresindeki yolu açın."), effects.events);
  }

  @Test
  void partialDeliveryOwnsRecoveryMessageAndSuppressesDuplicateHelp() {
    var effects = new RecordingEffects(true);
    var result = NavigationGiveUpRecovery.apply(
        new NavigationGiveUpRecovery.Facts(false, false, true, false, true, true,
            true, AgentGoal.REQUEST_PLAYER_HELP, NOW), effects);

    assertEquals(NavigationGiveUpRecovery.Outcome.PARTIAL_DELIVERY, result);
    assertEquals(List.of("fail-target@50000", "clear-tree", "retry@52000", "partial-policy:true"), effects.events);
  }

  private static final class RecordingEffects implements NavigationGiveUpRecovery.Effects {
    private final List<String> events = new ArrayList<>();
    private final boolean startPartialDelivery;

    private RecordingEffects(boolean startPartialDelivery) { this.startPartialDelivery = startPartialDelivery; }
    @Override public void clearExplorationTarget() { events.add("clear-exploration"); }
    @Override public void clearHomeStepTarget() { events.add("clear-home-step"); }
    @Override public void incrementHomeStepAttempt() { events.add("home-attempt+1"); }
    @Override public void markTreeTargetFailed(long nowMillis) { events.add("fail-target@" + nowMillis); }
    @Override public void clearTreeTarget() { events.add("clear-tree"); }
    @Override public void setRetryAfter(long retryAtMillis) { events.add("retry@" + retryAtMillis); }
    @Override public boolean beginPartialDelivery(boolean cargoAvailable) {
      events.add("partial-policy:" + cargoAvailable);
      return startPartialDelivery;
    }
    @Override public void requestHelp(String reason) { events.add("help:" + reason); }
  }
}
