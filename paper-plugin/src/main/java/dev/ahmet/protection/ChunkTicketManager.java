package dev.ahmet.protection;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages bounded Paper plugin chunk tickets to prevent chunk loading leaks and runaway memory usage.
 */
public final class ChunkTicketManager {
  private static final Logger LOGGER = Logger.getLogger(ChunkTicketManager.class.getName());

  /**
   * Maximum concurrent plugin chunk tickets allowed per villager.
   */
  public static final int MAX_TICKETS_PER_VILLAGER = 9;

  public record ChunkKey(String worldName, int chunkX, int chunkZ) {}

  private final Map<String, Set<ChunkKey>> tickets = new ConcurrentHashMap<>();

  /**
   * Requests a bounded plugin chunk ticket for a specific villager.
   *
   * @param world the target world
   * @param villagerId the villager requesting navigation/work ticket
   * @param chunkX chunk X coordinate
   * @param chunkZ chunk Z coordinate
   * @param plugin the owning plugin
   * @return true if granted (or already held), false if budget exceeded or invalid input
   */
  public synchronized boolean requestTicket(World world, String villagerId, int chunkX, int chunkZ, Plugin plugin) {
    if (world == null || villagerId == null || plugin == null) {
      return false;
    }

    String vid = villagerId.trim().toLowerCase();
    Set<ChunkKey> villagerTickets = tickets.computeIfAbsent(vid, k -> new HashSet<>());
    ChunkKey key = new ChunkKey(world.getName(), chunkX, chunkZ);

    if (villagerTickets.contains(key)) {
      return true; // Already held
    }

    if (villagerTickets.size() >= MAX_TICKETS_PER_VILLAGER) {
      LOGGER.warning("Villager '" + vid + "' reached maximum active chunk tickets (" + MAX_TICKETS_PER_VILLAGER + ")");
      return false; // Bounded
    }

    try {
      boolean added = world.addPluginChunkTicket(chunkX, chunkZ, plugin);
      if (added) {
        villagerTickets.add(key);
        return true;
      }
      return false;
    } catch (Throwable t) {
      LOGGER.warning("Failed to add plugin chunk ticket for villager '" + vid + "' at " + key + ": " + t.getMessage());
      return false;
    }
  }

  /**
   * Releases all tickets held by the given villager in the specified world.
   */
  public synchronized void releaseTickets(World world, String villagerId, Plugin plugin) {
    if (world == null || villagerId == null || plugin == null) {
      return;
    }

    String vid = villagerId.trim().toLowerCase();
    Set<ChunkKey> villagerTickets = tickets.get(vid);
    if (villagerTickets == null || villagerTickets.isEmpty()) {
      return;
    }

    Iterator<ChunkKey> it = villagerTickets.iterator();
    while (it.hasNext()) {
      ChunkKey key = it.next();
      if (key.worldName().equals(world.getName())) {
        try {
          world.removePluginChunkTicket(key.chunkX(), key.chunkZ(), plugin);
        } catch (Throwable t) {
          LOGGER.warning("Failed to remove chunk ticket for villager '" + vid + "' at " + key + ": " + t.getMessage());
        }
        it.remove();
      }
    }

    if (villagerTickets.isEmpty()) {
      tickets.remove(vid);
    }
  }

  /**
   * Releases all chunk tickets across all villagers and worlds.
   */
  public synchronized void releaseAll(Plugin plugin) {
    if (plugin == null) {
      return;
    }

    for (Map.Entry<String, Set<ChunkKey>> entry : tickets.entrySet()) {
      String vid = entry.getKey();
      for (ChunkKey key : entry.getValue()) {
        try {
          World world = Bukkit.getWorld(key.worldName());
          if (world != null) {
            world.removePluginChunkTicket(key.chunkX(), key.chunkZ(), plugin);
          }
        } catch (Throwable t) {
          LOGGER.warning("Failed to release chunk ticket for villager '" + vid + "' at " + key + ": " + t.getMessage());
        }
      }
    }

    tickets.clear();
  }

  /**
   * Returns the number of active chunk tickets held by the specified villager.
   */
  public synchronized int activeTicketCount(String villagerId) {
    if (villagerId == null) {
      return 0;
    }
    Set<ChunkKey> set = tickets.get(villagerId.trim().toLowerCase());
    return set != null ? set.size() : 0;
  }

  /**
   * Returns the total active chunk tickets held across all villagers.
   */
  public synchronized int totalTicketCount() {
    return tickets.values().stream().mapToInt(Set::size).sum();
  }

  /**
   * Returns an unmodifiable view of active tickets for a villager.
   */
  public synchronized Set<ChunkKey> getActiveTickets(String villagerId) {
    if (villagerId == null) {
      return Collections.emptySet();
    }
    Set<ChunkKey> set = tickets.get(villagerId.trim().toLowerCase());
    return set != null ? Collections.unmodifiableSet(new HashSet<>(set)) : Collections.emptySet();
  }
}
