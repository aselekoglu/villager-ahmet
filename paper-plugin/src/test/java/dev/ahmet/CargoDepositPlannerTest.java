package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CargoDepositPlannerTest {
  @Test
  void fillsExistingLogStacksThenEmptySlotsWithoutChangingOtherItems() {
    List<CargoDepositPlanner.Slot> before = List.of(
        new CargoDepositPlanner.Slot("OAK_LOG", 60, 64),
        CargoDepositPlanner.Slot.empty(64),
        new CargoDepositPlanner.Slot("COBBLESTONE", 64, 64));

    CargoDepositPlanner.Plan plan = CargoDepositPlanner.plan(before, "OAK_LOG", 10);
    List<CargoDepositPlanner.Slot> after = CargoDepositPlanner.apply(before, plan);

    assertEquals(10, plan.moved());
    assertEquals(0, plan.remaining());
    assertEquals(new CargoDepositPlanner.Slot("OAK_LOG", 64, 64), after.get(0));
    assertEquals(new CargoDepositPlanner.Slot("OAK_LOG", 6, 64), after.get(1));
    assertEquals(before.get(2), after.get(2));
  }

  @Test
  void applyingTheSameJournalPlanTwiceIsIdempotentAndConflictsFailClosed() {
    List<CargoDepositPlanner.Slot> before = List.of(new CargoDepositPlanner.Slot("BIRCH_LOG", 60, 64));
    CargoDepositPlanner.Plan plan = CargoDepositPlanner.plan(before, "BIRCH_LOG", 4);
    List<CargoDepositPlanner.Slot> after = CargoDepositPlanner.apply(before, plan);

    assertEquals(after, CargoDepositPlanner.apply(after, plan));
    assertThrows(IllegalStateException.class, () -> CargoDepositPlanner.apply(
        List.of(new CargoDepositPlanner.Slot("BIRCH_LOG", 63, 64)), plan));
  }

  @Test
  void aFullChestKeepsAllRequestedCargoUndeposited() {
    List<CargoDepositPlanner.Slot> full = List.of(
        new CargoDepositPlanner.Slot("COBBLESTONE", 64, 64),
        new CargoDepositPlanner.Slot("OAK_LOG", 64, 64));

    CargoDepositPlanner.Plan plan = CargoDepositPlanner.plan(full, "OAK_LOG", 20);

    assertEquals(0, plan.moved());
    assertEquals(20, plan.remaining());
    assertEquals(List.of(), plan.changes());
  }

  @Test
  void depositsOnlyTheSelectedWoodSpeciesIntoMatchingStacks() {
    List<CargoDepositPlanner.Slot> before = List.of(
        new CargoDepositPlanner.Slot("OAK_LOG", 10, 64),
        new CargoDepositPlanner.Slot("BIRCH_LOG", 2, 64),
        CargoDepositPlanner.Slot.empty(64));
    CargoDepositPlanner.Plan plan = CargoDepositPlanner.plan(before, "BIRCH_LOG", 5);
    List<CargoDepositPlanner.Slot> after = CargoDepositPlanner.apply(before, plan);
    assertEquals(new CargoDepositPlanner.Slot("OAK_LOG", 10, 64), after.get(0));
    assertEquals(new CargoDepositPlanner.Slot("BIRCH_LOG", 7, 64), after.get(1));
    assertEquals(before.get(2), after.get(2));
  }
}
