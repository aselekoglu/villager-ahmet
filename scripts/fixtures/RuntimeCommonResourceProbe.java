package dev.ahmetprobe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Disposable Paper 1.21.6 runtime verification probe for P0.5 common-resource acceptance.
 * Exercises /ahmet items, all 11 source block drops (+ COARSE_DIRT negative control),
 * incremental scan budgeting/invalidation, and end-to-end bounded collection + Citizens NPC
 * walking + virtual cargo + chest delivery across all 7 common-resource groups.
 */
public final class RuntimeCommonResourceProbe extends JavaPlugin {
  private record JobSpec(String group, String preference, String outputMaterial, List<BlockPlacement> placements) {}
  private record BlockPlacement(String material, int dx, int dz) {}

  private Plugin ahmetPlugin;
  private Object woodTask;
  private Object agentController;
  private NPC npc;
  private World world;
  private Location centerLoc;
  private Location chestLoc;
  private final List<JobSpec> jobs = List.of(
      new JobSpec("dirt", "item:DIRT", "DIRT", List.of(
          new BlockPlacement("DIRT", 4, 0),
          new BlockPlacement("GRASS_BLOCK", -4, 0))),
      new JobSpec("sand", "item:SAND", "SAND", List.of(
          new BlockPlacement("SAND", 4, 2))),
      new JobSpec("red_sand", "item:RED_SAND", "RED_SAND", List.of(
          new BlockPlacement("RED_SAND", -4, 2))),
      new JobSpec("cobblestone", "item:COBBLESTONE", "COBBLESTONE", List.of(
          new BlockPlacement("STONE", 0, 4))),
      new JobSpec("coal", "item:COAL", "COAL", List.of(
          new BlockPlacement("COAL_ORE", 4, 0),
          new BlockPlacement("DEEPSLATE_COAL_ORE", -4, 0))),
      new JobSpec("raw_iron", "item:RAW_IRON", "RAW_IRON", List.of(
          new BlockPlacement("IRON_ORE", 4, 0),
          new BlockPlacement("DEEPSLATE_IRON_ORE", -4, 0))),
      new JobSpec("raw_gold", "item:RAW_GOLD", "RAW_GOLD", List.of(
          new BlockPlacement("GOLD_ORE", 4, 0),
          new BlockPlacement("DEEPSLATE_GOLD_ORE", -4, 0))));

  private int currentJobIndex = -1;
  private int jobTicks = 0;
  private int maxCargoObserved = 0;
  private String maxCargoItemsSnapshot = "{}";
  private Location lastNpcLoc;
  private double totalDistanceWalked = 0.0;
  private double maxTickStepDistance = 0.0;
  private boolean teleportDetected = false;
  private boolean optedIn;

  @Override
  public void onEnable() {
    optedIn = Boolean.getBoolean("ahmet.disposableProbe");
    if (!optedIn) {
      getLogger().warning("AHMET_PROBE_DISABLED set -Dahmet.disposableProbe=true only on a disposable server; fixture will not mutate the world");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    Bukkit.getScheduler().runTaskLater(this, this::startVerification, 40L);
  }

  private void startVerification() {
    try {
      ahmetPlugin = Bukkit.getPluginManager().getPlugin("VillagerAhmet");
      if (ahmetPlugin == null) {
        failDisposableProbe(new IllegalStateException("VillagerAhmet plugin not found"));
        return;
      }
      woodTask = getField(ahmetPlugin, "task");
      agentController = getField(ahmetPlugin, "controller");
      world = Bukkit.getWorld("world");
      if (world == null) {
        failDisposableProbe(new IllegalStateException("world not found"));
        return;
      }

      prepareArena();
      runItemsCommandChecks();
      runDropAndExpectedOutputChecks();
      runIncrementalScanProbe(() -> {
        try {
          setupNpcAndChest();
          startNextJob();
          Bukkit.getScheduler().runTaskTimer(this, this::tickJobMonitor, 1L, 1L);
        } catch (Exception e) {
          failDisposableProbe(e);
        }
      });
    } catch (Exception e) {
      failDisposableProbe(e);
    }
  }

  private void prepareArena() {
    for (int cx = -2; cx <= 2; cx++) {
      for (int cz = -2; cz <= 2; cz++) {
        world.getChunkAt(cx, cz).load(true);
        world.getChunkAt(cx, cz).setForceLoaded(true);
      }
    }
    for (int x = -26; x <= 26; x++) {
      for (int z = -26; z <= 26; z++) {
        for (int y = 110; y <= 135; y++) {
          Block b = world.getBlockAt(x, y, z);
          if (!b.getType().isAir()) {
            b.setType(Material.AIR, false);
          }
        }
      }
    }
    for (int x = -9; x <= 9; x++) {
      for (int z = -9; z <= 9; z++) {
        world.getBlockAt(x, 119, z).setType(Material.SMOOTH_STONE, false);
      }
    }
    centerLoc = new Location(world, 0.5, 120.0, 0.5);
    chestLoc = new Location(world, 0, 120, -4);
  }

  private void runItemsCommandChecks() {
    String[] queries = {
        "dirt", "coarse_dirt", "sand", "red_sand", "cobblestone",
        "coal", "raw_iron", "raw_gold", "oak_log", "diamond"
    };
    for (String q : queries) {
      getLogger().info("AHMET_ITEMS_CMD_DISPATCH query=" + q);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ahmet items " + q);
    }
  }

  private void runDropAndExpectedOutputChecks() throws Exception {
    String[][] cases = {
        {"DIRT", "", "item:DIRT", "DIRT"},
        {"GRASS_BLOCK", "", "item:DIRT", "DIRT"},
        {"COARSE_DIRT", "", "item:DIRT", "null"},
        {"SAND", "", "item:SAND", "SAND"},
        {"RED_SAND", "", "item:RED_SAND", "RED_SAND"},
        {"STONE", "WOODEN_PICKAXE", "item:COBBLESTONE", "COBBLESTONE"},
        {"COAL_ORE", "WOODEN_PICKAXE", "item:COAL", "COAL"},
        {"DEEPSLATE_COAL_ORE", "WOODEN_PICKAXE", "item:COAL", "COAL"},
        {"IRON_ORE", "STONE_PICKAXE", "item:RAW_IRON", "RAW_IRON"},
        {"DEEPSLATE_IRON_ORE", "STONE_PICKAXE", "item:RAW_IRON", "RAW_IRON"},
        {"GOLD_ORE", "IRON_PICKAXE", "item:RAW_GOLD", "RAW_GOLD"},
        {"DEEPSLATE_GOLD_ORE", "IRON_PICKAXE", "item:RAW_GOLD", "RAW_GOLD"}
    };
    Method expectedOutputMethod = ahmetPlugin.getClass().getDeclaredMethod("expectedOutput", Block.class, String.class);
    expectedOutputMethod.setAccessible(true);

    int x = -6;
    for (String[] c : cases) {
      Material source = Material.valueOf(c[0]);
      Block block = world.getBlockAt(x++, 120, 8);
      block.setType(source, false);
      List<ItemStack> drops = c[1].isEmpty()
          ? List.copyOf(block.getDrops())
          : List.copyOf(block.getDrops(new ItemStack(Material.valueOf(c[1]))));
      String dropStr = drops.stream()
          .map(i -> i.getType().name() + "x" + i.getAmount())
          .collect(Collectors.joining(","));
      String pluginOut = (String) expectedOutputMethod.invoke(ahmetPlugin, block, c[2]);
      String actualOut = pluginOut == null ? "null" : pluginOut;
      boolean matches = c[3].equals(actualOut);
      getLogger().info("AHMET_DROP_PROBE source=" + source.name()
          + " tool=" + (c[1].isEmpty() ? "none" : c[1])
          + " drops=" + dropStr
          + " preference=" + c[2]
          + " pluginExpectedOutput=" + actualOut
          + " verified=" + matches);
      block.setType(Material.AIR, false);
      if (!matches) {
        throw new IllegalStateException("Drop probe mismatch for " + source.name());
      }
    }
    getLogger().info("AHMET_DROP_PROBE_COMPLETE registeredVariants=11 negativeControls=1");
  }

  @SuppressWarnings("unchecked")
  private void runIncrementalScanProbe(Runnable continuation) throws Exception {
    Method invalidateMethod = ahmetPlugin.getClass().getDeclaredMethod("invalidateTargetScan");
    invalidateMethod.setAccessible(true);
    Method findTargetsMethod = ahmetPlugin.getClass().getDeclaredMethod("findTargets", Location.class, String.class);
    findTargetsMethod.setAccessible(true);

    Block probeBlock = world.getBlockAt(5, 120, 0);
    probeBlock.setType(Material.IRON_ORE, false);
    invalidateMethod.invoke(ahmetPlugin);

    final int[] invocations = {0};
    final int[] pendingTicks = {0};
    final long[] totalNs = {0L};
    final long[] maxNs = {0L};
    final long[] previousTick = {-1L};
    final long[] firstTick = {-1L};
    final long[] lastTick = {-1L};
    final List<Block>[] result = new List[] {null};
    Bukkit.getScheduler().runTaskTimer(this, task -> {
      try {
        long tick = Bukkit.getCurrentTick();
        if (previousTick[0] >= 0 && tick - previousTick[0] != 1) {
          throw new IllegalStateException("Scanner invocation tick delta was " + (tick - previousTick[0]));
        }
        previousTick[0] = tick;
        if (firstTick[0] < 0) firstTick[0] = tick;
        lastTick[0] = tick;
        long t0 = System.nanoTime();
        result[0] = (List<Block>) findTargetsMethod.invoke(ahmetPlugin, centerLoc, "item:RAW_IRON");
        long dt = System.nanoTime() - t0;
        totalNs[0] += dt;
        maxNs[0] = Math.max(maxNs[0], dt);
        invocations[0]++;
        if (result[0] == null) pendingTicks[0]++;
        if (result[0] == null && invocations[0] < 200) return;
        task.cancel();
        int foundBefore = result[0] == null ? -1 : result[0].size();
        long scanLastTick = lastTick[0];
        Bukkit.getScheduler().runTaskLater(this, () -> {
          try {
            long validationTick = Bukkit.getCurrentTick();
            long validationTickDelta = validationTick - scanLastTick;
            long invalidationStart = System.nanoTime();
            probeBlock.setType(Material.AIR, false);
            List<Block> afterMutation = (List<Block>) findTargetsMethod.invoke(ahmetPlugin, centerLoc, "item:RAW_IRON");
            int foundAfter = afterMutation == null ? -1 : afterMutation.size();
            long invalidationUs = (System.nanoTime() - invalidationStart) / 1000;
            invalidateMethod.invoke(ahmetPlugin);
            getLogger().info("AHMET_SCAN_PROBE radius=24 scanInvocations=" + invocations[0]
                + " validationInvocations=1 firstTick=" + firstTick[0] + " scanLastTick=" + scanLastTick
                + " validationTick=" + validationTick + " scanTickSpan=" + (scanLastTick - firstTick[0] + 1)
                + " validationTickDelta=" + validationTickDelta
                + " pendingTicks=" + pendingTicks[0]
                + " avgInvocationUs=" + (totalNs[0] / invocations[0] / 1000)
                + " maxInvocationUs=" + (maxNs[0] / 1000)
                + " invalidationUs=" + invalidationUs
                + " foundBeforeMutation=" + foundBefore + " foundAfterMutation=" + foundAfter);
            if (foundBefore != 1 || foundAfter != 0 || pendingTicks[0] < 70
                || invocations[0] != scanLastTick - firstTick[0] + 1
                || validationTickDelta != 1) {
              throw new IllegalStateException("Incremental scan probe failed: before=" + foundBefore
                  + " after=" + foundAfter + " scanInvocations=" + invocations[0]
                  + " validationTickDelta=" + validationTickDelta);
            }
            continuation.run();
          } catch (Exception e) {
            failDisposableProbe(e);
          }
        }, 1L);
      } catch (Exception e) {
        task.cancel();
        failDisposableProbe(e);
      }
    }, 1L, 1L);
  }

  private void failDisposableProbe(Exception e) {
    getLogger().severe("AHMET_PROBE_ERROR " + e.getClass().getName() + ": " + e.getMessage());
    e.printStackTrace();
    if (optedIn) Bukkit.shutdown();
  }

  private void setupNpcAndChest() throws Exception {
    ahmetPlugin.getConfig().set("options.search-radius", 8);
    Block chestBlock = chestLoc.getBlock();
    chestBlock.setType(Material.CHEST, false);

    npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "Ahmet");
    if (!npc.spawn(centerLoc)) {
      throw new IllegalStateException("Failed to spawn Citizens VILLAGER NPC Ahmet");
    }
    setField(ahmetPlugin, "npc", npc);
    setField(ahmetPlugin, "chestLocation", chestBlock.getLocation());
    setField(ahmetPlugin, "home", centerLoc.clone());
    setField(ahmetPlugin, "protectedHome", null);
    setField(ahmetPlugin, "protectHomeRadius", 0);

    Object savedRef = getField(ahmetPlugin, "savedNpcReference");
    Method createdMethod = savedRef.getClass().getDeclaredMethod("created", int.class);
    createdMethod.setAccessible(true);
    createdMethod.invoke(savedRef, npc.getId());

    invokeNoArg(ahmetPlugin, "persist");
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ahmet status");
  }

  private void startNextJob() throws Exception {
    currentJobIndex++;
    if (currentJobIndex >= jobs.size()) {
      finishAllJobs();
      return;
    }
    JobSpec spec = jobs.get(currentJobIndex);
    if (npc.getNavigator().isNavigating()) {
      npc.getNavigator().cancelNavigation();
    }
    invokeNoArg(ahmetPlugin, "clearNavigation");
    invokeNoArg(ahmetPlugin, "invalidateTargetScan");
    setField(ahmetPlugin, "returningHome", false);
    setField(ahmetPlugin, "treeTarget", null);
    setField(ahmetPlugin, "treeRetryAfterMs", 0L);
    setField(ahmetPlugin, "waitDecisions", 0);
    setField(ahmetPlugin, "routeClears", 0);
    invokeNoArg(getField(ahmetPlugin, "failedTargets"), "clear");
    invokeNoArg(getField(ahmetPlugin, "decisionRetry"), "clear");
    invokeNoArg(getField(ahmetPlugin, "partialDeliveryPolicy"), "reset");

    List<String> placedSummary = new ArrayList<>();
    for (BlockPlacement p : spec.placements()) {
      Block b = world.getBlockAt(p.dx(), 120, p.dz());
      b.setType(Material.valueOf(p.material()), false);
      placedSummary.add(p.material() + "@(" + p.dx() + ",120," + p.dz() + ")");
    }

    int count = spec.placements().size();
    Method startMethod = woodTask.getClass().getDeclaredMethod("start", UUID.class, int.class, String.class);
    startMethod.setAccessible(true);
    startMethod.invoke(woodTask, UUID.fromString("00000000-0000-0000-0000-000000000001"), count, spec.preference());

    Method nextGenMethod = agentController.getClass().getDeclaredMethod("nextGeneration");
    nextGenMethod.setAccessible(true);
    nextGenMethod.invoke(agentController);
    invokeNoArg(ahmetPlugin, "persist");

    jobTicks = 0;
    maxCargoObserved = 0;
    maxCargoItemsSnapshot = "{}";
    lastNpcLoc = npc.getEntity().getLocation().clone();
    totalDistanceWalked = 0.0;
    maxTickStepDistance = 0.0;
    teleportDetected = false;

    getLogger().info("AHMET_COLLECT_START group=" + spec.group()
        + " preference=" + spec.preference()
        + " requested=" + count
        + " placements=" + String.join(";", placedSummary));
  }

  @SuppressWarnings("unchecked")
  private void tickJobMonitor() {
    try {
      if (currentJobIndex < 0 || currentJobIndex >= jobs.size()) return;
      JobSpec spec = jobs.get(currentJobIndex);
      jobTicks++;

      Location currentLoc = npc.getEntity().getLocation();
      if (lastNpcLoc != null) {
        double stepDist = currentLoc.distance(lastNpcLoc);
        totalDistanceWalked += stepDist;
        if (stepDist > maxTickStepDistance) {
          maxTickStepDistance = stepDist;
        }
        if (stepDist > 1.5) {
          teleportDetected = true;
        }
      }
      lastNpcLoc = currentLoc.clone();

      int cargo = (int) getField(woodTask, "cargo");
      if (cargo > maxCargoObserved) {
        maxCargoObserved = cargo;
        Method cargoItemsMethod = woodTask.getClass().getDeclaredMethod("cargoItems");
        cargoItemsMethod.setAccessible(true);
        Map<String, Integer> items = (Map<String, Integer>) cargoItemsMethod.invoke(woodTask);
        maxCargoItemsSnapshot = items.toString();
      }

      if (jobTicks % 100 == 0) {
        Location treeTarget = (Location) getField(ahmetPlugin, "treeTarget");
        Location navigationGoal = (Location) getField(ahmetPlugin, "navigationGoal");
        Object decisionRetry = getField(ahmetPlugin, "decisionRetry");
        Method shouldWait = decisionRetry.getClass().getDeclaredMethod("shouldWait", long.class);
        shouldWait.setAccessible(true);
        boolean retryPending = (boolean) shouldWait.invoke(decisionRetry, System.currentTimeMillis());
        getLogger().info("AHMET_JOB_DIAGNOSTIC group=" + spec.group()
            + " tick=" + jobTicks
            + " npc=" + locationText(currentLoc)
            + " treeTarget=" + locationText(treeTarget)
            + " navigationGoal=" + locationText(navigationGoal)
            + " navigating=" + npc.getNavigator().isNavigating()
            + " deciding=" + getField(ahmetPlugin, "deciding")
            + " decisionRetryPending=" + retryPending
            + " requested=" + getField(woodTask, "requested")
            + " collected=" + getField(woodTask, "collected")
            + " deposited=" + getField(woodTask, "deposited")
            + " cargo=" + cargo);
      }

      boolean active = (boolean) getField(woodTask, "active");
      if (!active) {
        int collected = (int) getField(woodTask, "collected");
        int deposited = (int) getField(woodTask, "deposited");
        Chest chest = (Chest) chestLoc.getBlock().getState();
        int chestItemCount = countMaterial(chest.getInventory(), Material.valueOf(spec.outputMaterial()));
        boolean allAir = spec.placements().stream()
            .allMatch(p -> world.getBlockAt(p.dx(), 120, p.dz()).getType() == Material.AIR);

        getLogger().info("AHMET_COLLECT_PROBE group=" + spec.group()
            + " preference=" + spec.preference()
            + " output=" + spec.outputMaterial()
            + " requested=" + spec.placements().size()
            + " collected=" + collected
            + " maxCargo=" + maxCargoObserved
            + " cargoSnapshot=" + maxCargoItemsSnapshot
            + " deposited=" + deposited
            + " finalCargo=" + cargo
            + " chestCount=" + chestItemCount
            + " sourceBlocksRemoved=" + allAir
            + " pathWalked=" + String.format(Locale.ROOT, "%.2f", totalDistanceWalked)
            + " maxTickStep=" + String.format(Locale.ROOT, "%.3f", maxTickStepDistance)
            + " teleportDetected=" + teleportDetected
            + " ticks=" + jobTicks);

        if (collected != spec.placements().size()
            || deposited != spec.placements().size()
            || maxCargoObserved != spec.placements().size()
            || cargo != 0
            || chestItemCount != spec.placements().size()
            || !allAir
            || totalDistanceWalked < 3.0
            || teleportDetected) {
          throw new IllegalStateException("Collection verification failed for group " + spec.group());
        }
        startNextJob();
        return;
      }

      if (jobTicks > 1200) {
        throw new IllegalStateException("Timeout waiting for collection job " + spec.group()
            + " after ticks=" + jobTicks + " (1200-tick timeout; diagnostics emitted every 100 ticks)"
            + " at npc=" + locationText(currentLoc));
      }
    } catch (Exception e) {
      failDisposableProbe(e);
    }
  }

  private void finishAllJobs() {
    Chest chest = (Chest) chestLoc.getBlock().getState();
    Map<String, Integer> summary = new LinkedHashMap<>();
    int totalItems = 0;
    for (ItemStack stack : chest.getInventory().getContents()) {
      if (stack != null && !stack.getType().isAir()) {
        summary.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
        totalItems += stack.getAmount();
      }
    }
    getLogger().info("AHMET_CHEST_SUMMARY totalItems=" + totalItems + " contents=" + summary);
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ahmet status");
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ahmet cargo");
    getLogger().info("AHMET_RUNTIME_PROBE_ALL_PASSED groups=7 variants=11 totalItemsDeposited=" + totalItems);
    if (optedIn) Bukkit.shutdown();
  }

  private static int countMaterial(Inventory inv, Material m) {
    int sum = 0;
    for (ItemStack item : inv.getContents()) {
      if (item != null && item.getType() == m) {
        sum += item.getAmount();
      }
    }
    return sum;
  }

  private static String locationText(Location location) {
    if (location == null) return "null";
    return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", location.getX(), location.getY(), location.getZ());
  }

  private static Object getField(Object target, String name) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static void invokeNoArg(Object target, String name) throws Exception {
    Method m = target.getClass().getDeclaredMethod(name);
    m.setAccessible(true);
    m.invoke(target);
  }
}
