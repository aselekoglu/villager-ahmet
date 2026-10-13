package dev.ahmet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ClearTransactionTest {
  @Test void successfulClearRunsReserveBeforeMutationThenCheckpointAndRetire() {
    List<String> calls=new ArrayList<>();
    boolean success=ClearTransaction.execute(
        ()->calls.add("reserve"),()->calls.add("prepare"),()->calls.add("mutate"),
        ()->calls.add("complete"),()->calls.add("checkpoint"),()->calls.add("retire"),e->fail(e));
    assertTrue(success);
    assertEquals(List.of("reserve","prepare","mutate","complete","checkpoint","retire"),calls);
  }

  @Test void everyFailureStopsLaterStepsAndReturnsFailureForCaller() {
    for(int failureAt=0;failureAt<6;failureAt++){
      List<String> calls=new ArrayList<>();int failedStage=failureAt;
      ClearTransaction.CheckedAction[] stages={
          ()->calls.add("reserve"),()->calls.add("prepare"),()->calls.add("mutate"),
          ()->calls.add("complete"),()->calls.add("checkpoint"),()->calls.add("retire")};
      stages[failureAt]=()->{calls.add("stage"+failedStage);throw new IOException("injected failure");};
      List<Exception> errors=new ArrayList<>();
      assertFalse(ClearTransaction.execute(stages[0],stages[1],stages[2],stages[3],stages[4],stages[5],errors::add));
      assertEquals(1,errors.size());
      assertEquals(failureAt+1,calls.size());
    }
  }
}
