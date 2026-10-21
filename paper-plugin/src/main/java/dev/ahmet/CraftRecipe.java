package dev.ahmet;

import java.util.Set;

/** The small, source-verified vanilla recipe allowlist for the planner-only M5-A increment. */
enum CraftRecipe {
  OAK_PLANKS("minecraft:oak_planks", Set.of(
      "OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_LOG", "STRIPPED_OAK_WOOD"), "OAK_PLANKS", 1),
  STICK("minecraft:stick", Set.of(
      "OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS", "JUNGLE_PLANKS", "ACACIA_PLANKS", "DARK_OAK_PLANKS",
      "MANGROVE_PLANKS", "CHERRY_PLANKS", "BAMBOO_PLANKS", "CRIMSON_PLANKS", "WARPED_PLANKS", "PALE_OAK_PLANKS"),
      "STICK", 2);

  private final String key;
  private final Set<String> ingredients;
  private final String output;
  private final int inputsPerBatch;

  CraftRecipe(String key, Set<String> ingredients, String output, int inputsPerBatch) {
    this.key = key;
    this.ingredients = ingredients;
    this.output = output;
    this.inputsPerBatch = inputsPerBatch;
  }

  String key() { return key; }
  Set<String> ingredients() { return ingredients; }
  String output() { return output; }
  int inputsPerBatch() { return inputsPerBatch; }
  int outputPerBatch() { return 4; }

  static CraftRecipe forKey(String key) {
    if (key == null) return null;
    for (CraftRecipe recipe : values()) if (recipe.key.equals(key)) return recipe;
    return null;
  }
}
