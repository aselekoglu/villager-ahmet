package dev.ahmet.protection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents an operator-defined claim with permission boundaries, allowed villagers, and actions.
 */
public record LandClaim(
    String id,
    ClaimBoundary boundary,
    String owner,
    Set<String> allowedVillagers,
    Set<ClaimAction> allowedActions,
    boolean enabled
) {

  public LandClaim {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Claim id is required");
    }
    if (id.length() > 32) {
      throw new IllegalArgumentException("Claim id exceeds max length of 32 characters: " + id);
    }
    if (!id.matches("^[a-zA-Z0-9_-]+$")) {
      throw new IllegalArgumentException("Claim id must be alphanumeric, dash, or underscore: " + id);
    }
    Objects.requireNonNull(boundary, "boundary must not be null");
    if (owner == null || owner.isBlank()) {
      throw new IllegalArgumentException("Claim owner must not be null or blank");
    }
    if (allowedVillagers == null) {
      throw new IllegalArgumentException("allowedVillagers must not be null");
    }
    if (allowedActions == null) {
      throw new IllegalArgumentException("allowedActions must not be null");
    }

    allowedVillagers = allowedVillagers.stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(String::toLowerCase)
        .collect(Collectors.toUnmodifiableSet());
    allowedActions = Set.copyOf(allowedActions);
  }

  /**
   * Evaluates if the given villager and action are permitted in this claim.
   *
   * @param villagerId candidate villager id (e.g. "ahmet", "mehmet")
   * @param action candidate action
   * @return true if permitted, false otherwise
   */
  public boolean permits(String villagerId, ClaimAction action) {
    if (!enabled) {
      return false;
    }
    boolean villagerPermitted = allowedVillagers.contains("*")
        || (villagerId != null && allowedVillagers.contains(villagerId.trim().toLowerCase()));
    boolean actionPermitted = allowedActions.contains(ClaimAction.ALL)
        || (action != null && (allowedActions.contains(action) || allowedActions.stream().anyMatch(a -> a.matches(action))));
    return villagerPermitted && actionPermitted;
  }

  /**
   * Serializes this claim to a JsonObject.
   */
  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", id);
    obj.addProperty("owner", owner);
    obj.addProperty("enabled", enabled);

    JsonObject bObj = new JsonObject();
    bObj.addProperty("world", boundary.world());
    bObj.addProperty("minX", boundary.minX());
    bObj.addProperty("minY", boundary.minY());
    bObj.addProperty("minZ", boundary.minZ());
    bObj.addProperty("maxX", boundary.maxX());
    bObj.addProperty("maxY", boundary.maxY());
    bObj.addProperty("maxZ", boundary.maxZ());
    obj.add("boundary", bObj);

    JsonArray vArr = new JsonArray();
    for (String v : allowedVillagers) {
      vArr.add(v);
    }
    obj.add("allowedVillagers", vArr);

    JsonArray aArr = new JsonArray();
    for (ClaimAction a : allowedActions) {
      aArr.add(a.name());
    }
    obj.add("allowedActions", aArr);

    return obj;
  }

  /**
   * Deserializes a LandClaim from a JsonObject with strict validation.
   *
   * @throws IllegalStateException if fields are missing, invalid, or corrupt
   */
  public static LandClaim fromJson(JsonObject obj) {
    if (obj == null) {
      throw new IllegalStateException("Claim JSON object cannot be null");
    }
    if (!obj.has("id") || !obj.get("id").isJsonPrimitive()) {
      throw new IllegalStateException("Claim JSON missing 'id'");
    }
    String id = obj.get("id").getAsString();

    if (!obj.has("owner") || !obj.get("owner").isJsonPrimitive()) {
      throw new IllegalStateException("Claim JSON missing 'owner'");
    }
    String owner = obj.get("owner").getAsString();

    boolean enabled = true;
    if (obj.has("enabled")) {
      if (!obj.get("enabled").isJsonPrimitive()) {
        throw new IllegalStateException("Claim JSON 'enabled' must be boolean primitive");
      }
      enabled = obj.get("enabled").getAsBoolean();
    }

    if (!obj.has("boundary") || !obj.get("boundary").isJsonObject()) {
      throw new IllegalStateException("Claim JSON missing 'boundary' object");
    }
    JsonObject bObj = obj.getAsJsonObject("boundary");
    if (!bObj.has("world") || !bObj.get("world").isJsonPrimitive()) {
      throw new IllegalStateException("Claim boundary missing 'world'");
    }
    String world = bObj.get("world").getAsString();
    int minX = requireInt(bObj, "minX");
    int minY = requireInt(bObj, "minY");
    int minZ = requireInt(bObj, "minZ");
    int maxX = requireInt(bObj, "maxX");
    int maxY = requireInt(bObj, "maxY");
    int maxZ = requireInt(bObj, "maxZ");

    ClaimBoundary boundary;
    try {
      boundary = new ClaimBoundary(world, minX, minY, minZ, maxX, maxY, maxZ);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid claim boundary: " + e.getMessage(), e);
    }

    Set<String> allowedVillagers = new HashSet<>();
    if (!obj.has("allowedVillagers") || !obj.get("allowedVillagers").isJsonArray()) {
      throw new IllegalStateException("Claim JSON missing 'allowedVillagers' array");
    }
    for (JsonElement el : obj.getAsJsonArray("allowedVillagers")) {
      if (!el.isJsonPrimitive()) {
        throw new IllegalStateException("allowedVillagers entries must be string primitive");
      }
      allowedVillagers.add(el.getAsString());
    }

    Set<ClaimAction> allowedActions = new HashSet<>();
    if (!obj.has("allowedActions") || !obj.get("allowedActions").isJsonArray()) {
      throw new IllegalStateException("Claim JSON missing 'allowedActions' array");
    }
    for (JsonElement el : obj.getAsJsonArray("allowedActions")) {
      if (!el.isJsonPrimitive()) {
        throw new IllegalStateException("allowedActions entries must be string primitive");
      }
      try {
        allowedActions.add(ClaimAction.valueOf(el.getAsString()));
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("Unknown ClaimAction in claim JSON: " + el.getAsString(), e);
      }
    }

    try {
      return new LandClaim(id, boundary, owner, allowedVillagers, allowedActions, enabled);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid claim specification: " + e.getMessage(), e);
    }
  }

  private static int requireInt(JsonObject obj, String member) {
    if (!obj.has(member) || !obj.get(member).isJsonPrimitive()) {
      throw new IllegalStateException("Claim boundary missing integer '" + member + "'");
    }
    try {
      return obj.get(member).getAsInt();
    } catch (Exception e) {
      throw new IllegalStateException("Invalid integer for '" + member + "': " + e.getMessage(), e);
    }
  }
}
