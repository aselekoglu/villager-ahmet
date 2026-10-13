package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PartialDeliveryPolicyTest {
  @Test
  void requiresThreeFailedRoutesAndResetsAfterAResourceIsCollected() {
    PartialDeliveryPolicy policy = new PartialDeliveryPolicy();
    assertFalse(policy.unreachableRoute(true));
    assertFalse(policy.unreachableRoute(true));
    assertTrue(policy.unreachableRoute(true));
    policy.harvested();
    assertFalse(policy.unreachableRoute(true));
  }

  @Test
  void requiresTwoEmptyScansAndAnyFoundTargetResetsThatCounter() {
    PartialDeliveryPolicy policy = new PartialDeliveryPolicy();
    assertFalse(policy.emptyScan(true));
    policy.targetsFound();
    assertFalse(policy.emptyScan(true));
    assertTrue(policy.emptyScan(true));
  }

  @Test
  void failuresWithoutCargoCannotTriggerPartialDelivery() {
    PartialDeliveryPolicy policy = new PartialDeliveryPolicy();
    assertFalse(policy.unreachableRoute(false));
    assertFalse(policy.emptyScan(false));
    assertFalse(policy.unreachableRoute(true));
    assertFalse(policy.emptyScan(true));
  }
}
