package dev.ahmet.agent;

import dev.ahmet.protection.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

public class ActionPreconditionsTest {

  private World mockWorld(String name, boolean chunkLoaded) {
    return (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> name;
          case "isChunkLoaded" -> chunkLoaded;
          case "getMinHeight" -> -64;
          case "getMaxHeight" -> 320;
          default -> null;
        }
    );
  }

  private Block mockBlock(World world, Material material, int x, int y, int z) {
    return (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[]{Block.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getWorld" -> world;
          case "getType" -> material;
          case "getX" -> x;
          case "getY" -> y;
          case "getZ" -> z;
          case "isSolid" -> material.isSolid();
          case "isLiquid" -> false;
          case "isPassable" -> !material.isSolid();
          default -> null;
        }
    );
  }

  private Block mockChestBlock(World world, int x, int y, int z) {
    Chest chestState = (Chest) Proxy.newProxyInstance(
        Chest.class.getClassLoader(),
        new Class<?>[]{Chest.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getWorld" -> world;
          default -> null;
        }
    );
    return (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[]{Block.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getWorld" -> world;
          case "getType" -> Material.CHEST;
          case "getX" -> x;
          case "getY" -> y;
          case "getZ" -> z;
          case "getState" -> chestState;
          default -> null;
        }
    );
  }

  @Test
  void testGatherPreconditionsValidation() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 10.0, 64.0, 10.0);
    Block closeOak = mockBlock(world, Material.OAK_LOG, 11, 64, 10);
    Block farOak = mockBlock(world, Material.OAK_LOG, 20, 64, 10);

    ProtectionHook allowAll = (b, loc) -> false;
    ProtectionHook blockAll = (b, loc) -> true;

    ActionPreconditions pre = new ActionPreconditions(allowAll, ActionBudget.DEFAULT);

    // Success case
    ExecutionResult res = pre.validateGather(actor, closeOak, "oak_log", 10);
    assertTrue(res.isSuccess());

    // Out of reach
    ExecutionResult farRes = pre.validateGather(actor, farOak, "oak_log", 10);
    assertFalse(farRes.isSuccess());
    assertTrue(farRes.message().contains("out of reach"));

    // Full cargo
    ExecutionResult fullCargo = pre.validateGather(actor, closeOak, "oak_log", 64);
    assertFalse(fullCargo.isSuccess());
    assertTrue(fullCargo.message().contains("Cargo capacity reached"));

    // Protected block
    ActionPreconditions preProtected = new ActionPreconditions(blockAll, ActionBudget.DEFAULT);
    ExecutionResult protectedRes = preProtected.validateGather(actor, closeOak, "oak_log", 10);
    assertFalse(protectedRes.isSuccess());
    assertTrue(protectedRes.message().contains("protected"));

    // Unloaded chunk
    World unloadedWorld = mockWorld("world", false);
    Block unloadedBlock = mockBlock(unloadedWorld, Material.OAK_LOG, 11, 64, 10);
    Location actorUnloaded = new Location(unloadedWorld, 10.0, 64.0, 10.0);
    ExecutionResult unloadedRes = pre.validateGather(actorUnloaded, unloadedBlock, "oak_log", 10);
    assertFalse(unloadedRes.isSuccess());
    assertTrue(unloadedRes.message().contains("chunk is not loaded"));
  }

  @Test
  void gatherPreferenceMatchesWoodAndResourceCapabilitiesExactly() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 10.0, 64.0, 10.0);
    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);

    assertTrue(pre.validateGather(actor, mockBlock(world, Material.OAK_LOG, 10, 64, 10), "any", 0).isSuccess());
    assertTrue(pre.validateGather(actor, mockBlock(world, Material.BIRCH_LOG, 10, 64, 10), "any", 0).isSuccess());
    assertTrue(pre.validateGather(actor, mockBlock(world, Material.OAK_LOG, 10, 64, 10), "oak", 0).isSuccess());
    assertFalse(pre.validateGather(actor, mockBlock(world, Material.BIRCH_LOG, 10, 64, 10), "oak", 0).isSuccess());
    assertTrue(pre.validateGather(actor, mockBlock(world, Material.DIRT, 10, 64, 10), "item:DIRT", 0).isSuccess());
    assertFalse(pre.validateGather(actor, mockBlock(world, Material.OAK_LOG, 10, 64, 10), "item:DIRT", 0).isSuccess());
  }


  @Test
  void clearRecomputesSupportRelationshipAndAlwaysSimulatesCurrentSupport() {
    World world=mockWorld("world",true);
    Location actor=new Location(world,10.2,64,10.2);
    Block support=mockBlock(world,Material.OAK_LEAVES,10,63,10);
    var safeCalls=new java.util.concurrent.atomic.AtomicInteger();
    ActionPreconditions safePre=new ActionPreconditions((b,l)->false,ActionBudget.DEFAULT,loc->{safeCalls.incrementAndGet();return true;});
    assertTrue(safePre.validateClear(actor,support,true,0,0).isSuccess(), "Current safe support may be cleared");
    assertEquals(1,safeCalls.get());

    var unsafeCalls=new java.util.concurrent.atomic.AtomicInteger();
    ActionPreconditions unsafePre=new ActionPreconditions((b,l)->false,ActionBudget.DEFAULT,loc->{unsafeCalls.incrementAndGet();return false;});
    assertFalse(unsafePre.validateClear(actor,support,true,0,0).isSuccess(), "Unsafe current support must be rejected");
    assertFalse(unsafePre.validateClear(actor,support,false,0,0).isSuccess(),
        "A stale false offer flag must not bypass current support simulation");
    assertEquals(2,unsafeCalls.get());

    Block sideLeaf=mockBlock(world,Material.OAK_LEAVES,10,65,10);
    var sideCalls=new java.util.concurrent.atomic.AtomicInteger();
    ActionPreconditions sidePre=new ActionPreconditions((b,l)->false,ActionBudget.DEFAULT,loc->{sideCalls.incrementAndGet();return true;});
    assertFalse(sidePre.validateClear(actor,sideLeaf,true,0,0).isSuccess(),
        "A stale true support offer must be rejected after it becomes a side leaf");
    assertEquals(0,sideCalls.get(), "Current side leaves must not run descent simulation");
  }

  @Test
  void testClearObstructionBudgetPrecondition() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 10.0, 64.0, 10.0);
    Block leaf = mockBlock(world, Material.OAK_LEAVES, 10, 65, 10);

    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);

    // Within budget
    ExecutionResult ok = pre.validateClear(actor, leaf, false, 0, 0);
    assertTrue(ok.isSuccess());
    assertFalse(pre.validateClear(actor, mockBlock(world, Material.OAK_LOG, 10, 65, 10), false, 0, 0).isSuccess(),
        "Unknown/non-leaf obstructions must fail closed");
    ActionPreconditions protectedPre = new ActionPreconditions((b, l) -> true, ActionBudget.DEFAULT);
    assertFalse(protectedPre.validateClear(actor, leaf, false, 0, 0).isSuccess(),
        "Protected leaves must fail closed");

    // Route budget exhausted (max 3)
    ExecutionResult routeExhausted = pre.validateClear(actor, leaf, false, 3, 0);
    assertFalse(routeExhausted.isSuccess());
    assertTrue(routeExhausted.message().contains("budget exceeded"));

    // Job budget exhausted (max 12)
    ExecutionResult jobExhausted = pre.validateClear(actor, leaf, false, 1, 12);
    assertFalse(jobExhausted.isSuccess());
    assertTrue(jobExhausted.message().contains("budget exceeded"));
  }

  @Test
  void testHelpRequestCooldown() {
    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);

    ExecutionResult ok = pre.validateHelpRequest(0L, 30_000L);
    assertTrue(ok.isSuccess());

    ExecutionResult onCooldown = pre.validateHelpRequest(10_000L, 20_000L);
    assertFalse(onCooldown.isSuccess());
    assertTrue(onCooldown.message().contains("cooldown active"));
  }

  @Test
  void testCraftPreconditionsValidation() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 10.0, 64.0, 10.0);
    Block closeChest = mockChestBlock(world, 11, 64, 10);
    Block farChest = mockChestBlock(world, 20, 64, 10);

    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);

    // Null actor or null chest
    assertFalse(pre.validateCraft(null, closeChest).isSuccess());
    assertFalse(pre.validateCraft(actor, null).isSuccess());

    // Different worlds
    World otherWorld = mockWorld("other_world", true);
    Block otherWorldChest = mockChestBlock(otherWorld, 11, 64, 10);
    ExecutionResult diffWorldRes = pre.validateCraft(actor, otherWorldChest);
    assertFalse(diffWorldRes.isSuccess());
    assertTrue(diffWorldRes.message().contains("different or invalid world"));

    // Null world on actor
    Location noWorldActor = new Location(null, 10.0, 64.0, 10.0);
    assertFalse(pre.validateCraft(noWorldActor, closeChest).isSuccess());

    // Unloaded chunk
    World unloadedWorld = mockWorld("world", false);
    Block unloadedChest = mockChestBlock(unloadedWorld, 11, 64, 10);
    Location unloadedActor = new Location(unloadedWorld, 10.0, 64.0, 10.0);
    ExecutionResult unloadedRes = pre.validateCraft(unloadedActor, unloadedChest);
    assertFalse(unloadedRes.isSuccess());
    assertTrue(unloadedRes.message().contains("chunk is not loaded"));

    // Bound block is not a chest (e.g. OAK_LOG block)
    Block nonChest = mockBlock(world, Material.OAK_LOG, 11, 64, 10);
    ExecutionResult nonChestRes = pre.validateCraft(actor, nonChest);
    assertFalse(nonChestRes.isSuccess());
    assertTrue(nonChestRes.message().contains("no longer a chest"));

    // Chest out of reach
    ExecutionResult farRes = pre.validateCraft(actor, farChest);
    assertFalse(farRes.isSuccess());
    assertTrue(farRes.message().contains("out of reach"));

    // Success case
    ExecutionResult okRes = pre.validateCraft(actor, closeChest);
    assertTrue(okRes.isSuccess());
    assertTrue(okRes.message().contains("Craft preconditions satisfied"));
  }
}
