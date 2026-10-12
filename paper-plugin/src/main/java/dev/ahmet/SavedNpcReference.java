package dev.ahmet;

/** Keeps the persisted Citizens id until the registry finishes its delayed startup load. */
final class SavedNpcReference {
  static final int MAX_LOOKUPS = 100;
  private int savedId;
  private int failedLookups;
  private boolean pending;

  SavedNpcReference(int savedId) {
    if (savedId < -1) throw new IllegalArgumentException("Invalid saved NPC id");
    this.savedId = savedId;
    pending = savedId >= 0;
  }

  boolean pending() {
    return pending;
  }

  int savedId() {
    return savedId;
  }

  void recordLookup(Integer foundId) {
    if (!pending) return;
    if (foundId != null && foundId >= 0) {
      savedId = foundId;
      pending = false;
    } else if (++failedLookups >= MAX_LOOKUPS) {
      pending = false;
    }
  }

  void created(int id) {
    if (id < 0) throw new IllegalArgumentException("Invalid created NPC id");
    savedId = id;
    failedLookups = 0;
    pending = false;
  }

  int idForPersistence(Integer currentId) {
    return currentId == null ? savedId : currentId;
  }
}
