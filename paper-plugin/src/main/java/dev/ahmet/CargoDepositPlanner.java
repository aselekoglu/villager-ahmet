package dev.ahmet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class CargoDepositPlanner {
  /** Legacy material identifier used for v1 journal recovery. */
  static final String CARGO_MATERIAL = "OAK_LOG";

  private CargoDepositPlanner() {}

  record Slot(String material, int amount, int maxAmount, boolean plainCargo) {
    Slot(String material, int amount, int maxAmount) { this(material, amount, maxAmount, true); }
    Slot {
      if (maxAmount < 1 || maxAmount > 64) throw new IllegalArgumentException("Invalid stack limit");
      if (material == null && amount != 0) throw new IllegalArgumentException("Empty slots must have zero items");
      if (material != null && (amount < 1 || amount > maxAmount)) throw new IllegalArgumentException("Invalid stack amount");
    }

    static Slot empty(int maxAmount) {
      return new Slot(null, 0, maxAmount, true);
    }

    boolean isEmpty() {
      return material == null;
    }
  }

  record Change(int slot, String beforeMaterial, int beforeAmount, int afterAmount) {
    Change {
      if (slot < 0 || beforeAmount < 0 || afterAmount < 1 || afterAmount > 64) {
        throw new IllegalArgumentException("Invalid inventory change");
      }
    }
  }

  record Plan(String material, int requested, int moved, int remaining, List<Change> changes) {
    Plan {
      if (!WoodMaterials.isAllowedName(material)) throw new IllegalArgumentException("Unsupported cargo material");
      if (requested < 1 || requested > 64 || moved < 0 || remaining < 0 || moved + remaining != requested) {
        throw new IllegalArgumentException("Invalid deposit plan");
      }
      changes = List.copyOf(changes);
    }
  }

  static Plan plan(List<Slot> contents, int requested) {
    return plan(contents, CARGO_MATERIAL, requested);
  }

  static Plan plan(List<Slot> contents, String material, int requested) {
    if (contents == null) throw new IllegalArgumentException("Inventory is required");
    if (!WoodMaterials.isAllowedName(material)) throw new IllegalArgumentException("Unsupported cargo material");
    if (requested < 1 || requested > 64) throw new IllegalArgumentException("Cargo must be between 1 and 64");
    List<Change> changes = new ArrayList<>();
    int remaining = requested;

    for (int i = 0; i < contents.size() && remaining > 0; i++) {
      Slot slot = Objects.requireNonNull(contents.get(i), "Slot is required");
      if (!slot.plainCargo() || !material.equals(slot.material()) || slot.amount() == slot.maxAmount()) continue;
      int added = Math.min(remaining, slot.maxAmount() - slot.amount());
      changes.add(new Change(i, slot.material(), slot.amount(), slot.amount() + added));
      remaining -= added;
    }
    for (int i = 0; i < contents.size() && remaining > 0; i++) {
      Slot slot = contents.get(i);
      if (!slot.isEmpty()) continue;
      int added = Math.min(remaining, slot.maxAmount());
      changes.add(new Change(i, null, 0, added));
      remaining -= added;
    }

    return new Plan(material, requested, requested - remaining, remaining, changes);
  }

  static boolean fullyApplied(List<Slot> contents, Plan plan) {
    if (contents == null || plan == null) return false;
    for (Change change : plan.changes()) {
      if (change.slot() >= contents.size()) return false;
      Slot current = contents.get(change.slot());
      if (!plan.material().equals(current.material()) || current.amount() != change.afterAmount() || !current.plainCargo()) return false;
    }
    return true;
  }

  static List<Slot> apply(List<Slot> contents, Plan plan) {
    if (contents == null || plan == null) throw new IllegalArgumentException("Inventory and plan are required");
    List<Slot> result = new ArrayList<>(contents);
    for (Change change : plan.changes()) {
      if (change.slot() >= result.size()) throw new IllegalStateException("Chest inventory shape changed during deposit");
      Slot current = Objects.requireNonNull(result.get(change.slot()), "Slot is required");
      boolean before = current.plainCargo() && Objects.equals(current.material(), change.beforeMaterial()) && current.amount() == change.beforeAmount();
      boolean after = current.plainCargo() && plan.material().equals(current.material()) && current.amount() == change.afterAmount();
      if (before) result.set(change.slot(), new Slot(plan.material(), change.afterAmount(), current.maxAmount()));
      else if (!after) throw new IllegalStateException("Chest slot changed during pending deposit");
    }
    return List.copyOf(result);
  }
}
