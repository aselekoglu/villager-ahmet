package dev.ahmet;

import java.util.Set;
import org.bukkit.Material;

final class SafeGroundPolicy {
  private static final Set<Material> HAZARDS=Set.of(Material.MAGMA_BLOCK,Material.CACTUS,Material.FIRE,
      Material.SOUL_FIRE,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.SWEET_BERRY_BUSH,
      Material.WITHER_ROSE,Material.POWDER_SNOW);
  private SafeGroundPolicy() {}
  static boolean isHazard(Material material){return HAZARDS.contains(material);}
}
