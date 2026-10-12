package dev.ahmet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class CargoDepositPlanner {
  static final String CARGO_MATERIAL = "OAK_LOG";

  private CargoDepositPlanner() {}

  record Slot(String material, int amount, int maxAmount) {
    Slot {
      if (maxAmount < 1 || maxAmount > 64) throw new IllegalArgumentException("Invalid stack limit");
      if (material == null && amount != 0) throw new IllegalArgumentException("Empty slots must have zero items");
      if (material != null && (amount < 1 || amount > maxAmount)) throw new IllegalArgumentException("Invalid stack amount");
    }

    static Slot empty(int maxAmount) {
      return new Slot(null, 0, maxAmount);
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

  record Plan(int requested, int moved, int remaining, List<Change> changes) {
    Plan {
      if (requested < 1 || requested > 64 || moved < 0 || remaining < 0 || moved + remaining != requested) {
        throw new IllegalArgumentException("Invalid deposit plan");
      }
      changes = List.copyOf(changes);
    }
  }

  static Plan plan(List<Slot> contents, int requested) {
    if (contents == null) throw new IllegalArgumentException("Inventory is required");
    if (requested < 1 || requested > 64) throw new IllegalArgumentException("Cargo must be between 1 and 64");
    List<Change> changes = new ArrayList<>();
    int remaining = requested;

    for (int i = 0; i < contents.size() && remaining > 0; i++) {
      Slot slot = Objects.requireNonNull(contents.get(i), "Slot is required");
      if (!CARGO_MATERIAL.equals(slot.material()) || slot.amount() == slot.maxAmount()) continue;
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

    return new Plan(requested, requested - remaining, remaining, changes);
  }

  static List<Slot> apply(List<Slot> contents, Plan plan) {
    if (contents == null || plan == null) throw new IllegalArgumentException("Inventory and plan are required");
    List<Slot> result = new ArrayList<>(contents);
    for (Change change : plan.changes()) {
      if (change.slot() >= result.size()) throw new IllegalStateException("Chest inventory shape changed during deposit");
      Slot current = Objects.requireNonNull(result.get(change.slot()), "Slot is required");
      boolean before = Objects.equals(current.material(), change.beforeMaterial()) && current.amount() == change.beforeAmount();
      boolean after = CARGO_MATERIAL.equals(current.material()) && current.amount() == change.afterAmount();
      if (before) result.set(change.slot(), new Slot(CARGO_MATERIAL, change.afterAmount(), current.maxAmount()));
      else if (!after) throw new IllegalStateException("Chest slot changed during pending deposit");
    }
    return List.copyOf(result);
  }
}
