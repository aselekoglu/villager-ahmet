package dev.ahmet;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class IncrementalTargetScannerTest {
  @Test void scanHonorsBudgetAndDistinguishesPendingFromCompleteResults() {
    var scan = new IncrementalTargetScanner();
    scan.begin("world", "any", 7, 0, 64, 0, 1, -1, 1);
    assertEquals(1, scan.advance(1).size());
    assertFalse(scan.complete());
    assertNull(scan.resultsIfComplete());
    int count = 0;
    while (!scan.complete()) count += scan.advance(3).size();
    assertTrue(count > 1);
    assertEquals(15, scan.resultsIfComplete().size());
  }

  @Test void generationOrPreferenceChangeStartsANewCursor() {
    var scan = new IncrementalTargetScanner();
    scan.begin("world", "any", 1, 0, 64, 0, 1, -1, 1);
    scan.advance(2);
    scan.begin("world", "item:DIRT", 2, 0, 64, 0, 1, -1, 1);
    assertEquals(0, scan.visited());
    assertEquals(1, scan.advance(1).size());
  }
}
