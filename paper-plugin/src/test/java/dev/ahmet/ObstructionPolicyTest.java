package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;
import org.junit.jupiter.api.Test;

final class ObstructionPolicyTest {

  private Block mockBlock(Material material, boolean persistent, boolean waterlogged,
      boolean chunkLoaded, String worldName, int x, int y, int z) {
    World world = (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "isChunkLoaded" -> chunkLoaded;
          case "getName" -> worldName;
          case "getMinHeight" -> -64;
          case "getMaxHeight" -> 320;
          default -> null;
        }
    );

    Object blockData = Proxy.newProxyInstance(
        Leaves.class.getClassLoader(),
        new Class<?>[]{Leaves.class, Waterlogged.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "isPersistent" -> persistent;
          case "isWaterlogged" -> waterlogged;
          case "getMaterial" -> material;
          default -> null;
        }
    );

    BlockState state = (BlockState) Proxy.newProxyInstance(
        BlockState.class.getClassLoader(),
        new Class<?>[]{BlockState.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getType" -> material;
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
          case "getType" -> material;
          case "getBlockData" -> blockData;
          case "getState" -> state;
          default -> null;
        }
    );
  }

  @Test
  void naturalLeavesAreEligibleWhenUnprotectedAndChunkLoaded() {
    Block block = mockBlock(Material.OAK_LEAVES, false, false, true, "world", 10, 70, 15);
    PlayerPlacedWoodRegistry registry = new PlayerPlacedWoodRegistry();
    ProtectedHomeArea home = new ProtectedHomeArea("world", 0, 64, 0, 5);

    assertTrue(ObstructionPolicy.isEligibleLeaf(block, home, registry));
  }

  @Test
  void persistentLeavesAreRejectedAsPlayerPlaced() {
    Block block = mockBlock(Material.OAK_LEAVES, true, false, true, "world", 10, 70, 15);
    assertFalse(ObstructionPolicy.isEligibleLeaf(block, null, null));
  }

  @Test
  void waterloggedLeavesAreRejected() {
    Block block = mockBlock(Material.OAK_LEAVES, false, true, true, "world", 10, 70, 15);
    assertFalse(ObstructionPolicy.isEligibleLeaf(block, null, null));
  }

  @Test
  void nonLeavesMaterialsAreRejected() {
    Block log = mockBlock(Material.OAK_LOG, false, false, true, "world", 10, 70, 15);
    assertFalse(ObstructionPolicy.isEligibleLeaf(log, null, null));

    Block dirt = mockBlock(Material.DIRT, false, false, true, "world", 10, 70, 15);
    assertFalse(ObstructionPolicy.isEligibleLeaf(dirt, null, null));
  }

  @Test
  void unloadedChunkIsRejected() {
    Block block = mockBlock(Material.OAK_LEAVES, false, false, false, "world", 10, 70, 15);
    assertFalse(ObstructionPolicy.isEligibleLeaf(block, null, null));
  }

  @Test
  void trackedPlayerPlacementIsRejected() {
    Block block = mockBlock(Material.OAK_LEAVES, false, false, true, "world", 10, 70, 15);
    PlayerPlacedWoodRegistry registry = new PlayerPlacedWoodRegistry();
    registry.record("world", 10, 70, 15);

    assertFalse(ObstructionPolicy.isEligibleLeaf(block, null, registry));
  }

  @Test
  void protectedHomeAreaIsRejected() {
    Block block = mockBlock(Material.OAK_LEAVES, false, false, true, "world", 2, 65, 2);
    ProtectedHomeArea home = new ProtectedHomeArea("world", 0, 64, 0, 10);

    assertFalse(ObstructionPolicy.isEligibleLeaf(block, home, null));
  }
}
