package dev.ahmet.protection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class LandClaimTest {

  private ClaimBoundary sampleBoundary() {
    return new ClaimBoundary("world", 0, 60, 0, 50, 80, 50);
  }

  @Test
  void testIdValidation() {
    ClaimBoundary b = sampleBoundary();

    // Valid IDs
    assertDoesNotThrow(() -> new LandClaim("farm_1", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));
    assertDoesNotThrow(() -> new LandClaim("village-north_area", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));
    assertDoesNotThrow(() -> new LandClaim("A1-B2_C3", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));

    // Invalid IDs
    assertThrows(IllegalArgumentException.class, () -> new LandClaim(null, b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));
    assertThrows(IllegalArgumentException.class, () -> new LandClaim("", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));
    assertThrows(IllegalArgumentException.class, () -> new LandClaim("claim with space", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));
    assertThrows(IllegalArgumentException.class, () -> new LandClaim("claim@invalid!", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));
    assertThrows(IllegalArgumentException.class, () -> new LandClaim("this_id_is_way_too_long_and_exceeds_thirty_two_chars", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), true));
  }

  @Test
  void testPermitsWildcardVillagerAndSpecificVillager() {
    ClaimBoundary b = sampleBoundary();

    // Wildcard villager
    LandClaim wildcardClaim = new LandClaim("wildcard", b, "steve", Set.of("*"), Set.of(ClaimAction.GATHER_WOOD), true);
    assertTrue(wildcardClaim.permits("ahmet", ClaimAction.GATHER_WOOD));
    assertTrue(wildcardClaim.permits("mehmet", ClaimAction.GATHER_WOOD));
    assertTrue(wildcardClaim.permits("any_new_villager", ClaimAction.GATHER_WOOD));
    assertFalse(wildcardClaim.permits("ahmet", ClaimAction.HARVEST_CROPS));

    // Specific villager (case insensitive)
    LandClaim specificClaim = new LandClaim("specific", b, "steve", Set.of("Ahmet", "Ayse"), Set.of(ClaimAction.HARVEST_CROPS), true);
    assertTrue(specificClaim.permits("ahmet", ClaimAction.HARVEST_CROPS));
    assertTrue(specificClaim.permits("AHMET", ClaimAction.HARVEST_CROPS));
    assertTrue(specificClaim.permits("ayse", ClaimAction.HARVEST_CROPS));
    assertFalse(specificClaim.permits("mehmet", ClaimAction.HARVEST_CROPS));
  }

  @Test
  void testPermitsAllActionsAndSpecificActions() {
    ClaimBoundary b = sampleBoundary();

    // All actions
    LandClaim allActionsClaim = new LandClaim("all_actions", b, "operator", Set.of("ahmet"), Set.of(ClaimAction.ALL), true);
    assertTrue(allActionsClaim.permits("ahmet", ClaimAction.GATHER_WOOD));
    assertTrue(allActionsClaim.permits("ahmet", ClaimAction.HARVEST_CROPS));
    assertTrue(allActionsClaim.permits("ahmet", ClaimAction.CLEAR_OBSTRUCTION));
    assertTrue(allActionsClaim.permits("ahmet", ClaimAction.OPEN_CHEST));

    // Specific actions
    LandClaim woodChestClaim = new LandClaim("wood_chest", b, "operator", Set.of("ahmet"), Set.of(ClaimAction.GATHER_WOOD, ClaimAction.OPEN_CHEST), true);
    assertTrue(woodChestClaim.permits("ahmet", ClaimAction.GATHER_WOOD));
    assertTrue(woodChestClaim.permits("ahmet", ClaimAction.OPEN_CHEST));
    assertFalse(woodChestClaim.permits("ahmet", ClaimAction.HARVEST_CROPS));
    assertFalse(woodChestClaim.permits("ahmet", ClaimAction.CLEAR_OBSTRUCTION));
  }

  @Test
  void testDisabledClaimPermitsNothing() {
    ClaimBoundary b = sampleBoundary();
    LandClaim disabledClaim = new LandClaim("disabled", b, "operator", Set.of("*"), Set.of(ClaimAction.ALL), false);

    assertFalse(disabledClaim.permits("ahmet", ClaimAction.GATHER_WOOD));
    assertFalse(disabledClaim.permits("ahmet", ClaimAction.HARVEST_CROPS));
    assertFalse(disabledClaim.permits("operator", ClaimAction.ALL));
  }

  @Test
  void testJsonRoundTrip() {
    ClaimBoundary boundary = new ClaimBoundary("world_the_end", -100, 0, -200, 100, 255, 200);
    LandClaim original = new LandClaim(
        "end_base_1",
        boundary,
        "uuid-1234-abcd",
        Set.of("ahmet", "mehmet"),
        Set.of(ClaimAction.OPEN_CHEST, ClaimAction.CLEAR_OBSTRUCTION),
        true
    );

    JsonObject json = original.toJson();
    LandClaim parsed = LandClaim.fromJson(json);

    assertEquals(original.id(), parsed.id());
    assertEquals(original.owner(), parsed.owner());
    assertEquals(original.enabled(), parsed.enabled());
    assertEquals(original.boundary(), parsed.boundary());
    assertEquals(original.allowedVillagers(), parsed.allowedVillagers());
    assertEquals(original.allowedActions(), parsed.allowedActions());
  }

  @Test
  void testStrictJsonParsingValidation() {
    // Missing id
    JsonObject noId = JsonParser.parseString("""
        {
          "owner": "operator",
          "enabled": true,
          "boundary": {"world": "world", "minX": 0, "minY": 0, "minZ": 0, "maxX": 10, "maxY": 10, "maxZ": 10},
          "allowedVillagers": ["*"],
          "allowedActions": ["ALL"]
        }
        """).getAsJsonObject();
    assertThrows(IllegalStateException.class, () -> LandClaim.fromJson(noId));

    // Corrupt boundary (missing maxY)
    JsonObject badBoundary = JsonParser.parseString("""
        {
          "id": "claim_1",
          "owner": "operator",
          "enabled": true,
          "boundary": {"world": "world", "minX": 0, "minY": 0, "minZ": 0, "maxX": 10, "maxZ": 10},
          "allowedVillagers": ["*"],
          "allowedActions": ["ALL"]
        }
        """).getAsJsonObject();
    assertThrows(IllegalStateException.class, () -> LandClaim.fromJson(badBoundary));

    // Invalid enum value
    JsonObject badAction = JsonParser.parseString("""
        {
          "id": "claim_1",
          "owner": "operator",
          "enabled": true,
          "boundary": {"world": "world", "minX": 0, "minY": 0, "minZ": 0, "maxX": 10, "maxY": 10, "maxZ": 10},
          "allowedVillagers": ["*"],
          "allowedActions": ["NON_EXISTENT_ACTION"]
        }
        """).getAsJsonObject();
    assertThrows(IllegalStateException.class, () -> LandClaim.fromJson(badAction));
  }
}
