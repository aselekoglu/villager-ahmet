package dev.ahmet.protection;

/**
 * Bounded action types subject to land claim permissions.
 */
public enum ClaimAction {
  GATHER_WOOD,
  HARVEST_CROPS,
  CLEAR_OBSTRUCTION,
  OPEN_CHEST,
  ALL;

  /**
   * Evaluates if this action permits or matches the requested action.
   * {@code ALL} matches any requested action.
   *
   * @param requested the candidate action being requested
   * @return true if permitted, false otherwise
   */
  public boolean matches(ClaimAction requested) {
    if (requested == null) {
      return false;
    }
    return this == ALL || requested == ALL || this == requested;
  }
}
