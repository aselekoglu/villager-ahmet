package dev.ahmet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Persistent coordinates of wood blocks placed by players since tracking was enabled. */
final class PlayerPlacedWoodRegistry {
  private final LinkedHashSet<BlockPosition> positions=new LinkedHashSet<>();

  boolean record(String world,int x,int y,int z){
    if(world==null||world.isBlank())throw new IllegalArgumentException("World is required");
    return positions.add(new BlockPosition(world,x,y,z));
  }

  boolean contains(String world,int x,int y,int z){
    return world!=null&&positions.contains(new BlockPosition(world,x,y,z));
  }

  int size(){return positions.size();}

  List<String> serialize(){
    return positions.stream().sorted(Comparator.comparing(BlockPosition::world)
        .thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z))
        .map(position->position.world()+"\t"+position.x()+"\t"+position.y()+"\t"+position.z()).toList();
  }

  void load(List<String> encoded){
    if(encoded==null)throw new IllegalStateException("Player-placed wood registry is missing");
    LinkedHashSet<BlockPosition> loaded=new LinkedHashSet<>();
    for(String entry:encoded){
      String[] parts=entry==null?new String[0]:entry.split("\\t",-1);
      if(parts.length!=4||parts[0].isBlank())throw new IllegalStateException("Player-placed wood marker is invalid");
      try{loaded.add(new BlockPosition(parts[0],Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3])));}
      catch(NumberFormatException error){throw new IllegalStateException("Player-placed wood marker has invalid coordinates",error);}
    }
    positions.clear();positions.addAll(loaded);
  }

  private record BlockPosition(String world,int x,int y,int z){}
}
