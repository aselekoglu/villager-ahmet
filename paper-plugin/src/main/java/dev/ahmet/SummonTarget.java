package dev.ahmet;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.Predicate;

final class SummonTarget {
  private SummonTarget() {}

  static Location find(Location origin, Predicate<Location> isSafe) {
    if(origin==null || isSafe==null)return null;
    Vector forward=origin.getDirection().setY(0);
    if(forward.lengthSquared()<1.0e-6)forward=new Vector(0,0,1);
    forward.normalize();
    Vector side=new Vector(-forward.getZ(),0,forward.getX());
    List<Vector> offsets=List.of(
        forward.clone().multiply(-1.5),side.clone().multiply(1.5),side.clone().multiply(-1.5),
        forward.clone().multiply(-2.5),forward.clone().multiply(1.5),
        side.clone().multiply(2.5),side.clone().multiply(-2.5));
    int baseY=origin.getBlockY();
    int[] verticalOffsets={0,1,-1};
    for(Vector offset:offsets){
      int blockX=(int)Math.floor(origin.getX()+offset.getX());
      int blockZ=(int)Math.floor(origin.getZ()+offset.getZ());
      for(int verticalOffset:verticalOffsets){
        Location candidate=new Location(origin.getWorld(),blockX+0.5,baseY+verticalOffset,blockZ+0.5,origin.getYaw(),0);
        if(isSafe.test(candidate))return candidate;
      }
    }
    return null;
  }
}
