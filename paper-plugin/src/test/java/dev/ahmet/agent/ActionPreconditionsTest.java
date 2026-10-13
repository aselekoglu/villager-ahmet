package dev.ahmet.agent;

import dev.ahmet.protection.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
  void testClearObstructionBudgetPrecondition() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 10.0, 64.0, 10.0);
    Block leaf = mockBlock(world, Material.OAK_LEAVES, 10, 65, 10);

    ActionPreconditions pre = new ActionPreconditions((b, l) -> false, ActionBudget.DEFAULT);

    // Within budget
    ExecutionResult ok = pre.validateClear(actor, leaf, false, 0, 0);
    assertTrue(ok.isSuccess());

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
}
