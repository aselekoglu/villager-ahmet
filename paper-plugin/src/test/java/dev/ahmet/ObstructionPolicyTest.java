package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ahmet.agent.ActionBudget;
import dev.ahmet.agent.ActionPreconditions;

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
    var actor=new org.bukkit.Location(block.getWorld(),block.getX(),block.getY(),block.getZ());
    var result=new ActionPreconditions((b,l)->false,ActionBudget.DEFAULT).validateClear(actor,block,false,0,0);
    assertFalse(result.isSuccess(), "Deterministic preconditions must reject persistent leaves too");
  }

  @Test
  void waterloggedLeavesAreRejected() {
    Block block = mockBlock(Material.OAK_LEAVES, false, true, true, "world", 10, 70, 15);
    assertFalse(ObstructionPolicy.isEligibleLeaf(block, null, null));
    var actor=new org.bukkit.Location(block.getWorld(),block.getX(),block.getY(),block.getZ());
    var result=new ActionPreconditions((b,l)->false,ActionBudget.DEFAULT).validateClear(actor,block,false,0,0);
    assertFalse(result.isSuccess(), "Deterministic preconditions must reject waterlogged leaves too");
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
  @Test
  void asyncObstructionOfferRejectsChangedBlockStateAtSameLocation() {
    var currentType=new java.util.concurrent.atomic.AtomicReference<>(Material.OAK_LEAVES);
    World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},
        (p,m,a)->switch(m.getName()){case "getName"->"world";case "isChunkLoaded"->true;default->null;});
    Object data=Proxy.newProxyInstance(org.bukkit.block.data.BlockData.class.getClassLoader(),
        new Class<?>[]{org.bukkit.block.data.BlockData.class},(p,m,a)->switch(m.getName()){
          case "getAsString"->"minecraft:"+currentType.get().name().toLowerCase();
          case "getMaterial"->currentType.get();default->null;});
    Block block=(Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},
        (p,m,a)->switch(m.getName()){case "getWorld"->world;case "getType"->currentType.get();case "getX"->4;case "getY"->70;case "getZ"->2;case "getBlockData"->data;default->null;});
    var candidate=new ObstructionPolicy.CandidateObstruction(block,false,1.0);
    assertTrue(ObstructionPolicy.matchesOfferedState(candidate,block));
    currentType.set(Material.SPRUCE_LEAVES);
    assertFalse(ObstructionPolicy.matchesOfferedState(candidate,block));
  }

  @Test
  void asynchronousOfferIsBoundToOriginalActorBlockPosition() {
    Block block=mockBlock(Material.OAK_LEAVES,false,false,true,"world",10,63,10);
    var offeredActor=new org.bukkit.Location(block.getWorld(),10.2,64,10.2);
    var candidate=new ObstructionPolicy.CandidateObstruction(block,true,1.0,offeredActor);
    assertTrue(ObstructionPolicy.matchesOfferedActor(candidate,offeredActor));
    var movedActor=new org.bukkit.Location(block.getWorld(),11.2,64,10.2);
    assertFalse(ObstructionPolicy.matchesOfferedActor(candidate,movedActor));
  }

}
