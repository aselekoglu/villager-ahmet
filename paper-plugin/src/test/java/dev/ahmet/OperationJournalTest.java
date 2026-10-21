package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OperationJournalTest {
  @TempDir Path temp;

  @Test
  void pendingHarvestAndDepositAreDurableAcrossReloads() throws Exception {
    Path path = temp.resolve("operations.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    OperationJournal.Harvest harvest = new OperationJournal.Harvest("task-1", "world", 4, 65, -2, 2, 2);
    journal.prepareHarvest(harvest);
    assertTrue(Files.readString(path).contains("\"version\":3"));

    OperationJournal afterRestart = new OperationJournal(path);
    afterRestart.load();
    assertEquals(harvest, afterRestart.pendingHarvest().orElseThrow());
    afterRestart.completeHarvest();

    OperationJournal afterSecondRestart = new OperationJournal(path);
    afterSecondRestart.load();
    assertEquals(List.of(harvest), afterSecondRestart.harvests());
    var change = new CargoDepositPlanner.Change(0, null, 0, 2);
    var deposit = new OperationJournal.Deposit("task-1", "world", 8, 65, -2, 2, 0, 2, List.of(change));
    afterSecondRestart.prepareDeposit(deposit);

    OperationJournal afterThirdRestart = new OperationJournal(path);
    afterThirdRestart.load();
    assertEquals(deposit, afterThirdRestart.pendingDeposit().orElseThrow());
    assertTrue(afterThirdRestart.hasOperations());
  }

  @Test
  void legacyVersionOneJournalDefaultsMissingMaterialsToOak() throws Exception {
    Path path = temp.resolve("legacy-operations.json");
    Files.writeString(path, """
        {"version":1,"harvests":[],"pendingHarvest":{"taskId":"legacy","world":"world","x":1,"y":2,"z":3,"beforeCollected":0,"beforeCargo":0}}
        """);
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    assertEquals("OAK_LOG", journal.pendingHarvest().orElseThrow().effectiveMaterial());
  }

  @Test
  void versionTwoJournalMissingSafetyFieldsFailsClosedAndIsPreserved() throws Exception {
    Path path = temp.resolve("version-two-operations.json");
    String contents = """
        {"version":2,"harvests":[],"pendingHarvest":{"taskId":"legacy-v2","world":"world","x":1,"y":2,"z":3,"beforeCollected":0,"beforeCargo":0,"material":"OAK_LOG","outputMaterial":"OAK_LOG"}}
        """;
    Files.writeString(path, contents);

    assertThrows(java.io.IOException.class, () -> new OperationJournal(path).load());
    assertEquals(contents, Files.readString(path));
  }

  @Test
  void harvestJournalPersistsTheBlockAndItsDifferentOutputItem() throws Exception {
    Path path = temp.resolve("resource-harvest.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    var harvest = new OperationJournal.Harvest("task-resource", "world", 2, 65, 3, 0, 0, "STONE", "COBBLESTONE");
    journal.prepareHarvest(harvest);

    OperationJournal restored = new OperationJournal(path);
    restored.load();
    var pending = restored.pendingHarvest().orElseThrow();
    assertEquals("STONE", pending.effectiveMaterial());
    assertEquals("COBBLESTONE", pending.effectiveOutputMaterial());
  }

  @Test
  void pendingClearIsDurableAcrossReloadsAndCompletesWithoutCargoChange() throws Exception {
    Path path = temp.resolve("clear-operations.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();
    OperationJournal.Clear clear = new OperationJournal.Clear("task-clear", "world", 5, 70, 10, "OAK_LEAVES");
    journal.prepareClear(clear);
    assertTrue(journal.hasOperations());

    OperationJournal afterRestart = new OperationJournal(path);
    afterRestart.load();
    assertEquals(clear, afterRestart.pendingClear().orElseThrow());
    afterRestart.completeClear();

    OperationJournal afterSecondRestart = new OperationJournal(path);
    afterSecondRestart.load();
    assertTrue(afterSecondRestart.pendingClear().isEmpty());
    assertEquals(List.of(clear), afterSecondRestart.clears());
    assertTrue(afterSecondRestart.hasOperations());
    assertTrue(afterSecondRestart.hasCommittedOperationsReadyForCheckpoint());
    afterSecondRestart.clearAfterWorldSave();
    assertFalse(afterSecondRestart.hasOperations());
  }

  @Test
  void committedV3ClearOnlyJournalWithMultipleRecordsCanBeCheckpointed() throws Exception {
    Path path=temp.resolve("committed-clear-only-v3.json");
    Files.writeString(path,"""
        {"version":3,"harvests":[],"clears":[
          {"taskId":"task-clear","world":"world","x":5,"y":70,"z":10,"material":"OAK_LEAVES"},
          {"taskId":"task-clear","world":"world","x":6,"y":70,"z":10,"material":"BIRCH_LEAVES"}
        ]}
        """);
    OperationJournal journal=new OperationJournal(path);
    journal.load();
    assertEquals(2,journal.clears().size());
    for(var clear:journal.clears())assertEquals(OperationRecovery.ClearDisposition.ALREADY_APPLIED,
        OperationRecovery.classifyClear(clear,"task-clear","world","AIR"));
    assertTrue(journal.hasCommittedOperationsReadyForCheckpoint());
  }

  @Test
  void mixedCommittedHarvestAndClearHistoryCanBeCheckpointedAfterReconciliation() throws Exception {
    Path path=temp.resolve("mixed-committed-history.json");
    OperationJournal journal=new OperationJournal(path);
    journal.load();
    journal.prepareHarvest(new OperationJournal.Harvest("task-mixed","world",1,64,1,0,0));
    journal.completeHarvest();
    journal.prepareClear(new OperationJournal.Clear("task-mixed","world",2,65,2,"OAK_LEAVES"));
    journal.completeClear();
    assertTrue(journal.hasCommittedOperationsReadyForCheckpoint());
    journal.clearAfterWorldSave();
    assertFalse(journal.hasOperations());
  }

  @Test
  void pendingClearCannotBeMistakenForCheckpointableCommittedJournal() throws Exception {
    Path path=temp.resolve("pending-clear-only.json");
    OperationJournal journal=new OperationJournal(path);
    journal.load();
    journal.prepareClear(new OperationJournal.Clear("task-clear","world",5,70,10,"OAK_LEAVES"));
    assertFalse(journal.hasCommittedOperationsReadyForCheckpoint());
  }

  @Test
  void hasPendingOperationOnlyTrueWhenOperationIsPending() throws Exception {
    Path path = temp.resolve("pending-op-check.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();

    // 1. Initially empty
    assertFalse(journal.hasPendingOperation());

    // 2. Pending harvest
    var harvest = new OperationJournal.Harvest("task-1", "world", 1, 64, 1, 0, 0);
    journal.prepareHarvest(harvest);
    assertTrue(journal.hasPendingOperation());
    journal.completeHarvest();
    // Now committed harvest only
    assertFalse(journal.hasPendingOperation());
    assertTrue(journal.hasOperations());

    // 3. Pending deposit
    var change = new CargoDepositPlanner.Change(0, null, 0, 1);
    var deposit = new OperationJournal.Deposit("task-1", "world", 2, 64, 2, 1, 0, 1, List.of(change));
    Path depositPath = temp.resolve("pending-deposit-check.json");
    OperationJournal depositJournal = new OperationJournal(depositPath);
    depositJournal.load();
    depositJournal.prepareDeposit(deposit);
    assertTrue(depositJournal.hasPendingOperation());

    // 4. Pending clear
    Path clearPath = temp.resolve("pending-clear-check.json");
    OperationJournal clearJournal = new OperationJournal(clearPath);
    clearJournal.load();
    assertFalse(clearJournal.hasPendingOperation());
    var clear = new OperationJournal.Clear("task-clear", "world", 3, 65, 3, "OAK_LEAVES");
    clearJournal.prepareClear(clear);
    assertTrue(clearJournal.hasPendingOperation());
    clearJournal.completeClear();
    // Now committed clear only
    assertFalse(clearJournal.hasPendingOperation());
    assertTrue(clearJournal.hasOperations());

    // 5. Mixed committed harvests and clears without pending operations
    Path mixedPath = temp.resolve("mixed-committed-no-pending.json");
    OperationJournal mixedJournal = new OperationJournal(mixedPath);
    mixedJournal.load();
    mixedJournal.prepareHarvest(harvest);
    mixedJournal.completeHarvest();
    mixedJournal.prepareClear(clear);
    mixedJournal.completeClear();
    assertFalse(mixedJournal.hasPendingOperation());
    assertTrue(mixedJournal.hasOperations());
  }

  private static List<CraftingPlanner.Slot> chestWithOakLogs(int size, int count) {
    List<CraftingPlanner.Slot> slots = new ArrayList<>();
    slots.add(new CraftingPlanner.Slot("OAK_LOG", count, 64, true));
    for (int i = 1; i < size; i++) {
      slots.add(CraftingPlanner.Slot.empty(64));
    }
    return List.copyOf(slots);
  }

  @Test
  void pendingCraftRoundTripAtVersionFour() throws Exception {
    Path path = temp.resolve("craft-operations.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();

    var result = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var plan = result.plan();
    var craft = OperationJournal.Craft.fromPlan("task-c1", "user-uuid", "world", 10, 64, 20, plan);

    journal.prepareCraft(craft);
    assertTrue(journal.hasPendingOperation());
    assertTrue(journal.hasOperations());
    assertFalse(journal.hasCommittedOperationsReadyForCheckpoint());

    String json = Files.readString(path);
    assertTrue(json.contains("\"version\":4"));
    assertTrue(json.contains("\"pendingCraft\""));
    assertTrue(json.contains("\"recipeKey\":\"minecraft:oak_planks\""));

    OperationJournal restored = new OperationJournal(path);
    restored.load();
    assertTrue(restored.hasPendingOperation());
    OperationJournal.Craft loaded = restored.pendingCraft().orElseThrow();
    assertEquals(craft, loaded);
    assertEquals("task-c1", loaded.taskId());
    assertEquals("user-uuid", loaded.owner());
    assertEquals("CRAFT", loaded.kind());
    assertEquals("minecraft:oak_planks", loaded.recipeKey());
    assertEquals("world", loaded.world());
    assertEquals(10, loaded.x());
    assertEquals(64, loaded.y());
    assertEquals(20, loaded.z());
    assertEquals(27, loaded.inventorySize());
    assertEquals(4, loaded.requested());
    assertEquals(1, loaded.inputCount());
    assertEquals(0, loaded.beforeCollected());
    assertEquals(0, loaded.beforeDeposited());
    assertEquals(4, loaded.afterCollected());
    assertEquals(4, loaded.afterDeposited());
    assertTrue(loaded.matchesChest("world", 10, 64, 20));
    assertFalse(loaded.matchesChest("other", 10, 64, 20));
    assertFalse(loaded.matchesChest("world", 11, 64, 20));
    assertEquals(plan, loaded.toPlan());

    restored.clearAfterWorldSave();
    assertFalse(restored.hasOperations());
    assertFalse(restored.hasPendingOperation());
    assertFalse(Files.exists(path));
  }

  @Test
  void doubleChestCraftRoundTrip() throws Exception {
    Path path = temp.resolve("double-chest-craft.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();

    var result = CraftingPlanner.plan(chestWithOakLogs(54, 2), CraftRecipe.OAK_PLANKS, 8);
    var plan = result.plan();
    var craft = OperationJournal.Craft.fromPlan("task-c2", "user-2", "world", -5, 70, 15, plan);

    journal.prepareCraft(craft);
    OperationJournal restored = new OperationJournal(path);
    restored.load();
    assertEquals(craft, restored.pendingCraft().orElseThrow());
    assertEquals(54, restored.pendingCraft().get().inventorySize());
    assertEquals(8, restored.pendingCraft().get().requested());
    assertEquals(2, restored.pendingCraft().get().inputCount());
  }

  @Test
  void mutualExclusionEnforcedAcrossAllOperations() throws Exception {
    var result = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var craft = OperationJournal.Craft.fromPlan("task-c", "user", "world", 0, 64, 0, result.plan());
    var harvest = new OperationJournal.Harvest("task-h", "world", 1, 64, 1, 0, 0);
    var change = new CargoDepositPlanner.Change(0, null, 0, 1);
    var deposit = new OperationJournal.Deposit("task-d", "world", 2, 64, 2, 1, 0, 1, List.of(change));
    var clear = new OperationJournal.Clear("task-cl", "world", 3, 65, 3, "OAK_LEAVES");

    // 1. Cannot prepareCraft when pendingHarvest exists
    Path p1 = temp.resolve("me-1.json");
    OperationJournal j1 = new OperationJournal(p1);
    j1.load();
    j1.prepareHarvest(harvest);
    assertThrows(IllegalStateException.class, () -> j1.prepareCraft(craft));

    // 2. Cannot prepareCraft when pendingDeposit exists
    Path p2 = temp.resolve("me-2.json");
    OperationJournal j2 = new OperationJournal(p2);
    j2.load();
    j2.prepareDeposit(deposit);
    assertThrows(IllegalStateException.class, () -> j2.prepareCraft(craft));

    // 3. Cannot prepareCraft when pendingClear exists
    Path p3 = temp.resolve("me-3.json");
    OperationJournal j3 = new OperationJournal(p3);
    j3.load();
    j3.prepareClear(clear);
    assertThrows(IllegalStateException.class, () -> j3.prepareCraft(craft));

    // 4. Cannot prepareCraft when pendingCraft exists
    Path p4 = temp.resolve("me-4.json");
    OperationJournal j4 = new OperationJournal(p4);
    j4.load();
    j4.prepareCraft(craft);
    assertThrows(IllegalStateException.class, () -> j4.prepareCraft(craft));

    // 5. Cannot prepareHarvest, prepareDeposit, or prepareClear when pendingCraft exists
    assertThrows(IllegalStateException.class, () -> j4.prepareHarvest(harvest));
    assertThrows(IllegalStateException.class, () -> j4.prepareDeposit(deposit));
    assertThrows(IllegalStateException.class, () -> j4.prepareClear(clear));

    // 6. Cannot prepareCraft when committed harvests exist
    Path p5 = temp.resolve("me-5.json");
    OperationJournal j5 = new OperationJournal(p5);
    j5.load();
    j5.prepareHarvest(harvest);
    j5.completeHarvest();
    assertThrows(IllegalStateException.class, () -> j5.prepareCraft(craft));

    // 7. Cannot prepareCraft when committed clears exist
    Path p6 = temp.resolve("me-6.json");
    OperationJournal j6 = new OperationJournal(p6);
    j6.load();
    j6.prepareClear(clear);
    j6.completeClear();
    assertThrows(IllegalStateException.class, () -> j6.prepareCraft(craft));
  }

  @Test
  void discardPendingCraftDeletesJournalWhenNoOtherOps() throws Exception {
    Path path = temp.resolve("discard-craft.json");
    OperationJournal journal = new OperationJournal(path);
    journal.load();

    var result = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var craft = OperationJournal.Craft.fromPlan("task-c", "user", "world", 0, 64, 0, result.plan());
    journal.prepareCraft(craft);
    assertTrue(journal.hasPendingOperation());
    assertTrue(Files.exists(path));

    journal.discardPendingCraft();
    assertFalse(journal.hasPendingOperation());
    assertFalse(journal.hasOperations());
    assertFalse(Files.exists(path));

    // Safe no-op when already empty
    journal.discardPendingCraft();
    assertFalse(journal.hasPendingOperation());
  }

  @Test
  void forgedCraftJsonFailsClosedAndPreservesFile() throws Exception {
    // A: Syntax error
    Path p1 = temp.resolve("forged-syntax.json");
    String malformedSyntax = "{\"version\":4,\"pendingCraft\":{invalid-json";
    Files.writeString(p1, malformedSyntax);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p1).load());
    assertEquals(malformedSyntax, Files.readString(p1));

    // B: Invalid kind
    Path p2 = temp.resolve("forged-kind.json");
    String forgedKind = """
        {"version":4,"pendingCraft":{"taskId":"t1","owner":"u1","kind":"FORGED","recipeKey":"minecraft:oak_planks","world":"world","x":0,"y":64,"z":0,"inventorySize":27,"requested":4,"inputCount":1,"beforeCollected":0,"beforeDeposited":0,"afterCollected":4,"afterDeposited":4,"before":[],"after":[],"changes":[]}}
        """;
    Files.writeString(p2, forgedKind);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p2).load());
    assertEquals(forgedKind, Files.readString(p2));

    // C: Unsupported recipe key
    Path p3 = temp.resolve("forged-recipe.json");
    String forgedRecipe = """
        {"version":4,"pendingCraft":{"taskId":"t1","owner":"u1","kind":"CRAFT","recipeKey":"minecraft:diamond_sword","world":"world","x":0,"y":64,"z":0,"inventorySize":27,"requested":4,"inputCount":1,"beforeCollected":0,"beforeDeposited":0,"afterCollected":4,"afterDeposited":4,"before":[],"after":[],"changes":[]}}
        """;
    Files.writeString(p3, forgedRecipe);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p3).load());
    assertEquals(forgedRecipe, Files.readString(p3));

    // D: Invalid requested count (not a multiple of 4)
    Path p4 = temp.resolve("forged-requested.json");
    String forgedRequested = """
        {"version":4,"pendingCraft":{"taskId":"t1","owner":"u1","kind":"CRAFT","recipeKey":"minecraft:oak_planks","world":"world","x":0,"y":64,"z":0,"inventorySize":27,"requested":5,"inputCount":1,"beforeCollected":0,"beforeDeposited":0,"afterCollected":5,"afterDeposited":5,"before":[],"after":[],"changes":[]}}
        """;
    Files.writeString(p4, forgedRequested);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p4).load());
    assertEquals(forgedRequested, Files.readString(p4));

    // E: Forged coexistence: craft with committed harvests
    var result = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var craft = OperationJournal.Craft.fromPlan("task-c", "user", "world", 0, 64, 0, result.plan());
    Path p5 = temp.resolve("forged-coexistence-harvest.json");
    OperationJournal tempJ = new OperationJournal(p5);
    tempJ.load();
    tempJ.prepareCraft(craft);
    String craftJson = Files.readString(p5);
    String coexistenceJson = craftJson.replace("\"harvests\":[]",
        "\"harvests\":[{\"taskId\":\"task-h\",\"world\":\"world\",\"x\":1,\"y\":64,\"z\":1,\"beforeCollected\":0,\"beforeCargo\":0,\"material\":\"OAK_LOG\",\"outputMaterial\":\"OAK_LOG\",\"afterCollected\":1,\"afterCargo\":1}]");
    Files.writeString(p5, coexistenceJson);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p5).load());
    assertEquals(coexistenceJson, Files.readString(p5));

    // F: Forged coexistence: craft with pendingClear
    Path p6 = temp.resolve("forged-coexistence-clear.json");
    String coexistenceClearJson = craftJson.replace("\"pendingCraft\":",
        "\"pendingClear\":{\"taskId\":\"task-cl\",\"world\":\"world\",\"x\":2,\"y\":65,\"z\":2,\"material\":\"OAK_LEAVES\"},\"pendingCraft\":");
    Files.writeString(p6, coexistenceClearJson);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p6).load());
    assertEquals(coexistenceClearJson, Files.readString(p6));
  }

  @Test
  void legacyVersionsRejectCraftOperation() throws Exception {
    var result = CraftingPlanner.plan(chestWithOakLogs(27, 1), CraftRecipe.OAK_PLANKS, 4);
    var craft = OperationJournal.Craft.fromPlan("task-c", "user", "world", 0, 64, 0, result.plan());

    Path p1 = temp.resolve("craft-v1.json");
    OperationJournal j1 = new OperationJournal(p1);
    j1.load();
    j1.prepareCraft(craft);
    String v4Json = Files.readString(p1);

    String v1Json = v4Json.replace("\"version\":4", "\"version\":1");
    Files.writeString(p1, v1Json);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p1).load());
    assertEquals(v1Json, Files.readString(p1));

    Path p2 = temp.resolve("craft-v2.json");
    String v2Json = v4Json.replace("\"version\":4", "\"version\":2");
    Files.writeString(p2, v2Json);
    assertThrows(java.io.IOException.class, () -> new OperationJournal(p2).load());
    assertEquals(v2Json, Files.readString(p2));
  }

  @Test
  void legacyVersionsWithoutCraftLoadCleanly() throws Exception {
    // v1
    Path p1 = temp.resolve("clean-v1.json");
    Files.writeString(p1, """
        {"version":1,"harvests":[],"pendingHarvest":{"taskId":"legacy-1","world":"world","x":1,"y":2,"z":3,"beforeCollected":0,"beforeCargo":0}}
        """);
    OperationJournal j1 = new OperationJournal(p1);
    j1.load();
    assertEquals("legacy-1", j1.pendingHarvest().orElseThrow().taskId());

    // v2
    Path p2 = temp.resolve("clean-v2.json");
    Files.writeString(p2, """
        {"version":2,"harvests":[],"pendingHarvest":{"taskId":"legacy-2","world":"world","x":1,"y":2,"z":3,"beforeCollected":0,"beforeCargo":0,"material":"OAK_LOG","outputMaterial":"OAK_LOG","afterCollected":1,"afterCargo":1,"beforeOutputCargo":0}}
        """);
    OperationJournal j2 = new OperationJournal(p2);
    j2.load();
    assertEquals("legacy-2", j2.pendingHarvest().orElseThrow().taskId());

    // v3
    Path p3 = temp.resolve("clean-v3.json");
    Files.writeString(p3, """
        {"version":3,"harvests":[],"clears":[],"pendingHarvest":{"taskId":"legacy-3","world":"world","x":1,"y":2,"z":3,"beforeCollected":0,"beforeCargo":0,"material":"OAK_LOG","outputMaterial":"OAK_LOG","afterCollected":1,"afterCargo":1}}
        """);
    OperationJournal j3 = new OperationJournal(p3);
    j3.load();
    assertEquals("legacy-3", j3.pendingHarvest().orElseThrow().taskId());
  }
}

