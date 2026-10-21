package dev.ahmet.protection;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class LandClaimRegistryTest {

  private Block mockBlock(String worldName, int x, int y, int z) {
    World world = (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> worldName;
          default -> null;
        }
    );

    return (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[]{Block.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getWorld" -> world;
          case "getX" -> x;
          case "getY" -> y;
          case "getZ" -> z;
          default -> null;
        }
    );
  }

  @Test
  void testAddRemoveAndGet(@TempDir Path tempDir) {
    Path file = tempDir.resolve("claims.json");
    LandClaimRegistry registry = new LandClaimRegistry(file);

    ClaimBoundary b1 = new ClaimBoundary("world", 0, 0, 0, 10, 10, 10);
    LandClaim claim1 = new LandClaim("c1", b1, "owner1", Set.of("*"), Set.of(ClaimAction.ALL), true);

    registry.addClaim(claim1);
    assertEquals(1, registry.allClaims().size());
    assertEquals(claim1, registry.getClaim("c1"));

    assertTrue(registry.removeClaim("c1"));
    assertEquals(0, registry.allClaims().size());
    assertNull(registry.getClaim("c1"));
    assertFalse(registry.removeClaim("c1"));
  }

  @Test
  void testWildernessIsUnprotected(@TempDir Path tempDir) {
    Path file = tempDir.resolve("claims.json");
    LandClaimRegistry registry = new LandClaimRegistry(file);

    ClaimBoundary b1 = new ClaimBoundary("world", 0, 0, 0, 10, 10, 10);
    registry.addClaim(new LandClaim("c1", b1, "owner1", Set.of("ahmet"), Set.of(ClaimAction.GATHER_WOOD), true));

    Block wildernessBlock = mockBlock("world", 50, 64, 50);
    assertFalse(registry.isProtected("ahmet", wildernessBlock, ClaimAction.GATHER_WOOD));
    assertFalse(registry.isProtected("other_villager", wildernessBlock, ClaimAction.HARVEST_CROPS));
  }

  @Test
  void testPermitVsProtectedInsideClaim(@TempDir Path tempDir) {
    Path file = tempDir.resolve("claims.json");
    LandClaimRegistry registry = new LandClaimRegistry(file);

    ClaimBoundary b1 = new ClaimBoundary("world", 0, 0, 0, 20, 100, 20);
    registry.addClaim(new LandClaim("farm_claim", b1, "owner1", Set.of("ahmet"), Set.of(ClaimAction.HARVEST_CROPS), true));

    Block claimBlock = mockBlock("world", 10, 65, 10);

    // Permitted villager + action
    assertFalse(registry.isProtected("ahmet", claimBlock, ClaimAction.HARVEST_CROPS));

    // Permitted villager, but wrong action
    assertTrue(registry.isProtected("ahmet", claimBlock, ClaimAction.GATHER_WOOD));

    // Unauthorized villager
    assertTrue(registry.isProtected("mehmet", claimBlock, ClaimAction.HARVEST_CROPS));
  }

  @Test
  void testOverlappingClaimsDenyClosed(@TempDir Path tempDir) {
    Path file = tempDir.resolve("claims.json");
    LandClaimRegistry registry = new LandClaimRegistry(file);

    // Claim 1 allows Ahmet to gather wood
    ClaimBoundary b1 = new ClaimBoundary("world", 0, 0, 0, 50, 100, 50);
    registry.addClaim(new LandClaim("wood_area", b1, "owner1", Set.of("ahmet"), Set.of(ClaimAction.GATHER_WOOD), true));

    // Overlapping Claim 2 in same area only allows Mehmet
    ClaimBoundary b2 = new ClaimBoundary("world", 10, 0, 10, 30, 100, 30);
    registry.addClaim(new LandClaim("restricted_subarea", b2, "owner2", Set.of("mehmet"), Set.of(ClaimAction.GATHER_WOOD), true));

    Block overlapBlock = mockBlock("world", 20, 65, 20);

    // For Ahmet: Claim 1 permits, but Claim 2 denies -> must deny (fail-closed, protected=true)
    assertTrue(registry.isProtected("ahmet", overlapBlock, ClaimAction.GATHER_WOOD));

    // For Mehmet: Claim 2 permits, but Claim 1 denies -> must deny (protected=true)
    assertTrue(registry.isProtected("mehmet", overlapBlock, ClaimAction.GATHER_WOOD));

    // Outside the overlap (e.g. at x=5, z=5), Claim 1 permits Ahmet and Claim 2 doesn't cover it
    Block nonOverlapBlock = mockBlock("world", 5, 65, 5);
    assertFalse(registry.isProtected("ahmet", nonOverlapBlock, ClaimAction.GATHER_WOOD));
    assertTrue(registry.isProtected("mehmet", nonOverlapBlock, ClaimAction.GATHER_WOOD));
  }

  @Test
  void testNullBlockOrWorldFailsClosed(@TempDir Path tempDir) {
    Path file = tempDir.resolve("claims.json");
    LandClaimRegistry registry = new LandClaimRegistry(file);

    assertTrue(registry.isProtected("ahmet", null, ClaimAction.GATHER_WOOD));

    Block nullWorldBlock = (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[]{Block.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getWorld" -> null;
          default -> null;
        }
    );
    assertTrue(registry.isProtected("ahmet", nullWorldBlock, ClaimAction.GATHER_WOOD));
  }

  @Test
  void testPersistenceAcrossInstances(@TempDir Path tempDir) {
    Path file = tempDir.resolve("claims.json");
    LandClaimRegistry registry1 = new LandClaimRegistry(file);

    ClaimBoundary b1 = new ClaimBoundary("world", 0, 0, 0, 15, 60, 15);
    registry1.addClaim(new LandClaim("saved_claim", b1, "operator", Set.of("*"), Set.of(ClaimAction.OPEN_CHEST), true));

    LandClaimRegistry registry2 = new LandClaimRegistry(file);
    registry2.load();

    assertEquals(1, registry2.allClaims().size());
    LandClaim loaded = registry2.getClaim("saved_claim");
    assertNotNull(loaded);
    assertEquals("saved_claim", loaded.id());
    assertTrue(loaded.permits("any", ClaimAction.OPEN_CHEST));
  }

  @Test
  void testCorruptJsonFailsClosedAndPreservesFile(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("claims.json");
    String corruptContent = "{\"not_an_array\": true}";
    Files.writeString(file, corruptContent);

    LandClaimRegistry registry = new LandClaimRegistry(file);
    assertThrows(IllegalStateException.class, registry::load);
    assertFalse(registry.isStateValid());

    // All blocks must fail closed when state is invalid
    Block block = mockBlock("world", 100, 100, 100);
    assertTrue(registry.isProtected("ahmet", block, ClaimAction.ALL));

    // Save must refuse to overwrite corrupt state
    registry.save();
    assertEquals(corruptContent, Files.readString(file), "Corrupt file must not be overwritten");
  }
}
