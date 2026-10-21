package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Optional;

final class VillagerRoleTest {

  @Test
  void rolePropertiesAndExecutionPermissions() {
    // ALL_ROUNDER
    assertEquals("genel", VillagerRole.ALL_ROUNDER.labelTr());
    assertFalse(VillagerRole.ALL_ROUNDER.isSpecialist());
    assertTrue(VillagerRole.ALL_ROUNDER.canExecute(WoodTask.Kind.GATHER));
    assertTrue(VillagerRole.ALL_ROUNDER.canExecute(WoodTask.Kind.FARM));
    assertTrue(VillagerRole.ALL_ROUNDER.canExecute(WoodTask.Kind.CRAFT));
    assertFalse(VillagerRole.ALL_ROUNDER.canExecute(null));

    // WORKER
    assertEquals("işçi", VillagerRole.WORKER.labelTr());
    assertTrue(VillagerRole.WORKER.isSpecialist());
    assertTrue(VillagerRole.WORKER.canExecute(WoodTask.Kind.GATHER));
    assertFalse(VillagerRole.WORKER.canExecute(WoodTask.Kind.FARM));
    assertFalse(VillagerRole.WORKER.canExecute(WoodTask.Kind.CRAFT));
    assertFalse(VillagerRole.WORKER.canExecute(null));

    // FARMER
    assertEquals("çiftçi", VillagerRole.FARMER.labelTr());
    assertTrue(VillagerRole.FARMER.isSpecialist());
    assertFalse(VillagerRole.FARMER.canExecute(WoodTask.Kind.GATHER));
    assertTrue(VillagerRole.FARMER.canExecute(WoodTask.Kind.FARM));
    assertFalse(VillagerRole.FARMER.canExecute(WoodTask.Kind.CRAFT));
    assertFalse(VillagerRole.FARMER.canExecute(null));

    // CRAFTER
    assertEquals("usta", VillagerRole.CRAFTER.labelTr());
    assertTrue(VillagerRole.CRAFTER.isSpecialist());
    assertFalse(VillagerRole.CRAFTER.canExecute(WoodTask.Kind.GATHER));
    assertFalse(VillagerRole.CRAFTER.canExecute(WoodTask.Kind.FARM));
    assertTrue(VillagerRole.CRAFTER.canExecute(WoodTask.Kind.CRAFT));
    assertFalse(VillagerRole.CRAFTER.canExecute(null));
  }

  @Test
  void parseAliasesCaseInsensitiveAndTurkish() {
    // ALL_ROUNDER aliases
    assertEquals(Optional.of(VillagerRole.ALL_ROUNDER), VillagerRole.parse("all"));
    assertEquals(Optional.of(VillagerRole.ALL_ROUNDER), VillagerRole.parse("ALL_ROUNDER"));
    assertEquals(Optional.of(VillagerRole.ALL_ROUNDER), VillagerRole.parse("all-rounder"));
    assertEquals(Optional.of(VillagerRole.ALL_ROUNDER), VillagerRole.parse("genel"));
    assertEquals(Optional.of(VillagerRole.ALL_ROUNDER), VillagerRole.parse("  HEPSI  "));

    // WORKER aliases
    assertEquals(Optional.of(VillagerRole.WORKER), VillagerRole.parse("worker"));
    assertEquals(Optional.of(VillagerRole.WORKER), VillagerRole.parse("WORKER"));
    assertEquals(Optional.of(VillagerRole.WORKER), VillagerRole.parse("isci"));
    assertEquals(Optional.of(VillagerRole.WORKER), VillagerRole.parse("işçi"));
    assertEquals(Optional.of(VillagerRole.WORKER), VillagerRole.parse("oduncu"));
    assertEquals(Optional.of(VillagerRole.WORKER), VillagerRole.parse("gatherer"));

    // FARMER aliases
    assertEquals(Optional.of(VillagerRole.FARMER), VillagerRole.parse("farmer"));
    assertEquals(Optional.of(VillagerRole.FARMER), VillagerRole.parse("FARMER"));
    assertEquals(Optional.of(VillagerRole.FARMER), VillagerRole.parse("ciftci"));
    assertEquals(Optional.of(VillagerRole.FARMER), VillagerRole.parse("çiftçi"));
    assertEquals(Optional.of(VillagerRole.FARMER), VillagerRole.parse("tarimci"));
    assertEquals(Optional.of(VillagerRole.FARMER), VillagerRole.parse("tarımcı"));

    // CRAFTER aliases
    assertEquals(Optional.of(VillagerRole.CRAFTER), VillagerRole.parse("crafter"));
    assertEquals(Optional.of(VillagerRole.CRAFTER), VillagerRole.parse("CRAFTER"));
    assertEquals(Optional.of(VillagerRole.CRAFTER), VillagerRole.parse("usta"));
    assertEquals(Optional.of(VillagerRole.CRAFTER), VillagerRole.parse("zanaatkar"));
    assertEquals(Optional.of(VillagerRole.CRAFTER), VillagerRole.parse("zanaatkâr"));

    // Negative / empty cases
    assertEquals(Optional.empty(), VillagerRole.parse(null));
    assertEquals(Optional.empty(), VillagerRole.parse(""));
    assertEquals(Optional.empty(), VillagerRole.parse("   "));
    assertEquals(Optional.empty(), VillagerRole.parse("unknown_role"));
    assertEquals(Optional.empty(), VillagerRole.parse("miner"));
  }

  @Test
  void fromConfigValueStrictValidation() {
    // null defaults to ALL_ROUNDER
    assertEquals(VillagerRole.ALL_ROUNDER, VillagerRole.fromConfigValue(null));

    // Exact enum names
    assertEquals(VillagerRole.ALL_ROUNDER, VillagerRole.fromConfigValue("ALL_ROUNDER"));
    assertEquals(VillagerRole.WORKER, VillagerRole.fromConfigValue("WORKER"));
    assertEquals(VillagerRole.FARMER, VillagerRole.fromConfigValue("FARMER"));
    assertEquals(VillagerRole.CRAFTER, VillagerRole.fromConfigValue("CRAFTER"));

    // Invalid string or lowercase must throw IllegalStateException
    assertThrows(IllegalStateException.class, () -> VillagerRole.fromConfigValue("worker"));
    assertThrows(IllegalStateException.class, () -> VillagerRole.fromConfigValue("INVALID"));
    assertThrows(IllegalStateException.class, () -> VillagerRole.fromConfigValue(""));
    assertThrows(IllegalStateException.class, () -> VillagerRole.fromConfigValue(123));
    assertThrows(IllegalStateException.class, () -> VillagerRole.fromConfigValue(true));
  }

  @Test
  void villagerIdValidation() {
    assertTrue(VillagerRole.isValidVillagerId("ahmet"));
    assertTrue(VillagerRole.isValidVillagerId("mehmet"));
    assertTrue(VillagerRole.isValidVillagerId("ali"));
    assertTrue(VillagerRole.isValidVillagerId("ayse"));
    assertTrue(VillagerRole.isValidVillagerId("v1"));
    assertTrue(VillagerRole.isValidVillagerId("v_2"));
    assertTrue(VillagerRole.isValidVillagerId("worker-1"));
    assertTrue(VillagerRole.isValidVillagerId("a123456789012345")); // 16 chars: 1 + 15

    // Invalid
    assertFalse(VillagerRole.isValidVillagerId(null));
    assertFalse(VillagerRole.isValidVillagerId(""));
    assertFalse(VillagerRole.isValidVillagerId("a")); // too short (< 2 chars)
    assertFalse(VillagerRole.isValidVillagerId("Ahmet")); // uppercase
    assertFalse(VillagerRole.isValidVillagerId("1ahmet")); // starts with digit
    assertFalse(VillagerRole.isValidVillagerId("_ahmet")); // starts with underscore
    assertFalse(VillagerRole.isValidVillagerId("-ahmet")); // starts with hyphen
    assertFalse(VillagerRole.isValidVillagerId("a1234567890123456")); // 17 chars (too long)
    assertFalse(VillagerRole.isValidVillagerId("ahmet!"));
    assertFalse(VillagerRole.isValidVillagerId("ahmet villager"));
  }
}
