package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FailedTargetsTest {
  @Test
  void excludesAnUnreachableBlockTemporarilyThenAllowsItToBeRetried() {
    FailedTargets failed = new FailedTargets();
    failed.mark("world", 12, 65, -4, 1_000);

    assertTrue(failed.contains("world", 12, 65, -4, 2_000));
    assertFalse(failed.contains("world", 13, 65, -4, 2_000));
    assertFalse(failed.contains("world", 12, 65, -4, 61_000));
  }

  @Test
  void clearStartsANewJobWithNoStaleTargetFailures() {
    FailedTargets failed = new FailedTargets();
    failed.mark("world", 12, 65, -4, 1_000);
    failed.clear();

    assertFalse(failed.contains("world", 12, 65, -4, 1_001));
  }
}
