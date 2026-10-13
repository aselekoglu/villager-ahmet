package dev.ahmet;

import java.util.Objects;
import java.util.function.Consumer;

/** Orders a journaled obstruction clear and reports success only after checkpoint and retirement. */
final class ClearTransaction {
  @FunctionalInterface interface CheckedAction { void run() throws Exception; }
  private ClearTransaction() {}

  static boolean execute(CheckedAction reserveBudget, CheckedAction prepareJournal,
      CheckedAction mutateWorld, CheckedAction commitJournal, CheckedAction checkpointWorld,
      CheckedAction retireJournal, Consumer<Exception> onFailure) {
    Objects.requireNonNull(reserveBudget);Objects.requireNonNull(prepareJournal);Objects.requireNonNull(mutateWorld);
    Objects.requireNonNull(commitJournal);Objects.requireNonNull(checkpointWorld);Objects.requireNonNull(retireJournal);
    Objects.requireNonNull(onFailure);
    try {
      reserveBudget.run();
      prepareJournal.run();
      mutateWorld.run();
      commitJournal.run();
      checkpointWorld.run();
      retireJournal.run();
      return true;
    } catch(Exception failure) {
      onFailure.accept(failure);
      return false;
    }
  }
}
