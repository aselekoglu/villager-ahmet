package dev.ahmet;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Main-thread bridge from bounded presentation facts to Citizens and Bukkit effects. */
final class BukkitPresentationAdapter {
    static final int MAX_INSPECTED_PLAYERS = 32;
    private static final long DIAGNOSTIC_CADENCE_MILLIS = 10_000L;

    interface MainThread { boolean isPrimaryThread(); }
    interface PlayerSource {
        List<Player> players(World world);
        boolean chunkLoaded(World world, int chunkX, int chunkZ);
        boolean lineOfSight(LivingEntity entity, Player player);
    }
    interface Effects {
        void face(NPC npc, Location target);
        void send(Player player, String message);
        void swing(LivingEntity entity);
        void workSound(LivingEntity entity);
    }

    private final MainThread mainThread;
    private final PlayerSource playerSource;
    private final Effects effects;
    private final LongSupplier monotonicMillis;
    private final Consumer<String> diagnostics;
    private final PresentationPolicy policy;
    private final Map<String, Long> lastDiagnosticAt = new HashMap<>();

    BukkitPresentationAdapter() {
        this(org.bukkit.Bukkit::isPrimaryThread, new BukkitPlayerSource(), new BukkitEffects(),
                () -> System.nanoTime() / 1_000_000L, BukkitPresentationAdapter::logDiagnostic);
    }

    BukkitPresentationAdapter(MainThread mainThread, PlayerSource playerSource, Effects effects,
                              LongSupplier monotonicMillis, Consumer<String> diagnostics) {
        this.mainThread = mainThread;
        this.playerSource = playerSource;
        this.effects = effects;
        this.monotonicMillis = monotonicMillis;
        this.diagnostics = diagnostics;
        this.policy = new PresentationPolicy(monotonicMillis);
    }

    void tickLook(NPC npc, boolean blocked, long serverTick) {
        bestEffort("look", () -> tickLookSafely(npc, blocked, serverTick));
    }

    boolean isAhmet(NPC npc, Entity clicked) {
        try {
            return mainThread.isPrimaryThread() && npc != null && npc.isSpawned() && clicked != null
                    && npc.getEntity().getUniqueId().equals(clicked.getUniqueId());
        } catch (RuntimeException failure) {
            diagnose("speech", failure);
            return false;
        }
    }

    private void tickLookSafely(NPC npc, boolean blocked, long serverTick) {
        if (!mainThread.isPrimaryThread() || blocked || npc == null || !npc.isSpawned()) return;
        Entity entity = npc.getEntity();
        if (!(entity instanceof LivingEntity villager) || villager.isDead() || npc.getNavigator().isNavigating()) return;
        if (villager instanceof org.bukkit.entity.Villager nativeVillager && nativeVillager.isSleeping()) return;
        World world = villager.getWorld();
        Location origin = villager.getLocation();
        if (!policy.reserveLookScan(serverTick)) return;

        List<Player> players = boundedPlayers(world);
        boolean lineOfSightChunksLoaded = lookAreaLoaded(world, origin);
        List<PresentationPolicy.PlayerFact> facts = new ArrayList<>(players.size());
        Map<UUID, Player> byId = new HashMap<>();
        for (Player player : players) {
            if (!player.isOnline()) continue;
            // Native Bukkit visibility is honored here: invisible entities and spectators are excluded.
            // Third-party vanish systems are not claimed or inferred by this adapter.
            if (player.isInvisible() || player.getGameMode() == GameMode.SPECTATOR) continue;
            Location location = player.getLocation();
            double distanceSquared = location.distanceSquared(origin);
            boolean visible = distanceSquared <= PresentationPolicy.LOOK_RADIUS_SQUARED
                    && lineOfSightChunksLoaded && playerSource.lineOfSight(villager, player);
            facts.add(new PresentationPolicy.PlayerFact(player.getUniqueId(), world.getUID(), distanceSquared,
                    true, visible));
            byId.put(player.getUniqueId(), player);
        }
        policy.lookTarget(facts, world.getUID())
                .map(byId::get).filter(Player::isOnline).ifPresent(player -> effects.face(npc, player.getLocation()));
    }

    void sayNearby(NPC npc, String message) {
        bestEffort("speech", () -> sayNearbySafely(npc, message));
    }

    private void sayNearbySafely(NPC npc, String message) {
        if (!mainThread.isPrimaryThread() || npc == null || !npc.isSpawned()) return;
        Entity entity = npc.getEntity();
        World world = entity.getWorld();
        Location origin = entity.getLocation();
        List<Player> players = boundedPlayers(world);
        List<PresentationPolicy.PlayerFact> facts = new ArrayList<>(players.size());
        Map<UUID, Player> byId = new HashMap<>();
        for (Player player : players) {
            facts.add(new PresentationPolicy.PlayerFact(player.getUniqueId(), player.getWorld().getUID(),
                    player.getLocation().distanceSquared(origin), player.isOnline(), true));
            byId.put(player.getUniqueId(), player);
        }
        for (UUID recipient : policy.speechRecipients(facts, world.getUID())) {
            Player player = byId.get(recipient);
            if (player != null && player.isOnline() && player.getWorld().equals(world)) {
                bestEffort("speech", () -> effects.send(player, "§6[Ahmet] §f" + message));
            }
        }
    }

    void replyToActor(NPC npc, Player actor, String message) {
        bestEffort("speech", () -> replyToActorSafely(npc, actor, message));
    }

    private void replyToActorSafely(NPC npc, Player actor, String message) {
        if (!mainThread.isPrimaryThread() || npc == null || !npc.isSpawned() || actor == null || !actor.isOnline()) return;
        Entity entity = npc.getEntity();
        if (!entity.getWorld().equals(actor.getWorld())) return;
        double distanceSquared = entity.getLocation().distanceSquared(actor.getLocation());
        var fact = new PresentationPolicy.PlayerFact(actor.getUniqueId(), actor.getWorld().getUID(),
                distanceSquared, actor.isOnline(), true);
        if (policy.speechRecipients(List.of(fact), entity.getWorld().getUID()).contains(actor.getUniqueId())) {
            bestEffort("speech", () -> effects.send(actor, "§6[Ahmet] §f" + message));
        }
    }

    void committedWorkCue(NPC npc, boolean committedSuccess) {
        bestEffort("work-cue", () -> {
            if (!mainThread.isPrimaryThread() || !committedSuccess || npc == null || !npc.isSpawned()) return;
            Entity entity = npc.getEntity();
            if (!(entity instanceof LivingEntity villager) || villager.isDead() || !policy.allowWorkCue(true)) return;
            effects.swing(villager);
            effects.workSound(villager);
        });
    }

    private void bestEffort(String category, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            diagnose(category, failure);
        }
    }

    private void diagnose(String category, RuntimeException failure) {
        long now;
        try {
            now = monotonicMillis.getAsLong();
            Long previous = lastDiagnosticAt.get(category);
            if (previous != null && now >= previous && now - previous < DIAGNOSTIC_CADENCE_MILLIS) return;
            lastDiagnosticAt.put(category, now);
            String type = failure.getClass().getSimpleName().replaceAll("[^A-Za-z0-9]", "");
            if (type.isEmpty()) type = "RuntimeException";
            if (type.length() > 40) type = type.substring(0, 40);
            diagnostics.accept(category + ":" + type);
        } catch (RuntimeException ignored) {
            // Diagnostics are cosmetic too and cannot escape into the task or navigation loop.
        }
    }

    private List<Player> boundedPlayers(World world) {
        List<Player> bounded = new ArrayList<>(MAX_INSPECTED_PLAYERS);
        // Candidate cap follows Bukkit's world-player iteration order; policy breaks distance ties by UUID.
        for (Player player : playerSource.players(world)) {
            if (bounded.size() == MAX_INSPECTED_PLAYERS) break;
            bounded.add(player);
        }
        return List.copyOf(bounded);
    }

    private boolean lookAreaLoaded(World world, Location origin) {
        int minChunkX = (int) Math.floor(origin.getX() - 6.0) >> 4;
        int maxChunkX = (int) Math.floor(origin.getX() + 6.0) >> 4;
        int minChunkZ = (int) Math.floor(origin.getZ() - 6.0) >> 4;
        int maxChunkZ = (int) Math.floor(origin.getZ() + 6.0) >> 4;
        if ((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1) > 4) return false;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!playerSource.chunkLoaded(world, chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private static void logDiagnostic(String message) {
        org.bukkit.Bukkit.getLogger().warning("Villager Ahmet presentation skipped (" + message + ").");
    }

    private static final class BukkitPlayerSource implements PlayerSource {
        @Override public List<Player> players(World world) { return world.getPlayers(); }
        @Override public boolean chunkLoaded(World world, int chunkX, int chunkZ) { return world.isChunkLoaded(chunkX, chunkZ); }
        @Override public boolean lineOfSight(LivingEntity entity, Player player) { return entity.hasLineOfSight(player); }
    }

    private static final class BukkitEffects implements Effects {
        @Override public void face(NPC npc, Location target) { npc.faceLocation(target); }
        @Override public void send(Player player, String message) { player.sendMessage(message); }
        @Override public void swing(LivingEntity entity) { entity.swingMainHand(); }
        @Override public void workSound(LivingEntity entity) {
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_WOOD_HIT, 0.18f, 1.15f);
        }
    }
}
