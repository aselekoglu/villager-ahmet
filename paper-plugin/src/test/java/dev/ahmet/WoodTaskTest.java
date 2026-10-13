package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}

