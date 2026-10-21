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
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/**
 * Opt-in disposable runtime probe for M5 Crafting verification.
 * Never install on a user or live server.
 */
public final class RuntimeCraftingProbe extends JavaPlugin implements Listener {
  private static final int GLOBAL_TICK_BUDGET = 2400;
  private static final UUID OPERATOR_ID = UUID.fromString("36efc754-6a02-4ed3-9a42-46e2a8f70001");
  private static final UUID ACTOR_ID = UUID.fromString("36efc754-6a02-4ed3-9a42-46e2a8f70002");

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
    if (!Boolean.getBoolean("ahmet.craftingProbe")) {
      getLogger().warning("AHMET_CRAFTING_PROBE_DISABLED");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    Bukkit.getPluginManager().registerEvents(this, this);
    ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    tasks.add(ticker);
    tasks.add(Bukkit.getScheduler().runTaskLater(this,
        () -> fail(new IllegalStateException("global 2400-tick budget exhausted")), GLOBAL_TICK_BUDGET));
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

      operator = new PlayerProxy(OPERATOR_ID, true, new Location(world, 0.5, 120, 0.5));
      command(operator.player, "ahmet spawn");

      Object savedNpc = field(ahmet, "npc");
      require(savedNpc instanceof NPC, "actual /ahmet spawn did not create Citizens NPC");
      npc = (NPC) savedNpc;
      require(npc.isSpawned() && npc.getEntity().getType() == EntityType.VILLAGER
          && npc.getEntity() instanceof Villager, "expected a real Citizens EntityType.VILLAGER");

      home = new Location(world, 0.5, 120, 0.5);
      set(ahmet, "home", home.clone());
      set(ahmet, "protectedHome", home.clone());
      set(ahmet, "protectHomeRadius", 0);
      set(ahmet, "chestLocation", chestLocation.clone());
      invoke(ahmet, "persist");

      operator.targetBlock = chestLocation.getBlock();
      command(operator.player, "ahmet chest");
      require(chestLocation.getBlock().getState() instanceof Chest, "fixture chest was not bound through command path");
      startMockBrain();

      awaitTicks(5, this::startStage1);
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void prepareArena() {
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
        for (int y = 110; y <= 128; y++) {
          Block block = world.getBlockAt(x, y, z);
          if (!block.getType().isAir()) block.setType(Material.AIR, false);
        }
        world.getBlockAt(x, 119, z).setType(Material.SMOOTH_STONE, false);
      }
    }

    setupDoubleChest();
  }

  private void setupDoubleChest() {
    chestLocation = new Location(world, 0, 120, -4);
    Block b0 = world.getBlockAt(0, 120, -4);
    Block b1 = world.getBlockAt(1, 120, -4);
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

  // ==================== STAGE 1: oak_planks_walk_craft ====================
  private void startStage1() {
    stage = "oak_planks_walk_craft";
    repositionNpc(new Location(world, 0.5, 120, 4.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 1));
      inv.setItem(30, new ItemStack(Material.STRIPPED_OAK_LOG, 2));
      inv.setItem(40, new ItemStack(Material.DIAMOND, 5));

      command(operator.player, "ahmet craft oak_planks 8");
      require(bool(task, "active"), "craft task should be active");

      waitFor("oak_planks_walk_craft", 400, () -> !bool(task, "active"), this::verifyStage1);
    });
  }

  private void verifyStage1() {
    try {
      require(totalDistanceWalked >= 3.0, "totalDistanceWalked >= 3.0 required, actual=" + totalDistanceWalked);
      require(maxStep <= 0.22, "maxStep <= 0.22 required, actual=" + maxStep);
      require(normalTeleports == 0, "normalTeleports == 0 required, actual=" + normalTeleports);
      require(integer(task, "collected") == 8, "task.collected == 8 required");
      require(integer(task, "deposited") == 8, "task.deposited == 8 required");
      require(integer(task, "cargo") == 0, "task.cargo == 0 required");

      Inventory inv = doubleChestInventory();
      ItemStack slot0 = inv.getItem(0);
      require(slot0 != null && slot0.getType() == Material.OAK_PLANKS && slot0.getAmount() == 8,
          "slot 0 must receive OAK_PLANKS x8 after OAK_LOG x1 is consumed");
      require(countMaterial(inv, Material.OAK_LOG) == 0, "OAK_LOG must be fully consumed");
      ItemStack slot30 = inv.getItem(30);
      require(slot30 != null && slot30.getType() == Material.STRIPPED_OAK_LOG && slot30.getAmount() == 1,
          "slot 30 must have STRIPPED_OAK_LOG x1");
      ItemStack slot40 = inv.getItem(40);
      require(slot40 != null && slot40.getType() == Material.DIAMOND && slot40.getAmount() == 5,
          "slot 40 must have DIAMOND x5");
      require(countMaterial(inv, Material.OAK_PLANKS) == 8, "total OAK_PLANKS must be 8");

      getLogger().info("AHMET_CRAFT_STAGE_PASS stage=oak_planks_walk_craft walked=" + rounded(totalDistanceWalked)
          + " maxStep=" + rounded(maxStep) + " teleports=0 planks=8 remainingStrippedOakLog=1 preservedDiamond=5");

      awaitTicks(5, this::startStage2);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 2: stick_walk_craft_custom_preserved ====================
  private void startStage2() {
    stage = "stick_walk_craft_custom_preserved";
    repositionNpc(new Location(world, 0.5, 120, 4.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();

      ItemStack heirloom = new ItemStack(Material.OAK_PLANKS, 1);
      ItemMeta meta = heirloom.getItemMeta();
      require(meta != null, "ItemMeta cannot be null");
      meta.setDisplayName("Heirloom Plank");
      heirloom.setItemMeta(meta);

      inv.setItem(2, heirloom);
      inv.setItem(5, new ItemStack(Material.OAK_PLANKS, 2));
      inv.setItem(35, new ItemStack(Material.BIRCH_PLANKS, 3));

      command(operator.player, "ahmet craft stick 8");
      require(bool(task, "active"), "craft stick task should be active");

      waitFor("stick_walk_craft_custom_preserved", 400, () -> !bool(task, "active"), this::verifyStage2);
    });
  }

  private void verifyStage2() {
    try {
      require(totalDistanceWalked >= 3.0, "totalDistanceWalked >= 3.0 required, actual=" + totalDistanceWalked);
      require(maxStep <= 0.22, "maxStep <= 0.22 required, actual=" + maxStep);
      require(normalTeleports == 0, "normalTeleports == 0 required, actual=" + normalTeleports);
      require(integer(task, "collected") == 8, "task.collected == 8 required");
      require(integer(task, "deposited") == 8, "task.deposited == 8 required");
      require(integer(task, "cargo") == 0, "task.cargo == 0 required");

      Inventory inv = doubleChestInventory();
      ItemStack slot2 = inv.getItem(2);
      require(slot2 != null && slot2.getType() == Material.OAK_PLANKS && slot2.getAmount() == 1
          && slot2.hasItemMeta() && "Heirloom Plank".equals(slot2.getItemMeta().getDisplayName()),
          "slot 2 must still have Heirloom Plank x1");
      ItemStack slot5 = inv.getItem(5);
      require(slot5 == null || slot5.getType().isAir() || slot5.getAmount() == 0, "slot 5 must be empty");
      ItemStack slot35 = inv.getItem(35);
      require(slot35 != null && slot35.getType() == Material.BIRCH_PLANKS && slot35.getAmount() == 1,
          "slot 35 must have BIRCH_PLANKS x1");
      require(countMaterial(inv, Material.STICK) == 8, "total STICK must be 8");

      getLogger().info("AHMET_CRAFT_STAGE_PASS stage=stick_walk_craft_custom_preserved walked=" + rounded(totalDistanceWalked)
          + " maxStep=" + rounded(maxStep) + " teleports=0 sticks=8 remainingBirchPlanks=1 customPlankPreserved=true");

      awaitTicks(5, this::startStage2B);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 2B: chat_craft_item_and_events ====================
  private void startStage2B() {
    stage = "chat_craft_item_and_events";
    repositionNpc(new Location(world, 0.5, 120, 4.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 2));
      eventBodies.clear();

      command(operator.player, "ahmet ask 8 meşe tahtası üret");

      waitFor("chat_craft_admitted", 100, () -> bool(task, "active"), () ->
          waitFor("chat_craft_completed", 400, () -> !bool(task, "active"), this::verifyStage2B));
    });
  }

  private void verifyStage2B() {
    try {
      require(totalDistanceWalked >= 3.0, "totalDistanceWalked >= 3.0 required in 2B, actual=" + totalDistanceWalked);
      require(maxStep <= 0.22, "maxStep <= 0.22 required in 2B, actual=" + maxStep);
      require(normalTeleports == 0, "normalTeleports == 0 required in 2B");
      require(integer(task, "collected") == 8 && integer(task, "deposited") == 8 && integer(task, "cargo") == 0,
          "chat craft counters must be 8/8/0");
      Inventory inv = doubleChestInventory();
      require(countMaterial(inv, Material.OAK_LOG) == 0 && countMaterial(inv, Material.OAK_PLANKS) == 8,
          "chat craft must convert 2 OAK_LOG into 8 OAK_PLANKS");
      awaitTicks(10, () -> {
        try {
          require(hasEventType("task_started") && hasEventType("task_delivered"),
              "chat craft must emit task_started and task_delivered events");
          getLogger().info("AHMET_CRAFT_STAGE_PASS stage=chat_craft_item_and_events walked=" + rounded(totalDistanceWalked)
              + " maxStep=" + rounded(maxStep) + " teleports=0 planks=8 taskStartedEvent=true taskDeliveredEvent=true");
          awaitTicks(5, this::startStage3);
        } catch (Throwable error) {
          fail(error);
        }
      });
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 3: refusals_and_arrival_shortage ====================
  private void startStage3() {
    stage = "refusals_and_arrival_shortage";
    try {
      Inventory inv = doubleChestInventory();
      eventBodies.clear();

      // 1. Non-batch count refusal
      inv.clear();
      command(operator.player, "ahmet craft oak_planks 6");
      require(!bool(task, "active"), "non-batch count craft must be refused");

      // 2. Insufficient inputs refusal
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 1));
      command(operator.player, "ahmet craft oak_planks 16");
      require(!bool(task, "active"), "insufficient input craft must be refused");
      ItemStack s0 = inv.getItem(0);
      require(s0 != null && s0.getType() == Material.OAK_LOG && s0.getAmount() == 1, "OAK_LOG x1 must remain in slot 0");

      // 3. Full chest output refusal
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 3));
      for (int i = 1; i <= 53; i++) {
        inv.setItem(i, new ItemStack(Material.COBBLESTONE, 64));
      }
      command(operator.player, "ahmet craft oak_planks 8");
      require(!bool(task, "active"), "full chest output craft must be refused");
      ItemStack s0Full = inv.getItem(0);
      require(s0Full != null && s0Full.getType() == Material.OAK_LOG && s0Full.getAmount() == 3,
          "OAK_LOG x3 must remain in slot 0");

      // 4. Owner conflict refusal and operator cancel while walking
      repositionNpc(new Location(world, 0.5, 120, 4.5), () -> {
        inv.clear();
        inv.setItem(0, new ItemStack(Material.OAK_LOG, 2));
        PlayerProxy nonOwner = new PlayerProxy(ACTOR_ID, false, new Location(world, 0.5, 120, 3.5));
        command(operator.player, "ahmet craft oak_planks 8");
        require(bool(task, "active"), "craft task must be active for owner conflict check");
        command(nonOwner.player, "ahmet craft stick 4");
        require("minecraft:oak_planks".equals(invoke(invoke(task, "craftRecipe"), "key")),
            "busy craft task must reject concurrent craft command");
        command(nonOwner.player, "ahmet cancel");
        require(bool(task, "active"), "non-owner non-admin must not cancel operator craft task");
        command(operator.player, "ahmet cancel");
        require(!bool(task, "active"), "operator cancel must stop active craft task");
        require(countMaterial(inv, Material.OAK_LOG) == 2 && countMaterial(inv, Material.OAK_PLANKS) == 0,
            "cancelled craft task must leave chest inputs untouched");

        // 5. Arrival-time shortage abort test
        repositionNpc(new Location(world, 0.5, 120, 4.5), () -> {
          inv.clear();
          inv.setItem(0, new ItemStack(Material.OAK_LOG, 2));
          eventBodies.clear();

          command(operator.player, "ahmet craft oak_planks 8");
          require(bool(task, "active"), "craft task should be active before walking");

          // Immediately mutate slot 0 to 1 log while NPC is walking
          inv.setItem(0, new ItemStack(Material.OAK_LOG, 1));

          waitFor("arrival_shortage_abort", 400, () -> !bool(task, "active"), this::verifyStage3);
        });
      });
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void verifyStage3() {
    try {
      require(integer(task, "collected") == 0, "task.collected must be 0 on arrival shortage abort");
      require(integer(task, "deposited") == 0, "task.deposited must be 0 on arrival shortage abort");
      require(integer(task, "cargo") == 0, "task.cargo must be 0 on arrival shortage abort");
      require(normalTeleports == 0, "normalTeleports == 0 required");

      Inventory inv = doubleChestInventory();
      ItemStack s0 = inv.getItem(0);
      require(s0 != null && s0.getType() == Material.OAK_LOG && s0.getAmount() == 1,
          "slot 0 must still have OAK_LOG x1");
      require(countMaterial(inv, Material.OAK_PLANKS) == 0, "chest must have 0 OAK_PLANKS");
      awaitTicks(10, () -> {
        try {
          require(!hasEventType("task_delivered"), "arrival shortage abort must not emit fake task_delivered");
          require(hasEventType("task_cancelled"), "arrival shortage abort must emit task_cancelled");
          getLogger().info("AHMET_CRAFT_STAGE_PASS stage=refusals_and_arrival_shortage "
              + "nonBatchRefused=true shortageRefused=true fullChestRefused=true ownerConflictRefused=true cancelWhileWalking=true arrivalShortageAborted=true fakeDeliveredEvent=false");

          awaitTicks(5, this::runStage4);
        } catch (Throwable error) {
          fail(error);
        }
      });
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 4: journal_recovery_and_conflict ====================
  private void runStage4() {
    stage = "journal_recovery_and_conflict";
    try {
      Class<?> craftClass = Class.forName("dev.ahmet.OperationJournal$Craft");
      Class<?> plannerClass = Class.forName("dev.ahmet.CraftingPlanner");
      Class<?> planClass = Class.forName("dev.ahmet.CraftingPlanner$Plan");
      Class<?> recipeClass = Class.forName("dev.ahmet.CraftRecipe");

      Object recipeOakPlanks = Enum.valueOf((Class<Enum>) recipeClass, "OAK_PLANKS");
      Object recipeStick = Enum.valueOf((Class<Enum>) recipeClass, "STICK");

      Method planMethod = plannerClass.getDeclaredMethod("plan", List.class, recipeClass, int.class);
      planMethod.setAccessible(true);

      Method fromPlan = craftClass.getDeclaredMethod("fromPlan",
          String.class, String.class, String.class, int.class, int.class, int.class, planClass);
      fromPlan.setAccessible(true);

      Method prepCraft = journal.getClass().getDeclaredMethod("prepareCraft", craftClass);
      prepCraft.setAccessible(true);

      Inventory inv = doubleChestInventory();

      // ----- 4A: APPLY_REMAINING -----
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 2));

      invoke(task, "startCraft", new Class<?>[]{UUID.class, recipeClass, int.class}, ACTOR_ID, recipeOakPlanks, 8);
      String taskIdA = string(task, "taskId");

      List<?> slotsA = (List<?>) invoke(ahmet, "craftInventorySlots", new Class<?>[]{Inventory.class}, inv);
      Object resA = planMethod.invoke(null, slotsA, recipeOakPlanks, 8);
      Object planA = invoke(resA, "plan");

      Object craftOpA = fromPlan.invoke(null,
          taskIdA, ACTOR_ID.toString(), chestLocation.getWorld().getName(),
          chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ(), planA);
      prepCraft.invoke(journal, craftOpA);

      reconcileStartupState();

      require(!bool(ahmet, "recoveryBlocked"), "recoveryBlocked must be false in 4A");
      require(!journalHasOperations(), "journal must have no operations in 4A");
      require(!bool(task, "active"), "task must be inactive in 4A");
      require(integer(task, "collected") == 8, "task.collected must be 8 in 4A");
      require(integer(task, "deposited") == 8, "task.deposited must be 8 in 4A");
      ItemStack s0A = inv.getItem(0);
      require(s0A != null && s0A.getType() == Material.OAK_PLANKS && s0A.getAmount() == 8,
          "slot 0 must have OAK_PLANKS x8 in 4A");
      require(countMaterial(inv, Material.OAK_LOG) == 0, "OAK_LOG must be 0 in 4A");
      require(countMaterial(inv, Material.OAK_PLANKS) == 8, "total OAK_PLANKS must be 8 in 4A");

      // ----- 4B: COMPLETE_ALREADY_APPLIED -----
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_PLANKS, 4));

      invoke(task, "startCraft", new Class<?>[]{UUID.class, recipeClass, int.class}, ACTOR_ID, recipeStick, 8);
      String taskIdB = string(task, "taskId");

      List<?> slotsB = (List<?>) invoke(ahmet, "craftInventorySlots", new Class<?>[]{Inventory.class}, inv);
      Object resB = planMethod.invoke(null, slotsB, recipeStick, 8);
      Object planB = invoke(resB, "plan");

      Object craftOpB = fromPlan.invoke(null,
          taskIdB, ACTOR_ID.toString(), chestLocation.getWorld().getName(),
          chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ(), planB);
      prepCraft.invoke(journal, craftOpB);

      // Apply the changes to the chest inventory (slot 0 empty, stick in output slot)
      List<?> changesB = (List<?>) invoke(planB, "changes");
      for (Object ch : changesB) {
        int slot = (int) invoke(ch, "slot");
        String afterMat = (String) invoke(ch, "afterMaterial");
        int afterAmt = (int) invoke(ch, "afterAmount");
        if (afterMat == null || afterAmt <= 0) {
          inv.setItem(slot, null);
        } else {
          inv.setItem(slot, new ItemStack(Material.matchMaterial(afterMat), afterAmt));
        }
      }

      reconcileStartupState();

      require(!bool(ahmet, "recoveryBlocked"), "recoveryBlocked must be false in 4B");
      require(!journalHasOperations(), "journal must have no operations in 4B");
      require(!bool(task, "active"), "task must be inactive in 4B");
      require(integer(task, "collected") == 8, "task.collected must be 8 in 4B");
      require(integer(task, "deposited") == 8, "task.deposited must be 8 in 4B");
      require(countMaterial(inv, Material.STICK) == 8, "total STICK must be 8 in 4B");

      // ----- 4C: CONFLICT -----
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 2));

      invoke(task, "startCraft", new Class<?>[]{UUID.class, recipeClass, int.class}, ACTOR_ID, recipeOakPlanks, 8);
      String taskIdC = string(task, "taskId");

      List<?> slotsC = (List<?>) invoke(ahmet, "craftInventorySlots", new Class<?>[]{Inventory.class}, inv);
      Object resC = planMethod.invoke(null, slotsC, recipeOakPlanks, 8);
      Object planC = invoke(resC, "plan");

      Object craftOpC = fromPlan.invoke(null,
          taskIdC, ACTOR_ID.toString(), chestLocation.getWorld().getName(),
          chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ(), planC);
      prepCraft.invoke(journal, craftOpC);

      // Tamper with slot 0
      inv.setItem(0, new ItemStack(Material.COBBLESTONE, 5));

      reconcileStartupState();

      require(bool(ahmet, "recoveryBlocked"), "recoveryBlocked must be true in 4C");
      require(journalHasPendingOperation(), "journal must have pending operation in 4C");

      // Clean up test state
      clearJournal();
      set(ahmet, "recoveryBlocked", false);
      invoke(task, "cancel");
      invoke(ahmet, "persist");

      getLogger().info("AHMET_CRAFT_STAGE_PASS stage=journal_recovery_and_conflict "
          + "applyRemaining=true alreadyApplied=true conflictBlocked=true");

      awaitTicks(5, this::startStage5);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 5: gather_oak_log_then_craft_chain ====================
  private void startStage5() {
    stage = "gather_oak_log_then_craft_chain";
    repositionNpc(new Location(world, 0.5, 120, 0.5), () -> {
      Inventory inv = doubleChestInventory();
      inv.clear();
      Block oakLog = world.getBlockAt(4, 120, 0);
      oakLog.setType(Material.OAK_LOG, false);
      world.getBlockAt(4, 122, 0).setType(Material.OAK_LEAVES, false);
      invoke(ahmet, "invalidateTargetScan");

      command(operator.player, "ahmet collect oak_log 1");
      require(bool(task, "active"), "gather oak_log task must be active");

      waitFor("gather_oak_log_completed", 600, () -> !bool(task, "active") && integer(task, "cargo") == 0, () -> {
        try {
          require(oakLog.getType() == Material.AIR, "gathered OAK_LOG block must be AIR");
          require(integer(task, "collected") == 1 && integer(task, "deposited") == 1,
              "gather task must collect and deposit 1 OAK_LOG");
          require(countMaterial(inv, Material.OAK_LOG) == 1, "bound chest must contain gathered OAK_LOG x1");

          repositionNpc(new Location(world, 0.5, 120, 4.5), () -> {
            command(operator.player, "ahmet craft oak_planks 4");
            require(bool(task, "active"), "craft oak_planks from gathered log must be active");
            waitFor("craft_planks_from_gathered_log", 400, () -> !bool(task, "active"), () -> {
              try {
                require(countMaterial(inv, Material.OAK_LOG) == 0 && countMaterial(inv, Material.OAK_PLANKS) == 4,
                    "gathered OAK_LOG x1 must be crafted into OAK_PLANKS x4");
                repositionNpc(new Location(world, 0.5, 120, 4.5), () -> {
                  command(operator.player, "ahmet craft stick 4");
                  require(bool(task, "active"), "craft stick from crafted planks must be active");
                  waitFor("craft_stick_from_crafted_planks", 400, () -> !bool(task, "active"), () -> {
                    try {
                      require(countMaterial(inv, Material.OAK_PLANKS) == 2 && countMaterial(inv, Material.STICK) == 4,
                          "crafted OAK_PLANKS x4 must yield OAK_PLANKS x2 and STICK x4");
                      require(normalTeleports == 0, "normalTeleports must remain 0 across gather+craft chain");
                      getLogger().info("AHMET_CRAFT_STAGE_PASS stage=gather_oak_log_then_craft_chain "
                          + "gatheredOakLog=1 remainingOakPlanks=2 craftedSticks=4 teleports=0");
                      getLogger().info("AHMET_CRAFTING_RUNTIME_PROBE_PASS stages=6 normalTeleports=0");
                      complete();
                    } catch (Throwable error) {
                      fail(error);
                    }
                  });
                });
              } catch (Throwable error) {
                fail(error);
              }
            });
          });
        } catch (Throwable error) {
          fail(error);
        }
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

  private boolean journalHasOperations() {
    try {
      return (boolean) invoke(journal, "hasOperations");
    } catch (Exception e) {
      throw new RuntimeException(e);
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
        Method m2 = journal.getClass().getDeclaredMethod("discardPendingCraft");
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
    getLogger().severe("AHMET_CRAFTING_RUNTIME_PROBE_FAIL stage=" + stage
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
        exchange.getRequestBody().readAllBytes();
        respond(exchange, 200, "{\"reply\":\"Tamam, 8 meşe tahtası üretiyorum.\",\"task\":{\"type\":\"craft_item\",\"recipe\":\"minecraft:oak_planks\",\"amount\":8},\"cancel\":false,\"goHome\":false}");
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
        case "getName", "getDisplayName" -> admin ? "CraftingOperator" : "SyntheticActor";
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
        case "toString" -> "M5 Crafting PlayerProxy";
        default -> primitiveDefault(method.getReturnType());
      };
    }
  }
}
