package dev.ahmet.agent;

import java.util.Objects;
import java.util.regex.Pattern;

public record OfferedAction(
    String id,
    ActionType type,
    String description,
    Object targetData
) {
  private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]{1,32}$");

  public OfferedAction {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(description, "description must not be null");
    if (!VALID_ID.matcher(id).matches()) {
      throw new IllegalArgumentException("Action id must match ^[a-z0-9_]{1,32}$: " + id);
    }
  }

  public static OfferedAction of(String id, ActionType type, String description) {
    return new OfferedAction(id, type, description, null);
  }

  public static OfferedAction of(String id, ActionType type, String description, Object targetData) {
    return new OfferedAction(id, type, description, targetData);
  }
}
