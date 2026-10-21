package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

final class TaskAdmissionTest {
  @TempDir Path temp;
  @Test void committedClearJournalBlocksAdmissionUntilWorldCheckpointRetiresIt() throws Exception {
    var path=temp.resolve("operations.json");
    var journal=new OperationJournal(path);
    journal.load();
    journal.prepareClear(new OperationJournal.Clear("task","world",1,64,1,"OAK_LEAVES"));
    journal.completeClear();
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,
        TaskAdmission.evaluate(true,false,journal.hasOperations(),false,0,true));
    journal.clearAfterWorldSave();
    assertEquals(TaskAdmission.Result.ACCEPT,
        TaskAdmission.evaluate(true,false,journal.hasOperations(),false,0,true));
  }

  @Test void recoveryAndBusyReasonsAreNotMisreportedAsUnsupported() throws Exception {
    var path=temp.resolve("harvest-operations.json");
    var journal=new OperationJournal(path);
    journal.load();
    journal.prepareHarvest(new OperationJournal.Harvest("task","world",1,64,1,0,0));
    journal.completeHarvest();
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,journal.hasOperations(),true,1,true));
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,journal.hasOperations(),false,1,true));
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,TaskAdmission.evaluate(true,true,journal.hasOperations(),true,1,true));
    assertEquals(TaskAdmission.Result.RECOVERY_REQUIRED,TaskAdmission.evaluate(true,true,false,false,0,true));
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,false,true,0,true));
    assertEquals(TaskAdmission.Result.BUSY,TaskAdmission.evaluate(true,false,false,false,2,true));
    assertEquals(TaskAdmission.Result.CHEST_REQUIRED,TaskAdmission.evaluate(true,false,false,false,0,false));
    assertEquals(TaskAdmission.Result.UNSUPPORTED,TaskAdmission.evaluate(false,false,false,false,0,true));
    assertEquals(TaskAdmission.Result.ACCEPT,TaskAdmission.evaluate(true,false,false,false,0,true));
  }

  @Test
  void canCancelOrReturnHomeGuardsPendingOperationRecoveryAndTaskValidity() throws Exception {
    var path = temp.resolve("cancel-guard.json");
    var journal = new OperationJournal(path);
    journal.load();

    // Valid state, no recovery blocked, no pending operation -> allowed
    assertTrue(TaskAdmission.canCancelOrReturnHome(true, false, journal.hasPendingOperation()));

    // Task state invalid -> refused
    assertFalse(TaskAdmission.canCancelOrReturnHome(false, false, journal.hasPendingOperation()));

    // Recovery blocked -> refused
    assertFalse(TaskAdmission.canCancelOrReturnHome(true, true, journal.hasPendingOperation()));

    // Pending operation in journal -> refused
    journal.prepareHarvest(new OperationJournal.Harvest("task", "world", 1, 64, 1, 0, 0));
    assertTrue(journal.hasPendingOperation());
    assertFalse(TaskAdmission.canCancelOrReturnHome(true, false, journal.hasPendingOperation()));

    // Committed harvest history exists (journal.hasOperations() is true, but hasPendingOperation() is false) -> allowed!
    journal.completeHarvest();
    assertTrue(journal.hasOperations());
    assertFalse(journal.hasPendingOperation());
    assertTrue(TaskAdmission.canCancelOrReturnHome(true, false, journal.hasPendingOperation()));
  }

  @Test
  void canExecuteGatherAndCraftStepsOnlyAllowMatchingActiveTasks() {
    assertFalse(TaskAdmission.canExecuteGatherStep(null));
    assertFalse(TaskAdmission.canExecuteCraftStep(null));

    WoodTask inactiveGather = new WoodTask();
    assertFalse(TaskAdmission.canExecuteGatherStep(inactiveGather));
    assertFalse(TaskAdmission.canExecuteCraftStep(inactiveGather));

    WoodTask activeGather = new WoodTask();
    activeGather.start(java.util.UUID.randomUUID(), 4);
    assertTrue(TaskAdmission.canExecuteGatherStep(activeGather));
    assertFalse(TaskAdmission.canExecuteCraftStep(activeGather));

    WoodTask activeCraft = new WoodTask();
    activeCraft.startCraft(java.util.UUID.randomUUID(), CraftRecipe.OAK_PLANKS, 4);
    assertFalse(TaskAdmission.canExecuteGatherStep(activeCraft));
    assertTrue(TaskAdmission.canExecuteCraftStep(activeCraft));

    WoodTask inactiveCraft = new WoodTask();
    inactiveCraft.startCraft(java.util.UUID.randomUUID(), CraftRecipe.OAK_PLANKS, 4);
    inactiveCraft.cancel();
    assertFalse(TaskAdmission.canExecuteGatherStep(inactiveCraft));
    assertFalse(TaskAdmission.canExecuteCraftStep(inactiveCraft));
  }
}
