package dev.ahmet;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure domain catalog for allowlisted farm crops (wheat, carrot, potato).
 */
public final class FarmCatalog {
  private FarmCatalog() {}

  public enum Crop {
    WHEAT("crop:WHEAT", "minecraft:wheat", "WHEAT", "WHEAT", 7, 0,
        List.of("wheat", "minecraft:wheat", "bugday", "buğday")),
    CARROT("crop:CARROT", "minecraft:carrot", "CARROTS", "CARROT", 7, 0,
        List.of("carrot", "carrots", "minecraft:carrot", "havuc", "havuç")),
    POTATO("crop:POTATO", "minecraft:potato", "POTATOES", "POTATO", 7, 0,
        List.of("potato", "potatoes", "minecraft:potato", "patates"));

    private final String preference;
    private final String itemId;
    private final String blockMaterial;
    private final String outputMaterial;
    private final int matureAge;
    private final int replantAge;
    private final List<String> aliases;

    Crop(String preference, String itemId, String blockMaterial, String outputMaterial,
        int matureAge, int replantAge, List<String> aliases) {
      this.preference = preference;
      this.itemId = itemId;
      this.blockMaterial = blockMaterial;
      this.outputMaterial = outputMaterial;
      this.matureAge = matureAge;
      this.replantAge = replantAge;
      this.aliases = List.copyOf(aliases);
    }

    public String preference() { return preference; }
    public String itemId() { return itemId; }
    public String blockMaterial() { return blockMaterial; }
    public String outputMaterial() { return outputMaterial; }
    public int matureAge() { return matureAge; }
    public int replantAge() { return replantAge; }
    public List<String> aliases() { return aliases; }
  }

  public static final String ANY_PREFERENCE = "crop:ANY";
  private static final List<String> ANY_ALIASES = List.of("any", "crop", "crops", "ekin", "ekinler", "hasat", "mahsul", "tarla", "farm");

  public static boolean isPreference(String preference) {
    if (preference == null) return false;
    if (ANY_PREFERENCE.equals(preference)) return true;
    for (Crop crop : Crop.values()) {
      if (crop.preference().equals(preference)) return true;
    }
    return false;
  }

  public static Optional<String> preferenceForAlias(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    String trimmed = token.trim();
    if (ANY_PREFERENCE.equalsIgnoreCase(trimmed)) return Optional.of(ANY_PREFERENCE);
    for (Crop crop : Crop.values()) {
      if (crop.preference().equalsIgnoreCase(trimmed)) return Optional.of(crop.preference());
    }

    String raw = trimmed.replace('İ', 'i').replace('I', 'i').replace('ı', 'i').toLowerCase(Locale.ROOT);
    if (ANY_ALIASES.contains(raw)) return Optional.of(ANY_PREFERENCE);
    for (Crop crop : Crop.values()) {
      if (crop.aliases().contains(raw)) return Optional.of(crop.preference());
    }
    return Optional.empty();
  }

  public static String displayItemName(String preference) {
    if (preference == null) return null;
    return switch (preference) {
      case "crop:WHEAT" -> "buğday";
      case "crop:CARROT" -> "havuç";
      case "crop:POTATO" -> "patates";
      case ANY_PREFERENCE -> "ekin";
      default -> null;
    };
  }

  public static String displayItemName(Crop crop) {
    if (crop == null) return null;
    return displayItemName(crop.preference());
  }

  public static Optional<Crop> forBlockMaterial(String blockMaterial) {
    if (blockMaterial == null) return Optional.empty();
    for (Crop crop : Crop.values()) {
      if (crop.blockMaterial().equals(blockMaterial)) return Optional.of(crop);
    }
    return Optional.empty();
  }

  public static Optional<Crop> forOutputMaterial(String outputMaterial) {
    if (outputMaterial == null) return Optional.empty();
    for (Crop crop : Crop.values()) {
      if (crop.outputMaterial().equals(outputMaterial)) return Optional.of(crop);
    }
    return Optional.empty();
  }

  public static boolean isCropBlock(String blockMaterial) {
    return forBlockMaterial(blockMaterial).isPresent();
  }

  public static boolean isCropOutput(String outputMaterial) {
    return forOutputMaterial(outputMaterial).isPresent();
  }

  public static boolean matchesCropBlock(String blockMaterial, String target) {
    if (blockMaterial == null || target == null) return false;
    Optional<Crop> crop = forBlockMaterial(blockMaterial);
    if (crop.isEmpty()) return false;
    Crop c = crop.get();
    return ANY_PREFERENCE.equals(target) || c.preference().equals(target) || c.outputMaterial().equals(target);
  }

  public static String outputForBlock(String blockMaterial) {
    return forBlockMaterial(blockMaterial).map(Crop::outputMaterial).orElse(null);
  }

  public static String outputForBlock(String blockMaterial, String preference) {
    if (!matchesCropBlock(blockMaterial, preference)) return null;
    return outputForBlock(blockMaterial);
  }

  public static boolean isMatureAndFarmable(String blockMaterial, String preference, String belowBlockMaterial, int age, int maxAge) {
    return matchesCropBlock(blockMaterial, preference)
        && "FARMLAND".equals(belowBlockMaterial)
        && maxAge == 7
        && age == 7;
  }
}
