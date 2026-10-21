package dev.ahmet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Crash-safe local economy ledger tracking villager work credits, contributions,
 * produced/consumed resource totals, and net village inventory balances.
 */
public final class LocalEconomyLedger {

  public static final int MAX_RECENT_ENTRIES = 64;
  public static final int MAX_APPLIED_KEYS = 512;

  public record Entry(
      String key,
      String villagerId,
      String role,
      String taskId,
      String action,
      String inputMaterial,
      int inputConsumed,
      String outputMaterial,
      int outputProduced,
      int creditDelta
  ) {
    public Entry {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
      if (villagerId == null || villagerId.isBlank()) throw new IllegalArgumentException("villagerId is required");
      if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
      if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId is required");
      if (action == null || action.isBlank()) throw new IllegalArgumentException("action is required");
      if (inputConsumed < 0) throw new IllegalArgumentException("inputConsumed cannot be negative");
      if (outputProduced < 0) throw new IllegalArgumentException("outputProduced cannot be negative");
      if (creditDelta < 0) throw new IllegalArgumentException("creditDelta cannot be negative");
    }
  }

  public record VillagerSummary(
      int credits,
      int gatheredItems,
      int farmedItems,
      int craftedItems,
      int tasksContributed
  ) {
    public VillagerSummary {
      if (credits < 0 || gatheredItems < 0 || farmedItems < 0 || craftedItems < 0 || tasksContributed < 0) {
        throw new IllegalArgumentException("VillagerSummary counters cannot be negative");
      }
    }
  }

  private static final class Data {
    int version = 1;
    Map<String, Integer> netBalances = new LinkedHashMap<>();
    Map<String, Integer> producedTotals = new LinkedHashMap<>();
    Map<String, Integer> consumedTotals = new LinkedHashMap<>();
    Map<String, VillagerSummary> villagerSummaries = new LinkedHashMap<>();
    List<Entry> recentEntries = new ArrayList<>();
    List<String> appliedKeys = new ArrayList<>();
  }

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path path;
  private Data data = new Data();

  public LocalEconomyLedger(Path path) {
    this.path = Objects.requireNonNull(path, "path is required");
  }

  public synchronized void load() throws IOException {
    data = new Data();
    if (!Files.exists(path)) {
      return;
    }

    Data loaded;
    try {
      loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
    } catch (Exception e) {
      throw new IllegalStateException("Corrupted economy ledger JSON", e);
    }

    if (loaded == null || loaded.version != 1) {
      throw new IllegalStateException("Corrupt or invalid economy ledger version");
    }
    if (loaded.netBalances == null || loaded.producedTotals == null || loaded.consumedTotals == null
        || loaded.villagerSummaries == null || loaded.recentEntries == null || loaded.appliedKeys == null) {
      throw new IllegalStateException("Missing economy ledger sections");
    }

    // Validate produced & consumed counters
    for (var entry : loaded.producedTotals.entrySet()) {
      if (entry.getValue() == null || entry.getValue() < 0) {
        throw new IllegalStateException("Economy ledger contains invalid produced counter for " + entry.getKey());
      }
    }
    for (var entry : loaded.consumedTotals.entrySet()) {
      if (entry.getValue() == null || entry.getValue() < 0) {
        throw new IllegalStateException("Economy ledger contains invalid consumed counter for " + entry.getKey());
      }
    }

    // Validate villager summaries
    for (var entry : loaded.villagerSummaries.entrySet()) {
      var s = entry.getValue();
      if (s == null || s.credits() < 0 || s.gatheredItems() < 0 || s.farmedItems() < 0
          || s.craftedItems() < 0 || s.tasksContributed() < 0) {
        throw new IllegalStateException("Economy ledger contains invalid villager summary for " + entry.getKey());
      }
    }

    data = loaded;
  }

  public synchronized void save() throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
    byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
    try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    }
    try {
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public synchronized boolean recordDeposit(
      String transactionKey,
      String villagerId,
      VillagerRole role,
      String taskId,
      WoodTask.Kind kind,
      String material,
      int amount
  ) throws IOException {
    if (transactionKey == null || transactionKey.isBlank()) {
      throw new IllegalArgumentException("transactionKey is required");
    }
    if (villagerId == null || villagerId.isBlank()) {
      throw new IllegalArgumentException("villagerId is required");
    }
    if (role == null) {
      throw new IllegalArgumentException("role is required");
    }
    if (taskId == null || taskId.isBlank()) {
      throw new IllegalArgumentException("taskId is required");
    }
    if (kind != WoodTask.Kind.GATHER && kind != WoodTask.Kind.FARM) {
      throw new IllegalArgumentException("kind must be GATHER or FARM");
    }
    if (material == null || material.isBlank()) {
      throw new IllegalArgumentException("material is required");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }

    if (data.appliedKeys.contains(transactionKey)) {
      return false;
    }

    data.producedTotals.merge(material, amount, Integer::sum);
    data.netBalances.merge(material, amount, Integer::sum);

    VillagerSummary current = villagerSummary(villagerId);
    int gathered = current.gatheredItems() + (kind == WoodTask.Kind.GATHER ? amount : 0);
    int farmed = current.farmedItems() + (kind == WoodTask.Kind.FARM ? amount : 0);
    int credits = current.credits() + amount;
    int tasks = current.tasksContributed() + 1;
    data.villagerSummaries.put(villagerId, new VillagerSummary(credits, gathered, farmed, current.craftedItems(), tasks));

    Entry entry = new Entry(
        transactionKey,
        villagerId,
        role.name(),
        taskId,
        kind.name().toLowerCase(Locale.ROOT),
        null,
        0,
        material,
        amount,
        amount
    );
    data.recentEntries.add(entry);
    while (data.recentEntries.size() > MAX_RECENT_ENTRIES) {
      data.recentEntries.remove(0);
    }

    data.appliedKeys.add(transactionKey);
    while (data.appliedKeys.size() > MAX_APPLIED_KEYS) {
      data.appliedKeys.remove(0);
    }

    save();
    return true;
  }

  public synchronized boolean recordCraft(
      String transactionKey,
      String villagerId,
      VillagerRole role,
      String taskId,
      String inputMaterial,
      int inputConsumed,
      String outputMaterial,
      int outputProduced
  ) throws IOException {
    if (transactionKey == null || transactionKey.isBlank()) {
      throw new IllegalArgumentException("transactionKey is required");
    }
    if (villagerId == null || villagerId.isBlank()) {
      throw new IllegalArgumentException("villagerId is required");
    }
    if (role == null) {
      throw new IllegalArgumentException("role is required");
    }
    if (taskId == null || taskId.isBlank()) {
      throw new IllegalArgumentException("taskId is required");
    }
    if (inputMaterial == null || inputMaterial.isBlank()) {
      throw new IllegalArgumentException("inputMaterial is required");
    }
    if (outputMaterial == null || outputMaterial.isBlank()) {
      throw new IllegalArgumentException("outputMaterial is required");
    }
    if (inputConsumed <= 0) {
      throw new IllegalArgumentException("inputConsumed must be positive");
    }
    if (outputProduced <= 0) {
      throw new IllegalArgumentException("outputProduced must be positive");
    }

    if (data.appliedKeys.contains(transactionKey)) {
      return false;
    }

    data.consumedTotals.merge(inputMaterial, inputConsumed, Integer::sum);
    data.producedTotals.merge(outputMaterial, outputProduced, Integer::sum);
    data.netBalances.merge(inputMaterial, -inputConsumed, Integer::sum);
    data.netBalances.merge(outputMaterial, outputProduced, Integer::sum);

    VillagerSummary current = villagerSummary(villagerId);
    int crafted = current.craftedItems() + outputProduced;
    int credits = current.credits() + outputProduced;
    int tasks = current.tasksContributed() + 1;
    data.villagerSummaries.put(villagerId, new VillagerSummary(credits, current.gatheredItems(), current.farmedItems(), crafted, tasks));

    Entry entry = new Entry(
        transactionKey,
        villagerId,
        role.name(),
        taskId,
        "craft",
        inputMaterial,
        inputConsumed,
        outputMaterial,
        outputProduced,
        outputProduced
    );
    data.recentEntries.add(entry);
    while (data.recentEntries.size() > MAX_RECENT_ENTRIES) {
      data.recentEntries.remove(0);
    }

    data.appliedKeys.add(transactionKey);
    while (data.appliedKeys.size() > MAX_APPLIED_KEYS) {
      data.appliedKeys.remove(0);
    }

    save();
    return true;
  }

  public synchronized VillagerSummary villagerSummary(String villagerId) {
    if (villagerId == null) {
      return new VillagerSummary(0, 0, 0, 0, 0);
    }
    return data.villagerSummaries.getOrDefault(villagerId, new VillagerSummary(0, 0, 0, 0, 0));
  }

  public synchronized int villagerCredits(String villagerId) {
    return villagerSummary(villagerId).credits();
  }

  public synchronized Map<String, Integer> netBalances() {
    return Map.copyOf(data.netBalances);
  }

  public synchronized Map<String, Integer> producedTotals() {
    return Map.copyOf(data.producedTotals);
  }

  public synchronized Map<String, Integer> consumedTotals() {
    return Map.copyOf(data.consumedTotals);
  }

  public synchronized List<Entry> recentEntries() {
    return List.copyOf(data.recentEntries);
  }

  public synchronized String describeSummary() {
    int totalProduced = data.producedTotals.values().stream().mapToInt(Integer::intValue).sum();
    int totalConsumed = data.consumedTotals.values().stream().mapToInt(Integer::intValue).sum();
    String villagers = data.villagerSummaries.entrySet().stream()
        .map(e -> e.getKey() + ": " + e.getValue().credits() + " kredi (" + e.getValue().tasksContributed() + " görev)")
        .collect(Collectors.joining(", "));
    if (villagers.isBlank()) {
      villagers = "kayıt yok";
    }
    return String.format(Locale.ROOT,
        "Köy Ekonomisi: Üretilen: %d, Tüketilen: %d | Köylüler: [%s]",
        totalProduced, totalConsumed, villagers);
  }
}
