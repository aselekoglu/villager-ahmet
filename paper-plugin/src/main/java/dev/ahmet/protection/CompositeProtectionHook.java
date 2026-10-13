package dev.ahmet.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Composite protection hook that aggregates multiple protection rules.
 * Fails closed (returns true) if any delegate rejects the block or throws an unexpected error.
 */
public final class CompositeProtectionHook implements ProtectionHook {
  private static final Logger LOGGER = Logger.getLogger(CompositeProtectionHook.class.getName());
  private final List<ProtectionHook> hooks;

  public CompositeProtectionHook(List<ProtectionHook> hooks) {
    Objects.requireNonNull(hooks, "hooks list must not be null");
    this.hooks = new ArrayList<>(hooks);
  }

  public CompositeProtectionHook() {
    this.hooks = new ArrayList<>();
  }

  public synchronized void addHook(ProtectionHook hook) {
    Objects.requireNonNull(hook, "hook must not be null");
    hooks.add(hook);
  }

  @Override
  public synchronized boolean isProtected(Block block, Location actorLocation) {
    if (block == null) {
      return true; // Fail closed on null block
    }
    for (ProtectionHook hook : hooks) {
      try {
        if (hook.isProtected(block, actorLocation)) {
          return true;
        }
      } catch (Throwable t) {
        LOGGER.warning("Protection hook " + hook.getClass().getSimpleName() + " threw an error; failing closed: " + t.getMessage());
        return true; // Fail closed
      }
    }
    return false;
  }
}
