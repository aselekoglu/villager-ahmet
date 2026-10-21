package dev.ahmet;

import java.util.Locale;
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

  record CraftRequest(CraftRecipe recipe, int amount, String itemName) {}

  static Optional<CraftRequest> parseCraft(String recipeArg, String amountText) {
    if (recipeArg == null || amountText == null) return Optional.empty();
    final int amount;
    try {
      amount = Integer.parseInt(amountText.trim());
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
    if (amount < 4 || amount > 64 || amount % 4 != 0) return Optional.empty();
    String normalized = recipeArg.trim().toLowerCase(Locale.ROOT);
    CraftRecipe recipe = CraftRecipe.forKey(normalized);
    if (recipe == null) {
      if ("oak_planks".equals(normalized) || "oak_plank".equals(normalized)
          || "mese_tahtasi".equals(normalized) || "meşe_tahtası".equals(normalized)
          || "mese_tahta".equals(normalized) || "meşe_tahta".equals(normalized)) {
        recipe = CraftRecipe.OAK_PLANKS;
      } else if ("stick".equals(normalized) || "sticks".equals(normalized)
          || "cubuk".equals(normalized) || "çubuk".equals(normalized)) {
        recipe = CraftRecipe.STICK;
      }
    }
    if (recipe == null) return Optional.empty();
    String itemName = recipe == CraftRecipe.OAK_PLANKS ? "oak_planks" : "stick";
    return Optional.of(new CraftRequest(recipe, amount, itemName));
  }

  record FarmRequest(String preference, int amount, String itemName) {}

  static Optional<FarmRequest> parseFarm(String cropArg, String amountText) {
    if (cropArg == null || amountText == null) return Optional.empty();
    final int amount;
    try {
      amount = Integer.parseInt(amountText.trim());
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
    if (amount < 1 || amount > 64) return Optional.empty();
    Optional<String> prefOpt = FarmCatalog.preferenceForAlias(cropArg);
    if (prefOpt.isEmpty()) return Optional.empty();
    String preference = prefOpt.get();
    String itemName = FarmCatalog.displayItemName(preference);
    if (itemName == null) itemName = "ekin";
    return Optional.of(new FarmRequest(preference, amount, itemName));
  }
}
