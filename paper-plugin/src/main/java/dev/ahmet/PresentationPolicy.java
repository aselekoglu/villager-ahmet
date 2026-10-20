package dev.ahmet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded deterministic decisions for cosmetic presentation. Contains no Bukkit objects. */
final class PresentationPolicy {
    static final double LOOK_RADIUS_SQUARED = 6.0 * 6.0;
    static final double SPEECH_RADIUS_SQUARED = 18.0 * 18.0;
    static final int LOOK_CADENCE_TICKS = 20;
    static final long SPEECH_CADENCE_MILLIS = 1_000L;
    static final long RECIPIENT_RETENTION_MILLIS = 60_000L;
    static final int MAX_RETAINED_RECIPIENTS = 128;
    static final long WORK_CUE_CADENCE_MILLIS = 250L;
    static final int MAX_SPEECH_RECIPIENTS = 16;

    record PlayerFact(UUID id, UUID worldId, double distanceSquared, boolean online, boolean visible) { }

    private final LongSupplier monotonicMillis;
    // Public speech and private replies intentionally share this UUID cadence; replies can be suppressed.
    private final Map<UUID, Long> lastSpeechAt = new HashMap<>();
    private long lastLookTick = Long.MIN_VALUE;
    private long lastWorkCueAt = Long.MIN_VALUE;

    PresentationPolicy(LongSupplier monotonicMillis) {
        this.monotonicMillis = monotonicMillis;
    }

    boolean reserveLookScan(long serverTick) {
        if (lastLookTick != Long.MIN_VALUE && (serverTick < lastLookTick
                || serverTick - lastLookTick < LOOK_CADENCE_TICKS)) return false;
        lastLookTick = serverTick;
        return true;
    }

    Optional<UUID> lookTarget(List<PlayerFact> candidates, UUID worldId) {
        UUID selected = candidates.stream()
                .filter(player -> eligible(player, worldId, LOOK_RADIUS_SQUARED) && player.visible())
                .min(Comparator.comparingDouble(PlayerFact::distanceSquared).thenComparing(PlayerFact::id))
                .map(PlayerFact::id).orElse(null);
        if (selected == null) return Optional.empty();
        return Optional.of(selected);
    }

    List<UUID> speechRecipients(List<PlayerFact> candidates, UUID worldId) {
        long now = monotonicMillis.getAsLong();
        lastSpeechAt.entrySet().removeIf(entry -> elapsed(now, entry.getValue()) > RECIPIENT_RETENTION_MILLIS);
        List<PlayerFact> ordered = candidates.stream()
                .filter(player -> eligible(player, worldId, SPEECH_RADIUS_SQUARED))
                .sorted(Comparator.comparingDouble(PlayerFact::distanceSquared).thenComparing(PlayerFact::id))
                .toList();
        List<UUID> recipients = new ArrayList<>(MAX_SPEECH_RECIPIENTS);
        Set<UUID> seen = new HashSet<>();
        for (PlayerFact player : ordered) {
            if (recipients.size() == MAX_SPEECH_RECIPIENTS) break;
            if (!seen.add(player.id())) continue;
            Long lastSent = lastSpeechAt.get(player.id());
            if (lastSent != null && elapsed(now, lastSent) < SPEECH_CADENCE_MILLIS) continue;
            if (lastSent == null && lastSpeechAt.size() >= MAX_RETAINED_RECIPIENTS) evictOldestRecipient();
            lastSpeechAt.put(player.id(), now);
            recipients.add(player.id());
        }
        return List.copyOf(recipients);
    }

    boolean allowWorkCue(boolean committedSuccess) {
        if (!committedSuccess) return false;
        long now = monotonicMillis.getAsLong();
        if (lastWorkCueAt != Long.MIN_VALUE && elapsed(now, lastWorkCueAt) < WORK_CUE_CADENCE_MILLIS) return false;
        lastWorkCueAt = now;
        return true;
    }

    private void evictOldestRecipient() {
        UUID oldestId = null;
        long oldestAt = Long.MAX_VALUE;
        for (Map.Entry<UUID, Long> entry : lastSpeechAt.entrySet()) {
            if (entry.getValue() < oldestAt || (entry.getValue() == oldestAt
                    && (oldestId == null || entry.getKey().compareTo(oldestId) < 0))) {
                oldestAt = entry.getValue();
                oldestId = entry.getKey();
            }
        }
        if (oldestId != null) lastSpeechAt.remove(oldestId);
    }

    private static long elapsed(long now, long then) {
        return now >= then ? now - then : 0L;
    }

    private static boolean eligible(PlayerFact player, UUID worldId, double radiusSquared) {
        return player.id() != null && worldId.equals(player.worldId()) && player.online()
                && Double.isFinite(player.distanceSquared()) && player.distanceSquared() >= 0.0
                && player.distanceSquared() <= radiusSquared;
    }
}
