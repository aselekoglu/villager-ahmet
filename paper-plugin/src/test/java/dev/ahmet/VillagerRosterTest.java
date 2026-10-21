package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

final class VillagerRosterTest {

  @Test
  void validRosterPasses() {
    Set<String> secondaries = Set.of("mehmet", "ayse", "fatma");
    Map<String, Integer> npcIds = Map.of(
        "ahmet", 1,
        "mehmet", 2,
        "ayse", 3,
        "fatma", 4
    );
    VillagerRoster.ValidationResult res = VillagerRoster.validate(secondaries, npcIds);
    assertTrue(res.valid(), res.reason());
  }

  @Test
  void invalidSecondaryIdFails() {
    // Uppercase characters, special symbols, single letter, or primary id
    Set<String> invalidIds = Set.of("Mehmet", "123", "a", "mehmet!", "ahmet");
    for (String id : invalidIds) {
      VillagerRoster.ValidationResult res = VillagerRoster.validate(Set.of(id), Map.of());
      assertFalse(res.valid(), "Expected invalid for id: " + id);
      assertTrue(res.reason().contains(id));
    }
  }

  @Test
  void fiveVillagersExceedsMaxLimit() {
    // 4 secondaries + 1 primary = 5 villagers > MAX_VILLAGERS (4)
    Set<String> secondaries = Set.of("mehmet", "ayse", "fatma", "ali");
    VillagerRoster.ValidationResult res = VillagerRoster.validate(secondaries, Map.of());
    assertFalse(res.valid());
    assertTrue(res.reason().contains("exceeds limit"));
  }

  @Test
  void duplicateNpcIdBetweenPrimaryAndSecondaryFails() {
    Set<String> secondaries = Set.of("mehmet");
    Map<String, Integer> npcIds = Map.of(
        "ahmet", 42,
        "mehmet", 42
    );
    VillagerRoster.ValidationResult res = VillagerRoster.validate(secondaries, npcIds);
    assertFalse(res.valid());
    assertTrue(res.reason().contains("42"));
    assertTrue(res.reason().contains("Duplicate Citizens NPC ID"));
  }

  @Test
  void duplicateNpcIdBetweenTwoSecondariesFails() {
    Set<String> secondaries = Set.of("mehmet", "ayse");
    Map<String, Integer> npcIds = Map.of(
        "ahmet", 1,
        "mehmet", 99,
        "ayse", 99
    );
    VillagerRoster.ValidationResult res = VillagerRoster.validate(secondaries, npcIds);
    assertFalse(res.valid());
    assertTrue(res.reason().contains("99"));
    assertTrue(res.reason().contains("Duplicate Citizens NPC ID"));
  }

  @Test
  void negativeOneNpcIdsNeverCountAsDuplicates() {
    Set<String> secondaries = Set.of("mehmet", "ayse", "fatma");
    Map<String, Integer> npcIds = Map.of(
        "ahmet", -1,
        "mehmet", -1,
        "ayse", -1,
        "fatma", -1
    );
    VillagerRoster.ValidationResult res = VillagerRoster.validate(secondaries, npcIds);
    assertTrue(res.valid(), res.reason());
  }
}
