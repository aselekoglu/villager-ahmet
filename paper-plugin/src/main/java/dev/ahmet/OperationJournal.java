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
      if (loaded == null || loaded.version < 1 || loaded.version > 4) throw new IOException("Unsupported operation journal version");
      if (loaded.harvests == null) loaded.harvests = new ArrayList<>();
      if (loaded.clears == null) loaded.clears = new ArrayList<>();
      if (loaded.harvests.size() > 64 || loaded.clears.size() > 64) throw new IOException("Operation journal exceeds the task bound");
      if (loaded.version == 2) {
        if (missingRecoveryFields(loaded.pendingHarvest) || loaded.harvests.stream().anyMatch(OperationJournal::missingRecoveryFields)
            || missingInventorySize(loaded.pendingDeposit)) {
          throw new IOException("Version 2 operation journal lacks recovery safety fields; it was preserved for manual recovery");
        }
      }
      if (loaded.version < 3 && loaded.pendingCraft != null) {
        throw new IOException("Legacy operation journal version cannot contain a craft operation");
      }
      if (loaded.pendingCraft != null && (loaded.pendingHarvest != null || loaded.pendingDeposit != null
          || loaded.pendingClear != null || !loaded.harvests.isEmpty() || !loaded.clears.isEmpty())) {
        throw new IOException("Craft journal cannot coexist with other operations; it was preserved for manual recovery");
      }
      if (loaded.pendingHarvest != null) {
        loaded.pendingHarvest = new Harvest(
            loaded.pendingHarvest.taskId(), loaded.pendingHarvest.world(),
            loaded.pendingHarvest.x(), loaded.pendingHarvest.y(), loaded.pendingHarvest.z(),
            loaded.pendingHarvest.beforeCollected(), loaded.pendingHarvest.beforeCargo(),
            loaded.pendingHarvest.material(), loaded.pendingHarvest.outputMaterial(),
            loaded.pendingHarvest.beforeOutputCargo(), loaded.pendingHarvest.beforeCropAge(),
            loaded.pendingHarvest.afterCropAge());
      }
      List<Harvest> revalidatedHarvests = new ArrayList<>();
      for (Harvest h : loaded.harvests) {
        revalidatedHarvests.add(new Harvest(
            h.taskId(), h.world(), h.x(), h.y(), h.z(),
            h.beforeCollected(), h.beforeCargo(),
            h.material(), h.outputMaterial(),
            h.beforeOutputCargo(), h.beforeCropAge(), h.afterCropAge()));
      }
      loaded.harvests = revalidatedHarvests;
      if (loaded.pendingCraft != null) {
        loaded.pendingCraft = new Craft(
            loaded.pendingCraft.taskId(), loaded.pendingCraft.owner(), loaded.pendingCraft.kind(),
            loaded.pendingCraft.recipeKey(), loaded.pendingCraft.world(), loaded.pendingCraft.x(),
            loaded.pendingCraft.y(), loaded.pendingCraft.z(), loaded.pendingCraft.inventorySize(),
            loaded.pendingCraft.requested(), loaded.pendingCraft.inputCount(), loaded.pendingCraft.beforeCollected(),
            loaded.pendingCraft.beforeDeposited(), loaded.pendingCraft.afterCollected(), loaded.pendingCraft.afterDeposited(),
            loaded.pendingCraft.before(), loaded.pendingCraft.after(), loaded.pendingCraft.changes());
      }
      loaded.version = loaded.pendingCraft != null ? 4 : 3;
      data = loaded;
    } catch (RuntimeException e) {
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

  synchronized Optional<Clear> pendingClear() {
    return Optional.ofNullable(data.pendingClear);
  }

  synchronized Optional<Craft> pendingCraft() {
    return Optional.ofNullable(data.pendingCraft);
  }

  synchronized List<Clear> clears() {
    return List.copyOf(data.clears);
  }

  synchronized boolean hasOperations() {
    return hasPendingOperation() || !data.harvests.isEmpty() || !data.clears.isEmpty();
  }

  synchronized boolean hasCommittedOperationsReadyForCheckpoint() {
    return !hasPendingOperation() && (!data.harvests.isEmpty() || !data.clears.isEmpty());
  }

  synchronized boolean hasPendingOperation() {
    return data.pendingHarvest != null || data.pendingDeposit != null || data.pendingClear != null || data.pendingCraft != null;
  }

  synchronized void prepareHarvest(Harvest operation) throws IOException {
    if (operation == null || hasPendingOperation() || data.harvests.size() >= 64) {
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
    if (operation == null || hasPendingOperation()) {
      throw new IllegalStateException("Cannot prepare another deposit operation");
    }
    data.pendingDeposit = operation;
    persist();
  }

  synchronized void prepareClear(Clear operation) throws IOException {
    if (operation == null || hasPendingOperation() || data.clears.size() >= 64) {
      throw new IllegalStateException("Cannot prepare another clear operation");
    }
    data.pendingClear = operation;
    persist();
  }

  synchronized void completeClear() throws IOException {
    if (data.pendingClear == null) throw new IllegalStateException("No clear operation is pending");
    data.clears.add(data.pendingClear);
    data.pendingClear = null;
    persist();
  }

  synchronized void discardPendingClear() throws IOException {
    if (data.pendingClear == null) return;
    data.pendingClear = null;
    persistOrDeleteIfEmpty();
  }

  synchronized void prepareCraft(Craft operation) throws IOException {
    if (operation == null || hasPendingOperation() || !data.harvests.isEmpty() || !data.clears.isEmpty()) {
      throw new IllegalStateException("Cannot prepare craft operation while other journal records exist");
    }
    data.version = 4;
    data.pendingCraft = operation;
    persist();
  }

  synchronized void discardPendingCraft() throws IOException {
    if (data.pendingCraft == null) return;
    data.pendingCraft = null;
    persistOrDeleteIfEmpty();
  }

  synchronized void clearAfterWorldSave() throws IOException {
    Files.deleteIfExists(path);
    data = new Data();
  }

  private void persistOrDeleteIfEmpty() throws IOException {
    if (!hasPendingOperation() && data.harvests.isEmpty() && data.clears.isEmpty()) {
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
      String material, String outputMaterial, Integer beforeOutputCargo, Integer beforeCropAge, Integer afterCropAge) {
    Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo) {
      this(taskId, world, x, y, z, beforeCollected, beforeCargo, "OAK_LOG", "OAK_LOG", beforeCargo, null, null);
    }

    Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo,
        String material) {
      this(taskId, world, x, y, z, beforeCollected, beforeCargo, material, material, beforeCargo, null, null);
    }

    Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo,
        String material, String outputMaterial) {
      this(taskId, world, x, y, z, beforeCollected, beforeCargo, material, outputMaterial, beforeCargo, null, null);
    }

    Harvest(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo,
        String material, String outputMaterial, Integer beforeOutputCargo) {
      this(taskId, world, x, y, z, beforeCollected, beforeCargo, material, outputMaterial, beforeOutputCargo, null, null);
    }

    static Harvest forCrop(String taskId, String world, int x, int y, int z, int beforeCollected, int beforeCargo,
        String blockMaterial, String outputMaterial, int beforeOutputCargo) {
      return new Harvest(taskId, world, x, y, z, beforeCollected, beforeCargo, blockMaterial, outputMaterial, beforeOutputCargo, 7, 0);
    }

    Harvest {
      if (taskId == null || taskId.isBlank() || world == null || world.isBlank()
          || beforeCollected < 0 || beforeCollected >= 64 || beforeCargo < 0 || beforeCargo >= 64
          || (material != null && !WoodMaterials.isAllowedName(material) && !ResourceCatalog.isBlockMaterial(material) && !FarmCatalog.isCropBlock(material))
          || (outputMaterial != null && !WoodMaterials.isAllowedName(outputMaterial) && !FarmCatalog.isCropOutput(outputMaterial))
          || (beforeOutputCargo != null && (beforeOutputCargo < 0 || beforeOutputCargo > beforeCargo))) {
        throw new IllegalArgumentException("Invalid harvest journal entry");
      }
      if (beforeCropAge != null || afterCropAge != null) {
        if (beforeCropAge == null || afterCropAge == null
            || beforeCropAge != 7 || afterCropAge != 0
            || beforeOutputCargo == null
            || material == null || !FarmCatalog.isCropBlock(material)
            || outputMaterial == null || !FarmCatalog.isCropOutput(outputMaterial)
            || !FarmCatalog.matchesCropBlock(material, outputMaterial)) {
          throw new IllegalArgumentException("Invalid crop harvest journal entry");
        }
      } else {
        if (material != null && FarmCatalog.isCropBlock(material)) {
          throw new IllegalArgumentException("Crop harvest entry must specify crop ages 7 and 0");
        }
      }
    }

    boolean isCropHarvest() {
      return beforeCropAge != null && afterCropAge != null;
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

  record Clear(String taskId, String world, int x, int y, int z, String material) {
    Clear {
      if (taskId == null || taskId.isBlank() || world == null || world.isBlank()
          || material == null || material.isBlank()) {
        throw new IllegalArgumentException("Invalid clear journal entry");
      }
    }
  }

  record Craft(String taskId, String owner, String kind, String recipeKey, String world,
      int x, int y, int z, int inventorySize, int requested, int inputCount,
      int beforeCollected, int beforeDeposited, int afterCollected, int afterDeposited,
      List<CraftingPlanner.Slot> before, List<CraftingPlanner.Slot> after,
      List<CraftingPlanner.Change> changes) {

    Craft {
      if (taskId == null || taskId.isBlank()
          || owner == null || owner.isBlank()
          || !"CRAFT".equals(kind)
          || world == null || world.isBlank()
          || beforeCollected != 0 || beforeDeposited != 0
          || afterCollected != requested || afterDeposited != requested
          || before == null || after == null || changes == null || changes.isEmpty()) {
        throw new IllegalArgumentException("Invalid craft journal entry");
      }
      CraftRecipe recipe = CraftRecipe.forKey(recipeKey);
      if (recipe == null) {
        throw new IllegalArgumentException("Invalid craft journal entry: unsupported recipe");
      }
      CraftingPlanner.Plan validated;
      try {
        validated = new CraftingPlanner.Plan(recipe, requested, inputCount, before, after, changes, inventorySize);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Invalid craft journal entry", e);
      }
      before = validated.before();
      after = validated.after();
      changes = validated.changes();
    }

    Craft(String taskId, String owner, String world, int x, int y, int z, CraftingPlanner.Plan plan) {
      this(taskId, owner, "CRAFT", plan.recipe().key(), world, x, y, z,
          plan.inventorySize(), plan.outputCount(), plan.inputCount(), 0, 0,
          plan.outputCount(), plan.outputCount(), plan.before(), plan.after(), plan.changes());
    }

    static Craft fromPlan(String taskId, String owner, String world, int x, int y, int z, CraftingPlanner.Plan plan) {
      Objects.requireNonNull(plan, "Plan is required");
      return new Craft(taskId, owner, "CRAFT", plan.recipe().key(), world, x, y, z,
          plan.inventorySize(), plan.outputCount(), plan.inputCount(), 0, 0,
          plan.outputCount(), plan.outputCount(), plan.before(), plan.after(), plan.changes());
    }

    CraftRecipe recipe() { return CraftRecipe.forKey(recipeKey); }

    CraftingPlanner.Plan toPlan() {
      return new CraftingPlanner.Plan(recipe(), requested, inputCount, before, after, changes, inventorySize);
    }

    boolean matchesChest(String worldName, int blockX, int blockY, int blockZ) {
      return Objects.equals(world, worldName) && x == blockX && y == blockY && z == blockZ;
    }
  }

  private static final class Data {
    int version = 3;
    Harvest pendingHarvest;
    List<Harvest> harvests = new ArrayList<>();
    Deposit pendingDeposit;
    Clear pendingClear;
    List<Clear> clears = new ArrayList<>();
    Craft pendingCraft;
  }
}

