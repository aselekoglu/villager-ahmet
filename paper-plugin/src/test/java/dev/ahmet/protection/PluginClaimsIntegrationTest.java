package dev.ahmet.protection;

import dev.ahmet.FarmCatalog;
import dev.ahmet.ObstructionPolicy;
import dev.ahmet.WoodMaterials;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PluginClaimsIntegrationTest {

  private Block mockBlock(String worldName, int x, int y, int z, Material material) {
    World world = (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> worldName;
          default -> null;
        }
    );

    return (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[]{Block.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getWorld" -> world;
          case "getX" -> x;
          case "getY" -> y;
          case "getZ" -> z;
          case "getType" -> material;
          default -> null;
        }
    );
  }

  private Plugin mockPlugin() {
    return (Plugin) Proxy.newProxyInstance(
        Plugin.class.getClassLoader(),
        new Class<?>[]{Plugin.class},
        (proxy, method, args) -> null
    );
  }

  private World mockWorld(String worldName, AtomicInteger addedCount, AtomicInteger removedCount) {
    return (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> worldName;
          case "addPluginChunkTicket" -> {
            if (addedCount != null) addedCount.incrementAndGet();
            yield true;
          }
          case "removePluginChunkTicket" -> {
            if (removedCount != null) removedCount.incrementAndGet();
            yield true;
          }
          default -> null;
        }
    );
  }

  @Test
  void testClaimsActionCategorizationWithCompositeProtectionHook(@TempDir Path tempDir) {
    Path claimsFile = tempDir.resolve("claims.json");
    LandClaimRegistry registry = new LandClaimRegistry(claimsFile);

    // Forest claim allows only Ahmet to gather wood
    ClaimBoundary forestBoundary = new ClaimBoundary("world", 0, 50, 0, 50, 100, 50);
    LandClaim forestClaim = new LandClaim(
        "forest_zone",
        forestBoundary,
        "operator",
        Set.of("ahmet"),
        Set.of(ClaimAction.GATHER_WOOD),
        true
    );
    registry.addClaim(forestClaim);

    // Farm claim allows only Mehmet to harvest crops
    ClaimBoundary farmBoundary = new ClaimBoundary("world", 60, 50, 0, 100, 100, 50);
    LandClaim farmClaim = new LandClaim(
        "farm_zone",
        farmBoundary,
        "operator",
        Set.of("mehmet"),
        Set.of(ClaimAction.HARVEST_CROPS),
        true
    );
    registry.addClaim(farmClaim);

    // Replicate the hook lambda from AhmetPlugin.onEnable()
    CompositeProtectionHook hook = new CompositeProtectionHook();
    String[] currentActiveVillager = new String[]{"ahmet"};

    hook.addHook((block, loc) -> {
      if (registry == null) return false;
      ClaimAction action = ClaimAction.ALL;
      if (FarmCatalog.isCropBlock(block.getType().name())) {
        action = ClaimAction.HARVEST_CROPS;
      } else if (WoodMaterials.isCollectableWood(block.getType())) {
        action = ClaimAction.GATHER_WOOD;
      } else if (ObstructionPolicy.isLeaf(block.getType())) {
        action = ClaimAction.CLEAR_OBSTRUCTION;
      }
      return registry.isProtected(currentActiveVillager[0], block, action);
    });

    Block woodBlock = mockBlock("world", 10, 65, 10, Material.OAK_LOG);
    Block leafBlock = mockBlock("world", 10, 68, 10, Material.OAK_LEAVES);
    Block cropBlock = mockBlock("world", 70, 65, 10, Material.WHEAT);
    Block wildernessWood = mockBlock("world", 200, 65, 200, Material.OAK_LOG);

    // 1. With Ahmet active:
    currentActiveVillager[0] = "ahmet";
    assertFalse(hook.isProtected(woodBlock, null), "Wood gather in forest must be permitted for Ahmet");
    assertTrue(hook.isProtected(leafBlock, null), "Leaf clear in forest must be protected for Ahmet (CLEAR_OBSTRUCTION not permitted)");
    assertTrue(hook.isProtected(cropBlock, null), "Crop harvest in farm must be protected for Ahmet (only Mehmet permitted)");
    assertFalse(hook.isProtected(wildernessWood, null), "Wilderness must be unprotected");

    // 2. Switch to Mehmet:
    currentActiveVillager[0] = "mehmet";
    assertTrue(hook.isProtected(woodBlock, null), "Wood gather in forest must be protected for Mehmet");
    assertFalse(hook.isProtected(cropBlock, null), "Crop harvest in farm must be permitted for Mehmet");
    assertTrue(hook.isProtected(leafBlock, null), "Leaf clear in forest must be protected for Mehmet");

    // 3. Switch to an unauthorized third villager:
    currentActiveVillager[0] = "ali";
    assertTrue(hook.isProtected(woodBlock, null), "Unauthorized villager must be protected from wood in forest");
    assertTrue(hook.isProtected(cropBlock, null), "Unauthorized villager must be protected from crops in farm");
  }

  @Test
  void testExternalProtectionHookWithTaskOwner() {
    ExternalProtectionAdapter externalAdapter = new ExternalProtectionAdapter();
    String authorizedOwner = "owner-uuid-42";

    externalAdapter.setProviderOverride((block, loc, taskOwnerUuid) -> {
      return !authorizedOwner.equals(taskOwnerUuid);
    });

    CompositeProtectionHook hook = new CompositeProtectionHook();
    String[] activeTaskOwner = new String[]{null};

    hook.addHook((block, loc) -> {
      return externalAdapter.isProtected(block, loc, activeTaskOwner[0]);
    });

    Block block = mockBlock("world", 5, 5, 5, Material.STONE);

    // Without authorized task owner -> protected
    assertTrue(hook.isProtected(block, null));

    // With authorized task owner -> safe to mutate
    activeTaskOwner[0] = authorizedOwner;
    assertFalse(hook.isProtected(block, null));

    // With foreign task owner -> protected
    activeTaskOwner[0] = "foreign-uuid-99";
    assertTrue(hook.isProtected(block, null));
  }

  @Test
  void testChunkTicketLifecycleOnCancelAndComplete() {
    AtomicInteger added = new AtomicInteger();
    AtomicInteger removed = new AtomicInteger();
    World world = mockWorld("world", added, removed);
    Plugin plugin = mockPlugin();
    ChunkTicketManager manager = new ChunkTicketManager();

    // 1. Request chunk tickets during navigation/work for Ahmet
    assertTrue(manager.requestTicket(world, "ahmet", 10, 20, plugin));
    assertTrue(manager.requestTicket(world, "ahmet", 11, 20, plugin));
    assertEquals(2, manager.activeTicketCount("ahmet"));
    assertEquals(2, added.get());

    // 2. Simulate task cancel or completion releasing tickets for Ahmet
    manager.releaseTickets(world, "ahmet", plugin);
    assertEquals(0, manager.activeTicketCount("ahmet"));
    assertEquals(2, removed.get());

    // 3. Request tickets again for multiple villagers
    assertTrue(manager.requestTicket(world, "ahmet", 5, 5, plugin));
    assertTrue(manager.requestTicket(world, "mehmet", 6, 6, plugin));
    assertEquals(2, manager.totalTicketCount());

    // 4. Simulate onDisable() calling releaseAll
    manager.releaseAll(plugin);
    assertEquals(0, manager.totalTicketCount());
    assertEquals(0, manager.activeTicketCount("ahmet"));
    assertEquals(0, manager.activeTicketCount("mehmet"));
  }
}
