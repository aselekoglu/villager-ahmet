package dev.ahmet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/** Version-pinned inventory of every obtainable vanilla Paper item material. */
final class MaterialCatalog {
  static final String MINECRAFT_VERSION = "1.21.6";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private static volatile List<Entry> items;

  private MaterialCatalog() {}

  static List<Entry> items() {
    List<Entry> current = items;
    if (current != null) return current;
    synchronized (MaterialCatalog.class) {
      if (items == null) {
        items = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .filter(material -> !material.isAir() && !material.isLegacy())
            .map(MaterialCatalog::toEntry)
            .sorted(Comparator.comparing(Entry::id))
            .toList();
      }
      return items;
    }
  }

  static Material find(String id) {
    String name = normalizeId(id);
    if (name == null) return null;
    Material material = Material.matchMaterial(name);
    return material != null && material.isItem() && !material.isAir() && !material.isLegacy() ? material : null;
  }

  static Entry entry(String id) {
    Material material = find(id);
    return material == null ? null : toEntry(material);
  }

  static List<Entry> search(String query) {
    String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
    if (normalized.isEmpty()) return items();
    return items().stream().filter(item -> item.id().contains(normalized)).toList();
  }

  static String normalizeId(String id) {
    if (id == null || id.isBlank()) return null;
    String normalized = id.toLowerCase(Locale.ROOT);
    if (normalized.indexOf(':') >= 0 && !normalized.startsWith("minecraft:")) return null;
    String name = normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
    return name.matches("[a-z0-9_]+") ? name : null;
  }

  static void write(Path path) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
    Files.writeString(temporary, GSON.toJson(new Catalog(MINECRAFT_VERSION, items())), StandardCharsets.UTF_8);
    try {
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
      Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Entry toEntry(Material material) {
    String capability = WoodMaterials.isWoodName(material.name()) ? "collect_wood"
        : ResourceCatalog.isSupportedOutput(material.name()) ? "collect_resource" : "none";
    return new Entry(material.getKey().toString(), material.name(), material.isBlock(), material.getMaxStackSize(),
        !"none".equals(capability), capability);
  }

  record Entry(String id, String material, boolean block, int maxStackSize, boolean gatherable, String capability) {}
  private record Catalog(String minecraftVersion, List<Entry> items) {}
}
