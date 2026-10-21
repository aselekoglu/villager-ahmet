package dev.ahmet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain selector deterministically delegating tasks to eligible villager candidates.
 */
public final class TaskDelegator {
  private TaskDelegator() {}

  public record Candidate(
      String villagerId,
      String displayName,
      VillagerRole role,
      boolean spawned,
      boolean sameWorld,
      double distanceSq,
      TaskAdmission.Result admission,
      boolean craftPrePlanOk
  ) {
    public Candidate {
      Objects.requireNonNull(villagerId, "villagerId is required");
      Objects.requireNonNull(displayName, "displayName is required");
    }
  }

  public enum Status {
    SELECTED,
    NO_SPAWNED_VILLAGER,
    OUT_OF_RANGE,
    NO_ROLE_CAPABLE,
    BUSY_OR_UNREADY
  }

  public record Decision(
      Status status,
      Candidate selected,
      String reason
  ) {}

  public static Decision select(WoodTask.Kind kind, List<Candidate> candidates, double maxDistanceSq) {
    Objects.requireNonNull(kind, "kind is required");
    if (candidates == null || candidates.isEmpty()) {
      return new Decision(Status.NO_SPAWNED_VILLAGER, null, "Uygun dünyada doğmuş köylü bulunamadı.");
    }

    // 1. Filter spawned && sameWorld
    List<Candidate> spawnedSameWorld = candidates.stream()
        .filter(c -> c.spawned() && c.sameWorld())
        .toList();
    if (spawnedSameWorld.isEmpty()) {
      return new Decision(Status.NO_SPAWNED_VILLAGER, null, "Uygun dünyada doğmuş köylü bulunamadı.");
    }

    // 2. Filter distanceSq <= maxDistanceSq
    List<Candidate> inRange = spawnedSameWorld.stream()
        .filter(c -> c.distanceSq() <= maxDistanceSq)
        .toList();
    if (inRange.isEmpty()) {
      return new Decision(Status.OUT_OF_RANGE, null, "Köylüler menzil dışında.");
    }

    // 3. Filter role != null && role.canExecute(kind)
    List<Candidate> capable = inRange.stream()
        .filter(c -> c.role() != null && c.role().canExecute(kind))
        .toList();
    if (capable.isEmpty()) {
      return new Decision(Status.NO_ROLE_CAPABLE, null, "Bu görevi yapabilecek role sahip köylü bulunamadı.");
    }

    // 4. Filter admission == ACCEPT && (kind != CRAFT || craftPrePlanOk)
    List<Candidate> ready = capable.stream()
        .filter(c -> c.admission() == TaskAdmission.Result.ACCEPT && (kind != WoodTask.Kind.CRAFT || c.craftPrePlanOk()))
        .toList();
    if (ready.isEmpty()) {
      return new Decision(Status.BUSY_OR_UNREADY, null, "Köylüler meşgul veya göreve hazır değil.");
    }

    // 5. Sort: specialists first, then closer distanceSq, then villagerId tiebreak
    List<Candidate> sorted = new ArrayList<>(ready);
    sorted.sort((a, b) -> {
      int spec = Boolean.compare(b.role().isSpecialist(), a.role().isSpecialist());
      if (spec != 0) return spec;
      int dist = Double.compare(a.distanceSq(), b.distanceSq());
      if (dist != 0) return dist;
      return a.villagerId().compareTo(b.villagerId());
    });

    Candidate chosen = sorted.get(0);
    return new Decision(Status.SELECTED, chosen, "Görev için seçilen köylü: " + chosen.displayName());
  }
}
