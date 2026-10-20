package dev.ahmetprobe;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCTeleportEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Opt-in disposable Paper runtime acceptance for Citizens schedule walking and sleep. */
public final class RuntimeScheduleProbe extends JavaPlugin implements Listener {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final int NAV_TIMEOUT_TICKS = 1_800;
  private static final int SETUP_POSITION_TIMEOUT_TICKS = 120;
  private Plugin ahmet;
  private World world;
  private NPC npc;
  private Object adapter;
  private Object task;
  private Location home;
  private Location bedFoot;
  private Location chest;
  private Block proxyTarget;
  private World wrongWorldProxy;
  private final List<String> messages = new ArrayList<>();
  private Player proxy;
  private boolean restartVerify;
  private Properties restartExpected;
  private int trackedNpcId = -1;
  private UUID trackedEntityUuid;
  private boolean failed;
  private boolean measuring;
  private boolean fixtureSetupMovement;
  private Location setupTeleportTarget;
  private Location lastLocation;
  private boolean lastSleeping;
  private double routeDistance;
  private double maxRouteStep;
  private double maxSleepPoseShift;
  private int poseShiftSamples;
  private boolean bedApproachObserved;
  private boolean unsafePreSleepPoseObserved;
  private boolean wanderBoundsExceeded;
  private double wanderMaxRadiusSquared;
  private final Set<String> wanderTargets = new LinkedHashSet<>();
  private int teleportEvents;
  private int setupTeleportEvents;
  private int stageTeleportBaseline;
  private int wrongWorldAccesses;
  private String stageName = "boot";
  private String measurementMode = "none";
  private BukkitTask heartbeat;

  @Override public void onEnable() {
    if (!Boolean.getBoolean("ahmet.scheduleProbe")) {
      getLogger().warning("AHMET_SCHEDULE_PROBE_DISABLED: set -Dahmet.scheduleProbe=true only on a disposable server");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    restartVerify = Boolean.getBoolean("ahmet.scheduleProbe.restartVerify");
    if (restartVerify) {
      try {
        restartExpected = new Properties();
        try (InputStream in = Files.newInputStream(getDataFolder().toPath().resolve("restart.properties"))) { restartExpected.load(in); }
        trackedNpcId = Integer.parseInt(restartExpected.getProperty("npcId"));
      } catch (Exception e) { Bukkit.getScheduler().runTask(this, () -> fail(e)); return; }
    }
    Bukkit.getPluginManager().registerEvents(this, this);
    Bukkit.getScheduler().runTaskLater(this, this::initialize, 40L);
  }

  private void initialize() {
    try {
      ahmet = Bukkit.getPluginManager().getPlugin("VillagerAhmet");
      world = Bukkit.getWorld("world");
      if (ahmet == null || world == null) throw new IllegalStateException("VillagerAhmet or disposable world is missing");
      task = getField(ahmet, "task");
      adapter = getField(ahmet, "scheduleAdapter");
      if (restartVerify) beginRestartVerification();
      else beginFreshRun();
    } catch (Exception e) { fail(e); }
  }

  private void beginFreshRun() throws Exception {
    prepareArena();
    home = new Location(world, .5, 120, .5);
    chest = new Location(world, 0, 120, -4);
    bedFoot = new Location(world, 4, 120, 0);
    Block chestBlock = chest.getBlock(); chestBlock.setType(Material.CHEST, false);
    placeBed();
    npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "Ahmet");
    trackedNpcId = npc.getId();
    fixtureSetupMovement = true;
    setupTeleportTarget=home.clone();
    try { if (!npc.spawn(home)) throw new IllegalStateException("Could not spawn real Citizens VILLAGER"); }
    finally { fixtureSetupMovement = false; setupTeleportTarget=null; }
    trackedEntityUuid=npc.getEntity().getUniqueId();
    setField(ahmet, "npc", npc);
    setField(ahmet, "home", home.clone());
    setField(ahmet, "protectedHome", null);
    setField(ahmet, "protectHomeRadius", 0);
    setField(ahmet, "scheduleBed", null);
    setField(ahmet, "chestLocation", chestBlock.getLocation());
    Object saved = getField(ahmet, "savedNpcReference");
    invoke(saved, "created", new Class<?>[]{int.class}, npc.getId());
    ahmet.getConfig().set("options.search-radius", 8);
    ahmet.getConfig().set("options.schedule-enabled", true);
    proxy = makeProxy();
    invokeNoArg(ahmet, "persist");
    heartbeat = Bukkit.getScheduler().runTaskTimer(this, this::sample, 1L, 1L);
    log("ARENA_READY mode=fresh npcType=" + npc.getEntity().getType() + " home=" + loc(home) + " bed=" + loc(bedFoot));
    phaseWalking();
  }

  private void phaseWalking() {
    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    resetTravel("wander");
    world.setTime(10_000L);
    await("wander observes multiple targets and walks inside home radius", NAV_TIMEOUT_TICKS,
        () -> phase().equals("WANDER") && wanderTargets.size() >= 2 && routeDistance > 2.0,
        () -> {
          require(!wanderBoundsExceeded && wanderMaxRadiusSquared <= 36.05, "wander position exceeded six-block home radius");
          require(wanderTargets.size() >= 2, "wander did not expose multiple distinct schedule targets to observation");
          require(maxRouteStep<=1.5,"wander normal-route tick step exceeded 1.5 blocks");
          assertNoStageTeleport("wander");
          logSample("wander");
          wanderRecoveryPhase();
        });
  }

  private void wanderRecoveryPhase() {
    fixtureTeleport(new Location(world, 8.5, 120, 8.5), "wander-recovery", 10_000L, () ->
        await("WANDER recovers home from outside its radius by walking", NAV_TIMEOUT_TICKS,
            () -> phase().equals("WANDER") && horizontalDistance(npc.getEntity().getLocation(), home) <= 2.5,
            () -> {
              require(routeDistance > 2.0, "WANDER outside-radius recovery did not produce measured walking");
              require(maxRouteStep <= 1.5, "WANDER recovery normal-route tick step exceeded 1.5 blocks");
              assertNoStageTeleport("WANDER outside-radius recovery");
              logSample("wander-recovery");
              returnHomePhase();
            }));
  }

  private void returnHomePhase() {
    fixtureTeleport(new Location(world, 8.5, 120, 8.5),"work-return",2_000L,()->
        await("WORK returns to home by walking", NAV_TIMEOUT_TICKS,
            () -> phase().equals("WORK") && horizontalDistance(npc.getEntity().getLocation(), home) <= 2.55,
            () -> {
              require(routeDistance > 2.0, "WORK home return did not produce measured walking");
              require(maxRouteStep<=1.5,"WORK normal-route tick step exceeded 1.5 blocks");
              assertNoStageTeleport("WORK home return");
              logSample("work-return");
              wakeReturnPhase();
            }));
  }

  private void wakeReturnPhase() {
    fixtureTeleport(new Location(world, 8.5, 120, 8.5),"wake-return",500L,()->
        await("WAKE returns to home by walking", NAV_TIMEOUT_TICKS,
            () -> phase().equals("WAKE") && horizontalDistance(npc.getEntity().getLocation(), home) <= 2.55,
            () -> {
              require(routeDistance > 2.0, "WAKE home return did not produce measured walking");
              require(maxRouteStep<=1.5,"WAKE normal-route tick step exceeded 1.5 blocks");
              assertNoStageTeleport("WAKE home return");
              logSample("wake-return");
              bindBedAndSleep();
            }));
  }

  private void bindBedAndSleep() {
    proxyTarget = bedFoot.getBlock();
    require(command("ahmet bed"), "production /ahmet bed dispatch rejected");
    Location bound = (Location) getFieldUnchecked(ahmet, "scheduleBed");
    require(bound != null && sameBlock(bound, bedFoot), "production bed command did not bind the bed foot");
    require(messages.stream().anyMatch(m -> m.contains("yatağı kaydedildi")), "bed command success response was not observed");
    fixtureTeleport(new Location(world, .5, 120, 5.5),"bed-walk-sleep",13_000L,()->
        await("Citizens walks adjacent before native sleep", NAV_TIMEOUT_TICKS,
            () -> phase().equals("SLEEP") && bool(adapter,"ownsSleep") && ((Villager) npc.getEntity()).isSleeping(),
            () -> {
              require(routeDistance > 1.0, "no route walking was observed on the bed approach");
              require(maxRouteStep<=1.5,"bed approach normal-route tick step exceeded 1.5 blocks");
              require(bedApproachObserved&&!unsafePreSleepPoseObserved, "sleep transition was not observed from a safe adjacent standing cell");
              assertNoStageTeleport("bed approach");
              logSample("sleep-start");
              await("WAKE phase wakes native villager", 300,
                  () -> { world.setTime(500L); return phase().equals("WAKE") && !((Villager) npc.getEntity()).isSleeping(); },
                  () -> { require(!((Villager) npc.getEntity()).isSleeping(), "villager remained asleep after WAKE phase"); taskPreemption(); });
            }));
  }

  private void taskPreemption() {
    world.setTime(13_000L);
    await("schedule reaches owned sleep before job", NAV_TIMEOUT_TICKS,
        () -> phase().equals("SLEEP") && ((Villager) npc.getEntity()).isSleeping() && bool(adapter, "ownsSleep"),
        () -> {
          Block first = world.getBlockAt(7, 120, 3); first.setType(Material.DIRT, false);
          Block second = world.getBlockAt(8, 120, 3); second.setType(Material.DIRT, false);
          invokeNoArgUnchecked(ahmet, "invalidateTargetScan");
          proxyTarget = null;
          boolean dispatched = command("ahmet collect dirt 2");
          require(dispatched, "production collect command dispatch failed");
          require(bool(task, "active"), "job was not active in the same command callback");
          require(!bool(adapter, "ownsSleep") && !((Villager) npc.getEntity()).isSleeping(), "schedule sleep was not preempted synchronously");
          require(!bool(adapter, "ownsNavigation"), "schedule route remained owned after job admission");
          resetTravel("task-route");
          await("harvest exactly one dirt before cancel", 1_800,
              () -> integer(task, "collected") == 1 && integer(task, "cargo") == 1,
              () -> {
                require(integer(task, "requested") == 2, "fixture job quantity changed");
                require(command("ahmet cancel"), "production cancel command dispatch failed");
                require(!bool(task, "active") && integer(task, "cargo") == 1, "cancel did not retain one item of cargo");
                require(items(task).getOrDefault("DIRT", 0) == 1, "cancelled cargo was not exactly one DIRT");
                require(!bool(adapter, "ownsTurn"), "schedule resumed while cancelled cargo remained");
                int cancelledAt = Bukkit.getCurrentTick();
                await("cancelled cargo remains unchanged across production ticks", 10,
                    () -> {
                      require(!bool(task,"active") && integer(task,"cargo")==1 && items(task).equals(java.util.Map.of("DIRT",1)),"cancelled cargo changed before delivery");
                      require(!bool(adapter,"ownsNavigation")&&!bool(adapter,"ownsSleep")&&!npc.getNavigator().isNavigating()&&!((Villager)npc.getEntity()).isSleeping(),"schedule resumed while cancelled cargo remained");
                      return Bukkit.getCurrentTick()-cancelledAt>=5;
                    },
                    () -> {
                      assertNoStageTeleport("cancelled job route");
                      require(command("ahmet deliver"), "production deliver command dispatch failed");
                      require(bool(task, "active"), "production deliver command did not resume cargo task");
                      await("deliver exact cancelled cargo and resume schedule", 1_800,
                          () -> !bool(task, "active") && integer(task, "cargo") == 0,
                          () -> {
                            require(routeDistance>1.0,"cancelled task did not show actual walking");
                            require(maxRouteStep<=1.5,"cancelled task normal-route tick step exceeded 1.5 blocks");
                            assertNoStageTeleport("cancelled job route");
                            require(count(((Chest)chest.getBlock().getState()).getInventory(), Material.DIRT) == 1, "chest did not receive exactly the cancelled cargo");
                            require(integer(task, "collected") == 1 && integer(task, "deposited") == 1, "task counters do not conserve the delivered item");
                            require(world.getBlockAt(8, 120, 3).getType() == Material.DIRT, "second source block changed");
                            await("schedule resumes after job, cargo, journal clear", NAV_TIMEOUT_TICKS,
                                () -> bool(adapter, "ownsSleep"), this::recoveryDuringSleep);
                          });
                    });
              });
        });
  }

  private void recoveryDuringSleep() {
    setFieldUnchecked(ahmet, "recoveryBlocked", true);
    await("recovery preempts owned sleep", 30,
        () -> !bool(adapter, "ownsSleep") && !((Villager) npc.getEntity()).isSleeping(),
        () -> {
          require(!bool(adapter, "ownsNavigation"), "recovery retained schedule navigation");
          setFieldUnchecked(ahmet, "recoveryBlocked", false);
          world.setTime(10_000L);
    await("schedule starts a real wander route", NAV_TIMEOUT_TICKS,
              () -> bool(adapter, "ownsNavigation"), this::recoveryDuringWalk);
        });
  }

  private void recoveryDuringWalk() {
    setFieldUnchecked(ahmet, "recoveryBlocked", true);
    await("recovery preempts owned walking", 30,
        () -> !bool(adapter, "ownsNavigation") && !bool(adapter, "ownsTurn"),
        () -> {
          require(!npc.getNavigator().isNavigating(), "schedule navigator remained active after recovery preemption");
          setFieldUnchecked(ahmet, "recoveryBlocked", false);
          jobAdmissionPreemptsWalking();
        });
  }

  private void jobAdmissionPreemptsWalking() {
    world.setTime(10_000L);
    await("schedule owns a walking route before job admission", NAV_TIMEOUT_TICKS,
        () -> phase().equals("WANDER") && bool(adapter,"ownsNavigation") && npc.getNavigator().isNavigating(),
        () -> {
          world.getBlockAt(1,120,1).setType(Material.DIRT,false);
          invokeNoArgUnchecked(ahmet,"invalidateTargetScan");
          resetTravel("walking-job-preemption");
          require(command("ahmet collect dirt 1"), "production collect command dispatch failed during schedule walking");
          require(bool(task,"active"),"walking-preemption job was not admitted");
          require(!bool(adapter,"ownsNavigation")&&!npc.getNavigator().isNavigating(),"schedule-owned walk was not cancelled in the command callback");
          await("task takes navigation and walks after schedule preemption", 1_200,
              () -> getFieldUnchecked(ahmet,"navigationGoal")!=null && npc.getNavigator().isNavigating() && routeDistance>.25,
              () -> {
                require(teleportEvents==stageTeleportBaseline,"teleport occurred while task took over movement");
                await("walking-admitted task delivers remaining dirt",1_800,
                    () -> !bool(task,"active")&&integer(task,"cargo")==0&&integer(task,"collected")==1&&integer(task,"deposited")==1,
                    () -> {
                      require(count(((Chest)chest.getBlock().getState()).getInventory(),Material.DIRT)==2,"combined cargo deliveries were not exact");
                      require(routeDistance>.5,"walking-admitted task showed no measured movement");
                      require(maxRouteStep<=1.5,"walking-admitted task normal-route tick step exceeded 1.5 blocks");
                      assertNoStageTeleport("walking-job task route");
                      bedFailureCases();
                    });
              });
        });
  }

  private void bedFailureCases() {
    resetBedAndBind();
    bedFoot.getBlock().setType(Material.AIR, false);
    world.setTime(13_000L);
    await("removed bound bed safely falls back home", NAV_TIMEOUT_TICKS,
        () -> fallbackAtHomeOrHeading("bed block is missing"),
        () -> {
          require(!((Villager) npc.getEntity()).isSleeping(), "NPC slept at a removed bed");
          placeBed(); resetBedAndBind(); setOccupied(true);
          world.setTime(13_000L);
          await("occupied bed safely falls back home", NAV_TIMEOUT_TICKS,
              () -> fallbackAtHomeOrHeading("bed is occupied"),
              () -> {
                require(!((Villager) npc.getEntity()).isSleeping(), "NPC slept in an occupied bed");
                setOccupied(false); resetBedAndBind();
                for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                  world.getBlockAt(bedFoot.getBlockX()+d[0],119,bedFoot.getBlockZ()+d[1]).setType(Material.MAGMA_BLOCK,false);
                world.setTime(13_000L);
                await("bed with no safe approach falls back", NAV_TIMEOUT_TICKS,
                    () -> fallbackAtHomeOrHeading("no loaded safe approach"),
                    () -> {
                      require(!((Villager) npc.getEntity()).isSleeping(), "NPC slept without a safe adjacent cell");
                      for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                        world.getBlockAt(bedFoot.getBlockX()+d[0],119,bedFoot.getBlockZ()+d[1]).setType(Material.SMOOTH_STONE,false);
                      probeStaleBeds();
                    });
              });
        });
  }

  private void probeStaleBeds() {
    long chunkX = 20, chunkZ = 20;
    if (world.isChunkLoaded((int) chunkX, (int) chunkZ)) world.unloadChunk((int) chunkX, (int) chunkZ, true);
    final int[] loads = {0};
    Listener counter = new Listener() {
      @EventHandler public void onLoad(ChunkLoadEvent e) { if (e.getWorld() == world && e.getChunk().getX() == chunkX && e.getChunk().getZ() == chunkZ) loads[0]++; }
    };
    Bukkit.getPluginManager().registerEvents(counter, this);
    setFieldUnchecked(ahmet, "scheduleBed", new Location(world, chunkX * 16 + 1, 120, chunkZ * 16 + 1));
    world.setTime(13_000L);
    await("unloaded stale bed rejects without loading its chunk", NAV_TIMEOUT_TICKS,
        () -> fallbackAtHomeOrHeading("bound bed chunk is unloaded"),
        () -> {
          require(loads[0] == 0 && !world.isChunkLoaded((int)chunkX,(int)chunkZ), "stale-bed check loaded the bound chunk");
          wrongWorldProxy = fakeWorld("wrong-world-probe");
          setFieldUnchecked(ahmet, "scheduleBed", new Location(wrongWorldProxy, 4, 120, 0));
          await("wrong-world bed rejects before world access", NAV_TIMEOUT_TICKS,
              () -> fallbackAtHomeOrHeading("bound bed is in another world"),
              () -> {
                require(loads[0] == 0, "wrong-world validation loaded the stale chunk");
                require(wrongWorldAccesses==0,"wrong-world validation accessed the untrusted World proxy");
                wrongWorldProxy=null;
                org.bukkit.event.HandlerList.unregisterAll(counter);
                restoreFinalArena();
              });
        });
  }

  private void restoreFinalArena() {
    placeBed();
    for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
      world.getBlockAt(bedFoot.getBlockX()+d[0],119,bedFoot.getBlockZ()+d[1]).setType(Material.SMOOTH_STONE,false);
    resetBedAndBind();
    Location savedHome = (Location) getFieldUnchecked(ahmet, "home");
    require(savedHome != null && sameBlock(savedHome, home), "home changed during schedule probe");
    require(count(((Chest)chest.getBlock().getState()).getInventory(), Material.DIRT) == 2, "final chest count changed");
    require(integer(task, "cargo") == 0 && !bool(task, "active"), "task did not end idle and empty");
    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);
    resetTravel("fresh-final-wake");
    world.setTime(500L);
    await("fresh verification finishes awake and home before shutdown",NAV_TIMEOUT_TICKS,
        () -> phase().equals("WAKE")&&!((Villager)npc.getEntity()).isSleeping()
            &&status(adapter).contains("at home")&&!bool(adapter,"ownsNavigation")&&safeCell(home),
        this::finishFreshRun);
  }

  private void finishFreshRun(){
    try{
      require(teleportEvents==0,"Citizens teleport event occurred during measured production movement");
      require(maxRouteStep<=1.5,"fresh final WAKE route tick step exceeded 1.5 blocks");
      require(count(((Chest)chest.getBlock().getState()).getInventory(),Material.DIRT)==2,"fresh final chest count changed");
      require(integer(task,"cargo")==0&&!bool(task,"active"),"fresh final task is not idle and empty");
      saveRestartExpectation();invokeNoArgUnchecked(ahmet,"persist");
      log("AHMET_SCHEDULE_PROBE_FRESH_PASSED proxyCommands=true awakeAtHome=true taskCollected="+integer(task,"collected")
          +" taskDeposited="+integer(task,"deposited")+" dirtInChest="+count(((Chest)chest.getBlock().getState()).getInventory(),Material.DIRT)
          +" teleportEventsCumulative="+teleportEvents+" setupTeleportEvents="+setupTeleportEvents+" maxRouteStep="+fmt(maxRouteStep)
          +" nativePoseShifts="+poseShiftSamples+" restartVerifyRequired=true");
      Bukkit.shutdown();
    }catch(Exception e){fail(e);}
  }

  private void beginRestartVerification() throws Exception {
    if (restartExpected == null) throw new IllegalStateException("restart expectation file is missing");
    Properties expected = restartExpected;
    heartbeat = Bukkit.getScheduler().runTaskTimer(this, this::sample, 1L, 1L);
    await("Citizens restores saved schedule NPC", 400,
        () -> {
          try {
            npc = (NPC) getField(ahmet, "npc");
            if(npc!=null&&npc.isSpawned()&&npc.getEntity() instanceof Villager)trackedEntityUuid=npc.getEntity().getUniqueId();
            return npc != null && npc.isSpawned() && npc.getEntity() instanceof Villager;
          } catch (Exception e) { return false; }
        },
        () -> {
          try {
            Location savedHome=(Location)getField(ahmet,"home");
            Location savedBed=(Location)getField(ahmet,"scheduleBed");
            Object savedTask=getField(ahmet,"task");
            require(npc.getId()==Integer.parseInt(expected.getProperty("npcId")),"restored Citizens id differs");
            require(npc.getUniqueId().toString().equals(expected.getProperty("npcUuid")),"restored Citizens UUID differs");
            require(savedHome!=null && expected.getProperty("home").equals(blockLoc(savedHome)),"persisted home block differs");
            require(savedBed!=null && expected.getProperty("bed").equals(blockLoc(savedBed)),"persisted bed differs");
            require(expected.getProperty("taskId").equals(string(savedTask,"taskId")),"persisted task identity differs");
            require(integer(savedTask,"collected")==Integer.parseInt(expected.getProperty("collected")),"persisted collected count differs");
            require(integer(savedTask,"deposited")==Integer.parseInt(expected.getProperty("deposited")),"persisted deposited count differs");
            require(integer(savedTask,"cargo")==0 && !bool(savedTask,"active"),"restart task is not idle and empty");
            require(!bool(ahmet,"recoveryBlocked"),"restart recovery state is blocked");
            require(!(boolean)invokeNoArg(getField(ahmet,"journal"),"hasOperations"),"restart journal has pending operations");
            adapter=getField(ahmet,"scheduleAdapter");
            home=savedHome;bedFoot=savedBed;
            resetTravel("restart-bed-walk");
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);
            world.setTime(13_000L);
            await("saved bed resumes sleep after restart", NAV_TIMEOUT_TICKS,
                () -> phase().equals("SLEEP")&&bool(adapter,"ownsSleep")&&((Villager)npc.getEntity()).isSleeping(),
                () -> {
                  try {
                    require(routeDistance>1.0&&bedApproachObserved&&!unsafePreSleepPoseObserved,"restart sleep transition was not observed from a safe adjacent standing cell");
                    require(maxRouteStep<=1.5,"restart normal-route tick step exceeded 1.5 blocks");
                    require(teleportEvents==0,"NPC teleport event occurred during restart schedule movement");
                    world.setTime(500L);
                    await("saved schedule wakes after restart",300,
                        () -> phase().equals("WAKE")&&!((Villager)npc.getEntity()).isSleeping(),
                        () -> {
                          require(teleportEvents==0,"NPC teleport event occurred during restart wake");
                          log("AHMET_SCHEDULE_PROBE_RESTART_PASSED arenaRebuilt=false npcType="+npc.getEntity().getType()
                              +" npcId="+npc.getId()+" npcUuid="+npc.getUniqueId()+" home="+blockLoc(savedHome)+" bed="+blockLoc(savedBed)
                              +" taskId="+string(savedTask,"taskId")+" teleports="+teleportEvents+" setupTeleports="+setupTeleportEvents);
                          if(heartbeat!=null)heartbeat.cancel();
                          Bukkit.shutdown();
                        });
                  } catch(Exception e){fail(e);}
                });
          } catch(Exception e){fail(e);}
        });
  }

  private void resetBedAndBind() {
    try {
      placeBed(); proxyTarget=bedFoot.getBlock(); command("ahmet bed");
      require(sameBlock((Location)getField(ahmet,"scheduleBed"),bedFoot),"could not restore production bed binding");
    } catch(Exception e){fail(e);}
  }

  private void placeBed() {
    Block foot=bedFoot.getBlock(); Block head=world.getBlockAt(bedFoot.getBlockX()+1,bedFoot.getBlockY(),bedFoot.getBlockZ());
    foot.setType(Material.RED_BED,false); head.setType(Material.RED_BED,false);
    Bed f=(Bed)foot.getBlockData(); f.setFacing(org.bukkit.block.BlockFace.EAST); f.setPart(Bed.Part.FOOT); f.setOccupied(false); foot.setBlockData(f,false);
    Bed h=(Bed)head.getBlockData(); h.setFacing(org.bukkit.block.BlockFace.EAST); h.setPart(Bed.Part.HEAD); h.setOccupied(false); head.setBlockData(h,false);
  }

  private void setOccupied(boolean value) {
    for(int x=bedFoot.getBlockX();x<=bedFoot.getBlockX()+1;x++){
      Block block=world.getBlockAt(x,bedFoot.getBlockY(),bedFoot.getBlockZ());
      Bed data=(Bed)block.getBlockData();data.setOccupied(value);block.setBlockData(data,false);
    }
  }

  private void prepareArena() {
    for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++){
      var chunk=world.getChunkAt(cx,cz);chunk.load(true);chunk.setForceLoaded(true);
    }
    for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)for(int y=110;y<=135;y++){
      Block block=world.getBlockAt(x,y,z);if(!block.getType().isAir())block.setType(Material.AIR,false);
    }
    for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)world.getBlockAt(x,119,z).setType(Material.SMOOTH_STONE,false);
  }

  private Player makeProxy() {
    InvocationHandler handler=(p,m,args)->switch(m.getName()){
      case "hasPermission" -> "ahmet.admin".equals(args[0]);
      case "getUniqueId" -> OWNER;
      case "getWorld" -> world;
      case "getLocation" -> npc.getEntity().getLocation().clone();
      case "getTargetBlockExact" -> proxyTarget;
      case "sendMessage" -> { if(args!=null&&args.length>0)messages.add(String.valueOf(args[0])); yield null; }
      case "getName","getDisplayName" -> "ScheduleProbeProxy";
      case "isOnline" -> true;
      case "equals" -> p==args[0];
      case "hashCode" -> System.identityHashCode(p);
      case "toString" -> "ScheduleProbeProxy (not a human player)";
      default -> primitiveDefault(m.getReturnType());
    };
    return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},handler);
  }

  private boolean command(String line) {
    messages.clear();
    String[] tokens=line.trim().split("\\s+");
    PluginCommand registered=((org.bukkit.plugin.java.JavaPlugin)ahmet).getCommand(tokens[0]);
    if(registered==null)throw new IllegalStateException("registered /ahmet PluginCommand not found");
    boolean ok=registered.execute((CommandSender)proxy,tokens[0],Arrays.copyOfRange(tokens,1,tokens.length));
    log("AHMET_PROXY_COMMAND mode=registered-plugin-command-proxy command="+line+" accepted="+ok+" messages="+messages);
    return ok;
  }

  private void await(String name,int timeoutTicks,BooleanSupplier condition,Runnable next) {
    if(failed)return;
    stageName=name;long started=Bukkit.getCurrentTick();
    getLogger().info("AHMET_SCHEDULE_STAGE_START name="+name+" tick="+started+" timeoutTicks="+timeoutTicks);
    final int[] elapsed={0};
    Bukkit.getScheduler().runTaskTimer(this, timer -> {
      if(failed){timer.cancel();return;}
      elapsed[0]++;
      try{
        if(condition.getAsBoolean()){
          timer.cancel();next.run();getLogger().info("AHMET_SCHEDULE_STAGE_PASS name="+name+" ticks="+elapsed[0]+" tick="+Bukkit.getCurrentTick());
        }else if(elapsed[0]>=timeoutTicks){timer.cancel();throw new IllegalStateException("timeout stage="+name+" ticks="+elapsed[0]+" location="+loc(npc==null?null:npc.getEntity().getLocation())+" schedule="+status(adapter));}
        else if(elapsed[0]%100==0)logSample(name);
      }catch(Exception e){timer.cancel();fail(e);}
    },1L,1L);
  }

  private void sample() {
    if(npc==null||!npc.isSpawned()||!(npc.getEntity() instanceof Villager villager))return;
    Location now=villager.getLocation();boolean sleeping=villager.isSleeping();
    if(measuring&&measurementMode.equals("wander")){
      double radiusSquared=now.distanceSquared(home);wanderMaxRadiusSquared=Math.max(wanderMaxRadiusSquared,radiusSquared);
      if(radiusSquared>36.05)wanderBoundsExceeded=true;
      Location target=scheduleTarget();
      if(target!=null){if(target.distanceSquared(home)>36.05)wanderBoundsExceeded=true;wanderTargets.add(blockLoc(target));}
    }
    if(measuring&&lastLocation!=null){
      double step=now.distance(lastLocation);
      if(sleeping!=lastSleeping||sleeping||lastSleeping){maxSleepPoseShift=Math.max(maxSleepPoseShift,step);if(step>.02)poseShiftSamples++;}
      else {routeDistance+=step;maxRouteStep=Math.max(maxRouteStep,step);}
    }
    lastLocation=now.clone();lastSleeping=sleeping;
  }

  private void resetTravel(String label) {
    routeDistance=0;maxRouteStep=0;maxSleepPoseShift=0;poseShiftSamples=0;
    measurementMode=label;stageTeleportBaseline=teleportEvents;
    bedApproachObserved=false;unsafePreSleepPoseObserved=false;
    if(label.equals("wander")){wanderBoundsExceeded=false;wanderMaxRadiusSquared=0;wanderTargets.clear();}
    lastLocation=npc.getEntity().getLocation().clone();lastSleeping=((Villager)npc.getEntity()).isSleeping();
    stageName=label;measuring=true;
  }

  private void fixtureTeleport(Location target,String nextMeasurement,long phaseTime,Runnable next){
    measuring=false;
    try{
      setField(ahmet,"recoveryBlocked",true);
      invoke(adapter,"suspend",new Class<?>[]{NPC.class,String.class},npc,"fixture setup reposition");
      fixtureSetupMovement=true;
      setupTeleportTarget=target.clone();
      npc.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN);
      await("fixture setup position reaches requested destination",SETUP_POSITION_TIMEOUT_TICKS,
          ()->npc.getEntity().getWorld()==target.getWorld()&&npc.getEntity().getLocation().distanceSquared(target)<=.04,
          ()->{
            world.setTime(phaseTime);
            resetTravel(nextMeasurement);
            fixtureSetupMovement=false;
            setupTeleportTarget=null;
            setFieldUnchecked(ahmet,"recoveryBlocked",false);
            next.run();
          });
    }catch(Exception e){
      fixtureSetupMovement=false;
      setupTeleportTarget=null;
      setFieldUnchecked(ahmet,"recoveryBlocked",false);
      fail(e);
    }
  }

  private Location scheduleTarget(){
    Object state=getFieldUnchecked(adapter,"state");
    return (Location)getFieldUnchecked(state,"target");
  }

  private void assertNoStageTeleport(String label){
    require(teleportEvents==stageTeleportBaseline,"NPC teleport event during "+label+": cumulative="+teleportEvents+" baseline="+stageTeleportBaseline);
  }

  private boolean fallbackAtHomeOrHeading(String cause){
    if(!status(adapter).contains(cause)||((Villager)npc.getEntity()).isSleeping())return false;
    Location current=npc.getEntity().getLocation();
    if(current.distanceSquared(home)<=6.25&&!bool(adapter,"ownsNavigation")&&!npc.getNavigator().isNavigating())return safeCell(home);
    Location target=scheduleTarget();
    return bool(adapter,"ownsNavigation")&&target!=null&&target.getWorld()==current.getWorld()
        &&target.distanceSquared(home)<current.distanceSquared(home)&&safeCell(target);
  }

  private boolean safeCell(Location location){
    int x=location.getBlockX(),y=location.getBlockY(),z=location.getBlockZ();
    if(!world.isChunkLoaded(x>>4,z>>4))return false;
    Block floor=world.getBlockAt(x,y-1,z),feet=world.getBlockAt(x,y,z),head=world.getBlockAt(x,y+1,z);
    return floor.getType().isSolid()&&feet.isPassable()&&head.isPassable()
        &&!floor.isLiquid()&&!feet.isLiquid()&&!head.isLiquid()
        &&floor.getType()!=Material.MAGMA_BLOCK&&feet.getType()!=Material.MAGMA_BLOCK&&head.getType()!=Material.MAGMA_BLOCK
        &&floor.getType()!=Material.CACTUS&&feet.getType()!=Material.CACTUS&&head.getType()!=Material.CACTUS
        &&floor.getType()!=Material.FIRE&&feet.getType()!=Material.FIRE&&head.getType()!=Material.FIRE;
  }

  @EventHandler public void onNpcTeleport(NPCTeleportEvent event) {
    if(trackedNpcId<0||event.getNPC().getId()!=trackedNpcId)return;
    Location to=event.getTo();
    if(fixtureSetupMovement&&setupTeleportTarget!=null&&to!=null
        &&to.getWorld()==setupTeleportTarget.getWorld()&&to.distanceSquared(setupTeleportTarget)<=.04)setupTeleportEvents++;
    else teleportEvents++;
  }

  @EventHandler public void onEntityPoseChange(EntityPoseChangeEvent event){
    if(!measuring||trackedEntityUuid==null||!trackedEntityUuid.equals(event.getEntity().getUniqueId())
        ||event.getPose()!=Pose.SLEEPING
        ||!(measurementMode.equals("bed-walk-sleep")||measurementMode.equals("restart-bed-walk")))return;
    Location standing=event.getEntity().getLocation().clone();
    boolean adjacent=adjacentToBed(standing);
    boolean safe=adjacent&&safeCell(standing);
    if(safe)bedApproachObserved=true;else unsafePreSleepPoseObserved=true;
    log("AHMET_PRE_SLEEP_STANDING measurement="+measurementMode+" npcId="+trackedNpcId+" entityUuid="+trackedEntityUuid
        +" location="+loc(standing)+" safeAdjacent="+safe+" adjacent="+adjacent+" safeCell="+(adjacent&&safeCell(standing)));
  }

  private void logSample(String label) {
    Location loc=npc==null||!npc.isSpawned()?null:npc.getEntity().getLocation();
    var navigator=npc==null||!npc.isSpawned()?null:npc.getNavigator();
    boolean navigating=navigator!=null&&navigator.isNavigating();
    String localPathfinder=navigator==null?"n/a":String.valueOf(navigator.getLocalParameters().pathfinderType());
    String defaultPathfinder=navigator==null?"n/a":String.valueOf(navigator.getDefaultParameters().pathfinderType());
    Object pathStrategy=navigator==null?null:navigator.getPathStrategy();
    String strategyClass=pathStrategy==null?"null":pathStrategy.getClass().getName();
    Object target=adapter==null?null:getFieldUnchecked(adapter,"state");
    Location goal=target==null?null:(Location)getFieldUnchecked(target,"target");
    log("AHMET_SCHEDULE_SAMPLE stage="+label+" tick="+Bukkit.getCurrentTick()+" time="+(world==null?-1:world.getTime())
        +" phase="+invokeNoArgUnchecked(adapter,"phase")+" location="+loc(loc)+" routeTarget="+loc(goal)
        +" schedule="+status(adapter)+" sleeping="+(npc!=null&&npc.isSpawned()&&((Villager)npc.getEntity()).isSleeping())
        +" navIsNavigating="+navigating+" localPathfinderType="+localPathfinder+" defaultPathfinderType="+defaultPathfinder
        +" pathStrategyClass="+strategyClass
        +" routeDistance="+fmt(routeDistance)+" maxRouteStep="+fmt(maxRouteStep)+" nativePoseShiftMax="+fmt(maxSleepPoseShift)
        +" measurement="+measurementMode+" teleportsCumulative="+teleportEvents+" setupTeleports="+setupTeleportEvents
        +" stageTeleportDelta="+(teleportEvents-stageTeleportBaseline)+" wanderTargets="+wanderTargets.size());
  }

  private void saveRestartExpectation() {
    try {
      Properties p=new Properties();p.setProperty("home",blockLoc((Location)getField(ahmet,"home")));
      p.setProperty("bed",blockLoc((Location)getField(ahmet,"scheduleBed")));
      p.setProperty("npcId",Integer.toString(npc.getId()));p.setProperty("npcUuid",npc.getUniqueId().toString());
      p.setProperty("taskId",string(task,"taskId"));p.setProperty("collected",Integer.toString(integer(task,"collected")));
      p.setProperty("deposited",Integer.toString(integer(task,"deposited")));
      Files.createDirectories(getDataFolder().toPath());
      try(OutputStream out=Files.newOutputStream(getDataFolder().toPath().resolve("restart.properties"))){p.store(out,"Disposable schedule restart expectation");}
    } catch(Exception e){throw new IllegalStateException("could not save restart expectation",e);}
  }

  private void fail(Exception e) {
    if(failed)return;failed=true;measuring=false;
    getLogger().severe("AHMET_SCHEDULE_PROBE_ERROR stage="+stageName+" "+e.getClass().getName()+": "+e.getMessage());
    e.printStackTrace();Bukkit.shutdown();
  }

  private boolean adjacentToBed(Location location){
    for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
      double x=bedFoot.getBlockX()+.5+d[0],z=bedFoot.getBlockZ()+.5+d[1];
      if(Math.abs(location.getX()-x)<=.20&&Math.abs(location.getY()-bedFoot.getY())<=.20&&Math.abs(location.getZ()-z)<=.20)return true;
    }
    return false;
  }
  private void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
  private static double horizontalDistance(Location a,Location b){double dx=a.getX()-b.getX(),dz=a.getZ()-b.getZ();return Math.sqrt(dx*dx+dz*dz);}
  private static boolean sameBlock(Location a,Location b){return a!=null&&b!=null&&a.getWorld()==b.getWorld()&&a.getBlockX()==b.getBlockX()&&a.getBlockY()==b.getBlockY()&&a.getBlockZ()==b.getBlockZ();}
  private static String blockLoc(Location l){return l==null?"null":l.getWorld().getName()+":"+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ();}
  private static String loc(Location l){return l==null?"null":l.getWorld().getName()+":"+String.format(Locale.ROOT,"%.2f,%.2f,%.2f",l.getX(),l.getY(),l.getZ());}
  private static String fmt(double d){return String.format(Locale.ROOT,"%.3f",d);}
  private static int count(org.bukkit.inventory.Inventory inv,Material material){int n=0;for(var item:inv.getContents())if(item!=null&&item.getType()==material)n+=item.getAmount();return n;}
  @SuppressWarnings("unchecked") private static java.util.Map<String,Integer> items(Object t){try{return (java.util.Map<String,Integer>)invokeNoArg(t,"cargoItems");}catch(Exception e){throw new IllegalStateException(e);}}
  private static Object primitiveDefault(Class<?> type){if(!type.isPrimitive()||type==void.class)return null;if(type==boolean.class)return false;if(type==char.class)return '\0';if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0f;if(type==double.class)return 0d;return null;}
  private World fakeWorld(String name){
    return (World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->{
      if(m.getName().equals("equals"))return p==a[0];
      if(m.getName().equals("hashCode"))return System.identityHashCode(p);
      if(m.getName().equals("toString"))return name;
      wrongWorldAccesses++;
      throw new IllegalStateException("unexpected wrong-world API access: "+m.getName());
    });
  }
  private static Object getField(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
  private static Object getFieldUnchecked(Object target,String name){try{return getField(target,name);}catch(Exception e){throw new IllegalStateException(e);}}
  private static void setField(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
  private static void setFieldUnchecked(Object target,String name,Object value){try{setField(target,name,value);}catch(Exception e){throw new IllegalStateException(e);}}
  private static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
  private static Object invokeNoArg(Object target,String name)throws Exception{return invoke(target,name,new Class<?>[0]);}
  private static Object invokeNoArgUnchecked(Object target,String name){try{return target==null?null:invokeNoArg(target,name);}catch(Exception e){throw new IllegalStateException(e);}}
  private static boolean bool(Object target,String name){
    try{
      Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return (boolean)field.get(target);
    }catch(NoSuchFieldException noField){
      try{
        Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);return (boolean)method.invoke(target);
      }catch(Exception noGetter){
        noGetter.addSuppressed(noField);
        throw new IllegalStateException("no boolean field or getter "+name+" on "+target.getClass().getName(),noGetter);
      }
    }catch(Exception error){throw new IllegalStateException("could not read boolean "+name+" from "+target.getClass().getName(),error);}
  }
  private static int integer(Object target,String name){return (int)getFieldUnchecked(target,name);}
  private static String string(Object target,String name){return (String)getFieldUnchecked(target,name);}
  private String status(Object target){return String.valueOf(invokeNoArgUnchecked(target,"status"));}
  private String phase(){return String.valueOf(invokeNoArgUnchecked(adapter,"phase"));}
  private void log(String message){getLogger().info(message);}
}
