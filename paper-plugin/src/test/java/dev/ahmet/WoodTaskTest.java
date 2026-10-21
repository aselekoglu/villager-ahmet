package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class WoodTaskTest {
  @Test
  void acceptsVanillaLogStemAndStrippedTypesButRejectsPlanks() {
    assertEquals(true, WoodMaterials.isAllowedName("OAK_LOG"));
    assertEquals(true, WoodMaterials.isAllowedName("BIRCH_WOOD"));
    assertEquals(true, WoodMaterials.isAllowedName("STRIPPED_CRIMSON_STEM"));
    assertEquals(true, WoodMaterials.isAllowedName("STRIPPED_PALE_OAK_LOG"));
    assertEquals(true, WoodMaterials.isAllowedName("BAMBOO"));
    assertEquals(false, WoodMaterials.isAllowedName("OAK_PLANKS"));
    assertEquals(false, WoodMaterials.isAllowedName("DIAMOND"));
  }

  @Test
  void startRejectsAmountsOutsideTheServerBound() {
    WoodTask task = new WoodTask();

    assertThrows(IllegalArgumentException.class, () -> task.start(UUID.randomUUID(), 0));
    assertThrows(IllegalArgumentException.class, () -> task.start(UUID.randomUUID(), 65));
  }

  @Test
  void harvestIsBoundedAndCancellationPreservesCargo() {
    WoodTask task = new WoodTask();
    task.start(UUID.randomUUID(), 1);

    task.harvestOne();
    assertEquals(1, task.collected);
    assertEquals(1, task.cargo);
    assertThrows(IllegalStateException.class, task::harvestOne);

    task.cancel();
    assertFalse(task.active);
    assertEquals(1, task.cargo);
  }

  @Test
  void tracksAndDepositsMixedWoodItemsIndependently() {
    WoodTask task = new WoodTask();
    task.start(UUID.randomUUID(), 3, "any");
    task.harvestOne("BIRCH_LOG");
    task.harvestOne("OAK_LOG");
    task.harvestOne("STRIPPED_CRIMSON_STEM");

    assertEquals(3, task.cargo);
    assertEquals(1, task.cargoItems().get("BIRCH_LOG"));
    assertEquals(1, task.cargoItems().get("OAK_LOG"));
    assertEquals(1, task.cargoItems().get("STRIPPED_CRIMSON_STEM"));
    task.deposit("BIRCH_LOG", 1);
    assertEquals(2, task.cargo);
    assertEquals(1, task.deposited);
    assertFalse(task.cargoItems().containsKey("BIRCH_LOG"));
  }

  @Test
  void mixedMaterialCargoPersistsAndReloadsByExactItemType() {
    WoodTask original = new WoodTask();
    original.start(UUID.randomUUID(), 2, "any");
    original.harvestOne("BIRCH_LOG");
    original.harvestOne("STRIPPED_CRIMSON_STEM");
    YamlConfiguration config = new YamlConfiguration();
    original.save(config);

    WoodTask restored = new WoodTask();
    restored.load(config);
    assertEquals(2, restored.cargo);
    assertEquals(1, restored.cargoItems().get("BIRCH_LOG"));
    assertEquals(1, restored.cargoItems().get("STRIPPED_CRIMSON_STEM"));
    assertEquals("any", restored.materialPreference);
  }

  @Test
  void inconsistentPersistedMixedCargoFailsClosed() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("task.cargo", 3);
    config.set("task.cargoItems", java.util.Map.of("BIRCH_LOG", 2));
    WoodTask restored = new WoodTask();
    assertThrows(IllegalStateException.class, () -> restored.load(config));
  }

  @Test
  void rejectsPlanksAndMaterialPreferencesOutsideTheAllowlist() {
    WoodTask task = new WoodTask();
    assertThrows(IllegalArgumentException.class, () -> task.start(UUID.randomUUID(), 2, "planks"));
    task.start(UUID.randomUUID(), 1, "any");
    assertThrows(IllegalArgumentException.class, () -> task.harvestOne("OAK_PLANKS"));
  }

  @Test
  void depositCannotExceedCargo() {
    WoodTask task = new WoodTask();
    task.start(UUID.randomUUID(), 1);
    task.harvestOne();

    assertThrows(IllegalArgumentException.class, () -> task.deposit(2));
    task.deposit(1);
    assertEquals(0, task.cargo);
    assertEquals(1, task.deposited);
  }

  @Test
  void boundedCommonResourceTasksCarryTheMappedOutputItem() {
    WoodTask task = new WoodTask();
    String preference = ResourceCatalog.preferenceFor("minecraft:raw_iron").orElseThrow();
    task.start(UUID.randomUUID(), 2, preference);
    task.harvestOne("RAW_IRON");
    task.harvestOne("RAW_IRON");
    assertEquals(2, task.cargoItems().get("RAW_IRON"));
    task.deposit("RAW_IRON", 2);
    assertTrue(task.readyToFinish());
  }

  @Test
  void unreachableContinuationCanDeliverCollectedCargoAsAnExplicitPartialResult() {
    WoodTask task = new WoodTask();
    task.start(UUID.randomUUID(), 64, "any");
    for (int i = 0; i < 5; i++) task.harvestOne("BIRCH_LOG");

    task.requestPartialDelivery();
    assertTrue(task.partialDelivery);
    assertEquals(64, task.requested, "keep the original requested amount for truthful reporting");
    assertFalse(task.readyToFinish(), "cargo must be delivered before task completion");
    task.deposit("BIRCH_LOG", 5);
    assertTrue(task.readyToFinish());
    task.finish();
    assertFalse(task.active);
    assertFalse(task.partialDelivery);
  }

  @Test
  void partialDeliveryStateSurvivesRestart() {
    WoodTask original = new WoodTask();
    original.start(UUID.randomUUID(), 64, "any");
    original.harvestOne("BIRCH_LOG");
    original.requestPartialDelivery();
    YamlConfiguration config = new YamlConfiguration();
    original.save(config);

    WoodTask restored = new WoodTask();
    restored.load(config);
    assertTrue(restored.partialDelivery);
    assertEquals(64, restored.requested);
    assertEquals(1, restored.cargo);
  }

  @Test
  void obstructionClearBudgetIsEnforcedAndPersisted() {
    WoodTask task = new WoodTask();
    task.start(UUID.randomUUID(), 10, "any");
    assertEquals(0, task.cleared);
    assertTrue(task.canClearObstruction());

    for (int i = 0; i < WoodTask.MAX_JOB_CLEARS; i++) {
      task.recordClear();
    }
    assertEquals(WoodTask.MAX_JOB_CLEARS, task.cleared);
    assertFalse(task.canClearObstruction());
    assertThrows(IllegalStateException.class, task::recordClear);

    YamlConfiguration config = new YamlConfiguration();
    task.save(config);

    WoodTask restored = new WoodTask();
    restored.load(config);
    assertEquals(WoodTask.MAX_JOB_CLEARS, restored.cleared);
    assertFalse(restored.canClearObstruction());
  }

  @Test
  void legacyConfigWithoutKindDefaultsToGather() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("task.active", true);
    config.set("task.owner", UUID.randomUUID().toString());
    config.set("task.requested", 10);
    config.set("task.collected", 2);
    config.set("task.deposited", 2);
    config.set("task.cargo", 0);
    config.set("task.materialPreference", "any");

    WoodTask restored = new WoodTask();
    restored.load(config);
    assertEquals(WoodTask.Kind.GATHER, restored.kind);
    assertTrue(restored.isGather());
    assertFalse(restored.isCraft());
    assertEquals(null, restored.craftRecipe());
  }

  @Test
  void craftTaskLifecycleAndDescribe() {
    WoodTask task = new WoodTask();
    UUID user = UUID.randomUUID();
    task.startCraft(user, CraftRecipe.OAK_PLANKS, 4);

    assertTrue(task.active);
    assertTrue(task.isCraft());
    assertFalse(task.isGather());
    assertEquals(CraftRecipe.OAK_PLANKS, task.craftRecipe());
    assertEquals(4, task.requested);
    assertEquals(0, task.collected);
    assertEquals(0, task.deposited);
    assertEquals(0, task.cargo);
    assertEquals("oak_planks 0/4 üretildi", task.describe());
    assertFalse(task.readyToDeliver());
    assertFalse(task.readyToFinish());

    task.completeCraft(4);
    assertEquals(4, task.collected);
    assertEquals(4, task.deposited);
    assertEquals("oak_planks 4/4 üretildi", task.describe());
    assertFalse(task.readyToDeliver());
    assertTrue(task.readyToFinish());

    task.cancel();
    assertEquals("boşta (taşınan kaynak: 0)", task.describe());
  }

  @Test
  void loadFailureLeavesExistingTaskFieldsCompletelyUnmutated() {
    WoodTask task = new WoodTask();
    UUID user = UUID.randomUUID();
    task.start(user, 10, "any");
    task.harvestOne("OAK_LOG");
    task.recordClear();

    String originalTaskId = task.taskId;
    WoodTask.Kind originalKind = task.kind;
    CraftRecipe originalRecipe = task.craftRecipe();
    boolean originalActive = task.active;
    String originalOwner = task.owner;
    int originalRequested = task.requested;
    int originalCollected = task.collected;
    int originalDeposited = task.deposited;
    int originalCargo = task.cargo;
    int originalCleared = task.cleared;
    String originalPref = task.materialPreference;
    Map<String, Integer> originalCargoItems = task.cargoItems();

    Runnable assertUnchanged = () -> {
      assertEquals(originalTaskId, task.taskId);
      assertEquals(originalKind, task.kind);
      assertEquals(originalRecipe, task.craftRecipe());
      assertEquals(originalActive, task.active);
      assertEquals(originalOwner, task.owner);
      assertEquals(originalRequested, task.requested);
      assertEquals(originalCollected, task.collected);
      assertEquals(originalDeposited, task.deposited);
      assertEquals(originalCargo, task.cargo);
      assertEquals(originalCleared, task.cleared);
      assertEquals(originalPref, task.materialPreference);
      assertEquals(originalCargoItems, task.cargoItems());
    };

    // 1. Invalid kind
    YamlConfiguration badKind = new YamlConfiguration();
    badKind.set("task.kind", "UNKNOWN");
    assertThrows(IllegalStateException.class, () -> task.load(badKind));
    assertUnchanged.run();

    // 2. Invalid craft recipe
    YamlConfiguration badRecipe = new YamlConfiguration();
    badRecipe.set("task.kind", "CRAFT");
    badRecipe.set("task.craftRecipe", "minecraft:diamond_sword");
    badRecipe.set("task.requested", 4);
    assertThrows(IllegalStateException.class, () -> task.load(badRecipe));
    assertUnchanged.run();

    // 3. Invalid craft counters
    YamlConfiguration badCounters = validCraftConfig(4, 3, 3);
    assertThrows(IllegalStateException.class, () -> task.load(badCounters));
    assertUnchanged.run();

    // 4. Invalid cargo on craft
    YamlConfiguration badCargo = validCraftConfig(4, 0, 0);
    badCargo.set("task.cargo", 2);
    assertThrows(IllegalStateException.class, () -> task.load(badCargo));
    assertUnchanged.run();

    // 5. Invalid cargo map on gather
    YamlConfiguration badCargoMap = new YamlConfiguration();
    badCargoMap.set("task.kind", "GATHER");
    badCargoMap.set("task.cargo", 2);
    badCargoMap.set("task.cargoItems", "not-a-map");
    assertThrows(IllegalStateException.class, () -> task.load(badCargoMap));
    assertUnchanged.run();
  }

  @Test
  void craftSaveLoadRoundTripForOakPlanksAndStickAtFourAndSixtyFourOutputs() {
    for (CraftRecipe recipe : List.of(CraftRecipe.OAK_PLANKS, CraftRecipe.STICK)) {
      for (int amount : List.of(4, 64)) {
        // 1. Before completion: 0/amount
        WoodTask initial = new WoodTask();
        initial.startCraft(UUID.randomUUID(), recipe, amount);
        YamlConfiguration configBefore = new YamlConfiguration();
        initial.save(configBefore);

        WoodTask restoredBefore = new WoodTask();
        restoredBefore.load(configBefore);
        assertTrue(restoredBefore.isCraft());
        assertEquals(recipe, restoredBefore.craftRecipe());
        assertEquals(amount, restoredBefore.requested);
        assertEquals(0, restoredBefore.collected);
        assertEquals(0, restoredBefore.deposited);
        assertEquals(0, restoredBefore.cargo);
        assertTrue(restoredBefore.active);

        // 2. After completion: amount/amount
        initial.completeCraft(amount);
        YamlConfiguration configAfter = new YamlConfiguration();
        initial.save(configAfter);

        WoodTask restoredAfter = new WoodTask();
        restoredAfter.load(configAfter);
        assertTrue(restoredAfter.isCraft());
        assertEquals(recipe, restoredAfter.craftRecipe());
        assertEquals(amount, restoredAfter.requested);
        assertEquals(amount, restoredAfter.collected);
        assertEquals(amount, restoredAfter.deposited);
        assertEquals(0, restoredAfter.cargo);
        assertTrue(restoredAfter.active);
      }
    }
  }

  @Test
  void invalidTaskKindFailsClosed() {
    for (Object badKind : List.of("UNKNOWN", "", 123, true)) {
      YamlConfiguration config = new YamlConfiguration();
      config.set("task.kind", badKind);
      WoodTask task = new WoodTask();
      assertThrows(IllegalStateException.class, () -> task.load(config));
    }
  }

  @Test
  void craftTaskMissingOrUnsupportedRecipeFailsClosed() {
    // Missing recipe
    YamlConfiguration missingRecipe = new YamlConfiguration();
    missingRecipe.set("task.kind", "CRAFT");
    missingRecipe.set("task.requested", 4);
    WoodTask task1 = new WoodTask();
    assertThrows(IllegalStateException.class, () -> task1.load(missingRecipe));

    // Unsupported recipe
    YamlConfiguration unsupportedRecipe = new YamlConfiguration();
    unsupportedRecipe.set("task.kind", "CRAFT");
    unsupportedRecipe.set("task.craftRecipe", "minecraft:diamond_sword");
    unsupportedRecipe.set("task.requested", 4);
    WoodTask task2 = new WoodTask();
    assertThrows(IllegalStateException.class, () -> task2.load(unsupportedRecipe));
  }

  @Test
  void gatherTaskWithCraftRecipeFailsClosed() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("task.kind", "GATHER");
    config.set("task.craftRecipe", "minecraft:oak_planks");
    WoodTask task = new WoodTask();
    assertThrows(IllegalStateException.class, () -> task.load(config));

    YamlConfiguration configNonString = new YamlConfiguration();
    configNonString.set("task.kind", "GATHER");
    configNonString.set("task.craftRecipe", 42);
    WoodTask task2 = new WoodTask();
    assertThrows(IllegalStateException.class, () -> task2.load(configNonString));
  }

  @Test
  void craftTaskWithInvalidOutputCountsFailsClosedOnLoad() {
    for (int badAmount : List.of(0, 3, 6, 68)) {
      YamlConfiguration config = new YamlConfiguration();
      config.set("task.kind", "CRAFT");
      config.set("task.craftRecipe", "minecraft:oak_planks");
      config.set("task.requested", badAmount);
      WoodTask task = new WoodTask();
      assertThrows(IllegalStateException.class, () -> task.load(config));
    }
  }

  @Test
  void craftTaskWithInvalidCargoOrGatherStateFailsClosedOnLoad() {
    // Non-zero cargo
    YamlConfiguration c1 = validCraftConfig(4, 0, 0);
    c1.set("task.cargo", 1);
    assertThrows(IllegalStateException.class, () -> new WoodTask().load(c1));

    // partialDelivery = true
    YamlConfiguration c2 = validCraftConfig(4, 0, 0);
    c2.set("task.partialDelivery", true);
    assertThrows(IllegalStateException.class, () -> new WoodTask().load(c2));

    // cleared > 0
    YamlConfiguration c3 = validCraftConfig(4, 0, 0);
    c3.set("task.cleared", 1);
    assertThrows(IllegalStateException.class, () -> new WoodTask().load(c3));

    // non-empty cargoItems
    YamlConfiguration c4 = validCraftConfig(4, 0, 0);
    c4.set("task.cargoItems", java.util.Map.of("OAK_LOG", 1));
    assertThrows(IllegalStateException.class, () -> new WoodTask().load(c4));

    // Mismatched counters
    YamlConfiguration c5 = validCraftConfig(4, 4, 0);
    assertThrows(IllegalStateException.class, () -> new WoodTask().load(c5));

    YamlConfiguration c6 = validCraftConfig(4, 2, 2);
    assertThrows(IllegalStateException.class, () -> new WoodTask().load(c6));
  }

  private static YamlConfiguration validCraftConfig(int requested, int collected, int deposited) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("task.kind", "CRAFT");
    config.set("task.craftRecipe", "minecraft:oak_planks");
    config.set("task.requested", requested);
    config.set("task.collected", collected);
    config.set("task.deposited", deposited);
    config.set("task.cargo", 0);
    config.set("task.cleared", 0);
    config.set("task.partialDelivery", false);
    return config;
  }

  @Test
  void gatherOnlyMethodsRejectCraftTasks() {
    WoodTask craft = new WoodTask();
    craft.startCraft(UUID.randomUUID(), CraftRecipe.OAK_PLANKS, 4);

    assertThrows(IllegalStateException.class, craft::harvestOne);
    assertThrows(IllegalStateException.class, () -> craft.harvestOne("OAK_LOG"));
    assertThrows(IllegalStateException.class, () -> craft.deposit(1));
    assertThrows(IllegalStateException.class, () -> craft.deposit("OAK_LOG", 1));
    assertThrows(IllegalStateException.class, craft::requestPartialDelivery);
    assertThrows(IllegalStateException.class, craft::resumeDelivery);
    assertFalse(craft.canClearObstruction());
    assertThrows(IllegalStateException.class, craft::recordClear);
  }

  @Test
  void completeCraftRejectsGatherTasks() {
    WoodTask gather = new WoodTask();
    gather.start(UUID.randomUUID(), 4);
    assertThrows(IllegalStateException.class, () -> gather.completeCraft(4));
  }

  @Test
  void startCraftAndCompleteCraftParameterValidation() {
    WoodTask task = new WoodTask();
    UUID user = UUID.randomUUID();

    assertThrows(IllegalArgumentException.class, () -> task.startCraft(null, CraftRecipe.OAK_PLANKS, 4));
    assertThrows(IllegalArgumentException.class, () -> task.startCraft(user, null, 4));
    assertThrows(IllegalArgumentException.class, () -> task.startCraft(user, CraftRecipe.OAK_PLANKS, 0));
    assertThrows(IllegalArgumentException.class, () -> task.startCraft(user, CraftRecipe.OAK_PLANKS, 3));
    assertThrows(IllegalArgumentException.class, () -> task.startCraft(user, CraftRecipe.OAK_PLANKS, 5));
    assertThrows(IllegalArgumentException.class, () -> task.startCraft(user, CraftRecipe.OAK_PLANKS, 68));

    task.startCraft(user, CraftRecipe.OAK_PLANKS, 4);
    assertThrows(IllegalStateException.class, () -> task.startCraft(user, CraftRecipe.STICK, 4));
    assertThrows(IllegalStateException.class, () -> task.completeCraft(8));
  }
}

