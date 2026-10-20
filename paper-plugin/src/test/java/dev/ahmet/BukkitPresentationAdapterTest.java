package dev.ahmet;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class BukkitPresentationAdapterTest {
    private static final UUID WORLD_ID = new UUID(0, 1);

    @Test
    void onlyReservesLookScanAtTickBoundaryForBothValidAndEmptySnapshots() {
        Fixture valid = new Fixture();
        valid.source.players = List.of(valid.player(1, 2, false, GameMode.SURVIVAL));
        valid.adapter.tickLook(valid.npc(false, false), false, 100);
        for (long tick = 101; tick < 120; tick++) valid.adapter.tickLook(valid.npc(false, false), false, tick);
        valid.adapter.tickLook(valid.npc(false, false), false, 120);
        assertEquals(2, valid.source.playerCalls.get());
        assertEquals(8, valid.source.chunkCalls.get());
        assertEquals(2, valid.source.losCalls.get());

        Fixture empty = new Fixture();
        for (long tick = 100; tick < 120; tick++) empty.adapter.tickLook(empty.npc(false, false), false, tick);
        empty.adapter.tickLook(empty.npc(false, false), false, 120);
        assertEquals(2, empty.source.playerCalls.get());
        assertEquals(8, empty.source.chunkCalls.get());
        assertEquals(0, empty.source.losCalls.get());
    }

    @Test
    void invisibleAndSpectatorPlayersAreSkippedBeforeLocationAndLineOfSight() {
        Fixture fixture = new Fixture();
        Player invisible = fixture.player(1, 1, true, GameMode.SURVIVAL);
        Player spectator = fixture.player(3, 2, false, GameMode.SPECTATOR);
        Player visible = fixture.player(2, 4, false, GameMode.SURVIVAL);
        fixture.source.players = List.of(invisible, spectator, visible);

        fixture.adapter.tickLook(fixture.npc(false, false), false, 100);

        assertEquals(List.of(4.0), fixture.effects.facedX);
        assertEquals(1, fixture.source.losCalls.get());
        assertEquals(0, fixture.playerLocationReads.get(1).get());
        assertEquals(0, fixture.playerLocationReads.get(3).get());
    }

    @Test
    void sourceVisibilityFaceSwingAndSoundRuntimeFailuresNeverEscape() {
        for (String failure : List.of("entity", "source", "visibility", "face", "swing", "sound", "send")) {
            Fixture fixture = new Fixture();
            fixture.source.players = List.of(fixture.player(1, 1, false, GameMode.SURVIVAL));
            if (failure.equals("entity")) {
                NPC brokenNpc = fixture.entityThrowingNpc();
                assertDoesNotThrow(() -> fixture.adapter.tickLook(brokenNpc, false, 100), failure);
                assertFalse(fixture.adapter.isAhmet(brokenNpc, fixture.villager(false)));
                assertDoesNotThrow(() -> fixture.adapter.committedWorkCue(brokenNpc, true), failure);
                assertFalse(fixture.diagnostics.isEmpty());
                continue;
            }
            if (failure.equals("source")) fixture.source.failPlayers = true;
            if (failure.equals("visibility")) fixture.source.failLos = true;
            if (failure.equals("face")) fixture.effects.failFace = true;
            if (failure.equals("swing")) fixture.effects.failSwing = true;
            if (failure.equals("sound")) fixture.effects.failSound = true;
            if (failure.equals("send")) fixture.effects.failSend = true;

            assertDoesNotThrow(() -> fixture.adapter.tickLook(fixture.npc(false, false), false, 100), failure);
            if (failure.equals("source")) {
                fixture.adapter.tickLook(fixture.npc(false, false), false, 101);
                assertEquals(1, fixture.source.playerCalls.get());
            }
            assertDoesNotThrow(() -> fixture.adapter.committedWorkCue(fixture.npc(false, false), true), failure);
            assertDoesNotThrow(() -> fixture.adapter.sayNearby(fixture.npc(false, false), "hello"), failure);
            assertTrue(fixture.diagnostics.stream().allMatch(message -> message.matches("(look|work-cue|speech):[A-Za-z0-9]{1,40}")));
        }
    }

    @Test
    void wrongThreadBusySleepNavigationAndMissingNpcPerformNoPresentationReadsOrEffects() {
        Fixture fixture = new Fixture();
        fixture.mainThread.set(false);
        fixture.adapter.tickLook(fixture.npc(false, false), false, 1);
        fixture.adapter.committedWorkCue(fixture.npc(false, false), true);
        fixture.mainThread.set(true);
        fixture.adapter.tickLook(fixture.npc(false, false), true, 2);
        fixture.adapter.tickLook(fixture.npc(true, false), false, 3);
        fixture.adapter.tickLook(fixture.npc(false, true), false, 4);
        fixture.adapter.tickLook(null, false, 5);
        fixture.adapter.committedWorkCue(null, true);
        fixture.adapter.committedWorkCue(fixture.npc(false, false), false);

        assertEquals(0, fixture.source.playerCalls.get());
        assertTrue(fixture.effects.facedX.isEmpty());
        assertEquals(0, fixture.effects.swingCalls.get());
        assertEquals(0, fixture.effects.soundCalls.get());
    }

    @Test
    void inspectsAtMostThirtyTwoPlayersAndSendsToAtMostSixteenRecipients() {
        Fixture fixture = new Fixture();
        List<Player> many = new ArrayList<>();
        for (int index = 1; index <= 40; index++) many.add(fixture.player(index, index, false, GameMode.SURVIVAL));
        fixture.source.players = many;
        fixture.adapter.sayNearby(fixture.npc(false, false), "hello");
        assertEquals(32, fixture.playerLocationReads.values().stream().mapToInt(AtomicInteger::get).sum());
        assertEquals(16, fixture.effects.messages.size());
    }

    @Test
    void privateReplyOnlyUsesInitiatingActorAndSharedCooldownMaySuppressIt() {
        Fixture fixture = new Fixture();
        Player actor = fixture.player(1, 1, false, GameMode.SURVIVAL);
        fixture.source.players = List.of(actor, fixture.player(2, 2, false, GameMode.SURVIVAL));
        NPC npc = fixture.npc(false, false);
        fixture.adapter.replyToActor(npc, actor, "private");
        assertEquals(List.of(actor), fixture.effects.messages.stream().map(Message::player).toList());

        Fixture sharedCadence = new Fixture();
        Player sameActor = sharedCadence.player(1, 1, false, GameMode.SURVIVAL);
        sharedCadence.source.players = List.of(sameActor, sharedCadence.player(2, 2, false, GameMode.SURVIVAL));
        NPC sharedNpc = sharedCadence.npc(false, false);
        sharedCadence.adapter.sayNearby(sharedNpc, "public");
        assertEquals(1, sharedCadence.effects.messages.stream().filter(message -> message.player().equals(sameActor)).count());
        sharedCadence.adapter.replyToActor(sharedNpc, sameActor, "suppressed by per-UUID cadence");
        assertEquals(1, sharedCadence.effects.messages.stream().filter(message -> message.player().equals(sameActor)).count());
    }

    private final class Fixture {
        final AtomicBoolean mainThread = new AtomicBoolean(true);
        final AtomicLong now = new AtomicLong(10_000L);
        final FakeSource source = new FakeSource();
        final FakeEffects effects = new FakeEffects();
        final List<String> diagnostics = new ArrayList<>();
        final java.util.Map<Integer, AtomicInteger> playerLocationReads = new java.util.HashMap<>();
        final World world = proxy(World.class, (object, method, args) -> method.getName().equals("getUID") ? WORLD_ID : defaultValue(method.getReturnType()));
        final BukkitPresentationAdapter adapter = new BukkitPresentationAdapter(mainThread::get, source, effects, now::get, diagnostics::add);

        Player player(int id, double distance, boolean invisible, GameMode gameMode) {
            playerLocationReads.put(id, new AtomicInteger());
            UUID uuid = new UUID(0, id);
            return proxy(Player.class, (object, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> uuid;
                case "getWorld" -> world;
                case "getLocation" -> { playerLocationReads.get(id).incrementAndGet(); yield new Location(world, distance, 64, 0); }
                case "isInvisible" -> invisible;
                case "getGameMode" -> gameMode;
                case "isOnline" -> true;
                default -> defaultValue(method.getReturnType());
            });
        }

        NPC npc(boolean navigating, boolean sleeping) {
            Villager villager = proxy(Villager.class, (object, method, args) -> switch (method.getName()) {
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 0, 64, 0);
                case "isDead", "isSleeping" -> method.getName().equals("isSleeping") && sleeping;
                default -> defaultValue(method.getReturnType());
            });
            Navigator navigator = proxy(Navigator.class, (object, method, args) -> method.getName().equals("isNavigating") && navigating);
            return proxy(NPC.class, (object, method, args) -> switch (method.getName()) {
                case "isSpawned" -> true;
                case "getEntity" -> villager;
                case "getNavigator" -> navigator;
                default -> defaultValue(method.getReturnType());
            });
        }

        NPC entityThrowingNpc() {
            return proxy(NPC.class, (object, method, args) -> switch (method.getName()) {
                case "isSpawned" -> true;
                case "getEntity" -> throw new IllegalStateException("private detail");
                default -> defaultValue(method.getReturnType());
            });
        }

        Villager villager(boolean sleeping) {
            return proxy(Villager.class, (object, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> new UUID(0, 999);
                case "isSleeping" -> sleeping;
                default -> defaultValue(method.getReturnType());
            });
        }

        private final class FakeSource implements BukkitPresentationAdapter.PlayerSource {
            List<Player> players = List.of();
            boolean failPlayers;
            boolean failLos;
            final AtomicInteger playerCalls = new AtomicInteger();
            final AtomicInteger chunkCalls = new AtomicInteger();
            final AtomicInteger losCalls = new AtomicInteger();
            @Override public List<Player> players(World ignored) { playerCalls.incrementAndGet(); if (failPlayers) throw new IllegalStateException("private detail"); return players; }
            @Override public boolean chunkLoaded(World ignored, int x, int z) { chunkCalls.incrementAndGet(); return true; }
            @Override public boolean lineOfSight(org.bukkit.entity.LivingEntity ignored, Player player) { losCalls.incrementAndGet(); if (failLos) throw new IllegalStateException("private detail"); return true; }
        }

        private final class FakeEffects implements BukkitPresentationAdapter.Effects {
            final List<Double> facedX = new ArrayList<>();
            final List<Message> messages = new ArrayList<>();
            final AtomicInteger swingCalls = new AtomicInteger();
            final AtomicInteger soundCalls = new AtomicInteger();
            boolean failFace;
            boolean failSwing;
            boolean failSound;
            boolean failSend;
            @Override public void face(NPC npc, Location target) { if (failFace) throw new IllegalStateException("private detail"); facedX.add(target.getX()); }
            @Override public void send(Player player, String message) { if (failSend) throw new IllegalStateException("private detail"); messages.add(new Message(player, message)); }
            @Override public void swing(org.bukkit.entity.LivingEntity entity) { swingCalls.incrementAndGet(); if (failSwing) throw new IllegalStateException("private detail"); }
            @Override public void workSound(org.bukkit.entity.LivingEntity entity) { soundCalls.incrementAndGet(); if (failSound) throw new IllegalStateException("private detail"); }
        }
    }

    private record Message(Player player, String text) { }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> object == args[0];
                case "hashCode" -> System.identityHashCode(object);
                case "toString" -> "presentation-test-proxy";
                default -> null;
            };
            return handler.invoke(object, method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
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
