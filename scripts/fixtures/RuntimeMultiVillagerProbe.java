package dev.ahmetprobe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.ahmet.LocalEconomyLedger;
import dev.ahmet.ResourceLockRegistry;
import dev.ahmet.VillagerRole;
import net.citizensnpcs.api.event.NPCTeleportEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/**
 * Opt-in disposable runtime probe for M7-C Multi-Villager verification.
 * Never install on a user or live server.
 */
public final class RuntimeMultiVillagerProbe extends JavaPlugin implements Listener {
  private static final int GLOBAL_TICK_BUDGET = 3600;
  private static final UUID OPERATOR_ID = UUID.fromString("36efc754-6a02-4ed3-9a42-46e2a8f70001");

  private record Wait(String name, int budgetTicks, BooleanSupplier condition, Runnable success, int[] elapsed) { }

  private Plugin ahmet;
  private World world;
  private Location chestLocation;
  private PlayerProxy operator;

  private Object slotAhmet;
  private Object slotMehmet;
  private NPC npcAhmet;
  private NPC npcMehmet;
  private Object taskAhmet;
  private Object taskMehmet;
  private Object journalAhmet;
  private Object journalMehmet;

  private final List<int[]> ownChunkTickets = new ArrayList<>();
  private final List<BukkitTask> tasks = new ArrayList<>();
  private final List<String> eventBodies = Collections.synchronizedList(new ArrayList<>());
  private HttpServer mockBrain;
  private ExecutorService mockExecutor;
  private String bridgeToken;
  private BukkitTask ticker;
  private Wait wait;

  private String stage = "startup";
  private boolean terminal;
  private int globalTicks;

  private boolean countNormalTeleports;
  private int normalTeleports;

  @Override
  public void onEnable() {
    if (!Boolean.getBoolean("ahmet.multiVillagerProbe")) {
      getLogger().warning("AHMET_MULTIVILLAGER_PROBE_DISABLED");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    Bukkit.getPluginManager().registerEvents(this, this);
    ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    tasks.add(ticker);
    tasks.add(Bukkit.getScheduler().runTaskLater(this,
        () -> fail(new IllegalStateException("global 3600-tick budget exhausted")), GLOBAL_TICK_BUDGET));
    tasks.add(Bukkit.getScheduler().runTaskLater(this, this::start, 40L));
  }

  @Override
  public void onDisable() {
    cleanup();
  }

  @EventHandler
  public void onNpcTeleport(NPCTeleportEvent event) {
    if (countNormalTeleports && event.getNPC() != null) {
      int id = event.getNPC().getId();
      if ((npcAhmet != null && id == npcAhmet.getId()) || (npcMehmet != null && id == npcMehmet.getId())) {
        normalTeleports++;
      }
    }
  }

  private void start() {
    try {
      ahmet = Bukkit.getPluginManager().getPlugin("VillagerAhmet");
      world = Bukkit.getWorld("world");
      if (world == null && !Bukkit.getWorlds().isEmpty()) {
        world = Bukkit.getWorlds().get(0);
      }
      require(ahmet != null && world != null, "required plugin or world missing");

      prepareArena();
      configureAhmetPlugin();

      operator = new PlayerProxy(OPERATOR_ID, true, new Location(world, 0.5, 64, 0.5));

      // 1. Spawn primary villager Ahmet
      command(operator.player, "ahmet spawn");
      Object savedNpc = field(ahmet, "npc");
      require(savedNpc instanceof NPC, "actual /ahmet spawn did not create Citizens NPC");
      npcAhmet = (NPC) savedNpc;
      require(npcAhmet.isSpawned() && npcAhmet.getEntity().getType() == EntityType.VILLAGER
          && npcAhmet.getEntity() instanceof Villager, "expected Ahmet to be a real Citizens EntityType.VILLAGER");

      // Bind Ahmet chest & farmplot
      operator.targetBlock = chestLocation.getBlock();
      command(operator.player, "ahmet chest");
      require(chestLocation.getBlock().getState() instanceof Chest, "fixture chest was not bound through command path");
      command(operator.player, "ahmet farmplot 0 64 -5 2");
      require(field(ahmet, "farmPlot") != null, "farm plot was not bound through command path for Ahmet");

      Location homeAhmet = new Location(world, 0.5, 64, 0.5);
      set(ahmet, "home", homeAhmet.clone());
      set(ahmet, "protectedHome", homeAhmet.clone());
      set(ahmet, "protectHomeRadius", 0);
      invoke(ahmet, "persist");

      // 2. Spawn secondary villager Mehmet
      command(operator.player, "ahmet npc mehmet spawn");

      @SuppressWarnings("unchecked")
      Map<String, Object> slots = (Map<String, Object>) field(ahmet, "villagerSlots");
      require(slots.containsKey("ahmet") && slots.containsKey("mehmet"), "villagerSlots must contain ahmet and mehmet");

      slotAhmet = slots.get("ahmet");
      slotMehmet = slots.get("mehmet");

      taskAhmet = field(slotAhmet, "task");
      journalAhmet = field(slotAhmet, "journal");

      taskMehmet = field(slotMehmet, "task");
      journalMehmet = field(slotMehmet, "journal");
      npcMehmet = (NPC) field(slotMehmet, "npc");
      require(npcMehmet != null && npcMehmet.isSpawned() && npcMehmet.getEntity().getType() == EntityType.VILLAGER
          && npcMehmet.getEntity() instanceof Villager, "expected Mehmet to be a real Citizens EntityType.VILLAGER");

      // Bind Mehmet chest & farmplot
      operator.targetBlock = chestLocation.getBlock();
      command(operator.player, "ahmet npc mehmet chest");
      command(operator.player, "ahmet npc mehmet farmplot 0 64 -5 2");

      Location homeMehmet = new Location(world, 2.5, 64, 0.5);
      set(slotMehmet, "home", homeMehmet.clone());
      invoke(ahmet, "persist");

      startMockBrain();

      // Begin normal teleport monitoring
      countNormalTeleports = true;
      normalTeleports = 0;

      awaitTicks(5, this::startStage1);
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void prepareArena() {
    world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);
    for (int chunkX = -1; chunkX <= 0; chunkX++) {
      for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
        var chunk = world.getChunkAt(chunkX, chunkZ);
        chunk.load(true);
        if (world.addPluginChunkTicket(chunkX, chunkZ, this)) {
          ownChunkTickets.add(new int[]{chunkX, chunkZ});
        }
      }
    }

    for (int x = -12; x <= 12; x++) {
      for (int z = -12; z <= 12; z++) {
        for (int y = 50; y <= 75; y++) {
          Block block = world.getBlockAt(x, y, z);
          if (!block.getType().isAir()) block.setType(Material.AIR, false);
        }
        world.getBlockAt(x, 63, z).setType(Material.SMOOTH_STONE, false);
      }
    }

    setupDoubleChest();
    setupFarmland();
  }

  private void setupDoubleChest() {
    chestLocation = new Location(world, 6, 64, 0);
    Block b0 = world.getBlockAt(6, 64, 0);
    Block b1 = world.getBlockAt(7, 64, 0);
    b0.setType(Material.CHEST, false);
    b1.setType(Material.CHEST, false);

    org.bukkit.block.data.type.Chest c0 = (org.bukkit.block.data.type.Chest) b0.getBlockData();
    org.bukkit.block.data.type.Chest c1 = (org.bukkit.block.data.type.Chest) b1.getBlockData();
    c0.setFacing(BlockFace.SOUTH);
    c1.setFacing(BlockFace.SOUTH);

    c0.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
    c1.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
    b0.setBlockData(c0, false);
    b1.setBlockData(c1, false);

    Chest state = (Chest) b0.getState();
    if (state.getInventory().getSize() != 54) {
      c0.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
      c1.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
      b0.setBlockData(c0, false);
      b1.setBlockData(c1, false);
      state = (Chest) b0.getState();
    }
    require(state.getInventory().getSize() == 54, "double chest inventory size must be 54");
  }

  private void setupFarmland() {
    Block water = world.getBlockAt(0, 63, -7);
    water.setType(Material.WATER, false);
    for (int fx = -1; fx <= 1; fx++) {
      for (int fz = -6; fz <= -4; fz++) {
        Block b = world.getBlockAt(fx, 63, fz);
        b.setType(Material.FARMLAND, false);
        Farmland fl = (Farmland) b.getBlockData();
        fl.setMoisture(fl.getMaximumMoisture());
        b.setBlockData(fl, false);
      }
    }
  }

  private void configureAhmetPlugin() {
    var config = ahmet.getConfig();
    config.set("options.schedule-enabled", false);
    config.set("options.production-goals-enabled", false);
    config.set("options.search-radius", 24);
    config.set("options.max-distance-from-home", 32);
    config.set("options.protect-home-radius", 0);
    config.set("perception.enabled", false);
    config.set("perception.include-bound-chest", false);
    config.set("perception.include-players", false);
    set(ahmet, "returningHome", false);
    set(ahmet, "recoveryBlocked", false);
  }

  private Inventory doubleChestInventory() {
    Chest chest = (Chest) chestLocation.getBlock().getState();
    Inventory inv = chest.getInventory();
    require(inv.getSize() == 54, "double chest inventory size must be 54");
    return inv;
  }

  private static int countMaterial(Inventory inv, Material mat) {
    int count = 0;
    for (ItemStack is : inv.getContents()) {
      if (is != null && is.getType() == mat) {
        count += is.getAmount();
      }
    }
    return count;
  }

  private void setCrop(int x, int y, int z, Material material, int age) {
    Block block = world.getBlockAt(x, y, z);
    block.setType(material, false);
    if (block.getBlockData() instanceof Ageable ageable) {
      ageable.setAge(age);
      block.setBlockData(ageable, false);
    }
  }

  private void clearPlotCrops() {
    for (int fx = -1; fx <= 1; fx++) {
      for (int fz = -6; fz <= -4; fz++) {
        Block b = world.getBlockAt(fx, 64, fz);
        if (!b.getType().isAir()) {
          b.setType(Material.AIR, false);
        }
      }
    }
  }

  // ==================== STAGE 1: stage1_role_refusals ====================
  private void startStage1() {
    stage = "stage1_role_refusals";
    try {
      // 1. Assign roles
      command(operator.player, "ahmet role worker");
      command(operator.player, "ahmet npc mehmet role farmer");

      VillagerRole roleAhmet = (VillagerRole) field(slotAhmet, "role");
      VillagerRole roleMehmet = (VillagerRole) field(slotMehmet, "role");
      require(roleAhmet == VillagerRole.WORKER, "Ahmet role must be WORKER, actual=" + roleAhmet);
      require(roleMehmet == VillagerRole.FARMER, "Mehmet role must be FARMER, actual=" + roleMehmet);

      // 2. Test illegal commands on Ahmet (WORKER can only GATHER)
      operator.messages.clear();
      command(operator.player, "ahmet farm wheat 1");
      require(!bool(taskAhmet, "active"), "Ahmet farm task must not be admitted for WORKER");
      require(operator.hasMessageContaining("yapamaz"), "Ahmet farm rejection message expected");

      operator.messages.clear();
      command(operator.player, "ahmet craft oak_planks 4");
      require(!bool(taskAhmet, "active"), "Ahmet craft task must not be admitted for WORKER");
      require(operator.hasMessageContaining("yapamaz"), "Ahmet craft rejection message expected");

      // 3. Test illegal commands on Mehmet (FARMER can only FARM)
      operator.messages.clear();
      command(operator.player, "ahmet npc mehmet collect oak_log 1");
      require(!bool(taskMehmet, "active"), "Mehmet collect task must not be admitted for FARMER");
      require(operator.hasMessageContaining("yapamaz"), "Mehmet collect rejection message expected");

      operator.messages.clear();
      command(operator.player, "ahmet npc mehmet craft oak_planks 4");
      require(!bool(taskMehmet, "active"), "Mehmet craft task must not be admitted for FARMER");
      require(operator.hasMessageContaining("yapamaz"), "Mehmet craft rejection message expected");

      require(normalTeleports == 0, "normalTeleports must be 0 in stage1");
      getLogger().info("[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage1_role_refusals teleports=0");

      awaitTicks(5, this::startStage2);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 2: stage2_task_delegation ====================
  private void startStage2() {
    stage = "stage2_task_delegation";
    try {
      Inventory inv = doubleChestInventory();
      inv.clear();
      clearPlotCrops();

      // Place oak tree for gather delegation
      Block oakLog = world.getBlockAt(-4, 64, 0);
      oakLog.setType(Material.OAK_LOG, false);
      Block leavesBlock = world.getBlockAt(-4, 65, 0);
      leavesBlock.setType(Material.OAK_LEAVES, false);
      if (leavesBlock.getBlockData() instanceof Leaves leavesData) {
        leavesData.setPersistent(false);
        leavesBlock.setBlockData(leavesData, false);
      }

      // Place mature wheat for farm delegation
      setCrop(0, 64, -5, Material.WHEAT, 7);

      invoke(ahmet, "withVillagerContext", new Class<?>[]{String.class, Runnable.class}, "ahmet",
          (Runnable) () -> invoke(ahmet, "invalidateTargetScan"));
      invoke(ahmet, "withVillagerContext", new Class<?>[]{String.class, Runnable.class}, "mehmet",
          (Runnable) () -> invoke(ahmet, "invalidateTargetScan"));

      // 1. Delegate collect -> should select Ahmet (specialist WORKER)
      operator.messages.clear();
      command(operator.player, "ahmet delegate collect oak_log 1");
      require(operator.hasMessageContaining("Ahmet") && operator.hasMessageContaining("delege edildi"),
          "Task collect must be delegated to Ahmet");
      require(bool(taskAhmet, "active"), "Ahmet task must be active after delegation");
      require(!bool(taskMehmet, "active"), "Mehmet task must remain inactive during Ahmet delegation");

      waitFor("stage2_ahmet_collect", 600,
          () -> !bool(taskAhmet, "active") && integer(taskAhmet, "cargo") == 0 && countMaterial(inv, Material.OAK_LOG) == 1,
          () -> {
            try {
              require(normalTeleports == 0, "normalTeleports must be 0 after Ahmet collect");

              // 2. Delegate farm -> should select Mehmet (specialist FARMER)
              operator.messages.clear();
              command(operator.player, "ahmet delegate farm wheat 1");
              require(operator.hasMessageContaining("Mehmet") && operator.hasMessageContaining("delege edildi"),
                  "Task farm must be delegated to Mehmet");
              require(bool(taskMehmet, "active"), "Mehmet task must be active after delegation");
              require(!bool(taskAhmet, "active"), "Ahmet task must remain inactive during Mehmet delegation");

              waitFor("stage2_mehmet_farm", 600,
                  () -> !bool(taskMehmet, "active") && integer(taskMehmet, "cargo") == 0 && countMaterial(inv, Material.WHEAT) >= 1,
                  this::verifyStage2);
            } catch (Throwable error) {
              fail(error);
            }
          });
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void verifyStage2() {
    try {
      Block bWheat = world.getBlockAt(0, 64, -5);
      require(bWheat.getType() == Material.WHEAT && ((Ageable) bWheat.getBlockData()).getAge() == 0,
          "(0, 64, -5) must be replanted WHEAT age=0");
      Inventory inv = doubleChestInventory();
      require(countMaterial(inv, Material.OAK_LOG) == 1, "Chest must contain OAK_LOG x1");
      require(countMaterial(inv, Material.WHEAT) >= 1, "Chest must contain WHEAT >= 1");
      require(normalTeleports == 0, "normalTeleports must be 0 in stage2");

      getLogger().info("[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage2_task_delegation teleports=0");
      awaitTicks(5, this::startStage3);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 3: stage3_concurrent_execution_and_block_locks ====================
  private void startStage3() {
    stage = "stage3_concurrent_execution_and_block_locks";
    try {
      // Place oak tree for Ahmet
      Block oakLog = world.getBlockAt(-4, 64, 2);
      oakLog.setType(Material.OAK_LOG, false);
      Block leavesBlock = world.getBlockAt(-4, 65, 2);
      leavesBlock.setType(Material.OAK_LEAVES, false);
      if (leavesBlock.getBlockData() instanceof Leaves leavesData) {
        leavesData.setPersistent(false);
        leavesBlock.setBlockData(leavesData, false);
      }

      // Place mature carrot for Mehmet
      setCrop(1, 64, -5, Material.CARROTS, 7);

      invoke(ahmet, "withVillagerContext", new Class<?>[]{String.class, Runnable.class}, "ahmet",
          (Runnable) () -> invoke(ahmet, "invalidateTargetScan"));
      invoke(ahmet, "withVillagerContext", new Class<?>[]{String.class, Runnable.class}, "mehmet",
          (Runnable) () -> invoke(ahmet, "invalidateTargetScan"));

      // Start Ahmet task
      command(operator.player, "ahmet collect oak_log 1");
      require(bool(taskAhmet, "active"), "Ahmet collect task must be active");

      // Start Mehmet task concurrently
      command(operator.player, "ahmet npc mehmet farm carrot 1");
      require(bool(taskMehmet, "active"), "Mehmet farm task must be active");

      // Verify concurrent execution
      require(bool(taskAhmet, "active") && bool(taskMehmet, "active"),
          "Both Ahmet and Mehmet tasks must execute concurrently");

      // Assert ResourceLockRegistry prevents conflicting block locks
      ResourceLockRegistry locks = (ResourceLockRegistry) field(ahmet, "resourceLocks");
      require(locks != null, "resourceLocks must not be null");

      // Test resource lock isolation on arbitrary arena block
      boolean ahmetLocked = locks.tryAcquireTargetBlock("ahmet", "probe-task", world.getName(), 3, 64, 3);
      require(ahmetLocked, "Ahmet should acquire probe test lock");
      boolean mehmetBlocked = !locks.tryAcquireTargetBlock("mehmet", "probe-task-2", world.getName(), 3, 64, 3);
      require(mehmetBlocked, "Mehmet must be blocked from acquiring Ahmet's locked block");
      require(locks.isBlockLockedByOther("mehmet", world.getName(), 3, 64, 3), "Block must be locked by other for Mehmet");
      require(!locks.isBlockLockedByOther("ahmet", world.getName(), 3, 64, 3), "Block must NOT be locked by other for Ahmet");

      locks.releaseBlock("ahmet", world.getName(), 3, 64, 3);
      require(!locks.isBlockLockedByOther("mehmet", world.getName(), 3, 64, 3), "Block must be unlocked after release");
      boolean mehmetAcquired = locks.tryAcquireTargetBlock("mehmet", "probe-task-2", world.getName(), 3, 64, 3);
      require(mehmetAcquired, "Mehmet should now acquire block");
      locks.releaseBlock("mehmet", world.getName(), 3, 64, 3);

      // Wait for both concurrent tasks to complete cleanly
      waitFor("stage3_concurrent_wait", 800,
          () -> !bool(taskAhmet, "active") && !bool(taskMehmet, "active")
              && integer(taskAhmet, "cargo") == 0 && integer(taskMehmet, "cargo") == 0,
          this::verifyStage3);
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void verifyStage3() {
    try {
      Block bCarrot = world.getBlockAt(1, 64, -5);
      require(bCarrot.getType() == Material.CARROTS && ((Ageable) bCarrot.getBlockData()).getAge() == 0,
          "(1, 64, -5) must be replanted CARROTS age=0");
      Inventory inv = doubleChestInventory();
      require(countMaterial(inv, Material.OAK_LOG) == 2, "Chest must now contain OAK_LOG x2");
      require(countMaterial(inv, Material.CARROT) >= 1, "Chest must contain CARROT >= 1");
      require(normalTeleports == 0, "normalTeleports must be 0 in stage3");

      getLogger().info("[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage3_concurrent_execution_and_block_locks teleports=0");
      awaitTicks(5, this::startStage4);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 4: stage4_chest_craft_lock_double_spend ====================
  private void startStage4() {
    stage = "stage4_chest_craft_lock_double_spend";
    try {
      // Set roles to CRAFTER so both can craft
      command(operator.player, "ahmet role crafter");
      command(operator.player, "ahmet npc mehmet role crafter");

      Inventory inv = doubleChestInventory();
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 1));
      inv.setItem(1, new ItemStack(Material.OAK_LOG, 1));

      // Mehmet begins craft task (which acquires the chest lock)
      command(operator.player, "ahmet npc mehmet craft oak_planks 4");
      require(bool(taskMehmet, "active"), "Mehmet craft task must be active");

      ResourceLockRegistry locks = (ResourceLockRegistry) field(ahmet, "resourceLocks");
      @SuppressWarnings("unchecked")
      List<ResourceLockRegistry.CoordKey> chestCoords = (List<ResourceLockRegistry.CoordKey>)
          invoke(ahmet, "chestCoordsFor", new Class<?>[]{Location.class}, chestLocation);

      require(locks.isChestLockedByOther("ahmet", chestCoords), "Chest must be locked for Ahmet while Mehmet crafts");

      // Ahmet attempts simultaneous craft from the same chest
      operator.messages.clear();
      command(operator.player, "ahmet craft oak_planks 4");
      require(!bool(taskAhmet, "active"), "Ahmet craft must be rejected due to CHEST_LOCKED");
      require(operator.hasMessageContaining("başka bir köylünün görevinde kilitli"),
          "Operator must receive CHEST_LOCKED refusal message");

      // Wait for Mehmet to finish crafting and deposit
      waitFor("stage4_mehmet_craft", 400,
          () -> !bool(taskMehmet, "active") && countMaterial(inv, Material.OAK_PLANKS) == 4,
          () -> {
            try {
              require(countMaterial(inv, Material.OAK_PLANKS) == 4, "Double chest must have OAK_PLANKS x4");
              require(countMaterial(inv, Material.OAK_LOG) == 1, "Double chest must preserve remaining OAK_LOG x1");
              require(!locks.isChestLockedByOther("ahmet", chestCoords), "Chest lock must be released after Mehmet finishes");
              require(normalTeleports == 0, "normalTeleports must be 0 in stage4");

              getLogger().info("[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage4_chest_craft_lock_double_spend teleports=0");
              awaitTicks(5, this::startStage5);
            } catch (Throwable error) {
              fail(error);
            }
          });
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 5: stage5_cross_npc_journal_recovery ====================
  private void startStage5() {
    stage = "stage5_cross_npc_journal_recovery";
    try {
      // 1. Ensure Ahmet's journal is clean
      int ahmetCollectedBefore = integer(taskAhmet, "collected");
      invoke(journalAhmet, "clearAfterWorldSave");
      require(!bool(journalAhmet, "hasPendingOperation") && !bool(journalAhmet, "hasOperations"),
          "Ahmet journal must be completely clear");
      require(!bool(ahmet, "recoveryBlocked"), "Ahmet must not be recoveryBlocked");

      // 2. Set crop at (0, 64, -5) to WHEAT age 0 (harvested & replanted before crash)
      setCrop(0, 64, -5, Material.WHEAT, 0);

      // 3. Start Mehmet farm task and prepare harvest op in operations-mehmet.json
      invoke(taskMehmet, "startFarm", new Class<?>[]{UUID.class, int.class, String.class}, OPERATOR_ID, 1, "crop:WHEAT");
      String taskIdMehmet = string(taskMehmet, "taskId");

      Class<?> harvestClass = Class.forName("dev.ahmet.OperationJournal$Harvest");
      Method forCrop = harvestClass.getDeclaredMethod("forCrop",
          String.class, String.class, int.class, int.class, int.class, int.class, int.class, String.class, String.class, int.class);
      forCrop.setAccessible(true);
      Object harvestOp = forCrop.invoke(null, taskIdMehmet, world.getName(), 0, 64, -5, 0, 0, "WHEAT", "WHEAT", 0);

      Method prepHarvest = journalMehmet.getClass().getDeclaredMethod("prepareHarvest", harvestClass);
      prepHarvest.setAccessible(true);
      prepHarvest.invoke(journalMehmet, harvestOp);

      require(bool(journalMehmet, "hasPendingOperation"), "Mehmet journal must have pending operation");
      require(!bool(journalAhmet, "hasPendingOperation") && !bool(journalAhmet, "hasOperations"),
          "Ahmet journal must not have pending operation");

      // 4. Run journal recovery specifically for Mehmet
      invoke(ahmet, "withVillagerContext", new Class<?>[]{String.class, Runnable.class}, "mehmet",
          (Runnable) () -> invoke(ahmet, "recoverJournal"));

      // 5. Assert recovery outcomes
      require(!bool(journalMehmet, "hasPendingOperation"), "Mehmet pending harvest must be cleared after APPLY");
      require(integer(taskMehmet, "collected") == 1, "Mehmet task.collected must be 1 after APPLY");
      require(integer(taskMehmet, "cargo") == 1, "Mehmet task.cargo must be 1 after APPLY");
      require(!bool(slotMehmet, "recoveryBlocked"), "Mehmet recoveryBlocked must be false after APPLY");

      // Ahmet must remain completely clean and unblocked
      require(!bool(journalAhmet, "hasPendingOperation"), "Ahmet journal must remain clean");
      require(!bool(journalAhmet, "hasOperations"), "Ahmet journal must remain empty");
      require(!bool(ahmet, "recoveryBlocked"), "Ahmet recoveryBlocked must remain false");
      require(integer(taskAhmet, "collected") == ahmetCollectedBefore, "Ahmet collected must not change during Mehmet recovery");
      require(!bool(taskAhmet, "active"), "Ahmet task must remain inactive");
      require(integer(taskAhmet, "cargo") == 0, "Ahmet cargo must remain 0");

      // Clean up Mehmet state
      invoke(taskMehmet, "cancel");
      set(taskMehmet, "cargo", 0);
      ((Map<?, ?>) field(taskMehmet, "cargoItems")).clear();
      invoke(journalMehmet, "clearAfterWorldSave");
      invoke(ahmet, "withVillagerContext", new Class<?>[]{String.class, Runnable.class}, "mehmet",
          (Runnable) () -> invoke(ahmet, "persist"));

      require(normalTeleports == 0, "normalTeleports must be 0 in stage5");
      getLogger().info("[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage5_cross_npc_journal_recovery teleports=0");

      awaitTicks(5, this::startStage6);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 6: stage6_local_economy_ledger_readback ====================
  private void startStage6() {
    stage = "stage6_local_economy_ledger_readback";
    try {
      LocalEconomyLedger ledger = (LocalEconomyLedger) field(ahmet, "economyLedger");
      require(ledger != null, "economyLedger must not be null");

      // 1. Assert credit accounting per villager
      LocalEconomyLedger.VillagerSummary sAhmet = ledger.villagerSummary("ahmet");
      require(sAhmet.gatheredItems() >= 1, "Ahmet gatheredItems must be >= 1, actual=" + sAhmet.gatheredItems());
      require(sAhmet.credits() >= 1, "Ahmet credits must be >= 1, actual=" + sAhmet.credits());
      require(sAhmet.tasksContributed() >= 1, "Ahmet tasksContributed must be >= 1, actual=" + sAhmet.tasksContributed());

      LocalEconomyLedger.VillagerSummary sMehmet = ledger.villagerSummary("mehmet");
      require(sMehmet.farmedItems() >= 1, "Mehmet farmedItems must be >= 1, actual=" + sMehmet.farmedItems());
      require(sMehmet.craftedItems() >= 1, "Mehmet craftedItems must be >= 1, actual=" + sMehmet.craftedItems());
      require(sMehmet.credits() >= 1, "Mehmet credits must be >= 1, actual=" + sMehmet.credits());
      require(sMehmet.tasksContributed() >= 2, "Mehmet tasksContributed must be >= 2, actual=" + sMehmet.tasksContributed());

      // 2. Assert net village inventory balances match produced - consumed
      Map<String, Integer> net = ledger.netBalances();
      Map<String, Integer> prod = ledger.producedTotals();
      Map<String, Integer> cons = ledger.consumedTotals();
      for (String item : prod.keySet()) {
        int n = net.getOrDefault(item, 0);
        int p = prod.getOrDefault(item, 0);
        int c = cons.getOrDefault(item, 0);
        require(n == p - c, "Net balance mismatch for " + item + ": net=" + n + " prod=" + p + " cons=" + c);
      }

      // 3. Command readback: /ahmet ledger
      operator.messages.clear();
      command(operator.player, "ahmet ledger");
      require(operator.hasMessageContaining("Köy Ekonomisi")
              || (operator.hasMessageContaining("ahmet") && operator.hasMessageContaining("mehmet")),
          "Operator must receive /ahmet ledger summary message");

      require(normalTeleports == 0, "normalTeleports must be 0 in stage6");

      getLogger().info("[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage6_local_economy_ledger_readback teleports=0");
      getLogger().info("[MultiVillagerProbe] AHMET_MULTIVILLAGER_RUNTIME_PROBE_PASS stages=6 normalTeleports=0");

      complete();
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== ENGINE & TICK LOOP ====================
  private void tick() {
    if (terminal) return;
    globalTicks++;

    Wait active = wait;
    if (active == null) return;
    try {
      if (active.condition().getAsBoolean()) {
        if (wait == active) wait = null;
        active.success().run();
      } else if (active.elapsed()[0]++ >= active.budgetTicks()) {
        fail(new IllegalStateException("bounded wait expired: " + active.name()));
      }
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void waitFor(String name, int budgetTicks, BooleanSupplier condition, Runnable success) {
    require(wait == null, "attempted to overlap bounded waits");
    stage = name;
    wait = new Wait(name, budgetTicks, condition, success, new int[]{0});
  }

  private void awaitTicks(int count, Runnable next) {
    int target = globalTicks + count;
    waitFor("bounded-tick-delay", count + 2, () -> globalTicks >= target, next);
  }

  private void command(Player sender, String line) {
    String[] tokens = line.trim().split("\\s+");
    PluginCommand command = ((JavaPlugin) ahmet).getCommand(tokens[0]);
    require(command != null, "missing plugin command registration: " + tokens[0]);
    command.execute((CommandSender) sender, tokens[0], Arrays.copyOfRange(tokens, 1, tokens.length));
  }

  private void fail(Throwable error) {
    if (terminal) return;
    terminal = true;
    getLogger().severe("[MultiVillagerProbe] AHMET_MULTIVILLAGER_RUNTIME_PROBE_FAIL stage=" + stage
        + " error=" + error.getMessage() + " globalTicks=" + globalTicks);
    error.printStackTrace();
    cleanup();
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private void complete() {
    if (terminal) return;
    terminal = true;
    cleanup();
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private void cleanup() {
    if (ticker != null) ticker.cancel();
    for (BukkitTask t : tasks) if (t != null) t.cancel();
    tasks.clear();
    try { if (mockBrain != null) mockBrain.stop(0); } catch (Exception ignored) { }
    try { if (mockExecutor != null) mockExecutor.shutdownNow(); } catch (Exception ignored) { }
    for (int[] ticket : ownChunkTickets) {
      try {
        world.removePluginChunkTicket(ticket[0], ticket[1], this);
      } catch (Exception ignored) { }
    }
    ownChunkTickets.clear();
  }

  private void startMockBrain() throws Exception {
    bridgeToken = ahmet.getConfig().getString("brain.token", "");
    require(!bridgeToken.isBlank() && !bridgeToken.equals("CHANGE_ME_TO_A_RANDOM_SECRET"),
        "disposable synthetic bridge token is missing");
    mockBrain = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
    int port = mockBrain.getAddress().getPort();
    ahmet.getConfig().set("brain.url", "http://127.0.0.1:" + port);
    ahmet.getConfig().set("brain.timeout-seconds", 4);
    mockExecutor = Executors.newFixedThreadPool(2);
    mockBrain.setExecutor(mockExecutor);
    mockBrain.createContext("/api/chat", exchange -> {
      try {
        respond(exchange, 200, "{\"reply\":\"Selam!\",\"cancel\":false,\"goHome\":false}");
      } catch (Exception ignored) { }
    });
    mockBrain.createContext("/api/decide", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        respond(exchange, 200, "{\"id\":\"target_a\",\"source\":\"heuristic\",\"model\":\"fixture\"}");
      } catch (Exception ignored) { }
    });
    mockBrain.createContext("/api/event", exchange -> {
      try {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        eventBodies.add(body);
        respond(exchange, 200, "{\"ok\":true}");
      } catch (Exception ignored) { }
    });
    mockBrain.start();
    getLogger().info("MOCK_BRAIN_STARTED port=" + port);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  // ==================== REFLECTION & UTILITY ====================
  private static Object invoke(Object target, String method) {
    try {
      Method m = findMethod(target, method);
      m.setAccessible(true);
      return m.invoke(target);
    } catch (Exception error) {
      throw new RuntimeException("reflection invoke failed: " + method, error);
    }
  }

  private static Object invoke(Object target, String method, Class<?>[] types, Object... args) {
    try {
      Method m = findMethod(target, method, types);
      m.setAccessible(true);
      return m.invoke(target, args);
    } catch (Exception error) {
      throw new RuntimeException("reflection invoke failed: " + method, error);
    }
  }

  private static Method findMethod(Object target, String name, Class<?>... types) {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Method m = type.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
      } catch (NoSuchMethodException ignored) { }
    }
    throw new RuntimeException("method not found: " + name);
  }

  private static Field findField(Object target, String name) {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) { }
    }
    throw new RuntimeException("field not found: " + name);
  }

  private static Object field(Object target, String name) {
    try {
      return findField(target, name).get(target);
    } catch (Exception error) {
      throw new RuntimeException("field read failed: " + name, error);
    }
  }

  private static void set(Object target, String name, Object value) {
    try {
      findField(target, name).set(target, value);
    } catch (Exception error) {
      throw new RuntimeException("field write failed: " + name, error);
    }
  }

  private static boolean bool(Object target, String name) {
    try {
      return (boolean) field(target, name);
    } catch (Exception ignored) {
      try {
        return (boolean) invoke(target, name);
      } catch (Exception error) {
        throw new RuntimeException("bool read failed: " + name, error);
      }
    }
  }

  private static int integer(Object target, String name) {
    return ((Number) field(target, name)).intValue();
  }

  private static String string(Object target, String name) {
    return String.valueOf(field(target, name));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static Object primitiveDefault(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0f;
    if (type == double.class) return 0d;
    return null;
  }

  // ==================== PLAYER PROXY ====================
  private final class PlayerProxy {
    final UUID id;
    final Player player;
    volatile Location location;
    boolean admin;
    Block targetBlock;
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    PlayerProxy(UUID id, boolean admin, Location location) {
      this.id = id;
      this.admin = admin;
      this.location = location.clone();
      this.player = (Player) Proxy.newProxyInstance(
          Player.class.getClassLoader(),
          new Class<?>[]{Player.class},
          handler()
      );
    }

    boolean hasMessageContaining(String text) {
      synchronized (messages) {
        for (String m : messages) {
          if (m != null && m.contains(text)) return true;
        }
      }
      return false;
    }

    private InvocationHandler handler() {
      return (proxy, method, args) -> switch (method.getName()) {
        case "getUniqueId" -> id;
        case "getWorld" -> location.getWorld();
        case "getLocation" -> location.clone();
        case "isOnline" -> true;
        case "isInvisible" -> false;
        case "getGameMode" -> GameMode.SURVIVAL;
        case "hasPermission" -> admin && "ahmet.admin".equals(args[0]);
        case "getName", "getDisplayName" -> "MultiVillagerOperator";
        case "canSee" -> true;
        case "getTargetBlockExact" -> targetBlock;
        case "sendMessage" -> {
          if (args != null && args.length > 0) {
            String msg = String.valueOf(args[0]);
            messages.add(msg);
            getLogger().info("PLAYER_MSG: " + msg);
          }
          yield null;
        }
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "M7 MultiVillager PlayerProxy";
        default -> primitiveDefault(method.getReturnType());
      };
    }
  }
}
