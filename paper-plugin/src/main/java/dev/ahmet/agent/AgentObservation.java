package dev.ahmet.agent;

import org.bukkit.Location;
import java.util.List;
import java.util.Objects;

public record AgentObservation(
    Location npcLocation,
    boolean chestBound,
    Location chestLocation,
    Location homeLocation,
    Location navigationGoal,
    String activeTaskId,
    boolean taskActive,
    String materialPreference,
    int requested,
    int collected,
    int cargo,
    int deposited,
    int clearedObstructions,
    int routeClears,
    int explorationAttempts,
    List<OfferedAction> candidateActions,
    long timestampMs
) {
  public AgentObservation {
    candidateActions = candidateActions == null ? List.of() : List.copyOf(candidateActions);
  }
}
