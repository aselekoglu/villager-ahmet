package dev.ahmet;

import java.util.Objects;
import java.util.UUID;

/** Counts distinct server ticks for one authorized task waiting behind a foreign route. */
final class ForeignNavigationWait {
  static final long MAX_WAIT_TICKS = 100;

  enum Status { IDLE, WAITING, HELP_DUE, HELP_ACCEPTED, RESUMED }

  record Observation(Status status, Long helpSequenceToClear) {}

  private String taskId;
  private UUID owner;
  private long generation;
  private long firstTick = -1;
  private long lastTick = -1;
  private long lastHelpAttemptTick = -1;
  private boolean active;
  private Long acceptedHelpSequence;

  Observation observe(boolean taskActive, String currentTaskId, UUID currentOwner, long currentGeneration,
                      long serverTick, boolean foreignRouteNavigating) {
    if (serverTick < 0 || currentGeneration < 0) throw new IllegalArgumentException("invalid task or tick identity");
    if (!taskActive || currentTaskId == null) {
      reset();
      return new Observation(Status.IDLE, null);
    }

    if (active && (!Objects.equals(taskId, currentTaskId) || !Objects.equals(owner, currentOwner)
        || generation != currentGeneration)) reset();

    if (!foreignRouteNavigating) {
      if (!active) return new Observation(Status.IDLE, null);
      Long clearSequence = acceptedHelpSequence;
      reset();
      return new Observation(Status.RESUMED, clearSequence);
    }

    if (!active) {
      active = true;
      taskId = currentTaskId;
      owner = currentOwner;
      generation = currentGeneration;
    }
    if (firstTick < 0) {
      firstTick = serverTick;
      lastTick = serverTick;
      return new Observation(Status.WAITING, null);
    }
    if (serverTick > lastTick) lastTick = serverTick;
    if (lastTick - firstTick < MAX_WAIT_TICKS) return new Observation(Status.WAITING, null);
    return new Observation(acceptedHelpSequence == null ? Status.HELP_DUE : Status.HELP_ACCEPTED, null);
  }

  /** Allows one controller help request attempt per distinct tick after the bounded wait. */
  boolean beginHelpAttempt(long serverTick) {
    if (!active || firstTick < 0 || serverTick < 0 || acceptedHelpSequence != null
        || lastTick - firstTick < MAX_WAIT_TICKS || lastHelpAttemptTick == serverTick) return false;
    lastHelpAttemptTick = serverTick;
    return true;
  }

  void markHelpAccepted(long requestSequence) {
    if (!active || firstTick < 0 || requestSequence <= 0) {
      throw new IllegalStateException("help acceptance is outside an active foreign-route wait");
    }
    acceptedHelpSequence = requestSequence;
  }

  /** Restarts elapsed waiting after recovery or journal work without clearing accepted help. */
  void resetWaitForSafetyBlock() {
    firstTick = -1;
    lastTick = -1;
    lastHelpAttemptTick = -1;
  }

  private void reset() {
    taskId = null;
    owner = null;
    generation = 0;
    firstTick = -1;
    lastTick = -1;
    lastHelpAttemptTick = -1;
    active = false;
    acceptedHelpSequence = null;
  }
}
