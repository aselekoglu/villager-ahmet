package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class TaskRequestTest {
  @Test
  void parsesTurkishWoodAliasAndCanonicalCommonResources() {
    assertEquals(new TaskRequest("any", 8, "odun"), TaskRequest.parse("odun", "8").orElseThrow());
    assertEquals(new TaskRequest("item:RAW_IRON", 5, "raw_iron"),
        TaskRequest.parse("minecraft:raw_iron", "5").orElseThrow());
  }

  @Test
  void resolvesSpecificWoodItemsToTheirSpecies() {
    assertEquals("birch", TaskRequest.parse("birch_log", "2").orElseThrow().preference());
    assertEquals("pale_oak", TaskRequest.parse("stripped_pale_oak_wood", "2").orElseThrow().preference());
    assertEquals("bamboo", TaskRequest.parse("bamboo", "2").orElseThrow().preference());
  }

  @Test
  void rejectsUnsupportedItemsAndOutOfRangeAmounts() {
    assertFalse(TaskRequest.parse("diamond", "1").isPresent());
    assertFalse(TaskRequest.parse("stone", "0").isPresent());
    assertFalse(TaskRequest.parse("coal", "65").isPresent());
    assertFalse(TaskRequest.parse("coal", "many").isPresent());
  }

  @Test
  void parseCraftAcceptsAllowedRecipesAtValidMultiplesOfFour() {
    var oak1 = TaskRequest.parseCraft("oak_planks", "4").orElseThrow();
    assertEquals(CraftRecipe.OAK_PLANKS, oak1.recipe());
    assertEquals(4, oak1.amount());
    assertEquals("oak_planks", oak1.itemName());

    var oak2 = TaskRequest.parseCraft("minecraft:oak_planks", "8").orElseThrow();
    assertEquals(CraftRecipe.OAK_PLANKS, oak2.recipe());
    assertEquals(8, oak2.amount());
    assertEquals("oak_planks", oak2.itemName());

    var oak3 = TaskRequest.parseCraft("meşe_tahtası", "16").orElseThrow();
    assertEquals(CraftRecipe.OAK_PLANKS, oak3.recipe());
    assertEquals(16, oak3.amount());
    assertEquals("oak_planks", oak3.itemName());

    var oak4 = TaskRequest.parseCraft("mese_tahtasi", "64").orElseThrow();
    assertEquals(CraftRecipe.OAK_PLANKS, oak4.recipe());
    assertEquals(64, oak4.amount());

    var stick1 = TaskRequest.parseCraft("stick", "4").orElseThrow();
    assertEquals(CraftRecipe.STICK, stick1.recipe());
    assertEquals(4, stick1.amount());
    assertEquals("stick", stick1.itemName());

    var stick2 = TaskRequest.parseCraft("minecraft:stick", "32").orElseThrow();
    assertEquals(CraftRecipe.STICK, stick2.recipe());
    assertEquals(32, stick2.amount());

    var stick3 = TaskRequest.parseCraft("çubuk", "64").orElseThrow();
    assertEquals(CraftRecipe.STICK, stick3.recipe());
    assertEquals(64, stick3.amount());

    var stick4 = TaskRequest.parseCraft("cubuk", "12").orElseThrow();
    assertEquals(CraftRecipe.STICK, stick4.recipe());
    assertEquals(12, stick4.amount());
  }

  @Test
  void parseCraftRejectsInvalidAmountsNonMultiplesAndUnsupportedRecipes() {
    // Non-multiples of 4
    assertFalse(TaskRequest.parseCraft("oak_planks", "1").isPresent());
    assertFalse(TaskRequest.parseCraft("oak_planks", "2").isPresent());
    assertFalse(TaskRequest.parseCraft("oak_planks", "3").isPresent());
    assertFalse(TaskRequest.parseCraft("oak_planks", "5").isPresent());
    assertFalse(TaskRequest.parseCraft("oak_planks", "6").isPresent());

    // Out of range (< 4 or > 64)
    assertFalse(TaskRequest.parseCraft("oak_planks", "0").isPresent());
    assertFalse(TaskRequest.parseCraft("oak_planks", "-4").isPresent());
    assertFalse(TaskRequest.parseCraft("oak_planks", "68").isPresent());
    assertFalse(TaskRequest.parseCraft("stick", "100").isPresent());

    // Non-numeric or null
    assertFalse(TaskRequest.parseCraft("oak_planks", "four").isPresent());
    assertFalse(TaskRequest.parseCraft(null, "4").isPresent());
    assertFalse(TaskRequest.parseCraft("oak_planks", null).isPresent());

    // Unsupported recipes
    assertFalse(TaskRequest.parseCraft("spruce_planks", "4").isPresent());
    assertFalse(TaskRequest.parseCraft("minecraft:spruce_planks", "4").isPresent());
    assertFalse(TaskRequest.parseCraft("diamond", "4").isPresent());
    assertFalse(TaskRequest.parseCraft("crafting_table", "4").isPresent());
  }

  @Test
  void parseFarmAcceptsAllowedCropsAndValidAmounts() {
    var any1 = TaskRequest.parseFarm("ekin", "8").orElseThrow();
    assertEquals("crop:ANY", any1.preference());
    assertEquals(8, any1.amount());
    assertEquals("ekin", any1.itemName());

    var any2 = TaskRequest.parseFarm("hasat", "1").orElseThrow();
    assertEquals("crop:ANY", any2.preference());
    assertEquals(1, any2.amount());
    assertEquals("ekin", any2.itemName());

    var wheat1 = TaskRequest.parseFarm("buğday", "16").orElseThrow();
    assertEquals("crop:WHEAT", wheat1.preference());
    assertEquals(16, wheat1.amount());
    assertEquals("buğday", wheat1.itemName());

    var wheat2 = TaskRequest.parseFarm("wheat", "64").orElseThrow();
    assertEquals("crop:WHEAT", wheat2.preference());
    assertEquals(64, wheat2.amount());
    assertEquals("buğday", wheat2.itemName());

    var carrot1 = TaskRequest.parseFarm("havuç", "32").orElseThrow();
    assertEquals("crop:CARROT", carrot1.preference());
    assertEquals(32, carrot1.amount());
    assertEquals("havuç", carrot1.itemName());

    var potato1 = TaskRequest.parseFarm("patates", "5").orElseThrow();
    assertEquals("crop:POTATO", potato1.preference());
    assertEquals(5, potato1.amount());
    assertEquals("patates", potato1.itemName());
  }

  @Test
  void parseFarmRejectsInvalidAmountsNonNumericAndUnsupportedCrops() {
    // Out of range (must be 1..64)
    assertFalse(TaskRequest.parseFarm("buğday", "0").isPresent());
    assertFalse(TaskRequest.parseFarm("buğday", "-1").isPresent());
    assertFalse(TaskRequest.parseFarm("buğday", "65").isPresent());
    assertFalse(TaskRequest.parseFarm("buğday", "100").isPresent());

    // Non-numeric or null
    assertFalse(TaskRequest.parseFarm("buğday", "five").isPresent());
    assertFalse(TaskRequest.parseFarm(null, "5").isPresent());
    assertFalse(TaskRequest.parseFarm("buğday", null).isPresent());

    // Unsupported crops
    assertFalse(TaskRequest.parseFarm("beetroot", "5").isPresent());
    assertFalse(TaskRequest.parseFarm("pancar", "5").isPresent());
    assertFalse(TaskRequest.parseFarm("melon", "5").isPresent());
    assertFalse(TaskRequest.parseFarm("pumpkin", "5").isPresent());
    assertFalse(TaskRequest.parseFarm("oak_log", "5").isPresent());
    assertFalse(TaskRequest.parseFarm("diamond", "5").isPresent());
  }
}
