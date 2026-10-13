package dev.ahmet.agent;

import dev.ahmet.protection.CompositeProtectionHook;
import dev.ahmet.protection.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replayable evaluation harness testing the Agent Loop under controlled observations,
 * mock JEV model responses, and deterministic server execution scenarios.
 * Explicitly separates model selection from server validation and execution outcome.
 */
public class AgentEvaluationHarnessTest {

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

  record ModelChoiceRecord(String id, String source, String model, long generation) {}
  record ServerExecutionRecord(boolean executed, String candidateId, ExecutionResult result, AgentState finalState) {}

  @Test
  void testScenario1_HappyPathGatherExecution() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 10.0, 64.0, 10.0);
    Block blockA = mockBlock(world, Material.OAK_LOG, 11, 64, 10);
    Block blockB = mockBlock(world, Material.BIRCH_LOG, 12, 64, 10);

    CompositeProtectionHook hook = new CompositeProtectionHook();
    ActionPreconditions pre = new ActionPreconditions(hook, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    long gen = controller.nextGeneration();
    OfferedAction actA = OfferedAction.of("target_a", ActionType.GATHER_TARGET, "Collect oak", blockA);
    OfferedAction actB = OfferedAction.of("target_b", ActionType.GATHER_TARGET, "Collect birch", blockB);
    OfferedAction wait = OfferedAction.of("wait", ActionType.WAIT, "Wait");
    controller.registerOfferedActions(List.of(actA, actB, wait));

    // Simulated JEV choice
    ModelChoiceRecord modelChoice = new ModelChoiceRecord("target_a", "jev", "jev-latest", gen);

    // Server decision validation
    OfferedAction validated = controller.validateDecision(modelChoice.id(), modelChoice.source(), modelChoice.generation());
    assertNotNull(validated, "Valid JEV choice must be accepted by server");
    assertEquals("target_a", validated.id());

    // Deterministic server precondition validation
    Block targetBlock = (Block) validated.targetData();
    ExecutionResult execResult = controller.preconditions().validateGather(actor, targetBlock, "oak_log", 2);
    assertTrue(execResult.isSuccess(), "Preconditions must succeed for valid target");

    // Server execution audit
    controller.transitionTo(AgentState.EXECUTING, "Harvesting " + targetBlock.getType());
    ServerExecutionRecord serverRecord = new ServerExecutionRecord(true, validated.id(), execResult, controller.state());

    assertEquals("target_a", modelChoice.id());
    assertEquals("target_a", serverRecord.candidateId());
    assertTrue(serverRecord.executed());
    assertEquals(AgentState.EXECUTING, serverRecord.finalState());
  }

  @Test
  void testScenario2_InvalidActionIdFromModel() {
    CompositeProtectionHook hook = new CompositeProtectionHook();
    ActionPreconditions pre = new ActionPreconditions(hook, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    long gen = controller.nextGeneration();
    controller.registerOfferedActions(List.of(
        OfferedAction.of("target_a", ActionType.GATHER_TARGET, "Collect oak"),
        OfferedAction.of("wait", ActionType.WAIT, "Wait")
    ));

    // JEV hallucinates an unoffered action ID
    ModelChoiceRecord modelChoice = new ModelChoiceRecord("target_z_arbitrary", "jev", "jev-latest", gen);

    OfferedAction validated = controller.validateDecision(modelChoice.id(), modelChoice.source(), modelChoice.generation());
    assertNull(validated, "Server must reject unoffered action ID");

    // Server does NOT execute any mutation; transitions to planning/retry
    controller.transitionTo(AgentState.PLANNING, "Retry after invalid model choice");
    ServerExecutionRecord serverRecord = new ServerExecutionRecord(false, null, ExecutionResult.preconditionFailed("Unoffered ID"), controller.state());

    assertFalse(serverRecord.executed());
    assertNull(serverRecord.candidateId());
    assertEquals(AgentState.PLANNING, serverRecord.finalState());
  }

  @Test
  void testScenario3_StaleGenerationDiscarded() {
    CompositeProtectionHook hook = new CompositeProtectionHook();
    ActionPreconditions pre = new ActionPreconditions(hook, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    long gen1 = controller.nextGeneration();
    controller.registerOfferedActions(List.of(OfferedAction.of("target_a", ActionType.GATHER_TARGET, "Collect oak")));

    // Job is cancelled or new target scanned, incrementing generation
    long gen2 = controller.nextGeneration();
    controller.registerOfferedActions(List.of(OfferedAction.of("target_b", ActionType.GATHER_TARGET, "Collect birch")));

    // Late response arrives with gen1
    ModelChoiceRecord lateResponse = new ModelChoiceRecord("target_a", "jev", "jev-latest", gen1);

    OfferedAction validated = controller.validateDecision(lateResponse.id(), lateResponse.source(), lateResponse.generation());
    assertNull(validated, "Server must discard stale generation response");
  }

  @Test
  void testScenario4_DecisionFallbackRejectsClearMutation() {
    World world = mockWorld("world", true);
    Block leaf = mockBlock(world, Material.OAK_LEAVES, 10, 65, 10);

    CompositeProtectionHook hook = new CompositeProtectionHook();
    ActionPreconditions pre = new ActionPreconditions(hook, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    long gen = controller.nextGeneration();
    OfferedAction clearAct = OfferedAction.of("clear_a", ActionType.CLEAR_OBSTRUCTION, "Clear leaf", leaf);
    OfferedAction retryAct = OfferedAction.of("retry_route", ActionType.RETRY_ROUTE, "Retry");
    controller.registerOfferedActions(List.of(clearAct, retryAct));

    // Decision timeout/fallback returns clear_a with source "fallback"
    ModelChoiceRecord fallbackChoice = new ModelChoiceRecord("clear_a", "fallback", "none", gen);

    OfferedAction validated = controller.validateDecision(fallbackChoice.id(), fallbackChoice.source(), fallbackChoice.generation());
    assertNull(validated, "Security Invariant: clear mutation MUST be rejected when source is fallback or non-JEV");

    // Server falls back safely to route retry without mutating world
    controller.transitionTo(AgentState.NAVIGATING, "Safe retry route");
    ServerExecutionRecord serverRecord = new ServerExecutionRecord(false, null, ExecutionResult.retryNeeded("Fallback cannot mutate"), controller.state());

    assertFalse(serverRecord.executed());
    assertEquals(AgentState.NAVIGATING, serverRecord.finalState());
  }

  @Test
  void testScenario5_ProtectionBoundaryFailsClosed() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 10.0, 64.0, 10.0);
    Block protectedLog = mockBlock(world, Material.OAK_LOG, 11, 64, 10);

    // Protection hook rejects this block (e.g. inside player home area)
    ProtectionHook homeProtection = (b, loc) -> b == protectedLog;
    CompositeProtectionHook composite = new CompositeProtectionHook(List.of(homeProtection));
    ActionPreconditions pre = new ActionPreconditions(composite, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    long gen = controller.nextGeneration();
    OfferedAction act = OfferedAction.of("target_a", ActionType.GATHER_TARGET, "Collect oak", protectedLog);
    controller.registerOfferedActions(List.of(act));

    ModelChoiceRecord choice = new ModelChoiceRecord("target_a", "jev", "jev-latest", gen);
    OfferedAction validated = controller.validateDecision(choice.id(), choice.source(), choice.generation());
    assertNotNull(validated);

    // Precondition check on server main thread
    ExecutionResult res = controller.preconditions().validateGather(actor, protectedLog, "oak_log", 0);
    assertFalse(res.isSuccess(), "Protected block must fail preconditions");
    assertTrue(res.message().contains("protected"));
  }

  @Test
  void testScenario6_FullDeliveryChestRetainsCargo() {
    World world = mockWorld("world", true);
    Location actor = new Location(world, 5.0, 64.0, 5.0);

    // Mock non-chest block
    Block notAChest = mockBlock(world, Material.STONE, 5, 64, 6);

    CompositeProtectionHook hook = new CompositeProtectionHook();
    ActionPreconditions pre = new ActionPreconditions(hook, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    // Deposit validation fails if chest is broken or missing
    ExecutionResult res = controller.preconditions().validateDeposit(actor, notAChest, 10);
    assertFalse(res.isSuccess());
    assertTrue(res.message().contains("no longer a chest"));

    // Validation fails if carried cargo is 0
    ExecutionResult emptyCargo = controller.preconditions().validateDeposit(actor, notAChest, 0);
    assertFalse(emptyCargo.isSuccess());
  }

  @Test
  void testScenario7_HelpRequestWhenStuckInCanopy() {
    CompositeProtectionHook hook = new CompositeProtectionHook();
    ActionPreconditions pre = new ActionPreconditions(hook, ActionBudget.DEFAULT);
    AgentController controller = new AgentController(pre);

    long now = 100_000L;
    boolean requested = controller.recordHelpRequest(now);
    assertTrue(requested, "Help request should be accepted on first request");
    assertEquals(AgentState.BLOCKED_AWAITING_HELP, controller.state());

    // Throttled within 30-second cooldown
    assertFalse(controller.recordHelpRequest(now + 10_000L), "Must be throttled within cooldown");
    assertEquals(AgentState.BLOCKED_AWAITING_HELP, controller.state());

    // Allowed after cooldown expires (30s)
    assertTrue(controller.recordHelpRequest(now + 31_000L));
  }
}
