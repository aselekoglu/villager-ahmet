package dev.ahmet;

import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class SafeGroundPolicyTest {
  @Test void knownDamageAndMovementHazardsAreRejected() {
    for (Material material : new Material[]{Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
        Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE, Material.POWDER_SNOW}) assertTrue(SafeGroundPolicy.isHazard(material), material.name());
    assertFalse(SafeGroundPolicy.isHazard(Material.GRASS_BLOCK));
  }
}
