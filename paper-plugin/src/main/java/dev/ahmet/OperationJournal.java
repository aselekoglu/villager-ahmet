package dev.ahmet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class OperationJournal {
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private final Path path;
  private Data data = new Data();

  OperationJournal(Path path) {
    this.path = Objects.requireNonNull(path);
  }

  synchronized void load() throws IOException {
    data = new Data();
    if (!Files.exists(path)) return;
    try {
      Data loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
      if (loaded == null || loaded.version < 1 || loaded.version > 3) throw new IOException("Unsupported operation journal version");
      if (loaded.harvests == null) loaded.harvests = new ArrayList<>();
      if (loaded.harvests.size() > 64) throw new IOException("Operation journal exceeds the task bound");
      if (loaded.version == 2) {
        if (missingRecoveryFields(loaded.pendingHarvest) || loaded.harvests.stream().anyMatch(OperationJournal::missingRecoveryFields)
            || missingInventorySize(loaded.pendingDeposit)) {
          throw new IOException("Version 2 operation journal lacks recovery safety fields; it was preserved for manual recovery");
        }
      }
      loaded.version = 3;
      data = loaded;
    } catch (JsonParseException | IllegalStateException e) {
      throw new IOException("Operation journal is invalid; it was preserved for manual recovery", e);
    }
  }

  private static boolean missingRecoveryFields(Harvest harvest) {
    return harvest != null && harvest.beforeOutputCargo() == null;
  }

  private static boolean missingInventorySize(Deposit deposit) {
    return deposit != null && deposit.inventorySize() < 1;
  }

  synchronized Optional<Harvest> pendingHarvest() {
    return Optional.ofNullable(data.pendingHarvest);
  }

  synchronized List<Harvest> harvests() {
    return List.copyOf(data.harvests);
  }

  synchronized Optional<Deposit> pendingDeposit() {
    return Optional.ofNullable(data.pendingDeposit);
  }

  synchronized boolean hasOperations() {
    return data.pendingHarvest != null || data.pendingDeposit != null || !data.harvests.isEmpty();
  }

  synchronized void prepareHarvest(Harvest operation) throws IOException {
    if (operation == null || data.pendingHarvest != null || data.pendingDeposit != null || data.harvests.size() >= 64) {
      throw new IllegalStateException("Cannot prepare another harvest operation");
    }
    data.pendingHarvest = operation;
    persist();
  }

  synchronized void completeHarvest() throws IOException {
    if (data.pendingHarvest == null) throw new IllegalStateException("No harvest operation is pending");
    data.harvests.add(data.pendingHarvest);
    data.pendingHarvest = null;
    persist();
  }

  synchronized void discardPendingHarvest() throws IOException {
    if (data.pendingHarvest == null) return;
    data.pendingHarvest = null;
    persistOrDeleteIfEmpty();
  }

  synchronized void prepareDeposit(Deposit operation) throws IOException {
    if (operation == null || data.pendingHarvest != null || data.pendingDeposit != null) {
      throw new IllegalStateException("Cannot prepare another deposit operation");
    }
    data.pendingDeposit = operation;
    persist();
  }

  synchronized void clearAfterWorldSave() throws IOException {
    Files.deleteIfExists(path);
    data = new Data();
  }

  private void persistOrDeleteIfEmpty() throws IOException {
    if (data.harvests.isEmpty() && data.pendingDeposit == null) {
      Files.deleteIfExists(path);
      data = new Data();
    } else {
      persist();
    }
  }

  private void persist() throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
    byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
    try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) channel.write(buffer);
      channel.force(true);
    }
    try {
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  record Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo,
      String material, String outputMaterial, Integer beforeOutputCargo) {
    Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo) {
      this(taskId, world, x, y, z, beforeCollected, beforeCargo, "OAK_LOG", "OAK_LOG", beforeCargo);
    }

    Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo,
        String material) {
      this(taskId, world, x, y, z, beforeCollected, beforeCargo, material, material, beforeCargo);
    }

    Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo,
        String material, String outputMaterial) {
      this(taskId,world,x,y,z,beforeCollected,beforeCargo,material,outputMaterial,beforeCargo);
    }

    Harvest {
      if (taskId == null || taskId.isBlank() || world == null || world.isBlank()
          || beforeCollected < 0 || beforeCollected >= 64 || beforeCargo < 0 || beforeCargo >= 64
          || material != null && !WoodMaterials.isAllowedName(material) && !ResourceCatalog.isBlockMaterial(material)
          || outputMaterial != null && !WoodMaterials.isAllowedName(outputMaterial)
          || beforeOutputCargo != null && (beforeOutputCargo < 0 || beforeOutputCargo > beforeCargo)) {
        throw new IllegalArgumentException("Invalid harvest journal entry");
      }
    }

    String effectiveMaterial() { return material == null ? "OAK_LOG" : material; }
    String effectiveOutputMaterial() { return outputMaterial == null ? effectiveMaterial() : outputMaterial; }
    int beforeOutputCargoCount() {
      if(beforeOutputCargo!=null)return beforeOutputCargo;
      return material==null&&outputMaterial==null?beforeCargo:-1;
    }
    int afterCollected() { return beforeCollected + 1; }
    int afterCargo() { return beforeCargo + 1; }
  }

  record Deposit(String taskId, String world, int x, int y, int z, int beforeCargo,
      int beforeDeposited, int moved, List<CargoDepositPlanner.Change> changes, String material, int inventorySize) {
    Deposit(String taskId, String world, int x, int y, int z, int beforeCargo,
        int beforeDeposited, int moved, List<CargoDepositPlanner.Change> changes) {
      this(taskId, world, x, y, z, beforeCargo, beforeDeposited, moved, changes, "OAK_LOG", maxSlot(changes));
    }

    Deposit(String taskId, String world, int x, int y, int z, int beforeCargo, int beforeDeposited,
        int moved, List<CargoDepositPlanner.Change> changes, String material) {
      this(taskId,world,x,y,z,beforeCargo,beforeDeposited,moved,changes,material,maxSlot(changes));
    }
    private static int maxSlot(List<CargoDepositPlanner.Change> changes) {
      return changes==null||changes.isEmpty()?0:changes.stream().mapToInt(CargoDepositPlanner.Change::slot).max().orElse(-1)+1;
    }

    Deposit {
      if (taskId == null || taskId.isBlank() || world == null || world.isBlank()
          || beforeCargo < 1 || beforeCargo > 64 || beforeDeposited < 0
          || moved < 1 || moved > beforeCargo || changes == null || changes.isEmpty() || inventorySize < 1
          || material != null && !WoodMaterials.isAllowedName(material)) {
        throw new IllegalArgumentException("Invalid deposit journal entry");
      }
      changes = List.copyOf(changes);
      java.util.Set<Integer> indices=new java.util.HashSet<>();int delta=0;
      for(CargoDepositPlanner.Change change:changes){
        if(change.slot()>=inventorySize||!indices.add(change.slot()))throw new IllegalArgumentException("Invalid deposit inventory shape or duplicate slot");
        delta+=change.afterAmount()-change.beforeAmount();
      }
      if(delta!=moved||beforeDeposited+moved>64)throw new IllegalArgumentException("Deposit journal counters do not match slot changes");
    }

    String effectiveMaterial() { return material == null ? "OAK_LOG" : material; }
    int afterCargo() { return beforeCargo - moved; }
    int afterDeposited() { return beforeDeposited + moved; }
  }

  private static final class Data {
    int version = 3;
    Harvest pendingHarvest;
    List<Harvest> harvests = new ArrayList<>();
    Deposit pendingDeposit;
  }
}
