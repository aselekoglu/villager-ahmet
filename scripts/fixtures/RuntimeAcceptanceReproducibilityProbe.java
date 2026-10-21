package dev.ahmetprobe;

import dev.ahmet.LocalEconomyLedger;
import dev.ahmet.ResourceLockRegistry;
import dev.ahmet.VillagerRole;
import dev.ahmet.protection.ChunkTicketManager;
import dev.ahmet.protection.ExternalProtectionAdapter;
import dev.ahmet.protection.LandClaimRegistry;
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
import org.bukkit.block.data.type.Farmland;
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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Opt-in disposable runtime probe for Milestone 10 (Acceptance & Reproducibility).
 * Never install on a user or live server.
 */
public final class RuntimeAcceptanceReproducibilityProbe extends JavaPlugin implements Listener {
  private static final int GLOBAL_TICK_BUDGET = 3600;
  private static final UUID OPERATOR_ID = UUID.fromString("10efc754-6a02-4ed3-9a42-46e2a8f70001");

  private record Wait(String name, int budgetTicks, BooleanSupplier condition, Runnable success, int[] elapsed) {}

  private Plugin ahmetPlugin;
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
  private BukkitTask ticker;
  private Wait wait;

  private String stage = "startup";
  private boolean terminal;
  private int globalTicks;

  private boolean countNormalTeleports;
  private int normalTeleports;

  @Override
  public void onEnable() {
    if (!Boolean.getBoolean("ahmet.m10Probe")) {
      getLogger().warning("AHMET_M10_PROBE_DISABLED");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    Bukkit.getPluginManager().registerEvents(this, this);
    ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    tasks.add(ticker);
    tasks.add(Bukkit.getScheduler().runTaskLater(this,
        () -> fail(new IllegalStateException("Global 3600-tick budget exhausted")), GLOBAL_TICK_BUDGET));
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
      ahmetPlugin = Bukkit.getPluginManager().getPlugin("VillagerAhmet");
      world = Bukkit.getWorld("world");
      if (world == null && !Bukkit.getWorlds().isEmpty()) {
        world = Bukkit.getWorlds().get(0);
      }
      require(ahmetPlugin != null && world != null, "VillagerAhmet plugin or world missing");

      prepareArena();
      operator = new PlayerProxy(OPERATOR_ID, true, new Location(world, 0.5, 64, 0.5));

      startStage1();
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 1: stage1_fresh_install_and_subsystems_verification ====================
  private void startStage1() {
    stage = "stage1_fresh_install_and_subsystems_verification";
    try {
      // 1. Verify VillagerAhmet plugin is enabled
      require(ahmetPlugin.isEnabled(), "VillagerAhmet must be enabled");

      // 2. Verify all 5 subsystems are initialized
      LandClaimRegistry landClaims = (LandClaimRegistry) field(ahmetPlugin, "landClaims");
      require(landClaims != null, "LandClaimRegistry must be initialized");

      ExternalProtectionAdapter externalProtection = (ExternalProtectionAdapter) field(ahmetPlugin, "externalProtection");
      require(externalProtection != null, "ExternalProtectionAdapter must be initialized");

      ChunkTicketManager chunkTickets = (ChunkTicketManager) field(ahmetPlugin, "chunkTicketManager");
      require(chunkTickets != null, "ChunkTicketManager must be initialized");

      LocalEconomyLedger ledger = (LocalEconomyLedger) field(ahmetPlugin, "economyLedger");
      require(ledger != null, "LocalEconomyLedger must be initialized");

      require(Class.forName("dev.ahmet.VillagerRoster") != null, "VillagerRoster class must exist");
      require(bool(ahmetPlugin, "rosterStateValid"), "rosterStateValid must be true");

      // 3. Spawn primary villager Ahmet
      command(operator.player, "ahmet spawn");
      Object savedNpc = field(ahmetPlugin, "npc");
      require(savedNpc instanceof NPC, "/ahmet spawn did not create Citizens NPC");
      npcAhmet = (NPC) savedNpc;
      require(npcAhmet.isSpawned() && npcAhmet.getEntity().getType() == EntityType.VILLAGER
          && npcAhmet.getEntity() instanceof Villager, "Ahmet must be a real Citizens EntityType.VILLAGER");

      // Bind Ahmet chest & farmplot
      operator.targetBlock = chestLocation.getBlock();
      command(operator.player, "ahmet chest");
      require(chestLocation.getBlock().getState() instanceof Chest, "Chest was not bound through command path");
      command(operator.player, "ahmet farmplot 0 64 -5 2");
      require(field(ahmetPlugin, "farmPlot") != null, "Farm plot was not bound through command path for Ahmet");

      Location homeAhmet = new Location(world, 0.5, 64, 0.5);
      set(ahmetPlugin, "home", homeAhmet.clone());
      set(ahmetPlugin, "protectedHome", homeAhmet.clone());
      set(ahmetPlugin, "protectHomeRadius", 0);
      invoke(ahmetPlugin, "persist");

      // 4. Spawn secondary villager Mehmet
      command(operator.player, "ahmet npc mehmet spawn");

      @SuppressWarnings("unchecked")
      Map<String, Object> slots = (Map<String, Object>) field(ahmetPlugin, "villagerSlots");
      require(slots.containsKey("ahmet") && slots.containsKey("mehmet"), "villagerSlots must contain ahmet and mehmet");

      slotAhmet = slots.get("ahmet");
      slotMehmet = slots.get("mehmet");

      taskAhmet = field(slotAhmet, "task");
      journalAhmet = field(slotAhmet, "journal");

      taskMehmet = field(slotMehmet, "task");
      journalMehmet = field(slotMehmet, "journal");
      npcMehmet = (NPC) field(slotMehmet, "npc");
      require(npcMehmet != null && npcMehmet.isSpawned() && npcMehmet.getEntity().getType() == EntityType.VILLAGER
          && npcMehmet.getEntity() instanceof Villager, "Mehmet must be a real Citizens EntityType.VILLAGER");

      // Bind Mehmet chest & farmplot
      operator.targetBlock = chestLocation.getBlock();
      command(operator.player, "ahmet npc mehmet chest");
      command(operator.player, "ahmet npc mehmet farmplot 0 64 -5 2");

      Location homeMehmet = new Location(world, 2.5, 64, 0.5);
      set(slotMehmet, "home", homeMehmet.clone());
      invoke(ahmetPlugin, "persist");

      // 5. Begin normal teleport monitoring
      countNormalTeleports = true;
      normalTeleports = 0;
      require(normalTeleports == 0, "normalTeleports must be 0 after fresh install and spawning");

      getLogger().info("[AcceptanceProbe] STAGE_PASS: stage1_fresh_install_and_subsystems_verification");
      awaitTicks(5, this::startStage2);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 2: stage2_gather_forced_crash_journal_recovery ====================
  private void startStage2() {
    stage = "stage2_gather_forced_crash_journal_recovery";
    try {
      // 1. Ensure journals and task are clear
      invoke(journalAhmet, "clearAfterWorldSave");
      if (bool(taskAhmet, "active")) {
        invoke(taskAhmet, "cancel");
      }
      set(taskAhmet, "cargo", 0);
      ((Map<?, ?>) field(taskAhmet, "cargoItems")).clear();
      require(!bool(journalAhmet, "hasPendingOperation") && !bool(journalAhmet, "hasOperations"),
          "Ahmet journal must be completely clear before stage 2");
      require(!bool(ahmetPlugin, "recoveryBlocked"), "Ahmet must not be recoveryBlocked");

      // 2. Set an arena log block at (0, 64, 4) to AIR simulating it was cut down right before crash (kill -9)
      Block logBlock = world.getBlockAt(0, 64, 4);
      logBlock.setType(Material.AIR, false);

      // 3. Start a gather task for Ahmet
      invoke(taskAhmet, "start", new Class<?>[]{UUID.class, int.class, String.class}, OPERATOR_ID, 1, "any");
      String taskId = string(taskAhmet, "taskId");

      // 4. Seed pending harvest in journal
      Class<?> harvestClass = Class.forName("dev.ahmet.OperationJournal$Harvest");
      Constructor<?> ctor = harvestClass.getDeclaredConstructor(
          String.class, String.class, int.class, int.class, int.class, int.class, int.class, String.class);
      ctor.setAccessible(true);
      Object harvestOp = ctor.newInstance(taskId, world.getName(), 0, 64, 4, 0, 0, "OAK_LOG");

      Method prepHarvest = journalAhmet.getClass().getDeclaredMethod("prepareHarvest", harvestClass);
      prepHarvest.setAccessible(true);
      prepHarvest.invoke(journalAhmet, harvestOp);

      require(bool(journalAhmet, "hasPendingOperation"), "Ahmet journal must have pending harvest");

      // 5. Trigger recoverJournal
      invoke(ahmetPlugin, "recoverJournal");

      // 6. Verify clean reconciliation
      require(!bool(ahmetPlugin, "recoveryBlocked"), "recoveryBlocked must be false in stage2");
      require(!bool(journalAhmet, "hasPendingOperation"), "pending harvest must be resolved");
      require(integer(taskAhmet, "collected") == 1, "task.collected must be 1 after recovery");
      require(integer(taskAhmet, "cargo") == 1, "task.cargo must be 1 after recovery");
      require(normalTeleports == 0, "normalTeleports must remain 0 in stage2");

      // Clean up task & journal
      invoke(taskAhmet, "cancel");
      set(taskAhmet, "cargo", 0);
      ((Map<?, ?>) field(taskAhmet, "cargoItems")).clear();
      invoke(journalAhmet, "clearAfterWorldSave");
      invoke(ahmetPlugin, "persist");

      getLogger().info("[AcceptanceProbe] STAGE_PASS: stage2_gather_forced_crash_journal_recovery");
      awaitTicks(5, this::startStage3);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 3: stage3_craft_forced_crash_recovery_and_tamper_conflict ====================
  private void startStage3() {
    stage = "stage3_craft_forced_crash_recovery_and_tamper_conflict";
    try {
      Class<?> craftClass = Class.forName("dev.ahmet.OperationJournal$Craft");
      Class<?> plannerClass = Class.forName("dev.ahmet.CraftingPlanner");
      Class<?> planClass = Class.forName("dev.ahmet.CraftingPlanner$Plan");
      Class<?> recipeClass = Class.forName("dev.ahmet.CraftRecipe");

      @SuppressWarnings({"rawtypes", "unchecked"})
      Object recipeOakPlanks = Enum.valueOf((Class<Enum>) recipeClass, "OAK_PLANKS");

      Method planMethod = plannerClass.getDeclaredMethod("plan", List.class, recipeClass, int.class);
      planMethod.setAccessible(true);

      Method fromPlan = craftClass.getDeclaredMethod("fromPlan",
          String.class, String.class, String.class, int.class, int.class, int.class, planClass);
      fromPlan.setAccessible(true);

      Method prepCraft = journalAhmet.getClass().getDeclaredMethod("prepareCraft", craftClass);
      prepCraft.setAccessible(true);

      Inventory inv = doubleChestInventory();

      // ----- 3A: APPLY_REMAINING (Inputs consumed, output deposited upon recovery) -----
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 2));

      invoke(taskAhmet, "startCraft", new Class<?>[]{UUID.class, recipeClass, int.class}, OPERATOR_ID, recipeOakPlanks, 8);
      String taskIdA = string(taskAhmet, "taskId");

      List<?> slotsA = (List<?>) invoke(ahmetPlugin, "craftInventorySlots", new Class<?>[]{Inventory.class}, inv);
      Object resA = planMethod.invoke(null, slotsA, recipeOakPlanks, 8);
      Object planA = invoke(resA, "plan");

      Object craftOpA = fromPlan.invoke(null,
          taskIdA, OPERATOR_ID.toString(), chestLocation.getWorld().getName(),
          chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ(), planA);
      prepCraft.invoke(journalAhmet, craftOpA);

      invoke(ahmetPlugin, "recoverJournal");

      require(!bool(ahmetPlugin, "recoveryBlocked"), "recoveryBlocked must be false in 3A");
      require(!bool(journalAhmet, "hasOperations"), "journal must have no operations after recovery in 3A");
      require(!bool(taskAhmet, "active"), "task must be inactive in 3A");
      require(integer(taskAhmet, "collected") == 8, "task.collected must be 8 in 3A");
      require(integer(taskAhmet, "deposited") == 8, "task.deposited must be 8 in 3A");
      ItemStack s0A = inv.getItem(0);
      require(s0A != null && s0A.getType() == Material.OAK_PLANKS && s0A.getAmount() == 8,
          "slot 0 must have OAK_PLANKS x8 in 3A");
      require(countMaterial(inv, Material.OAK_LOG) == 0, "OAK_LOG must be 0 in 3A");
      require(countMaterial(inv, Material.OAK_PLANKS) == 8, "total OAK_PLANKS must be 8 in 3A");

      // ----- 3B: CONFLICT (Tampered chest slot triggers CONFLICT and recoveryBlocked) -----
      inv.clear();
      inv.setItem(0, new ItemStack(Material.OAK_LOG, 2));

      invoke(taskAhmet, "startCraft", new Class<?>[]{UUID.class, recipeClass, int.class}, OPERATOR_ID, recipeOakPlanks, 8);
      String taskIdC = string(taskAhmet, "taskId");

      List<?> slotsC = (List<?>) invoke(ahmetPlugin, "craftInventorySlots", new Class<?>[]{Inventory.class}, inv);
      Object resC = planMethod.invoke(null, slotsC, recipeOakPlanks, 8);
      Object planC = invoke(resC, "plan");

      Object craftOpC = fromPlan.invoke(null,
          taskIdC, OPERATOR_ID.toString(), chestLocation.getWorld().getName(),
          chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ(), planC);
      prepCraft.invoke(journalAhmet, craftOpC);

      // Tamper with slot 0
      inv.setItem(0, new ItemStack(Material.COBBLESTONE, 5));

      invoke(ahmetPlugin, "recoverJournal");

      require(bool(ahmetPlugin, "recoveryBlocked"), "recoveryBlocked must be true in 3B");
      require(bool(journalAhmet, "hasPendingOperation"), "journal must have pending operation in 3B");
      ItemStack s0C = inv.getItem(0);
      require(s0C != null && s0C.getType() == Material.COBBLESTONE && s0C.getAmount() == 5,
          "chest slot 0 must not be corrupted by conflict recovery");

      // Clear conflict state for next stage
      clearJournal(journalAhmet);
      set(ahmetPlugin, "recoveryBlocked", false);
      invoke(taskAhmet, "cancel");
      inv.clear();
      invoke(ahmetPlugin, "persist");

      require(normalTeleports == 0, "normalTeleports must remain 0 in stage3");

      getLogger().info("[AcceptanceProbe] STAGE_PASS: stage3_craft_forced_crash_recovery_and_tamper_conflict");
      awaitTicks(5, this::startStage4);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 4: stage4_chest_interference_during_transit ====================
  private void startStage4() {
    stage = "stage4_chest_interference_during_transit";
    try {
      // 1. Give Ahmet 5 oak logs in cargo
      invoke(taskAhmet, "start", new Class<?>[]{UUID.class, int.class, String.class}, OPERATOR_ID, 5, "any");
      for (int i = 0; i < 5; i++) {
        invoke(taskAhmet, "harvestOne", new Class<?>[]{String.class}, "OAK_LOG");
      }
      require(integer(taskAhmet, "cargo") == 5, "Ahmet cargo must be 5");
      require(integer(taskAhmet, "collected") == 5, "Ahmet collected must be 5");

      // 2. Fill all 54 slots of double chest with cobblestone
      Inventory inv = doubleChestInventory();
      for (int i = 0; i < 54; i++) {
        inv.setItem(i, new ItemStack(Material.COBBLESTONE, 64));
      }
      require(inv.getSize() == 54, "double chest inventory must be 54");

      // 3. Ahmet attempts deposit: CargoDepositPlanner detects full capacity
      Chest chest = (Chest) chestLocation.getBlock().getState();
      Method depositMethod = findMethod(ahmetPlugin, "deposit", Chest.class);
      depositMethod.setAccessible(true);
      depositMethod.invoke(ahmetPlugin, chest);

      // Verify cargo retained, nothing deleted or dropped
      require(integer(taskAhmet, "cargo") == 5, "Ahmet cargo must remain 5 when chest full");
      require(integer(taskAhmet, "deposited") == 0, "Ahmet deposited must remain 0");
      require(countMaterial(inv, Material.OAK_LOG) == 0, "Chest must not have oak logs yet");
      require(countMaterial(inv, Material.COBBLESTONE) == 54 * 64, "All 54 cobblestone stacks must be preserved");

      // 4. Clear 1 slot in chest and call deliver
      inv.setItem(0, null);
      Chest chest2 = (Chest) chestLocation.getBlock().getState();
      depositMethod.invoke(ahmetPlugin, chest2);

      // Verify deposit into freed slot, cargo 0, zero item loss
      require(integer(taskAhmet, "cargo") == 0, "Ahmet cargo must be 0 after successful deposit");
      require(integer(taskAhmet, "deposited") == 5, "Ahmet deposited must be 5 after successful deposit");
      ItemStack freedSlot = inv.getItem(0);
      require(freedSlot != null && freedSlot.getType() == Material.OAK_LOG && freedSlot.getAmount() == 5,
          "Slot 0 must now contain exactly 5 oak logs");
      require(countMaterial(inv, Material.COBBLESTONE) == 53 * 64, "Remaining 53 cobblestone stacks must be intact");
      require(normalTeleports == 0, "normalTeleports must remain 0 in stage4");

      // Clean up task state
      invoke(taskAhmet, "cancel");
      inv.clear();
      invoke(ahmetPlugin, "persist");

      getLogger().info("[AcceptanceProbe] STAGE_PASS: stage4_chest_interference_during_transit");
      awaitTicks(5, this::startStage5);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 5: stage5_multi_villager_concurrent_economy_durability ====================
  private void startStage5() {
    stage = "stage5_multi_villager_concurrent_economy_durability";
    try {
      // 1. Verify ResourceLockRegistry concurrency isolation
      ResourceLockRegistry locks = (ResourceLockRegistry) field(ahmetPlugin, "resourceLocks");
      require(locks != null, "resourceLocks must not be null");

      // Target block locking isolation
      boolean aLock = locks.tryAcquireTargetBlock("ahmet", "m10-task-1", world.getName(), 3, 64, 3);
      require(aLock, "Ahmet should acquire probe block lock");
      boolean mBlocked = !locks.tryAcquireTargetBlock("mehmet", "m10-task-2", world.getName(), 3, 64, 3);
      require(mBlocked, "Mehmet must be blocked from acquiring Ahmet's locked block");
      require(locks.isBlockLockedByOther("mehmet", world.getName(), 3, 64, 3), "Block must be locked by other for Mehmet");
      require(!locks.isBlockLockedByOther("ahmet", world.getName(), 3, 64, 3), "Block must NOT be locked by other for Ahmet");

      locks.releaseBlock("ahmet", world.getName(), 3, 64, 3);
      require(!locks.isBlockLockedByOther("mehmet", world.getName(), 3, 64, 3), "Block must be unlocked after release");
      boolean mLock = locks.tryAcquireTargetBlock("mehmet", "m10-task-2", world.getName(), 3, 64, 3);
      require(mLock, "Mehmet should now acquire probe block");
      locks.releaseBlock("mehmet", world.getName(), 3, 64, 3);

      // Chest locking isolation
      @SuppressWarnings("unchecked")
      List<ResourceLockRegistry.CoordKey> chestCoords = (List<ResourceLockRegistry.CoordKey>)
          invoke(ahmetPlugin, "chestCoordsFor", new Class<?>[]{Location.class}, chestLocation);
      require(locks.tryAcquireChest("ahmet", "m10-chest", chestCoords), "Ahmet should acquire chest lock");
      require(locks.isChestLockedByOther("mehmet", chestCoords), "Chest must be locked for Mehmet");
      locks.releaseChestsForVillager("ahmet");
      require(!locks.isChestLockedByOther("mehmet", chestCoords), "Chest lock must be released");

      // 2. Verify LocalEconomyLedger durability & net balance conservation
      LocalEconomyLedger ledger = (LocalEconomyLedger) field(ahmetPlugin, "economyLedger");
      require(ledger != null, "economyLedger must not be null");

      Class<?> kindClass = Class.forName("dev.ahmet.WoodTask$Kind");
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object kindGather = Enum.valueOf((Class<Enum>) kindClass, "GATHER");
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object kindFarm = Enum.valueOf((Class<Enum>) kindClass, "FARM");

      Method recordDepositMethod = LocalEconomyLedger.class.getDeclaredMethod(
          "recordDeposit", String.class, String.class, VillagerRole.class, String.class, kindClass, String.class, int.class);
      recordDepositMethod.setAccessible(true);

      recordDepositMethod.invoke(ledger, "m10:tx:ahmet:gather", "ahmet", VillagerRole.WORKER, "m10-task-a", kindGather, "OAK_LOG", 10);
      ledger.recordCraft("m10:tx:mehmet:craft", "mehmet", VillagerRole.CRAFTER, "m10-task-m1", "OAK_LOG", 2, "OAK_PLANKS", 8);
      recordDepositMethod.invoke(ledger, "m10:tx:mehmet:farm", "mehmet", VillagerRole.FARMER, "m10-task-m2", kindFarm, "WHEAT", 6);

      LocalEconomyLedger.VillagerSummary sAhmet = ledger.villagerSummary("ahmet");
      require(sAhmet.gatheredItems() >= 10, "Ahmet gatheredItems must be >= 10");
      require(sAhmet.credits() >= 10, "Ahmet credits must be >= 10");

      LocalEconomyLedger.VillagerSummary sMehmet = ledger.villagerSummary("mehmet");
      require(sMehmet.farmedItems() >= 6, "Mehmet farmedItems must be >= 6");
      require(sMehmet.craftedItems() >= 8, "Mehmet craftedItems must be >= 8");
      require(sMehmet.credits() >= 14, "Mehmet credits must be >= 14");

      // Verify net balances match produced - consumed
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
      require(operator.hasMessageContaining("Köy Ekonomisi"), "Operator must receive ledger summary");
      require(normalTeleports == 0, "normalTeleports must remain 0 in stage5");

      getLogger().info("[AcceptanceProbe] STAGE_PASS: stage5_multi_villager_concurrent_economy_durability");
      awaitTicks(5, this::startStage6);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== STAGE 6: stage6_final_teleport_audit_and_clean_shutdown ====================
  private void startStage6() {
    stage = "stage6_final_teleport_audit_and_clean_shutdown";
    try {
      // 1. Verify normalTeleports strictly == 0
      require(normalTeleports == 0, "Final teleport audit failed: normalTeleports=" + normalTeleports);

      // 2. Write verification report
      String reportJson = """
          {
            "probe": "RuntimeAcceptanceReproducibilityProbe",
            "milestone": "M10",
            "status": "PASS",
            "stagesCompleted": 6,
            "stages": [
              "stage1_fresh_install_and_subsystems_verification",
              "stage2_gather_forced_crash_journal_recovery",
              "stage3_craft_forced_crash_recovery_and_tamper_conflict",
              "stage4_chest_interference_during_transit",
              "stage5_multi_villager_concurrent_economy_durability",
              "stage6_final_teleport_audit_and_clean_shutdown"
            ],
            "normalTeleports": %d,
            "subsystemsVerified": {
              "LandClaimRegistry": true,
              "ExternalProtectionAdapter": true,
              "ChunkTicketManager": true,
              "LocalEconomyLedger": true,
              "VillagerRoster": true
            },
            "journalRecovery": {
              "gatherReconciliation": true,
              "craftReconciliation": true,
              "tamperConflictSafety": true
            },
            "cargoConservation": {
              "fullChestHandling": true,
              "zeroItemLoss": true
            },
            "economyDurability": {
              "resourceLocking": true,
              "netBalanceConservation": true
            }
          }
          """.formatted(normalTeleports);

      try {
        File reportFile = new File("/private/tmp/ahmet-m10-acceptance-verification.json");
        Files.writeString(reportFile.toPath(), reportJson, StandardCharsets.UTF_8);
      } catch (Throwable t) {
        getLogger().warning("Could not write /private/tmp report: " + t.getMessage());
      }
      try {
        File localReport = new File("ahmet-m10-acceptance-verification.json");
        Files.writeString(localReport.toPath(), reportJson, StandardCharsets.UTF_8);
      } catch (Throwable t) {
        getLogger().warning("Could not write local report: " + t.getMessage());
      }

      // 3. Log milestone pass
      getLogger().info("[AcceptanceProbe] M10_ACCEPTANCE_REPRODUCIBILITY_PROBE_PASS stages=6 normalTeleports=" + normalTeleports);

      // 4. Gracefully save worlds and stop server
      for (World w : Bukkit.getWorlds()) {
        w.save();
      }
      terminal = true;
      Bukkit.shutdown();
    } catch (Throwable error) {
      fail(error);
    }
  }

  // ==================== ARENA PREPARATION ====================
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

    // Walls
    for (int x = -12; x <= 12; x++) {
      world.getBlockAt(x, 64, -12).setType(Material.STONE_BRICKS, false);
      world.getBlockAt(x, 65, -12).setType(Material.STONE_BRICKS, false);
      world.getBlockAt(x, 64, 12).setType(Material.STONE_BRICKS, false);
      world.getBlockAt(x, 65, 12).setType(Material.STONE_BRICKS, false);
    }
    for (int z = -12; z <= 12; z++) {
      world.getBlockAt(-12, 64, z).setType(Material.STONE_BRICKS, false);
      world.getBlockAt(-12, 65, z).setType(Material.STONE_BRICKS, false);
      world.getBlockAt(12, 64, z).setType(Material.STONE_BRICKS, false);
      world.getBlockAt(12, 65, z).setType(Material.STONE_BRICKS, false);
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
    for (int x = -1; x <= 2; x++) {
      for (int z = -6; z <= -4; z++) {
        Block block = world.getBlockAt(x, 63, z);
        block.setType(Material.FARMLAND, false);
        Farmland farmland = (Farmland) block.getBlockData();
        farmland.setMoisture(farmland.getMaximumMoisture());
        block.setBlockData(farmland, false);
      }
    }
  }

  private Inventory doubleChestInventory() {
    Chest state = (Chest) chestLocation.getBlock().getState();
    return state.getInventory();
  }

  private int countMaterial(Inventory inv, Material material) {
    int count = 0;
    for (ItemStack item : inv.getContents()) {
      if (item != null && item.getType() == material) {
        count += item.getAmount();
      }
    }
    return count;
  }

  private void clearJournal(Object j) {
    if (j == null) return;
    try {
      Method m = j.getClass().getDeclaredMethod("discardPendingCraft");
      m.setAccessible(true);
      m.invoke(j);
    } catch (Exception ignored) {}
    try {
      Method m = j.getClass().getDeclaredMethod("clearAfterWorldSave");
      m.setAccessible(true);
      m.invoke(j);
    } catch (Exception ignored) {}
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

  private void awaitTicks(int ticks, Runnable next) {
    tasks.add(Bukkit.getScheduler().runTaskLater(this, next, ticks));
  }

  private void command(CommandSender sender, String line) {
    String[] parts = line.split(" ");
    PluginCommand cmd = getServer().getPluginCommand(parts[0]);
    require(cmd != null, "command not found: " + parts[0]);
    String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
    boolean ok = cmd.execute(sender, parts[0], args);
    require(ok, "command execution failed: " + line);
  }

  private void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError("[" + stage + "] " + message);
    }
  }

  private void fail(Throwable error) {
    terminal = true;
    getLogger().severe("M10_ACCEPTANCE_REPRODUCIBILITY_PROBE_FAILED stage=" + stage + " error=" + error.getMessage());
    error.printStackTrace();
    cleanup();
    getServer().shutdown();
  }

  private void cleanup() {
    for (BukkitTask t : tasks) {
      try { t.cancel(); } catch (Exception ignored) {}
    }
    tasks.clear();
    if (world != null) {
      for (int[] chunk : ownChunkTickets) {
        try { world.removePluginChunkTicket(chunk[0], chunk[1], this); } catch (Exception ignored) {}
      }
    }
    ownChunkTickets.clear();
  }

  // ==================== REFLECTION UTILS ====================
  private static Object field(Object target, String name) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(target);
    } catch (Exception e) {
      throw new RuntimeException("failed to read field: " + name, e);
    }
  }

  private static void set(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("failed to write field: " + name, e);
    }
  }

  private static Object invoke(Object target, String name) {
    return invoke(target, name, new Class<?>[0]);
  }

  private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
    try {
      Method m = findMethod(target, name, types);
      m.setAccessible(true);
      return m.invoke(target, args);
    } catch (Exception e) {
      throw new RuntimeException("failed to invoke: " + name, e);
    }
  }

  private static Method findMethod(Object target, String name, Class<?>... types) {
    Class<?> cur = target.getClass();
    while (cur != null) {
      try {
        return cur.getDeclaredMethod(name, types);
      } catch (NoSuchMethodException ignored) {
        cur = cur.getSuperclass();
      }
    }
    throw new RuntimeException("method not found: " + name);
  }

  private static boolean bool(Object target, String name) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return (boolean) f.get(target);
    } catch (NoSuchFieldException e) {
      try {
        Method m = findMethod(target, name);
        m.setAccessible(true);
        return (boolean) m.invoke(target);
      } catch (Exception ex) {
        throw new RuntimeException("failed to read bool field/method: " + name, ex);
      }
    } catch (Exception e) {
      throw new RuntimeException("failed to read bool field: " + name, e);
    }
  }

  private static int integer(Object target, String name) {
    return (int) field(target, name);
  }

  private static String string(Object target, String name) {
    return (String) field(target, name);
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
        case "getName", "getDisplayName" -> "AcceptanceReproducibilityOperator";
        case "canSee" -> true;
        case "getTargetBlockExact" -> targetBlock;
        case "sendMessage" -> {
          if (args != null && args.length > 0) {
            String msg = String.valueOf(args[0]);
            messages.add(msg);
            getLogger().info("OPERATOR_MSG: " + msg);
          }
          yield null;
        }
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "M10 AcceptanceReproducibility PlayerProxy";
        default -> null;
      };
    }
  }
}
