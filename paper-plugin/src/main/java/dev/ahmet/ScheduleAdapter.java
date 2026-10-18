package dev.ahmet;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;

import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Owns schedule phases, navigation, bed validation, sleep, retries, and preemption. Main-thread only. */
final class ScheduleAdapter {
    interface RoutePlanner { Location towardHome(Location current, Location home); }
    interface ChunkAccess {
        boolean loaded(World world, int blockX, int blockZ);
        Material material(World world, int x, int y, int z);
        org.bukkit.block.data.BlockData data(World world, int x, int y, int z);
        boolean safeStandingCell(Location location);
    }

    record BedBinding(Location foot, String error) {
        static BedBinding failure(String error) { return new BedBinding(null, error); }
        static BedBinding success(Location foot) { return new BedBinding(foot, null); }
        boolean valid() { return error == null; }
    }

    private final ScheduleState state = new ScheduleState();
    private final LongSupplier clock;
    private boolean bedRoute;
    private String fallbackCause;
    private long bedRetryAt=-1;
    private final ScheduleRetryPolicy retries = new ScheduleRetryPolicy();
    private boolean preempted;

    ScheduleAdapter(){this(System::currentTimeMillis);}
    ScheduleAdapter(LongSupplier clock){this.clock=clock;}

    void bedRebound(){bedRetryAt=-1;retries.clear();}

    String status() {
        String phase = state.phase() == null ? "idle" : state.phase().name().toLowerCase(java.util.Locale.ROOT);
        String activity = state.ownsSleep() ? "sleeping" : state.ownsNavigation() ? "walking" : state.reason();
        String detail=activity;
        if(fallbackCause!=null&&!detail.contains(fallbackCause))detail+="; "+fallbackCause;
        return phase + " (" + detail + ")";
    }

    VillagerSchedule.Phase phase() { return state.phase(); }
    boolean ownsNavigation() { return state.ownsNavigation(); }
    boolean ownsSleep() { return state.ownsSleep(); }
    boolean ownsTurn() { return !preempted && state.phase()!=null; }
    Location target() { return state.target(); }

    void suspend(NPC npc, String reason) {
        preempted=true;
        cancelOwnedNavigation(npc);
        wakeOwned(npc);
        if(npc!=null&&npc.isSpawned()&&npc.getEntity() instanceof Villager villager&&villager.isSleeping())villager.wakeup();
        state.preempt(reason);
        retries.clear();
    }

    BedBinding bindBed(Block selected, NPC npc, Location home, int maxDistance, ChunkAccess access) {
        if (selected == null) return BedBinding.failure("look at an existing bed within 6 blocks");
        World world = selected.getWorld();
        int x = selected.getX(), y = selected.getY(), z = selected.getZ();
        if (!withinHeight(world, y) || !access.loaded(world, x, z)) return BedBinding.failure("bed chunk is not loaded");
        if (npc == null || !npc.isSpawned() || home == null) return BedBinding.failure("spawn Ahmet and establish his home first");
        if (npc.getEntity().getWorld() != world || home.getWorld() != world) return BedBinding.failure("bed must share Ahmet and home's world");
        if (home.distanceSquared(selected.getLocation()) > (double) maxDistance * maxDistance) return BedBinding.failure("bed exceeds configured home distance");
        Pair pair = pair(world, x, y, z, access);
        if (pair.error != null) return BedBinding.failure(pair.error);
        Location foot = new Location(world, pair.footX, pair.footY, pair.footZ);
        if (!hasSafeApproach(foot, access)) return BedBinding.failure("no loaded safe standing cell beside the bed");
        return BedBinding.success(foot);
    }

    void tick(NPC npc, Location home, Location bed, boolean enabled, boolean blocked, boolean returningHome,
              int maxDistance, Predicate<Location> safe, RoutePlanner planner, ChunkAccess access) {
        if (!enabled) { suspend(npc, "schedule disabled"); return; }
        if (blocked) { suspend(npc, "task/recovery preemption"); return; }
        if (returningHome) { suspend(npc, "manual return-home priority"); return; }
        if (npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof Villager villager)) return;
        preempted=false;
        Location current = villager.getLocation();
        if (home == null || home.getWorld() != current.getWorld()) return;
        VillagerSchedule.Phase phase = VillagerSchedule.phaseAt(current.getWorld().getTime());
        if (state.phase()!=phase) {
            cancelOwnedNavigation(npc);
            wakeOwned(npc);
            state.changePhase(phase);
            retries.clear();
            fallbackCause = null;
        }
        if (villager.isSleeping()&&!state.ownsSleep()) villager.wakeup();
        if (state.ownsSleep() && villager.isSleeping()) return;
        if (state.ownsSleep() && !villager.isSleeping()) state.wakeOwned(()->{});
        if (phase == VillagerSchedule.Phase.WAKE || phase == VillagerSchedule.Phase.WORK) {
            routeHome(npc, current, home, safe, planner);
            return;
        }
        if (phase == VillagerSchedule.Phase.WANDER) {
            if (current.distanceSquared(home) > 36.0) {
                fallbackCause = "wander radius exceeded";
                cancelOwnedNavigation(npc);
                routeHome(npc, current, home, safe, planner);
                return;
            }
            if (observeRoute(npc, current, 0.30, clock.getAsLong())) return;
            if (!retries.ready(clock.getAsLong())) return;
            Location waypoint = VillagerSchedule.findWanderWaypoint(current, home, state.waypointAttempt(),
                    candidate -> {
                        Location standing=centered(candidate);
                        return safe.test(standing)&&standing.distanceSquared(home)<=36.0;
                    });
            if (waypoint == null) { deferNoCandidate("no loaded safe wander waypoint"); return; }
            Location standing=centered(waypoint);
            if(!safe.test(standing)||standing.distanceSquared(home)>36.0){deferNoCandidate("no centered safe wander waypoint within home radius");return;}
            startRoute(npc, standing, safe, false);
            return;
        }
        long bedNow=clock.getAsLong();
        if(bedRetryAt>=0&&bedNow<bedRetryAt){fallbackHome(npc,current,home,safe,planner,"bed route retry cooldown");return;}
        if(bedRetryAt>=0){bedRetryAt=-1;retries.clear();}
        if (bed == null) { fallbackHome(npc,current,home,safe,planner,"no bed bound"); return; }
        String bedError = validateBoundBed(bed, current, home, maxDistance, access);
        if (bedError != null) { fallbackHome(npc,current,home,safe,planner,bedError); return; }
        Location standing = findBedApproach(bed, access);
        if (standing == null) { fallbackHome(npc,current,home,safe,planner,"bed has no loaded safe approach"); return; }
        standing = centered(standing);
        if (atStandingCell(current, standing)) {
            Pair pair = pair(bed.getWorld(), bed.getBlockX(), bed.getBlockY(), bed.getBlockZ(), access);
            if (pair.error != null) { fallbackHome(npc,current,home,safe,planner,pair.error); return; }
            Location footLocation=new Location(bed.getWorld(),pair.footX,pair.footY,pair.footZ);
            if (!bedArrival(current,standing,footLocation)) { return; }
            cancelOwnedNavigation(npc);
            Pair fresh=pair(bed.getWorld(),bed.getBlockX(),bed.getBlockY(),bed.getBlockZ(),access);
            if(fresh.error!=null){fallbackHome(npc,current,home,safe,planner,fresh.error);return;}
            if (villager.sleep(new Location(bed.getWorld(),fresh.footX,fresh.footY,fresh.footZ))) { state.sleepStarted();state.idle("sleeping");retries.succeeded(); }
            else { fallbackHome(npc,current,home,safe,planner,"native sleep rejected"); }
            return;
        }
        if (observeRoute(npc,current,0.20,clock.getAsLong())) {
            if(bedRetryAt>=0&&state.target()==null)fallbackHome(npc,current,home,safe,planner,"bed unreachable");
            return;
        }
        if (!retries.ready(clock.getAsLong())) return;
        startRoute(npc,standing,safe,true);
    }

    private void fallbackHome(NPC npc,Location current,Location home,Predicate<Location> safe,RoutePlanner planner,String cause){
        if(fallbackCause==null)fallbackCause=cause;
        if(bedRoute){cancelOwnedNavigation(npc);state.idle(cause);}
        routeHome(npc,current,home,safe,planner);
    }

    private void routeHome(NPC npc, Location current, Location home, Predicate<Location> safe, RoutePlanner planner) {
        if (current.distanceSquared(home) <= 6.25) { cancelOwnedNavigation(npc); state.idle(fallbackCause==null?"at home":"at home; "+fallbackCause); retries.succeeded(); return; }
        if (observeRoute(npc,current,0.35,clock.getAsLong())) return;
        if (!retries.ready(clock.getAsLong())) return;
        Location waypoint=planner.towardHome(current,home);
        if (waypoint==null||!safe.test(waypoint)) { deferNoCandidate("no loaded safe route home"); return; }
        startRoute(npc,centered(waypoint),safe,false);
    }

    private boolean observeRoute(NPC npc, Location current, double tolerance, long now) {
        Location target=state.target();if(target==null)return false;
        boolean arrived=routeArrived(current,target,bedRoute,tolerance);
        if(arrived){cancelOwnedNavigation(npc);state.arrived("waypoint reached");retries.succeeded();return false;}
        NavigationWatchdog.Outcome result=state.watchdog().observe(current.distanceSquared(target),now);
        if(result==NavigationWatchdog.Outcome.RETRY||result==NavigationWatchdog.Outcome.GIVE_UP){
            boolean failedBedRoute=bedRoute;
            cancelOwnedNavigation(npc);
            ScheduleRetryPolicy.Result retryResult=retries.failed(now);
            state.retry(now,retryResult==ScheduleRetryPolicy.Result.EXHAUSTED?"route retry budget exhausted":"route retry");
            if(retryResult==ScheduleRetryPolicy.Result.EXHAUSTED){
                state.idle("route retry budget exhausted");
                if(failedBedRoute){bedRetryAt=now+ScheduleRetryPolicy.COOLDOWN_MILLIS;retries.clear();}
            }
            return true;
        }
        return true;
    }

    private void startRoute(NPC npc, Location target, Predicate<Location> safe,boolean forBed) {
        if (target==null||npc==null||!npc.isSpawned()||!safe.test(target)||target.getWorld()!=npc.getEntity().getWorld()) return;
        if (!retries.ready(clock.getAsLong())) return;
        state.target(target.clone());bedRoute=forBed;state.watchdog().reset();
        double margin=navigationMargin(forBed);
        npc.getNavigator().getDefaultParameters().destinationTeleportMargin(-1).stationaryTicks(-1).distanceMargin(margin).pathDistanceMargin(margin);
        npc.getNavigator().getLocalParameters().destinationTeleportMargin(-1).stationaryTicks(-1).distanceMargin(margin).pathDistanceMargin(margin);
        npc.getNavigator().setTarget(target);state.navigationStarted();retries.started();
    }

    private void cancelOwnedNavigation(NPC npc) {
        boolean owned=state.ownsNavigation();
        state.navigationStopped(()->{if(npc!=null&&npc.isSpawned()&&npc.getNavigator().isNavigating())npc.getNavigator().cancelNavigation();});
        if(owned)bedRoute=false;
    }
    private void wakeOwned(NPC npc) {
        state.wakeOwned(()->{if(npc!=null&&npc.isSpawned()&&npc.getEntity() instanceof Villager villager&&villager.isSleeping())villager.wakeup();});
    }
    private void deferNoCandidate(String reason) { if(fallbackCause==null)fallbackCause=reason;retries.unavailable(clock.getAsLong());state.idle(reason); }

    private String validateBoundBed(Location bed,Location current,Location home,int maxDistance,ChunkAccess access){
        World world=bed.getWorld();int x=bed.getBlockX(),y=bed.getBlockY(),z=bed.getBlockZ();
        if(world!=current.getWorld()||world!=home.getWorld())return "bound bed is in another world";
        if(!withinHeight(world,y)||!access.loaded(world,x,z))return "bound bed chunk is unloaded";
        if(bed.distanceSquared(home)>(double)maxDistance*maxDistance)return "bound bed exceeds configured home distance";
        Pair pair=pair(world,x,y,z,access);return pair.error;
    }
    private Location findBedApproach(Location bed,ChunkAccess access){
        Pair pair=pair(bed.getWorld(),bed.getBlockX(),bed.getBlockY(),bed.getBlockZ(),access);if(pair.error!=null)return null;
        Location foot=new Location(bed.getWorld(),pair.footX,pair.footY,pair.footZ);
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){Location candidate=foot.clone().add(d[0],0,d[1]);if(access.safeStandingCell(candidate))return candidate;}return null;
    }
    private boolean hasSafeApproach(Location bed,ChunkAccess access){return findBedApproach(bed,access)!=null;}
    record Pair(int footX,int footY,int footZ,String error){}
    Pair pair(World world,int x,int y,int z,ChunkAccess access){
        if(!withinHeight(world,y)||!access.loaded(world,x,z))return new Pair(0,0,0,"bed chunk is not loaded or height is invalid");
        // Facing is stored in BlockData; preflight possible partner chunks before reading it.
        if(!access.loaded(world,x+1,z)||!access.loaded(world,x-1,z)||!access.loaded(world,x,z+1)||!access.loaded(world,x,z-1))return new Pair(0,0,0,"bed neighbor chunks must already be loaded");
        org.bukkit.block.data.BlockData raw=access.data(world,x,y,z);
        if(!(raw instanceof Bed selected))return new Pair(0,0,0,"bed block is missing");
        Vector direction=selected.getFacing().getDirection();
        int otherX=x,otherZ=z;
        if(selected.getPart()==Bed.Part.FOOT){otherX+=direction.getBlockX();otherZ+=direction.getBlockZ();}
        else {otherX-=direction.getBlockX();otherZ-=direction.getBlockZ();}
        if(!withinHeight(world,y)||!access.loaded(world,otherX,otherZ))return new Pair(0,0,0,"both bed halves must already be loaded");
        org.bukkit.block.data.BlockData secondRaw=access.data(world,otherX,y,otherZ);
        if(!(secondRaw instanceof Bed second)||access.material(world,x,y,z)!=access.material(world,otherX,y,otherZ)
                ||second.getFacing()!=selected.getFacing()||second.getPart()==selected.getPart())return new Pair(0,0,0,"bed halves do not match");
        if(selected.isOccupied()||second.isOccupied())return new Pair(0,0,0,"bed is occupied");
        return selected.getPart()==Bed.Part.FOOT?new Pair(x,y,z,null):new Pair(otherX,y,otherZ,null);
    }
    private static boolean withinHeight(World world,int y){return y>=world.getMinHeight()&&y<world.getMaxHeight();}
    private static Location centered(Location location){Location centered=location.clone();centered.setX(location.getBlockX()+.5);centered.setZ(location.getBlockZ()+.5);return centered;}
    static double navigationMargin(boolean forBed){return forBed?.10:.25;}
    static boolean routeArrived(Location current,Location target,boolean forBed,double ordinaryTolerance){
        return forBed?atStandingCell(current,target):atStandingCell(current,target,ordinaryTolerance);
    }
    static boolean bedArrival(Location current,Location standing,Location foot){
        if(!atStandingCell(current,standing))return false;
        double dx=current.getX()-(foot.getX()+.5),dy=current.getY()-foot.getY(),dz=current.getZ()-(foot.getZ()+.5);
        return dx*dx+dy*dy+dz*dz<=6.25;
    }
    static boolean atStandingCell(Location current,Location target){return atStandingCell(current,target,.20);}
    static boolean atStandingCell(Location current,Location target,double tolerance){return current.getWorld()==target.getWorld()&&Math.abs(current.getX()-target.getX())<=tolerance&&Math.abs(current.getZ()-target.getZ())<=tolerance&&Math.abs(current.getY()-target.getY())<=.20;}
}
