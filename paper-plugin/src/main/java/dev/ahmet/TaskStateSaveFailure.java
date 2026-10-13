package dev.ahmet;

import java.io.IOException;

final class TaskStateSaveFailure {
  private TaskStateSaveFailure() {}
  static IllegalStateException failClosed(IOException failure, Runnable lock) {
    lock.run();
    return new IllegalStateException("Could not atomically save plugin task state", failure);
  }
}
