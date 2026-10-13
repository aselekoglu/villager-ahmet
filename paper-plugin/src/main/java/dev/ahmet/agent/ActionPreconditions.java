package dev.ahmet.agent;

import dev.ahmet.DescentSimulator;
import dev.ahmet.ObstructionPolicy;
import dev.ahmet.ResourceCatalog;
import dev.ahmet.protection.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;

import java.util.Objects;

/**
 * Deterministic server main-thread precondition validator for agent actions.
 * Guarantees that actions only proceed when in reach, in loaded chunks, within budget,
 * unprotected, and physically safe.
 */
public final class ActionPreconditions {
  public static final double MAX_REACH_DISTANCE = 3.5;
  public static final double MAX_REACH_SQ = MAX_REACH_DISTANCE * MAX_REACH_DISTANCE;

  private final ProtectionHook protectionHook;
  private final ActionBudget budget;

  public ActionPreconditions(ProtectionHook protectionHook, ActionBudget budget) {
    this.protectionHook = Objects.requireNonNull(protectionHook, "protectionHook must not be null");
    this.budget = Objects.requireNonNull(budget, "budget must not be null");
  }

  public ActionBudget budget() {
    return budget;
  }

  public ProtectionHook protectionHook() {
    return protectionHook;
  }

  public ExecutionResult validateGather(Location actor, Block block, String materialPreference, int currentCargo) {
    if (actor == null || block == null) {
      return ExecutionResult.preconditionFailed("Actor location or target block is null");
    }
    if (actor.getWorld() == null || block.getWorld() == null
        || !actor.getWorld().getName().equals(block.getWorld().getName())) {
      return ExecutionResult.preconditionFailed("Actor and target are in different or invalid worlds");
    }
    if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
      return ExecutionResult.preconditionFailed("Target chunk is not loaded");
    }
    if (currentCargo >= 64) {
      return ExecutionResult.preconditionFailed("Cargo capacity reached maximum (64 items)");
    }
    double distSq = distanceSquared(actor, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    if (distSq > MAX_REACH_SQ) {
      return ExecutionResult.preconditionFailed("Target is out of reach (" + Math.sqrt(distSq) + " > " + MAX_REACH_DISTANCE + ")");
    }
    if (protectionHook.isProtected(block, actor)) {
      return ExecutionResult.preconditionFailed("Target block is protected against modification");
    }
    if (materialPreference != null && !materialPreference.isBlank()) {
      String expectedOutput = ResourceCatalog.outputMaterial(materialPreference);
      if (expectedOutput == null && !block.getType().name().equalsIgnoreCase(materialPreference)) {
        return ExecutionResult.preconditionFailed("Target block does not match requested preference: " + materialPreference);
      }
    }
    return ExecutionResult.success("Gather preconditions satisfied");
  }

  public ExecutionResult validateClear(Location actor, Block block, boolean isSupportLeaf, int routeClears, int jobClears) {
    if (actor == null || block == null) {
      return ExecutionResult.preconditionFailed("Actor location or block is null");
    }
    if (actor.getWorld() == null || block.getWorld() == null
        || !actor.getWorld().getName().equals(block.getWorld().getName())) {
      return ExecutionResult.preconditionFailed("Actor and obstruction are in different or invalid worlds");
    }
    if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
      return ExecutionResult.preconditionFailed("Obstruction chunk is not loaded");
    }
    if (!budget.canClearObstruction(routeClears, jobClears)) {
      return ExecutionResult.preconditionFailed("Obstruction clear budget exceeded (route=" + routeClears + "/" + budget.maxRouteClears()
          + ", job=" + jobClears + "/" + budget.maxJobClears() + ")");
    }
    double distSq = distanceSquared(actor, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    if (distSq > MAX_REACH_SQ) {
      return ExecutionResult.preconditionFailed("Obstruction is out of reach");
    }
    if (!ObstructionPolicy.isLeaf(block.getType())) {
      return ExecutionResult.preconditionFailed("Only natural leaf obstructions may be cleared");
    }
    if (protectionHook.isProtected(block, actor)) {
      return ExecutionResult.preconditionFailed("Obstruction is protected");
    }
    if (isSupportLeaf && !DescentSimulator.isSafeDescent(block.getWorld(), actor.getBlockX(), actor.getBlockY(), actor.getBlockZ())) {
      return ExecutionResult.preconditionFailed("Support leaf descent simulation failed: unsafe drop");
    }
    return ExecutionResult.success("Clear preconditions satisfied");
  }

  public ExecutionResult validateDeposit(Location actor, Block chestBlock, int carriedCargo) {
    if (actor == null || chestBlock == null) {
      return ExecutionResult.preconditionFailed("Actor or chest block is null");
    }
    if (actor.getWorld() == null || chestBlock.getWorld() == null
        || !actor.getWorld().getName().equals(chestBlock.getWorld().getName())) {
      return ExecutionResult.preconditionFailed("Chest is in a different or invalid world");
    }
    if (!chestBlock.getWorld().isChunkLoaded(chestBlock.getX() >> 4, chestBlock.getZ() >> 4)) {
      return ExecutionResult.preconditionFailed("Chest chunk is not loaded");
    }
    if (!(chestBlock.getState() instanceof Chest)) {
      return ExecutionResult.preconditionFailed("Bound block is no longer a chest");
    }
    if (carriedCargo <= 0) {
      return ExecutionResult.preconditionFailed("No cargo carried to deposit");
    }
    double distSq = distanceSquared(actor, chestBlock.getX() + 0.5, chestBlock.getY() + 0.5, chestBlock.getZ() + 0.5);
    if (distSq > MAX_REACH_SQ) {
      return ExecutionResult.preconditionFailed("Chest is out of reach");
    }
    return ExecutionResult.success("Deposit preconditions satisfied");
  }

  public ExecutionResult validateHelpRequest(long lastHelpMs, long nowMs) {
    if (!budget.canRequestHelp(lastHelpMs, nowMs)) {
      long remainingSeconds = (budget.helpCooldownMs() - (nowMs - lastHelpMs)) / 1000;
      return ExecutionResult.preconditionFailed("Help request cooldown active (" + remainingSeconds + "s remaining)");
    }
    return ExecutionResult.success("Help request precondition satisfied");
  }

  private static double distanceSquared(Location loc, double x, double y, double z) {
    double dx = loc.getX() - x;
    double dy = loc.getY() - y;
    double dz = loc.getZ() - z;
    return dx * dx + dy * dy + dz * dz;
  }
}
