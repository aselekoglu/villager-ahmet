package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ResourceCatalogTest {
  @Test
  void commonItemIdsMapToSpecificBlocksDropsAndMinimumToolTypes() {
    assertEquals("STONE", ResourceCatalog.forItemId("minecraft:cobblestone").orElseThrow().blockMaterials().get(0));
    assertEquals("WOODEN_PICKAXE", ResourceCatalog.forItemId("minecraft:coal").orElseThrow().toolMaterial());
    assertEquals("STONE_PICKAXE", ResourceCatalog.forItemId("minecraft:raw_iron").orElseThrow().toolMaterial());
    assertEquals("IRON_PICKAXE", ResourceCatalog.forItemId("minecraft:raw_gold").orElseThrow().toolMaterial());
    assertEquals("RAW_IRON", ResourceCatalog.forItemId("minecraft:raw_iron").orElseThrow().outputMaterial());
    assertEquals("DIRT", ResourceCatalog.forItemId("minecraft:dirt").orElseThrow().outputMaterial());
    assertFalse(ResourceCatalog.forItemId("minecraft:dirt").orElseThrow().blockMaterials().contains("COARSE_DIRT"));
  }

  @Test
  void resourcePreferenceNeverThrowsWhenScanningWoodBlocks() {
    for (String item : java.util.List.of("dirt", "sand", "red_sand", "cobblestone", "coal", "raw_iron", "raw_gold")) {
      String preference = ResourceCatalog.preferenceFor("minecraft:" + item).orElseThrow();
      assertFalse(WoodMaterials.matchesPreference(org.bukkit.Material.OAK_LOG, preference), item);
      assertFalse(WoodMaterials.matchesPreference(org.bukkit.Material.BIRCH_WOOD, preference), item);
      assertFalse(WoodMaterials.matchesPreference(org.bukkit.Material.BAMBOO, preference), item);
    }
  }

  @Test
  void catalogItemTokensMatchOnlyTheirMappedBlocks() {
    String coal = ResourceCatalog.preferenceFor("minecraft:coal").orElseThrow();
    assertTrue(ResourceCatalog.matchesBlockName("COAL_ORE", coal));
    assertTrue(ResourceCatalog.matchesBlockName("DEEPSLATE_COAL_ORE", coal));
    assertFalse(ResourceCatalog.matchesBlockName("IRON_ORE", coal));
    assertFalse(ResourceCatalog.preferenceFor("minecraft:diamond").isPresent());
  }
}
