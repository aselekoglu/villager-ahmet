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
