package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CraftingPlannerTest {
  @Test
  void acceptsOnlyExactFourItemOutputBatchesWithinTheBound() {
    assertEquals(CraftingPlanner.Failure.INVALID_OUTPUT_COUNT,
        CraftingPlanner.plan(chest(logs("OAK_LOG", 16)), CraftRecipe.OAK_PLANKS, 0).failure());
    assertEquals(CraftingPlanner.Failure.INVALID_OUTPUT_COUNT,
        CraftingPlanner.plan(chest(logs("OAK_LOG", 16)), CraftRecipe.OAK_PLANKS, 5).failure());
    assertEquals(CraftingPlanner.Failure.INVALID_OUTPUT_COUNT,
        CraftingPlanner.plan(chest(logs("OAK_LOG", 16)), CraftRecipe.OAK_PLANKS, 68).failure());
    assertEquals(CraftingPlanner.Failure.INVALID_OUTPUT_COUNT,
        CraftingPlanner.plan(chest(logs("OAK_LOG", 16)), CraftRecipe.OAK_PLANKS, Integer.MAX_VALUE).failure());
    assertEquals(4, CraftingPlanner.plan(chest(logs("OAK_LOG", 1)), CraftRecipe.OAK_PLANKS, 4)
        .plan().outputCount());
    assertEquals(64, CraftingPlanner.plan(chest(logs("OAK_LOG", 16)), CraftRecipe.OAK_PLANKS, 64)
        .plan().outputCount());
  }

  @Test
  void oakLogVariantsCraftPlanksAndMixedPlainPlanksCraftSticks() {
    for (String log : List.of("OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_LOG", "STRIPPED_OAK_WOOD")) {
      var result = CraftingPlanner.plan(chest(logs(log, 1)), CraftRecipe.OAK_PLANKS, 4);
      assertTrue(result.succeeded(), log);
      assertEquals(1, result.plan().inputCount());
      assertEquals("OAK_PLANKS", result.plan().after().get(0).material());
    }
    var mixed = CraftingPlanner.plan(chest(
        new CraftingPlanner.Slot("OAK_PLANKS", 1, 64, true),
        new CraftingPlanner.Slot("BIRCH_PLANKS", 1, 64, true)), CraftRecipe.STICK, 4);
    assertTrue(mixed.succeeded());
    assertEquals("STICK", mixed.plan().after().get(0).material());
  }

  @Test
  void debitsInAscendingSlotOrderBeforeCreditingAndUsesFreedSlots() {
    var before = chest(
        new CraftingPlanner.Slot("OAK_PLANKS", 1, 64, true),
        new CraftingPlanner.Slot("BIRCH_PLANKS", 1, 64, true),
        new CraftingPlanner.Slot("STICK", 62, 64, true),
        CraftingPlanner.Slot.empty(64));
    var plan = CraftingPlanner.plan(before, CraftRecipe.STICK, 4).plan();
    assertEquals(List.of(0, 1, 2), plan.changes().stream().map(CraftingPlanner.Change::slot).toList());
    assertEquals(new CraftingPlanner.Slot("STICK", 64, 64, true), plan.after().get(2));
    assertEquals(new CraftingPlanner.Slot("STICK", 2, 64, true), plan.after().get(0));
    assertEquals(CraftingPlanner.Slot.empty(64), plan.after().get(1));
  }

  @Test
  void outputCapacityMustFitTheWholeBatchEvenWhenInputsAreAvailable() {
    var fullExceptOne = new ArrayList<CraftingPlanner.Slot>();
    for (int i = 0; i < 27; i++) fullExceptOne.add(new CraftingPlanner.Slot("COBBLESTONE", 64, 64, true));
    fullExceptOne.set(0, new CraftingPlanner.Slot("OAK_LOG", 64, 64, true));
    fullExceptOne.set(1, new CraftingPlanner.Slot("OAK_PLANKS", 64, 64, true));
    var before = List.copyOf(fullExceptOne);
    var result = CraftingPlanner.plan(before, CraftRecipe.OAK_PLANKS, 64);
    assertEquals(CraftingPlanner.Failure.OUTPUT_CAPACITY, result.failure());
    assertEquals(before, result.noOpSnapshot());
  }

  @Test
  void oakPlankRecipeConservesIndependentMaterialTotalsAtFourAndSixtyFourOutputs() {
    var fourBefore = chest(
        new CraftingPlanner.Slot("OAK_LOG", 1, 64, true),
        new CraftingPlanner.Slot("OAK_PLANKS", 2, 64, true),
        new CraftingPlanner.Slot("OAK_WOOD", 7, 64, false),
        new CraftingPlanner.Slot("DIAMOND", 3, 16, true));
    var four = CraftingPlanner.plan(fourBefore, CraftRecipe.OAK_PLANKS, 4);
    assertTrue(four.succeeded());
    var fourAfter = CraftingPlanner.apply(fourBefore, four.plan());
    assertMaterialDeltas(fourBefore, fourAfter, Map.of("OAK_LOG", -1, "OAK_PLANKS", 4));
    assertEquals(fourBefore.get(2), fourAfter.get(2));
    assertEquals(fourBefore.get(3), fourAfter.get(3));

    var sixtyFourBefore = chest(
        new CraftingPlanner.Slot("OAK_LOG", 4, 64, true),
        new CraftingPlanner.Slot("OAK_WOOD", 4, 64, true),
        new CraftingPlanner.Slot("STRIPPED_OAK_LOG", 4, 64, true),
        new CraftingPlanner.Slot("STRIPPED_OAK_WOOD", 4, 64, true),
        new CraftingPlanner.Slot("OAK_PLANKS", 16, 64, true),
        new CraftingPlanner.Slot("DIAMOND", 3, 16, true));
    var sixtyFour = CraftingPlanner.plan(sixtyFourBefore, CraftRecipe.OAK_PLANKS, 64);
    assertTrue(sixtyFour.succeeded());
    var sixtyFourAfter = CraftingPlanner.apply(sixtyFourBefore, sixtyFour.plan());
    assertMaterialDeltas(sixtyFourBefore, sixtyFourAfter, Map.of(
        "OAK_LOG", -4, "OAK_WOOD", -4, "STRIPPED_OAK_LOG", -4, "STRIPPED_OAK_WOOD", -4,
        "OAK_PLANKS", 64));
    assertEquals(sixtyFourBefore.get(5), sixtyFourAfter.get(5));
  }

  @Test
  void stickRecipeConservesMixedPlankInputsAtFourAndSixtyFourOutputs() {
    var fourBefore = chest(
        new CraftingPlanner.Slot("OAK_PLANKS", 1, 64, true),
        new CraftingPlanner.Slot("BIRCH_PLANKS", 1, 64, true),
        new CraftingPlanner.Slot("SPRUCE_PLANKS", 9, 64, false),
        new CraftingPlanner.Slot("STICK", 2, 64, true),
        new CraftingPlanner.Slot("DIAMOND", 3, 16, true));
    var four = CraftingPlanner.plan(fourBefore, CraftRecipe.STICK, 4);
    assertTrue(four.succeeded());
    var fourAfter = CraftingPlanner.apply(fourBefore, four.plan());
    assertMaterialDeltas(fourBefore, fourAfter, Map.of(
        "OAK_PLANKS", -1, "BIRCH_PLANKS", -1, "STICK", 4));
    assertEquals(fourBefore.get(2), fourAfter.get(2));
    assertEquals(fourBefore.get(4), fourAfter.get(4));

    var sixtyFourBefore = chest(
        new CraftingPlanner.Slot("OAK_PLANKS", 16, 64, true),
        new CraftingPlanner.Slot("BIRCH_PLANKS", 16, 64, true),
        new CraftingPlanner.Slot("SPRUCE_PLANKS", 12, 64, false),
        new CraftingPlanner.Slot("STICK", 60, 64, true),
        new CraftingPlanner.Slot("DIAMOND", 3, 16, true));
    var sixtyFour = CraftingPlanner.plan(sixtyFourBefore, CraftRecipe.STICK, 64);
    assertTrue(sixtyFour.succeeded());
    var sixtyFourAfter = CraftingPlanner.apply(sixtyFourBefore, sixtyFour.plan());
    assertMaterialDeltas(sixtyFourBefore, sixtyFourAfter, Map.of(
        "OAK_PLANKS", -16, "BIRCH_PLANKS", -16, "SPRUCE_PLANKS", 0, "STICK", 64));
    assertEquals(sixtyFourBefore.get(2), sixtyFourAfter.get(2));
    assertEquals(sixtyFourBefore.get(4), sixtyFourAfter.get(4));
  }

  @Test
  void fullChestCanFitOutputOnlyByReusingTheFullyConsumedInputSlot() {
    var before = new ArrayList<CraftingPlanner.Slot>();
    before.add(new CraftingPlanner.Slot("OAK_LOG", 1, 64, true));
    for (int i = 1; i < 27; i++) before.add(new CraftingPlanner.Slot("COBBLESTONE", 64, 64, true));
    var snapshot = List.copyOf(before);

    var result = CraftingPlanner.plan(snapshot, CraftRecipe.OAK_PLANKS, 4);

    assertTrue(result.succeeded());
    var after = CraftingPlanner.apply(snapshot, result.plan());
    assertEquals(new CraftingPlanner.Slot("OAK_PLANKS", 4, 64, true), after.get(0));
    for (int i = 1; i < 27; i++) assertEquals(snapshot.get(i), after.get(i));
    assertMaterialDeltas(snapshot, after, Map.of("OAK_LOG", -1, "OAK_PLANKS", 4, "COBBLESTONE", 0));
  }

  @Test
  void insufficientIngredientsAreAnExplicitNoOpAndDecoratedOrUnrelatedSlotsRemainUntouched() {
    var before = chest(
        new CraftingPlanner.Slot("OAK_LOG", 1, 64, true),
        new CraftingPlanner.Slot("OAK_LOG", 9, 64, false),
        new CraftingPlanner.Slot("DIAMOND", 3, 16, true));
    var insufficient = CraftingPlanner.plan(before, CraftRecipe.OAK_PLANKS, 8);
    assertEquals(CraftingPlanner.Failure.INSUFFICIENT_INPUTS, insufficient.failure());
    assertEquals(before, insufficient.noOpSnapshot());
    var success = CraftingPlanner.plan(before, CraftRecipe.OAK_PLANKS, 4).plan();
    assertEquals(new CraftingPlanner.Slot("OAK_LOG", 9, 64, false), success.after().get(1));
    assertEquals(before.get(2), success.after().get(2));

    var ineligibleEmpty = new ArrayList<>(chest(logs("OAK_LOG", 2)));
    ineligibleEmpty.set(1, new CraftingPlanner.Slot(null, 0, 64, false));
    var withSkippedEmpty = CraftingPlanner.plan(ineligibleEmpty, CraftRecipe.OAK_PLANKS, 4).plan();
    assertEquals(ineligibleEmpty.get(1), withSkippedEmpty.after().get(1));
    assertEquals(new CraftingPlanner.Slot("OAK_PLANKS", 4, 64, true), withSkippedEmpty.after().get(2));
  }

  @Test
  void honorsSingleDoubleChestAndSlotSpecificStackLimits() {
    var single = new ArrayList<CraftingPlanner.Slot>();
    for (int i = 0; i < 27; i++) single.add(CraftingPlanner.Slot.empty(64));
    single.set(0, new CraftingPlanner.Slot("OAK_LOG", 1, 16, true));
    assertEquals(27, CraftingPlanner.plan(List.copyOf(single), CraftRecipe.OAK_PLANKS, 4).plan().before().size());
    var doubled = new ArrayList<>(single);
    for (int i = 0; i < 27; i++) doubled.add(CraftingPlanner.Slot.empty(64));
    var widePlan = CraftingPlanner.plan(List.copyOf(doubled), CraftRecipe.OAK_PLANKS, 4).plan();
    assertEquals(54, widePlan.after().size());
    assertEquals(16, widePlan.after().get(0).maxAmount());

    var tinyOutputSlots = new ArrayList<>(chest(logs("OAK_LOG", 1)));
    tinyOutputSlots.set(0, new CraftingPlanner.Slot("OAK_LOG", 1, 1, true));
    for (int i = 1; i <= 4; i++) tinyOutputSlots.set(i, CraftingPlanner.Slot.empty(1));
    var limited = CraftingPlanner.plan(tinyOutputSlots, CraftRecipe.OAK_PLANKS, 4).plan();
    assertEquals(List.of(1, 1, 1, 1), limited.after().subList(0, 4).stream()
        .map(CraftingPlanner.Slot::amount).toList());
    assertTrue(limited.after().stream().allMatch(slot -> slot.amount() <= slot.maxAmount()));
  }

  @Test
  void immutablePlanRequiresWholeSnapshotAndRecognizesOnlyWholeAfterState() {
    var before = chest(logs("OAK_LOG", 2), CraftingPlanner.Slot.empty(64));
    var plan = CraftingPlanner.plan(before, CraftRecipe.OAK_PLANKS, 4).plan();
    var after = CraftingPlanner.apply(before, plan);
    assertTrue(CraftingPlanner.fullyApplied(after, plan));
    assertEquals(after, CraftingPlanner.apply(after, plan));
    var mixed = new ArrayList<>(after);
    mixed.set(1, CraftingPlanner.Slot.empty(64));
    assertThrows(IllegalStateException.class, () -> CraftingPlanner.apply(mixed, plan));
    var changed = new ArrayList<>(after);
    changed.set(1, new CraftingPlanner.Slot("DIAMOND", 1, 64, true));
    assertThrows(IllegalStateException.class, () -> CraftingPlanner.apply(changed, plan));
    var changedCount = new ArrayList<>(after);
    changedCount.set(1, new CraftingPlanner.Slot("OAK_PLANKS", 3, 64, true));
    assertThrows(IllegalStateException.class, () -> CraftingPlanner.apply(changedCount, plan));
    var changedMeta = new ArrayList<>(after);
    changedMeta.set(1, new CraftingPlanner.Slot("OAK_PLANKS", 4, 64, false));
    assertThrows(IllegalStateException.class, () -> CraftingPlanner.apply(changedMeta, plan));
    assertThrows(IllegalArgumentException.class, () -> CraftingPlanner.apply(new ArrayList<>(after.subList(0, 26)), plan));
    assertFalse(CraftingPlanner.fullyApplied(before, plan));
  }

  @Test
  void rejectsInvalidSnapshotsAndUnsupportedRecipeNames() {
    assertThrows(IllegalArgumentException.class, () -> CraftingPlanner.plan(List.of(), CraftRecipe.OAK_PLANKS, 4));
    var nullSlot = new ArrayList<>(chest(logs("OAK_LOG", 1)));
    nullSlot.set(1, null);
    assertThrows(IllegalArgumentException.class, () -> CraftingPlanner.plan(nullSlot, CraftRecipe.OAK_PLANKS, 4));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Slot(null, 1, 64, true));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Slot("OAK_LOG", 65, 64, true));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Slot("OAK_LOG", 1, 65, true));
    assertEquals(CraftingPlanner.Failure.UNSUPPORTED_RECIPE,
        CraftingPlanner.plan(chest(logs("OAK_LOG", 4)), "minecraft:oak_stairs", 4).failure());
    assertEquals(CraftingPlanner.Failure.INSUFFICIENT_INPUTS,
        CraftingPlanner.plan(chest(logs("DARK_OAK_LOG", 4)), CraftRecipe.OAK_PLANKS, 4).failure());
  }

  @Test
  void snapshotsAndDeltaListsDoNotRetainMutableAliasesAndForgedPlansAreRejected() {
    var mutable = new ArrayList<>(chest(logs("OAK_LOG", 1), CraftingPlanner.Slot.empty(64)));
    var plan = CraftingPlanner.plan(mutable, CraftRecipe.OAK_PLANKS, 4).plan();
    mutable.set(1, new CraftingPlanner.Slot("DIAMOND", 1, 64, true));
    assertTrue(CraftingPlanner.apply(chest(logs("OAK_LOG", 1), CraftingPlanner.Slot.empty(64)), plan)
        .get(1).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 4, 1, plan.before(), plan.after(), List.of(), 27));
    var duplicateChanges = new ArrayList<>(plan.changes());
    duplicateChanges.add(plan.changes().getFirst());
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 4, 1, plan.before(), plan.after(), duplicateChanges, 27));
    var outsideChanges = new ArrayList<>(plan.changes());
    outsideChanges.add(new CraftingPlanner.Change(27, null, 0, "OAK_PLANKS", 1));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 4, 1, plan.before(), plan.after(), outsideChanges, 27));

    var outputWithoutDebit = new ArrayList<>(plan.before());
    outputWithoutDebit.set(1, new CraftingPlanner.Slot("OAK_PLANKS", 4, 64, true));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 4, 1, plan.before(), outputWithoutDebit,
        List.of(new CraftingPlanner.Change(1, null, 0, "OAK_PLANKS", 4)), 27));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 8, 2, plan.before(), plan.after(), plan.changes(), 27));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 4, 2, plan.before(), plan.after(), plan.changes(), 27));
    var doubleChestBefore = new ArrayList<>(plan.before());
    for (int i = 0; i < 27; i++) doubleChestBefore.add(CraftingPlanner.Slot.empty(64));
    var doubleChestPlan = CraftingPlanner.plan(doubleChestBefore, CraftRecipe.OAK_PLANKS, 4).plan();
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 4, 1, doubleChestPlan.before(), doubleChestPlan.after(), doubleChestPlan.changes(), 27));
    var afterWithIneligibleOutput = new ArrayList<>(plan.after());
    afterWithIneligibleOutput.set(0, new CraftingPlanner.Slot("OAK_PLANKS", 4, 64, false));
    assertThrows(IllegalArgumentException.class, () -> new CraftingPlanner.Plan(
        CraftRecipe.OAK_PLANKS, 4, 1, plan.before(), afterWithIneligibleOutput, plan.changes(), 27));
  }

  private static void assertMaterialDeltas(List<CraftingPlanner.Slot> before,
      List<CraftingPlanner.Slot> after, Map<String, Integer> expected) {
    Map<String, Integer> beforeTotals = materialTotals(before);
    Map<String, Integer> afterTotals = materialTotals(after);
    var materials = new HashSet<>(beforeTotals.keySet());
    materials.addAll(afterTotals.keySet());
    materials.addAll(expected.keySet());
    for (String material : materials) {
      int actual = afterTotals.getOrDefault(material, 0) - beforeTotals.getOrDefault(material, 0);
      assertEquals(expected.getOrDefault(material, 0), actual, material);
    }
  }

  private static Map<String, Integer> materialTotals(List<CraftingPlanner.Slot> contents) {
    Map<String, Integer> totals = new HashMap<>();
    for (CraftingPlanner.Slot slot : contents) {
      if (slot.material() != null) totals.merge(slot.material(), slot.amount(), Integer::sum);
    }
    return totals;
  }

  private static CraftingPlanner.Slot logs(String material, int amount) {
    return new CraftingPlanner.Slot(material, amount, 64, true);
  }

  private static List<CraftingPlanner.Slot> chest(CraftingPlanner.Slot... slots) {
    var contents = new ArrayList<>(List.of(slots));
    while (contents.size() < 27) contents.add(CraftingPlanner.Slot.empty(64));
    return List.copyOf(contents);
  }
}
