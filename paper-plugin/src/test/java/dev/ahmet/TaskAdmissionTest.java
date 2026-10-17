package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
