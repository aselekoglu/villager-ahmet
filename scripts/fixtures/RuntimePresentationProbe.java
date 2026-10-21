package dev.ahmetprobe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.citizensnpcs.api.event.NPCTeleportEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Opt-in, disposable Paper/Citizens runtime probe for bounded presentation. */
public final class RuntimePresentationProbe extends JavaPlugin implements Listener {
  private static final int GLOBAL_TICK_BUDGET = 6_000;
  private static final int JOB_TICK_BUDGET = 1_200;
  private static final UUID OPERATOR_ID = UUID.fromString("36efc754-6a02-4ed3-9a42-46e2a8f70001");
  private static final UUID ACTOR_ID = UUID.fromString("36efc754-6a02-4ed3-9a42-46e2a8f70002");
  private static final UUID BYSTANDER_ID = UUID.fromString("36efc754-6a02-4ed3-9a42-46e2a8f70003");
  private static final String PRIVATE_SENTINEL = "M4_PRIVATE_REPLY_SENTINEL";

  private enum Fault { NONE, SWING, SOUND }
  private static final class FixtureAssertion extends RuntimeException {
    private final String reason;
    private final String safeDetail;
    FixtureAssertion(String reason, String safeDetail) {
      super("fixture assertion");
      this.reason = reason;
      this.safeDetail = safeDetail;
    }
    String reason() { return reason; }
    String safeDetail() { return safeDetail; }
  }
  private static final class TaggedSinkFailure extends RuntimeException {
    private final String sink;
    TaggedSinkFailure(String sink) { super("fixture-injected-" + sink); this.sink = sink; }
    String sink() { return sink; }
  }
  private record Wait(String name, int budgetTicks, BooleanSupplier condition, Runnable success, int[] elapsed) { }
  private record Cue(String transaction, int collected, int cargo, int deposited, int chestItems,
                     boolean pendingHarvest, boolean pendingDeposit, long atNanos) { }
  private record Snapshot(String taskId, String owner, long generation, boolean active, int cargo,
                          boolean recoveryBlocked, boolean pendingJournal, boolean navigating,
                          boolean taskRouteOwned, boolean scheduleNavigating, String schedulePhase,
                          String scheduleStatus, int teleportCount, Location navigationGoal) { }

  private Plugin ahmet;
  private Object task;
  private Object controller;
  private Object journal;
  private Object injectedAdapter;
  private Object previousAdapter;
  private Field presentationField;
  private NPC npc;
  private World world;
  private final List<World> retainedWorlds = new ArrayList<>();
  private final List<int[]> ownChunkTickets = new ArrayList<>();
  private Location home;
  private Location chestLocation;
  private Block currentSource;
  private PlayerProxy operator;
  private PlayerProxy actor;
  private PlayerProxy bystander;
  private final List<BukkitTask> tasks = new ArrayList<>();
  private final List<String> diagnostics = new ArrayList<>();
  private final List<Cue> cues = new ArrayList<>();
  private final Map<UUID, List<String>> messages = new HashMap<>();
  private final AtomicInteger playerSourceCalls = new AtomicInteger();
  private final AtomicInteger activeTaskSourceQueries = new AtomicInteger();
  private final AtomicInteger activeLookChunkPreflights = new AtomicInteger();
  private final AtomicInteger chunkSourceCalls = new AtomicInteger();
  private final AtomicInteger losCalls = new AtomicInteger();
  private final AtomicInteger faceCalls = new AtomicInteger();
  private final AtomicInteger successfulFaceDelegations = new AtomicInteger();
  private final AtomicInteger swingCalls = new AtomicInteger();
  private final AtomicInteger soundCalls = new AtomicInteger();
  private final AtomicInteger actualSwings = new AtomicInteger();
  private final AtomicInteger actualSounds = new AtomicInteger();
  private final AtomicInteger injectedSwingFaults = new AtomicInteger();
  private final AtomicInteger injectedSoundFaults = new AtomicInteger();
  private final AtomicInteger chatRequests = new AtomicInteger();
  private final AtomicInteger chatResponsesWritten = new AtomicInteger();
  private final AtomicInteger noCapacityWarnings = new AtomicInteger();
  private final AtomicReference<String> unexpectedCallbackFailure = new AtomicReference<>();
  private boolean optedIn;
  private final boolean negativeSoundInvariant = Boolean.getBoolean("ahmet.presentationProbe.negativeSoundInvariant");
  private final boolean negativeFaceDelegate = Boolean.getBoolean("ahmet.presentationProbe.negativeFaceDelegate");
  private volatile List<Player> sourcePlayers = List.of();
  private volatile boolean failPlayerSource;
  private volatile boolean failLos;
  private volatile boolean failFace;
  private volatile boolean denyLookChunks;
  private volatile CountDownLatch nextChatRelease;

  private HttpServer mockBrain;
  private ExecutorService mockExecutor;
  private String bridgeToken;
  private BukkitTask ticker;
  private Handler warningHandler;
  private Wait wait;
  private String stage = "startup";
  private boolean terminal;
  private int globalTicks;
  private int normalTeleports;
  private boolean countNormalTeleports;
  private Location previousPosition;
  private double totalWalkBlocks;
  private double maxStep;
  private int movedTicks;
  private String lastStationaryWorkStatus;
  private int stableStationaryWorkTicks;
  private int baselineGeneration;
  private int jobStartTick;
  private int startCollected;
  private int startDeposited;
  private int startChestItems;
  private int startDiagnosticCount;
  private int startActualSwings;
  private int startActualSounds;
  private int startInjectedSwingFaults;
  private int startInjectedSoundFaults;
  private int startTeleportCount;
  private String taskId;
  private Fault activeFault;

  @Override public void onEnable() {
    optedIn = Boolean.getBoolean("ahmet.presentationProbe");
    if (!optedIn) {
      getLogger().warning("AHMET_PRESENTATION_PROBE_DISABLED: opt in only on the disposable fixture server");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    if (negativeSoundInvariant && negativeFaceDelegate) {
      getLogger().severe("AHMET_PRESENTATION_PROBE_CONFIG_FAIL controls=mutually-exclusive");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    Bukkit.getPluginManager().registerEvents(this, this);
    ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    tasks.add(ticker);
    tasks.add(Bukkit.getScheduler().runTaskLater(this,
        () -> fail(new IllegalStateException("global 6000-tick budget exhausted")), GLOBAL_TICK_BUDGET));
    tasks.add(Bukkit.getScheduler().runTaskLater(this, this::start, 40L));
  }

  @Override public void onDisable() { cleanup(); }

  @EventHandler public void onNpcTeleport(NPCTeleportEvent event) {
    if (countNormalTeleports && npc != null && event.getNPC().getId() == npc.getId()) normalTeleports++;
  }

  private void start() {
    try {
      ahmet = Bukkit.getPluginManager().getPlugin("VillagerAhmet");
      world = Bukkit.getWorld("world");
      require(ahmet != null && world != null, "required plugin or disposable world missing");
      retainedWorlds.add(world);
      task = field(ahmet, "task"); controller = field(ahmet, "controller"); journal = field(ahmet, "journal");
      warningHandler = new Handler() {
        @Override public void publish(LogRecord record) {
          if (record != null && record.getMessage() != null
              && record.getMessage().contains("Delivery chest has no capacity")) noCapacityWarnings.incrementAndGet();
        }
        @Override public void flush() { }
        @Override public void close() { }
      };
      ahmet.getLogger().addHandler(warningHandler);
      prepareArena();
      configureCore();
      operator = new PlayerProxy(OPERATOR_ID, true, new Location(world, 0.5, 120, 0.5));
      command(operator.player, "ahmet spawn");
      Object savedNpc = field(ahmet, "npc");
      require(savedNpc instanceof NPC, "actual /ahmet spawn did not create Citizens NPC");
      npc = (NPC) savedNpc;
      require(npc.isSpawned() && npc.getEntity().getType() == EntityType.VILLAGER
          && npc.getEntity() instanceof Villager, "expected a real Citizens EntityType.VILLAGER");
      home = npc.getEntity().getLocation().clone();
      set(ahmet, "home", home.clone());
      set(ahmet, "protectedHome", home.clone());
      set(ahmet, "protectHomeRadius", 0);
      set(ahmet, "chestLocation", chestLocation.clone());
      invoke(ahmet, "persist");
      operator.targetBlock = chestLocation.getBlock();
      command(operator.player, "ahmet chest");
      require(chestLocation.getBlock().getState() instanceof Chest, "fixture chest was not bound through command path");
      injectPresentation();
      startMockBrain();
      countNormalTeleports = true;
      previousPosition = npc.getEntity().getLocation().clone();
      requireNoUnexpectedCallbackFailure("setup pass");
      getLogger().info("AHMET_PRESENTATION_STAGE_PASS name=setup npcType=" + npc.getEntity().getType()
          + " setupChunkTickets=" + ownChunkTickets.size() + " sourceMode=injected-proxy-visibility"
          + " actualEffects=delegated teleports=0");
      runIdlePresentationChecks();
    } catch (Throwable error) { fail(error); }
  }

  private void prepareArena() {
    for (int chunkX = -1; chunkX <= 0; chunkX++) for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
      var chunk = world.getChunkAt(chunkX, chunkZ);
      chunk.load(true);
      require(world.addPluginChunkTicket(chunkX, chunkZ, this), "could not acquire fixture chunk ticket");
      ownChunkTickets.add(new int[]{chunkX, chunkZ});
    }
    for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) {
      for (int y = 110; y <= 128; y++) {
        Block block = world.getBlockAt(x, y, z);
        if (!block.getType().isAir()) block.setType(Material.AIR, false);
      }
      world.getBlockAt(x, 119, z).setType(Material.SMOOTH_STONE, false);
    }
    chestLocation = new Location(world, -6, 120, 0);
    chestLocation.getBlock().setType(Material.CHEST, false);
    require(chestLocation.getBlock().getState() instanceof Chest, "could not create disposable chest");
  }

  private void configureCore() throws Exception {
    var config = ahmet.getConfig();
    config.set("options.schedule-enabled", true);
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

  private void injectPresentation() throws Exception {
    presentationField = findField(ahmet, "presentation");
    previousAdapter = presentationField.get(ahmet);
    Class<?> adapterClass = Class.forName("dev.ahmet.BukkitPresentationAdapter");
    Class<?> mainThreadType = Class.forName("dev.ahmet.BukkitPresentationAdapter$MainThread");
    Class<?> playerSourceType = Class.forName("dev.ahmet.BukkitPresentationAdapter$PlayerSource");
    Class<?> effectsType = Class.forName("dev.ahmet.BukkitPresentationAdapter$Effects");
    Object mainThread = Proxy.newProxyInstance(mainThreadType.getClassLoader(), new Class<?>[]{mainThreadType},
        (proxy, method, args) -> method.getName().equals("isPrimaryThread") && Bukkit.isPrimaryThread());
    Object source = Proxy.newProxyInstance(playerSourceType.getClassLoader(), new Class<?>[]{playerSourceType}, this::sourceCall);
    Object effects = Proxy.newProxyInstance(effectsType.getClassLoader(), new Class<?>[]{effectsType}, this::effectCall);
    LongSupplier clock = () -> System.nanoTime() / 1_000_000L;
    Consumer<String> diagnosticSink = diagnostics::add;
    Constructor<?> constructor = adapterClass.getDeclaredConstructor(mainThreadType, playerSourceType,
        effectsType, LongSupplier.class, Consumer.class);
    constructor.setAccessible(true);
    injectedAdapter = constructor.newInstance(mainThread, source, effects, clock, diagnosticSink);
    presentationField.set(ahmet, injectedAdapter);
    require(presentationField.get(ahmet) == injectedAdapter, "could not install tagged fixture adapter");
  }

  private Object sourceCall(Object proxy, Method method, Object[] args) {
    try { return sourceCallSafely(method, args); }
    catch (TaggedSinkFailure expected) { throw expected; }
    catch (RuntimeException unexpected) {
      recordUnexpectedCallbackFailure("source-" + method.getName());
      throw unexpected;
    }
  }

  private Object sourceCallSafely(Method method, Object[] args) {
    return switch (method.getName()) {
      case "players" -> {
        requireCallback(Bukkit.isPrimaryThread(), "player-source-main-thread");
        playerSourceCalls.incrementAndGet();
        if (task != null && (bool(task, "active") || integer(task, "cargo") > 0)) activeTaskSourceQueries.incrementAndGet();
        if (failPlayerSource) throw new TaggedSinkFailure("PLAYER_SOURCE");
        yield sourcePlayers;
      }
      case "chunkLoaded" -> {
        requireCallback(Bukkit.isPrimaryThread(), "chunk-source-main-thread");
        chunkSourceCalls.incrementAndGet();
        if (task != null && (bool(task, "active") || integer(task, "cargo") > 0)) {
          activeLookChunkPreflights.incrementAndGet();
        }
        World sourceWorld = (World) args[0];
        yield !denyLookChunks && sourceWorld.isChunkLoaded((int) args[1], (int) args[2]);
      }
      case "lineOfSight" -> {
        requireCallback(Bukkit.isPrimaryThread(), "los-source-main-thread");
        losCalls.incrementAndGet();
        if (failLos) throw new TaggedSinkFailure("LOS");
        yield true;
      }
      default -> primitiveDefault(method.getReturnType());
    };
  }

  private Object effectCall(Object proxy, Method method, Object[] args) throws Throwable {
    try { return effectCallSafely(method, args); }
    catch (TaggedSinkFailure expected) { throw expected; }
    catch (RuntimeException unexpected) {
      recordUnexpectedCallbackFailure("effect-" + method.getName());
      throw unexpected;
    }
  }

  private Object effectCallSafely(Method method, Object[] args) throws Throwable {
    requireCallback(Bukkit.isPrimaryThread(), "presentation-effect-main-thread");
    return switch (method.getName()) {
      case "face" -> {
        faceCalls.incrementAndGet();
        lastFacedX = ((Location) args[1]).getX();
        if (failFace) throw new TaggedSinkFailure("FACE");
        invokeDelegateOrLatch("face", args);
        successfulFaceDelegations.incrementAndGet();
        yield null;
      }
      case "send" -> {
        invokeDelegateOrLatch("send", args);
        yield null;
      }
      case "swing" -> {
        swingCalls.incrementAndGet();
        Cue cue = observeCommittedCue("swing");
        cues.add(cue);
        if (activeFault == Fault.SWING) {
          injectedSwingFaults.incrementAndGet();
          throw new TaggedSinkFailure("SWING");
        }
        invokeDelegateOrLatch("swing", args);
        actualSwings.incrementAndGet();
        yield null;
      }
      case "workSound" -> {
        soundCalls.incrementAndGet();
        observeCommittedCue("workSound");
        if (negativeSoundInvariant) {
          recordUnexpectedCallbackFailure("sound-invariant");
          throw new IllegalStateException("fixture-negative-sound-invariant");
        }
        if (activeFault == Fault.SOUND) {
          injectedSoundFaults.incrementAndGet();
          throw new TaggedSinkFailure("SOUND");
        }
        invokeDelegateOrLatch("workSound", args);
        actualSounds.incrementAndGet();
        yield null;
      }
      default -> primitiveDefault(method.getReturnType());
    };
  }

  private Object defaultEffects;

  private void invokeDelegateOrLatch(String name, Object[] args) throws Throwable {
    if (name.equals("face") && negativeFaceDelegate) {
      recordUnexpectedCallbackFailure("face-delegate");
      throw new TaggedSinkFailure("FACE_DELEGATE");
    }
    try { invokeDelegate(name, args); }
    catch (Throwable failure) {
      if (failure instanceof Error fatal) throw fatal;
      recordUnexpectedCallbackFailure(name + "-delegate");
      throw new IllegalStateException("fixture unexpected " + name + " delegate failure", failure);
    }
  }

  private void invokeDelegate(String name, Object... args) throws Throwable {
    if (defaultEffects == null) {
      Class<?> type = Class.forName("dev.ahmet.BukkitPresentationAdapter$BukkitEffects");
      Constructor<?> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      defaultEffects = constructor.newInstance();
    }
    Class<?>[] parameters = Arrays.stream(args).map(value -> switch (name) {
      case "face" -> value instanceof NPC ? NPC.class : Location.class;
      case "send" -> value instanceof Player ? Player.class : String.class;
      case "swing", "workSound" -> LivingEntity.class;
      default -> value.getClass();
    }).toArray(Class<?>[]::new);
    Method method = defaultEffects.getClass().getDeclaredMethod(name, parameters);
    method.setAccessible(true);
    try { method.invoke(defaultEffects, args); }
    catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
  }

  private void runIdlePresentationChecks() {
    waitFor("stationary-schedule-work-home", Math.min(2_000, GLOBAL_TICK_BUDGET - globalTicks),
        this::stationaryWorkPremiseStable, this::beginIdlePresentationChecks);
  }

  private boolean stationaryWorkPremiseStable() {
    Object schedule = field(ahmet, "scheduleAdapter");
    boolean eligible = npc != null && npc.isSpawned() && npc.getEntity() instanceof Villager villager
        && !villager.isSleeping() && String.valueOf(invoke(schedule, "phase")).equals("WORK")
        && !invokeBoolean(schedule, "ownsNavigation") && !invokeBoolean(schedule, "ownsSleep")
        && !npc.getNavigator().isNavigating() && !bool(task, "active") && integer(task, "cargo") == 0
        && !bool(ahmet, "recoveryBlocked") && !journalHasOperations() && !bool(ahmet, "returningHome")
        && npc.getEntity().getLocation().distanceSquared(home) <= 2.5 * 2.5;
    if (!eligible) {
      lastStationaryWorkStatus = null;
      stableStationaryWorkTicks = 0;
      return false;
    }
    String status = String.valueOf(invoke(schedule, "status"));
    if (status.equals(lastStationaryWorkStatus)) stableStationaryWorkTicks++;
    else { lastStationaryWorkStatus = status; stableStationaryWorkTicks = 1; }
    return stableStationaryWorkTicks >= 5;
  }

  private void beginIdlePresentationChecks() {
    try {
      require(npc.getEntity() instanceof Villager && !npc.getNavigator().isNavigating()
          && String.valueOf(invoke(field(ahmet, "scheduleAdapter"), "phase")).equals("WORK"),
          "idle presentation premise did not reach stationary schedule WORK");
      Snapshot before = snapshot();
      PlayerProxy invisible = new PlayerProxy(new UUID(0, 701), false, new Location(world, 1.5, 120, 0.5));
      invisible.invisible = true;
      PlayerProxy spectator = new PlayerProxy(new UUID(0, 702), false, new Location(world, 2.0, 120, 0.5));
      spectator.gameMode = GameMode.SPECTATOR;
      PlayerProxy visible = new PlayerProxy(new UUID(0, 703), false, new Location(world, 4.5, 120, 0.5));
      sourcePlayers = List.of(invisible.player, spectator.player, visible.player);
      int previousDelegatedFaces = successfulFaceDelegations.get();
      waitFor("idle-real-citizens-face", 45,
          () -> successfulFaceDelegations.get() > previousDelegatedFaces, () -> {
        try {
          require(lastFacedX == 4.5, "nearest invisible/spectator proxy was faced instead of visible proxy");
          require(invisible.locationReads() == 0 && spectator.locationReads() == 0,
              "native-invisible/spectator proxy location was read before exclusion");
          require(losCalls.get() > 0, "injected proxy LOS evidence was not exercised");
          require(npc.getEntity() instanceof Villager, "presentation changed real Citizens entity type");
          requireNoUnexpectedCallbackFailure("idle look pass");
          requireUnchanged(before, "successful idle look");
          getLogger().info("AHMET_PRESENTATION_STAGE_PASS name=idle-look realNpc=true faceLocationDelegated=true"
              + " nearestInvisibleExcluded=true spectatorExcluded=true los=injected-proxy-only");
          runIdleFaultChecks(invisible, spectator, visible);
        } catch (Throwable error) { fail(error); }
      });
    } catch (Throwable error) { fail(error); }
  }

  private volatile double lastFacedX = Double.NaN;

  private void runIdleFaultChecks(PlayerProxy invisible, PlayerProxy spectator, PlayerProxy visible) {
    sourcePlayers = List.of(visible.player);
    int chunksBefore = chunkSourceCalls.get();
    int losBefore = losCalls.get();
    int loadedChunksBefore = world.getLoadedChunks().length;
    denyLookChunks = true;
    waitFor("idle-unloaded-preflight-fails-closed", 45,
        () -> chunkSourceCalls.get() > chunksBefore, () -> {
          try {
            require(losCalls.get() == losBefore, "LOS ran after injected unloaded-chunk preflight denial");
            require(world.getLoadedChunks().length == loadedChunksBefore,
                "look preflight loaded a chunk during presentation check");
            denyLookChunks = false;
            Snapshot baseline = snapshot();
            runOneLookFault("player-source", () -> failPlayerSource = true,
                () -> failPlayerSource = false, playerSourceCalls, () -> {}, baseline, () ->
                    runOneLookFault("line-of-sight", () -> failLos = true, () -> failLos = false,
                        losCalls, () -> sourcePlayers = List.of(visible.player), baseline, () ->
                            runOneLookFault("face", () -> failFace = true, () -> failFace = false,
                                faceCalls, () -> sourcePlayers = List.of(visible.player), baseline,
                                this::runMockChatChecks)));
          } catch (Throwable error) { fail(error); }
        });
  }

  private void runOneLookFault(String name, Runnable enable, Runnable disable, AtomicInteger counter,
                               Runnable prepare, Snapshot baseline, Runnable next) {
    prepare.run();
    int before = counter.get();
    enable.run();
    waitFor("idle-fault-" + name, 45, () -> counter.get() > before, () -> {
      disable.run();
      waitFor("idle-fault-diagnostic-" + name, 2, () -> diagnostics.stream().anyMatch(value -> value.startsWith("look:")), () -> {
        try {
          requireUnchanged(baseline, "idle " + name + " failure");
          require(!bool(ahmet, "recoveryBlocked"), "idle " + name + " failure changed recovery state");
          next.run();
        } catch (Throwable error) { fail(error); }
      });
    });
  }

  private void runMockChatChecks() {
    try {
      sourcePlayers = List.of();
      actor = new PlayerProxy(ACTOR_ID, false, npc.getEntity().getLocation().clone().add(1.5, 0, 0));
      bystander = new PlayerProxy(BYSTANDER_ID, false, npc.getEntity().getLocation().clone().add(2.0, 0, 0));
      sourcePlayers = List.of(actor.player, bystander.player);
      messages.clear();
      int responseBefore = chatResponsesWritten.get();
      command(actor.player, "ahmet ask test private reply");
      waitFor("private-mock-chat-positive", 120,
          () -> chatResponsesWritten.get() > responseBefore && containsMessage(ACTOR_ID, PRIVATE_SENTINEL), () -> {
            try {
              require(chatRequests.get() > 0, "mock /api/chat did not receive the plugin request");
              require(!containsMessage(BYSTANDER_ID, PRIVATE_SENTINEL), "private model reply reached a bystander");
              requireNoUnexpectedCallbackFailure("private chat pass");
              getLogger().info("AHMET_PRESENTATION_STAGE_PASS name=mock-chat-private-positive actorOnly=true"
                  + " channel=actual-plugin-HTTP-command=synthetic-proxy");
              sourcePlayers = List.of();
              awaitTicks(30, () -> runDelayedFarChatCheck());
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void runDelayedFarChatCheck() {
    try {
      messages.clear();
      actor.location = npc.getEntity().getLocation().clone().add(1, 0, 0);
      CountDownLatch release = new CountDownLatch(1);
      nextChatRelease = release;
      int responsesBefore = chatResponsesWritten.get();
      command(actor.player, "ahmet ask delayed private reply");
      waitFor("delayed-chat-request-received", 80, () -> chatRequests.get() >= 2, () -> {
        int locationReadsBeforeRelease = actor.locationReads();
        actor.location = new Location(world, 30, 120, 0);
        release.countDown();
        waitFor("delayed-chat-response-observed-but-far", 120,
            () -> chatResponsesWritten.get() > responsesBefore && actor.locationReads() > locationReadsBeforeRelease, () -> {
              try {
                require(!containsMessage(ACTOR_ID, PRIVATE_SENTINEL), "far actor received delayed private reply");
                require(!containsMessage(BYSTANDER_ID, PRIVATE_SENTINEL), "bystander received delayed private reply");
                requireNoUnexpectedCallbackFailure("delayed far chat pass");
                getLogger().info("AHMET_PRESENTATION_STAGE_PASS name=mock-chat-delayed-far-negative"
                    + " httpResponseWritten=true callbackProximityRechecked=true actorBeyond18=true bystander=false");
                testSharedSpeechCooldownThenStartJobs();
              } catch (Throwable error) { fail(error); }
            });
      });
    } catch (Throwable error) { fail(error); }
  }

  private void testSharedSpeechCooldownThenStartJobs() {
    try {
      actor.location = npc.getEntity().getLocation().clone().add(1, 0, 0);
      sourcePlayers = List.of(actor.player, bystander.player);
      messages.clear();
      awaitTicks(30, () -> {
        invoke(injectedAdapter, "sayNearby", new Class<?>[]{NPC.class, String.class}, npc, "public cadence sample");
        int actorPublicCount = messageCount(ACTOR_ID);
        int bystanderPublicCount = messageCount(BYSTANDER_ID);
        require(actorPublicCount == 1 && bystanderPublicCount == 1, "expected one bounded public line per proxy recipient");
        invoke(injectedAdapter, "replyToActor", new Class<?>[]{NPC.class, Player.class, String.class},
            npc, actor.player, "suppressed private cadence sample");
        require(messageCount(ACTOR_ID) == actorPublicCount,
            "shared one-second public/private cadence did not suppress the immediate private reply");
        requireNoUnexpectedCallbackFailure("shared cooldown pass");
        getLogger().info("AHMET_PRESENTATION_STAGE_PASS name=shared-public-private-cooldown suppressionObserved=true"
            + " deliveryGuaranteed=false actorUuidOnly=true");
        sourcePlayers = List.of();
        startJob("normal", 8, 2, Fault.NONE, () -> startJob("swing-fault", 8, -2, Fault.SWING,
            () -> startJob("sound-fault", 8, 4, Fault.SOUND,
                () -> runFullChestScenario())));
      });
    } catch (Throwable error) { fail(error); }
  }

  private void startJob(String label, int sourceX, int sourceZ, Fault fault, Runnable next) {
    try {
      require(!bool(task, "active") && integer(task, "cargo") == 0 && !bool(ahmet, "recoveryBlocked"),
          "prior transaction did not return to idle before " + label);
      sourcePlayers = List.of();
      currentSource = world.getBlockAt(sourceX, 120, sourceZ);
      currentSource.setType(Material.DIRT, false);
      resetMovementWindow();
      activeFault = fault;
      cues.clear();
      int startCues = swingCalls.get();
      int chestItemsAtAdmission = countMaterial(chestLocation.getBlock().getState() instanceof Chest chest
          ? chest.getInventory() : null, Material.DIRT);
      startChestItems = countMaterial(chestLocation.getBlock().getState() instanceof Chest chest
          ? chest.getInventory() : null, Material.DIRT);
      startTeleportCount = normalTeleports;
      baselineGeneration = integer(controller, "jobGeneration");
      startDiagnosticCount = diagnostics.size();
      startActualSwings = actualSwings.get();
      startActualSounds = actualSounds.get();
      startInjectedSwingFaults = injectedSwingFaults.get();
      startInjectedSoundFaults = injectedSoundFaults.get();
      jobStartTick = globalTicks;
      command(operator.player, "ahmet collect dirt 1");
      require(bool(task, "active") && integer(task, "requested") == 1 && integer(task, "collected") == 0
          && integer(task, "deposited") == 0 && integer(task, "cargo") == 0,
          "real collect command did not admit and reset a one-item task");
      require(countMaterial(((Chest) chestLocation.getBlock().getState()).getInventory(), Material.DIRT)
          == chestItemsAtAdmission, "chest inventory changed during task admission");
      startCollected = integer(task, "collected");
      startDeposited = integer(task, "deposited");
      taskId = string(task, "taskId");
      resetLookCallsForJob();
      getLogger().info("AHMET_PRESENTATION_STAGE_START name=actual-one-item label=" + label + " fault=" + fault);
      waitFor("job-" + label, remainingJobTicks(),
          () -> !bool(task, "active") && integer(task, "collected") == startCollected + 1
              && integer(task, "deposited") == startDeposited + 1 && integer(task, "cargo") == 0,
          () -> finishJob(label, fault, startCues, next));
    } catch (Throwable error) { fail(error); }
  }

  private void finishJob(String label, Fault fault, int startCueCalls, Runnable next) {
    try {
      waitFor("job-home-cleanup-" + label, remainingJobTicks(),
          () -> !npc.getNavigator().isNavigating() && field(ahmet, "navigationGoal") == null
              && !bool(ahmet, "returningHome") && npc.getEntity().getLocation().distanceSquared(home) <= 2.5 * 2.5,
          () -> {
            try {
              requireStage(currentSource.getType() == Material.AIR, "source-not-air-after-harvest");
              requireStage(integer(task, "collected") == startCollected + 1
                  && integer(task, "deposited") == startDeposited + 1 && integer(task, "cargo") == 0,
                  "task-item-conservation");
              requireStage(countMaterial(((Chest) chestLocation.getBlock().getState()).getInventory(), Material.DIRT)
                  == startChestItems + 1, "chest-dirt-count");
              requireStage(!bool(ahmet, "recoveryBlocked") && !journalHasOperations()
                  && !pending("pendingHarvest") && !pending("pendingDeposit"), "journal-or-recovery-not-clear");
              requireStage(!bool(task, "active") && integer(controller, "jobGeneration") == baselineGeneration + 2,
                  "task-completion-generation");
              requireStage(field(ahmet, "treeTarget") == null && field(ahmet, "navigationGoal") == null
                  && !taskRouteOwned() && !npc.getNavigator().isNavigating(), "task-target-navigation-cleanup");
              requireStage(normalTeleports == startTeleportCount && movedTicks > 0 && totalWalkBlocks > 4.0 && maxStep <= 1.5,
                  "bounded-native-movement-or-teleport");
              requireStage(activeLookChunkPreflights.get() == 0 && faceCalls.get() == 0 && losCalls.get() == 0,
                  "look-activity-during-job");
              boolean newCueDiagnostic = diagnostics.subList(startDiagnosticCount, diagnostics.size()).stream()
                  .anyMatch(line -> line.startsWith("work-cue:"));
              requireStage(fault == Fault.NONE ? !newCueDiagnostic
                      : diagnostics.stream().anyMatch(line -> line.startsWith("work-cue:")),
                  "work-cue-diagnostic-evidence");
              int expectedCueAttempts = 2;
              requireStage(swingCalls.get() - startCueCalls == expectedCueAttempts, "committed-cue-call-count");
              requireStage(cues.stream().filter(cue -> cue.transaction().equals("harvest")).count() == 1
                  && cues.stream().filter(cue -> cue.transaction().equals("deposit")).count() == 1,
                  "committed-cue-transaction-order");
              Cue harvestCue = cues.stream().filter(cue -> cue.transaction().equals("harvest")).findFirst().orElseThrow();
              Cue depositCue = cues.stream().filter(cue -> cue.transaction().equals("deposit")).findFirst().orElseThrow();
              requireStage(depositCue.atNanos() - harvestCue.atNanos() >= 250_000_000L,
                  "committed-cue-spacing");
              Object watchdog = field(ahmet, "navigationWatchdog");
              requireStage(!bool(watchdog, "initialized") && integer(watchdog, "stalledAttempts") == 0,
                  "navigation-watchdog-cleanup");
              if (fault == Fault.NONE) requireStage(actualSwings.get() - startActualSwings == 2
                      && actualSounds.get() - startActualSounds == 2,
                  "default-bukkit-effect-delegation");
              if (fault == Fault.SWING) requireStage(actualSwings.get() - startActualSwings == 0
                      && actualSounds.get() - startActualSounds == 0,
                  "swing-fault-isolation");
              if (fault == Fault.SOUND) requireStage(actualSwings.get() - startActualSwings == 2
                      && actualSounds.get() - startActualSounds == 0,
                  "sound-fault-preserves-swings");
              requireStage(fault == Fault.SWING
                      ? injectedSwingFaults.get() - startInjectedSwingFaults == 2
                          && injectedSoundFaults.get() - startInjectedSoundFaults == 0
                      : fault == Fault.SOUND
                          ? injectedSoundFaults.get() - startInjectedSoundFaults == 2
                              && injectedSwingFaults.get() - startInjectedSwingFaults == 0
                          : injectedSwingFaults.get() == startInjectedSwingFaults
                              && injectedSoundFaults.get() == startInjectedSoundFaults,
                  "tagged-cosmetic-fault-site");
              requireStage(unexpectedCallbackFailure.get() == null, "unexpected-callback-latch-before-job-pass");
              getLogger().info("AHMET_PRESENTATION_STAGE_PASS name=actual-one-item label=" + label
                  + " collected=" + integer(task, "collected") + " deposited=" + integer(task, "deposited")
                  + " cargo=" + integer(task, "cargo") + " journalClear=true recoveryBlocked=false"
                  + " cueAttempts=" + (swingCalls.get() - startCueCalls) + " fault=" + fault
                  + " walkingBlocks=" + rounded(totalWalkBlocks) + " maxStep=" + rounded(maxStep)
                  + " normalTeleports=" + (normalTeleports - startTeleportCount));
              activeFault = Fault.NONE;
              next.run();
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void runFullChestScenario() {
    try {
      clearChest();
      Inventory inventory = ((Chest) chestLocation.getBlock().getState()).getInventory();
      for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
      List<String> fullChestBeforeAdmission = inventorySnapshot(inventory);
      require(fullChestBeforeAdmission.size() == inventory.getSize()
          && fullChestBeforeAdmission.stream().allMatch("COBBLESTONE:64"::equals),
          "full-chest setup did not fill every slot with 64 COBBLESTONE");
      int dirtBefore = countMaterial(inventory, Material.DIRT);
      cues.clear();
      int cueCallsBefore = swingCalls.get();
      currentSource = world.getBlockAt(8, 120, -4);
      currentSource.setType(Material.DIRT, false);
      resetMovementWindow();
      startTeleportCount = normalTeleports;
      baselineGeneration = integer(controller, "jobGeneration");
      startChestItems = countMaterial(((Chest) chestLocation.getBlock().getState()).getInventory(), Material.DIRT);
      startDiagnosticCount = diagnostics.size();
      startActualSwings = actualSwings.get();
      startActualSounds = actualSounds.get();
      startInjectedSwingFaults = injectedSwingFaults.get();
      startInjectedSoundFaults = injectedSoundFaults.get();
      int noCapacityBefore = noCapacityWarnings.get();
      jobStartTick = globalTicks;
      command(operator.player, "ahmet collect dirt 1");
      require(bool(task, "active") && integer(task, "requested") == 1 && integer(task, "collected") == 0
          && integer(task, "deposited") == 0 && integer(task, "cargo") == 0,
          "full-chest command did not admit and reset a one-item task");
      startCollected = integer(task, "collected");
      startDeposited = integer(task, "deposited");
      resetLookCallsForJob();
      waitFor("full-chest-zero-move-hold", remainingJobTicks(),
          () -> integer(task, "collected") == startCollected + 1 && integer(task, "cargo") == 1
              && currentSource.getType() == Material.AIR && !pending("pendingDeposit")
              && !npc.getNavigator().isNavigating()
              && npc.getEntity().getLocation().distanceSquared(chestLocation.clone().add(0.5, 0, 0.5)) <= 3.2 * 3.2
              && noCapacityWarnings.get() > noCapacityBefore, () -> {
            try {
              int heldChestItems = countChest();
              require(integer(task, "cargo") == 1 && integer(task, "deposited") == startDeposited
                  && heldChestItems == inventory.getSize() * 64
                  && inventorySnapshot(((Chest) chestLocation.getBlock().getState()).getInventory()).equals(fullChestBeforeAdmission)
                  && countMaterial(((Chest) chestLocation.getBlock().getState()).getInventory(), Material.DIRT) == dirtBefore,
                  "no-capacity deposit did not retain cargo/chest state");
              require(cues.stream().filter(cue -> cue.transaction().equals("harvest")).count() == 1
                  && cues.stream().noneMatch(cue -> cue.transaction().equals("deposit")),
                  "zero-move full-chest branch emitted a successful deposit cue");
              requireNoUnexpectedCallbackFailure("full chest hold pass");
              getLogger().info("AHMET_PRESENTATION_STAGE_PASS name=full-chest-no-capacity retainedCargo=1"
                  + " zeroMoveDepositCue=false harvestCue=true capacityFailureObserved=true chestItems=" + heldChestItems);
              clearChest();
              waitFor("full-chest-restored-delivery", remainingJobTicks(),
                  () -> !bool(task, "active") && integer(task, "cargo") == 0
                      && integer(task, "deposited") == startDeposited + 1,
                  () -> finishJob("full-chest-restored", Fault.NONE, cueCallsBefore, this::finishProbe));
            } catch (Throwable error) { fail(error); }
          });
    } catch (Throwable error) { fail(error); }
  }

  private void finishProbe() {
    try {
      require(npc.getEntity() instanceof Villager && normalTeleports == 0, "final Citizens identity/teleport invariant failed");
      requireNoUnexpectedCallbackFailure("final pass");
      getLogger().info("AHMET_PRESENTATION_RUNTIME_PROBE_PASS realCitizensVillager=true sourceTransactions=actual"
          + " mockedHttpChatRequests=" + chatRequests.get() + " proxyPlayers=true proxyLos=injected"
          + " normalWalkBlocks=" + rounded(totalWalkBlocks) + " normalTeleports=" + normalTeleports
          + " defaultBukkitEffectsDelegated=true clientHeadArmAudio=UNKNOWN humanAcceptance=UNEXECUTED");
      complete();
    } catch (Throwable error) { fail(error); }
  }

  private void tick() {
    if (terminal) return;
    String callbackFailure = unexpectedCallbackFailure.get();
    if (callbackFailure != null) {
      fail(new IllegalStateException(callbackFailure));
      return;
    }
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
    } catch (Throwable error) { fail(error); }
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

  private int remainingJobTicks() {
    return Math.max(1, JOB_TICK_BUDGET - (globalTicks - jobStartTick));
  }

  private void sampleMovement() {
    if (npc == null || !npc.isSpawned() || previousPosition == null) return;
    Location current = npc.getEntity().getLocation();
    if (current.getWorld().equals(previousPosition.getWorld())) {
      double step = previousPosition.distance(current);
      totalWalkBlocks += step;
      if (step > 0.001) movedTicks++;
      maxStep = Math.max(maxStep, step);
    }
    previousPosition = current.clone();
  }

  private void resetMovementWindow() {
    previousPosition = npc.getEntity().getLocation().clone();
    totalWalkBlocks = 0;
    maxStep = 0;
    movedTicks = 0;
  }

  private void resetLookCallsForJob() {
    playerSourceCalls.set(0);
    activeTaskSourceQueries.set(0);
    activeLookChunkPreflights.set(0);
    faceCalls.set(0);
    losCalls.set(0);
    sourcePlayers = List.of();
  }

  private Snapshot snapshot() throws Exception {
    Object schedule = field(ahmet, "scheduleAdapter");
    return new Snapshot(string(task, "taskId"), string(task, "owner"), integer(controller, "jobGeneration"),
        bool(task, "active"), integer(task, "cargo"), bool(ahmet, "recoveryBlocked"), journalHasOperations(),
        npc.getNavigator().isNavigating(), taskRouteOwned(), invokeBoolean(schedule, "ownsNavigation"),
        String.valueOf(invoke(schedule, "phase")), String.valueOf(invoke(schedule, "status")), normalTeleports,
        (Location) field(ahmet, "navigationGoal"));
  }

  private void requireUnchanged(Snapshot before, String label) throws Exception {
    Snapshot after = snapshot();
    List<String> changed = new ArrayList<>();
    if (!before.taskId().equals(after.taskId())) changed.add("taskId");
    if (!before.owner().equals(after.owner())) changed.add("owner");
    if (before.generation() != after.generation()) changed.add("generation");
    if (before.active() != after.active()) changed.add("active");
    if (before.cargo() != after.cargo()) changed.add("cargo");
    if (before.recoveryBlocked() != after.recoveryBlocked()) changed.add("recoveryBlocked");
    if (before.pendingJournal() != after.pendingJournal()) changed.add("journalState");
    if (before.navigating() != after.navigating()) changed.add("npcNavigation");
    if (before.taskRouteOwned() != after.taskRouteOwned()) changed.add("taskRouteOwnership");
    if (before.scheduleNavigating() != after.scheduleNavigating()) changed.add("scheduleNavigation");
    if (!before.schedulePhase().equals(after.schedulePhase())) changed.add("schedulePhase");
    if (before.teleportCount() != after.teleportCount()) changed.add("teleportCount");
    if (!java.util.Objects.equals(before.navigationGoal(), after.navigationGoal())) changed.add("navigationGoalSet");
    if (!before.scheduleStatus().equals(after.scheduleStatus())) changed.add("scheduleStatus");
    if (!changed.isEmpty()) {
      throw new FixtureAssertion("idle-authority-state-changed", "changedFields=" + String.join(",", changed)
          + " schedulePhase=" + before.schedulePhase() + "->" + after.schedulePhase()
          + " scheduleStatus=" + safeScheduleStatus(before.scheduleStatus()) + "->" + safeScheduleStatus(after.scheduleStatus()));
    }
  }

  private static String safeScheduleStatus(String status) {
    String safe = status.replaceAll("[^A-Za-z0-9 ()_;,-]", "?");
    return safe.length() <= 100 ? safe : safe.substring(0, 100);
  }

  private void recordUnexpectedCallbackFailure(String reason) {
    unexpectedCallbackFailure.compareAndSet(null, reason);
  }

  private void requireCallback(boolean condition, String reason) {
    if (condition) return;
    recordUnexpectedCallbackFailure(reason);
    throw new IllegalStateException("fixture callback invariant failed");
  }

  private void requireNoUnexpectedCallbackFailure(String context) {
    String failure = unexpectedCallbackFailure.get();
    require(failure == null, context + " observed an unexpected callback invariant");
  }

  private void requireStage(boolean condition, String reason) {
    if (!condition) throw new FixtureAssertion(reason, "");
  }

  private Cue observeCommittedCue(String effect) {
    try { return observeCommittedCueSafely(effect); }
    catch (Exception unexpected) {
      recordUnexpectedCallbackFailure("cue-observation-" + effect);
      throw unexpected instanceof RuntimeException runtime ? runtime
          : new IllegalStateException("fixture cue observation failed", unexpected);
    }
  }

  private Cue observeCommittedCueSafely(String effect) throws Exception {
    requireCallback(Bukkit.isPrimaryThread(), "cue-observation-main-thread");
    int collected = integer(task, "collected"), cargo = integer(task, "cargo"), deposited = integer(task, "deposited");
    boolean pendingHarvest = pending("pendingHarvest"), pendingDeposit = pending("pendingDeposit");
    String transaction;
    if (collected > startCollected && cargo > 0 && currentSource != null && currentSource.getType() == Material.AIR) {
      transaction = "harvest";
      requireCallback(!pendingHarvest && journalHasOperations(), "harvest-cue-before-journal-commit");
    } else if (deposited > startDeposited && cargo == 0) {
      transaction = "deposit";
      requireCallback(!pendingDeposit && !journalHasOperations(), "deposit-cue-before-journal-retirement");
      requireCallback(countMaterial(((Chest) chestLocation.getBlock().getState()).getInventory(), Material.DIRT)
          > startChestItems, "deposit-cue-before-chest-write");
    } else {
      recordUnexpectedCallbackFailure(effect + "-without-commit");
      throw new IllegalStateException("fixture cue without committed transaction");
    }
    return new Cue(transaction, collected, cargo, deposited, countChest(), pendingHarvest, pendingDeposit, System.nanoTime());
  }

  private void clearChest() {
    Inventory inventory = ((Chest) chestLocation.getBlock().getState()).getInventory();
    inventory.clear();
  }

  private int countChest() {
    return countMaterial(((Chest) chestLocation.getBlock().getState()).getInventory(), null);
  }

  private static int countMaterial(Inventory inventory, Material material) {
    if (inventory == null) return 0;
    int amount = 0;
    for (ItemStack item : inventory.getContents()) {
      if (item != null && !item.getType().isAir() && (material == null || item.getType() == material)) amount += item.getAmount();
    }
    return amount;
  }

  private static List<String> inventorySnapshot(Inventory inventory) {
    List<String> slots = new ArrayList<>(inventory.getSize());
    for (ItemStack item : inventory.getContents()) {
      slots.add(item == null || item.getType().isAir() ? "AIR:0" : item.getType().name() + ":" + item.getAmount());
    }
    return List.copyOf(slots);
  }

  private boolean journalHasOperations() { return invokeBoolean(journal, "hasOperations"); }
  private boolean pending(String method) { return ((Optional<?>) invoke(journal, method)).isPresent(); }
  private boolean taskRouteOwned() {
    return invokeBoolean(field(ahmet, "taskNavigationRoute"), "matches", new Class<?>[]{NPC.class}, npc);
  }

  private void command(Player sender, String line) throws Exception {
    String[] tokens = line.trim().split("\\s+");
    PluginCommand command = ((JavaPlugin) ahmet).getCommand(tokens[0]);
    require(command != null, "missing plugin command registration: " + tokens[0]);
    command.execute((CommandSender) sender, tokens[0], Arrays.copyOfRange(tokens, 1, tokens.length));
  }

  private void startMockBrain() throws Exception {
    bridgeToken = ahmet.getConfig().getString("brain.token", "");
    require(!bridgeToken.isBlank() && !bridgeToken.equals("CHANGE_ME_TO_A_RANDOM_SECRET"),
        "disposable synthetic bridge token is missing");
    ahmet.getConfig().set("brain.url", "http://127.0.0.1:3199");
    ahmet.getConfig().set("brain.timeout-seconds", 4);
    mockBrain = HttpServer.create(new InetSocketAddress("127.0.0.1", 3199), 4);
    mockExecutor = Executors.newFixedThreadPool(2);
    mockBrain.setExecutor(mockExecutor);
    mockBrain.createContext("/api/chat", this::mockChat);
    mockBrain.createContext("/api/decide", exchange -> respondAuthorized(exchange, "{\"id\":\"target_a\",\"source\":\"heuristic\",\"model\":\"fixture\"}"));
    mockBrain.createContext("/api/event", exchange -> respondAuthorized(exchange, "{\"ok\":true}"));
    mockBrain.start();
  }

  private void mockChat(HttpExchange exchange) {
    try {
      if (!authorized(exchange)) { respond(exchange, 401, "{}"); return; }
      if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, "{}"); return; }
      if (exchange.getRequestBody().readNBytes(16_001).length > 16_000) { respond(exchange, 413, "{}"); return; }
      chatRequests.incrementAndGet();
      CountDownLatch gate = nextChatRelease;
      if (gate != null) {
        nextChatRelease = null;
        if (!gate.await(5, TimeUnit.SECONDS)) { respond(exchange, 504, "{}"); return; }
      }
      respond(exchange, 200, "{\"reply\":\"" + PRIVATE_SENTINEL + "\",\"cancel\":false,\"goHome\":false}");
      chatResponsesWritten.incrementAndGet();
    } catch (Throwable error) {
      try { respond(exchange, 400, "{}"); } catch (Exception ignored) { }
    }
  }

  private boolean authorized(HttpExchange exchange) {
    return ("Bearer " + bridgeToken).equals(exchange.getRequestHeaders().getFirst("Authorization"));
  }

  private void respondAuthorized(HttpExchange exchange, String body) {
    try { respond(exchange, authorized(exchange) ? 200 : 401, authorized(exchange) ? body : "{}"); }
    catch (Exception ignored) { }
  }

  private static void respond(HttpExchange exchange, int status, String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) { output.write(bytes); }
  }

  private boolean containsMessage(UUID player, String fragment) {
    return messages.getOrDefault(player, List.of()).stream().anyMatch(value -> value.contains(fragment));
  }
  private int messageCount(UUID player) { return messages.getOrDefault(player, List.of()).size(); }

  private String safeFailureState() {
    if (ahmet == null || task == null || controller == null || npc == null) return "state=unavailable";
    try {
      Object schedule = field(ahmet, "scheduleAdapter");
      String phase = String.valueOf(invoke(schedule, "phase"));
      String status = safeScheduleStatus(String.valueOf(invoke(schedule, "status")));
      return "taskActive=" + bool(task, "active") + " requested=" + integer(task, "requested")
          + " collected=" + integer(task, "collected") + " deposited=" + integer(task, "deposited")
          + " cargo=" + integer(task, "cargo") + " generation=" + integer(controller, "jobGeneration")
          + " recoveryBlocked=" + bool(ahmet, "recoveryBlocked") + " journalHasOperations=" + journalHasOperations()
          + " pendingHarvest=" + pending("pendingHarvest") + " pendingDeposit=" + pending("pendingDeposit")
          + " sharedPlayersQueriesDuringTask=" + activeTaskSourceQueries.get()
          + " lookChunkPreflightsDuringTask=" + activeLookChunkPreflights.get()
          + " faceCalls=" + faceCalls.get()
          + " losCalls=" + losCalls.get()
          + " npcNavigating=" + npc.getNavigator().isNavigating() + " taskRouteOwned=" + taskRouteOwned()
          + " navigationGoalSet=" + (field(ahmet, "navigationGoal") != null)
          + " returningHome=" + bool(ahmet, "returningHome") + " schedulePhase=" + phase
          + " scheduleStatus=" + status + " normalTeleports=" + normalTeleports
          + " movedTicks=" + movedTicks + " walkBlocks=" + rounded(totalWalkBlocks)
          + " maxStep=" + rounded(maxStep) + " cueCalls=" + swingCalls.get()
          + " actualSwings=" + actualSwings.get() + " soundCalls=" + soundCalls.get()
          + " actualSounds=" + actualSounds.get() + " injectedSwingFaults=" + injectedSwingFaults.get()
          + " injectedSoundFaults=" + injectedSoundFaults.get();
    } catch (RuntimeException ignored) {
      return "stateRead=failed";
    }
  }

  private void fail(Throwable error) {
    if (terminal) return;
    terminal = true;
    String expectedNegative = negativeSoundInvariant ? "sound-invariant"
        : negativeFaceDelegate ? "face-delegate" : null;
    String observed = unexpectedCallbackFailure.get();
    if (expectedNegative != null && expectedNegative.equals(observed)) {
      getLogger().severe("AHMET_PRESENTATION_RUNTIME_PROBE_FAIL mode=negative-control expectedReason="
          + expectedNegative + " observedReason=" + observed + " normalPass=false globalTicks=" + globalTicks
          + " " + safeFailureState());
    } else {
      String failureReason = error instanceof FixtureAssertion assertion
          ? assertion.reason() : error.getClass().getSimpleName();
      String safeDetail = error instanceof FixtureAssertion assertion && !assertion.safeDetail().isBlank()
          ? " detail=" + assertion.safeDetail() : "";
      getLogger().severe("AHMET_PRESENTATION_RUNTIME_PROBE_FAIL stage=" + stage
          + " exception=" + error.getClass().getSimpleName() + " reason=" + failureReason + safeDetail
          + " globalTicks=" + globalTicks + " passMarkerAbsent=true " + safeFailureState());
    }
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
    for (BukkitTask task : tasks) if (task != null) task.cancel();
    tasks.clear();
    try { if (mockBrain != null) mockBrain.stop(0); } catch (RuntimeException ignored) { }
    try { if (mockExecutor != null) mockExecutor.shutdownNow(); } catch (RuntimeException ignored) { }
    try { if (ahmet != null && warningHandler != null) ahmet.getLogger().removeHandler(warningHandler); }
    catch (RuntimeException ignored) { }
    try {
      if (ahmet != null && presentationField != null && presentationField.get(ahmet) == injectedAdapter)
        presentationField.set(ahmet, previousAdapter);
    } catch (Exception error) {
      if (isEnabled()) getLogger().severe("AHMET_PRESENTATION_CLEANUP_WARNING adapterRestore=" + error.getClass().getSimpleName());
    }
    for (int[] ticket : ownChunkTickets) {
      try { world.removePluginChunkTicket(ticket[0], ticket[1], this); } catch (RuntimeException ignored) { }
    }
    ownChunkTickets.clear();
    retainedWorlds.clear();
  }

  private static boolean invokeBoolean(Object target, String method) {
    return Boolean.TRUE.equals(invoke(target, method));
  }
  private static boolean invokeBoolean(Object target, String method, Class<?>[] types, Object... args) {
    return Boolean.TRUE.equals(invoke(target, method, types, args));
  }
  private static Object invoke(Object target, String method) {
    try {
      Method value = target.getClass().getDeclaredMethod(method); value.setAccessible(true); return value.invoke(target);
    } catch (Exception error) { throw new IllegalStateException("fixture reflection failed", error); }
  }
  private static Object invoke(Object target, String method, Class<?>[] types, Object... args) {
    try {
      Method value = target.getClass().getDeclaredMethod(method, types); value.setAccessible(true); return value.invoke(target, args);
    } catch (Exception error) { throw new IllegalStateException("fixture reflection failed", error); }
  }
  private static Field findField(Object target, String name) {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try { Field result = type.getDeclaredField(name); result.setAccessible(true); return result; }
      catch (NoSuchFieldException ignored) { }
    }
    throw new IllegalStateException("fixture field missing: " + name);
  }
  private static Object field(Object target, String name) {
    try { return findField(target, name).get(target); }
    catch (Exception error) { throw new IllegalStateException("fixture field read failed", error); }
  }
  private static void set(Object target, String name, Object value) {
    try { findField(target, name).set(target, value); }
    catch (Exception error) { throw new IllegalStateException("fixture field write failed", error); }
  }
  private static int integer(Object target, String name) { return ((Number) field(target, name)).intValue(); }
  private static boolean bool(Object target, String name) { return (boolean) field(target, name); }
  private static String string(Object target, String name) { return String.valueOf(field(target, name)); }
  private static Object primitiveDefault(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) return null;
    if (type == boolean.class) return false; if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0; if (type == short.class) return (short) 0;
    if (type == int.class) return 0; if (type == long.class) return 0L;
    if (type == float.class) return 0f; if (type == double.class) return 0d;
    return null;
  }
  private static String rounded(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private final class PlayerProxy {
    final UUID id;
    final Player player;
    volatile Location location;
    boolean admin;
    boolean invisible;
    GameMode gameMode = GameMode.SURVIVAL;
    Block targetBlock;
    private final AtomicInteger reads = new AtomicInteger();

    PlayerProxy(UUID id, boolean admin, Location location) {
      this.id = id; this.admin = admin; this.location = location.clone();
      retainedWorlds.add(location.getWorld());
      this.player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, handler());
    }

    int locationReads() { return reads.get(); }

    private InvocationHandler handler() {
      return (proxy, method, args) -> {
        try {
          return switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getWorld" -> location.getWorld();
            case "getLocation" -> { reads.incrementAndGet(); yield location.clone(); }
            case "isOnline" -> true;
            case "isInvisible" -> invisible;
            case "getGameMode" -> gameMode;
            case "hasPermission" -> admin && "ahmet.admin".equals(args[0]);
            case "getName", "getDisplayName" -> admin ? "PresentationOperator" : "SyntheticActor";
            case "canSee" -> true;
            case "getTargetBlockExact" -> targetBlock;
            case "sendMessage" -> { messages.computeIfAbsent(id, ignored -> new ArrayList<>()).add(String.valueOf(args[0])); yield null; }
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "M4 synthetic Player proxy";
            default -> primitiveDefault(method.getReturnType());
          };
        } catch (RuntimeException unexpected) {
          recordUnexpectedCallbackFailure("player-proxy-" + method.getName());
          throw unexpected;
        }
      };
    }
  }
}
