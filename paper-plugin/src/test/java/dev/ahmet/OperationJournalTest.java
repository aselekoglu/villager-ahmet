package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OperationJournalTest {
  @TempDir Path temp;

  @Test
  void pendingHarvestAndDepositAreDurableAcrossReloads() throws Exception {
    Path path = temp.resolve("operations.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    OperationJournal.Harvest harvest = new OperationJournal.Harvest("task-1", "world", 4, 65, -2, 2, 2);
    journal.prepareHarvest(harvest);

    OperationJournal afterRestart = new OperationJournal(path);
    afterRestart.load();
    assertEquals(harvest, afterRestart.pendingHarvest().orElseThrow());
    afterRestart.completeHarvest();

    OperationJournal afterSecondRestart = new OperationJournal(path);
    afterSecondRestart.load();
    assertEquals(List.of(harvest), afterSecondRestart.harvests());
    var change = new CargoDepositPlanner.Change(0, null, 0, 2);
    var deposit = new OperationJournal.Deposit("task-1", "world", 8, 65, -2, 2, 0, 2, List.of(change));
    afterSecondRestart.prepareDeposit(deposit);

    OperationJournal afterThirdRestart = new OperationJournal(path);
    afterThirdRestart.load();
    assertEquals(deposit, afterThirdRestart.pendingDeposit().orElseThrow());
    assertTrue(afterThirdRestart.hasOperations());
  }
}
