package dev.ahmet;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Small, explicit capability registry; most catalog items intentionally have no gather action. */
public final class ResourceCatalog {
  private static final Map<String, Resource> BY_ITEM = Map.of(
      "dirt", new Resource("minecraft:dirt", "DIRT", List.of("DIRT", "GRASS_BLOCK"), null),
      "sand", new Resource("minecraft:sand", "SAND", List.of("SAND"), null),
      "red_sand", new Resource("minecraft:red_sand", "RED_SAND", List.of("RED_SAND"), null),
      "cobblestone", new Resource("minecraft:cobblestone", "COBBLESTONE", List.of("STONE"), "WOODEN_PICKAXE"),
      "coal", new Resource("minecraft:coal", "COAL", List.of("COAL_ORE", "DEEPSLATE_COAL_ORE"), "WOODEN_PICKAXE"),
      "raw_iron", new Resource("minecraft:raw_iron", "RAW_IRON", List.of("IRON_ORE", "DEEPSLATE_IRON_ORE"), "STONE_PICKAXE"),
      "raw_gold", new Resource("minecraft:raw_gold", "RAW_GOLD", List.of("GOLD_ORE", "DEEPSLATE_GOLD_ORE"), "IRON_PICKAXE"));

  private ResourceCatalog() {}

  static Optional<Resource> forItemId(String id) {
    String name = MaterialCatalog.normalizeId(id);
    return Optional.ofNullable(name == null ? null : BY_ITEM.get(name));
  }

  static Optional<String> preferenceFor(String id) {
    return forItemId(id).map(resource -> "item:" + resource.outputMaterial());
  }

  static boolean isPreference(String preference) {
    return preference != null && preference.startsWith("item:")
        && BY_ITEM.values().stream().anyMatch(resource -> preference.equals("item:" + resource.outputMaterial()));
  }

  static Optional<Resource> forPreference(String preference) {
    if (!isPreference(preference)) return Optional.empty();
    return BY_ITEM.values().stream().filter(resource -> preference.equals("item:" + resource.outputMaterial())).findFirst();
  }

  static boolean matchesBlockName(String blockMaterial, String preference) {
    if (!isPreference(preference)) return false;
    return BY_ITEM.values().stream().anyMatch(resource -> preference.equals("item:" + resource.outputMaterial())
        && resource.blockMaterials().contains(blockMaterial));
  }

  /** Matches a supported gather preference using the same wood/resource rules as task planning. */
  public static boolean matchesGatherTarget(String blockMaterial, String preference) {
    if (blockMaterial == null || preference == null || preference.isBlank()) return false;
    if (FarmCatalog.isPreference(preference)) return FarmCatalog.matchesCropBlock(blockMaterial, preference);
    if (isPreference(preference)) return matchesBlockName(blockMaterial, preference);
    if (WoodMaterials.isPreference(preference)) {
      org.bukkit.Material woodMaterial = org.bukkit.Material.matchMaterial(blockMaterial);
      return WoodMaterials.matchesPreference(woodMaterial, preference);
    }
    org.bukkit.Material material = org.bukkit.Material.matchMaterial(blockMaterial);
    if (material != null && WoodMaterials.matchesPreference(material, preference)) return true;
    return blockMaterial.equalsIgnoreCase(preference) && WoodMaterials.isAllowedName(blockMaterial);
  }

  public static String outputMaterial(String preference) {
    if (!isPreference(preference)) return null;
    return BY_ITEM.values().stream().filter(resource -> preference.equals("item:" + resource.outputMaterial()))
        .map(Resource::outputMaterial).findFirst().orElse(null);
  }

  static boolean isCargoName(String material) {
    if (material == null) return false;
    String name = material.toUpperCase(Locale.ROOT);
    return BY_ITEM.values().stream().anyMatch(resource -> resource.outputMaterial().equals(name));
  }

  static boolean isBlockMaterial(String material) {
    return material != null && BY_ITEM.values().stream().anyMatch(resource -> resource.blockMaterials().contains(material));
  }

  static boolean isSupportedOutput(String material) {
    return isCargoName(material);
  }

  record Resource(String itemId, String outputMaterial, List<String> blockMaterials, String toolMaterial) {
    Resource { blockMaterials = List.copyOf(blockMaterials); }
  }
}
