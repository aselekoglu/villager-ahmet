package dev.ahmet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class TaskStateSaveFailureTest {
  @Test void locksTaskExecutionBeforeSaveFailurePropagates() {
    var locked = new AtomicBoolean();
    var failure = new IOException("disk full");
    var thrown = assertThrows(IllegalStateException.class,
        () -> { throw TaskStateSaveFailure.failClosed(failure, () -> locked.set(true)); });
    assertTrue(locked.get());
    assertSame(failure, thrown.getCause());
  }
}
