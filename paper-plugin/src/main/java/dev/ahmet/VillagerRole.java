package dev.ahmet;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bounded villager roles defining task execution authority and specialization.
 */
public enum VillagerRole {
  ALL_ROUNDER("genel", false, Set.of(WoodTask.Kind.GATHER, WoodTask.Kind.FARM, WoodTask.Kind.CRAFT)),
  WORKER("işçi", true, Set.of(WoodTask.Kind.GATHER)),
  FARMER("çiftçi", true, Set.of(WoodTask.Kind.FARM)),
  CRAFTER("usta", true, Set.of(WoodTask.Kind.CRAFT));

  private static final Pattern VILLAGER_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]{1,15}$");

  private final String labelTr;
  private final boolean specialist;
  private final Set<WoodTask.Kind> allowedKinds;

  VillagerRole(String labelTr, boolean specialist, Set<WoodTask.Kind> allowedKinds) {
    this.labelTr = Objects.requireNonNull(labelTr, "labelTr is required");
    this.specialist = specialist;
    this.allowedKinds = Set.copyOf(allowedKinds);
  }

  public String labelTr() {
    return labelTr;
  }

  public boolean isSpecialist() {
    return specialist;
  }

  public boolean canExecute(WoodTask.Kind kind) {
    if (kind == null) {
      return false;
    }
    return allowedKinds.contains(kind);
  }

  public static Optional<VillagerRole> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "all", "all_rounder", "all-rounder", "genel", "hepsi" -> Optional.of(ALL_ROUNDER);
      case "worker", "isci", "işçi", "oduncu", "gatherer" -> Optional.of(WORKER);
      case "farmer", "ciftci", "çiftçi", "tarimci", "tarımcı" -> Optional.of(FARMER);
      case "crafter", "usta", "zanaatkar", "zanaatkâr" -> Optional.of(CRAFTER);
      default -> Optional.empty();
    };
  }

  public static VillagerRole fromConfigValue(Object raw) {
    if (raw == null) {
      return ALL_ROUNDER;
    }
    if (raw instanceof String s) {
      return switch (s) {
        case "ALL_ROUNDER" -> ALL_ROUNDER;
        case "WORKER" -> WORKER;
        case "FARMER" -> FARMER;
        case "CRAFTER" -> CRAFTER;
        default -> throw new IllegalStateException("Saved villager role is invalid");
      };
    }
    throw new IllegalStateException("Saved villager role is invalid");
  }

  public static boolean isValidVillagerId(String id) {
    if (id == null) {
      return false;
    }
    return VILLAGER_ID_PATTERN.matcher(id).matches();
  }
}
