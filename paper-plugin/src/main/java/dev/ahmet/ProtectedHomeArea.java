package dev.ahmet;

/** Conservative spherical exclusion around an existing player-built home. */
record ProtectedHomeArea(String world,int centerX,int centerY,int centerZ,int radius){
  ProtectedHomeArea{
    if(world==null||world.isBlank())throw new IllegalArgumentException("World is required");
    if(radius<0||radius>32)throw new IllegalArgumentException("Protection radius must be between 0 and 32");
  }

  boolean contains(String candidateWorld,int x,int y,int z){
    if(radius==0||!world.equals(candidateWorld))return false;
    double dx=(x+.5)-(centerX+.5),dy=(y+.5)-(centerY+.5),dz=(z+.5)-(centerZ+.5);
    return dx*dx+dy*dy+dz*dz<=radius*radius;
  }
}
