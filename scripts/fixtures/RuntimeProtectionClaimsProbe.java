package dev.ahmetprobe;

import dev.ahmet.protection.ClaimAction;
import dev.ahmet.protection.ClaimBoundary;
import dev.ahmet.protection.LandClaim;
import dev.ahmet.protection.LandClaimRegistry;
import dev.ahmet.protection.ExternalProtectionAdapter;
import dev.ahmet.protection.ChunkTicketManager;
import net.citizensnpcs.api.event.NPCTeleportEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Opt-in disposable runtime probe for Milestone 8 (Claims, Protection Compatibility, Chunk Tickets).
 * Never install on a live server.
 */
public final class RuntimeProtectionClaimsProbe extends JavaPlugin implements Listener {
  private static final int GLOBAL_TICK_BUDGET = 3600;
  private static final UUID OPERATOR_ID = UUID.fromString("48efc754-6a02-4ed3-9a42-46e2a8f70001");

  private record Wait(String name, int budgetTicks, BooleanSupplier condition, Runnable success, int[] elapsed) {}

  private Plugin ahmetPlugin;
  private World world;
  private PlayerProxy operator;

  private NPC npcAhmet;
  private NPC npcMehmet;

  private LandClaimRegistry landClaims;
  private ExternalProtectionAdapter externalProtection;
  private ChunkTicketManager chunkTickets;

  private final List<BukkitTask> tasks = new ArrayList<>();
  private final List<String> capturedMessages = Collections.synchronizedList(new ArrayList<>());
  private BukkitTask ticker;
  private Wait wait;

  private String stage = "startup";
  private boolean terminal;
  private int globalTicks;

  private boolean countNormalTeleports;
  private int normalTeleports;

  @Override
  public void onEnable() {
    if (!Boolean.getBoolean("ahmet.claimsProbe")) {
      getLogger().warning("AHMET_CLAIMS_PROBE_DISABLED");
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

      operator = new PlayerProxy(OPERATOR_ID, true, new Location(world, 0.5, 64, 0.5));

      // 1. Spawn primary villager Ahmet
      command(operator.player, "ahmet spawn");
      Object savedNpc = field(ahmetPlugin, "npc");
      require(savedNpc instanceof NPC, "/ahmet spawn did not create Citizens NPC");
      npcAhmet = (NPC) savedNpc;
      require(npcAhmet.isSpawned() && npcAhmet.getEntity().getType() == EntityType.VILLAGER
          && npcAhmet.getEntity() instanceof Villager, "Expected Ahmet to be a Citizens EntityType.VILLAGER");

      // 2. Spawn secondary villager Mehmet
      command(operator.player, "ahmet npc mehmet spawn");
      @SuppressWarnings("unchecked")
      Map<String, Object> slots = (Map<String, Object>) field(ahmetPlugin, "villagerSlots");
      require(slots.containsKey("ahmet") && slots.containsKey("mehmet"), "villagerSlots must contain ahmet and mehmet");
      Object slotMehmet = slots.get("mehmet");
      npcMehmet = (NPC) field(slotMehmet, "npc");
      require(npcMehmet != null && npcMehmet.isSpawned() && npcMehmet.getEntity().getType() == EntityType.VILLAGER
          && npcMehmet.getEntity() instanceof Villager, "Expected Mehmet to be a Citizens EntityType.VILLAGER");

      // Access plugin components via reflection
      landClaims = (LandClaimRegistry) field(ahmetPlugin, "landClaims");
      externalProtection = (ExternalProtectionAdapter) field(ahmetPlugin, "externalProtection");
      chunkTickets = (ChunkTicketManager) field(ahmetPlugin, "chunkTicketManager");
      require(landClaims != null, "landClaims registry must be initialized in AhmetPlugin");
      require(externalProtection != null, "externalProtection must be initialized in AhmetPlugin");
      require(chunkTickets != null, "chunkTicketManager must be initialized in AhmetPlugin");

      countNormalTeleports = true;
      normalTeleports = 0;

      awaitTicks(5, this::startStage1);
    } catch (Throwable error) {
      fail(error);
    }
  }

  // --- STAGE 1: Land claim command registration & persistence ---
  private void startStage1() {
    stage = "stage1_claim_registration_and_persistence";
    getLogger().info("=== PROBE STAGE 1: Land Claim Registration & Persistence ===");

    capturedMessages.clear();
    command(operator.player, "ahmet claim add village_farm -10 60 -10 10 70 10 ahmet HARVEST_CROPS " + world.getName());
    require(landClaims.getClaim("village_farm") != null, "Claim 'village_farm' was not added to registry");

    LandClaim claim = landClaims.getClaim("village_farm");
    require(claim.boundary().world().equals(world.getName()), "Claim world mismatch");
    require(claim.boundary().minX() == -10 && claim.boundary().maxX() == 10, "Claim bounds X mismatch");
    require(claim.allowedVillagers().contains("ahmet"), "Claim must allow ahmet");
    require(!claim.allowedVillagers().contains("mehmet"), "Claim must NOT allow mehmet");
    require(claim.allowedActions().contains(ClaimAction.HARVEST_CROPS), "Claim must allow HARVEST_CROPS");
    require(!claim.allowedActions().contains(ClaimAction.GATHER_WOOD), "Claim must NOT allow GATHER_WOOD");

    // Verify persistence on disk
    File claimsFile = new File(ahmetPlugin.getDataFolder(), "claims.json");
    require(claimsFile.exists() && claimsFile.length() > 0, "claims.json was not created on disk");

    // Test list command
    capturedMessages.clear();
    command(operator.player, "ahmet claim list");
    require(capturedMessages.stream().anyMatch(msg -> msg.contains("village_farm")), "Command /ahmet claim list did not output village_farm");

    // Test info command
    capturedMessages.clear();
    command(operator.player, "ahmet claim info village_farm");
    require(capturedMessages.stream().anyMatch(msg -> msg.contains("village_farm")), "Command /ahmet claim info did not report claim info");

    awaitTicks(5, this::startStage2);
  }

  // --- STAGE 2: Foreign claim refusal & authorized acceptance ---
  private void startStage2() {
    stage = "stage2_foreign_claim_refusal";
    getLogger().info("=== PROBE STAGE 2: Foreign Claim Refusal & Authorized Acceptance ===");

    Block cropBlock = world.getBlockAt(0, 64, -5); // Inside village_farm
    cropBlock.setType(Material.WHEAT);

    Block woodBlock = world.getBlockAt(0, 64, -6); // Inside village_farm
    woodBlock.setType(Material.OAK_LOG);

    // 1. In village_farm: Ahmet is allowed to harvest crops
    boolean ahmetCropProtected = landClaims.isProtected("ahmet", cropBlock, ClaimAction.HARVEST_CROPS);
    require(!ahmetCropProtected, "Ahmet must NOT be protected against HARVEST_CROPS inside village_farm");

    // 2. In village_farm: Mehmet is NOT allowed (foreign villager refusal)
    boolean mehmetCropProtected = landClaims.isProtected("mehmet", cropBlock, ClaimAction.HARVEST_CROPS);
    require(mehmetCropProtected, "Mehmet must be REFUSED (protected=true) for HARVEST_CROPS in village_farm");

    // 3. In village_farm: Wood is NOT allowed even for Ahmet because action is limited to HARVEST_CROPS
    boolean ahmetWoodProtected = landClaims.isProtected("ahmet", woodBlock, ClaimAction.GATHER_WOOD);
    require(ahmetWoodProtected, "Ahmet must be REFUSED (protected=true) for GATHER_WOOD in village_farm");

    // 4. In wilderness: block outside claim is allowed
    Block wildernessBlock = world.getBlockAt(100, 64, 100);
    wildernessBlock.setType(Material.OAK_LOG);
    boolean wildernessProtected = landClaims.isProtected("mehmet", wildernessBlock, ClaimAction.GATHER_WOOD);
    require(!wildernessProtected, "Wilderness block outside any claim must NOT be protected");

    awaitTicks(5, this::startStage3);
  }

  // --- STAGE 3: Wildcard allowed villager & action ---
  private void startStage3() {
    stage = "stage3_claim_action_granularity";
    getLogger().info("=== PROBE STAGE 3: Wildcard Villagers & Global Action Claims ===");

    capturedMessages.clear();
    command(operator.player, "ahmet claim add common_forest 20 60 20 30 70 30 * ALL " + world.getName());
    require(landClaims.getClaim("common_forest") != null, "Claim 'common_forest' was not added");

    Block commonBlock = world.getBlockAt(25, 65, 25);
    commonBlock.setType(Material.BIRCH_LOG);

    // Wildcard '*' allows both Ahmet and Mehmet
    require(!landClaims.isProtected("ahmet", commonBlock, ClaimAction.GATHER_WOOD), "Ahmet must be allowed in wildcard claim");
    require(!landClaims.isProtected("mehmet", commonBlock, ClaimAction.GATHER_WOOD), "Mehmet must be allowed in wildcard claim");
    require(!landClaims.isProtected("ahmet", commonBlock, ClaimAction.CLEAR_OBSTRUCTION), "CLEAR_OBSTRUCTION must be allowed in ALL claim");

    awaitTicks(5, this::startStage4);
  }

  // --- STAGE 4: External protection reflection and fail-closed safety ---
  private void startStage4() {
    stage = "stage4_external_protection_reflection_and_fail_closed";
    getLogger().info("=== PROBE STAGE 4: External Protection Reflection & Fail-Closed Safety ===");

    Block testBlock = world.getBlockAt(50, 64, 50);

    // 1. Default without external plugins: graceful wilderness fallback (not protected)
    boolean defaultProtected = externalProtection.isProtected(testBlock, null, null);
    require(!defaultProtected, "Default external protection without external plugins must return false");

    // 2. Simulated foreign owner rejection
    externalProtection.setProviderOverride((block, loc, ownerUuid) -> {
      return !"trusted-owner-uuid".equals(ownerUuid);
    });
    boolean foreignOwnerProtected = externalProtection.isProtected(testBlock, null, "foreign-owner-uuid");
    require(foreignOwnerProtected, "Simulated foreign owner claim must be protected");

    boolean trustedOwnerProtected = externalProtection.isProtected(testBlock, null, "trusted-owner-uuid");
    require(!trustedOwnerProtected, "Simulated trusted owner claim must NOT be protected");

    // 3. Exception in external provider -> fail closed!
    externalProtection.setProviderOverride((block, loc, ownerUuid) -> {
      throw new RuntimeException("Simulated GP/WG database timeout");
    });
    boolean exceptionProtected = externalProtection.isProtected(testBlock, null, "trusted-owner-uuid");
    require(exceptionProtected, "External provider exception MUST fail closed as protected=true");

    // Reset override
    externalProtection.setProviderOverride(null);

    awaitTicks(5, this::startStage5);
  }

  // --- STAGE 5: Managed chunk tickets bounded lifecycle ---
  private void startStage5() {
    stage = "stage5_chunk_ticket_lifecycle_and_bounded_budget";
    getLogger().info("=== PROBE STAGE 5: Managed Chunk Ticket Bounded Budget & Lifecycle ===");

    String vid = "ahmet";
    chunkTickets.releaseTickets(world, vid, ahmetPlugin);
    require(chunkTickets.activeTicketCount(vid) == 0, "Initial ticket count must be 0");

    // Request up to max budget (9)
    for (int i = 1; i <= 9; i++) {
      boolean ok = chunkTickets.requestTicket(world, vid, i, i, ahmetPlugin);
      require(ok, "Chunk ticket request " + i + " must succeed");
    }
    require(chunkTickets.activeTicketCount(vid) == 9, "Active ticket count must be 9");

    // 10th request must be refused (bounded budget!)
    boolean refused = chunkTickets.requestTicket(world, vid, 10, 10, ahmetPlugin);
    require(!refused, "10th chunk ticket request must be REFUSED (budget exceeded)");
    require(chunkTickets.activeTicketCount(vid) == 9, "Active ticket count must remain 9");

    // Idempotent duplicate request
    boolean duplicate = chunkTickets.requestTicket(world, vid, 5, 5, ahmetPlugin);
    require(duplicate, "Duplicate chunk ticket request must return true without exceeding budget");
    require(chunkTickets.activeTicketCount(vid) == 9, "Active ticket count must remain 9 after duplicate");

    // Clean release
    chunkTickets.releaseTickets(world, vid, ahmetPlugin);
    require(chunkTickets.activeTicketCount(vid) == 0, "Ticket count after release must be 0");

    awaitTicks(5, this::startStage6);
  }

  // --- STAGE 6: Deletion, teleport check and report ---
  private void startStage6() {
    stage = "stage6_teleport_check_and_shutdown";
    getLogger().info("=== PROBE STAGE 6: Claim Removal, Teleport Zero Check & Shutdown ===");

    // Test removal via command
    command(operator.player, "ahmet claim remove village_farm");
    require(landClaims.getClaim("village_farm") == null, "Claim village_farm must be deleted");

    // Verify zero normal teleports
    require(normalTeleports == 0, "normalTeleports must be 0 throughout all autonomous operations, observed: " + normalTeleports);

    writeVerificationReport();
    getLogger().info("PROBE COMPLETED SUCCESSFULLY WITH 0 TELEPORTS.");

    terminal = true;
    Bukkit.getScheduler().runTaskLater(ahmetPlugin, () -> {
      Bukkit.shutdown();
    }, 10L);
  }

  private void writeVerificationReport() {
    try {
      File reportFile = new File("/private/tmp/ahmet-claims-verification.json");
      String json = "{\n"
          + "  \"status\": \"PASS\",\n"
          + "  \"milestone\": \"M8\",\n"
          + "  \"stage\": \"" + stage + "\",\n"
          + "  \"globalTicks\": " + globalTicks + ",\n"
          + "  \"normalTeleports\": " + normalTeleports + ",\n"
          + "  \"claimsValid\": " + landClaims.isStateValid() + ",\n"
          + "  \"activeClaimsCount\": " + landClaims.allClaims().size() + ",\n"
          + "  \"chunkTicketsClean\": true\n"
          + "}\n";
      Files.writeString(reportFile.toPath(), json);
      getLogger().info("Verification report written to: " + reportFile.getAbsolutePath());
    } catch (Exception e) {
      getLogger().warning("Failed to write verification report: " + e.getMessage());
    }
  }

  private void tick() {
    globalTicks++;
    if (terminal) return;
    if (wait != null) {
      wait.elapsed[0]++;
      if (wait.condition.getAsBoolean()) {
        Wait ready = wait;
        wait = null;
        ready.success.run();
      } else if (wait.elapsed[0] >= wait.budgetTicks) {
        fail(new IllegalStateException("Wait condition timed out: " + wait.name));
      }
    }
  }

  private void awaitTicks(int ticks, Runnable action) {
    int[] elapsed = new int[]{0};
    wait = new Wait("ticks_" + ticks, ticks + 10, () -> elapsed[0] >= ticks, action, elapsed);
  }

  private void cleanup() {
    terminal = true;
    for (BukkitTask task : tasks) {
      try { task.cancel(); } catch (Throwable ignored) {}
    }
    tasks.clear();
  }

  private void fail(Throwable error) {
    terminal = true;
    getLogger().severe("PROBE FAILED at stage '" + stage + "': " + error.getMessage());
    error.printStackTrace();
    cleanup();
    Bukkit.shutdown();
  }

  private void command(CommandSender sender, String commandLine) {
    getLogger().info("EXEC COMMAND: /" + commandLine);
    String[] tokens = commandLine.trim().split("\\s+");
    org.bukkit.command.PluginCommand cmd = ((JavaPlugin) ahmetPlugin).getCommand(tokens[0]);
    require(cmd != null, "Missing plugin command registration: " + tokens[0]);
    cmd.execute(sender, tokens[0], java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
  }

  private void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static Object field(Object target, String name) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(target);
    } catch (Exception e) {
      throw new RuntimeException("Failed to read field " + name, e);
    }
  }

  private class PlayerProxy implements InvocationHandler {
    final Player player;
    final UUID uuid;
    final boolean op;
    Location location;

    PlayerProxy(UUID uuid, boolean op, Location location) {
      this.uuid = uuid;
      this.op = op;
      this.location = location;
      this.player = (Player) Proxy.newProxyInstance(
          Player.class.getClassLoader(),
          new Class<?>[]{Player.class},
          this
      );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      String name = method.getName();
      if ("getUniqueId".equals(name)) return uuid;
      if ("getName".equals(name)) return "Operator";
      if ("isOp".equals(name)) return op;
      if ("hasPermission".equals(name)) return op;
      if ("getLocation".equals(name)) return location.clone();
      if ("getWorld".equals(name)) return location.getWorld();
      if ("sendMessage".equals(name)) {
        if (args != null && args.length > 0 && args[0] != null) {
          capturedMessages.add(args[0].toString());
          getLogger().info("[OperatorMsg] " + args[0]);
        }
        return null;
      }
      if ("isOnline".equals(name)) return true;
      if ("isValid".equals(name)) return true;
      if ("hashCode".equals(name)) return uuid.hashCode();
      if ("equals".equals(name)) return args != null && args.length == 1 && proxy == args[0];
      if (method.getReturnType().equals(boolean.class)) return false;
      if (method.getReturnType().equals(int.class)) return 0;
      if (method.getReturnType().equals(double.class)) return 0.0;
      return null;
    }
  }
}
