package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class LocalEconomyLedgerTest {

  @TempDir
  Path tempDir;

  @Test
  void recordDepositUpdatesTotalsAndVillagerSummary() throws IOException {
    Path file = tempDir.resolve("economy-ledger.json");
    LocalEconomyLedger ledger = new LocalEconomyLedger(file);
    ledger.load();

    assertTrue(ledger.recordDeposit("tx1", "ahmet", VillagerRole.WORKER, "task1", WoodTask.Kind.GATHER, "OAK_LOG", 4));
    // Idempotent duplicate check
    assertFalse(ledger.recordDeposit("tx1", "ahmet", VillagerRole.WORKER, "task1", WoodTask.Kind.GATHER, "OAK_LOG", 4));

    // Farm deposit
    assertTrue(ledger.recordDeposit("tx2", "mehmet", VillagerRole.FARMER, "task2", WoodTask.Kind.FARM, "WHEAT", 6));

    // Ahmet
    LocalEconomyLedger.VillagerSummary sAhmet = ledger.villagerSummary("ahmet");
    assertEquals(4, sAhmet.credits());
    assertEquals(4, sAhmet.gatheredItems());
    assertEquals(0, sAhmet.farmedItems());
    assertEquals(0, sAhmet.craftedItems());
    assertEquals(1, sAhmet.tasksContributed());
    assertEquals(4, ledger.villagerCredits("ahmet"));

    // Mehmet
    LocalEconomyLedger.VillagerSummary sMehmet = ledger.villagerSummary("mehmet");
    assertEquals(6, sMehmet.credits());
    assertEquals(0, sMehmet.gatheredItems());
    assertEquals(6, sMehmet.farmedItems());
    assertEquals(0, sMehmet.craftedItems());
    assertEquals(1, sMehmet.tasksContributed());

    // Produced & Net balances
    assertEquals(Map.of("OAK_LOG", 4, "WHEAT", 6), ledger.producedTotals());
    assertEquals(Map.of("OAK_LOG", 4, "WHEAT", 6), ledger.netBalances());
    assertEquals(Map.of(), ledger.consumedTotals());

    // Reload from file to verify persistence
    LocalEconomyLedger loaded = new LocalEconomyLedger(file);
    loaded.load();
    assertEquals(4, loaded.villagerCredits("ahmet"));
    assertEquals(6, loaded.villagerCredits("mehmet"));
    assertEquals(2, loaded.recentEntries().size());
  }

  @Test
  void recordCraftUpdatesNetBalancesAndTotals() throws IOException {
    Path file = tempDir.resolve("economy-ledger-craft.json");
    LocalEconomyLedger ledger = new LocalEconomyLedger(file);
    ledger.load();

    // Produce oak log first
    ledger.recordDeposit("txLog", "ahmet", VillagerRole.WORKER, "task1", WoodTask.Kind.GATHER, "OAK_LOG", 2);

    // Craft planks
    assertTrue(ledger.recordCraft("txCraft1", "ayse", VillagerRole.CRAFTER, "task2", "OAK_LOG", 2, "OAK_PLANKS", 8));
    // Duplicate txCraft1
    assertFalse(ledger.recordCraft("txCraft1", "ayse", VillagerRole.CRAFTER, "task2", "OAK_LOG", 2, "OAK_PLANKS", 8));

    // Ayse summary
    LocalEconomyLedger.VillagerSummary sAyse = ledger.villagerSummary("ayse");
    assertEquals(8, sAyse.credits());
    assertEquals(8, sAyse.craftedItems());
    assertEquals(1, sAyse.tasksContributed());

    // Totals
    assertEquals(Map.of("OAK_LOG", 2), ledger.consumedTotals());
    assertEquals(Map.of("OAK_LOG", 2, "OAK_PLANKS", 8), ledger.producedTotals());
    // Net: OAK_LOG: 2 produced - 2 consumed = 0; OAK_PLANKS: 8 produced = 8
    assertEquals(Map.of("OAK_LOG", 0, "OAK_PLANKS", 8), ledger.netBalances());

    // Verify describeSummary output
    String summary = ledger.describeSummary();
    assertTrue(summary.contains("Köy Ekonomisi"));
    assertTrue(summary.contains("Üretilen: 10"));
    assertTrue(summary.contains("Tüketilen: 2"));
  }

  @Test
  void boundsAndEvictionOfRecentEntriesAndAppliedKeys() throws IOException {
    Path file = tempDir.resolve("economy-ledger-bounds.json");
    LocalEconomyLedger ledger = new LocalEconomyLedger(file);
    ledger.load();

    for (int i = 0; i < 70; i++) {
      ledger.recordDeposit("key_" + i, "ahmet", VillagerRole.WORKER, "task_" + i, WoodTask.Kind.GATHER, "OAK_LOG", 1);
    }

    // Recent entries capped at MAX_RECENT_ENTRIES (64)
    assertEquals(64, ledger.recentEntries().size());
    // Most recent is key_69
    assertEquals("key_69", ledger.recentEntries().get(63).key());
  }

  @Test
  void corruptLedgerFailsClosed() throws IOException {
    Path file = tempDir.resolve("corrupt-ledger.json");
    Files.writeString(file, "{\"version\": 2, \"corrupt\": true}");

    LocalEconomyLedger ledger = new LocalEconomyLedger(file);
    assertThrows(IllegalStateException.class, ledger::load);
  }

  @Test
  void failedLoadOnCorruptFileDoesNotModifyFileBytes() throws IOException {
    Path file = tempDir.resolve("corrupt-ledger-preserve.json");
    byte[] corruptBytes = "{\"version\": 2, \"corrupt\": true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(file, corruptBytes);

    LocalEconomyLedger ledger = new LocalEconomyLedger(file);
    assertThrows(IllegalStateException.class, ledger::load);

    byte[] actualBytes = Files.readAllBytes(file);
    org.junit.jupiter.api.Assertions.assertArrayEquals(corruptBytes, actualBytes);
  }

  @Test
  void invalidInputArgumentsThrow() {
    Path file = tempDir.resolve("economy-ledger-invalid.json");
    LocalEconomyLedger ledger = new LocalEconomyLedger(file);

    assertThrows(IllegalArgumentException.class, () ->
        ledger.recordDeposit("", "ahmet", VillagerRole.WORKER, "task1", WoodTask.Kind.GATHER, "OAK_LOG", 1));
    assertThrows(IllegalArgumentException.class, () ->
        ledger.recordDeposit("tx", "", VillagerRole.WORKER, "task1", WoodTask.Kind.GATHER, "OAK_LOG", 1));
    assertThrows(IllegalArgumentException.class, () ->
        ledger.recordDeposit("tx", "ahmet", null, "task1", WoodTask.Kind.GATHER, "OAK_LOG", 1));
    assertThrows(IllegalArgumentException.class, () ->
        ledger.recordDeposit("tx", "ahmet", VillagerRole.WORKER, "", WoodTask.Kind.GATHER, "OAK_LOG", 1));
    assertThrows(IllegalArgumentException.class, () ->
        ledger.recordDeposit("tx", "ahmet", VillagerRole.WORKER, "task1", WoodTask.Kind.CRAFT, "OAK_LOG", 1));
    assertThrows(IllegalArgumentException.class, () ->
        ledger.recordDeposit("tx", "ahmet", VillagerRole.WORKER, "task1", WoodTask.Kind.GATHER, "OAK_LOG", 0));
  }
}
