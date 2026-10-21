package dev.ahmet.protection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.block.Block;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe registry and persistence manager for land claims.
 */
public final class LandClaimRegistry {
  private static final Logger LOGGER = Logger.getLogger(LandClaimRegistry.class.getName());
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Map<String, LandClaim> claims = new ConcurrentHashMap<>();
  private final Path storagePath;
  private volatile boolean stateValid = true;

  public LandClaimRegistry(Path storagePath) {
    this.storagePath = storagePath;
  }

  /**
   * Loads land claims from disk. Fails closed and preserves corrupt state on parse failure.
   *
   * @throws IllegalStateException if configuration file is corrupt or invalid
   */
  public synchronized void load() {
    if (storagePath == null || !Files.exists(storagePath)) {
      claims.clear();
      stateValid = true;
      return;
    }

    try {
      String content = Files.readString(storagePath).trim();
      if (content.isEmpty()) {
        claims.clear();
        stateValid = true;
        return;
      }

      JsonElement element = JsonParser.parseString(content);
      if (!element.isJsonArray()) {
        stateValid = false;
        throw new IllegalStateException("Saved claims configuration is invalid");
      }

      JsonArray array = element.getAsJsonArray();
      Map<String, LandClaim> loaded = new HashMap<>();
      for (JsonElement item : array) {
        if (!item.isJsonObject()) {
          stateValid = false;
          throw new IllegalStateException("Saved claims configuration is invalid");
        }
        LandClaim claim = LandClaim.fromJson(item.getAsJsonObject());
        loaded.put(claim.id(), claim);
      }

      claims.clear();
      claims.putAll(loaded);
      stateValid = true;
    } catch (IllegalStateException e) {
      stateValid = false;
      throw e;
    } catch (Exception e) {
      stateValid = false;
      throw new IllegalStateException("Saved claims configuration is invalid", e);
    }
  }

  /**
   * Persists claims to disk via an atomic write-replace. Skips if state is marked invalid.
   */
  public synchronized void save() {
    if (!stateValid) {
      LOGGER.warning("Refusing to save claims: in-memory state is marked invalid (fail-closed).");
      return;
    }
    if (storagePath == null) {
      return;
    }

    Path temp = null;
    try {
      Path parent = storagePath.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      temp = Files.createTempFile(parent != null ? parent : Path.of("."), "claims-", ".tmp");

      JsonArray array = new JsonArray();
      for (LandClaim claim : claims.values()) {
        array.add(claim.toJson());
      }

      String json = GSON.toJson(array);
      Files.writeString(temp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
      Files.move(temp, storagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {}
      }
      throw new UncheckedIOException("Failed to save claims registry to " + storagePath, e);
    }
  }

  /**
   * Adds or replaces a land claim and persists to disk.
   */
  public synchronized void addClaim(LandClaim claim) {
    Objects.requireNonNull(claim, "claim must not be null");
    claims.put(claim.id(), claim);
    save();
  }

  /**
   * Removes a land claim by id and persists to disk.
   *
   * @return true if a claim was removed, false otherwise
   */
  public synchronized boolean removeClaim(String id) {
    if (id == null) {
      return false;
    }
    LandClaim removed = claims.remove(id);
    if (removed != null) {
      save();
      return true;
    }
    return false;
  }

  public LandClaim getClaim(String id) {
    if (id == null) {
      return null;
    }
    return claims.get(id);
  }

  public Collection<LandClaim> allClaims() {
    return Collections.unmodifiableCollection(claims.values());
  }

  public boolean isStateValid() {
    return stateValid;
  }

  /**
   * Evaluates if a given block is protected against the candidate villager and action.
   *
   * @param villagerId the acting villager id
   * @param block the block to inspect
   * @param action the action being attempted
   * @return true if the block is protected (access denied), false if safe to mutate
   */
  public boolean isProtected(String villagerId, Block block, ClaimAction action) {
    if (block == null || block.getWorld() == null) {
      return true; // Fail closed
    }
    if (!stateValid) {
      return true; // Fail closed on corrupt claims state
    }

    String worldName = block.getWorld().getName();
    int x = block.getX();
    int y = block.getY();
    int z = block.getZ();

    List<LandClaim> matching = claims.values().stream()
        .filter(c -> c.boundary().contains(worldName, x, y, z))
        .toList();

    if (matching.isEmpty()) {
      return false; // Unprotected wilderness
    }

    for (LandClaim claim : matching) {
      if (!claim.permits(villagerId, action)) {
        return true; // Access denied by this claim
      }
    }

    return false; // Permitted by all overlapping claims
  }
}
