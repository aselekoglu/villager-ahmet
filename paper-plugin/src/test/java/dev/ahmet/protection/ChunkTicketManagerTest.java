package dev.ahmet.protection;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkTicketManagerTest {

  private World mockWorld(String worldName, AtomicInteger addedCount, AtomicInteger removedCount) {
    return (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> worldName;
          case "addPluginChunkTicket" -> {
            if (addedCount != null) {
              addedCount.incrementAndGet();
            }
            yield true;
          }
          case "removePluginChunkTicket" -> {
            if (removedCount != null) {
              removedCount.incrementAndGet();
            }
            yield true;
          }
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

  @Test
  void testBudgetEnforcement() {
    AtomicInteger added = new AtomicInteger();
    World world = mockWorld("world", added, null);
    Plugin plugin = mockPlugin();
    ChunkTicketManager manager = new ChunkTicketManager();

    // Up to 9 tickets allowed
    for (int i = 0; i < ChunkTicketManager.MAX_TICKETS_PER_VILLAGER; i++) {
      assertTrue(manager.requestTicket(world, "ahmet", i, 0, plugin));
    }

    assertEquals(9, manager.activeTicketCount("ahmet"));
    assertEquals(9, added.get());

    // 10th ticket must be rejected by budget guard
    assertFalse(manager.requestTicket(world, "ahmet", 10, 0, plugin));
    assertEquals(9, manager.activeTicketCount("ahmet"));
    assertEquals(9, added.get());

    // Different villager has its own isolated budget
    assertTrue(manager.requestTicket(world, "mehmet", 0, 0, plugin));
    assertEquals(1, manager.activeTicketCount("mehmet"));
  }

  @Test
  void testDuplicateRequestIdempotency() {
    AtomicInteger added = new AtomicInteger();
    World world = mockWorld("world", added, null);
    Plugin plugin = mockPlugin();
    ChunkTicketManager manager = new ChunkTicketManager();

    // First request grants ticket
    assertTrue(manager.requestTicket(world, "ahmet", 5, 10, plugin));
    assertEquals(1, added.get());
    assertEquals(1, manager.activeTicketCount("ahmet"));

    // Duplicate request returns true immediately without re-invoking world or consuming budget
    assertTrue(manager.requestTicket(world, "ahmet", 5, 10, plugin));
    assertEquals(1, added.get());
    assertEquals(1, manager.activeTicketCount("ahmet"));
  }

  @Test
  void testReleaseTicketsPerVillager() {
    AtomicInteger added = new AtomicInteger();
    AtomicInteger removed = new AtomicInteger();
    World world = mockWorld("world", added, removed);
    Plugin plugin = mockPlugin();
    ChunkTicketManager manager = new ChunkTicketManager();

    // Request 3 tickets
    manager.requestTicket(world, "ahmet", 1, 1, plugin);
    manager.requestTicket(world, "ahmet", 1, 2, plugin);
    manager.requestTicket(world, "ahmet", 1, 3, plugin);
    assertEquals(3, manager.activeTicketCount("ahmet"));

    // Release all tickets for Ahmet in world
    manager.releaseTickets(world, "ahmet", plugin);
    assertEquals(0, manager.activeTicketCount("ahmet"));
    assertEquals(3, removed.get());

    // Ahmet can now request new tickets within budget
    assertTrue(manager.requestTicket(world, "ahmet", 2, 2, plugin));
    assertEquals(1, manager.activeTicketCount("ahmet"));
  }

  @Test
  void testReleaseAll() {
    AtomicInteger removed = new AtomicInteger();
    World world = mockWorld("world", null, removed);
    Plugin plugin = mockPlugin();
    ChunkTicketManager manager = new ChunkTicketManager();

    manager.requestTicket(world, "ahmet", 1, 1, plugin);
    manager.requestTicket(world, "mehmet", 2, 2, plugin);
    assertEquals(2, manager.totalTicketCount());

    manager.releaseAll(plugin);
    assertEquals(0, manager.totalTicketCount());
    assertEquals(0, manager.activeTicketCount("ahmet"));
    assertEquals(0, manager.activeTicketCount("mehmet"));
  }

  @Test
  void testNullParametersHandledGracefully() {
    ChunkTicketManager manager = new ChunkTicketManager();
    Plugin plugin = mockPlugin();
    World world = mockWorld("world", null, null);

    assertFalse(manager.requestTicket(null, "ahmet", 0, 0, plugin));
    assertFalse(manager.requestTicket(world, null, 0, 0, plugin));
    assertFalse(manager.requestTicket(world, "ahmet", 0, 0, null));

    assertDoesNotThrow(() -> manager.releaseTickets(null, "ahmet", plugin));
    assertDoesNotThrow(() -> manager.releaseTickets(world, null, plugin));
    assertDoesNotThrow(() -> manager.releaseTickets(world, "ahmet", null));
    assertDoesNotThrow(() -> manager.releaseAll(null));

    assertEquals(0, manager.activeTicketCount(null));
  }
}
