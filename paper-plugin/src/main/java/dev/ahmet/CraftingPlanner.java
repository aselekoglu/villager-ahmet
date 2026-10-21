package dev.ahmet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure exact-snapshot planner. It has no Bukkit, inventory, persistence, or I/O dependency. */
final class CraftingPlanner {
  private static final int MIN_CHEST_SLOTS = 27;
  private static final int DOUBLE_CHEST_SLOTS = 54;

  private CraftingPlanner() {}

  record Slot(String material, int amount, int maxAmount, boolean plain) {
    Slot {
      if (maxAmount < 1 || maxAmount > 64) throw new IllegalArgumentException("Invalid stack limit");
      if (material == null) {
        if (amount != 0) throw new IllegalArgumentException("Empty slots must have zero items");
      } else if (!material.matches("[A-Z][A-Z0-9_]{0,63}") || amount < 1 || amount > maxAmount) {
        throw new IllegalArgumentException("Invalid material or stack amount");
      }
    }

    static Slot empty(int maxAmount) { return new Slot(null, 0, maxAmount, true); }
    boolean isEmpty() { return material == null; }
  }

  record Change(int slot, String beforeMaterial, int beforeAmount, String afterMaterial, int afterAmount) {
    Change {
      if (slot < 0 || beforeAmount < 0 || afterAmount < 0
          || (beforeMaterial == null) != (beforeAmount == 0)
          || (afterMaterial == null) != (afterAmount == 0)) {
        throw new IllegalArgumentException("Invalid inventory change");
      }
    }
  }

  enum Failure { INVALID_OUTPUT_COUNT, INSUFFICIENT_INPUTS, OUTPUT_CAPACITY, UNSUPPORTED_RECIPE }

  record Result(Plan plan, Failure failure, List<Slot> noOpSnapshot) {
    Result {
      noOpSnapshot = List.copyOf(noOpSnapshot);
      if ((plan == null) == (failure == null)) throw new IllegalArgumentException("Result must be success or failure");
      if (plan != null && !noOpSnapshot.isEmpty()) throw new IllegalArgumentException("Successful results do not carry a no-op snapshot");
    }
    boolean succeeded() { return plan != null; }
  }

  /** Includes whole snapshots so later validation cannot accept a mixture of before and after slots. */
  record Plan(CraftRecipe recipe, int outputCount, int inputCount, List<Slot> before,
      List<Slot> after, List<Change> changes, int inventorySize) {
    Plan {
      Objects.requireNonNull(recipe, "Recipe is required");
      before = validatedSnapshot(before);
      after = validatedSnapshot(after);
      changes = List.copyOf(changes);
      if ((inventorySize != MIN_CHEST_SLOTS && inventorySize != DOUBLE_CHEST_SLOTS)
          || inventorySize != before.size() || after.size() != inventorySize
          || outputCount < 4 || outputCount > 64 || outputCount % 4 != 0
          || inputCount != (outputCount / 4) * recipe.inputsPerBatch()) {
        throw new IllegalArgumentException("Invalid craft plan bounds");
      }
      List<Slot> expectedAfter = transformedSnapshot(before, recipe, outputCount);
      if (expectedAfter == null || !expectedAfter.equals(after) || !changes.equals(changesBetween(before, after))) {
        throw new IllegalArgumentException("Craft plan deltas do not match the exact recipe transformation");
      }
      if (!conservesMaterials(before, after, recipe, inputCount, outputCount)) {
        throw new IllegalArgumentException("Craft plan violates material conservation");
      }
    }
  }

  static Result plan(List<Slot> contents, CraftRecipe recipe, int exactOutputCount) {
    List<Slot> before = validatedSnapshot(contents);
    if (recipe == null) return failure(before, Failure.UNSUPPORTED_RECIPE);
    if (exactOutputCount < 4 || exactOutputCount > 64 || exactOutputCount % 4 != 0) {
      return failure(before, Failure.INVALID_OUTPUT_COUNT);
    }
    List<Slot> after = transformedSnapshot(before, recipe, exactOutputCount);
    if (after == null) {
      int inputCount = (exactOutputCount / 4) * recipe.inputsPerBatch();
      return failure(before, hasInputs(before, recipe, inputCount) ? Failure.OUTPUT_CAPACITY : Failure.INSUFFICIENT_INPUTS);
    }
    int inputCount = (exactOutputCount / 4) * recipe.inputsPerBatch();
    List<Change> changes = changesBetween(before, after);
    return new Result(new Plan(recipe, exactOutputCount, inputCount, before, after, changes, before.size()), null, List.of());
  }

  static Result plan(List<Slot> contents, String recipeKey, int exactOutputCount) {
    return plan(contents, CraftRecipe.forKey(recipeKey), exactOutputCount);
  }

  static boolean fullyApplied(List<Slot> contents, Plan plan) {
    if (contents == null || plan == null) return false;
    try {
      return validatedSnapshot(contents).equals(plan.after());
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  static List<Slot> apply(List<Slot> contents, Plan plan) {
    if (plan == null) throw new IllegalArgumentException("Plan is required");
    List<Slot> current = validatedSnapshot(contents);
    if (current.equals(plan.after())) return current;
    if (!current.equals(plan.before())) throw new IllegalStateException("Inventory changed during pending craft");
    return plan.after();
  }

  private static Result failure(List<Slot> unchanged, Failure failure) {
    return new Result(null, failure, unchanged);
  }

  private static List<Slot> validatedSnapshot(List<Slot> contents) {
    if (contents == null || (contents.size() != MIN_CHEST_SLOTS && contents.size() != DOUBLE_CHEST_SLOTS)) {
      throw new IllegalArgumentException("Crafting snapshot must contain 27 or 54 slots");
    }
    List<Slot> copy = new ArrayList<>(contents.size());
    for (Slot slot : contents) {
      if (slot == null) throw new IllegalArgumentException("Slot is required");
      copy.add(slot);
    }
    return List.copyOf(copy);
  }

  private static List<Slot> transformedSnapshot(List<Slot> before, CraftRecipe recipe, int outputCount) {
    int inputCount = (outputCount / 4) * recipe.inputsPerBatch();
    if (!hasInputs(before, recipe, inputCount)) return null;
    List<Slot> working = new ArrayList<>(before);
    int remainingInput = inputCount;
    for (int i = 0; i < working.size() && remainingInput > 0; i++) {
      Slot slot = working.get(i);
      if (!slot.plain() || slot.material() == null || !recipe.ingredients().contains(slot.material())) continue;
      int consumed = Math.min(remainingInput, slot.amount());
      int left = slot.amount() - consumed;
      working.set(i, left == 0 ? Slot.empty(slot.maxAmount())
          : new Slot(slot.material(), left, slot.maxAmount(), slot.plain()));
      remainingInput -= consumed;
    }
    int remainingOutput = outputCount;
    for (int i = 0; i < working.size() && remainingOutput > 0; i++) {
      Slot slot = working.get(i);
      if (!slot.plain() || !recipe.output().equals(slot.material()) || slot.amount() == slot.maxAmount()) continue;
      int added = Math.min(remainingOutput, slot.maxAmount() - slot.amount());
      working.set(i, new Slot(recipe.output(), slot.amount() + added, slot.maxAmount(), true));
      remainingOutput -= added;
    }
    for (int i = 0; i < working.size() && remainingOutput > 0; i++) {
      Slot slot = working.get(i);
      if (!slot.isEmpty() || !slot.plain()) continue;
      int added = Math.min(remainingOutput, slot.maxAmount());
      working.set(i, new Slot(recipe.output(), added, slot.maxAmount(), true));
      remainingOutput -= added;
    }
    return remainingOutput == 0 ? List.copyOf(working) : null;
  }

  private static boolean hasInputs(List<Slot> contents, CraftRecipe recipe, int inputCount) {
    int available = 0;
    for (Slot slot : contents) {
      if (slot.plain() && slot.material() != null && recipe.ingredients().contains(slot.material())) available += slot.amount();
    }
    return available >= inputCount;
  }

  private static List<Change> changesBetween(List<Slot> before, List<Slot> after) {
    List<Change> changes = new ArrayList<>();
    for (int i = 0; i < before.size(); i++) {
      Slot b = before.get(i), a = after.get(i);
      if (!b.equals(a)) changes.add(new Change(i, b.material(), b.amount(), a.material(), a.amount()));
    }
    return List.copyOf(changes);
  }

  private static boolean conservesMaterials(List<Slot> before, List<Slot> after,
      CraftRecipe recipe, int inputCount, int outputCount) {
    long ingredientDelta = 0;
    long outputDelta = 0;
    java.util.Map<String, Long> deltas = new java.util.HashMap<>();
    for (Slot slot : before) if (slot.material() != null) deltas.merge(slot.material(), -(long) slot.amount(), Long::sum);
    for (Slot slot : after) if (slot.material() != null) deltas.merge(slot.material(), (long) slot.amount(), Long::sum);
    for (var entry : deltas.entrySet()) {
      if (recipe.ingredients().contains(entry.getKey())) ingredientDelta += entry.getValue();
      else if (recipe.output().equals(entry.getKey())) outputDelta += entry.getValue();
      else if (entry.getValue() != 0) return false;
    }
    return ingredientDelta == -inputCount && outputDelta == outputCount;
  }
}
