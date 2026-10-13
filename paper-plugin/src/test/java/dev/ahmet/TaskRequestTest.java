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
}
