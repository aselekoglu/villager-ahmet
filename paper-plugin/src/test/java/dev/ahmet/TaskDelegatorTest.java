package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.List;

final class TaskDelegatorTest {

  private static final double MAX_DIST_SQ = 18.0 * 18.0; // 324.0

  @Test
  void nullKindThrows() {
    assertThrows(NullPointerException.class, () ->
        TaskDelegator.select(null, List.of(), MAX_DIST_SQ));
  }

  @Test
  void noSpawnedVillagersReturnsExpectedStatus() {
    // Empty list
    var d1 = TaskDelegator.select(WoodTask.Kind.GATHER, List.of(), MAX_DIST_SQ);
    assertEquals(TaskDelegator.Status.NO_SPAWNED_VILLAGER, d1.status());
    assertNull(d1.selected());

    // Unspawned or different world
    var c1 = new TaskDelegator.Candidate("ahmet", "Ahmet", VillagerRole.ALL_ROUNDER, false, true, 10.0, TaskAdmission.Result.ACCEPT, true);
    var c2 = new TaskDelegator.Candidate("mehmet", "Mehmet", VillagerRole.WORKER, true, false, 10.0, TaskAdmission.Result.ACCEPT, true);
    var d2 = TaskDelegator.select(WoodTask.Kind.GATHER, List.of(c1, c2), MAX_DIST_SQ);
    assertEquals(TaskDelegator.Status.NO_SPAWNED_VILLAGER, d2.status());
  }

  @Test
  void outOfRangeReturnsExpectedStatus() {
    var c1 = new TaskDelegator.Candidate("ahmet", "Ahmet", VillagerRole.ALL_ROUNDER, true, true, 400.0, TaskAdmission.Result.ACCEPT, true);
    var d = TaskDelegator.select(WoodTask.Kind.GATHER, List.of(c1), MAX_DIST_SQ);
    assertEquals(TaskDelegator.Status.OUT_OF_RANGE, d.status());
    assertNull(d.selected());
  }

  @Test
  void noRoleCapableReturnsExpectedStatus() {
    // Only WORKER available, but task is FARM
    var c1 = new TaskDelegator.Candidate("mehmet", "Mehmet", VillagerRole.WORKER, true, true, 16.0, TaskAdmission.Result.ACCEPT, true);
    var d = TaskDelegator.select(WoodTask.Kind.FARM, List.of(c1), MAX_DIST_SQ);
    assertEquals(TaskDelegator.Status.NO_ROLE_CAPABLE, d.status());
    assertNull(d.selected());
  }

  @Test
  void busyOrUnreadyReturnsExpectedStatus() {
    // FARMER is busy
    var c1 = new TaskDelegator.Candidate("ayse", "Ayse", VillagerRole.FARMER, true, true, 16.0, TaskAdmission.Result.BUSY, true);
    var d1 = TaskDelegator.select(WoodTask.Kind.FARM, List.of(c1), MAX_DIST_SQ);
    assertEquals(TaskDelegator.Status.BUSY_OR_UNREADY, d1.status());

    // CRAFTER admission ACCEPT, but craftPrePlanOk is false
    var c2 = new TaskDelegator.Candidate("ali", "Ali", VillagerRole.CRAFTER, true, true, 16.0, TaskAdmission.Result.ACCEPT, false);
    var d2 = TaskDelegator.select(WoodTask.Kind.CRAFT, List.of(c2), MAX_DIST_SQ);
    assertEquals(TaskDelegator.Status.BUSY_OR_UNREADY, d2.status());
  }

  @Test
  void prefersSpecialistOverAllRounder() {
    // Ahmet is ALL_ROUNDER at distanceSq = 4.0 (closer)
    var ahmet = new TaskDelegator.Candidate("ahmet", "Ahmet", VillagerRole.ALL_ROUNDER, true, true, 4.0, TaskAdmission.Result.ACCEPT, true);
    // Mehmet is FARMER (specialist) at distanceSq = 25.0 (further)
    var mehmet = new TaskDelegator.Candidate("mehmet", "Mehmet", VillagerRole.FARMER, true, true, 25.0, TaskAdmission.Result.ACCEPT, true);

    var d = TaskDelegator.select(WoodTask.Kind.FARM, List.of(ahmet, mehmet), MAX_DIST_SQ);
    assertEquals(TaskDelegator.Status.SELECTED, d.status());
    assertNotNull(d.selected());
    assertEquals("mehmet", d.selected().villagerId()); // specialist wins!
  }

  @Test
  void breaksTiesByDistanceThenId() {
    // Two specialists for GATHER: worker1 at distance 25.0, worker2 at distance 9.0
    var w1 = new TaskDelegator.Candidate("worker1", "Worker 1", VillagerRole.WORKER, true, true, 25.0, TaskAdmission.Result.ACCEPT, true);
    var w2 = new TaskDelegator.Candidate("worker2", "Worker 2", VillagerRole.WORKER, true, true, 9.0, TaskAdmission.Result.ACCEPT, true);

    var d1 = TaskDelegator.select(WoodTask.Kind.GATHER, List.of(w1, w2), MAX_DIST_SQ);
    assertEquals("worker2", d1.selected().villagerId()); // closer wins

    // Equal distance: tiebreak by ID lexicographical
    var wA = new TaskDelegator.Candidate("a_worker", "Worker A", VillagerRole.WORKER, true, true, 16.0, TaskAdmission.Result.ACCEPT, true);
    var wB = new TaskDelegator.Candidate("b_worker", "Worker B", VillagerRole.WORKER, true, true, 16.0, TaskAdmission.Result.ACCEPT, true);

    var d2 = TaskDelegator.select(WoodTask.Kind.GATHER, List.of(wB, wA), MAX_DIST_SQ);
    assertEquals("a_worker", d2.selected().villagerId()); // "a_worker" < "b_worker"
  }
}
