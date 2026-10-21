package dev.ahmet.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Dynamic reflection adapter for external claim/protection plugins (e.g. GriefPrevention, WorldGuard).
 * Fails closed (returns true) on reflection errors or provider exceptions, and falls back gracefully
 * (returns false) when no external protection plugins are detected.
 */
public final class ExternalProtectionAdapter implements ProtectionHook {
  private static final Logger LOGGER = Logger.getLogger(ExternalProtectionAdapter.class.getName());

  /**
   * Pluggable provider interface for testing and extension without requiring heavy plugin dependencies.
   */
  @FunctionalInterface
  public interface ExternalProvider {
    boolean isProtected(Block block, Location actorLocation, String taskOwnerUuid);
  }

  private volatile ExternalProvider providerOverride;
  private volatile String defaultTaskOwnerUuid;

  public ExternalProtectionAdapter() {
    this(null, null);
  }

  public ExternalProtectionAdapter(ExternalProvider providerOverride) {
    this(providerOverride, null);
  }

  public ExternalProtectionAdapter(ExternalProvider providerOverride, String defaultTaskOwnerUuid) {
    this.providerOverride = providerOverride;
    this.defaultTaskOwnerUuid = defaultTaskOwnerUuid;
  }

  public void setProviderOverride(ExternalProvider providerOverride) {
    this.providerOverride = providerOverride;
  }

  public ExternalProvider getProviderOverride() {
    return providerOverride;
  }

  public void setDefaultTaskOwnerUuid(String defaultTaskOwnerUuid) {
    this.defaultTaskOwnerUuid = defaultTaskOwnerUuid;
  }

  public String getDefaultTaskOwnerUuid() {
    return defaultTaskOwnerUuid;
  }

  @Override
  public boolean isProtected(Block block, Location actorLocation) {
    return isProtected(block, actorLocation, defaultTaskOwnerUuid);
  }

  /**
   * Evaluates protection against a specific task owner UUID.
   */
  public boolean isProtected(Block block, Location actorLocation, String taskOwnerUuid) {
    if (block == null) {
      return true; // Fail closed on null block
    }

    if (providerOverride != null) {
      try {
        return providerOverride.isProtected(block, actorLocation, taskOwnerUuid);
      } catch (Throwable t) {
        LOGGER.warning("External provider override threw an exception; failing closed: " + t.getMessage());
        return true;
      }
    }

    PluginManager pm;
    try {
      pm = Bukkit.getPluginManager();
    } catch (Throwable t) {
      // Bukkit may not be initialized in non-server tests without provider override
      return false;
    }
    if (pm == null) {
      return false;
    }

    // 1. Check GriefPrevention
    try {
      Plugin gp = pm.getPlugin("GriefPrevention");
      if (gp != null && gp.isEnabled()) {
        if (checkGriefPrevention(block, taskOwnerUuid)) {
          return true; // Protected by GriefPrevention
        }
      }
    } catch (Throwable t) {
      LOGGER.warning("GriefPrevention reflection check failed; failing closed: " + t.getMessage());
      return true;
    }

    // 2. Check WorldGuard
    try {
      Plugin wg = pm.getPlugin("WorldGuard");
      if (wg != null && wg.isEnabled()) {
        if (checkWorldGuard(block, taskOwnerUuid)) {
          return true; // Protected by WorldGuard
        }
      }
    } catch (Throwable t) {
      LOGGER.warning("WorldGuard reflection check failed; failing closed: " + t.getMessage());
      return true;
    }

    return false; // Graceful fallback: no protection plugin detected
  }

  private static String normalizeUuid(String uuid) {
    return uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase();
  }

  private boolean checkGriefPrevention(Block block, String taskOwnerUuid) throws Exception {
    Location loc = block.getLocation();
    Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
    Field instanceField = gpClass.getField("instance");
    Object instance = instanceField.get(null);
    Field dataStoreField = gpClass.getField("dataStore");
    Object dataStore = dataStoreField.get(instance);

    Method getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Class.forName("me.ryanhamshire.GriefPrevention.Claim"));
    Object claim = getClaimAt.invoke(dataStore, loc, false, null);
    if (claim == null) {
      return false; // Unclaimed wilderness
    }

    // Inspect owner
    String claimOwner = null;
    try {
      Field ownerIdField = claim.getClass().getField("ownerID");
      Object ownerId = ownerIdField.get(claim);
      if (ownerId != null) {
        claimOwner = ownerId.toString();
      }
    } catch (NoSuchFieldException ignored) {}

    if (claimOwner == null) {
      try {
        Method getOwnerName = claim.getClass().getMethod("getOwnerName");
        Object name = getOwnerName.invoke(claim);
        if (name != null) {
          claimOwner = name.toString();
        }
      } catch (NoSuchMethodException ignored) {}
    }

    if (taskOwnerUuid != null && claimOwner != null && normalizeUuid(taskOwnerUuid).equals(normalizeUuid(claimOwner))) {
      return false; // Owned by task owner
    }

    // Foreign claim or claim without authorization
    return true;
  }

  private boolean checkWorldGuard(Block block, String taskOwnerUuid) throws Exception {
    Location loc = block.getLocation();
    Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
    Method getInstance = wgClass.getMethod("getInstance");
    Object wgInstance = getInstance.invoke(null);

    Method getPlatform = wgInstance.getClass().getMethod("getPlatform");
    Object platform = getPlatform.invoke(wgInstance);

    Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
    Object container = getRegionContainer.invoke(platform);

    Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
    Method adaptLocation = bukkitAdapter.getMethod("adapt", Location.class);
    Object wgLoc = adaptLocation.invoke(null, loc);

    Method createQuery = container.getClass().getMethod("createQuery");
    Object query = createQuery.invoke(container);

    Method getApplicableRegions = query.getClass().getMethod("getApplicableRegions", Class.forName("com.sk89q.worldedit.util.Location"));
    Object applicableRegions = getApplicableRegions.invoke(query, wgLoc);

    Method sizeMethod = applicableRegions.getClass().getMethod("size");
    int count = (Integer) sizeMethod.invoke(applicableRegions);
    if (count > 0) {
      return true; // In protected WorldGuard region
    }
    return false;
  }
}
