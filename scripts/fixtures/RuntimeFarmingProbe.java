package dev.ahmetprobe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import org.bukkit.event.player.PlayerTeleportEvent;
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
 * Opt-in disposable runtime probe for M6 Farming verification.
 * Never install on a user or live server.
 */
public final class RuntimeFarmingProbe extends JavaPlugin implements Listener {
  private static final int GLOBAL_TICK_BUDGET = 3600;
  private static final UUID OPERATOR_ID = UUID.fromString("36efc754-6a02-4ed3-9a42-46e2a8f70001");

  private record Wait(String name, int budgetTicks, BooleanSupplier condition, Runnable success, int[] elapsed) { }

  private Plugin ahmet;
  private Object task;
  private Object controller;
  private Object journal;
  private NPC npc;
  private World world;
  private Location chestLocation;
  private Location home;
  private PlayerProxy operator;

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
  private boolean monitoringMovement;
  private int normalTeleports;
  private Location previousPosition;
  private double totalDistanceWalked;
  private double maxStep;

  @Override
  public void onEnable() {
    if (!Boolean.getBoolean("ahmet.farmingProbe")) {
      getLogger().warning("AHMET_FARMING_PROBE_DISABLED");
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
    if (countNormalTeleports && npc != null && event.getNPC() != null && event.getNPC().getId() == npc.getId()) {
      normalTeleports++;
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

      task = field(ahmet, "task");
      controller = field(ahmet, "controller");
      journal = field(ahmet, "journal");

      prepareArena();
      configureAhmet();

      operator = new PlayerProxy(OPERATOR_ID, true, new Location(world, 0.5, 64, 0.5));
      command(operator.player, "ahmet spawn");

      Object savedNpc = field(ahmet, "npc");
      require(savedNpc instanceof NPC, "actual /ahmet spawn did not create Citizens NPC");
      npc = (NPC) savedNpc;
      require(npc.isSpawned() && npc.getEntity().getType() == EntityType.VILLAGER
          && npc.getEntity() instanceof Villager, "expected a real Citizens EntityType.VILLAGER");

      home = new Location(world, 0.5, 64, 0.5);
      set(ahmet, "home", home.clone());
      set(ahmet, "protectedHome", home.clone());
      set(ahmet, "protectHomeRadius", 0);
      set(ahmet, "chestLocation", chestLocation.clone());
      invoke(ahmet, "persist");

      operator.targetBlock = chestLocation.getBlock();
      command(operator.player, "ahmet chest");
      require(chestLocation.getBlock().getState() instanceof Chest, "fixture chest was not bound through command path");

      command(operator.player, "ahmet farmplot 0 64 -5 2");
      require(field(ahmet, "farmPlot") != null, "farm plot was not bound through command path");

      startMockBrain();

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

  private void configureAhmet() {
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
    for (ItemStack item : inv.getContents()) {
      if (item != null && item.getType() == mat) {
        count += item.getAmount();
      }
    }
    return count;
  }

  private void setCrop(int x, int y, int z, Material mat, int age) {
    Block b = world.getBlockAt(x, y, z);
    b.setType(mat, false);
    if (b.getBlockData() instanceof Ageable a) {
      a.setAge(age);
      b.setBlockData(a, false);
    }
  }

  private void clearPlotCrops() {
    for (int x = -3; x <= 3; x++) {
      for (int z = -8; z <= -2; z++) {
        Block b = world.getBlockAt(x, 64, z);
        if (!b.getType().isAir()) b.setType(Material.AIR, false);
      }
    }
  }

  private void repositionNpc(Location loc, Runnable onReady) {
    monitoringMovement = false;
    countNormalTeleports = false;
    set(ahmet, "returningHome", false);
    invoke(ahmet, "clearNavigation");
    npc.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
    previousPosition = loc.clone();
    awaitTicks(2, () -> {
      totalDistanceWalked = 0;
      maxStep = 0;
      countNormalTeleports = true;
      monitoringMovement = true;
      onReady.run();
    });
  }

  // ==================== STAGE 1: plot_and_wheat_harvest_replant ====================
  private void startStage1() {
    stage = "plot_and_wheat_harvest_replant";
    repositionNpc(new Location(world, 0.5, 64, 0.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();
      clearPlotCrops();

      // 2 mature wheat inside plot
      setCrop(-1, 64, -5, Material.WHEAT, 7);
      setCrop(0, 64, -5, Material.WHEAT, 7);
      // 1 immature wheat inside plot
      setCrop(1, 64, -5, Material.WHEAT, 3);
      // 1 mature beetroot inside plot
      setCrop(0, 64, -4, Material.BEETROOTS, 3);

      // 1 mature wheat on farmland outside plot
      Block outFarmland = world.getBlockAt(5, 63, -5);
      outFarmland.setType(Material.FARMLAND, false);
      Farmland fl = (Farmland) outFarmland.getBlockData();
      fl.setMoisture(fl.getMaximumMoisture());
      outFarmland.setBlockData(fl, false);
      setCrop(5, 64, -5, Material.WHEAT, 7);

      invoke(ahmet, "invalidateTargetScan");

      command(operator.player, "ahmet farm wheat 2");
      require(bool(task, "active"), "farm wheat task should be active");

      waitFor("plot_and_wheat_harvest_replant", 600,
          () -> !bool(task, "active") && integer(task, "cargo") == 0 && countMaterial(doubleChestInventory(), Material.WHEAT) == 2,
          this::verifyStage1);
    });
  }

  private void verifyStage1() {
    try {
      require(totalDistanceWalked >= 3.0, "totalDistanceWalked >= 3.0 required, actual=" + totalDistanceWalked);
      require(maxStep <= 0.22, "maxStep <= 0.22 required, actual=" + maxStep);
      require(normalTeleports == 0, "normalTeleports == 0 required, actual=" + normalTeleports);

      Block bNeg1 = world.getBlockAt(-1, 64, -5);
      Block b0 = world.getBlockAt(0, 64, -5);
      require(bNeg1.getType() == Material.WHEAT && ((Ageable) bNeg1.getBlockData()).getAge() == 0,
          "(-1, 64, -5) must be replanted WHEAT age=0");
      require(b0.getType() == Material.WHEAT && ((Ageable) b0.getBlockData()).getAge() == 0,
          "(0, 64, -5) must be replanted WHEAT age=0");

      Block bImmature = world.getBlockAt(1, 64, -5);
      require(bImmature.getType() == Material.WHEAT && ((Ageable) bImmature.getBlockData()).getAge() == 3,
          "(1, 64, -5) immature WHEAT must remain age=3");

      Block bBeetroot = world.getBlockAt(0, 64, -4);
      require(bBeetroot.getType() == Material.BEETROOTS && ((Ageable) bBeetroot.getBlockData()).getAge() == 3,
          "(0, 64, -4) BEETROOTS must be skipped and remain age=3");

      Block bOutside = world.getBlockAt(5, 64, -5);
      require(bOutside.getType() == Material.WHEAT && ((Ageable) bOutside.getBlockData()).getAge() == 7,
          "(5, 64, -5) out-of-plot WHEAT must be untouched and remain age=7");

      // Clean up outside block and beetroot
      bOutside.setType(Material.AIR, false);
      world.getBlockAt(5, 63, -5).setType(Material.SMOOTH_STONE, false);
      bBeetroot.setType(Material.AIR, false);

      getLogger().info("AHMET_FARMING_STAGE_PASS stage=plot_and_wheat_harvest_replant walked=" + rounded(totalDistanceWalked)
          + " maxStep=" + rounded(maxStep) + " teleports=0 wheat=2 replanted=2 immaturePreserved=true outOfPlotPreserved=true beetrootSkipped=true");

      awaitTicks(5, this::startStage2);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 2: carrot_and_potato_mixed_harvest ====================
  private void startStage2() {
    stage = "carrot_and_potato_mixed_harvest";
    repositionNpc(new Location(world, 0.5, 64, 0.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();
      clearPlotCrops();

      setCrop(-1, 64, -5, Material.CARROTS, 7);
      setCrop(1, 64, -5, Material.POTATOES, 7);
      invoke(ahmet, "invalidateTargetScan");

      command(operator.player, "ahmet farm ekin 2");
      require(bool(task, "active"), "farm ekin task should be active");

      waitFor("carrot_and_potato_mixed_harvest", 600,
          () -> !bool(task, "active") && integer(task, "cargo") == 0
              && countMaterial(doubleChestInventory(), Material.CARROT) == 1
              && countMaterial(doubleChestInventory(), Material.POTATO) == 1,
          this::verifyStage2);
    });
  }

  private void verifyStage2() {
    try {
      require(normalTeleports == 0, "normalTeleports == 0 required in stage 2");

      Block bCarrot = world.getBlockAt(-1, 64, -5);
      require(bCarrot.getType() == Material.CARROTS && ((Ageable) bCarrot.getBlockData()).getAge() == 0,
          "(-1, 64, -5) must be replanted CARROTS age=0");

      Block bPotato = world.getBlockAt(1, 64, -5);
      require(bPotato.getType() == Material.POTATOES && ((Ageable) bPotato.getBlockData()).getAge() == 0,
          "(1, 64, -5) must be replanted POTATOES age=0");

      Inventory inv = doubleChestInventory();
      require(countMaterial(inv, Material.POISONOUS_POTATO) == 0, "POISONOUS_POTATO must be 0");

      getLogger().info("AHMET_FARMING_STAGE_PASS stage=carrot_and_potato_mixed_harvest teleports=0 carrot=1 potato=1 poisonousPotato=0 replantedCarrot=true replantedPotato=true");

      awaitTicks(5, this::startStage3);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 3: chat_and_partial_delivery_quota ====================
  private void startStage3() {
    stage = "chat_and_partial_delivery_quota";
    repositionNpc(new Location(world, 0.5, 64, 0.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();
      clearPlotCrops();
      eventBodies.clear();

      setCrop(0, 64, -5, Material.WHEAT, 7);
      setCrop(-1, 64, -5, Material.WHEAT, 2);
      invoke(ahmet, "invalidateTargetScan");

      command(operator.player, "ahmet ask tarladan 3 buğday hasat et");

      waitFor("chat_partial_delivery", 600,
          () -> hasEventType("task_started") && !bool(task, "active") && integer(task, "cargo") == 0
              && countMaterial(doubleChestInventory(), Material.WHEAT) == 1 && hasEventType("task_delivered"),
          this::verifyStage3);
    });
  }

  private void verifyStage3() {
    try {
      require(normalTeleports == 0, "normalTeleports == 0 required in stage 3");

      Block b0 = world.getBlockAt(0, 64, -5);
      require(b0.getType() == Material.WHEAT && ((Ageable) b0.getBlockData()).getAge() == 0,
          "(0, 64, -5) must be replanted WHEAT age=0");

      Block bImmature = world.getBlockAt(-1, 64, -5);
      require(bImmature.getType() == Material.WHEAT && ((Ageable) bImmature.getBlockData()).getAge() == 2,
          "(-1, 64, -5) immature WHEAT must remain age=2");

      require(integer(task, "deposited") == 1, "task.deposited must be 1 on partial delivery");

      getLogger().info("AHMET_FARMING_STAGE_PASS stage=chat_and_partial_delivery_quota teleports=0 requested=3 harvested=1 deposited=1 immaturePreserved=true taskStartedEvent=true taskDeliveredEvent=true");

      awaitTicks(5, this::startStage4);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 4: refusals_cancel_and_full_chest_deliver ====================
  private void startStage4() {
    stage = "refusals_cancel_and_full_chest_deliver";
    try {
      // 1. Refusal: unbind plot then request farm
      command(operator.player, "ahmet farmplot 0");
      command(operator.player, "ahmet farm wheat 1");
      require(!bool(task, "active"), "farm wheat without plot must be refused");

      // Re-bind plot
      command(operator.player, "ahmet farmplot 0 64 -5 2");
      require(field(ahmet, "farmPlot") != null, "farmplot re-bind failed");

      // 2. Refusal: unsupported crop
      command(operator.player, "ahmet farm beetroot 2");
      require(!bool(task, "active"), "unsupported crop must be refused");

      // 3. Refusal: out-of-range amount
      command(operator.player, "ahmet farm wheat 65");
      require(!bool(task, "active"), "out-of-range farm amount must be refused");

      // 4. Full chest hold, cancel, and deliver
      Inventory inv = doubleChestInventory();
      inv.clear();
      for (int i = 0; i < 54; i++) {
        inv.setItem(i, new ItemStack(Material.COBBLESTONE, 64));
      }

      clearPlotCrops();
      setCrop(0, 64, -5, Material.CARROTS, 7);
      invoke(ahmet, "invalidateTargetScan");

      repositionNpc(new Location(world, 0.5, 64, 0.5), () -> {
        command(operator.player, "ahmet farm carrot 1");
        require(bool(task, "active"), "farm carrot 1 should be active");

        waitFor("full_chest_harvest_and_approach", 500,
            () -> integer(task, "collected") == 1 && integer(task, "cargo") == 1
                && npc.getEntity().getLocation().distanceSquared(chestLocation.clone().add(0.5, 0, 0.5)) <= 3.5 * 3.5,
            () -> {
              try {
                Block bCarrot = world.getBlockAt(0, 64, -5);
                require(bCarrot.getType() == Material.CARROTS && ((Ageable) bCarrot.getBlockData()).getAge() == 0,
                    "(0, 64, -5) must be replanted CARROTS age=0");
                require(integer(task, "cargo") == 1, "task.cargo must be 1 while waiting at full chest");
                require(integer(task, "deposited") == 0, "task.deposited must be 0 while chest is full");

                command(operator.player, "ahmet cancel");
                require(!bool(task, "active"), "task must be inactive after cancel");
                require(integer(task, "cargo") == 1, "task.cargo must remain 1 after cancel");

                // Free slot 53 and deliver
                inv.setItem(53, null);
                command(operator.player, "ahmet deliver");

                waitFor("deliver_remaining_cargo", 400,
                    () -> !bool(task, "active") && integer(task, "cargo") == 0 && countMaterial(inv, Material.CARROT) == 1,
                    () -> {
                      getLogger().info("AHMET_FARMING_STAGE_PASS stage=refusals_cancel_and_full_chest_deliver unboundPlotRefused=true unsupportedCropRefused=true outOfRangeRefused=true fullChestHold=true cancelWithCargo=true deliverSuccess=true");
                      awaitTicks(5, this::runStage5);
                    });
              } catch (Throwable error) {
                fail(error);
              }
            });
      });
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 5: crop_journal_recovery_and_conflict ====================
  private void runStage5() {
    stage = "crop_journal_recovery_and_conflict";
    try {
      Class<?> harvestClass = Class.forName("dev.ahmet.OperationJournal$Harvest");
      Method forCrop = harvestClass.getDeclaredMethod("forCrop",
          String.class, String.class, int.class, int.class, int.class, int.class, int.class, String.class, String.class, int.class);
      forCrop.setAccessible(true);

      Method prepHarvest = journal.getClass().getDeclaredMethod("prepareHarvest", harvestClass);
      prepHarvest.setAccessible(true);

      // ----- 5A: NOT_APPLIED -----
      clearPlotCrops();
      setCrop(0, 64, -5, Material.WHEAT, 7);

      invoke(task, "startFarm", new Class<?>[]{UUID.class, int.class, String.class}, OPERATOR_ID, 1, "crop:WHEAT");
      String taskIdA = string(task, "taskId");

      Object harvestOpA = forCrop.invoke(null, taskIdA, world.getName(), 0, 64, -5, 0, 0, "WHEAT", "WHEAT", 0);
      prepHarvest.invoke(journal, harvestOpA);

      reconcileStartupState();

      require(!bool(ahmet, "recoveryBlocked"), "recoveryBlocked must be false in 5A");
      require(!journalHasPendingOperation(), "journal must have no pending operations in 5A");
      require(integer(task, "collected") == 0, "task.collected must be 0 in 5A");
      require(integer(task, "cargo") == 0, "task.cargo must be 0 in 5A");

      invoke(task, "cancel");
      invoke(ahmet, "persist");

      // ----- 5B: APPLY -----
      clearPlotCrops();
      setCrop(0, 64, -5, Material.WHEAT, 0);

      invoke(task, "startFarm", new Class<?>[]{UUID.class, int.class, String.class}, OPERATOR_ID, 1, "crop:WHEAT");
      String taskIdB = string(task, "taskId");

      Object harvestOpB = forCrop.invoke(null, taskIdB, world.getName(), 0, 64, -5, 0, 0, "WHEAT", "WHEAT", 0);
      prepHarvest.invoke(journal, harvestOpB);

      reconcileStartupState();

      require(!bool(ahmet, "recoveryBlocked"), "recoveryBlocked must be false in 5B");
      require(!journalHasPendingOperation(), "journal must have no pending operations in 5B");
      require(integer(task, "collected") == 1, "task.collected must be 1 in 5B");
      require(integer(task, "cargo") == 1, "task.cargo must be 1 in 5B");

      // Reset state cleanly
      clearJournal();
      set(ahmet, "recoveryBlocked", false);
      invoke(task, "cancel");
      set(task, "cargo", 0);
      ((Map<?, ?>) field(task, "cargoItems")).clear();
      invoke(ahmet, "persist");

      // ----- 5C: CONFLICT -----
      clearPlotCrops();
      setCrop(0, 64, -5, Material.WHEAT, 4);

      invoke(task, "startFarm", new Class<?>[]{UUID.class, int.class, String.class}, OPERATOR_ID, 1, "crop:WHEAT");
      String taskIdC = string(task, "taskId");

      Object harvestOpC = forCrop.invoke(null, taskIdC, world.getName(), 0, 64, -5, 0, 0, "WHEAT", "WHEAT", 0);
      prepHarvest.invoke(journal, harvestOpC);

      reconcileStartupState();

      require(bool(ahmet, "recoveryBlocked"), "recoveryBlocked must be true in 5C");
      require(journalHasPendingOperation(), "journal must have pending operation in 5C");

      // Clean up test state
      clearJournal();
      set(ahmet, "recoveryBlocked", false);
      invoke(task, "cancel");
      set(task, "cargo", 0);
      ((Map<?, ?>) field(task, "cargoItems")).clear();
      invoke(ahmet, "persist");

      getLogger().info("AHMET_FARMING_STAGE_PASS stage=crop_journal_recovery_and_conflict notApplied=true apply=true conflictBlocked=true");

      awaitTicks(5, this::startStage6);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 6: gather_craft_farm_regression_chain ====================
  private void startStage6() {
    stage = "gather_craft_farm_regression_chain";
    repositionNpc(new Location(world, 0.5, 64, 0.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();
      clearPlotCrops();

      Block oakLog = world.getBlockAt(-4, 64, 0);
      oakLog.setType(Material.OAK_LOG, false);
      Block leavesBlock = world.getBlockAt(-4, 65, 0);
      leavesBlock.setType(Material.OAK_LEAVES, false);
      if (leavesBlock.getBlockData() instanceof Leaves leavesData) {
        leavesData.setPersistent(false);
        leavesBlock.setBlockData(leavesData, false);
      }

      setCrop(0, 64, -5, Material.POTATOES, 7);
      invoke(ahmet, "invalidateTargetScan");

      // 1. Gather oak log
      command(operator.player, "ahmet collect oak_log 1");
      require(bool(task, "active"), "gather oak_log task must be active");

      waitFor("gather_oak_log", 600,
          () -> !bool(task, "active") && integer(task, "cargo") == 0 && countMaterial(inv, Material.OAK_LOG) == 1,
          () -> {
            // 2. Craft planks
            command(operator.player, "ahmet craft oak_planks 4");
            require(bool(task, "active"), "craft oak_planks task must be active");

            waitFor("craft_oak_planks", 400,
                () -> !bool(task, "active") && countMaterial(inv, Material.OAK_PLANKS) == 4,
                () -> {
                  // 3. Farm potato
                  command(operator.player, "ahmet farm potato 1");
                  require(bool(task, "active"), "farm potato task must be active");

                  waitFor("farm_potato", 600,
                      () -> !bool(task, "active") && integer(task, "cargo") == 0 && countMaterial(inv, Material.POTATO) == 1,
                      () -> {
                        try {
                          Block bPotato = world.getBlockAt(0, 64, -5);
                          require(bPotato.getType() == Material.POTATOES && ((Ageable) bPotato.getBlockData()).getAge() == 0,
                              "(0, 64, -5) must be replanted POTATOES age=0");
                          require(normalTeleports == 0, "normalTeleports must remain 0 across gather+craft+farm chain");

                          getLogger().info("AHMET_FARMING_STAGE_PASS stage=gather_craft_farm_regression_chain gatheredOakLog=1 craftedOakPlanks=4 harvestedPotato=1 teleports=0");
                          getLogger().info("AHMET_FARMING_RUNTIME_PROBE_PASS stages=6 normalTeleports=0");
                          complete();
                        } catch (Throwable error) {
                          fail(error);
                        }
                      });
                });
          });
    });
  }

  // ==================== RECOVERY & JOURNAL HELPERS ====================
  private void reconcileStartupState() {
    try {
      Method m;
      try {
        m = findMethod(ahmet, "reconcileStartupState");
      } catch (Exception ignored) {
        m = findMethod(ahmet, "recoverJournal");
      }
      m.setAccessible(true);
      m.invoke(ahmet);
    } catch (Exception e) {
      throw new RuntimeException("failed to reconcile startup state", e);
    }
  }

  private boolean journalHasPendingOperation() {
    try {
      return (boolean) invoke(journal, "hasPendingOperation");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void clearJournal() {
    try {
      Method m;
      try {
        m = journal.getClass().getDeclaredMethod("clearAll");
      } catch (NoSuchMethodException e) {
        m = journal.getClass().getDeclaredMethod("clearAfterWorldSave");
      }
      m.setAccessible(true);
      m.invoke(journal);
    } catch (Exception e) {
      try {
        Method m2 = journal.getClass().getDeclaredMethod("discardPendingHarvest");
        m2.setAccessible(true);
        m2.invoke(journal);
      } catch (Exception ignored) { }
    }
  }

  // ==================== ENGINE & TICK LOOP ====================
  private void tick() {
    if (terminal) return;
    globalTicks++;
    sampleMovement();

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

  private void sampleMovement() {
    if (npc == null || !npc.isSpawned()) return;
    Location now = npc.getEntity().getLocation();
    if (previousPosition != null && previousPosition.getWorld().equals(now.getWorld())) {
      double step = previousPosition.distance(now);
      if (monitoringMovement) {
        totalDistanceWalked += step;
        maxStep = Math.max(maxStep, step);
      }
    }
    previousPosition = now.clone();
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
    getLogger().severe("AHMET_FARMING_RUNTIME_PROBE_FAIL stage=" + stage
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
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("hasat")) {
          respond(exchange, 200, "{\"reply\":\"Tamam, tarladan hasat ediyorum.\",\"task\":{\"type\":\"harvest_crop\",\"crop\":\"minecraft:wheat\",\"amount\":3},\"taskSource\":\"heuristic\",\"cancel\":false,\"goHome\":false}");
        } else {
          respond(exchange, 200, "{\"reply\":\"Selam!\",\"cancel\":false,\"goHome\":false}");
        }
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

  private boolean hasEventType(String eventType) {
    synchronized (eventBodies) {
      return eventBodies.stream().anyMatch(body -> body.contains("\"type\":\"" + eventType + "\""));
    }
  }

  private static void respond(HttpExchange exchange, int status, String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
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
    return (boolean) field(target, name);
  }

  private static int integer(Object target, String name) {
    return ((Number) field(target, name)).intValue();
  }

  private static String string(Object target, String name) {
    return String.valueOf(field(target, name));
  }

  private static String rounded(double value) {
    return String.format(java.util.Locale.ROOT, "%.2f", value);
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

    private InvocationHandler handler() {
      return (proxy, method, args) -> switch (method.getName()) {
        case "getUniqueId" -> id;
        case "getWorld" -> location.getWorld();
        case "getLocation" -> location.clone();
        case "isOnline" -> true;
        case "isInvisible" -> false;
        case "getGameMode" -> GameMode.SURVIVAL;
        case "hasPermission" -> admin && "ahmet.admin".equals(args[0]);
        case "getName", "getDisplayName" -> admin ? "FarmingOperator" : "SyntheticActor";
        case "canSee" -> true;
        case "getTargetBlockExact" -> targetBlock;
        case "sendMessage" -> {
          if (args != null && args.length > 0) {
            getLogger().info("PLAYER_MSG: " + Arrays.toString(args));
          }
          yield null;
        }
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "M6 Farming PlayerProxy";
        default -> primitiveDefault(method.getReturnType());
      };
    }
  }
}
