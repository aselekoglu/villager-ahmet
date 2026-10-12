package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class WoodTaskTest {
  @Test
  void startRejectsAmountsOutsideTheServerBound() {
    WoodTask task = new WoodTask();

    assertThrows(IllegalArgumentException.class, () -> task.start(UUID.randomUUID(), 0));
    assertThrows(IllegalArgumentException.class, () -> task.start(UUID.randomUUID(), 65));
  }

  @Test
  void harvestIsBoundedAndCancellationPreservesCargo() {
    WoodTask task = new WoodTask();
    task.start(UUID.randomUUID(), 1);

    task.harvestOne();
    assertEquals(1, task.collected);
    assertEquals(1, task.cargo);
    assertThrows(IllegalStateException.class, task::harvestOne);

    task.cancel();
    assertFalse(task.active);
    assertEquals(1, task.cargo);
  }

  @Test
  void depositCannotExceedCargo() {
    WoodTask task = new WoodTask();
    task.start(UUID.randomUUID(), 1);
    task.harvestOne();

    assertThrows(IllegalArgumentException.class, () -> task.deposit(2));
    task.deposit(1);
    assertEquals(0, task.cargo);
    assertEquals(1, task.deposited);
  }
}
