package dev.ahmet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure domain validation for the multi-villager roster configuration.
 */
public final class VillagerRoster {
  private VillagerRoster() {}

  public record ValidationResult(boolean valid, String reason) {
    public static ValidationResult ok() {
      return new ValidationResult(true, "OK");
    }

    public static ValidationResult fail(String reason) {
      return new ValidationResult(false, reason);
    }
  }

  public static ValidationResult validate(Set<String> secondaryIds, Map<String, Integer> npcIdsById) {
    if (secondaryIds == null) {
      secondaryIds = Set.of();
    }
    if (npcIdsById == null) {
      npcIdsById = Map.of();
    }

    // 1. Total villager count limit (primary + secondaries)
    if (secondaryIds.size() + 1 > AhmetPlugin.MAX_VILLAGERS) {
      return ValidationResult.fail("Villager count exceeds limit of " + AhmetPlugin.MAX_VILLAGERS
          + " (found " + (secondaryIds.size() + 1) + ")");
    }

    // 2. Validate secondary IDs
    for (String id : secondaryIds) {
      if (AhmetPlugin.PRIMARY_VILLAGER_ID.equals(id)) {
        return ValidationResult.fail("Secondary villager id cannot be primary id: " + id);
      }
      if (!VillagerRole.isValidVillagerId(id)) {
        return ValidationResult.fail("Invalid secondary villager id: " + id);
      }
    }

    // 3. Duplicate Citizens NPC IDs (>= 0) check
    Map<Integer, String> seenNpcIds = new HashMap<>();
    for (Map.Entry<String, Integer> entry : npcIdsById.entrySet()) {
      String vid = entry.getKey();
      Integer npcId = entry.getValue();
      if (npcId == null || npcId < 0) {
        continue;
      }
      String existing = seenNpcIds.put(npcId, vid);
      if (existing != null) {
        return ValidationResult.fail("Duplicate Citizens NPC ID detected: " + npcId + " for " + existing + " and " + vid);
      }
    }

    return ValidationResult.ok();
  }
}
