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
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathStrategy;
import net.citizensnpcs.api.ai.PathfinderType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import net.citizensnpcs.api.event.NPCTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

/** Opt-in, disposable Paper/Citizens runtime probe for enabled production-goal wiring. */
public final class RuntimeGoalProbe extends JavaPlugin implements Listener {
  private static final UUID OWNER = UUID.fromString("6d9ea6da-7fb1-4b4c-a7e3-e8a7d64a0a11");
  private static final UUID OPERATOR = UUID.fromString("e4f09903-86f4-4a7a-bf27-f143b15f46e0");
  private static final UUID FOREIGN = UUID.fromString("b9c82ef1-2079-48bf-83b0-56ca51a0a3ab");
  // 2,400 existing + 100 foreign-wait + 800 resumed-delivery ticks = 3,300; no added margin.
  private static final int DEADLINE_TICKS = 3300;
  private static final int FOREIGN_ROUTE_WAIT_TICKS = 100;
  private static final int CITIZENS_ASTAR_DEFAULT_ITERATIONS_PER_TICK = 250;
  private static final int CITIZENS_ASTAR_DEFAULT_MAX_ITERATIONS = 1024;
  private final boolean budgetControlOnly = Boolean.getBoolean("ahmet.goalBudgetControlZero");
  private final boolean deliveryPathControlOnly = Boolean.getBoolean("ahmet.goalDeliveryPathControl");
  private final boolean activationShamControlOnly = Boolean.getBoolean("ahmet.goalActivationShamControl");
  private boolean pathfinderComparisonStarted;
  private boolean pathfinderTypeChanged;
  private PathfinderType originalControlPathfinder;
  private boolean activationControlStarted;
  private boolean activationTickChanged;
  private boolean activationTickRestored;
  private long originalActivationTick;
  private long activationControlTargetTick;
  private Plugin ahmet;
  private Object task;
  private Object controller;
  private Object journal;
  private NPC npc;
  private NavigatorParameters foreignNavigatorDefaults;
  private double foreignOriginalTeleportMargin;
  private int foreignOriginalStationaryTicks;
  private float foreignOriginalSpeed;
  private boolean foreignNavigatorDefaultsChanged;
  private PathStrategy foreignRouteStrategy;
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
  private final AtomicInteger chatCalls = new AtomicInteger();
  private String bridgeToken;
  private String lastExplorationDiagnosticKey;
  private int explorationStateDiagnosticLines;
  private int explorationPeriodicDiagnosticLines;
  private Location budgetDeliveryWaitStart;
  private long budgetDeliveryWaitStartedAtMillis;
  private String lastBudgetDeliveryDiagnosticKey;
  private int budgetDeliveryStateDiagnosticLines;
  private int budgetDeliveryPeriodicDiagnosticLines;

  @Override public void onEnable() {
    if (!Boolean.getBoolean("ahmet.goalProbe")) {
      getLogger().warning("AHMET_GOAL_PROBE_DISABLED: set -Dahmet.goalProbe=true only for a disposable server");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    if ((budgetControlOnly && deliveryPathControlOnly)
        || (activationShamControlOnly && !deliveryPathControlOnly)) {
      getLogger().severe("AHMET_GOAL_CONTROL_CONFIG_ERROR: choose one diagnostic control mode");
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
          + " productionGoalsEnabled=" + ahmet.getConfig().getBoolean("options.production-goals-enabled")
          + " budgetMode=" + (budgetControlOnly ? "controlled-zero-cap" : "acceptance-32-cap")
          + " deliveryPathControl=" + deliveryPathControlOnly);
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
                      getLogger().info("AHMET_GOAL_NATIVE_PATH_CONTROL_BASELINE phase=known-moving-exploration"
                          + " start=" + precise(firstStart) + " target=" + precise(firstRoute)
                          + " current=" + precise(npc.getEntity().getLocation())
                          + " nativeState={" + nativeMotionSnapshot() + "}");
                      await("first-exploration-leg-arrival", 300,
                          () -> !npc.getNavigator().isNavigating()
                              && "REST_IDLE".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                              && fieldQuiet(ahmet, "navigationGoal") == null, () -> {
                            try {
                              require(integer(ahmet, "explorationAttempts") == 1
                                  && npc.getEntity().getLocation().distance(firstRoute) <= 3.5,
                                  "first exploration leg did not arrive with EXPLORE purpose preserved: target=" + loc(firstRoute));
                              getLogger().info("AHMET_GOAL_STAGE_PASS name=first-exploration-leg start-move-arrival purpose=EXPLORE_RESOURCES target="
                                  + loc(firstRoute) + " arrival=" + loc(npc.getEntity().getLocation())
                                  + " arrivedNativeState={" + nativeMotionSnapshot() + "}");
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
                                                  () -> {
                                                    require(integer(ahmet, "explorationAttempts") <= 4
                                                        && !"EXPLORE_RESOURCES".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                                                        && !npc.getNavigator().isNavigating(),
                                                        "production admitted another exploration route after leg four");
                                                    return ++noFifthTicks[0] >= 60;
                                                  }, () -> {
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
                      runStage2MandatoryDelivery(taskId, eligible);
                    } catch (Throwable error) { fail(error); }
                  });
            } catch (Throwable error) { fail(error); }
          });
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private void runStage2MandatoryDelivery(String taskId, Block harvestedSource) {
    try {
      ahmet.getConfig().set("options.max-distance-from-home", 32);
      Location controlledHome = new Location(world, 12.5, 120, 12.5);
      require(controlledHome.getBlock().isPassable()
          && controlledHome.clone().subtract(0, 1, 0).getBlock().getType() == Material.SMOOTH_STONE
          && controlledHome.clone().add(0, 1, 0).getBlock().isPassable()
          && npc.getEntity().getLocation().distance(controlledHome) > 4.0
          && chestLocation.distance(controlledHome) > 4.0,
          "controlled idle-home route destination is not safe or sufficiently far");
      set(ahmet, "home", controlledHome);
      set(ahmet, "protectedHome", null);
      set(ahmet, "protectHomeRadius", 0);
      invoke(ahmet, "persist");

      int collected = integer(task, "collected");
      int cargo = integer(task, "cargo");
      require(bool(task, "active") && collected == 1 && cargo == 1
          && string(task, "taskId").equals(taskId) && harvestedSource.getType() == Material.AIR,
          "stage-two budget scenario did not start with the actual partial harvest");
      set(task, "requested", collected); // Controlled readiness setup; cargo and collection remain production-earned.
      int productiveCap = budgetControlOnly ? 0 : 32;
      set(controller, "productiveTransitionCount", productiveCap);
      require(bool(task, "active") && integer(task, "collected") == collected && integer(task, "cargo") == cargo,
          "productive-budget setup changed task counters");
      int chestBefore = countChest();
      int depositedBefore = integer(task, "deposited");
      long generationBefore = number(controller, "jobGeneration");
      String budgetRouteStage = deliveryPathControlOnly ? "native-path-control-delivery-route"
          : budgetControlOnly ? "budget-control-zero-native-route" : "budget32-mandatory-delivery-native-route";
      String budgetFinishStage = budgetControlOnly
          ? "budget-control-zero-delivery-finish" : "budget32-mandatory-delivery-finish";
      String modeName = deliveryPathControlOnly ? "native-path-control"
          : budgetControlOnly ? "budget-control-zero" : "budget32-mandatory-delivery";
      getLogger().info("AHMET_GOAL_STAGE_START name=" + modeName
          + " controlledReady=true productiveTransitions=" + productiveCap + " cargo=" + cargo);

      await(budgetRouteStage, 180,
          () -> npc.getNavigator().isNavigating() && "DELIVER_CARGO".equals(String.valueOf(controllerGoal())), () -> {
            try {
              require(integer(controller, "productiveTransitionCount") == productiveCap
                  && bool(task, "active") && integer(task, "cargo") == cargo
                  && integer(task, "collected") == collected && string(task, "taskId").equals(taskId),
                  "productive cap changed before mandatory delivery started");
              Location nativeTarget = assertNativeChestRoute();
              getLogger().info("AHMET_GOAL_STAGE_PASS name=" + budgetRouteStage
                  + " goal=DELIVER_CARGO nativeTarget=" + precise(nativeTarget)
                  + " productiveTransitions=" + productiveCap + " controlledReadyState=true");
              if (deliveryPathControlOnly) {
                runNativeDeliveryPathControl(taskId, collected, cargo, chestBefore, depositedBefore,
                    generationBefore, productiveCap, harvestedSource, nativeTarget);
                return;
              }
              await(budgetFinishStage, 1200,
                  () -> !bool(task, "active") && integer(task, "cargo") == 0, () -> {
                    try {
                      require(countChest() == chestBefore + cargo
                          && integer(task, "deposited") == depositedBefore + cargo
                          && integer(task, "collected") == collected && integer(task, "cargo") == 0
                          && string(task, "taskId").equals(taskId)
                          && number(controller, "jobGeneration") > generationBefore
                          && integer(controller, "productiveTransitionCount") == 0
                          && harvestedSource.getType() == Material.AIR,
                          "mandatory delivery at the productive cap did not conserve the harvested item");
                      getLogger().info("AHMET_GOAL_STAGE_PASS name=" + budgetFinishStage + " deposited="
                          + cargo + " productiveTransitionsBeforeFinish=" + productiveCap
                          + " resetOnTaskGeneration=true sourceUnchanged=true");
                      if (budgetControlOnly) {
                        completeBudgetControl(taskId, productiveCap);
                      } else {
                        createIdleHomeRoute("idle-home-route-for-invalid-command", this::testRejectedAdmissionPreservesHomeRoute);
                      }
                    } catch (Throwable error) { fail(error); }
                  });
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void completeBudgetControl(String taskId, int productiveCap) {
    require(teleports == 0 && walkedTicks > 0 && maxStep <= 1.5,
        "zero-cap delivery control violated the normal movement oracle");
    if (terminal) return;
    terminal = true;
    if (deadline != null) deadline.cancel();
    stopFixtureAdvancement();
    getLogger().info("AHMET_GOAL_BUDGET_CONTROL_RESULT outcome=actual-delivery completed=true"
        + " taskId=" + taskId + " productiveTransitionsAtAdmission=" + productiveCap
        + " cargo=" + integer(task, "cargo") + " deposited=" + integer(task, "deposited")
        + " liveProviderCalls=0 overallAcceptance=false");
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  /** Diagnostic only: hold the plugin step, observe one Citizens chest route, then one adjacent standing route if stalled. */
  private void runNativeDeliveryPathControl(String taskId, int collected, int cargo, int chestBefore,
      int depositedBefore, long generationBefore, int productiveCap, Block harvestedSource,
      Location chestNativeTarget) {
    try {
      require(deliveryPathControlOnly && !budgetControlOnly && productiveCap == 32,
          "native path control must use its dedicated 32-cap diagnostic mode");
      require(bool(task, "active") && Boolean.parseBoolean(safeReadyToDeliver()) && integer(task, "cargo") == cargo
          && integer(task, "collected") == collected && string(task, "taskId").equals(taskId)
          && integer(controller, "productiveTransitionCount") == productiveCap,
          "native path control did not enter with the accepted delivery state");
      require(chestNativeTarget != null && sameBlock(chestNativeTarget, npc.getNavigator().getTargetAsLocation()),
          "native path control lost the production-created chest target before observation");
      Location chestStart = npc.getEntity().getLocation().clone();
      Location pluginChestTarget = chestLocation.clone().add(0.5, 0, 0.5);
      double chestDistanceAtStart = chestStart.distance(chestNativeTarget);
      double chestWalkedAtStart = walked;
      set(ahmet, "deciding", true); // Pauses only the production controller loop; Citizens navigation continues.
      getLogger().info("AHMET_GOAL_NATIVE_PATH_CONTROL_START taskId=" + taskId
          + " currentGoal=" + controllerGoal() + " purpose=" + fieldQuiet(ahmet, "navigationPurpose")
          + " start=" + precise(chestStart) + " nativeChestTarget=" + precise(chestNativeTarget)
          + " productiveTransitions=" + productiveCap + " pause=deciding-true teleport=false");
      int[] chestTicks = {0};
      await("native-path-chest-target-observe", 100, () -> {
        chestTicks[0]++;
        assertNativePathControlInvariants(taskId, collected, cargo, chestBefore, depositedBefore,
            generationBefore, productiveCap, harvestedSource, pluginChestTarget, chestNativeTarget);
        if (chestTicks[0] % 20 == 0 || chestTicks[0] == 100) {
          logNativePathControlSample("chest", chestTicks[0], chestNativeTarget);
        }
        return chestTicks[0] >= 100;
      }, () -> {
        try {
          Location chestEnd = npc.getEntity().getLocation().clone();
          double sampledWalking = walked - chestWalkedAtStart;
          double distanceToChestTarget = chestEnd.distance(chestNativeTarget);
          double targetDistanceProgress = chestDistanceAtStart - distanceToChestTarget;
          String chestOutcome = distanceToChestTarget <= 2.5 ? "arrived-near-target"
              : sampledWalking > 0.25 ? targetDistanceProgress > 0.25
                  ? "observed-progress-toward-target" : "observed-movement"
                  : "no-sampled-movement-100ticks";
          require(bool(task, "active") && integer(task, "cargo") == cargo
              && integer(task, "collected") == collected && integer(task, "deposited") == depositedBefore
              && string(task, "taskId").equals(taskId)
              && integer(controller, "productiveTransitionCount") == productiveCap
              && countChest() == chestBefore && harvestedSource.getType() == Material.AIR
              && number(controller, "jobGeneration") == generationBefore && teleports == 0,
              "chest route control changed task, cargo, chest, source, generation, or teleport state");
          getLogger().info("AHMET_GOAL_NATIVE_PATH_CONTROL_CHEST_RESULT outcome=" + chestOutcome
              + " ticks=" + chestTicks[0] + " start=" + precise(chestStart) + " end=" + precise(chestEnd)
              + " sampledWalking=" + numberText(sampledWalking)
              + " targetDistanceProgress=" + numberText(targetDistanceProgress)
              + " distanceToTarget=" + numberText(distanceToChestTarget)
              + " nativeNavigating=" + npc.getNavigator().isNavigating()
              + " nativeTarget=" + precise(npc.getNavigator().getTargetAsLocation()));
          if (!"no-sampled-movement-100ticks".equals(chestOutcome)) {
            completeNativePathControl(taskId, productiveCap, chestOutcome, "not-run");
            return;
          }
          runAdjacentStandingPathControl(taskId, collected, cargo, chestBefore, depositedBefore,
              generationBefore, productiveCap, harvestedSource, chestNativeTarget, chestStart);
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private void runAdjacentStandingPathControl(String taskId, int collected, int cargo, int chestBefore,
      int depositedBefore, long generationBefore, int productiveCap, Block harvestedSource,
      Location chestNativeTarget, Location originalPosition) {
    try {
      Location adjacent = new Location(world, chestLocation.getBlockX() + 1.5,
          chestLocation.getBlockY(), chestLocation.getBlockZ() + 0.5);
      Block floor = adjacent.clone().subtract(0, 1, 0).getBlock();
      Block feet = adjacent.getBlock();
      Block head = adjacent.clone().add(0, 1, 0).getBlock();
      require(floor.getType().isSolid() && feet.isPassable() && head.isPassable()
          && !sameBlock(adjacent, chestLocation),
          "adjacent standing control location is not a clear supported cell: " + precise(adjacent));
      npc.getNavigator().cancelNavigation();
      npc.getNavigator().getDefaultParameters().destinationTeleportMargin(-1).stationaryTicks(-1);
      npc.getNavigator().setTarget(adjacent);
      Location adjacentNativeTarget = npc.getNavigator().getTargetAsLocation();
      require(adjacentNativeTarget != null && sameLocationWithin(adjacent, adjacentNativeTarget, 0.01),
          "Citizens did not retain the explicit adjacent standing target; actual=" + precise(adjacentNativeTarget));
      Location adjacentStart = npc.getEntity().getLocation().clone();
      Location pluginChestTarget = chestLocation.clone().add(0.5, 0, 0.5);
      double adjacentDistanceAtStart = adjacentStart.distance(adjacentNativeTarget);
      double adjacentWalkedAtStart = walked;
      getLogger().info("AHMET_GOAL_NATIVE_PATH_CONTROL_ADJACENT_START taskId=" + taskId
          + " from=" + precise(adjacentStart) + " target=" + precise(adjacentNativeTarget)
          + " floor=" + floor.getType() + " feet=" + feet.getType() + " head=" + head.getType()
          + " afterChestOutcome=no-sampled-movement-100ticks teleport=false");
      int[] adjacentTicks = {0};
      await("native-path-adjacent-standing-observe", 100, () -> {
        adjacentTicks[0]++;
        assertNativePathControlInvariants(taskId, collected, cargo, chestBefore, depositedBefore,
            generationBefore, productiveCap, harvestedSource, pluginChestTarget, adjacentNativeTarget);
        if (adjacentTicks[0] % 20 == 0 || adjacentTicks[0] == 100) {
          logNativePathControlSample("adjacent", adjacentTicks[0], adjacentNativeTarget);
        }
        return adjacentTicks[0] >= 100;
      }, () -> {
        try {
          Location adjacentEnd = npc.getEntity().getLocation().clone();
          double sampledWalking = walked - adjacentWalkedAtStart;
          double remaining = adjacentEnd.distance(adjacentNativeTarget);
          double targetDistanceProgress = adjacentDistanceAtStart - remaining;
          String outcome = remaining <= 1.5 ? "arrived-near-target"
              : sampledWalking > 0.25 ? targetDistanceProgress > 0.25
                  ? "observed-progress-toward-target" : "observed-movement"
                  : "no-sampled-movement-100ticks";
          require(bool(task, "active") && integer(task, "cargo") == cargo
              && integer(task, "collected") == collected && integer(task, "deposited") == depositedBefore
              && string(task, "taskId").equals(taskId)
              && integer(controller, "productiveTransitionCount") == productiveCap
              && countChest() == chestBefore && harvestedSource.getType() == Material.AIR
              && number(controller, "jobGeneration") == generationBefore && teleports == 0
              && bool(ahmet, "deciding"),
              "adjacent route control changed task, cargo, chest, source, generation, pause, or teleport state");
          getLogger().info("AHMET_GOAL_NATIVE_PATH_CONTROL_ADJACENT_RESULT outcome=" + outcome
              + " ticks=" + adjacentTicks[0] + " start=" + precise(adjacentStart) + " end=" + precise(adjacentEnd)
              + " sampledWalking=" + numberText(sampledWalking)
              + " targetDistanceProgress=" + numberText(targetDistanceProgress)
              + " distanceToTarget=" + numberText(remaining)
              + " nativeNavigating=" + npc.getNavigator().isNavigating()
              + " nativeTarget=" + precise(npc.getNavigator().getTargetAsLocation())
              + " originalPosition=" + precise(originalPosition) + " chestTarget=" + precise(chestNativeTarget));
          if ("no-sampled-movement-100ticks".equals(outcome)) {
            runCitizensPathfinderControl(taskId, collected, cargo, chestBefore, depositedBefore,
                generationBefore, productiveCap, harvestedSource, chestNativeTarget, adjacent,
                originalPosition);
          } else {
            completeNativePathControl(taskId, productiveCap, "no-sampled-movement-100ticks", outcome);
          }
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  /** Orthogonal control: same adjacent cell, Citizens A* planner instead of Minecraft path strategy. */
  private void runCitizensPathfinderControl(String taskId, int collected, int cargo, int chestBefore,
      int depositedBefore, long generationBefore, int productiveCap, Block harvestedSource,
      Location chestNativeTarget, Location adjacent, Location originalPosition) {
    try {
      int iterationsPerTick = readCitizensAStarSetting("CITIZENS_PATHFINDER_ASTAR_ITERATIONS_PER_TICK");
      int maxIterations = readCitizensAStarSetting("CITIZENS_PATHFINDER_MAXIMUM_ASTAR_ITERATIONS");
      require(iterationsPerTick > 0 && iterationsPerTick <= CITIZENS_ASTAR_DEFAULT_ITERATIONS_PER_TICK
          && maxIterations > 0 && maxIterations <= CITIZENS_ASTAR_DEFAULT_MAX_ITERATIONS,
          "Citizens A* effective work settings exceed the pinned finite defaults; perTick=" + iterationsPerTick
              + " maxIterations=" + maxIterations + " caps=" + CITIZENS_ASTAR_DEFAULT_ITERATIONS_PER_TICK
              + "/" + CITIZENS_ASTAR_DEFAULT_MAX_ITERATIONS);
      var navigator = npc.getNavigator();
      var defaults = navigator.getDefaultParameters();
      originalControlPathfinder = defaults.pathfinderType();
      require(originalControlPathfinder == PathfinderType.MINECRAFT
          && defaults.destinationTeleportMargin() == -1 && defaults.stationaryTicks() == -1,
          "alternate pathfinder control requires the existing no-teleport MINECRAFT baseline; pathfinder="
              + originalControlPathfinder + " teleportMargin=" + defaults.destinationTeleportMargin()
              + " stationaryTicks=" + defaults.stationaryTicks());
      require(bool(ahmet, "deciding") && npc.getEntity().getLocation().distanceSquared(originalPosition) < 0.0001
          && integer(task, "cargo") == cargo && integer(task, "collected") == collected
          && string(task, "taskId").equals(taskId),
          "alternate pathfinder control did not retain the stationary adjacent-route state");

      pathfinderComparisonStarted = true;
      navigator.cancelNavigation();
      require(!navigator.isNavigating() && navigator.getLocalParameters() == defaults,
          "Citizens did not return to default parameters after cancelling the native route");
      // Carry the existing fail-closed navigation margins forward; the only changed parameter is pathfinderType.
      defaults.pathfinderType(PathfinderType.CITIZENS);
      pathfinderTypeChanged = true;
      navigator.setTarget(adjacent);
      Location alternateTarget = navigator.getTargetAsLocation();
      Object strategy = navigator.getPathStrategy();
      require(navigator.isNavigating() && alternateTarget != null
          && sameLocationWithin(adjacent, alternateTarget, 0.01)
          && strategy != null && "net.citizensnpcs.npc.ai.AStarNavigationStrategy".equals(strategy.getClass().getName())
          && navigator.getLocalParameters().pathfinderType() == PathfinderType.CITIZENS
          && defaults.destinationTeleportMargin() == -1 && defaults.stationaryTicks() == -1,
          "Citizens A* did not preserve the adjacent target, strategy, or no-teleport margins");

      Location pluginChestTarget = chestLocation.clone().add(0.5, 0, 0.5);
      Location alternateStart = npc.getEntity().getLocation().clone();
      double alternateDistanceAtStart = alternateStart.distance(alternateTarget);
      double alternateWalkedAtStart = walked;
      getLogger().info("AHMET_GOAL_PATHFINDER_COMPARISON_START taskId=" + taskId
          + " baselinePathfinder=MINECRAFT alternatePathfinder=CITIZENS strategy=" + strategy.getClass().getName()
          + " from=" + precise(alternateStart) + " target=" + precise(alternateTarget)
          + " astarPerTick=" + iterationsPerTick + " astarMaxIterations=" + maxIterations
          + " teleportMargin=" + defaults.destinationTeleportMargin()
          + " stationaryTicks=" + defaults.stationaryTicks() + " parameterChanges=pathfinderType-only");
      int[] alternateTicks = {0};
      int[] stoppedAtTick = {-1};
      await("native-path-citizens-astar-adjacent-observe", 100, () -> {
        alternateTicks[0]++;
        assertNativePathControlInvariants(taskId, collected, cargo, chestBefore, depositedBefore,
            generationBefore, productiveCap, harvestedSource, pluginChestTarget, alternateTarget);
        boolean navigating = navigator.isNavigating();
        PathStrategy currentStrategy = navigator.getPathStrategy();
        require(navigator.getLocalParameters().pathfinderType() == PathfinderType.CITIZENS
            && (!navigating || (currentStrategy == strategy && currentStrategy != null
                && "net.citizensnpcs.npc.ai.AStarNavigationStrategy".equals(currentStrategy.getClass().getName())))
            && defaults.destinationTeleportMargin() == -1 && defaults.stationaryTicks() == -1,
            "Citizens A* route or no-teleport path parameters changed during observation");
        if (!navigating && stoppedAtTick[0] < 0) {
          stoppedAtTick[0] = alternateTicks[0];
          getLogger().info("AHMET_GOAL_PATHFINDER_COMPARISON_ALT_STOPPED tick=" + stoppedAtTick[0]
              + " position=" + precise(npc.getEntity().getLocation())
              + " distanceToTarget=" + numberText(npc.getEntity().getLocation().distance(alternateTarget))
              + " strategy=" + (currentStrategy == null ? "null" : currentStrategy.getClass().getName())
              + " continueObservingUntilTick=100");
        }
        if (alternateTicks[0] % 20 == 0 || alternateTicks[0] == 100) {
          logNativePathControlSample("citizens-astar-adjacent", alternateTicks[0], alternateTarget);
        }
        return alternateTicks[0] >= 100;
      }, () -> {
        try {
          Location alternateEnd = npc.getEntity().getLocation().clone();
          double sampledWalking = walked - alternateWalkedAtStart;
          double remaining = alternateEnd.distance(alternateTarget);
          double targetDistanceProgress = alternateDistanceAtStart - remaining;
          boolean routeStillActive = navigator.isNavigating() && navigator.getPathStrategy() == strategy;
          String outcome = remaining <= 1.5 ? "arrived-near-target"
              : sampledWalking > 0.25 ? targetDistanceProgress > 0.25
                  ? "observed-progress-toward-target" : "observed-movement"
              : stoppedAtTick[0] >= 0 ? "path-stopped-before-target" : "no-sampled-movement-100ticks";
          assertNativePathControlInvariants(taskId, collected, cargo, chestBefore, depositedBefore,
              generationBefore, productiveCap, harvestedSource, pluginChestTarget, alternateTarget);
          require(alternateTicks[0] == 100 && integer(task, "cargo") == cargo
              && integer(task, "collected") == collected && integer(task, "deposited") == depositedBefore
              && countChest() == chestBefore && harvestedSource.getType() == Material.AIR
              && number(controller, "jobGeneration") == generationBefore && teleports == 0,
              "Citizens A* comparison changed task, item, world, generation, or teleport state");
          getLogger().info("AHMET_GOAL_PATHFINDER_COMPARISON_ALT_RESULT engine=CITIZENS"
              + " outcome=" + outcome + " ticks=" + alternateTicks[0]
              + " start=" + precise(alternateStart) + " end=" + precise(alternateEnd)
              + " target=" + precise(alternateTarget) + " sampledWalking=" + numberText(sampledWalking)
              + " targetDistanceProgress=" + numberText(targetDistanceProgress)
              + " astarPerTick=" + iterationsPerTick + " astarMaxIterations=" + maxIterations
              + " taskAndWorldUnchanged=true teleports=" + teleports);
          if ("no-sampled-movement-100ticks".equals(outcome) && routeStillActive) {
            runActivationWindowControl(taskId, collected, cargo, chestBefore, depositedBefore,
                generationBefore, productiveCap, harvestedSource, pluginChestTarget, alternateTarget,
                (PathStrategy) strategy, iterationsPerTick, maxIterations);
          } else {
            if ("no-sampled-movement-100ticks".equals(outcome)) outcome = "path-stopped-before-activation-control";
            completePathfinderComparisonControl(taskId, productiveCap, iterationsPerTick, maxIterations,
                "no-sampled-movement-100ticks", "no-sampled-movement-100ticks", outcome);
          }
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private int readCitizensAStarSetting(String settingName) throws Exception {
    ClassLoader citizensLoader = NPC.class.getClassLoader();
    Class<?> settingType = Class.forName("net.citizensnpcs.Settings$Setting", false, citizensLoader);
    Object setting = settingType.getField(settingName).get(null);
    return ((Number) settingType.getMethod("asInt").invoke(setting)).intValue();
  }

  /** The only activation mutation in this opt-in disposable control: extend the pinned Paper field. */
  private void runActivationWindowControl(String taskId, int collected, int cargo, int chestBefore,
      int depositedBefore, long generationBefore, int productiveCap, Block harvestedSource,
      Location expectedPluginTarget, Location expectedNativeTarget, PathStrategy expectedStrategy,
      int iterationsPerTick, int maxIterations) {
    try {
      var navigator = npc.getNavigator();
      require(deliveryPathControlOnly && pathfinderComparisonStarted && pathfinderTypeChanged
          && bool(ahmet, "deciding") && navigator.isNavigating()
          && navigator.getPathStrategy() == expectedStrategy
          && "net.citizensnpcs.npc.ai.AStarNavigationStrategy".equals(expectedStrategy.getClass().getName())
          && navigator.getLocalParameters().pathfinderType() == PathfinderType.CITIZENS
          && sameLocationWithin(expectedNativeTarget, navigator.getTargetAsLocation(), 0.01),
          "activation control requires the same active Citizens A* adjacent route");
      require(bool(task, "active") && string(task, "taskId").equals(taskId)
          && integer(task, "collected") == collected && integer(task, "cargo") == cargo
          && integer(task, "deposited") == depositedBefore && countChest() == chestBefore
          && harvestedSource.getType() == Material.AIR && number(controller, "jobGeneration") == generationBefore
          && integer(controller, "productiveTransitionCount") == productiveCap && teleports == 0,
          "activation control did not preserve the ready-cargo task and source state");

      ClassLoader paperLoader = NPC.class.getClassLoader();
      Class<?> nmsEntityType = Class.forName("net.minecraft.world.entity.Entity", false, paperLoader);
      Class<?> serverType = Class.forName("net.minecraft.server.MinecraftServer", false, paperLoader);
      Object handle = getPaperEntityHandle(npc.getEntity());
      var activatedTickField = nmsEntityType.getField("activatedTick");
      int serverTick = serverType.getField("currentTick").getInt(null);
      boolean defaultActivation = nmsEntityType.getField("defaultActivationState").getBoolean(handle);
      int entityTickCount = nmsEntityType.getField("tickCount").getInt(handle);
      long currentActivatedTick = activatedTickField.getLong(handle);
      boolean temporarilyActive = nmsEntityType.getField("isTemporarilyActive").getBoolean(handle);
      Object activationType = nmsEntityType.getField("activationType").get(handle);
      require(!defaultActivation && entityTickCount >= 200 && currentActivatedTick < serverTick,
          "activation comparison is inconclusive for default or startup-active entity state");
      originalActivationTick = currentActivatedTick;
      activationControlTargetTick = activationShamControlOnly
          ? currentActivatedTick : (long) serverTick + 200L;
      activationControlStarted = true;
      if (!activationShamControlOnly) {
        activationTickChanged = true; // Cleanup restores even if reflection write/verification throws midway.
        activatedTickField.setLong(handle, activationControlTargetTick);
      }
      require(activatedTickField.getLong(handle) == activationControlTargetTick,
          "Paper activatedTick control value did not match its expected value");

      Location start = npc.getEntity().getLocation().clone();
      double distanceAtStart = start.distance(expectedNativeTarget);
      double walkedAtStart = walked;
      getLogger().info(activationControlMarker("START") + " taskId=" + taskId
          + " engine=CITIZENS strategy=AStarNavigationStrategy"
          + " start=" + precise(start) + " target=" + precise(expectedNativeTarget)
          + " originalActivatedTick=" + originalActivationTick + " serverTick=" + serverTick
          + " mode=" + (activationShamControlOnly ? "SHAM" : "EXTENDED")
          + " activatedTickWrite=" + (activationShamControlOnly ? "none" : "currentTickPlus200")
          + " assignedActivatedTick=" + activationControlTargetTick
          + " activationType=" + activationType + " defaultActivation=" + defaultActivation
          + " temporarilyActiveBeforeWrite=" + temporarilyActive + " entityTickCount=" + entityTickCount
          + " deciding=true otherChanges=none teleport=false");

      int[] ticks = {0};
      int[] pathStoppedAt = {-1};
      int[] activationExpiredAt = {-1};
      await("native-path-activation-window-observe", 100, () -> {
        ticks[0]++;
        assertNativePathControlInvariants(taskId, collected, cargo, chestBefore, depositedBefore,
            generationBefore, productiveCap, harvestedSource, expectedPluginTarget, expectedNativeTarget);
        boolean navigating = navigator.isNavigating();
        PathStrategy currentStrategy = navigator.getPathStrategy();
        require(navigator.getLocalParameters().pathfinderType() == PathfinderType.CITIZENS
            && (!navigating || (currentStrategy == expectedStrategy
                && sameLocationWithin(expectedNativeTarget, navigator.getTargetAsLocation(), 0.01)))
            && navigator.getDefaultParameters().destinationTeleportMargin() == -1
            && navigator.getDefaultParameters().stationaryTicks() == -1,
            "activation control replaced the active path or changed Citizens route parameters");
        if (!navigating && pathStoppedAt[0] < 0) {
          pathStoppedAt[0] = ticks[0];
          getLogger().info(activationControlMarker("PATH_STOPPED") + " tick=" + ticks[0]
              + " position=" + precise(npc.getEntity().getLocation())
              + " distanceToTarget=" + numberText(npc.getEntity().getLocation().distance(expectedNativeTarget))
              + " continueObservingUntilTick=100 noReissue=true");
        }
        boolean activationStillFuture;
        try { activationStillFuture = paperActivationWindowIsFuture(npc.getEntity()); }
        catch (Exception error) { throw new IllegalStateException("could not read Paper activation window", error); }
        if (!activationStillFuture && activationExpiredAt[0] < 0) {
          activationExpiredAt[0] = ticks[0];
          getLogger().warning(activationControlMarker("WINDOW_EXPIRED") + " tick=" + ticks[0]
              + " continueObservingUntilTick=100");
        }
        if (ticks[0] % 20 == 0 || ticks[0] == 100) {
          logNativePathControlSample(activationShamControlOnly
              ? "activation-sham-astar" : "activation-extended-astar", ticks[0], expectedNativeTarget);
        }
        return ticks[0] >= 100;
      }, () -> {
        try {
          Location end = npc.getEntity().getLocation().clone();
          double sampledWalking = walked - walkedAtStart;
          double remaining = end.distance(expectedNativeTarget);
          double targetProgress = distanceAtStart - remaining;
          String outcome = remaining <= 1.5 ? "arrived-near-target"
              : sampledWalking > 0.25 ? targetProgress > 0.25
                  ? "observed-progress-toward-target" : "observed-movement"
              : "no-sampled-movement-100ticks";
          require(ticks[0] == 100 && bool(task, "active") && string(task, "taskId").equals(taskId)
              && integer(task, "collected") == collected && integer(task, "cargo") == cargo
              && integer(task, "deposited") == depositedBefore && countChest() == chestBefore
              && harvestedSource.getType() == Material.AIR && number(controller, "jobGeneration") == generationBefore
              && integer(controller, "productiveTransitionCount") == productiveCap
              && "DELIVER_CARGO".equals(String.valueOf(controllerGoal()))
              && sameLocationWithin(expectedPluginTarget,
                  (Location) fieldQuiet(ahmet, "navigationGoal"), 0.0001)
              && !bool(ahmet, "recoveryBlocked") && "false".equals(pendingOperationsSafe())
              && teleports == 0 && maxStep <= 1.5,
              "activation window changed task, cargo, source, chest, goal, journal, recovery, or movement bounds");
          getLogger().info(activationControlMarker("OBSERVATION") + " outcome=" + outcome
              + " ticks=" + ticks[0] + " pathStoppedAtTick=" + pathStoppedAt[0]
              + " activationExpiredAtTick=" + activationExpiredAt[0]
              + " start=" + precise(start) + " end=" + precise(end)
              + " target=" + precise(expectedNativeTarget) + " sampledWalking=" + numberText(sampledWalking)
              + " targetProgress=" + numberText(targetProgress)
              + " originalActivatedTick=" + originalActivationTick
              + " mode=" + (activationShamControlOnly ? "SHAM" : "EXTENDED")
              + " activatedTickWrite=" + (activationShamControlOnly ? "none" : "currentTickPlus200")
              + " assignedActivatedTick=" + activationControlTargetTick
              + " cargo=" + integer(task, "cargo") + " deposited=" + integer(task, "deposited")
              + " teleports=" + teleports + " overallAcceptance=false");
          completeActivationControl(taskId, productiveCap, iterationsPerTick, maxIterations, outcome,
              pathStoppedAt[0], activationExpiredAt[0]);
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private Object getPaperEntityHandle(org.bukkit.entity.Entity entity) throws Exception {
    ClassLoader paperLoader = NPC.class.getClassLoader();
    Class<?> impl = Class.forName("net.citizensnpcs.nms.v1_21_R5.util.NMSImpl", false, paperLoader);
    return impl.getMethod("getHandle", org.bukkit.entity.Entity.class).invoke(null, entity);
  }

  private boolean paperActivationWindowIsFuture(org.bukkit.entity.Entity entity) throws Exception {
    ClassLoader paperLoader = NPC.class.getClassLoader();
    Class<?> entityType = Class.forName("net.minecraft.world.entity.Entity", false, paperLoader);
    Class<?> serverType = Class.forName("net.minecraft.server.MinecraftServer", false, paperLoader);
    Object handle = getPaperEntityHandle(entity);
    return entityType.getField("activatedTick").getLong(handle)
        >= serverType.getField("currentTick").getInt(null);
  }

  private void assertNativePathControlInvariants(String taskId, int collected, int cargo, int chestBefore,
      int depositedBefore, long generationBefore, int productiveCap, Block harvestedSource,
      Location expectedPluginTarget, Location expectedNativeTarget) {
    Location currentPluginTarget = (Location) fieldQuiet(ahmet, "navigationGoal");
    boolean navigating = npc.getNavigator().isNavigating();
    Location currentNativeTarget = npc.getNavigator().getTargetAsLocation();
    require(bool(task, "active") && string(task, "taskId").equals(taskId)
        && integer(task, "collected") == collected && integer(task, "cargo") == cargo
        && integer(task, "deposited") == depositedBefore && countChest() == chestBefore
        && chestLocation.getBlock().getType() == Material.CHEST && harvestedSource.getType() == Material.AIR
        && number(controller, "jobGeneration") == generationBefore
        && integer(controller, "productiveTransitionCount") == productiveCap
        && "DELIVER_CARGO".equals(String.valueOf(controllerGoal()))
        && "DELIVER_CARGO".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
        && sameLocationWithin(expectedPluginTarget, currentPluginTarget, 0.0001)
        && (!navigating || sameLocationWithin(expectedNativeTarget, currentNativeTarget, 0.01))
        && bool(ahmet, "deciding") && !bool(ahmet, "recoveryBlocked")
        && "false".equals(pendingOperationsSafe()) && teleports == 0,
        "paused native path control changed task, cargo, chest, source, generation, route target, journal, or teleport state");
  }

  private void logNativePathControlSample(String route, int ticks, Location expectedNativeTarget) {
    Location current = npc.getEntity().getLocation();
    boolean navigating = npc.getNavigator().isNavigating();
    Location actualNativeTarget = npc.getNavigator().getTargetAsLocation();
    String nativeTargetStatus = !navigating ? "navigator-stopped"
        : sameLocationWithin(expectedNativeTarget, actualNativeTarget, 0.01) ? "matches-expected" : "mismatch";
    getLogger().info("AHMET_GOAL_NATIVE_PATH_CONTROL_SAMPLE route=" + route + " ticks=" + ticks
        + " position=" + precise(current) + " currentGoal=" + controllerGoal()
        + " purpose=" + fieldQuiet(ahmet, "navigationPurpose")
        + " pluginTarget=" + precise((Location) fieldQuiet(ahmet, "navigationGoal"))
        + " nativeNavigating=" + navigating
        + " nativeTarget=" + precise(actualNativeTarget)
        + " nativeTargetStatus=" + nativeTargetStatus
        + " deciding=" + bool(ahmet, "deciding")
        + " nativeState={" + nativeMotionSnapshot() + "}");
  }

  /** Bounded, read-only Paper/Citizens state captured on the server thread. */
  private String nativeMotionSnapshot() {
    if (npc == null || !npc.isSpawned()) return "npc=unspawned";
    var entity = npc.getEntity();
    var navigator = npc.getNavigator();
    PathStrategy strategy = navigator.getPathStrategy();
    NavigatorParameters parameters = navigator.getLocalParameters();
    if (parameters == null) parameters = navigator.getDefaultParameters();
    int pathPoints = 0;
    boolean pathTruncated = false;
    var path = strategy == null ? null : strategy.getPath();
    boolean pathAvailable = path != null;
    if (pathAvailable) {
      var iterator = path.iterator();
      while (iterator.hasNext() && pathPoints < 32) { iterator.next(); pathPoints++; }
      pathTruncated = iterator.hasNext();
    }
    Mob mob = entity instanceof Mob value ? value : null;
    var chunk = entity.getChunk();
    var velocity = entity.getVelocity();
    return "entityId=" + entity.getEntityId()
        + ",valid=" + entity.isValid() + ",dead=" + entity.isDead() + ",ticking=" + entity.isTicking()
        + ",onGround=" + entity.isOnGround() + ",gravity=" + entity.hasGravity()
        + ",velocity=" + rounded(velocity.getX()) + "/" + rounded(velocity.getY()) + "/" + rounded(velocity.getZ())
        + ",vehicle=" + entity.isInsideVehicle() + ",frozen=" + entity.isFrozen() + ",freezeTicks=" + entity.getFreezeTicks()
        + ",ai=" + (mob != null && mob.hasAI())
        + ",aware=" + (mob != null && mob.isAware())
        + ",chunk=" + chunk.getX() + "/" + chunk.getZ() + ":loaded=" + chunk.isLoaded()
        + ":entitiesLoaded=" + chunk.isEntitiesLoaded() + ":loadLevel=" + chunk.getLoadLevel()
        + ",navigatorPaused=" + navigator.isPaused() + ",navigating=" + navigator.isNavigating()
        + ",strategy=" + (strategy == null ? "null" : strategy.getClass().getName())
        + ",strategyTarget=" + precise(strategy == null ? null : strategy.getTargetAsLocation())
        + ",strategyDestination=" + precise(strategy == null ? null : strategy.getCurrentDestination())
        + ",strategyCancel=" + (strategy == null ? "null" : strategy.getCancelReason())
        + ",moveControl={" + nativeMoveControlSnapshot(entity) + "}"
        + ",activation={" + nativeActivationSnapshot(entity) + "}"
        + ",movementSpeed={" + movementSpeedSnapshot(mob) + "}"
        + ",collision={" + collisionSnapshot(entity) + "}"
        + ",pathAvailable=" + pathAvailable + ",pathPointsObserved=" + pathPoints + ",pathTruncated=" + pathTruncated
        + ",targetType=" + navigator.getTargetType()
        + ",speed=" + rounded(parameters.speed()) + ",baseSpeed=" + rounded(parameters.baseSpeed())
        + ",speedModifier=" + rounded(parameters.speedModifier())
        + ",stationaryTicks=" + parameters.stationaryTicks()
        + ",pathfinder=" + parameters.pathfinderType();
  }

  /** Read Paper's pinned activation-range fields without invoking its state-mutating checkIfActive method. */
  private String nativeActivationSnapshot(org.bukkit.entity.Entity entity) {
    try {
      ClassLoader paperLoader = NPC.class.getClassLoader();
      Class<?> impl = Class.forName("net.citizensnpcs.nms.v1_21_R5.util.NMSImpl", false, paperLoader);
      Object handle = impl.getMethod("getHandle", org.bukkit.entity.Entity.class).invoke(null, entity);
      Class<?> entityType = Class.forName("net.minecraft.world.entity.Entity", false, paperLoader);
      Class<?> serverType = Class.forName("net.minecraft.server.MinecraftServer", false, paperLoader);
      long activatedTick = entityType.getField("activatedTick").getLong(handle);
      int currentTick = serverType.getField("currentTick").getInt(null);
      return "available=true,type=" + entityType.getField("activationType").get(handle)
          + ",default=" + entityType.getField("defaultActivationState").getBoolean(handle)
          + ",activatedTick=" + activatedTick + ",serverTick=" + currentTick
          + ",temporarilyActive=" + entityType.getField("isTemporarilyActive").getBoolean(handle)
          + ",entityTickCount=" + entityType.getField("tickCount").getInt(handle);
    } catch (ReflectiveOperationException | LinkageError error) {
      return "available=false,reason=" + error.getClass().getSimpleName();
    }
  }

  /** Read-only view of the task route guard and Citizens activation metadata. */
  private String activationRouteSnapshot() {
    if (npc == null || !npc.isSpawned()) return "available=false,reason=npc-unspawned";
    try {
      var metadata = npc.data();
      boolean hasRange = metadata.has(NPC.Metadata.ACTIVATION_RANGE);
      Object range = metadata.get(NPC.Metadata.ACTIVATION_RANGE, null);
      Object guard = fieldQuiet(ahmet, "activationRangeGuard");
      Object route = fieldQuiet(ahmet, "taskNavigationRoute");
      Object recordedStrategy = route == null ? null : fieldQuiet(route, "strategy");
      Location recordedTarget = route == null ? null : (Location) fieldQuiet(route, "nativeTarget");
      PathStrategy currentStrategy = npc.getNavigator().getPathStrategy();
      Location currentTarget = npc.getNavigator().getTargetAsLocation();
      String matches;
      try { matches = String.valueOf(invoke(route, "matches", new Class<?>[] { NPC.class }, npc)); }
      catch (Exception error) { matches = "unavailable:" + error.getClass().getSimpleName(); }
      String tracks;
      try { tracks = String.valueOf(invoke(route, "tracksRoute")); }
      catch (Exception error) { tracks = "unavailable:" + error.getClass().getSimpleName(); }
      String guardOwns;
      try { guardOwns = String.valueOf(invoke(guard, "ownsOverride")); }
      catch (Exception error) { guardOwns = "unavailable:" + error.getClass().getSimpleName(); }
      Object retry = fieldQuiet(ahmet, "navigationRetry");
      return "available=true,activationRange={has=" + hasRange + ",value=" + range
          + "},guardOwnsOverride=" + guardOwns + ",route={matches=" + matches + ",tracks=" + tracks
          + ",recordedStrategy=" + strategyIdentity(recordedStrategy)
          + ",recordedTarget=" + precise(recordedTarget)
          + ",currentStrategy=" + strategyIdentity(currentStrategy)
          + ",currentTarget=" + precise(currentTarget)
          + ",navigating=" + npc.getNavigator().isNavigating() + "}"
          + ",task={active=" + bool(task, "active") + ",purpose=" + fieldQuiet(ahmet, "navigationPurpose")
          + ",goal=" + controllerGoalSafe() + "},deciding=" + bool(ahmet, "deciding")
          + ",recoveryBlocked=" + bool(ahmet, "recoveryBlocked")
          + ",pendingOperations=" + pendingOperationsSafe()
          + ",retryPending=" + safeMethod(retry, "pending")
          + ",nativeActivation={" + nativeActivationSnapshot(npc.getEntity()) + "}";
    } catch (Throwable error) {
      return "available=false,reason=" + error.getClass().getSimpleName();
    }
  }

  private static String strategyIdentity(Object strategy) {
    return strategy == null ? "null" : strategy.getClass().getSimpleName() + "@"
        + Integer.toHexString(System.identityHashCode(strategy));
  }

  /** Read effective villager movement speed and at most six modifier summaries. */
  private String movementSpeedSnapshot(Mob mob) {
    if (mob == null) return "available=false,reason=not-mob";
    AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
    if (speed == null) return "available=false,reason=attribute-missing";
    var modifiers = speed.getModifiers().iterator();
    StringBuilder details = new StringBuilder();
    int count = 0;
    while (modifiers.hasNext() && count < 6) {
      AttributeModifier modifier = modifiers.next();
      if (count > 0) details.append(';');
      details.append(modifier.getKey()).append(':').append(modifier.getOperation()).append(':')
          .append(rounded(modifier.getAmount()));
      count++;
    }
    return "available=true,base=" + rounded(speed.getBaseValue()) + ",value=" + rounded(speed.getValue())
        + ",default=" + rounded(speed.getDefaultValue()) + ",modifierCount=" + speed.getModifiers().size()
        + ",modifiers=" + details + ",modifiersTruncated=" + modifiers.hasNext();
  }

  /** Check actual entity-box overlap with collision shapes in a fixed, already-loaded 3x3x3 neighborhood. */
  private String collisionSnapshot(org.bukkit.entity.Entity entity) {
    BoundingBox entityBox = entity.getBoundingBox();
    Location location = entity.getLocation();
    int centerX = location.getBlockX(), centerY = location.getBlockY(), centerZ = location.getBlockZ();
    int checked = 0, skippedUnloaded = 0, shapeCount = 0, overlaps = 0;
    boolean shapeTruncated = false;
    StringBuilder overlapBlocks = new StringBuilder();
    for (int x = centerX - 1; x <= centerX + 1; x++) {
      for (int y = centerY - 1; y <= centerY + 1; y++) {
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
          if (!world.isChunkLoaded(x >> 4, z >> 4)) { skippedUnloaded++; continue; }
          checked++;
          Block block = world.getBlockAt(x, y, z);
          VoxelShape shape = block.getCollisionShape();
          int perBlockShapes = 0;
          for (BoundingBox localShape : shape.getBoundingBoxes()) {
            if (perBlockShapes >= 8) { shapeTruncated = true; break; }
            perBlockShapes++;
            shapeCount++;
            if (localShape.clone().shift(x, y, z).overlaps(entityBox)) {
              overlaps++;
              if (overlaps <= 6) {
                if (overlapBlocks.length() > 0) overlapBlocks.append(';');
                overlapBlocks.append(x).append('/').append(y).append('/').append(z).append(':').append(block.getType());
              }
            }
          }
        }
      }
    }
    return "available=true,checked=" + checked + ",skippedUnloaded=" + skippedUnloaded
        + ",shapeBoxes=" + shapeCount + ",overlapCount=" + overlaps
        + ",overlapBlocks=" + overlapBlocks + ",overlapListTruncated=" + (overlaps > 6)
        + ",shapeTruncated=" + shapeTruncated + ",entityBox=" + entityBox;
  }

  /** Read the pinned Citizens 2.0.41 v1_21_R5 MoveControl fields without linking to Paper internals. */
  private String nativeMoveControlSnapshot(org.bukkit.entity.Entity entity) {
    try {
      ClassLoader citizensLoader = NPC.class.getClassLoader();
      Class<?> impl = Class.forName("net.citizensnpcs.nms.v1_21_R5.util.NMSImpl", false, citizensLoader);
      var getHandle = impl.getMethod("getHandle", org.bukkit.entity.Entity.class);
      Object handle = getHandle.invoke(null, entity);
      Class<?> mobAI = Class.forName("net.citizensnpcs.nms.v1_21_R5.util.MobAI", false, citizensLoader);
      Object adapter = mobAI.getMethod("from", getHandle.getReturnType()).invoke(null, handle);
      if (adapter == null) return "available=false,reason=no-mob-adapter";
      Object moveControl = mobAI.getMethod("getMoveControl").invoke(adapter);
      if (moveControl == null) return "available=false,reason=no-move-control";
      Class<?> citizensControl = Class.forName(
          "net.citizensnpcs.nms.v1_21_R5.util.EntityMoveControl", false, citizensLoader);
      if (citizensControl.isInstance(moveControl)) {
        return "available=true,kind=citizens,moving=" + citizensControl.getMethod("b").invoke(moveControl)
            + ",speedModifier=" + rounded(((Number) citizensControl.getMethod("c").invoke(moveControl)).doubleValue())
            + ",wanted=" + rounded(((Number) citizensControl.getMethod("d").invoke(moveControl)).doubleValue())
            + "/" + rounded(((Number) citizensControl.getMethod("e").invoke(moveControl)).doubleValue())
            + "/" + rounded(((Number) citizensControl.getMethod("f").invoke(moveControl)).doubleValue());
      }
      if ("net.minecraft.world.entity.ai.control.MoveControl".equals(moveControl.getClass().getName())) {
        Class<?> vanillaControl = moveControl.getClass();
        String operation = "unavailable";
        String operationRead = "unknown";
        try {
          var operationField = vanillaControl.getDeclaredField("operation");
          if (operationField.trySetAccessible()) {
            operation = String.valueOf(operationField.get(moveControl));
            operationRead = "read";
          } else operationRead = "inaccessible";
        } catch (ReflectiveOperationException error) {
          operationRead = error.getClass().getSimpleName();
        }
        return "available=true,kind=paper-mojang,hasWanted=" + vanillaControl.getMethod("hasWanted").invoke(moveControl)
            + ",operation=" + operation + ",operationRead=" + operationRead
            + ",speedModifier=" + rounded(((Number) vanillaControl.getMethod("getSpeedModifier").invoke(moveControl)).doubleValue())
            + ",wanted=" + rounded(((Number) vanillaControl.getMethod("getWantedX").invoke(moveControl)).doubleValue())
            + "/" + rounded(((Number) vanillaControl.getMethod("getWantedY").invoke(moveControl)).doubleValue())
            + "/" + rounded(((Number) vanillaControl.getMethod("getWantedZ").invoke(moveControl)).doubleValue());
      }
      return "available=false,reason=unsupported-control,type=" + moveControl.getClass().getName();
    } catch (ReflectiveOperationException | LinkageError error) {
      return "available=false,reason=" + error.getClass().getSimpleName();
    }
  }

  private void completeNativePathControl(String taskId, int productiveCap, String chestOutcome,
      String adjacentOutcome) {
    require(teleports == 0 && walkedTicks > 0 && maxStep <= 1.5
        && bool(task, "active") && integer(task, "cargo") == 1
        && string(task, "taskId").equals(taskId)
        && integer(controller, "productiveTransitionCount") == productiveCap,
        "native path control violated its bounded movement or unchanged-cargo oracle");
    if (terminal) return;
    terminal = true;
    if (deadline != null) deadline.cancel();
    stopFixtureAdvancement();
    getLogger().info("AHMET_GOAL_DELIVERY_PATH_CONTROL_RESULT outcome=bounded-control-complete taskId=" + taskId
        + " pluginDeliveryTarget=" + precise((Location) fieldQuiet(ahmet, "navigationGoal"))
        + " chestRoute=" + chestOutcome + " adjacentRoute=" + adjacentOutcome
        + " cargo=" + integer(task, "cargo") + " deposited=" + integer(task, "deposited")
        + " teleports=" + teleports + " maxStep=" + numberText(maxStep)
        + " liveProviderCalls=0 overallAcceptance=false");
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private void completePathfinderComparisonControl(String taskId, int productiveCap,
      int iterationsPerTick, int maxIterations, String chestOutcome, String minecraftAdjacentOutcome,
      String citizensAdjacentOutcome) {
    require(pathfinderComparisonStarted && originalControlPathfinder == PathfinderType.MINECRAFT
        && teleports == 0 && walkedTicks > 0 && maxStep <= 1.5
        && bool(task, "active") && integer(task, "cargo") == 1 && integer(task, "deposited") == 0
        && string(task, "taskId").equals(taskId)
        && integer(controller, "productiveTransitionCount") == productiveCap,
        "pathfinder comparison violated its bounded movement or unchanged-cargo oracle");
    if (terminal) return;
    terminal = true;
    if (deadline != null) deadline.cancel();
    boolean cleanupSucceeded = stopFixtureAdvancement();
    if (!cleanupSucceeded) {
      getLogger().severe("AHMET_GOAL_PATHFINDER_COMPARISON_CONTROL_RESULT outcome=cleanup-failure"
          + " terminalNoAcceptance=true pluginEnabled=" + (ahmet != null && ahmet.isEnabled())
          + " pathfinderChanged=" + pathfinderTypeChanged + " pauseHeld="
          + (ahmet == null ? "UNKNOWN" : fieldQuiet(ahmet, "deciding"))
          + " paperShutdown=true");
      Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
      return;
    }
    getLogger().info("AHMET_GOAL_PATHFINDER_COMPARISON_CONTROL_RESULT outcome=bounded-control-complete"
        + " taskId=" + taskId + " baselinePathfinder=MINECRAFT alternatePathfinder=CITIZENS"
        + " chestRoute=" + chestOutcome + " minecraftAdjacentRoute=" + minecraftAdjacentOutcome
        + " citizensAdjacentRoute=" + citizensAdjacentOutcome
        + " astarPerTick=" + iterationsPerTick + " astarMaxIterations=" + maxIterations
        + " cargo=" + integer(task, "cargo") + " deposited=" + integer(task, "deposited")
        + " teleports=" + teleports + " maxStep=" + numberText(maxStep)
        + " liveProviderCalls=0 overallAcceptance=false");
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private void completeActivationControl(String taskId, int productiveCap,
      int iterationsPerTick, int maxIterations, String outcome, int pathStoppedAtTick,
      int activationExpiredAtTick) {
    require(activationControlStarted
        && (activationShamControlOnly ? !activationTickChanged : activationTickChanged)
        && pathfinderComparisonStarted
        && originalControlPathfinder == PathfinderType.MINECRAFT
        && teleports == 0 && walkedTicks > 0 && maxStep <= 1.5
        && bool(ahmet, "deciding") && bool(task, "active")
        && integer(task, "cargo") == 1 && integer(task, "deposited") == 0
        && string(task, "taskId").equals(taskId)
        && integer(controller, "productiveTransitionCount") == productiveCap,
        "activation control violated its one-variable or unchanged-cargo oracle");
    if (terminal) return;
    terminal = true;
    if (deadline != null) deadline.cancel();
    boolean cleanupSucceeded = stopFixtureAdvancement();
    if (!cleanupSucceeded) {
      getLogger().severe(activationResultMarker() + " outcome=cleanup-failure"
          + " terminalNoAcceptance=true pluginEnabled=" + (ahmet != null && ahmet.isEnabled())
          + " activationTickChanged=" + activationTickChanged + " pauseHeld="
          + (ahmet == null ? "UNKNOWN" : fieldQuiet(ahmet, "deciding"))
          + " activationRestore=" + activationRestoreOutcome() + " paperShutdown=true");
      Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
      return;
    }
    getLogger().info(activationResultMarker() + " outcome=bounded-control-complete"
        + " taskId=" + taskId + " engine=CITIZENS strategy=AStarNavigationStrategy"
        + " activationOutcome=" + outcome + " pathStoppedAtTick=" + pathStoppedAtTick
        + " activationExpiredAtTick=" + activationExpiredAtTick
        + " mode=" + (activationShamControlOnly ? "SHAM" : "EXTENDED")
        + " activatedTickWrite=" + (activationShamControlOnly ? "none" : "currentTickPlus200")
        + " originalActivatedTick=" + originalActivationTick
        + " assignedActivatedTick=" + activationControlTargetTick
        + " astarPerTick=" + iterationsPerTick + " astarMaxIterations=" + maxIterations
        + " cargo=" + integer(task, "cargo") + " deposited=" + integer(task, "deposited")
        + " teleports=" + teleports + " maxStep=" + numberText(maxStep)
        + " activationRestore=" + activationRestoreOutcome() + " overallAcceptance=false");
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private String activationResultMarker() {
    return activationShamControlOnly ? "AHMET_GOAL_ACTIVATION_SHAM_CONTROL_RESULT"
        : "AHMET_GOAL_ACTIVATION_CONTROL_RESULT";
  }

  private String activationControlMarker(String suffix) {
    return activationShamControlOnly ? "AHMET_GOAL_ACTIVATION_SHAM_CONTROL_" + suffix
        : "AHMET_GOAL_ACTIVATION_CONTROL_" + suffix;
  }

  private String activationRestoreOutcome() {
    if (activationShamControlOnly) return "not-needed";
    return activationTickRestored ? "restored" : "NOT_VERIFIED";
  }

  private void createIdleHomeRoute(String stageName, Runnable next) {
    try {
      require(!bool(task, "active") && integer(task, "cargo") == 0, "idle route must start without an active task or cargo");
      String completedTaskId = string(task, "taskId");
      long generation = number(controller, "jobGeneration");
      command(owner, "ahmet home");
      require(messagesContain("eve doğru yönlendirildi"), "operator home command did not report acceptance");
      await(stageName, 220,
          () -> bool(ahmet, "returningHome") && !bool(task, "active") && integer(task, "cargo") == 0
              && "RETURN_HOME".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
              && npc.getNavigator().isNavigating(), () -> {
            try {
              Location pluginTarget = (Location) field(ahmet, "navigationGoal");
              Location nativeTarget = npc.getNavigator().getTargetAsLocation();
              require(pluginTarget != null && nativeTarget != null && sameBlock(pluginTarget, nativeTarget)
                  && string(task, "taskId").equals(completedTaskId)
                  && number(controller, "jobGeneration") == generation,
                  "idle home route is not a real unchanged RETURN_HOME route");
              getLogger().info("AHMET_GOAL_STAGE_PASS name=" + stageName + " purpose=RETURN_HOME nativeTarget="
                  + precise(nativeTarget) + " generation=" + generation);
              next.run();
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void testRejectedAdmissionPreservesHomeRoute() {
    try {
      Location pluginTarget = (Location) field(ahmet, "navigationGoal");
      Location nativeTarget = npc.getNavigator().getTargetAsLocation();
      String taskId = string(task, "taskId");
      long generation = number(controller, "jobGeneration");
      command(owner, "ahmet collect diamond 1");
      require(messagesContain("Bu kaynak desteklenmiyor") && !bool(task, "active")
          && string(task, "taskId").equals(taskId) && number(controller, "jobGeneration") == generation
          && bool(ahmet, "returningHome") && pluginTarget.equals(field(ahmet, "navigationGoal"))
          && npc.getNavigator().isNavigating() && sameBlock(nativeTarget, npc.getNavigator().getTargetAsLocation()),
          "invalid /collect changed the real idle home route or missed its rejection branch");
      getLogger().info("AHMET_GOAL_STAGE_PASS name=invalid-command-preserves-idle-home-route"
          + " taskId=" + taskId + " generation=" + generation + " nativeTarget=" + precise(nativeTarget));
      runAcceptedCommandHandoff();
    } catch (Throwable error) { fail(error); }
  }

  private void runAcceptedCommandHandoff() {
    try {
      Block source = placeDirtAhead();
      Location oldPluginTarget = (Location) field(ahmet, "navigationGoal");
      Location oldNativeTarget = npc.getNavigator().getTargetAsLocation();
      String oldTaskId = string(task, "taskId");
      long oldGeneration = number(controller, "jobGeneration");
      require(bool(ahmet, "returningHome") && npc.getNavigator().isNavigating()
          && oldPluginTarget != null && oldNativeTarget != null,
          "accepted command handoff setup lacks a real idle home route");
      command(owner, "ahmet collect dirt 1");
      require(bool(task, "active") && !string(task, "taskId").equals(oldTaskId)
          && number(controller, "jobGeneration") > oldGeneration && !bool(ahmet, "returningHome")
          && fieldQuiet(ahmet, "navigationGoal") == null && !npc.getNavigator().isNavigating()
          && "HARVEST_RESOURCE".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose"))),
          "accepted /collect did not clear the live idle-home route before admission");
      getLogger().info("AHMET_GOAL_STAGE_PASS name=accepted-command-clears-idle-home-route"
          + " oldPluginTarget=" + precise(oldPluginTarget) + " oldNativeTarget=" + precise(oldNativeTarget)
          + " newTaskId=" + string(task, "taskId") + " generation=" + number(controller, "jobGeneration"));
      await("command-harvest-native-route", 300,
          () -> npc.getNavigator().isNavigating()
              && "HARVEST_RESOURCE".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
              && "HARVEST_RESOURCE".equals(String.valueOf(controllerGoal()))
              && sameBlock((Location) fieldQuiet(ahmet, "treeTarget"), source.getLocation()), () -> {
            try {
              Location nativeTarget = npc.getNavigator().getTargetAsLocation();
              require(nativeTarget != null && bool(task, "active") && integer(task, "requested") == 1,
                  "accepted command did not start the real bounded harvest route");
              getLogger().info("AHMET_GOAL_STAGE_PASS name=command-harvest-native-route source=" + loc(source.getLocation())
                  + " target=" + precise(nativeTarget));
              runCappedHarvestScenario(source, string(task, "taskId"));
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void runCappedHarvestScenario(Block source, String taskId) {
    try {
      int routeClearCap = ((Number) invoke(invoke(controller, "budget"), "maxRouteClears")).intValue();
      int jobClearCap = maxJobClearCount();
      require(routeClearCap == 3 && jobClearCap > 0, "unexpected pinned clear caps route=" + routeClearCap + " job=" + jobClearCap);
      Location originalPluginTarget = (Location) field(ahmet, "navigationGoal");
      Location originalNativeTarget = npc.getNavigator().getTargetAsLocation();
      require(originalPluginTarget != null && originalNativeTarget != null && source.getType() == Material.DIRT,
          "clear-cap setup requires an admitted active native harvest route");
      set(ahmet, "routeClears", routeClearCap);
      set(task, "cleared", jobClearCap);
      double walkedBefore = walked;
      Location npcStart = npc.getEntity().getLocation().clone();
      int[] movingTicks = {0};
      getLogger().info("AHMET_GOAL_STAGE_START name=clear-caps-harvest-native-progress routeClears="
          + routeClearCap + " jobClears=" + jobClearCap + " controlledCounterSetup=true");
      await("clear-caps-harvest-native-progress", 80, () -> {
        require(bool(task, "active") && string(task, "taskId").equals(taskId)
            && integer(task, "collected") == 0 && integer(task, "cargo") == 0
            && integer(task, "cleared") == jobClearCap && integer(ahmet, "routeClears") == routeClearCap
            && source.getType() == Material.DIRT
            && !"BLOCKED_AWAITING_HELP".equals(String.valueOf(fieldQuiet(controller, "state"))),
            "clear caps blocked or mutated an otherwise valid harvest task");
        if (npc.getNavigator().isNavigating()
            && "HARVEST_RESOURCE".equals(String.valueOf(controllerGoal()))
            && sameBlock(originalNativeTarget, npc.getNavigator().getTargetAsLocation())
            && walked - walkedBefore > 0.4) movingTicks[0]++;
        return movingTicks[0] >= 5;
      }, () -> {
        try {
          require(npcStart.distance(npc.getEntity().getLocation()) > 0.4 && source.getType() == Material.DIRT
              && integer(task, "cleared") == jobClearCap && integer(ahmet, "routeClears") == routeClearCap,
              "capped route did not walk while preserving the eligible source and clear counters");
          getLogger().info("AHMET_GOAL_STAGE_PASS name=clear-caps-harvest-native-progress ticks=5"
              + " goal=HARVEST_RESOURCE source=" + loc(source.getLocation()) + " routeClears=" + routeClearCap
              + " jobClears=" + jobClearCap + " walkingBlocks=" + rounded(walked - walkedBefore)
              + " controlledCounterSetup=true noProviderClearChoice=true");
          int chestBefore = countChest();
          await("clear-caps-harvest-real-delivery", 700,
              () -> !bool(task, "active") && integer(task, "cargo") == 0
                  && integer(task, "collected") == 1 && integer(task, "deposited") == 1,
              () -> {
                try {
                  require(source.getType() == Material.AIR && countChest() == chestBefore + 1
                      && string(task, "taskId").equals(taskId) && integer(task, "cleared") == jobClearCap,
                      "capped harvest did not remove the eligible source and deposit one actual item");
                  getLogger().info("AHMET_GOAL_STAGE_PASS name=clear-caps-harvest-real-delivery sourceRemoved=true"
                      + " deposited=1 jobClearsStill=" + jobClearCap);
                  createIdleHomeRoute("idle-home-route-for-chat", this::runAcceptedChatHandoff);
                } catch (Throwable error) { fail(error); }
              });
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private void runAcceptedChatHandoff() {
    try {
      Block source = placeDirtAhead();
      Location oldPluginTarget = (Location) field(ahmet, "navigationGoal");
      Location oldNativeTarget = npc.getNavigator().getTargetAsLocation();
      String oldTaskId = string(task, "taskId");
      long oldGeneration = number(controller, "jobGeneration");
      require(oldPluginTarget != null && oldNativeTarget != null && bool(ahmet, "returningHome")
          && npc.getNavigator().isNavigating() && !bool(task, "active") && integer(task, "cargo") == 0,
          "chat handoff setup lacks an idle native home route");
      int chatsBefore = chatCalls.get();
      command(owner, "ahmet ask collect dirt");
      await("mock-chat-clears-idle-home-route", 180,
          () -> bool(task, "active") && !string(task, "taskId").equals(oldTaskId)
              && number(controller, "jobGeneration") > oldGeneration && chatCalls.get() == chatsBefore + 1, () -> {
            try {
              Location currentPluginTarget = (Location) field(ahmet, "navigationGoal");
              Location currentNativeTarget = npc.getNavigator().getTargetAsLocation();
              Location selectedSource = (Location) field(ahmet, "treeTarget");
              boolean routeClearedTransiently = currentPluginTarget == null
                  && currentNativeTarget == null && !npc.getNavigator().isNavigating();
              boolean sourceBoundHarvestRoute = currentPluginTarget != null && currentNativeTarget != null
                  && npc.getNavigator().isNavigating()
                  && "HARVEST_RESOURCE".equals(String.valueOf(controllerGoal()))
                  && sameBlock(selectedSource, source.getLocation())
                  && currentPluginTarget.distanceSquared(source.getLocation().clone().add(0.5, 0, 0.5)) <= 2.9 * 2.9
                  && sameBlock(currentPluginTarget, currentNativeTarget);
              require(integer(task, "requested") == 1 && "item:DIRT".equals(string(task, "materialPreference"))
                  && string(task, "owner").equals(OWNER.toString()) && !bool(ahmet, "returningHome")
                  && "HARVEST_RESOURCE".equals(String.valueOf(fieldQuiet(ahmet, "navigationPurpose")))
                  && (routeClearedTransiently || sourceBoundHarvestRoute)
                  && (currentNativeTarget == null || !sameBlock(oldNativeTarget, currentNativeTarget)),
                  "accepted mock chat did not replace the actual idle-home target with a cleared state or source-bound harvest route");
              getLogger().info("AHMET_GOAL_STAGE_PASS name=mock-chat-clears-idle-home-route"
                  + " handoff=" + (routeClearedTransiently ? "cleared-transient" : "source-bound-harvest-route")
                  + " oldPluginTarget=" + precise(oldPluginTarget) + " oldNativeTarget=" + precise(oldNativeTarget)
                  + " currentPluginTarget=" + precise(currentPluginTarget) + " currentNativeTarget=" + precise(currentNativeTarget)
                  + " newTaskId=" + string(task, "taskId") + " generation=" + number(controller, "jobGeneration")
                  + " chatSource=fixture-mock-heuristic chatCalls=" + chatCalls.get());
              runChatHarvestAndDelivery(source, string(task, "taskId"));
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void runChatHarvestAndDelivery(Block source, String taskId) {
    int chestBefore = countChest();
    await("mock-chat-real-harvest-delivery", 800,
        () -> !bool(task, "active") && integer(task, "cargo") == 0
            && integer(task, "collected") == 1 && integer(task, "deposited") == 1,
        () -> {
          try {
            require(source.getType() == Material.AIR && countChest() == chestBefore + 1
                && string(task, "taskId").equals(taskId) && chatCalls.get() == 1,
                "mock chat job did not execute and deposit the exact one-item task");
            getLogger().info("AHMET_GOAL_STAGE_PASS name=mock-chat-real-harvest-delivery"
                + " sourceRemoved=true deposited=1 fixtureMockChat=true liveProviderCalls=0");
            runForeignRouteWaitProbe();
          } catch (Throwable error) { fail(error); }
        });
  }

  private void runForeignRouteWaitProbe() {
    try {
      Block source = world.getBlockAt(4, 120, 1);
      source.setType(Material.DIRT, false);
      Location foreignTarget = new Location(world, 3.5, 120, 4.5);
      foreignNavigatorDefaults = npc.getNavigator().getDefaultParameters();
      foreignOriginalTeleportMargin = foreignNavigatorDefaults.destinationTeleportMargin();
      foreignOriginalStationaryTicks = foreignNavigatorDefaults.stationaryTicks();
      foreignOriginalSpeed = foreignNavigatorDefaults.speed();
      foreignNavigatorDefaultsChanged = true;
      foreignNavigatorDefaults.destinationTeleportMargin(-1).stationaryTicks(-1).speed(0.01f);
      npc.getNavigator().setTarget(foreignTarget);
      foreignRouteStrategy = npc.getNavigator().getPathStrategy();
      Location targetReadback = npc.getNavigator().getTargetAsLocation();
      require(targetReadback != null,
          "pinned Citizens Navigator hid its active foreign route target before task admission");
      Location nativeTarget = targetReadback.clone();
      boolean activationOverrideBefore = activationGuardOwnsOverride();
      require(npc.getNavigator().isNavigating() && foreignRouteStrategy != null && nativeTarget != null
          && !npc.getNavigator().isPaused() && fieldQuiet(ahmet, "navigationGoal") == null
          && !activationOverrideBefore
          && Math.sqrt(horizontalDistanceSquared(nativeTarget, home)) <= 10.0,
          "fixture could not establish an active, slow in-bound foreign Citizens route before admission");
      int chestBefore = countChest();
      int chatsBefore = chatCalls.get();
      int decisionsBefore = decisionCalls.get();
      command(owner, "ahmet collect dirt 1");
      require(bool(task, "active") && OWNER.toString().equals(string(task, "owner")),
          "bounded wait task was not admitted for its authorized owner");
      String taskId = string(task, "taskId");
      long generation = number(controller, "jobGeneration");
      int productiveBefore = integer(controller, "productiveTransitionCount");
      long helpSequenceBefore = number(controller, "helpRequestSequence");
      long lastHelpRequestedMs = number(controller, "lastHelpRequestedMs");
      Object controllerBudget = invoke(controller, "budget");
      long helpCooldownMs = ((Number) invoke(controllerBudget, "helpCooldownMs")).longValue();
      long helpCooldownDeadlineMs = lastHelpRequestedMs + helpCooldownMs;
      long cooldownRemainingAtStartMs = Math.max(0, helpCooldownDeadlineMs - System.currentTimeMillis());
      long cooldownRemainingTicks = (cooldownRemainingAtStartMs + 49) / 50;
      int foreignWaitTimeoutTicks = Math.toIntExact(FOREIGN_ROUTE_WAIT_TICKS + cooldownRemainingTicks + 1);
      require(npc.getNavigator().isNavigating() && npc.getNavigator().getPathStrategy() == foreignRouteStrategy
          && !npc.getNavigator().isPaused()
          && sameLocationWithin(nativeTarget, npc.getNavigator().getTargetAsLocation(), 0.001),
          "task admission canceled or replaced the foreign native route");
      getLogger().info("AHMET_GOAL_STAGE_START name=foreign-route-bounded-wait taskId=" + taskId
          + " generation=" + generation + " target=" + precise(nativeTarget)
          + " source=" + loc(source.getLocation()) + " deadlineTicks=" + FOREIGN_ROUTE_WAIT_TICKS
          + " timeoutTicks=" + foreignWaitTimeoutTicks + " controllerHelpCooldownMs=" + helpCooldownMs
          + " lastAcceptedHelpMs=" + lastHelpRequestedMs + " remainingCooldownAtStartMs=" + cooldownRemainingAtStartMs);

      boolean[] cooldownRejectionLogged = {false};
      boolean[] helpAcceptanceLogged = {false};
      long[] lastLoggedHelpAttemptTick = {-1};
      await("foreign-route-bounded-wait", foreignWaitTimeoutTicks, () -> {
        long observedWaitTicks = foreignWaitElapsedTicks();
        long observedHelpSequence = number(controller, "helpRequestSequence");
        long remainingCooldownMs = Math.max(0, helpCooldownDeadlineMs - System.currentTimeMillis());
        long lastHelpAttemptTick = foreignWaitLastHelpAttemptTick();
        if (!cooldownRejectionLogged[0] && observedWaitTicks >= FOREIGN_ROUTE_WAIT_TICKS
            && observedHelpSequence == helpSequenceBefore && lastHelpAttemptTick >= 0
            && lastHelpAttemptTick > lastLoggedHelpAttemptTick[0]) {
          cooldownRejectionLogged[0] = true;
          lastLoggedHelpAttemptTick[0]=lastHelpAttemptTick;
          getLogger().info("AHMET_GOAL_FOREIGN_HELP_RETRY outcome="
              + (remainingCooldownMs > 0 ? "cooldown-rejected" : "retry-after-cooldown")
              + " observedWaitTicks=" + observedWaitTicks + " helpRequestSequence=" + observedHelpSequence
              + " source=controller.lastHelpRequestedMs+controller.budget.helpCooldownMs"
              + " cooldownMs=" + helpCooldownMs + " remainingCooldownMs=" + remainingCooldownMs);
        }
        if (!helpAcceptanceLogged[0] && observedHelpSequence == helpSequenceBefore + 1) {
          helpAcceptanceLogged[0] = true;
          getLogger().info("AHMET_GOAL_FOREIGN_HELP_RETRY outcome=accepted observedWaitTicks=" + observedWaitTicks
              + " helpRequestSequence=" + observedHelpSequence + " cooldownMs=" + helpCooldownMs
              + " remainingCooldownMs=" + remainingCooldownMs);
        }
        require(bool(task, "active") && taskId.equals(string(task, "taskId"))
            && OWNER.toString().equals(string(task, "owner")) && number(controller, "jobGeneration") == generation
            && integer(task, "requested") == 1 && integer(task, "collected") == 0
            && integer(task, "cargo") == 0 && integer(task, "deposited") == 0 && !safeReadyToDeliver().equals("true")
            && observedHelpSequence == helpSequenceBefore
                + ("BLOCKED_AWAITING_HELP".equals(String.valueOf(fieldQuiet(controller, "state"))) ? 1 : 0)
            && (observedHelpSequence == helpSequenceBefore
                || (observedWaitTicks >= FOREIGN_ROUTE_WAIT_TICKS && System.currentTimeMillis() >= helpCooldownDeadlineMs))
            && integer(controller, "productiveTransitionCount") == productiveBefore
            && chatCalls.get() == chatsBefore && decisionCalls.get() == decisionsBefore
            && source.getType() == Material.DIRT && countChest() == chestBefore
            && npc.getNavigator().isNavigating() && !npc.getNavigator().isPaused()
            && npc.getNavigator().getPathStrategy() == foreignRouteStrategy
            && sameLocationWithin(nativeTarget, npc.getNavigator().getTargetAsLocation(), 0.001)
            && !taskNavigationRouteMatches() && activationGuardOwnsOverride() == activationOverrideBefore,
            "foreign wait changed job, productive budget, source, chest, or exact foreign route");
        return "BLOCKED_AWAITING_HELP".equals(String.valueOf(fieldQuiet(controller, "state")))
            && "REQUEST_PLAYER_HELP".equals(String.valueOf(controllerGoal()));
      }, () -> {
        try {
          long waitedTicks = foreignWaitElapsedTicks();
          long nowMs = System.currentTimeMillis();
          require("Başka bir Citizens hareketi devam ettiği için Ahmet görevine devam edemiyor; bu hareketi durdurun veya bitmesini bekleyin."
              .equals(fieldQuiet(ahmet, "lastPlayerHelpReason"))
              && waitedTicks >= FOREIGN_ROUTE_WAIT_TICKS
              && nowMs >= helpCooldownDeadlineMs
              && number(controller, "helpRequestSequence") == helpSequenceBefore + 1
              && !pendingOperations() && integer(task, "collected") == 0 && integer(task, "cargo") == 0
              && integer(task, "deposited") == 0,
              "foreign route wait did not expose its bounded help reason without world or cargo changes");
          getLogger().info("AHMET_GOAL_STAGE_PASS name=foreign-route-bounded-wait limitTicks=" + FOREIGN_ROUTE_WAIT_TICKS
              + " observedTicks=" + waitedTicks + " cooldownMs=" + helpCooldownMs
              + " goal=REQUEST_PLAYER_HELP state=BLOCKED_AWAITING_HELP sameStrategy=true sameTarget=true activeForeignRoute=true"
              + " unchangedSource=true unchangedChest=true collected=0 cargo=0 productiveTransitions=" + productiveBefore
              + " chatCallsUnchanged=true decisionCallsUnchanged=true");

          // Explicit fixture cleanup releases only the route installed by this probe.
          require(restoreForeignRouteFixture(),"could not restore the fixture-owned foreign route/default navigator parameters");
          int chestAtResume = countChest();
          await("foreign-route-same-job-resume-and-delivery", 800,
              () -> !bool(task, "active") && integer(task, "cargo") == 0
                  && integer(task, "collected") == 1 && integer(task, "deposited") == 1,
              () -> {
                try {
                  require(source.getType() == Material.AIR && countChest() == chestAtResume + 1
                      && taskId.equals(string(task, "taskId")) && OWNER.toString().equals(string(task, "owner"))
                      && number(controller, "jobGeneration") > generation
                      && !"BLOCKED_AWAITING_HELP".equals(String.valueOf(fieldQuiet(controller, "state")))
                      && chatCalls.get() == 1,
                      "same authorized task did not resume after explicit foreign-route release and deliver its item");
                  getLogger().info("AHMET_GOAL_STAGE_PASS name=foreign-route-same-job-resume-and-delivery"
                      + " sameTaskId=" + taskId + " sameOwner=true generationAdvancedOnNormalCompletion=true"
                      + " sourceRemoved=true deposited=1 foreignHelpCleared=true liveProviderCalls=0");
                  complete();
                } catch (Throwable error) { fail(error); }
              });
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private Block placeDirtAhead() {
    int x = npc.getEntity().getLocation().getBlockX();
    int z = npc.getEntity().getLocation().getBlockZ();
    int[][] offsets = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {6, 6}, {-6, 6}, {6, -6}, {-6, -6}};
    for (int[] offset : offsets) {
      int targetX = x + offset[0], targetZ = z + offset[1];
      if (Math.abs(targetX) > 14 || Math.abs(targetZ) > 14) continue;
      Block block = world.getBlockAt(targetX, 120, targetZ);
      if (sameBlock(block.getLocation(), chestLocation) || !block.getType().isAir()
          || !world.getBlockAt(targetX, 119, targetZ).getType().isSolid()
          || !world.getBlockAt(targetX, 121, targetZ).isPassable()
          || !world.getBlockAt(targetX, 122, targetZ).isPassable()) continue;
      block.setType(Material.DIRT, false);
      require(block.getType() == Material.DIRT, "fixture failed to place a supported DIRT source");
      return block;
    }
    throw new IllegalStateException("no bounded, loaded, supported DIRT placement was available near Ahmet");
  }

  private int maxJobClearCount() throws Exception {
    Field cap = task.getClass().getDeclaredField("MAX_JOB_CLEARS");
    cap.setAccessible(true);
    return cap.getInt(null);
  }

  private Location assertNativeChestRoute() throws Exception {
    Location productionTarget = chestLocation.clone().add(0.5, 0, 0.5);
    Location citizensStandTarget = productionTarget.clone().add(0, 1, 0);
    Location nativeTarget = npc.getNavigator().getTargetAsLocation();
    require(chestLocation.getBlock().getType() == Material.CHEST
        && chestLocation.clone().add(0, -1, 0).getBlock().getType() == Material.SMOOTH_STONE
        && chestLocation.clone().add(0, 1, 0).getBlock().isPassable()
        && chestLocation.clone().add(0, 2, 0).getBlock().isPassable(),
        "fixture chest support/headroom does not match the pinned Citizens normalization case");
    require(productionTarget.equals(field(ahmet, "navigationGoal")) && nativeTarget != null
        && nativeTarget.distanceSquared(citizensStandTarget) <= 0.0001,
        "Citizens delivery target did not equal the pinned stand-above normalization; plugin="
            + loc((Location) field(ahmet, "navigationGoal")) + " expectedNative=" + loc(citizensStandTarget)
            + " actualNative=" + loc(nativeTarget));
    return nativeTarget;
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
    if (isBudgetDeliveryFinishStage(name)) {
      lastBudgetDeliveryDiagnosticKey = null;
      budgetDeliveryStateDiagnosticLines = 0;
      budgetDeliveryPeriodicDiagnosticLines = 0;
      budgetDeliveryWaitStart = npc == null || !npc.isSpawned() ? null : npc.getEntity().getLocation().clone();
      budgetDeliveryWaitStartedAtMillis = System.currentTimeMillis();
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
        if (isBudgetDeliveryFinishStage(name)) {
          recordBudgetDeliveryDiagnostic(elapsed, stageTimeout || (elapsed % 100 == 0));
        }
        if (name.equals("first-exploration-leg-moves-with-purpose")
            && (elapsed == 1 || elapsed == 20 || elapsed == 100 || elapsed == timeoutTicks)) {
          getLogger().info("AHMET_GOAL_ACTIVATION_RUNTIME_DIAG stage=" + name + " stageTicks=" + elapsed
              + " paperTick=" + Bukkit.getCurrentTick() + " " + activationRouteSnapshot());
        }
        if (condition.getAsBoolean()) {
          timer.cancel(); getLogger().info("AHMET_GOAL_STAGE_PASS name=" + name + " ticks=" + elapsed); next.run();
        } else if (stageTimeout) {
          timer.cancel(); throw new IllegalStateException("stage timeout=" + name + " ticks=" + elapsed
              + (name.equals("first-exploration-leg-start")
                  ? " state=" + explorationDiagnosticSnapshot()
              : isBudgetDeliveryFinishStage(name)
                      ? " deliveryState=" + budgetDeliveryDiagnosticSnapshot(elapsed)
                  : " location=" + loc(npc == null || !npc.isSpawned() ? null : npc.getEntity().getLocation())));
        }
      } catch (Throwable error) { timer.cancel(); fail(error); }
    }, 1L, 1L);
  }

  private boolean isBudgetDeliveryFinishStage(String name) {
    return name.equals("budget32-mandatory-delivery-finish") || name.equals("budget-control-zero-delivery-finish");
  }

  private void recordBudgetDeliveryDiagnostic(int ticks, boolean periodic) {
    String snapshot = budgetDeliveryDiagnosticSnapshot(ticks);
    String key = budgetDeliveryDiagnosticKey();
    if (periodic) {
      // Reserve thirteen of 25 records for 100-tick samples through the 1,200-tick deadline and one spare.
      if (budgetDeliveryPeriodicDiagnosticLines >= 13) return;
      budgetDeliveryPeriodicDiagnosticLines++;
    } else {
      if (key.equals(lastBudgetDeliveryDiagnosticKey) || budgetDeliveryStateDiagnosticLines >= 12) return;
      budgetDeliveryStateDiagnosticLines++;
    }
    lastBudgetDeliveryDiagnosticKey = key;
    getLogger().info("AHMET_GOAL_BUDGET_DELIVERY_DIAG mode=" + (budgetControlOnly ? "controlled-zero-cap"
        : deliveryPathControlOnly ? "native-path-control-32-cap" : "acceptance-32-cap")
        + " stageTicks=" + ticks + " reason="
        + (periodic ? "periodic-or-timeout" : "state-change") + " " + snapshot);
  }

  private String budgetDeliveryDiagnosticKey() {
    Object watchdog = fieldQuiet(ahmet, "navigationWatchdog");
    Object retry = fieldQuiet(ahmet, "navigationRetry");
    return "taskId=" + string(task, "taskId") + "|active=" + bool(task, "active")
        + "|ready=" + safeReadyToDeliver() + "|cargo=" + integer(task, "cargo")
        + "|collected=" + integer(task, "collected") + "|deposited=" + integer(task, "deposited")
        + "|productive=" + fieldQuiet(controller, "productiveTransitionCount")
        + "|currentGoal=" + controllerGoalSafe() + "|controllerState=" + fieldQuiet(controller, "state")
        + "|purpose=" + fieldQuiet(ahmet, "navigationPurpose")
        + "|pluginTarget=" + precise((Location) fieldQuiet(ahmet, "navigationGoal"))
        + "|nativeNavigating=" + (npc != null && npc.isSpawned() && npc.getNavigator().isNavigating())
        + "|nativeTarget=" + precise(npc == null || !npc.isSpawned() ? null : npc.getNavigator().getTargetAsLocation())
        + "|retryPending=" + safeMethod(retry, "pending")
        + "|retryAt=" + fieldQuiet(retry, "restartAtMillis")
        + "|watchdogInitialized=" + fieldQuiet(watchdog, "initialized")
        + "|watchdogStalls=" + fieldQuiet(watchdog, "stalledAttempts")
        + "|watchdogBestDistanceSq=" + fieldQuiet(watchdog, "bestDistanceSquared")
        + "|watchdogLastProgressAt=" + fieldQuiet(watchdog, "lastProgressAtMillis")
        + "|deciding=" + bool(ahmet, "deciding")
        + "|recoveryBlocked=" + bool(ahmet, "recoveryBlocked")
        + "|pending=" + pendingOperationsSafe();
  }

  private String budgetDeliveryDiagnosticSnapshot(int stageTicks) {
    Location current = npc == null || !npc.isSpawned() ? null : npc.getEntity().getLocation();
    Location nativeTarget = npc == null || !npc.isSpawned() ? null : npc.getNavigator().getTargetAsLocation();
    Object watchdog = fieldQuiet(ahmet, "navigationWatchdog");
    Object retry = fieldQuiet(ahmet, "navigationRetry");
    long now = System.currentTimeMillis();
    double distanceToTargetSq = current == null || nativeTarget == null ? Double.NaN : current.distanceSquared(nativeTarget);
    double movementSinceStart = current == null || budgetDeliveryWaitStart == null
        ? Double.NaN : current.distance(budgetDeliveryWaitStart);
    return "stageTicks=" + stageTicks + " paperTick=" + Bukkit.getCurrentTick()
        + " elapsedWallMs=" + Math.max(0, now - budgetDeliveryWaitStartedAtMillis)
        + " npc=" + precise(current) + " npcMovedFromStageStart=" + numberText(movementSinceStart)
        + " navigationPurpose=" + fieldQuiet(ahmet, "navigationPurpose")
        + " currentGoal=" + controllerGoalSafe() + " controllerState=" + fieldQuiet(controller, "state")
        + " pluginNavigationGoal=" + precise((Location) fieldQuiet(ahmet, "navigationGoal"))
        + " nativeNavigating=" + (npc != null && npc.isSpawned() && npc.getNavigator().isNavigating())
        + " nativeTargetType=" + (npc == null || !npc.isSpawned() ? "null" : npc.getNavigator().getTargetType())
        + " nativeTarget=" + precise(nativeTarget) + " distanceToNativeTargetSq=" + numberText(distanceToTargetSq)
        + " task={id:" + string(task, "taskId") + ",owner:" + string(task, "owner") + ",active:" + bool(task, "active")
        + ",readyToDeliver:" + safeReadyToDeliver() + ",requested:" + integer(task, "requested")
        + ",collected:" + integer(task, "collected") + ",cargo:" + integer(task, "cargo")
        + ",deposited:" + integer(task, "deposited") + ",jobClears:" + integer(task, "cleared") + "}"
        + " productiveTransitions=" + fieldQuiet(controller, "productiveTransitionCount")
        + " recoveryBlocked=" + bool(ahmet, "recoveryBlocked") + " pendingOperations=" + pendingOperationsSafe()
        + " deciding=" + bool(ahmet, "deciding")
        + " navigationRetry={pending:" + safeMethod(retry, "pending") + ",restartAt:" + fieldQuiet(retry, "restartAtMillis") + "}"
        + " watchdog={initialized:" + fieldQuiet(watchdog, "initialized")
        + ",stalledAttempts:" + fieldQuiet(watchdog, "stalledAttempts")
        + ",bestDistanceSq:" + fieldQuiet(watchdog, "bestDistanceSquared")
        + ",lastProgressAt:" + fieldQuiet(watchdog, "lastProgressAtMillis")
        + ",progressAgeMs:" + progressAge(now, watchdog) + "}";
  }

  private String safeReadyToDeliver() {
    try { return String.valueOf(invoke(task, "readyToDeliver")); }
    catch (Exception ignored) { return "UNKNOWN"; }
  }

  private String pendingOperationsSafe() {
    try { return String.valueOf(pendingOperations()); }
    catch (Exception ignored) { return "UNKNOWN"; }
  }

  private static String safeMethod(Object target, String name) {
    if (target == null) return "UNKNOWN";
    try { return String.valueOf(invoke(target, name)); }
    catch (Exception ignored) { return "UNKNOWN"; }
  }

  private static String progressAge(long now, Object watchdog) {
    Object raw = fieldQuiet(watchdog, "lastProgressAtMillis");
    return raw instanceof Number number && number.longValue() > 0
        ? String.valueOf(Math.max(0, now - number.longValue())) : "UNKNOWN";
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
        + " deterministicMockChats=" + chatCalls.get() + " liveProviderCalls=0 recoveryEvidence=injected-lock-only");
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private void fail(Throwable error) {
    if (terminal) return; terminal = true;
    if (deadline != null) deadline.cancel();
    stopFixtureAdvancement();
    String marker = activationControlStarted ? activationResultMarker()
        : pathfinderComparisonStarted ? "AHMET_GOAL_PATHFINDER_COMPARISON_CONTROL_RESULT"
        : deliveryPathControlOnly ? "AHMET_GOAL_DELIVERY_PATH_CONTROL_RESULT"
        : budgetControlOnly ? "AHMET_GOAL_BUDGET_CONTROL_RESULT" : "AHMET_GOAL_RUNTIME_PROBE_FAIL";
    getLogger().severe(marker
        + " stage=" + stage + " outcome=bounded-failure reason="
        + error.getClass().getSimpleName() + ": " + safeMessage(error));
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2L);
  }

  private boolean stopFixtureAdvancement() {
    boolean foreignFixtureWasActive=foreignRouteStrategy!=null||foreignNavigatorDefaultsChanged;
    boolean cleanupSucceeded = restoreForeignRouteFixture();
    boolean pathfinderRestored = !pathfinderTypeChanged;
    boolean activationCleanupSatisfied = !activationTickChanged;
    try { if (!foreignFixtureWasActive&&npc != null && npc.isSpawned()) npc.getNavigator().cancelNavigation(); }
    catch (Throwable error) {
      if (pathfinderComparisonStarted) {
        cleanupSucceeded = false;
        getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=navigation-stop-failed reason="
            + error.getClass().getSimpleName());
      }
    }
    try { if (mockBridge != null) mockBridge.stop(0); } catch (Throwable ignored) { }
    try { if (bridgeExecutor != null) bridgeExecutor.shutdownNow(); } catch (Throwable ignored) { }
    if (deliveryPathControlOnly && ahmet != null) {
      try { if (ahmet.isEnabled()) set(ahmet, "deciding", true); }
      catch (Throwable error) {
        cleanupSucceeded = false;
        getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=could-not-assert-pause reason="
            + error.getClass().getSimpleName());
      }
      try { if (ahmet.isEnabled()) Bukkit.getPluginManager().disablePlugin(ahmet); }
      catch (Throwable error) {
        cleanupSucceeded = false;
        getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=plugin-disable-threw pause-retained="
            + bool(ahmet, "deciding") + " reason=" + error.getClass().getSimpleName());
      }
      if (!ahmet.isEnabled()) {
        if (pathfinderTypeChanged) {
          try {
            require(npc != null && npc.isSpawned() && !npc.getNavigator().isNavigating()
                && originalControlPathfinder == PathfinderType.MINECRAFT,
                "cannot safely restore pathfinder until plugin is disabled and navigation is stopped");
            var defaults = npc.getNavigator().getDefaultParameters();
            require(defaults.pathfinderType() == PathfinderType.CITIZENS
                && defaults.destinationTeleportMargin() == -1 && defaults.stationaryTicks() == -1,
                "alternate pathfinder or no-teleport margins changed before cleanup");
            defaults.pathfinderType(originalControlPathfinder);
            require(defaults.pathfinderType() == PathfinderType.MINECRAFT
                && defaults.destinationTeleportMargin() == -1 && defaults.stationaryTicks() == -1,
                "original pathfinder restoration failed or changed no-teleport margins");
            getLogger().info("AHMET_GOAL_PATH_CONTROL_CLEANUP outcome=plugin-disabled-navigation-stopped"
                + " pathfinderRestored=MINECRAFT teleportMargin=-1 stationaryTicks=-1");
            pathfinderRestored = true;
          } catch (Throwable error) {
            cleanupSucceeded = false;
            getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=pathfinder-restore-failed reason="
                + error.getClass().getSimpleName() + " pluginDisabled=true");
          }
        }
        if (activationTickChanged) {
          try {
            require(npc != null && npc.isSpawned() && !npc.getNavigator().isNavigating(),
                "cannot safely restore activatedTick until plugin is disabled and navigation is stopped");
            ClassLoader paperLoader = NPC.class.getClassLoader();
            Class<?> entityType = Class.forName("net.minecraft.world.entity.Entity", false, paperLoader);
            Object handle = getPaperEntityHandle(npc.getEntity());
            var activatedTickField = entityType.getField("activatedTick");
            long observedBeforeRestore = activatedTickField.getLong(handle);
            activatedTickField.setLong(handle, originalActivationTick);
            require(activatedTickField.getLong(handle) == originalActivationTick,
                "original activatedTick restoration did not read back");
            activationTickRestored = true;
            activationCleanupSatisfied = true;
            getLogger().info("AHMET_GOAL_ACTIVATION_CONTROL_CLEANUP outcome=plugin-disabled-navigation-stopped"
                + " activatedTickRestored=" + originalActivationTick
                + " observedBeforeRestore=" + observedBeforeRestore);
          } catch (Throwable error) {
            cleanupSucceeded = false;
            getLogger().severe("AHMET_GOAL_ACTIVATION_CONTROL_CLEANUP_FAILURE outcome=activation-restore-failed reason="
                + error.getClass().getSimpleName() + " pluginDisabled=true");
          }
        }
        if (cleanupSucceeded && pathfinderRestored && activationCleanupSatisfied) {
          try {
            set(ahmet, "deciding", false);
            getLogger().info("AHMET_GOAL_PATH_CONTROL_CLEANUP outcome=plugin-disabled-pause-restored=true");
          } catch (Throwable error) {
            cleanupSucceeded = false;
            try { set(ahmet, "deciding", true); } catch (Throwable ignored) { }
            getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=plugin-disabled-pause-restore-failed reason="
                + error.getClass().getSimpleName());
          }
        } else {
          try { set(ahmet, "deciding", true); } catch (Throwable ignored) { }
          getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=terminal-cleanup-incomplete"
              + " pluginDisabled=true pathfinderRestored=" + pathfinderRestored
              + " activationCleanupSatisfied=" + activationCleanupSatisfied
              + " activationRestore=" + activationRestoreOutcome() + " pauseHeld=true");
        }
      } else {
        cleanupSucceeded = false;
        getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=plugin-still-enabled pause-retained="
            + fieldQuiet(ahmet, "deciding"));
      }
    } else if (pathfinderComparisonStarted) {
      cleanupSucceeded = false;
      getLogger().severe("AHMET_GOAL_PATH_CONTROL_CLEANUP_FAILURE outcome=plugin-reference-missing pauseState=UNKNOWN");
    }
    return cleanupSucceeded;
  }

  /** Restores only navigator defaults changed by the foreign-route fixture and cancels only its exact strategy. */
  private boolean restoreForeignRouteFixture() {
    boolean restored=true;
    try {
      if(foreignRouteStrategy!=null&&npc!=null&&npc.isSpawned()
          &&npc.getNavigator().getPathStrategy()==foreignRouteStrategy){
        npc.getNavigator().cancelNavigation();
      }
    } catch(Throwable error) {
      restored=false;
      getLogger().severe("AHMET_GOAL_FOREIGN_ROUTE_CLEANUP_FAILURE outcome=owned-route-cancel-failed reason="
          +error.getClass().getSimpleName());
    }
    try {
      if(foreignNavigatorDefaultsChanged&&foreignNavigatorDefaults!=null){
        foreignNavigatorDefaults.destinationTeleportMargin(foreignOriginalTeleportMargin)
            .stationaryTicks(foreignOriginalStationaryTicks).speed(foreignOriginalSpeed);
      }
    } catch(Throwable error) {
      restored=false;
      getLogger().severe("AHMET_GOAL_FOREIGN_ROUTE_CLEANUP_FAILURE outcome=defaults-restore-failed reason="
          +error.getClass().getSimpleName());
    } finally {
      foreignRouteStrategy=null;
      foreignNavigatorDefaults=null;
      foreignNavigatorDefaultsChanged=false;
    }
    return restored;
  }

  private void startMockBridge() throws Exception {
    mockBridge = HttpServer.create(new InetSocketAddress("127.0.0.1", 3199), 4);
    bridgeExecutor = Executors.newFixedThreadPool(2);
    mockBridge.setExecutor(bridgeExecutor);
    mockBridge.createContext("/api/decide", this::mockDecision);
    mockBridge.createContext("/api/chat", this::mockChat);
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

  private void mockChat(HttpExchange exchange) {
    try {
      if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, "{}"); return; }
      byte[] body = exchange.getRequestBody().readNBytes(16_001);
      if (body.length > 16_000) { respond(exchange, 413, "{}"); return; }
      if (!authorized(exchange)) { respond(exchange, 401, "{}"); return; }
      chatCalls.incrementAndGet();
      respond(exchange, 200, "{\"reply\":\"Controlled fixture request accepted.\","
          + "\"taskSource\":\"heuristic\",\"task\":{\"type\":\"collect_item\","
          + "\"itemId\":\"minecraft:dirt\",\"amount\":1},\"cancel\":false,\"goHome\":false}");
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
  private boolean taskNavigationRouteMatches() {
    try { return (boolean) invoke(field(ahmet, "taskNavigationRoute"), "matches", new Class<?>[] { NPC.class }, npc); }
    catch (Exception error) { throw new IllegalStateException("could not inspect task route ownership", error); }
  }
  private boolean activationGuardOwnsOverride() {
    try { return invokeBoolean(field(ahmet, "activationRangeGuard"), "ownsOverride"); }
    catch (Exception error) { throw new IllegalStateException("could not inspect activation override", error); }
  }
  private long productionGlobalTick() {
    try { return ((Number) invoke(field(ahmet, "productionGoalAdapter"), "globalStepId",
        new Class<?>[] { int.class }, Bukkit.getCurrentTick())).longValue(); }
    catch (Exception error) { throw new IllegalStateException("could not read production tick", error); }
  }
  private long foreignWaitElapsedTicks() {
    try { return productionGlobalTick() - number(field(ahmet, "foreignNavigationWait"), "firstTick"); }
    catch (Exception error) { throw new IllegalStateException("could not inspect foreign wait duration", error); }
  }
  private long foreignWaitLastHelpAttemptTick() {
    try { return number(field(ahmet, "foreignNavigationWait"), "lastHelpAttemptTick"); }
    catch (Exception error) { throw new IllegalStateException("could not inspect foreign help retry", error); }
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
  private static boolean sameLocationWithin(Location expected, Location actual, double tolerance) {
    return expected != null && actual != null && expected.getWorld().equals(actual.getWorld())
        && expected.distanceSquared(actual) <= tolerance * tolerance;
  }
  private static String loc(Location value) { return value == null ? "null" : value.getBlockX()+","+value.getBlockY()+","+value.getBlockZ(); }
  private static String rounded(double value) { return String.format(java.util.Locale.ROOT, "%.3f", value); }
  private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
