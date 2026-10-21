package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FarmCatalogTest {

  @Test
  void cropsHaveCorrectMaterialsAndAges() {
    assertEquals("WHEAT", FarmCatalog.Crop.WHEAT.blockMaterial());
    assertEquals("WHEAT", FarmCatalog.Crop.WHEAT.outputMaterial());
    assertEquals(7, FarmCatalog.Crop.WHEAT.matureAge());
    assertEquals(0, FarmCatalog.Crop.WHEAT.replantAge());
    assertEquals("minecraft:wheat", FarmCatalog.Crop.WHEAT.itemId());

    assertEquals("CARROTS", FarmCatalog.Crop.CARROT.blockMaterial());
    assertEquals("CARROT", FarmCatalog.Crop.CARROT.outputMaterial());
    assertEquals(7, FarmCatalog.Crop.CARROT.matureAge());
    assertEquals(0, FarmCatalog.Crop.CARROT.replantAge());
    assertEquals("minecraft:carrot", FarmCatalog.Crop.CARROT.itemId());

    assertEquals("POTATOES", FarmCatalog.Crop.POTATO.blockMaterial());
    assertEquals("POTATO", FarmCatalog.Crop.POTATO.outputMaterial());
    assertEquals(7, FarmCatalog.Crop.POTATO.matureAge());
    assertEquals(0, FarmCatalog.Crop.POTATO.replantAge());
    assertEquals("minecraft:potato", FarmCatalog.Crop.POTATO.itemId());
  }

  @Test
  void preferenceValidation() {
    assertTrue(FarmCatalog.isPreference("crop:ANY"));
    assertTrue(FarmCatalog.isPreference("crop:WHEAT"));
    assertTrue(FarmCatalog.isPreference("crop:CARROT"));
    assertTrue(FarmCatalog.isPreference("crop:POTATO"));

    assertFalse(FarmCatalog.isPreference(null));
    assertFalse(FarmCatalog.isPreference(""));
    assertFalse(FarmCatalog.isPreference("any"));
    assertFalse(FarmCatalog.isPreference("crop:BEETROOT"));
    assertFalse(FarmCatalog.isPreference("crop:PUMPKIN"));
    assertFalse(FarmCatalog.isPreference("item:OAK_LOG"));
  }

  @Test
  void preferenceForAliasResolution() {
    assertEquals("crop:ANY", FarmCatalog.preferenceForAlias("ekin").orElse(null));
    assertEquals("crop:ANY", FarmCatalog.preferenceForAlias("EKİN").orElse(null));
    assertEquals("crop:ANY", FarmCatalog.preferenceForAlias("ekinler").orElse(null));
    assertEquals("crop:ANY", FarmCatalog.preferenceForAlias("hasat").orElse(null));
    assertEquals("crop:ANY", FarmCatalog.preferenceForAlias("crop").orElse(null));
    assertEquals("crop:ANY", FarmCatalog.preferenceForAlias("crops").orElse(null));
    assertEquals("crop:ANY", FarmCatalog.preferenceForAlias("farm").orElse(null));

    assertEquals("crop:WHEAT", FarmCatalog.preferenceForAlias("buğday").orElse(null));
    assertEquals("crop:WHEAT", FarmCatalog.preferenceForAlias("bugday").orElse(null));
    assertEquals("crop:WHEAT", FarmCatalog.preferenceForAlias("BUĞDAY").orElse(null));
    assertEquals("crop:WHEAT", FarmCatalog.preferenceForAlias("wheat").orElse(null));
    assertEquals("crop:WHEAT", FarmCatalog.preferenceForAlias("minecraft:wheat").orElse(null));

    assertEquals("crop:CARROT", FarmCatalog.preferenceForAlias("havuç").orElse(null));
    assertEquals("crop:CARROT", FarmCatalog.preferenceForAlias("havuc").orElse(null));
    assertEquals("crop:CARROT", FarmCatalog.preferenceForAlias("HAVUÇ").orElse(null));
    assertEquals("crop:CARROT", FarmCatalog.preferenceForAlias("carrot").orElse(null));
    assertEquals("crop:CARROT", FarmCatalog.preferenceForAlias("carrots").orElse(null));
    assertEquals("crop:CARROT", FarmCatalog.preferenceForAlias("minecraft:carrot").orElse(null));

    assertEquals("crop:POTATO", FarmCatalog.preferenceForAlias("patates").orElse(null));
    assertEquals("crop:POTATO", FarmCatalog.preferenceForAlias("PATATES").orElse(null));
    assertEquals("crop:POTATO", FarmCatalog.preferenceForAlias("potato").orElse(null));
    assertEquals("crop:POTATO", FarmCatalog.preferenceForAlias("potatoes").orElse(null));
    assertEquals("crop:POTATO", FarmCatalog.preferenceForAlias("minecraft:potato").orElse(null));

    assertTrue(FarmCatalog.preferenceForAlias(null).isEmpty());
    assertTrue(FarmCatalog.preferenceForAlias("").isEmpty());
    assertTrue(FarmCatalog.preferenceForAlias("   ").isEmpty());
    assertTrue(FarmCatalog.preferenceForAlias("beetroot").isEmpty());
    assertTrue(FarmCatalog.preferenceForAlias("diamond").isEmpty());
    assertTrue(FarmCatalog.preferenceForAlias("oak_log").isEmpty());
  }

  @Test
  void displayItemNames() {
    assertEquals("buğday", FarmCatalog.displayItemName(FarmCatalog.Crop.WHEAT));
    assertEquals("havuç", FarmCatalog.displayItemName(FarmCatalog.Crop.CARROT));
    assertEquals("patates", FarmCatalog.displayItemName(FarmCatalog.Crop.POTATO));
    assertEquals("ekin", FarmCatalog.displayItemName(FarmCatalog.ANY_PREFERENCE));
  }

  @Test
  void materialLookupsAndMatching() {
    assertEquals(FarmCatalog.Crop.WHEAT, FarmCatalog.forBlockMaterial("WHEAT").orElse(null));
    assertEquals(FarmCatalog.Crop.CARROT, FarmCatalog.forBlockMaterial("CARROTS").orElse(null));
    assertEquals(FarmCatalog.Crop.POTATO, FarmCatalog.forBlockMaterial("POTATOES").orElse(null));
    assertTrue(FarmCatalog.forBlockMaterial("CARROT").isEmpty());
    assertTrue(FarmCatalog.forBlockMaterial("POTATO").isEmpty());
    assertTrue(FarmCatalog.forBlockMaterial("AIR").isEmpty());

    assertEquals(FarmCatalog.Crop.WHEAT, FarmCatalog.forOutputMaterial("WHEAT").orElse(null));
    assertEquals(FarmCatalog.Crop.CARROT, FarmCatalog.forOutputMaterial("CARROT").orElse(null));
    assertEquals(FarmCatalog.Crop.POTATO, FarmCatalog.forOutputMaterial("POTATO").orElse(null));
    assertTrue(FarmCatalog.forOutputMaterial("CARROTS").isEmpty());
    assertTrue(FarmCatalog.forOutputMaterial("POTATOES").isEmpty());
    assertTrue(FarmCatalog.forOutputMaterial("AIR").isEmpty());

    assertTrue(FarmCatalog.isCropBlock("WHEAT"));
    assertTrue(FarmCatalog.isCropBlock("CARROTS"));
    assertTrue(FarmCatalog.isCropBlock("POTATOES"));
    assertFalse(FarmCatalog.isCropBlock("CARROT"));
    assertFalse(FarmCatalog.isCropBlock("BEETROOTS"));
    assertFalse(FarmCatalog.isCropBlock(null));

    assertTrue(FarmCatalog.isCropOutput("WHEAT"));
    assertTrue(FarmCatalog.isCropOutput("CARROT"));
    assertTrue(FarmCatalog.isCropOutput("POTATO"));
    assertFalse(FarmCatalog.isCropOutput("CARROTS"));
    assertFalse(FarmCatalog.isCropOutput("BEETROOT"));
    assertFalse(FarmCatalog.isCropOutput(null));

    assertTrue(FarmCatalog.matchesCropBlock("WHEAT", "WHEAT"));
    assertTrue(FarmCatalog.matchesCropBlock("CARROTS", "CARROT"));
    assertTrue(FarmCatalog.matchesCropBlock("POTATOES", "POTATO"));
    assertFalse(FarmCatalog.matchesCropBlock("WHEAT", "CARROT"));
    assertFalse(FarmCatalog.matchesCropBlock("CARROTS", "CARROTS"));
    assertFalse(FarmCatalog.matchesCropBlock(null, "WHEAT"));

    assertEquals("WHEAT", FarmCatalog.outputForBlock("WHEAT"));
    assertEquals("CARROT", FarmCatalog.outputForBlock("CARROTS"));
    assertEquals("POTATO", FarmCatalog.outputForBlock("POTATOES"));
    assertNull(FarmCatalog.outputForBlock("BEETROOTS"));
    assertNull(FarmCatalog.outputForBlock(null));
  }
}
