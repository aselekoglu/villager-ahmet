package dev.ahmetprobe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import dev.ahmet.GroundedPerception;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

/** Opt-in, read-only-perception acceptance probe for a fresh disposable Paper server. */
public final class RuntimePerceptionProbe extends JavaPlugin {
  private static final int BRIDGE_PORT = 3199;
  private static final int ARENA_CHUNK_X = 64;
  private static final int ARENA_CHUNK_Z = 0;
  private static final UUID SYNTHETIC_PLAYER_ID = UUID.fromString("a3d1c8e6-0f5b-4a21-9e37-61b2048f7c10");
  private static final String SYNTHETIC_PLAYER_NAME = "SyntheticPerceptionProxy";
  private static final String PRIVATE_META_MARKER = "PERCEPTION_PRIVATE_META_MUST_NOT_ESCAPE";
  private static final Gson GSON = new Gson();

  private final AtomicInteger arenaChunkLoadEvents = new AtomicInteger();
  private final AtomicInteger perceptionCaptureCalls = new AtomicInteger();
  private final List<String> arenaChunkLoadDiagnostics = Collections.synchronizedList(new ArrayList<>());
  private final List<String> arenaChunkLoadCoordinateEvents = Collections.synchronizedList(new ArrayList<>());
  private final AtomicInteger npcTeleports = new AtomicInteger();
  private final AtomicReference<UUID> npcEntityId = new AtomicReference<>();
  private final AtomicReference<String> chatBody = new AtomicReference<>();
  private final AtomicReference<String> decisionBody = new AtomicReference<>();
  private final AtomicReference<CountDownLatch> heldChatSeen = new AtomicReference<>();
  private final AtomicReference<CountDownLatch> heldChatRelease = new AtomicReference<>();
  private final AtomicBoolean chatResponseSent = new AtomicBoolean();
  private final AtomicBoolean decisionResponseSent = new AtomicBoolean();
  private final AtomicInteger chatRequests = new AtomicInteger();
  private final AtomicInteger decisionRequests = new AtomicInteger();
  private final ConcurrentMap<Long, AtomicInteger> decisionsByGeneration = new ConcurrentHashMap<>();
  private final AtomicInteger eventRequests = new AtomicInteger();

  private boolean optedIn;
  private boolean setupOnly;
  private volatile boolean advanceStopped;
  private int setupDeferredLoads;
  private int setupSettleEventBaseline;
  private int setupSettleDiagnosticBaseline;
  private Plugin ahmet;
  private NPC npc;
  private NPC unspawnedNpc;
  private World arena;
  private Location npcLocation;
  private Location boundChest;
  private Location farChest;
  private Location unloadedEdgeChest;
  private Block dirtSource;
  private Block visibleMarker;
  private Block hiddenDiamond;
  private Block opaqueWall;
  private Inventory doubleChestInventory;
  private String inventoryBefore;
  private Map<String, String> blocksBefore;
  private HttpServer bridge;
  private ExecutorService bridgeExecutor;
  private int stage;
  private long stageStartTick;
  private long settleUntilTick;
  private long positiveGeneration;
  private long activeGeneration;
  private long protectedJobGeneration;
  private int staleDecisionBaseline;
  private String protectedTaskId;
  private boolean protectedReturningHome;
  private Path probeDataFolder;
  private final AtomicReference<String> fixtureWriteError = new AtomicReference<>();
  private long staleGeneration;
  private Player chatActor;
  private Listener probeListener;
  private String failureReason;

  @Override public void onEnable() {
    optedIn = Boolean.getBoolean("ahmet.perceptionProbe");
    setupOnly = Boolean.getBoolean("ahmet.perceptionProbe.setupOnly");
    if (!optedIn) {
      getLogger().warning("AHMET_PERCEPTION_PROBE_DISABLED; fixture has made no world changes");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    Bukkit.getScheduler().runTaskLater(this, this::begin, 40L);
  }

  @Override public void onDisable() {
    releaseAllWaiters();
    if (bridge != null) bridge.stop(0);
    if (bridgeExecutor != null) bridgeExecutor.shutdownNow();
  }

  private void begin() {
    if (!Bukkit.isPrimaryThread()) { fail("setup was not on Paper main thread"); return; }
    try {
      ahmet = Bukkit.getPluginManager().getPlugin("VillagerAhmet");
      if (ahmet == null || !ahmet.isEnabled()) throw new IllegalStateException("VillagerAhmet is unavailable");
      configureInMemory();
      prepareOneChunkArena();
      registerReadOnlyEventCounters();
      createVillagersAndChest();
      if (setupOnly) {
        beginSetupOnlyControl();
        return;
      }
      runSnapshotAssertions();
      saveUnchangedBaseline();
      beginNormalSetupSettle();
    } catch (Throwable error) {
      fail("setup: " + concise(error));
    }
  }

  private void configureInMemory() throws Exception {
    var config = ((JavaPlugin) ahmet).getConfig();
    config.set("brain.url", "http://127.0.0.1:" + BRIDGE_PORT);
    probeDataFolder = getDataFolder().toPath();
    Files.createDirectories(probeDataFolder);
    config.set("brain.timeout-seconds", 15);
    config.set("options.schedule-enabled", false);
    config.set("options.search-radius", 24);
    config.set("options.protect-home-radius", 0);
    config.set("perception.enabled", true);
    config.set("perception.include-bound-chest", false);
    config.set("perception.include-players", false);
    setField(ahmet, "home", null);
    setField(ahmet, "protectedHome", null);
    setField(ahmet, "scheduleBed", null);
    setField(ahmet, "protectHomeRadius", 0);
    startLoopbackBridge();
  }

  private void startLoopbackBridge() throws IOException {
    bridge = HttpServer.create(new InetSocketAddress("127.0.0.1", BRIDGE_PORT), 0);
    bridgeExecutor = Executors.newFixedThreadPool(2, runnable -> {
      Thread thread = new Thread(runnable, "ahmet-perception-probe-http");
      thread.setDaemon(true);
      return thread;
    });
    bridge.setExecutor(bridgeExecutor);
    bridge.createContext("/api/chat", exchange -> handleChat(exchange));
    bridge.createContext("/api/decide", exchange -> handleDecision(exchange));
    bridge.createContext("/api/event", exchange -> {
      eventRequests.incrementAndGet();
      respond(exchange, 200, "{\"ok\":true}");
    });
    bridge.start();
  }

  private void handleChat(HttpExchange exchange) throws IOException {
    String body = readBody(exchange);
    int requestNumber = chatRequests.incrementAndGet();
    if (requestNumber <= 2) {
      chatBody.set(body);
      writeSyntheticBody("chat-request.json", body);
    }
    CountDownLatch seen = heldChatSeen.get();
    CountDownLatch release = heldChatRelease.get();
    boolean hold = requestNumber == 2 && seen != null && release != null;
    if (hold) {
      seen.countDown();
      await(release, 8, TimeUnit.SECONDS);
    }
    respond(exchange, 200, taskReply(requestNumber == 2));
    chatResponseSent.set(true);
  }

  private void handleDecision(HttpExchange exchange) throws IOException {
    String body = readBody(exchange);
    decisionRequests.incrementAndGet();
    JsonObject request = JsonParser.parseString(body).getAsJsonObject();
    long generation = request.getAsJsonObject("observation").get("generation").getAsLong();
    decisionsByGeneration.computeIfAbsent(generation, ignored -> new AtomicInteger()).incrementAndGet();
    if (decisionBody.compareAndSet(null, body)) writeSyntheticBody("decision-request.json", body);
    respond(exchange, 200, "{\"id\":\"wait\",\"source\":\"jev\",\"model\":\"disposable-mock\"}");
    decisionResponseSent.set(true);
  }

  private static String readBody(HttpExchange exchange) throws IOException {
    byte[] body = exchange.getRequestBody().readNBytes(32_769);
    if (body.length > 32_768) throw new IOException("synthetic bridge request exceeded 32 KiB");
    return new String(body, StandardCharsets.UTF_8);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static void await(CountDownLatch latch, long timeout, TimeUnit unit) {
    try { latch.await(timeout, unit); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
  }

  private String taskReply(boolean staleSideEffectFlags) {
    return "{\"reply\":\"Synthetic bounded task reply\",\"taskSource\":\"heuristic\",\"task\":{\"type\":\"collect_item\",\"itemId\":\"minecraft:dirt\",\"amount\":1},\"cancel\":"
        + staleSideEffectFlags + ",\"goHome\":" + staleSideEffectFlags + "}";
  }

  private void prepareOneChunkArena() {
    arena = Bukkit.getWorld("world");
    if (arena == null || arena.getEnvironment() != World.Environment.NORMAL) throw new IllegalStateException("fresh normal world not found");
    int chunkX = ARENA_CHUNK_X, chunkZ = ARENA_CHUNK_Z;
    if (arena.isChunkLoaded(chunkX - 1, chunkZ) || arena.isChunkLoaded(chunkX + 1, chunkZ)
        || arena.isChunkLoaded(chunkX, chunkZ - 1) || arena.isChunkLoaded(chunkX, chunkZ + 1)) {
      throw new IllegalStateException("arena is not isolated to its single prepared chunk");
    }
    arena.getChunkAt(chunkX, chunkZ).load(true);
    int startX = chunkX << 4, startZ = chunkZ << 4;
    int groundY = arena.getHighestBlockYAt(startX + 1, startZ + 8) - 1;
    if (groundY < arena.getMinHeight() + 2 || groundY + 3 >= arena.getMaxHeight()) throw new IllegalStateException("invalid flat arena height");
    for (int x = startX; x < startX + 16; x++) {
      for (int z = startZ; z < startZ + 16; z++) {
        arena.getBlockAt(x, groundY, z).setType(Material.SMOOTH_STONE, false);
        arena.getBlockAt(x, groundY + 1, z).setType(Material.AIR, false);
        arena.getBlockAt(x, groundY + 2, z).setType(Material.AIR, false);
        arena.getBlockAt(x, groundY + 3, z).setType(Material.AIR, false);
      }
    }
    npcLocation = new Location(arena, startX + 1.5, groundY + 1.0, startZ + 8.5);
    visibleMarker = arena.getBlockAt(startX + 3, groundY, startZ + 8);
    visibleMarker.setType(Material.GOLD_BLOCK, false);
    opaqueWall = arena.getBlockAt(startX + 4, groundY + 1, startZ + 8);
    opaqueWall.setType(Material.STONE, false);
    arena.getBlockAt(startX + 4, groundY + 2, startZ + 8).setType(Material.STONE, false);
    hiddenDiamond = arena.getBlockAt(startX + 5, groundY + 1, startZ + 8);
    hiddenDiamond.setType(Material.DIAMOND_BLOCK, false);
    dirtSource = arena.getBlockAt(startX + 2, groundY + 1, startZ + 12);
    dirtSource.setType(Material.DIRT, false);
    boundChest = new Location(arena, startX + 2, groundY + 1, startZ + 11);
    Block firstChestHalf = arena.getBlockAt(startX + 2, groundY + 1, startZ + 11);
    Block secondChestHalf = arena.getBlockAt(startX + 3, groundY + 1, startZ + 11);
    firstChestHalf.setType(Material.CHEST, false);
    secondChestHalf.setType(Material.CHEST, false);
    setChestHalf(firstChestHalf, org.bukkit.block.data.type.Chest.Type.LEFT);
    setChestHalf(secondChestHalf, org.bukkit.block.data.type.Chest.Type.RIGHT);
    farChest = new Location(arena, startX + 10, groundY + 1, startZ + 8);
    unloadedEdgeChest = new Location(arena, startX + 1, groundY + 1, startZ + 15);
    arena.getBlockAt(unloadedEdgeChest.getBlockX(), unloadedEdgeChest.getBlockY(), unloadedEdgeChest.getBlockZ()).setType(Material.CHEST, false);
    if (arena.isChunkLoaded(chunkX - 1, chunkZ) || arena.isChunkLoaded(chunkX, chunkZ + 1)) {
      throw new IllegalStateException("setup unexpectedly loaded a neighboring chunk");
    }
  }

  private void registerReadOnlyEventCounters() {
    probeListener = new Listener() {
      @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getWorld().equals(arena)) {
          arenaChunkLoadEvents.incrementAndGet();
          if (Bukkit.isPrimaryThread() && arenaChunkLoadDiagnostics.size() < 32) {
            var chunk = event.getChunk();
            arenaChunkLoadCoordinateEvents.add(chunk.getX() + "," + chunk.getZ());
            StringBuilder source = new StringBuilder();
            StackTraceElement[] frames = Thread.currentThread().getStackTrace();
            for (StackTraceElement frame : frames) {
              if (source.length() >= 700) break;
              if (source.length() > 0) source.append(" <- ");
              source.append(frame.getClassName()).append('#').append(frame.getMethodName());
            }
            arenaChunkLoadDiagnostics.add("stage=" + stage + " tick=" + Bukkit.getCurrentTick()
                + " chunk=" + chunk.getX() + "," + chunk.getZ() + " source=" + source);
          }
        }
      }
      @EventHandler public void onTeleport(EntityTeleportEvent event) {
        UUID expected = npcEntityId.get();
        if (expected != null && expected.equals(event.getEntity().getUniqueId())) npcTeleports.incrementAndGet();
      }
    };
    Bukkit.getPluginManager().registerEvents(probeListener, this);
  }

  private void beginSetupOnlyControl() {
    if (!Bukkit.isPrimaryThread()) { fail("setup-only control was not on Paper main thread"); return; }
    try {
      Object task = getField(ahmet, "task");
      require(!boolField(task, "active"), "setup-only control requires an inactive production task");
      stage = 90;
      long startedAtTick = Bukkit.getCurrentTick();
      Bukkit.getScheduler().runTaskLater(this, () -> completeSetupOnlyControl(startedAtTick), 10L);
    } catch (Throwable error) {
      fail("setup-only control: " + concise(error));
    }
  }

  private void beginNormalSetupSettle() {
    if (!Bukkit.isPrimaryThread()) { fail("normal setup settle was not on Paper main thread"); return; }
    require(arenaChunkLoadEvents.get() == 0 && arenaChunkLoadDiagnostics.isEmpty()
        && arenaChunkLoadCoordinateEvents.isEmpty(),
        "arena chunk load occurred before the setup-settle measurement window");
    setupSettleEventBaseline = arenaChunkLoadEvents.get();
    setupSettleDiagnosticBaseline = arenaChunkLoadDiagnostics.size();
    stage = 91;
    stageStartTick = Bukkit.getCurrentTick();
    Bukkit.getScheduler().runTaskLater(this, this::completeNormalSetupSettle, 10L);
  }

  private void completeNormalSetupSettle() {
    if (!Bukkit.isPrimaryThread()) { fail("normal setup settle completion was not on Paper main thread"); return; }
    try {
      long settleTicks = Bukkit.getCurrentTick() - stageStartTick;
      require(settleTicks >= 10, "setup-load settle ended before ten server ticks");
      Object task = getField(ahmet, "task");
      require(!boolField(task, "active"), "production task became active during setup-load settle");
      require(chatRequests.get() == 0 && decisionRequests.get() == 0 && eventRequests.get() == 0,
          "bridge request occurred during setup-load settle");
      require(blockFingerprint().equals(blocksBefore) && inventoryState(doubleChestInventory).equals(inventoryBefore),
          "synthetic blocks or chest changed during setup-load settle");
      Set<String> expected = Set.of("63,0", "63,-1", "64,-1", "65,0", "65,-1", "64,1", "63,1", "65,1");
      List<String> coordinateEvents = List.copyOf(arenaChunkLoadCoordinateEvents);
      List<String> windowCoordinates = coordinateEvents.subList(
          Math.min(setupSettleDiagnosticBaseline, coordinateEvents.size()), coordinateEvents.size());
      List<String> diagnostics = List.copyOf(arenaChunkLoadDiagnostics);
      List<String> windowDiagnostics = diagnostics.subList(
          Math.min(setupSettleDiagnosticBaseline, diagnostics.size()), diagnostics.size());
      Set<String> observed = Set.copyOf(windowCoordinates);
      int windowLoads = arenaChunkLoadEvents.get() - setupSettleEventBaseline;
      require(windowLoads == expected.size() && windowCoordinates.size() == expected.size()
          && windowDiagnostics.size() == expected.size()
          && observed.equals(expected), "post-setup chunk-load window differed from the r6 eight-chunk control: count="
          + windowLoads + " coordinates=" + observed);
      setupDeferredLoads = windowLoads;
      getLogger().info("AHMET_PERCEPTION_SETUP_SETTLE_PASS waitTicks=" + settleTicks
          + " preSettleChunkLoads=0 deferredSetupLoads=" + setupDeferredLoads + " chunks=" + expected.size()
          + " coordinates=" + String.join("|", observed.stream().sorted().toList())
          + " taskActive=false bridgeRequests=0 fixtureBlocksUnchanged=true fixtureChestUnchanged=true");
      arenaChunkLoadEvents.set(0);
      synchronized (arenaChunkLoadDiagnostics) { arenaChunkLoadDiagnostics.clear(); }
      synchronized (arenaChunkLoadCoordinateEvents) { arenaChunkLoadCoordinateEvents.clear(); }
      startPositiveChatControl();
      Bukkit.getScheduler().runTaskTimer(this, this::advance, 1L, 1L);
      stageStartTick = Bukkit.getCurrentTick();
    } catch (Throwable error) {
      fail("setup-load settle: " + concise(error));
    }
  }

  private void completeSetupOnlyControl(long startedAtTick) {
    if (!Bukkit.isPrimaryThread()) { fail("setup-only completion was not on Paper main thread"); return; }
    try {
      Object task = getField(ahmet, "task");
      require(!boolField(task, "active"), "setup-only control unexpectedly started a production task");
      require(perceptionCaptureCalls.get() == 0, "setup-only fixture unexpectedly invoked its capture wrapper");
      require(chatRequests.get() == 0 && decisionRequests.get() == 0 && eventRequests.get() == 0,
          "setup-only fixture unexpectedly contacted the synthetic bridge");
      getLogger().info("AHMET_PERCEPTION_SETUP_CONTROL_DONE waitTicks="
          + (Bukkit.getCurrentTick() - startedAtTick) + " totalArenaChunkLoads=" + arenaChunkLoadEvents.get()
          + " retained=" + arenaChunkLoadDiagnostics.size() + " cap=32 perceptionCalls=" + perceptionCaptureCalls.get()
          + " chatRequests=" + chatRequests.get() + " decisionRequests=" + decisionRequests.get()
          + " eventRequests=" + eventRequests.get() + " taskActive=false npcTeleports=" + npcTeleports.get());
      synchronized (arenaChunkLoadDiagnostics) {
        for (String diagnostic : arenaChunkLoadDiagnostics) {
          getLogger().info("AHMET_PERCEPTION_SETUP_CONTROL_CHUNK_EVENT " + diagnostic);
        }
      }
      shutdownCleanly();
    } catch (Throwable error) {
      fail("setup-only completion: " + concise(error));
    }
  }

  private void createVillagersAndChest() throws Exception {
    npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "M2 Perception Probe");
    if (!npc.spawn(npcLocation)) throw new IllegalStateException("Citizens VILLAGER did not spawn in test arena");
    Entity entity = npc.getEntity();
    if (entity.getType() != EntityType.VILLAGER) throw new IllegalStateException("fixture NPC is not an actual VILLAGER");
    npcEntityId.set(entity.getUniqueId());
    setField(ahmet, "npc", npc);
    setField(ahmet, "chestLocation", boundChest.clone());
    Block firstHalf = boundChest.getBlock();
    Block secondHalf = arena.getBlockAt(boundChest.getBlockX() + 1, boundChest.getBlockY(), boundChest.getBlockZ());
    if (!(firstHalf.getState() instanceof Chest firstChest) || !(secondHalf.getState() instanceof Chest secondChest)) {
      throw new IllegalStateException("double chest halves are missing");
    }
    var firstData = (org.bukkit.block.data.type.Chest) firstHalf.getBlockData();
    var secondData = (org.bukkit.block.data.type.Chest) secondHalf.getBlockData();
    require(firstData.getFacing() == BlockFace.NORTH && secondData.getFacing() == BlockFace.NORTH,
        "double chest halves must share north-facing orientation");
    require(firstData.getType() == org.bukkit.block.data.type.Chest.Type.LEFT
        && secondData.getType() == org.bukkit.block.data.type.Chest.Type.RIGHT,
        "double chest halves must have compatible LEFT/RIGHT block data");
    Inventory full = firstChest.getInventory();
    Inventory secondView = secondChest.getInventory();
    if (full.getSize() != 54 || secondView.getSize() != 54) {
      throw new IllegalStateException("paired chest halves do not expose a 54-slot inventory from both blocks");
    }
    // Put controlled values in indices 27+ so the probe catches a first-half-only adapter.
    full.setItem(28, new ItemStack(Material.DIRT, 7));
    ItemStack namedStone = new ItemStack(Material.STONE, 3);
    ItemMeta meta = namedStone.getItemMeta();
    meta.displayName(Component.text(PRIVATE_META_MARKER));
    meta.lore(List.of(Component.text("synthetic lore that must not leave Bukkit")));
    namedStone.setItemMeta(meta);
    full.setItem(34, namedStone);
    doubleChestInventory = firstChest.getInventory();
    if (doubleChestInventory.getSize() != 54 || doubleChestInventory.getItem(28) == null
        || doubleChestInventory.getItem(34) == null) throw new IllegalStateException("second-half inventory setup failed");
    if (arena.isChunkLoaded(ARENA_CHUNK_X, ARENA_CHUNK_Z + 1)) throw new IllegalStateException("stale chest partner chunk must remain unloaded");
  }

  private void runSnapshotAssertions() throws Exception {
    int loadedEventsBefore = arenaChunkLoadEvents.get();
    String blockStateBefore = arenaBlockState();
    inventoryBefore = inventoryState(doubleChestInventory);

    unspawnedNpc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "Unspawned Perception Probe");
    AtomicInteger unspawnedCalls = new AtomicInteger();
    var unavailable = capture(trackedNpc(unspawnedNpc, unspawnedCalls), null, boundChest, true, true, 20L);
    require(!unavailable.available(), "unspawned NPC observation must be unavailable");
    require(unspawnedCalls.get() == 1, "unspawned guard must stop after checking NPC spawn state");
    unspawnedNpc.destroy();

    var config = ((JavaPlugin) ahmet).getConfig();
    require(!config.getBoolean("perception.include-bound-chest", false)
        && !config.getBoolean("perception.include-players", false), "inventory/player perception must default off");
    var defaultOff = capture(npc, null, boundChest,
        config.getBoolean("perception.include-bound-chest", false),
        config.getBoolean("perception.include-players", false), 21L);
    require(defaultOff.available(), "block-only snapshot must be available");
    require(!defaultOff.inventory().enabled() && !defaultOff.inventory().available() && defaultOff.inventory().items().isEmpty(), "inventory default-off section must be unavailable and empty");
    require(!defaultOff.players().enabled() && !defaultOff.players().available() && defaultOff.players().bands().isEmpty(), "players default-off section must be unavailable and empty");

    config.set("perception.include-bound-chest", true);
    Player deniedViewer = permissionProxy(false);
    var denied = capture(npc, deniedViewer, boundChest,
        config.getBoolean("perception.include-bound-chest", false), false, 22L);
    require(denied.inventory().enabled() && !denied.inventory().available() && denied.inventory().items().isEmpty(), "non-admin inventory must fail closed");

    Player adminViewer = permissionProxy(true);
    var populated = capture(npc, adminViewer, boundChest,
        config.getBoolean("perception.include-bound-chest", false), false, 23L);
    require(populated.inventory().enabled() && populated.inventory().available(), "authorized bound chest must be available");
    require(populated.inventory().items().stream().anyMatch(row -> row.material().equals("DIRT") && row.count() == 7), "second-half dirt count must be visible");
    require(populated.inventory().items().stream().anyMatch(row -> row.material().equals("STONE") && row.count() == 3), "second-half stone count must be visible");
    String populatedJson = populated.toJson();
    assertPrivatePerceptionSchema(populatedJson);

    var noViewer = capture(npc, null, boundChest, true, false, 24L);
    require(!noViewer.inventory().available() && noViewer.inventory().items().isEmpty(), "offline viewer must not expose inventory");

    World otherWorld = syntheticOtherWorld();
    var wrongWorld = capture(npc, adminViewer, new Location(otherWorld, boundChest.getX(), boundChest.getY(), boundChest.getZ()), true, false, 25L);
    require(!wrongWorld.inventory().available() && wrongWorld.inventory().items().isEmpty(), "wrong-world chest must fail closed");
    var far = capture(npc, adminViewer, farChest, true, false, 26L);
    require(!far.inventory().available() && far.inventory().items().isEmpty(), "far chest must fail closed");
    var unloaded = capture(npc, adminViewer, unloadedEdgeChest, true, false, 27L);
    require(!unloaded.inventory().available() && unloaded.inventory().items().isEmpty(), "chest with unloaded partner chunk must fail closed");
    require(!arena.isChunkLoaded(ARENA_CHUNK_X, ARENA_CHUNK_Z + 1), "capture must not load the unloaded partner chunk");

    config.set("perception.include-players", true);
    int onlinePlayers = Bukkit.getOnlinePlayers().size();
    require(onlinePlayers == 0, "player visibility runtime evidence requires no connected human players");
    var noPlayers = capture(npc, adminViewer, null, false,
        config.getBoolean("perception.include-players", false), 28L);
    require(noPlayers.players().enabled() && noPlayers.players().available() && noPlayers.players().bands().isEmpty(), "opted-in empty online-player set must be available-empty");

    var regular = capture(npc, adminViewer, boundChest, false, false, 29L);
    int goldCount = typeCount(regular, "GOLD_BLOCK");
    require(regular.available(), "actual spawned Citizens villager snapshot must be available");
    require(regular.blocks().sampledCells() <= 64 && regular.blocks().types().size() <= 8, "block sample/type budgets exceeded");
    require(goldCount > 0, "visible marker was not observed");
    require(typeCount(regular, "DIAMOND_BLOCK") == 0, "diamond marker behind opaque wall was observed");
    require(typeCount(regular, "STONE") > 0, "opaque wall should be visible");
    require(!arena.isChunkLoaded(ARENA_CHUNK_X - 1, ARENA_CHUNK_Z), "west sample boundary chunk was unexpectedly loaded");
    require(arenaChunkLoadEvents.get() == loadedEventsBefore, "capture emitted a chunk-load event");
    require(arenaBlockState().equals(blockStateBefore), "read-only snapshots changed seeded arena blocks");
    require(inventoryState(doubleChestInventory).equals(inventoryBefore), "read-only snapshots changed seeded chest inventory");

    AtomicInteger offThreadNpcCalls = new AtomicInteger();
    NPC offThreadNpc = trackedNpc(npc, offThreadNpcCalls);
    FutureTask<GroundedPerception.Snapshot> offThread = new FutureTask<>(() -> capture(offThreadNpc, null, boundChest, false, false, 30L));
    Thread worker = new Thread(offThread, "ahmet-perception-off-thread-check");
    worker.setDaemon(true);
    worker.start();
    var offThreadResult = offThread.get(2, TimeUnit.SECONDS);
    require(!offThreadResult.available(), "off-thread capture must be unavailable");
    require(offThreadNpcCalls.get() == 0, "off-thread guard touched the Citizens NPC");
    require(arenaChunkLoadEvents.get() == loadedEventsBefore, "off-thread guard emitted a chunk-load event");
    require(arenaBlockState().equals(blockStateBefore) && inventoryState(doubleChestInventory).equals(inventoryBefore), "off-thread guard changed world or inventory state");

    int captureChunkLoads = arenaChunkLoadEvents.get() - loadedEventsBefore;
    getLogger().info("AHMET_PERCEPTION_SNAPSHOT_PASS npc=Citizens_VILLAGER samples=" + regular.blocks().sampledCells()
        + " blockTypes=" + regular.blocks().types().size() + " visibleGold=" + goldCount
        + " hiddenDiamond=0 captureChunkLoads=" + captureChunkLoads
        + " defaultInventory=off defaultPlayers=off adminInventorySlots=" + doubleChestInventory.getSize()
        + " adminInventoryItems=" + populated.inventory().items().size() + " anonymousPlayers=" + noPlayers.players().bands().size());
  }

  private void saveUnchangedBaseline() {
    blocksBefore = blockFingerprint();
    inventoryBefore = inventoryState(doubleChestInventory);
  }

  private void startPositiveChatControl() throws Exception {
    chatActor = syntheticPlayer(true);
    chatBody.set(null);
    chatResponseSent.set(false);
    chatRequests.set(0);
    positiveGeneration = numberField(getField(ahmet, "controller"), "jobGeneration");
    invoke(ahmet, "interact", new Class<?>[]{Player.class, String.class}, chatActor, "synthetic current-generation request");
    stage = 1;
    stageStartTick = Bukkit.getCurrentTick();
  }

  private void advance() {
    if (advanceStopped) return;
    if (!Bukkit.isPrimaryThread()) { fail("stage monitor left Paper main thread"); return; }
    try {
      long tick = Bukkit.getCurrentTick();
      if (tick - stageStartTick > 240) throw new IllegalStateException("stage timed out: " + stage);
      switch (stage) {
        case 1 -> waitForPositiveTask(tick);
        case 2 -> waitForDecision(tick);
        case 3 -> waitForDecisionSettle(tick);
        case 4 -> waitForStaleChatRequest(tick);
        case 5 -> waitForStaleChatSettle(tick);
        default -> throw new IllegalStateException("invalid stage " + stage);
      }
    } catch (Throwable error) {
      fail("stage " + stage + ": " + concise(error));
    }
  }

  private void waitForPositiveTask(long tick) throws Exception {
    if (boolField(getField(ahmet, "task"), "active")) {
      require(numberField(getField(ahmet, "task"), "requested") == 1, "positive current-generation chat did not start the bounded one-item task");
      JsonObject body = parseBody(chatBody.get());
      JsonObject context = body.getAsJsonObject("context");
      require(context.get("generation").getAsLong() == positiveGeneration, "chat context generation differs from request generation");
      require(fixtureWriteError.get() == null, "synthetic request body write failed: " + fixtureWriteError.get());
      require(context.getAsJsonObject("perception").get("available").getAsBoolean(), "chat context did not forward available perception");
      require(context.getAsJsonObject("perception").get("generation").getAsLong() == positiveGeneration, "chat snapshot generation mismatch");
      activeGeneration = numberField(getField(ahmet, "controller"), "jobGeneration");
      require(activeGeneration > positiveGeneration, "accepted chat task did not advance generation");
      stage = 2;
      stageStartTick = tick;
      return;
    }
  }

  private void waitForDecision(long tick) throws Exception {
    if (decisionBody.get() != null && !boolField(ahmet, "deciding")) {
      JsonObject body = parseBody(decisionBody.get());
      JsonObject observation = body.getAsJsonObject("observation");
      require(observation.get("generation").getAsLong() == activeGeneration, "JEV observation generation mismatch");
      require(fixtureWriteError.get() == null, "synthetic request body write failed: " + fixtureWriteError.get());
      JsonObject perception = observation.getAsJsonObject("perception");
      require(perception.get("available").getAsBoolean(), "JEV did not receive available block perception");
      require(perception.get("generation").getAsLong() == activeGeneration, "JEV snapshot generation mismatch");
      writeSyntheticBody("decision-request.json", decisionBody.get());
      require(decisionResponseSent.get(), "mock wait response was not sent");
      require(boolField(getField(ahmet, "task"), "active"), "positive-control task ended before JEV request capture");
      require(numberField(ahmet, "waitDecisions") == 1, "production callback did not process the current-generation JEV wait choice");
      require(getField(ahmet, "treeTarget") == null, "JEV wait selected a target");
      require(numberField(getField(ahmet, "task"), "cargo") == 0, "positive-control JEV wait changed cargo");
      invoke(ahmet, "cancel", new Class<?>[0]);
      require(!boolField(getField(ahmet, "task"), "active"), "production cancellation did not stop positive-control task");
      require(numberField(getField(ahmet, "task"), "cargo") == 0, "positive-control path changed cargo before cancellation");
      require(blockFingerprint().equals(blocksBefore) && inventoryState(doubleChestInventory).equals(inventoryBefore), "positive-control request mutated seeded state before cancellation");
      stage = 3;
      stageStartTick = tick;
      settleUntilTick = tick + 10;
    }
  }

  private void waitForDecisionSettle(long tick) throws Exception {
    if (decisionResponseSent.get() && tick >= settleUntilTick) {
      require(!boolField(getField(ahmet, "task"), "active"), "stale JEV completion restarted a task");
      require(numberField(getField(ahmet, "task"), "cargo") == 0, "stale JEV completion changed cargo");
      require(blockFingerprint().equals(blocksBefore) && inventoryState(doubleChestInventory).equals(inventoryBefore), "JEV wait/cancel changed block or chest state");
      startStaleChatNegative(tick);
    }
  }

  private void startStaleChatNegative(long tick) throws Exception {
    CountDownLatch seen = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    heldChatSeen.set(seen);
    heldChatRelease.set(release);
    chatResponseSent.set(false);
    chatBody.set(null);
    staleGeneration = numberField(getField(ahmet, "controller"), "jobGeneration");
    invoke(ahmet, "interact", new Class<?>[]{Player.class, String.class}, chatActor, "synthetic delayed stale-generation request");
    stage = 4;
    stageStartTick = tick;
  }

  private void waitForStaleChatRequest(long tick) throws Exception {
    CountDownLatch seen = heldChatSeen.get();
    if (seen != null && seen.getCount() == 0) {
      JsonObject body = parseBody(chatBody.get());
      JsonObject context = body.getAsJsonObject("context");
      require(context.get("generation").getAsLong() == staleGeneration, "delayed chat request did not carry the captured generation");
      require(context.getAsJsonObject("perception").get("generation").getAsLong() == staleGeneration, "delayed chat perception generation mismatch");
      invoke(ahmet, "cancel", new Class<?>[0]);
      long afterCancel = numberField(getField(ahmet, "controller"), "jobGeneration");
      require(afterCancel > staleGeneration, "production cancellation did not advance the captured generation");
      var command = ((JavaPlugin) ahmet).getCommand("ahmet");
      require(command != null && command.execute(chatActor, "ahmet", new String[]{"collect", "dirt", "1"}), "production command did not admit the protected one-item task");
      Object currentTask = getField(ahmet, "task");
      require(boolField(currentTask, "active"), "new bounded task was not active before stale response release");
      protectedTaskId = (String) getField(currentTask, "taskId");
      protectedJobGeneration = numberField(getField(ahmet, "controller"), "jobGeneration");
      require(protectedJobGeneration > afterCancel, "new task did not advance generation after delayed chat capture");
      // cancel() legitimately sets this true when cargo is zero; normalize only this disposable fixture state.
      setField(ahmet, "returningHome", false);
      protectedReturningHome = boolField(ahmet, "returningHome");
      require(!protectedReturningHome, "fixture did not establish the required false returningHome baseline");
      staleDecisionBaseline = decisionCount(staleGeneration);
      heldChatRelease.get().countDown();
      stage = 5;
      stageStartTick = tick;
      settleUntilTick = tick + 20;
      return;
    }
  }

  private void waitForStaleChatSettle(long tick) throws Exception {
    if (chatResponseSent.get() && tick >= settleUntilTick) {
      if (boolField(ahmet, "deciding")) return;
      Object task = getField(ahmet, "task");
      require(boolField(task, "active"), "delayed stale chat response cancelled the newer bounded task");
      require(((String) getField(task, "taskId")).equals(protectedTaskId), "delayed stale chat response replaced the newer bounded task");
      require(numberField(task, "cargo") == 0 && numberField(task, "collected") == 0, "delayed stale chat response changed task counters");
      require(numberField(getField(ahmet, "controller"), "jobGeneration") == protectedJobGeneration, "stale chat response changed the newer task generation");
      boolean returningHomeFinal = boolField(ahmet, "returningHome");
      require(returningHomeFinal == protectedReturningHome && !returningHomeFinal,
          "delayed stale chat response changed the false returningHome baseline");
      require(blockFingerprint().equals(blocksBefore) && inventoryState(doubleChestInventory).equals(inventoryBefore), "delayed stale chat changed source block or chest");
      require(decisionCount(activeGeneration) == 1, "positive-control JEV request was not isolated to its generation");
      require(decisionCount(staleGeneration) == staleDecisionBaseline, "stale chat generation emitted a JEV request");
      for (long generation : decisionsByGeneration.keySet()) {
        require(generation == activeGeneration || generation == protectedJobGeneration,
            "JEV request was not attributable to the positive or newer task generation: " + generation);
      }
      require(npcTeleports.get() == 0, "normal NPC movement teleported during read-only probe");
      require(arenaChunkLoadEvents.get() == 0, "runtime capture loaded a chunk after setup");
      advanceStopped = true;
      getLogger().info("AHMET_PERCEPTION_STALE_CHAT_PASS responseSent=true mainThreadSettleTicks=20 newerTaskPreserved=true collected=0 cargo=0 generationPreserved=true returningHomeBaseline="
          + protectedReturningHome + " returningHomeFinal=" + returningHomeFinal + " sourceBlockUnchanged=true chestUnchanged=true");
      invoke(ahmet, "cancel", new Class<?>[0]);
      require(!boolField(task, "active") && numberField(task, "cargo") == 0, "probe cleanup failed to cancel synthetic task before shutdown");
      require(blockFingerprint().equals(blocksBefore) && inventoryState(doubleChestInventory).equals(inventoryBefore), "synthetic cleanup changed source block or chest");
      getLogger().info("AHMET_PERCEPTION_PROBE_PASS chatRequests=" + chatRequests.get() + " jevRequests=" + decisionRequests.get()
          + " decisionCountsByGeneration=" + decisionsByGeneration.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue().get()).toList()
          + " eventRequests=" + eventRequests.get() + " npcTeleports=" + npcTeleports.get()
          + " setupDeferredLoads=" + setupDeferredLoads + " postObservationArenaChunkLoads=" + arenaChunkLoadEvents.get()
          + " humanPlayers=0 providers=none");
      shutdownCleanly();
    }
  }

  private World syntheticOtherWorld() {
    return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> {
      if (method.getName().equals("equals")) return proxy == args[0];
      if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
      if (method.getName().equals("getName")) return "SyntheticOtherWorld";
      return defaultValue(method.getReturnType());
    });
  }

  private static void setChestHalf(Block block, org.bukkit.block.data.type.Chest.Type type) {
    if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData)) {
      throw new IllegalStateException("chest block did not expose chest block data");
    }
    chestData.setFacing(BlockFace.NORTH);
    chestData.setType(type);
    block.setBlockData(chestData, false);
  }

  private static NPC trackedNpc(NPC delegate, AtomicInteger calls) {
    return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class}, (proxy, method, args) -> {
      if (method.getName().equals("equals")) return proxy == args[0];
      if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
      if (method.getName().equals("toString")) return "TrackedSyntheticCitizensNpc";
      calls.incrementAndGet();
      try { return method.invoke(delegate, args); }
      catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
    });
  }

  private GroundedPerception.Snapshot capture(NPC actor, Player viewer, Location chest,
      boolean inventory, boolean players, long generation) throws Exception {
    perceptionCaptureCalls.incrementAndGet();
    Class<?> adapter = Class.forName("dev.ahmet.BukkitPerception");
    Method method = adapter.getDeclaredMethod("capture", NPC.class, Player.class, Location.class,
        boolean.class, boolean.class, long.class);
    method.setAccessible(true);
    return (GroundedPerception.Snapshot) method.invoke(null, actor, viewer, chest, inventory, players, generation);
  }

  private Player permissionProxy(boolean admin) {
    return proxyPlayer((proxy, method, args) -> switch (method.getName()) {
      case "isOnline" -> true;
      case "getWorld" -> arena;
      case "hasPermission" -> admin && "ahmet.admin".equals(args[0]);
      case "canSee" -> false;
      case "getUniqueId" -> SYNTHETIC_PLAYER_ID;
      case "getName" -> SYNTHETIC_PLAYER_NAME;
      default -> defaultValue(method.getReturnType());
    });
  }

  private Player syntheticPlayer(boolean admin) {
    return proxyPlayer((proxy, method, args) -> switch (method.getName()) {
      case "isOnline" -> true;
      case "getWorld" -> arena;
      case "getLocation" -> npc.getEntity().getLocation();
      case "getUniqueId" -> SYNTHETIC_PLAYER_ID;
      case "getName" -> SYNTHETIC_PLAYER_NAME;
      case "hasPermission" -> admin && "ahmet.admin".equals(args[0]);
      case "canSee" -> false;
      default -> defaultValue(method.getReturnType());
    });
  }

  private static Player proxyPlayer(InvocationHandler handler) {
    return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
      if (method.getName().equals("equals")) return proxy == args[0];
      if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
      if (method.getName().equals("toString")) return "SyntheticPerceptionProxy";
      return handler.invoke(proxy, method, args);
    });
  }



  private Map<String, String> blockFingerprint() {
    return Map.of("visible", visibleMarker.getType().name(), "wall", opaqueWall.getType().name(),
        "hidden", hiddenDiamond.getType().name(), "dirt", dirtSource.getType().name(),
        "chestA", boundChest.getBlock().getType().name(),
        "chestB", arena.getBlockAt(boundChest.getBlockX() + 1, boundChest.getBlockY(), boundChest.getBlockZ()).getType().name());
  }

  private String arenaBlockState() {
    return GSON.toJson(blockFingerprint());
  }

  private String inventoryState(Inventory inventory) {
    List<String> slots = new ArrayList<>(inventory.getSize());
    for (ItemStack stack : inventory.getContents()) {
      if (stack == null) { slots.add("AIR:0"); continue; }
      ItemMeta meta = stack.getItemMeta();
      String display = meta == null || meta.displayName() == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
      slots.add(stack.getType().name() + ":" + stack.getAmount() + ":" + display);
    }
    return GSON.toJson(slots);
  }

  private static int typeCount(GroundedPerception.Snapshot snapshot, String material) {
    return snapshot.blocks().types().stream().filter(row -> row.material().equals(material)).mapToInt(GroundedPerception.TypeCount::count).sum();
  }

  private int decisionCount(long generation) {
    AtomicInteger count = decisionsByGeneration.get(generation);
    return count == null ? 0 : count.get();
  }

  private void assertPrivatePerceptionSchema(String json) {
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertExactKeys(root, Set.of("version", "available", "capturedAt", "generation", "phase", "blocks", "inventory", "players"), "snapshot");
    require(root.get("version").getAsInt() == 1 && root.get("available").getAsBoolean(), "snapshot header is invalid");
    require(root.get("capturedAt").getAsLong() >= 0 && root.get("generation").getAsLong() >= 0, "snapshot time/generation is invalid");
    require(Set.of("WAKE", "WORK", "WANDER", "SLEEP", "UNKNOWN").contains(root.get("phase").getAsString()), "snapshot phase is invalid");

    JsonObject blocks = root.getAsJsonObject("blocks");
    assertExactKeys(blocks, Set.of("sampledCells", "types"), "blocks");
    int sampledCells = blocks.get("sampledCells").getAsInt();
    require(sampledCells >= 0 && sampledCells <= 64, "snapshot sample count escaped its bound");
    assertTypeRows(blocks.getAsJsonArray("types"), 8, 64, "block materials");

    JsonObject inventory = root.getAsJsonObject("inventory");
    assertExactKeys(inventory, Set.of("enabled", "available", "items"), "inventory");
    assertTypeRows(inventory.getAsJsonArray("items"), 8, 3456, "inventory materials");
    JsonObject players = root.getAsJsonObject("players");
    assertExactKeys(players, Set.of("enabled", "available", "bands"), "players");
    JsonArray bands = players.getAsJsonArray("bands");
    require(bands.size() <= 8, "player bands escaped their bound");
    for (JsonElement band : bands) require(Set.of("near", "nearby").contains(band.getAsString()), "player band is invalid");

    // Exact key sets above reject world, identity, position, coordinate, metadata and NBT fields.
    // Values are also constrained to enums/counts and must not contain this fixture's private markers.
    assertSafeStringValues(root);
  }

  private static void assertTypeRows(JsonArray rows, int cap, int maxCount, String section) {
    require(rows.size() <= cap, section + " escaped the type cap");
    for (JsonElement element : rows) {
      JsonObject row = element.getAsJsonObject();
      assertExactKeys(row, Set.of("material", "count"), section + " row");
      require(row.get("material").getAsString().matches("[A-Z][A-Z0-9_]{0,63}"), section + " material is not an enum-shaped value");
      int count = row.get("count").getAsInt();
      require(count >= 0 && count <= maxCount, section + " count is outside its bound");
    }
  }

  private void assertSafeStringValues(JsonElement value) {
    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
      String text = value.getAsString();
      require(!text.equals(SYNTHETIC_PLAYER_NAME) && !text.contains(SYNTHETIC_PLAYER_ID.toString()), "snapshot exposed synthetic player identity");
      require(!text.equals(arena.getName()) && !text.equals("SyntheticOtherWorld"), "snapshot exposed a world name");
      require(!text.contains(PRIVATE_META_MARKER) && !text.contains("synthetic lore"), "snapshot exposed item metadata");
      require(!text.equals(npcLocation.toString()) && !text.equals(boundChest.toString()), "snapshot exposed a coordinate location string");
      return;
    }
    if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) assertSafeStringValues(child);
    if (value.isJsonObject()) for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) assertSafeStringValues(entry.getValue());
  }

  private static void assertExactKeys(JsonObject object, Set<String> allowed, String context) {
    require(object.keySet().equals(allowed), context + " has unexpected or missing schema keys: " + object.keySet());
  }

  private static JsonObject parseBody(String body) {
    if (body == null) throw new IllegalStateException("expected synthetic request body was not captured");
    return JsonParser.parseString(body).getAsJsonObject();
  }

  private void writeSyntheticBody(String filename, String body) {
    try {
      Path path = probeDataFolder.resolve(filename);
      Files.createDirectories(path.getParent());
      Files.writeString(path, body, StandardCharsets.UTF_8);
    } catch (IOException error) {
      fixtureWriteError.compareAndSet(null, filename + ": " + concise(error));
    }
  }

  private void releaseAllWaiters() {
    CountDownLatch chat = heldChatRelease.get();
    if (chat != null) chat.countDown();
  }

  private void shutdownCleanly() {
    advanceStopped = true;
    releaseAllWaiters();
    if (bridge != null) bridge.stop(0);
    if (bridgeExecutor != null) bridgeExecutor.shutdownNow();
    Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 1L);
  }

  private void fail(String message) {
    if (failureReason != null) return;
    advanceStopped = true;
    failureReason = message;
    getLogger().severe("AHMET_PERCEPTION_PROBE_ERROR " + message);
    getLogger().severe("AHMET_PERCEPTION_CHUNK_DIAGNOSTIC total=" + arenaChunkLoadEvents.get()
        + " retained=" + arenaChunkLoadDiagnostics.size() + " cap=32");
    synchronized (arenaChunkLoadDiagnostics) {
      for (String diagnostic : arenaChunkLoadDiagnostics) getLogger().severe("AHMET_PERCEPTION_CHUNK_EVENT " + diagnostic);
    }
    releaseAllWaiters();
    if (bridge != null) bridge.stop(0);
    if (bridgeExecutor != null) bridgeExecutor.shutdownNow();
    if (Bukkit.isPrimaryThread()) Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 1L);
    else Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static String concise(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
    return cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
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

  private static Object getField(Object target, String name) throws Exception {
    Class<?> type = target.getClass();
    while (type != null) {
      try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
      catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
    }
    throw new NoSuchFieldException(name);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Class<?> type = target.getClass();
    while (type != null) {
      try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
      catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
    }
    throw new NoSuchFieldException(name);
  }

  private static boolean boolField(Object target, String name) throws Exception { return (boolean) getField(target, name); }
  private static long numberField(Object target, String name) throws Exception { return ((Number) getField(target, name)).longValue(); }

  private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(target, args);
  }
}
