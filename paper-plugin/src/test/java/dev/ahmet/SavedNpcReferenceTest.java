package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SavedNpcReferenceTest {
  @Test
  void preservesTheSavedIdUntilCitizensFinishesLoadingItsRegistry() {
    SavedNpcReference reference = new SavedNpcReference(7);

    assertTrue(reference.pending());
    assertEquals(7, reference.idForPersistence(null));
    reference.recordLookup(null);
    assertTrue(reference.pending());
    reference.recordLookup(7);

    assertFalse(reference.pending());
    assertEquals(7, reference.idForPersistence(null));
    assertEquals(7, reference.idForPersistence(7));
  }

  @Test
  void releasesTheSpawnGateAfterABoundedNumberOfMissingLookups() {
    SavedNpcReference reference = new SavedNpcReference(7);

    for (int i = 0; i < SavedNpcReference.MAX_LOOKUPS - 1; i++) reference.recordLookup(null);
    assertTrue(reference.pending());
    reference.recordLookup(null);

    assertFalse(reference.pending());
    assertEquals(7, reference.idForPersistence(null));
  }

  @Test
  void recordsAReplacementOnlyAfterExplicitCreation() {
    SavedNpcReference reference = new SavedNpcReference(-1);
    assertFalse(reference.pending());

    reference.created(12);

    assertFalse(reference.pending());
    assertEquals(12, reference.idForPersistence(null));
  }
}
