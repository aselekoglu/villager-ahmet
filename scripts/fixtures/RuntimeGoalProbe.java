package dev.ahmetprobe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import net.citizensnpcs.api.event.NPCTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

/** Opt-in, disposable Paper/Citizens runtime probe for enabled production-goal wiring. */
public final class RuntimeGoalProbe extends JavaPlugin implements Listener {
  private static final UUID OWNER = UUID.fromString("6d9ea6da-7fb1-4b4c-a7e3-e8a7d64a0a11");
  private static final UUID OPERATOR = UUID.fromString("e4f09903-86f4-4a7a-bf27-f143b15f46e0");
  private static final UUID FOREIGN = UUID.fromString("b9c82ef1-2079-48bf-83b0-56ca51a0a3ab");
  private static final int DEADLINE_TICKS = 2400;
  private Plugin ahmet;
  private Object task;
  private Object controller;
  private Object journal;
  private NPC npc;
  private World world;
  private Location home;
  private Location chestLocation;
  private Player owner;
  private Player operator;
  private Player foreign;
  private final List<String> messages = new ArrayList<>();
  private String stage = "startup";
  private int elapsed;
  private int teleports;
  private int walkedTicks;
  private Location lastLocation;
  private double walked;
  private double maxStep;
  private boolean normalMovementMonitoring;
  private boolean terminal;
  private org.bukkit.scheduler.BukkitTask deadline;
  private HttpServer mockBridge;
  private ExecutorService bridgeExecutor;
  private final AtomicInteger decisionCalls = new AtomicInteger();
  private String bridgeToken;
  private String lastExplorationDiagnosticKey;
  private int explorationStateDiagnosticLines;
  private int explorationPeriodicDiagnosticLines;

  @Override public void onEnable() {
    if (!Boolean.getBoolean("ahmet.goalProbe")) {
      getLogger().warning("AHMET_GOAL_PROBE_DISABLED: set -Dahmet.goalProbe=true only for a disposable server");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    Bukkit.getPluginManager().registerEvents(this, this);
    deadline = Bukkit.getScheduler().runTaskLater(this, () -> fail(new IllegalStateException("fixture deadline exceeded")), DEADLINE_TICKS);
    Bukkit.getScheduler().runTaskLater(this, this::start, 40L);
  }

  private void start() {
    try {
      ahmet = Bukkit.getPluginManager().getPlugin("VillagerAhmet");
      world = Bukkit.getWorld("world");
      require(ahmet != null && world != null, "required plugin/world unavailable");
      task = field(ahmet, "task"); controller = field(ahmet, "controller"); journal = field(ahmet, "journal");
      prepareArena();
      set(ahmet, "chestLocation", chestLocation);
      bridgeToken = UUID.randomUUID().toString();
      startMockBridge();
      ahmet.getConfig().set("brain.url", "http://127.0.0.1:3199");
      ahmet.getConfig().set("brain.token", bridgeToken);
      set(ahmet, "protectedHome", null);
      set(ahmet, "protectHomeRadius", 0);
      ahmet.getConfig().set("options.production-goals-enabled", true);
      ahmet.getConfig().set("options.schedule-enabled", false);
      ahmet.getConfig().set("options.max-distance-from-home", 10);
      ahmet.getConfig().set("options.search-radius", 16);
      invoke(ahmet, "persist");
      set(ahmet, "returningHome", false);
      set(ahmet, "homeStepTarget", null);
      set(ahmet, "homeStepAttempt", 0);
      operator = playerProxy(OPERATOR, true);
      owner = playerProxy(OWNER, false);
      foreign = playerProxy(FOREIGN, false);
      command(operator, "ahmet spawn");
      Object candidate = field(ahmet, "npc");
      require(candidate instanceof NPC, "production command did not create Citizens NPC");
      npc = (NPC) candidate;
      require(npc.isSpawned() && npc.getEntity().getType() == EntityType.VILLAGER, "expected spawned Citizens VILLAGER");
      // The initial /spawn establishes the arena home. Teleport counting starts afterward.
      set(ahmet, "home", npc.getEntity().getLocation().clone());
      home = npc.getEntity().getLocation().clone();
      set(ahmet, "chestLocation", chestLocation);
      invoke(ahmet, "persist");
      lastLocation = npc.getEntity().getLocation().clone();
      normalMovementMonitoring = true;
      getLogger().info("AHMET_GOAL_STAGE_PASS name=setup npcType=" + npc.getEntity().getType() + " home=" + loc(home)
          + " productionGoalsEnabled=" + ahmet.getConfig().getBoolean("options.production-goals-enabled"));
      stage = "harvest-cancel-deliver";
      getLogger().info("AHMET_GOAL_STAGE_START name=harvest-cancel-deliver");
      startHarvestScenario();
    } catch (Throwable error) { fail(error); }
  }

  private void prepareArena() {
    for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) {
      var chunk = world.getChunkAt(cx, cz); chunk.load(true); chunk.setForceLoaded(true);
    }
    for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) for (int y = 110; y <= 135; y++) {
      Block block = world.getBlockAt(x, y, z);
      if (!block.getType().isAir()) block.setType(Material.AIR, false);
    }
    for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++)
      world.getBlockAt(x, 119, z).setType(Material.SMOOTH_STONE, false);
    home = new Location(world, 0.5, 120, 0.5);
    chestLocation = new Location(world, -4, 120, 0);
    Block chestBlock = chestLocation.getBlock(); chestBlock.setType(Material.CHEST, false);
    require(chestBlock.getState() instanceof Chest, "could not create fixture chest");
  }

  private void startHarvestScenario() {
    try {
      Block first = world.getBlockAt(4, 120, 1); first.setType(Material.DIRT, false);
      Block second = world.getBlockAt(6, 120, -1); second.setType(Material.DIRT, false);
      command(owner, "ahmet collect dirt 2");
      require(bool(task, "active"), "collect command was not admitted");
      String taskId = string(task, "taskId"); long generation = number(controller, "jobGeneration");
      await("real-harvest-with-committed-history", 700,
          () -> integer(task, "cargo") == 1 && integer(task, "collected") == 1 && bool(task, "active")
              && journalHasOperations(), () -> {
            try {
              require(first.getType() == Material.AIR || second.getType() == Material.AIR,
                  "cargo credited without a source block mutation");
              require(!pendingOperations(), "completed harvest still has pending journal work");
              getLogger().info("AHMET_GOAL_STAGE_PASS name=real-harvest cargo=1 committedJournalHistory=true pending=false");
              await("rejected-home-preserves-task-route", 180, this::hasLiveTaskRoute, () -> {
                try {
                  Location route = (Location) field(ahmet, "navigationGoal");
                  Location nativeRoute = npc.getNavigator().getTargetAsLocation();
                  Object purpose = field(ahmet, "navigationPurpose");
                  require(nativeRoute != null, "Citizens did not expose the active pre-command route target");
                  command(owner, "ahmet home");
                  require(bool(task, "active") && string(task, "taskId").equals(taskId)
                      && number(controller, "jobGeneration") == generation && integer(task, "cargo") == 1
                      && route.equals(field(ahmet, "navigationGoal")) && purpose == field(ahmet, "navigationPurpose")
                      && npc.getNavigator().isNavigating()
                      && sameBlock(nativeRoute, npc.getNavigator().getTargetAsLocation())
                      && messagesContain("iptalden kalan yükü var"),
                      "rejected /home changed task identity, cargo, plugin/native route, or did not take cargo-rejection branch");
                  getLogger().info("AHMET_GOAL_STAGE_PASS name=rejected-home-preserves-task-route taskId=" + taskId
                      + " generation=" + generation + " cargo=1 nativeNavigation=true");
                  command(owner, "ahmet cancel");
                  require(!bool(task, "active") && integer(task, "cargo") == 1, "cancel did not hold harvested cargo");
                  int cargoBefore = integer(task, "cargo"); int chestBefore = countChest();
                  Material firstBeforeHold = first.getType(); Material secondBeforeHold = second.getType();
                  int[] idleTicks = {0};
                  await("cancelled-cargo-rest-hold", 40, () -> {
                    require(integer(task, "cargo") == cargoBefore && countChest() == chestBefore
                        && first.getType() == firstBeforeHold && second.getType() == secondBeforeHold
                        && !bool(task, "active") && !npc.getNavigator().isNavigating(),
                        "held cargo or world/chest changed during idle hold");
                    return ++idleTicks[0] >= 20;
                  }, () -> {
                    try {
                      require(integer(task, "cargo") == cargoBefore && countChest() == chestBefore
                          && first.getType() == firstBeforeHold && second.getType() == secondBeforeHold
                          && !npc.getNavigator().isNavigating(),
                          "held cargo or world/chest changed while idle");
                      Object observedGoal = invoke(controller, "currentGoal");
                      require("REST_IDLE".equals(String.valueOf(observedGoal)),
                          "idle cargo controller goal was not REST_IDLE: " + observedGoal);
                      Object goalBeforeForeign = invoke(controller, "currentGoal");
                      command(foreign, "ahmet deliver");
                      require(integer(task, "cargo") == 1 && !bool(task, "active")
                          && messagesContain("Yetkin yok"), "foreign deliver changed held cargo or missed authorization denial");
                      require(goalBeforeForeign.equals(invoke(controller, "currentGoal")),
                          "foreign delivery changed the controller goal");
                      getLogger().info("AHMET_GOAL_STAGE_PASS name=cancelled-cargo-rest-hold ticks=20 cargo=1 foreignDenied=true goal=REST_IDLE");
                      double walkedBeforeDelivery = walked;
                      Location deliveryStart = npc.getEntity().getLocation().clone();
                      command(owner, "ahmet deliver");
                      require(bool(task, "active"), "owner delivery command was not admitted");
                      await("owner-delivery-native-route", 180,
                          () -> npc.getNavigator().isNavigating()
                              && "DELIVER_CARGO".equals(String.valueOf(controllerGoal())), () -> {
                            try {
                              Location nativeDeliveryTarget = npc.getNavigator().getTargetAsLocation();
                              Location productionTarget = chestLocation.clone().add(0.5, 0, 0.5);
                              Location citizensStandTarget = productionTarget.clone().add(0, 1, 0);
                              require(chestLocation.getBlock().getType() == Material.CHEST
                                  && chestLocation.clone().add(0, -1, 0).getBlock().getType() == Material.SMOOTH_STONE
                                  && chestLocation.clone().add(0, 1, 0).getBlock().isPassable()
                                  && chestLocation.clone().add(0, 2, 0).getBlock().isPassable(),
                                  "fixture chest support/headroom does not match the pinned Citizens normalization case");
                              require(productionTarget.equals(field(ahmet, "navigationGoal"))
                                  && nativeDeliveryTarget != null
                                  && nativeDeliveryTarget.distanceSquared(citizensStandTarget) <= 0.0001,
                                  "Citizens delivery target did not equal the pinned stand-above normalization; plugin="
                                      + loc((Location) field(ahmet, "navigationGoal")) + " expectedNative=" + loc(citizensStandTarget)
                                      + " actualNative=" + loc(nativeDeliveryTarget));
                              getLogger().info("AHMET_GOAL_STAGE_PASS name=owner-delivery-native-route purpose=DELIVER_CARGO"
                                  + " pluginTarget=" + loc(productionTarget) + " nativeTarget=" + loc(nativeDeliveryTarget)
                                  + " normalization=Citizens-findValidLocationAbove-plus1Y");
                              await("owner-delivery-native-walk", 500,
                                  () -> integer(task, "cargo") == 0 && !bool(task, "active"), () -> {
                                    try {
                                      require(countChest() == chestBefore + 1, "delivery did not deposit exactly one item");
                                      require(walked - walkedBeforeDelivery > 1.0
                                          && deliveryStart.distance(npc.getEntity().getLocation()) > 1.0,
                                          "NPC did not make bounded progress during owner delivery");
                                      getLogger().info("AHMET_GOAL_STAGE_PASS name=owner-delivery-native-walk cargo=0 deposited=1 walkingBlocks="
                                          + rounded(walked - walkedBeforeDelivery) + " teleports=" + teleports);
                                      runBoundaryHandoff();
                                    } catch (Throwable error) { fail(error); }
                                  });
                            } catch (Throwable error) { fail(error); }
                          });
                    } catch (Throwable error) { fail(error); }
                  });
                } catch (Throwable error) { fail(error); }
              });
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private boolean hasLiveTaskRoute() {
    return npc.getNavigator().isNavigating() && fieldQuiet(ahmet, "navigationGoal") instanceof Location;
  }

  private void runBoundaryHandoff() {
    try {
      // Controlled source placement at the boundary. Production task admission and per-tick route handoff run normally.
      Block outward = world.getBlockAt(16, 120, 0); outward.setType(Material.DIRT, false);
      command(owner, "ahmet collect dirt 2");
      // Controlled fixture route setup: production's real tick loop must replace this live Citizens route.
      npc.getNavigator().getDefaultParameters().destinationTeleportMargin(-1).stationaryTicks(-1);
      npc.getNavigator().setTarget(outward.getLocation().add(.5, 0, .5));
      set(ahmet, "treeTarget", outward.getLocation());
      set(ahmet, "navigationGoal", outward.getLocation().add(.5, 0, .5));
      set(ahmet, "navigationPurpose", enumValue("HARVEST_RESOURCE"));
      await("outward-route-started", 250,
          () -> npc.getNavigator().isNavigating() && fieldQuiet(ahmet, "navigationPurpose") != null
              && "HARVEST_RESOURCE".equals(fieldQuiet(ahmet, "navigationPurpose").toString()), () -> {
            try {
              Location outwardTarget = npc.getNavigator().getTargetAsLocation();
              require(outwardTarget != null && outwardTarget.distanceSquared(home) > 100.0,
                  "Citizens did not retain the controlled outward route target: " + loc(outwardTarget));
              await("safe-home-preempts-outward-route", 300,
                  () -> "RETURN_HOME".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                      && fieldQuiet(ahmet, "navigationGoal") instanceof Location
                      && npc.getNavigator().isNavigating()
                      && npc.getEntity().getLocation().distanceSquared(home) > 100.0, () -> {
                    try {
                      Location safeTarget = (Location) field(ahmet, "navigationGoal");
                      Location nativeSafeTarget = npc.getNavigator().getTargetAsLocation();
                      require(nativeSafeTarget != null && sameBlock(nativeSafeTarget, safeTarget)
                          && safeTarget.distanceSquared(home) < outwardTarget.distanceSquared(home)
                          && !sameBlock(nativeSafeTarget, outwardTarget),
                          "safe-home plugin/native route did not replace outward Citizens target; native=" + loc(nativeSafeTarget)
                              + " safe=" + loc(safeTarget) + " outward=" + loc(outwardTarget));
                      getLogger().info("AHMET_GOAL_STAGE_PASS name=safe-home-route-handoff crossedBoundary=true nativeOld="
                          + loc(outwardTarget) + " nativeReplacement=" + loc(nativeSafeTarget) + " purpose=RETURN_HOME");
                      command(owner, "ahmet cancel");
                      runExplorationStages();
                    } catch (Throwable error) { fail(error); }
                  });
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void runExplorationStages() {
    try {
      // Remove all fixture targets and start a real bounded job so production scanning admits actual exploration routes.
      for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
        Block block = world.getBlockAt(x, 120, z);
        if (block.getType() == Material.DIRT) block.setType(Material.AIR, false);
      }
      set(ahmet, "home", npc.getEntity().getLocation().clone());
      home = npc.getEntity().getLocation().clone();
      set(ahmet, "protectedHome", null); set(ahmet, "protectHomeRadius", 0);
      set(ahmet, "returningHome", false); set(ahmet, "explorationAttempts", 0);
      command(owner, "ahmet collect dirt 2");
      await("first-exploration-leg-start", 350,
          () -> integer(ahmet, "explorationAttempts") == 1
              && "EXPLORE_RESOURCES".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
              && npc.getNavigator().isNavigating(), () -> {
            try {
              Location firstRoute = npc.getNavigator().getTargetAsLocation();
              Location firstStart = npc.getEntity().getLocation().clone();
              require(firstRoute != null, "Citizens has no first exploration target");
              await("first-exploration-leg-moves-with-purpose", 240,
                  () -> "EXPLORE_RESOURCES".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                      && npc.getNavigator().isNavigating()
                      && npc.getNavigator().getTargetAsLocation() != null
                      && sameBlock(npc.getNavigator().getTargetAsLocation(), firstRoute)
                      && firstStart.distance(npc.getEntity().getLocation()) > 0.4, () -> {
                    try {
                      await("first-exploration-leg-arrival", 300,
                          () -> !npc.getNavigator().isNavigating()
                              && "REST_IDLE".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                              && fieldQuiet(ahmet, "navigationGoal") == null, () -> {
                            try {
                              require(integer(ahmet, "explorationAttempts") == 1
                                  && npc.getEntity().getLocation().distance(firstRoute) <= 3.5,
                                  "first exploration leg did not arrive with EXPLORE purpose preserved: target=" + loc(firstRoute));
                              getLogger().info("AHMET_GOAL_STAGE_PASS name=first-exploration-leg start-move-arrival purpose=EXPLORE_RESOURCES target="
                                  + loc(firstRoute) + " arrival=" + loc(npc.getEntity().getLocation()));
                              await("fourth-exploration-leg-start", 900,
                                  () -> integer(ahmet, "explorationAttempts") == 4
                                      && "EXPLORE_RESOURCES".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                                      && npc.getNavigator().isNavigating(), () -> {
                                    try {
                                      require(!bool(ahmet, "recoveryBlocked") && integer(task, "cargo") == 0,
                                          "exploration unexpectedly entered recovery or acquired cargo");
                                      Location fourthRoute = npc.getNavigator().getTargetAsLocation();
                                      Location fourthStart = npc.getEntity().getLocation().clone();
                                      require(fourthRoute != null, "Citizens has no fourth exploration target");
                                      await("fourth-exploration-leg-movement", 240,
                                          () -> integer(ahmet, "explorationAttempts") == 4
                                              && "EXPLORE_RESOURCES".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                                              && npc.getNavigator().isNavigating()
                                              && npc.getNavigator().getTargetAsLocation() != null
                                              && sameBlock(npc.getNavigator().getTargetAsLocation(), fourthRoute)
                                              && fourthStart.distance(npc.getEntity().getLocation()) > 0.4, () -> {
                                        try {
                                          getLogger().info("AHMET_GOAL_STAGE_PASS name=fourth-exploration-leg attempts=4 purpose=EXPLORE_RESOURCES"
                                              + " nativeTarget=" + loc(fourthRoute) + " movementObserved=true");
                                          await("fourth-exploration-leg-arrival", 300,
                                              () -> !npc.getNavigator().isNavigating()
                                                  && "REST_IDLE".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                                                  && fieldQuiet(ahmet, "navigationGoal") == null, () -> {
                                            try {
                                              require(integer(ahmet, "explorationAttempts") == 4
                                                  && npc.getEntity().getLocation().distance(fourthRoute) <= 3.5,
                                                  "fourth exploration leg did not arrive or attempt count changed");
                                              int[] noFifthTicks = {0};
                                              await("no-fifth-exploration-attempt", 100,
                                                  () -> ++noFifthTicks[0] >= 60, () -> {
                                                try {
                                                  require(integer(ahmet, "explorationAttempts") <= 4
                                                      && !"EXPLORE_RESOURCES".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                                                      && !npc.getNavigator().isNavigating(),
                                                      "production admitted a fifth exploration route");
                                                  getLogger().info("AHMET_GOAL_STAGE_PASS name=fourth-exploration-leg-arrival noFifthAttempt=true");
                                                  command(owner, "ahmet cancel");
                                                  runPendingRecoveryHold();
                                                } catch (Throwable error) { fail(error); }
                                              });
                                            } catch (Throwable error) { fail(error); }
                                          });
                                        } catch (Throwable error) { fail(error); }
                                      });
                                    } catch (Throwable error) { fail(error); }
                                  });
                            } catch (Throwable error) { fail(error); }
                          });
                    } catch (Throwable error) { fail(error); }
                  });
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void runPendingRecoveryHold() {
    try {
      set(ahmet, "home", npc.getEntity().getLocation().clone());
      home = npc.getEntity().getLocation().clone();
      set(ahmet, "returningHome", false);
      set(ahmet, "recoveryBlocked", false);
      Block eligible = world.getBlockAt(npc.getEntity().getLocation().getBlockX() + 2, 120,
          npc.getEntity().getLocation().getBlockZ());
      eligible.setType(Material.DIRT, false);
      command(owner, "ahmet collect dirt 2");
      require(bool(task, "active") && integer(task, "cargo") == 0 && integer(task, "collected") == 0,
          "positive-control collect task was not admitted cleanly");
      String taskId = string(task, "taskId");
      Location start = npc.getEntity().getLocation().clone();
      int chest = countChest(); int collected = integer(task, "collected"); int cargo = integer(task, "cargo");
      Object pendingClear = clearRecord(eligible);
      invoke(journal, "prepareClear", new Class<?>[]{pendingClear.getClass()}, pendingClear);
      require(!bool(ahmet, "recoveryBlocked") && pendingOperations(), "pending-only guard setup is invalid");
      int[] pendingTicks = {0};
      await("pending-only-active-work-hold", 40, () -> {
        assertHeldTask(taskId, eligible, Material.DIRT, collected, cargo, chest, start,
            "pending-only guard allowed mutation or route advancement");
        return ++pendingTicks[0] >= 20;
      }, () -> {
        try {
          assertHeldTask(taskId, eligible, Material.DIRT, collected, cargo, chest, start,
              "pending-only guard allowed mutation or route advancement");
          require(!bool(ahmet, "recoveryBlocked") && pendingOperations(),
              "pending-only guard was conflated with the recovery lock");
          invoke(journal, "discardPendingClear");
          require(!pendingOperations(), "could not remove controlled pending journal record");
          // Same callback and server tick: isolate the recovery flag from the pending-record guard.
          set(ahmet, "recoveryBlocked", true);
          int[] recoveryTicks = {0};
          await("recovery-only-active-work-hold", 40, () -> {
            assertHeldTask(taskId, eligible, Material.DIRT, collected, cargo, chest, start,
                "recovery-only guard allowed mutation or route advancement");
            return ++recoveryTicks[0] >= 20;
          }, () -> {
            try {
              assertHeldTask(taskId, eligible, Material.DIRT, collected, cargo, chest, start,
                  "recovery-only guard allowed mutation or route advancement");
              require(bool(ahmet, "recoveryBlocked") && !pendingOperations(),
                  "recovery-only guard still had a pending journal record");
              set(ahmet, "recoveryBlocked", false);
              await("unlocked-positive-control-harvest", 250,
                  () -> integer(task, "collected") == 1 && integer(task, "cargo") == 1
                      && bool(task, "active") && eligible.getType() == Material.AIR,
                  () -> {
                    try {
                      require(string(task, "taskId").equals(taskId) && !bool(ahmet, "recoveryBlocked")
                          && !pendingOperations() && journalHasOperations(),
                          "positive control did not resume the same task with a committed harvest");
                      getLogger().info("AHMET_GOAL_STAGE_PASS name=unlocked-positive-control-harvest sameTask=true"
                          + " collected=1 cargo=1 committedHistory=true liveProviderCalls=0");
                      complete();
                    } catch (Throwable error) { fail(error); }
                  });
            } catch (Throwable error) { fail(error); }
          });
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private void assertHeldTask(String taskId, Block eligible, Material material, int collected, int cargo,
      int chest, Location start, String failure) {
    require(string(task, "taskId").equals(taskId) && bool(task, "active")
        && integer(task, "collected") == collected && integer(task, "cargo") == cargo
        && eligible.getType() == material && countChest() == chest
        && start.distanceSquared(npc.getEntity().getLocation()) < 0.0001
        && !npc.getNavigator().isNavigating(), failure);
  }

  private void await(String name, int timeoutTicks, BooleanSupplier condition, Runnable next) {
    if (terminal) return;
    stage = name; elapsed = 0;
    if (name.equals("first-exploration-leg-start")) {
      lastExplorationDiagnosticKey = null;
      explorationStateDiagnosticLines = 0;
      explorationPeriodicDiagnosticLines = 0;
    }
    getLogger().info("AHMET_GOAL_STAGE_START name=" + name + " timeoutTicks=" + timeoutTicks);
    Bukkit.getScheduler().runTaskTimer(this, timer -> {
      if (terminal) { timer.cancel(); return; }
      try {
        sampleMovement(); elapsed++;
        boolean stageTimeout = elapsed >= timeoutTicks;
        if (name.equals("first-exploration-leg-start")) {
          recordExplorationDiagnostic(elapsed, stageTimeout || (elapsed % 50 == 0));
        }
        if (condition.getAsBoolean()) {
          timer.cancel(); getLogger().info("AHMET_GOAL_STAGE_PASS name=" + name + " ticks=" + elapsed); next.run();
        } else if (stageTimeout) {
          timer.cancel(); throw new IllegalStateException("stage timeout=" + name + " ticks=" + elapsed
              + (name.equals("first-exploration-leg-start")
                  ? " state=" + explorationDiagnosticSnapshot()
                  : " location=" + loc(npc == null || !npc.isSpawned() ? null : npc.getEntity().getLocation())));
        }
      } catch (Throwable error) { timer.cancel(); fail(error); }
    }, 1L, 1L);
  }

  private void recordExplorationDiagnostic(int ticks, boolean periodic) {
    String snapshot = explorationDiagnosticSnapshot();
    String key = explorationDiagnosticKey(snapshot);
    if (periodic) {
      // Reserve eight of the 24 records for six 50-tick samples, the timeout snapshot, and one spare.
      if (explorationPeriodicDiagnosticLines >= 8) return;
      explorationPeriodicDiagnosticLines++;
    } else {
      if (key.equals(lastExplorationDiagnosticKey) || explorationStateDiagnosticLines >= 16) return;
      explorationStateDiagnosticLines++;
    }
    lastExplorationDiagnosticKey = key;
    getLogger().info("AHMET_GOAL_EXPLORATION_DIAG stageTicks=" + ticks + " reason="
        + (periodic ? "periodic-or-timeout" : "state-change") + " " + snapshot);
  }

  private String explorationDiagnosticSnapshot() {
    Object scanner = fieldQuiet(ahmet, "targetScanner");
    Object candidates = fieldQuiet(ahmet, "scanCandidates");
    Object result = fieldQuiet(ahmet, "scanResult");
    Location current = npc == null || !npc.isSpawned() ? null : npc.getEntity().getLocation();
    Location plannerCandidate = (Location) fieldQuiet(ahmet, "explorationTarget");
    Location pluginTarget = (Location) fieldQuiet(ahmet, "navigationGoal");
    Location nativeTarget = npc == null || !npc.isSpawned() ? null : npc.getNavigator().getTargetAsLocation();
    Location currentHome = (Location) fieldQuiet(ahmet, "home");
    Location protectedArea = (Location) fieldQuiet(ahmet, "protectedHome");
    double candidateHomeDistanceSquared = horizontalDistanceSquared(plannerCandidate, currentHome);
    double currentHomeDistanceSquared = horizontalDistanceSquared(current, currentHome);
    double snappedHomeDistanceSquared = plannerCandidate == null || currentHome == null ? Double.NaN
        : horizontalDistanceSquared(new Location(world, Math.floor(plannerCandidate.getX()) + 0.5,
            plannerCandidate.getY(), Math.floor(plannerCandidate.getZ()) + 0.5), currentHome);
    int maxDistance = ahmet == null ? -1 : ahmet.getConfig().getInt("options.max-distance-from-home", -1);
    return "attempts=" + integer(ahmet, "explorationAttempts")
        + " purpose=" + fieldQuiet(ahmet, "navigationPurpose")
        + " currentGoal=" + controllerGoalSafe()
        + " task={active:" + bool(task, "active") + ",id:" + string(task, "taskId")
        + ",collected:" + integer(task, "collected") + ",cargo:" + integer(task, "cargo") + "}"
        + " current=" + precise(current) + " home=" + precise(currentHome) + " protectedHome=" + precise(protectedArea)
        + " maxDistance=" + maxDistance + " currentHomeDistSq=" + numberText(currentHomeDistanceSquared)
        + " plannerCandidate=" + precise(plannerCandidate)
        + " candidateHomeDistSq=" + numberText(candidateHomeDistanceSquared)
        + " candidateCenterHomeDistSq=" + numberText(snappedHomeDistanceSquared)
        + " pluginTarget=" + precise(pluginTarget) + " nativeNavigating=" + (npc != null && npc.isSpawned() && npc.getNavigator().isNavigating())
        + " nativeTarget=" + precise(nativeTarget)
        + " recoveryBlocked=" + bool(ahmet, "recoveryBlocked")
        + " scan={complete:" + scannerMethod(scanner, "complete") + ",visited:" + scannerMethod(scanner, "visited")
        + ",evaluated:" + bool(ahmet, "scanEvaluated") + ",productionCandidate:" + bool(ahmet, "productionCandidateAvailable")
        + ",candidates:" + collectionSize(candidates) + ",result:" + collectionSize(result) + "}";
  }

  private String explorationDiagnosticKey(String snapshot) {
    // Omit current position so ordinary path movement does not flood the capped state-change trace.
    return "attempts=" + integer(ahmet, "explorationAttempts")
        + "|purpose=" + fieldQuiet(ahmet, "navigationPurpose")
        + "|goal=" + controllerGoalSafe()
        + "|plannerCandidate=" + precise((Location) fieldQuiet(ahmet, "explorationTarget"))
        + "|pluginTarget=" + precise((Location) fieldQuiet(ahmet, "navigationGoal"))
        + "|native=" + (npc != null && npc.isSpawned() && npc.getNavigator().isNavigating())
        + "|nativeTarget=" + precise(npc == null || !npc.isSpawned() ? null : npc.getNavigator().getTargetAsLocation())
        + "|scanComplete=" + scannerMethod(fieldQuiet(ahmet, "targetScanner"), "complete");
  }

  private String controllerGoalSafe() {
    try { return String.valueOf(controllerGoal()); }
    catch (Throwable ignored) { return "UNKNOWN"; }
  }

  private static String scannerMethod(Object scanner, String name) {
    if (scanner == null) return "UNKNOWN";
    try { return String.valueOf(invoke(scanner, name)); }
    catch (Exception ignored) { return "UNKNOWN"; }
  }

  private static int collectionSize(Object value) {
    return value instanceof java.util.Collection<?> collection ? collection.size() : -1;
  }

  private static double horizontalDistanceSquared(Location first, Location second) {
    if (first == null || second == null || !first.getWorld().equals(second.getWorld())) return Double.NaN;
    double dx = first.getX() - second.getX(); double dz = first.getZ() - second.getZ();
    return dx * dx + dz * dz;
  }

  private static String precise(Location value) {
    return value == null ? "null" : value.getWorld().getName() + "(" + numberText(value.getX()) + ","
        + numberText(value.getY()) + "," + numberText(value.getZ()) + ")";
  }

  private static String numberText(double value) {
    return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.4f", value) : "NaN";
  }

  private void sampleMovement() {
    if (npc == null || !npc.isSpawned()) return;
    Location now = npc.getEntity().getLocation();
    if (lastLocation != null && lastLocation.getWorld().equals(now.getWorld())) {
      double step = lastLocation.distance(now); if (step > 0.001) walkedTicks++;
      walked += step; maxStep = Math.max(maxStep, step);
    }
    lastLocation = now.clone();
  }

  @EventHandler public void onNpcTeleport(NPCTeleportEvent event) {
    if (normalMovementMonitoring && npc != null && event.getNPC().getId() == npc.getId()) teleports++;
  }

  private void complete() {
    require(teleports == 0, "Citizens emitted normal-operation teleport events: " + teleports);
    require(walkedTicks > 0 && maxStep <= 1.5, "sampled NPC movement step exceeded 1.5 blocks/tick or no walking was observed");
    if (terminal) return; terminal = true;
    if (deadline != null) deadline.cancel();
    stopFixtureAdvancement();
    getLogger().info("AHMET_GOAL_RUNTIME_PROBE_PASS npcType=" + npc.getEntity().getType()
        + " walkingBlocks=" + rounded(walked) + " walkingTicks=" + walkedTicks + " maxStep=" + rounded(maxStep)
        + " spawnExcluded=true normalTeleports=" + teleports + " maxStepLimit=1.5 deterministicMockDecisions=" + decisionCalls.get()
        + " liveProviderCalls=0 recoveryEvidence=injected-lock-only");
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private void fail(Throwable error) {
    if (terminal) return; terminal = true;
    if (deadline != null) deadline.cancel();
    stopFixtureAdvancement();
    getLogger().severe("AHMET_GOAL_RUNTIME_PROBE_FAIL stage=" + stage + " reason="
        + error.getClass().getSimpleName() + ": " + safeMessage(error));
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private void stopFixtureAdvancement() {
    try { if (npc != null && npc.isSpawned()) npc.getNavigator().cancelNavigation(); }
    catch (Throwable ignored) { }
    try { if (mockBridge != null) mockBridge.stop(0); } catch (Throwable ignored) { }
    try { if (bridgeExecutor != null) bridgeExecutor.shutdownNow(); } catch (Throwable ignored) { }
  }

  private void startMockBridge() throws Exception {
    mockBridge = HttpServer.create(new InetSocketAddress("127.0.0.1", 3199), 4);
    bridgeExecutor = Executors.newFixedThreadPool(2);
    mockBridge.setExecutor(bridgeExecutor);
    mockBridge.createContext("/api/decide", this::mockDecision);
    mockBridge.createContext("/api/event", exchange -> {
      try {
        boolean allowed = authorized(exchange);
        if (allowed) exchange.getRequestBody().readNBytes(4097);
        respond(exchange, allowed ? 200 : 401, allowed ? "{\"ok\":true}" : "{}");
      } catch (Exception ignored) { }
    });
    mockBridge.start();
  }

  private void mockDecision(HttpExchange exchange) {
    try {
      if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, "{}"); return; }
      byte[] body = exchange.getRequestBody().readNBytes(16_001);
      if (body.length > 16_000) { respond(exchange, 413, "{}"); return; }
      if (!authorized(exchange)) { respond(exchange, 401, "{}"); return; }
      decisionCalls.incrementAndGet();
      respond(exchange, 200, "{\"id\":\"target_a\",\"source\":\"heuristic\",\"model\":\"fixture-mock\"}");
    } catch (Exception ignored) {
      try { respond(exchange, 400, "{}"); } catch (Exception alsoIgnored) { }
    }
  }

  private boolean authorized(HttpExchange exchange) throws Exception {
    return ("Bearer " + bridgeToken).equals(exchange.getRequestHeaders().getFirst("Authorization"));
  }

  private static void respond(HttpExchange exchange, int status, String json) throws Exception {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) { out.write(bytes); }
  }

  private static String safeMessage(Throwable error) {
    String value = error.getMessage(); if (value == null) return "no message";
    return value.replaceAll("(?i)(key|token|authorization|secret)[=: ]+[^ ,;]+", "$1=[redacted]");
  }

  private int countChest() {
    Chest chest = (Chest) chestLocation.getBlock().getState(); Inventory inventory = chest.getInventory();
    int total = 0; for (ItemStack item : inventory.getContents()) if (item != null && !item.getType().isAir()) total += item.getAmount();
    return total;
  }

  private void command(Player actor, String line) {
    messages.clear(); String[] tokens = line.trim().split("\\s+");
    PluginCommand registered = ((JavaPlugin) ahmet).getCommand(tokens[0]);
    require(registered != null, "registered plugin command missing: " + tokens[0]);
    registered.execute((CommandSender) actor, tokens[0], Arrays.copyOfRange(tokens, 1, tokens.length));
    getLogger().info("AHMET_GOAL_COMMAND mode=registered-plugin-command-proxy command=" + line + " replies=" + messages.size());
  }

  private boolean messagesContain(String fragment) {
    return messages.stream().anyMatch(message -> message != null && message.contains(fragment));
  }

  private Player playerProxy(UUID id, boolean admin) {
    InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
      case "hasPermission" -> admin && "ahmet.admin".equals(args[0]);
      case "getUniqueId" -> id;
      case "getWorld" -> world;
      case "getLocation" -> npc == null || !npc.isSpawned() ? home.clone() : npc.getEntity().getLocation().clone();
      case "getTargetBlockExact" -> chestLocation.getBlock();
      case "sendMessage" -> { if (args != null) for (Object arg : args) messages.add(String.valueOf(arg)); yield null; }
      case "getName", "getDisplayName" -> admin ? "GoalProbeOwner" : "GoalProbeForeign";
      case "isOnline" -> true;
      case "equals" -> proxy == args[0];
      case "hashCode" -> System.identityHashCode(proxy);
      case "toString" -> "GoalProbe synthetic player";
      default -> primitiveDefault(method.getReturnType());
    };
    return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, handler);
  }

  private static Object primitiveDefault(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) return null;
    if (type == boolean.class) return false; if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0; if (type == short.class) return (short) 0;
    if (type == int.class) return 0; if (type == long.class) return 0L;
    if (type == float.class) return 0f; if (type == double.class) return 0d; return null;
  }

  private static Field findField(Object target, String name) throws Exception {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
      catch (NoSuchFieldException ignored) { }
    }
    throw new NoSuchFieldException(name);
  }
  private static Object field(Object target, String name) throws Exception { return findField(target, name).get(target); }
  private static Object fieldQuiet(Object target, String name) {
    try { return field(target, name); } catch (Exception e) { return null; }
  }
  private static void set(Object target, String name, Object value) throws Exception { findField(target, name).set(target, value); }
  private static boolean bool(Object target, String name) { return (boolean) fieldQuiet(target, name); }
  private static int integer(Object target, String name) { return ((Number) fieldQuiet(target, name)).intValue(); }
  private static long number(Object target, String name) { return ((Number) fieldQuiet(target, name)).longValue(); }
  private static String string(Object target, String name) { return String.valueOf(fieldQuiet(target, name)); }
  private static Object invoke(Object target, String name) throws Exception {
    Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target);
  }
  private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(name, parameterTypes); method.setAccessible(true); return method.invoke(target, args);
  }
  private static boolean invokeBoolean(Object target, String name) throws Exception { return (boolean) invoke(target, name); }
  private boolean pendingOperations() throws Exception {
    return ((java.util.Optional<?>) invoke(journal, "pendingHarvest")).isPresent()
        || ((java.util.Optional<?>) invoke(journal, "pendingDeposit")).isPresent()
        || ((java.util.Optional<?>) invoke(journal, "pendingClear")).isPresent();
  }
  private boolean journalHasOperations() {
    try { return invokeBoolean(journal, "hasOperations"); }
    catch (Exception error) { throw new IllegalStateException("could not read journal state", error); }
  }
  private Object controllerGoal() {
    try { return invoke(controller, "currentGoal"); }
    catch (Exception error) { throw new IllegalStateException("could not read controller goal", error); }
  }
  private Object clearRecord(Block block) throws Exception {
    Class<?> recordType = Class.forName("dev.ahmet.OperationJournal$Clear", true, ahmet.getClass().getClassLoader());
    var constructor = recordType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
    return constructor.newInstance(string(task, "taskId"), world.getName(), block.getX(), block.getY(), block.getZ(), block.getType().name());
  }
  private Object enumValue(String value) throws Exception {
    Class<?> type = Class.forName("dev.ahmet.agent.AgentGoal", true, ahmet.getClass().getClassLoader());
    @SuppressWarnings({"unchecked", "rawtypes"}) Object result = Enum.valueOf((Class) type, value);
    return result;
  }
  private static boolean sameBlock(Location a, Location b) {
    return a != null && b != null && a.getWorld().equals(b.getWorld()) && a.getBlockX() == b.getBlockX()
        && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
  }
  private static String loc(Location value) { return value == null ? "null" : value.getBlockX()+","+value.getBlockY()+","+value.getBlockZ(); }
  private static String rounded(double value) { return String.format(java.util.Locale.ROOT, "%.3f", value); }
  private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
