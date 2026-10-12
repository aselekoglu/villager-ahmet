package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NavigationRetryTest {
  @Test
  void restartsCancelledNavigationOnlyAfterItsBoundedDelay() {
    NavigationRetry retry = new NavigationRetry();
    assertFalse(retry.pending());

    retry.schedule(1_000);

    assertTrue(retry.pending());
    assertFalse(retry.consumeIfDue(2_999));
    assertTrue(retry.pending());
    assertTrue(retry.consumeIfDue(3_000));
    assertFalse(retry.pending());
    assertFalse(retry.consumeIfDue(4_000));
  }

  @Test
  void cancellationClearsAPendingNavigationRestart() {
    NavigationRetry retry = new NavigationRetry();
    retry.schedule(1_000);
    retry.clear();

    assertFalse(retry.pending());
    assertFalse(retry.consumeIfDue(3_000));
  }
}
