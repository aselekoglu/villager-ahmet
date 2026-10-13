package dev.ahmet;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class DecisionRetryTest {
  @Test void schedulesBoundedDelayAfterNoChoiceAndAllowsRetryAtDeadline() {
    var retry = new DecisionRetry(2_000);
    assertFalse(retry.shouldWait(10_000));
    retry.schedule(10_000);
    assertTrue(retry.shouldWait(11_999));
    assertFalse(retry.shouldWait(12_000));
    retry.clear();
    assertFalse(retry.shouldWait(10_001));
  }
}
