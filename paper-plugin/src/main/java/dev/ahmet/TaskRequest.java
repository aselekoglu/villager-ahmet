package dev.ahmet;

import java.util.Optional;

/** Validated direct command request; shares the same bounded preferences as chat intents. */
record TaskRequest(String preference, int amount, String itemName) {
  static Optional<TaskRequest> parse(String itemId, String amountText) {
    if (itemId == null || amountText == null) return Optional.empty();
    final int amount;
    try {
      amount = Integer.parseInt(amountText);
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
    if (amount < 1 || amount > 64) return Optional.empty();
    String preference = WoodMaterials.preferenceForItemId(itemId);
    if (preference == null) preference = ResourceCatalog.preferenceFor(itemId).orElse(null);
    if (preference == null || !WoodMaterials.isPreference(preference)) return Optional.empty();
    String name = MaterialCatalog.normalizeId(itemId);
    if (name == null || "wood".equals(name) || "logs".equals(name) || "log".equals(name)
        || "odun".equals(name) || "kutuk".equals(name) || "kütük".equals(name)) name = "odun";
    return Optional.of(new TaskRequest(preference, amount, name));
  }
}
