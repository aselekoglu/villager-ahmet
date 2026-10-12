package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MaterialCatalogTest {
  @Test
  void canonicalIdNormalizationAcceptsVanillaIdsAndRejectsForeignOrMalformedIds() {
    assertEquals("diamond", MaterialCatalog.normalizeId("minecraft:diamond"));
    assertEquals("oak_log", MaterialCatalog.normalizeId("Oak_Log"));
    assertEquals("not_a_vanilla_item", MaterialCatalog.normalizeId("minecraft:not_a_vanilla_item"));
    assertEquals(null, MaterialCatalog.normalizeId("other:diamond"));
    assertEquals(null, MaterialCatalog.normalizeId("minecraft:oak log"));
    assertEquals(null, MaterialCatalog.normalizeId(null));
  }

  @Test
  void catalogEntryClearlySeparatesRecognitionFromCapability() {
    var wood = new MaterialCatalog.Entry("minecraft:oak_log", "OAK_LOG", true, 64, true, "collect_wood");
    var diamond = new MaterialCatalog.Entry("minecraft:diamond", "DIAMOND", false, 64, false, "none");
    assertNotNull(wood);
    assertTrue(wood.gatherable());
    assertEquals("collect_wood", wood.capability());
    assertFalse(diamond.gatherable());
    assertEquals("none", diamond.capability());
  }
}
