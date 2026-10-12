package dev.ahmet;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

final class WoodMaterials {
  private static final Map<String, String> PREFIX = Map.ofEntries(
      Map.entry("oak", "OAK_"), Map.entry("spruce", "SPRUCE_"), Map.entry("birch", "BIRCH_"),
      Map.entry("jungle", "JUNGLE_"), Map.entry("acacia", "ACACIA_"),
      Map.entry("dark_oak", "DARK_OAK_"), Map.entry("mangrove", "MANGROVE_"),
      Map.entry("cherry", "CHERRY_"), Map.entry("pale_oak", "PALE_OAK_"),
      Map.entry("crimson", "CRIMSON_"), Map.entry("warped", "WARPED_"),
      Map.entry("bamboo", "BAMBOO"));
  private static final Set<String> LOG_SUFFIXES = Set.of("LOG", "WOOD", "STEM", "HYPHAE");

  private WoodMaterials() {}

  static boolean isPreference(String preference) {
    return "any".equals(preference) || PREFIX.containsKey(preference);
  }

  static boolean isAllowedName(String name) {
    if (name == null) return false;
    String normalized = name.toUpperCase(java.util.Locale.ROOT);
    if ("BAMBOO".equals(normalized)) return true;
    String base = normalized.startsWith("STRIPPED_") ? normalized.substring("STRIPPED_".length()) : normalized;
    return PREFIX.values().stream().anyMatch(prefix -> base.startsWith(prefix)
        && LOG_SUFFIXES.stream().anyMatch(suffix -> base.endsWith("_" + suffix)));
  }

  static boolean isCollectableWood(Material material) {
    return material != null && (Tag.LOGS.isTagged(material) || material == Material.BAMBOO);
  }

  static boolean matchesPreference(Material material, String preference) {
    if (!isCollectableWood(material) || !isPreference(preference)) return false;
    if ("any".equals(preference)) return true;
    if ("bamboo".equals(preference)) return material == Material.BAMBOO;
    if (material == Material.BAMBOO) return false;
    String name = material.name().startsWith("STRIPPED_")
        ? material.name().substring("STRIPPED_".length()) : material.name();
    return name.startsWith(PREFIX.get(preference));
  }
}
