package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
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
    assertTrue(Files.readString(path).contains("\"version\":3"));

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

  @Test
  void legacyVersionOneJournalDefaultsMissingMaterialsToOak() throws Exception {
    Path path = temp.resolve("legacy-operations.json");
    Files.writeString(path, """
        {"version":1,"harvests":[],"pendingHarvest":{"taskId":"legacy","world":"world","x":1,"y":2,"z":3,"beforeCollected":0,"beforeCargo":0}}
        """);
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    assertEquals("OAK_LOG", journal.pendingHarvest().orElseThrow().effectiveMaterial());
  }

  @Test
  void versionTwoJournalMissingSafetyFieldsFailsClosedAndIsPreserved() throws Exception {
    Path path = temp.resolve("version-two-operations.json");
    String contents = """
        {"version":2,"harvests":[],"pendingHarvest":{"taskId":"legacy-v2","world":"world","x":1,"y":2,"z":3,"beforeCollected":0,"beforeCargo":0,"material":"OAK_LOG","outputMaterial":"OAK_LOG"}}
        """;
    Files.writeString(path, contents);

    assertThrows(java.io.IOException.class, () -> new OperationJournal(path).load());
    assertEquals(contents, Files.readString(path));
  }

  @Test
  void harvestJournalPersistsTheBlockAndItsDifferentOutputItem() throws Exception {
    Path path = temp.resolve("resource-harvest.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    var harvest = new OperationJournal.Harvest("task-resource", "world", 2, 65, 3, 0, 0, "STONE", "COBBLESTONE");
    journal.prepareHarvest(harvest);

    OperationJournal restored = new OperationJournal(path);
    restored.load();
    var pending = restored.pendingHarvest().orElseThrow();
    assertEquals("STONE", pending.effectiveMaterial());
    assertEquals("COBBLESTONE", pending.effectiveOutputMaterial());
  }

  @Test
  void pendingClearIsDurableAcrossReloadsAndCompletesWithoutCargoChange() throws Exception {
    Path path = temp.resolve("clear-operations.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    OperationJournal.Clear clear = new OperationJournal.Clear("task-clear", "world", 5, 70, 10, "OAK_LEAVES");
    journal.prepareClear(clear);
    assertTrue(journal.hasOperations());

    OperationJournal afterRestart = new OperationJournal(path);
    afterRestart.load();
    assertEquals(clear, afterRestart.pendingClear().orElseThrow());
    afterRestart.completeClear();

    OperationJournal afterSecondRestart = new OperationJournal(path);
    afterSecondRestart.load();
    assertTrue(afterSecondRestart.pendingClear().isEmpty());
    assertEquals(List.of(clear), afterSecondRestart.clears());
    assertTrue(afterSecondRestart.hasOperations());
    assertTrue(afterSecondRestart.hasCommittedOperationsReadyForCheckpoint());
    afterSecondRestart.clearAfterWorldSave();
    assertFalse(afterSecondRestart.hasOperations());
  }

  @Test
  void committedV3ClearOnlyJournalWithMultipleRecordsCanBeCheckpointed() throws Exception {
    Path path=temp.resolve("committed-clear-only-v3.json");
    Files.writeString(path,"""
        {"version":3,"harvests":[],"clears":[
          {"taskId":"task-clear","world":"world","x":5,"y":70,"z":10,"material":"OAK_LEAVES"},
          {"taskId":"task-clear","world":"world","x":6,"y":70,"z":10,"material":"BIRCH_LEAVES"}
        ]}
        """);
    OperationJournal journal=new OperationJournal(path);
    journal.load();
    assertEquals(2,journal.clears().size());
    for(var clear:journal.clears())assertEquals(OperationRecovery.ClearDisposition.ALREADY_APPLIED,
        OperationRecovery.classifyClear(clear,"task-clear","world","AIR"));
    assertTrue(journal.hasCommittedOperationsReadyForCheckpoint());
  }

  @Test
  void mixedCommittedHarvestAndClearHistoryCanBeCheckpointedAfterReconciliation() throws Exception {
    Path path=temp.resolve("mixed-committed-history.json");
    OperationJournal journal=new OperationJournal(path);
    journal.load();
    journal.prepareHarvest(new OperationJournal.Harvest("task-mixed","world",1,64,1,0,0));
    journal.completeHarvest();
    journal.prepareClear(new OperationJournal.Clear("task-mixed","world",2,65,2,"OAK_LEAVES"));
    journal.completeClear();
    assertTrue(journal.hasCommittedOperationsReadyForCheckpoint());
    journal.clearAfterWorldSave();
    assertFalse(journal.hasOperations());
  }

  @Test
  void pendingClearCannotBeMistakenForCheckpointableCommittedJournal() throws Exception {
    Path path=temp.resolve("pending-clear-only.json");
    OperationJournal journal=new OperationJournal(path);
    journal.load();
    journal.prepareClear(new OperationJournal.Clear("task-clear","world",5,70,10,"OAK_LEAVES"));
    assertFalse(journal.hasCommittedOperationsReadyForCheckpoint());
  }
}

