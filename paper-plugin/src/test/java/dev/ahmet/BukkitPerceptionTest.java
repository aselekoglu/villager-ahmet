package dev.ahmet;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BukkitPerceptionTest {
  @Test void offThreadCaptureReturnsUnavailableBeforeTouchingNpc() {
    AtomicInteger calls = new AtomicInteger();
    NPC npc = npc(calls, true);
    var result = BukkitPerception.capture(false, npc, null, null, true, false, false, 12, 1000);
    assertFalse(result.available());
    assertEquals(12, result.generation());
    assertEquals(0, calls.get());
  }

  @Test void disabledPerceptionReturnsUnavailableWithoutInspectingNpc() {
    AtomicInteger calls = new AtomicInteger();
    var result = BukkitPerception.capture(true, npc(calls, true), null, null, false, true, true, 12, 1000);
    assertFalse(result.available());
    assertEquals(0, calls.get());
  }

  @Test void unspawnedNpcReturnsUnavailableWithoutResolvingEntityOrWorld() {
    AtomicInteger calls = new AtomicInteger();
    NPC npc = npc(calls, false);
    var result = BukkitPerception.capture(true, npc, null, null, true, false, false, 13, 1000);
    assertFalse(result.available());
    assertEquals(13, result.generation());
    assertEquals(1, calls.get());
  }

  @Test void blockCoordinatesUseArithmeticChunkShiftAtNegativeAndPositiveBoundaries() {
    assertEquals(-1, BukkitPerception.blockToChunk(-1));
    assertEquals(-1, BukkitPerception.blockToChunk(-16));
    assertEquals(0, BukkitPerception.blockToChunk(15));
    assertEquals(1, BukkitPerception.blockToChunk(16));
  }

  @Test void inventoryIsLazyAndFailsClosedForDisabledUnauthorizedWrongWorldFarAndUnloadedPartner() {
    Fixture disabled = new Fixture();
    disabled.capture(disabled.viewer(false), disabled.location(1, 64, 0), false);
    assertEquals(0, disabled.chestStateReads.get());
    assertEquals(0, disabled.slotReads.get());

    Fixture unauthorized = new Fixture();
    unauthorized.capture(unauthorized.viewer(false), unauthorized.location(1, 64, 0), true);
    assertEquals(0, unauthorized.chestStateReads.get());
    assertEquals(0, unauthorized.slotReads.get());

    Fixture wrongWorld = new Fixture();
    wrongWorld.capture(wrongWorld.viewer(true), new Location(wrongWorld.otherWorld(), 1, 64, 0), true);
    assertEquals(0, wrongWorld.chestStateReads.get());
    assertEquals(0, wrongWorld.slotReads.get());

    Fixture far = new Fixture();
    far.capture(far.viewer(true), far.location(24, 64, 0), true);
    assertEquals(0, far.chestStateReads.get());
    assertEquals(0, far.slotReads.get());
    assertEquals(0, far.neighborChunkChecks.get());

    Fixture partnerUnloaded = new Fixture();
    partnerUnloaded.originX = 8;
    partnerUnloaded.unloadedChunk = 1;
    partnerUnloaded.capture(partnerUnloaded.viewer(true), partnerUnloaded.location(15, 64, 0), true);
    assertTrue(partnerUnloaded.neighborChunkChecks.get() > 0);
    assertEquals(0, partnerUnloaded.chestStateReads.get());
    assertEquals(0, partnerUnloaded.slotReads.get());
  }

  @Test void authorizedBoundDoubleChestReadsExactly54SlotsAndMapsEmptySlotsSafely() {
    Fixture fixture = new Fixture();
    var snapshot = fixture.capture(fixture.viewer(true), fixture.location(1, 64, 0), true);
    assertTrue(snapshot.inventory().available());
    assertEquals(54, fixture.slotReads.get());
    assertTrue(snapshot.inventory().items().isEmpty());
    assertEquals(Material.AIR, BukkitPerception.itemSlot(null).material());
    assertEquals(0, BukkitPerception.itemSlot(null).amount());
  }

  @Test void chestHeightIsCheckedBeforeBlockResolution() {
    Fixture fixture = new Fixture();
    fixture.originY = 319;
    fixture.capture(fixture.viewer(true), fixture.location(1, 320, 0), true);
    assertEquals(0, fixture.outOfHeightChestBlockRequests.get());
    assertEquals(0, fixture.chestStateReads.get());
  }

  @Test void playerFilterExcludesSelfForeignInvisibleAndHiddenBeforePositionReadAndSerializesNoIdentity() {
    Fixture fixture = new Fixture();
    Player viewer = fixture.viewer(true);
    Player self = viewer;
    Player foreign = fixture.player(fixture.otherWorld(), fixture.location(0, 64, 0), false, true, false, false);
    Player invisible = fixture.player(fixture.world, fixture.location(1, 64, 0), true, true, true, false);
    Player hidden = fixture.player(fixture.world, fixture.location(1, 64, 0), false, false, false, false);
    Player visible = fixture.player(fixture.world, fixture.location(2, 64, 0), false, true, false, false);
    fixture.visibleTargets.add(visible);
    fixture.visiblePlayers = List.of(self, foreign, invisible, hidden, visible);
    var snapshot = fixture.capture(viewer, null, false, true);
    assertEquals(List.of("near"), snapshot.players().bands());
    assertEquals(0, fixture.positionReads.get(foreign).get());
    assertEquals(0, fixture.positionReads.get(invisible).get());
    assertEquals(0, fixture.positionReads.get(hidden).get());
    String json = snapshot.toJson();
    assertFalse(json.contains("player-name"));
    assertFalse(json.contains("123e4567"));
  }

  @Test void playerIterationStopsAt32CandidatesBeforeReadingLaterLocations() {
    Fixture fixture = new Fixture();
    Player viewer = fixture.viewer(true);
    List<Player> candidates = new ArrayList<>();
    for (int i = 0; i < 32; i++) candidates.add(fixture.player(fixture.world, fixture.location(1, 64, 0), false, false, false, false));
    Player candidate33 = fixture.player(fixture.world, fixture.location(1, 64, 0), false, true, false, false);
    candidates.add(candidate33);
    fixture.visiblePlayers = candidates;
    fixture.capture(viewer, null, false, true);
    assertEquals(0, fixture.positionReads.get(candidate33).get());
  }

  private static NPC npc(AtomicInteger calls, boolean spawned) {
    return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class}, (proxy, method, args) -> {
      if (method.getName().equals("isSpawned")) { calls.incrementAndGet(); return spawned; }
      if (method.getName().equals("toString")) return "test-npc";
      if (method.getReturnType().isPrimitive()) return method.getReturnType() == boolean.class ? false : 0;
      return null;
    });
  }

  private static final class Fixture {
    final AtomicInteger chestStateReads = new AtomicInteger();
    final AtomicInteger slotReads = new AtomicInteger();
    final AtomicInteger neighborChunkChecks = new AtomicInteger();
    final AtomicInteger outOfHeightChestBlockRequests = new AtomicInteger();
    final java.util.Map<Player, AtomicInteger> positionReads = new java.util.IdentityHashMap<>();
    final java.util.Set<Player> visibleTargets = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    final AtomicInteger npcCalls = new AtomicInteger();
    List<Player> visiblePlayers = new ArrayList<>();
    int unloadedChunk = Integer.MIN_VALUE;
    int originX = 0;
    int originY = 64;
    final World world = world(false);

    World otherWorld() { return world(true); }
    Location location(double x, double y, double z) { return new Location(world, x, y, z); }

    GroundedPerception.Snapshot capture(Player viewer, Location chest, boolean includeInventory) {
      return capture(viewer, chest, includeInventory, false);
    }

    GroundedPerception.Snapshot capture(Player viewer, Location chest, boolean includeInventory, boolean includePlayers) {
      NPC npc = (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class}, (p, m, a) -> switch (m.getName()) {
        case "isSpawned" -> true;
        case "getEntity" -> proxy(Entity.class, (e, em, ea) -> switch (em.getName()) {
          case "getWorld" -> world;
          case "getLocation" -> location(originX, originY, 0);
          default -> defaultValue(em.getReturnType());
        });
        default -> defaultValue(m.getReturnType());
      });
      return BukkitPerception.capture(true, npc, viewer, chest, true, includeInventory, includePlayers, 1, 1000,
          () -> visiblePlayers);
    }

    Player viewer(boolean admin) { return player(world, location(0, 64, 0), false, true, admin, true); }

    Player player(World playerWorld, Location playerLocation, boolean invisible, boolean canSee,
        boolean admin, boolean online) {
      AtomicInteger positions = new AtomicInteger();
      Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
        case "isOnline" -> online;
        case "getWorld" -> playerWorld;
        case "hasPermission" -> admin;
        case "isInvisible" -> invisible;
        case "hasPotionEffect" -> false;
        case "canSee" -> visibleTargets.contains(a[0]);
        case "getLocation" -> { positions.incrementAndGet(); yield playerLocation; }
        case "getUniqueId" -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        case "getName" -> "player-name";
        default -> defaultValue(m.getReturnType());
      });
      positionReads.put(player, positions);
      return player;
    }

    private World world(boolean distinct) {
      return proxy(World.class, (w, m, a) -> switch (m.getName()) {
        case "getMinHeight" -> -64;
        case "getMaxHeight" -> 320;
        case "getTime" -> 1000L;
        case "isChunkLoaded" -> {
          int cx = (int) a[0], cz = (int) a[1];
          if (!distinct && cx == 1) neighborChunkChecks.incrementAndGet();
          yield cx != unloadedChunk;
        }
        case "getBlockAt" -> {
          int x = (int) a[0], y = (int) a[1], z = (int) a[2];
          if (!distinct && x == 1 && y == 400 && z == 0) outOfHeightChestBlockRequests.incrementAndGet();
          boolean chest = !distinct && x == 1 && y == 64 && z == 0;
          yield proxy(Block.class, (b, bm, ba) -> switch (bm.getName()) {
            case "getType" -> chest ? Material.CHEST : Material.AIR;
            case "isPassable" -> !chest;
            case "getState" -> {
              chestStateReads.incrementAndGet();
              yield proxy(Chest.class, (c, cm, ca) -> cm.getName().equals("getInventory") ? inventory() : defaultValue(cm.getReturnType()));
            }
            default -> defaultValue(bm.getReturnType());
          });
        }
        default -> defaultValue(m.getReturnType());
      });
    }

    private Inventory inventory() {
      return proxy(Inventory.class, (i, m, a) -> switch (m.getName()) {
        case "getSize" -> 54;
        case "getItem" -> {
          slotReads.incrementAndGet();
          yield null;
        }
        default -> defaultValue(m.getReturnType());
      });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
      return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
        if (m.getName().equals("equals")) return p == a[0];
        if (m.getName().equals("hashCode")) return System.identityHashCode(p);
        if (m.getName().equals("toString")) return type.getSimpleName() + "-fixture";
        return handler.invoke(p, m, a);
      }));
    }

    private static Object defaultValue(Class<?> type) {
      if (!type.isPrimitive()) return null;
      if (type == boolean.class) return false;
      if (type == char.class) return '\0';
      if (type == byte.class) return (byte) 0;
      if (type == short.class) return (short) 0;
      if (type == int.class) return 0;
      if (type == long.class) return 0L;
      if (type == float.class) return 0f;
      if (type == double.class) return 0d;
      return null;
    }
  }
}
