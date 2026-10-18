package dev.ahmet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathfinderType;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleAdapterTest {
    @Test
    void footAndHeadResolveTheirPartnerInTheFacingDirection() {
        World world=world("bed-test");
        TestChunks chunks=new TestChunks();
        chunks.put(10,64,10,Bed.Part.FOOT,org.bukkit.block.BlockFace.EAST,false);
        chunks.put(11,64,10,Bed.Part.HEAD,org.bukkit.block.BlockFace.EAST,false);
        ScheduleAdapter adapter=new ScheduleAdapter();
        ScheduleAdapter.Pair fromFoot=adapter.pair(world,10,64,10,chunks);
        ScheduleAdapter.Pair fromHead=adapter.pair(world,11,64,10,chunks);
        assertNull(fromFoot.error()); assertEquals(10,fromFoot.footX());
        assertNull(fromHead.error()); assertEquals(10,fromHead.footX());
    }

    @Test
    void unloadedPossiblePartnerChunkIsRejectedBeforeAnyBlockDataRead() {
        World world=world("unloaded");
        TestChunks chunks=new TestChunks();
        chunks.put(15,64,10,Bed.Part.FOOT,org.bukkit.block.BlockFace.EAST,false);
        chunks.unloaded.add("1,0");
        ScheduleAdapter.Pair pair=new ScheduleAdapter().pair(world,15,64,10,chunks);
        assertNotNull(pair.error());
        assertEquals(0,chunks.dataReads.get());
    }

    @Test
    void bothBedHalvesMustBeUnoccupiedAndMatch() {
        World world=world("occupied");
        TestChunks chunks=new TestChunks();
        chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,false);
        chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.NORTH,true);
        assertEquals("bed is occupied",new ScheduleAdapter().pair(world,0,64,0,chunks).error());
    }

    @Test
    void invalidHeightAndMismatchedFacingFailBeforeUnsafeAccess() {
        World world=world("height");
        TestChunks chunks=new TestChunks();
        assertNotNull(new ScheduleAdapter().pair(world,0,320,0,chunks).error());
        assertEquals(0,chunks.dataReads.get());
        chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,false);
        chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.SOUTH,false);
        assertEquals("bed halves do not match",new ScheduleAdapter().pair(world,0,64,0,chunks).error());
    }

    @Test
    void wanderRadiusChecksCenteredDestinationAndKeepsValidHalfBlockHome() {
        World world=world("wander-radius",10_000L);
        AtomicInteger issued=new AtomicInteger();
        AtomicReference<Location> issuedTarget=new AtomicReference<>();
        Location integerHome=new Location(world,0,64,0);
        ScheduleAdapter integerAdapter=new ScheduleAdapter(()->1_000L);
        integerAdapter.tick(movingNpc(integerHome,issued,issuedTarget),integerHome,null,true,false,false,32,
                loc->loc.getBlockX()==4&&loc.getBlockZ()==4,(from,to)->null,new TestChunks());
        assertEquals(0,issued.get(),"centered target would exceed six blocks from integer home");
        assertTrue(integerAdapter.target()==null);

        issued.set(0);issuedTarget.set(null);
        Location centeredHome=new Location(world,.5,64,.5);
        ScheduleAdapter centeredAdapter=new ScheduleAdapter(()->1_000L);
        centeredAdapter.tick(movingNpc(centeredHome,issued,issuedTarget),centeredHome,null,true,false,false,32,
                loc->loc.getBlockX()==4&&loc.getBlockZ()==4,(from,to)->null,new TestChunks());
        assertEquals(1,issued.get());
        assertNotNull(issuedTarget.get());
        assertTrue(issuedTarget.get().distance(centeredHome)<=6.0);
    }

    @Test
    void arrivalRequiresCenteredStandingCellRatherThanNearbySphere() {
        World world=world("arrival");
        Location target=new Location(world,4.5,64,4.5);
        assertTrue(ScheduleAdapter.atStandingCell(new Location(world,4.65,64,4.4),target));
        assertFalse(ScheduleAdapter.atStandingCell(new Location(world,5.4,64,4.5),target));
        assertFalse(ScheduleAdapter.atStandingCell(new Location(world,4.5,64.3,4.5),target));
    }

    @Test
    void bedRouteUsesTheSameTightArrivalForRouteAndSleepAndKeepsOwnershipOutsideIt() {
        World world=world("bed-arrival");
        Location target=new Location(world,4.5,64,4.5);
        Location foot=new Location(world,4,64,3);
        Location betweenOldAndNewMargins=new Location(world,4.8,64,4.5);
        assertFalse(ScheduleAdapter.routeArrived(betweenOldAndNewMargins,target,true,.30));
        assertFalse(ScheduleAdapter.bedArrival(betweenOldAndNewMargins,target,foot));
        assertEquals(.10,ScheduleAdapter.navigationMargin(true),0.0001);
        assertEquals(.25,ScheduleAdapter.navigationMargin(false),0.0001);
        assertTrue(ScheduleAdapter.routeArrived(new Location(world,4.8,64,4.5),target,false,.30));
        ScheduleState state=new ScheduleState();
        state.changePhase(VillagerSchedule.Phase.SLEEP);state.target(target);state.navigationStarted();
        assertTrue(state.ownsNavigation());
        assertEquals(target,state.target());
        Location withinGate=new Location(world,4.65,64,4.5);
        assertTrue(ScheduleAdapter.routeArrived(withinGate,target,true,.30));
        assertTrue(ScheduleAdapter.bedArrival(withinGate,target,foot));
    }

    @Test
    void adapterKeepsBedRouteUntilStrictArrivalThenSleepsFromAdjacentCell() {
        World world=world("sleep-seam");
        AtomicReference<Location> current=new AtomicReference<>(new Location(world,1.79,64,.5));
        AtomicInteger sleepCalls=new AtomicInteger();
        AtomicInteger navigatorCancels=new AtomicInteger();
        AtomicReference<Location> navigatorTarget=new AtomicReference<>();
        AtomicReference<Boolean> navigating=new AtomicReference<>(false);
        NavigatorParameters defaults=taskDefaults(),locals=new NavigatorParameters();
        Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
            case "getDefaultParameters"->defaults;case "getLocalParameters"->locals;case "setTarget"->{copyPrecision(locals,defaults);navigatorTarget.set((Location)a[0]);navigating.set(true);yield null;}
            case "isNavigating"->navigating.get();case "cancelNavigation"->{navigatorCancels.incrementAndGet();navigating.set(false);yield null;}default->null;});
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->current.get();case "isSleeping"->false;case "getWorld"->world;case "sleep"->{sleepCalls.incrementAndGet();yield true;}case "toString"->"sleep seam villager";default->null;});
        NPC npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "isFlyable"->false;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
        TestChunks chunks=new TestChunks();
        chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,false);
        chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.NORTH,false);
        Location foot=new Location(world,0,64,0),home=new Location(world,1.79,64,.5);
        ScheduleAdapter adapter=new ScheduleAdapter(()->1_000L);
        adapter.tick(npc,home,foot,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertTaskDefaults(defaults);
        assertRouteParameters(locals,true);
        assertTrue(adapter.ownsNavigation());
        Location firstTarget=navigatorTarget.get();
        assertNotNull(firstTarget);
        adapter.tick(npc,home,foot,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertTrue(adapter.ownsNavigation());
        assertEquals(firstTarget,adapter.target());
        assertEquals(0,navigatorCancels.get(),"between the old and new margins the bed route must remain active");
        assertEquals(0,sleepCalls.get());
        current.set(new Location(world,1.65,64,.5));
        adapter.tick(npc,home,foot,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertEquals(1,sleepCalls.get());
        assertEquals(1,navigatorCancels.get());
        assertTrue(adapter.ownsSleep());
        assertFalse(adapter.ownsNavigation());
    }

    @Test
    void missingBedAndUnavailableHomeRouteRespectCooldownAcrossRepeatedAdapterTicks() {
        World world=world("fallback");
        Location current=new Location(world,20,64,20),home=new Location(world,0,64,0);
        AtomicLong now=new AtomicLong(1_000);
        ScheduleAdapter adapter=new ScheduleAdapter(now::get);
        AtomicInteger plannerCalls=new AtomicInteger();
        NPC npc=fakeNpc(current);
        for(int i=0;i<20;i++) adapter.tick(npc,home,null,true,false,false,32,loc->false,
                (from,to)->{plannerCalls.incrementAndGet();return null;},new TestChunks());
        assertEquals(1,plannerCalls.get());
        assertTrue(adapter.status().contains("no loaded safe route home"),adapter.status());
        now.set(15_999);
        adapter.tick(npc,home,null,true,false,false,32,loc->false,(from,to)->{plannerCalls.incrementAndGet();return null;},new TestChunks());
        assertEquals(1,plannerCalls.get());
        now.set(16_000);
        adapter.tick(npc,home,null,true,false,false,32,loc->false,(from,to)->{plannerCalls.incrementAndGet();return null;},new TestChunks());
        assertEquals(2,plannerCalls.get());
    }

    @Test
    void bedStatusTracksTheCurrentFailureAcrossRebindAndValidationChanges() {
        World world=world("bed-status"),otherWorld=world("bed-status-other");
        Location home=new Location(world,.5,64,.5),bed=new Location(world,0,64,0);
        ScheduleAdapter adapter=new ScheduleAdapter(()->1_000L);
        TestChunks chunks=new TestChunks();
        NPC npc=fakeNpc(home);
        java.util.function.Predicate<Location> safe=loc->true;
        ScheduleAdapter.RoutePlanner noRoute=(from,to)->null;

        adapter.tick(npc,home,bed,true,false,false,32,safe,noRoute,chunks);
        assertTrue(adapter.status().contains("bed block is missing"),adapter.status());

        chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,true);
        chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.NORTH,true);
        adapter.bedRebound();
        adapter.tick(npc,home,bed,true,false,false,32,safe,noRoute,chunks);
        assertTrue(adapter.status().contains("bed is occupied"),adapter.status());
        assertFalse(adapter.status().contains("bed block is missing"),adapter.status());

        chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,false);
        chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.NORTH,false);
        chunks.unsafe.add("1,0");chunks.unsafe.add("-1,0");chunks.unsafe.add("0,1");chunks.unsafe.add("0,-1");
        adapter.tick(npc,home,bed,true,false,false,32,safe,noRoute,chunks);
        assertTrue(adapter.status().contains("bed has no loaded safe approach"),adapter.status());
        assertFalse(adapter.status().contains("bed is occupied"),adapter.status());

        chunks.unsafe.clear();chunks.unloaded.add("0,0");
        adapter.tick(npc,home,bed,true,false,false,32,safe,noRoute,chunks);
        assertTrue(adapter.status().contains("bound bed chunk is unloaded"),adapter.status());
        assertFalse(adapter.status().contains("bed has no loaded safe approach"),adapter.status());

        chunks.unloaded.clear();
        adapter.tick(npc,home,new Location(otherWorld,0,64,0),true,false,false,32,safe,noRoute,chunks);
        assertTrue(adapter.status().contains("bound bed is in another world"),adapter.status());
        assertFalse(adapter.status().contains("bound bed chunk is unloaded"),adapter.status());
    }

    @Test
    void bedRetryExhaustionKeepsCooldownEvenWhenFallbackHomeIsAlreadyReached() {
        World world=world("bed-cooldown");
        Location home=new Location(world,1.79,64,.5);
        AtomicLong now=new AtomicLong(0);
        AtomicInteger targetCount=new AtomicInteger(),cancelCount=new AtomicInteger(),sleepCount=new AtomicInteger();
        NavigatorParameters defaults=taskDefaults(),locals=new NavigatorParameters();
        AtomicReference<Boolean> navigating=new AtomicReference<>(false);
        Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
            case "getDefaultParameters"->defaults;case "getLocalParameters"->locals;case "setTarget"->{copyPrecision(locals,defaults);targetCount.incrementAndGet();navigating.set(true);yield null;}
            case "isNavigating"->navigating.get();case "cancelNavigation"->{cancelCount.incrementAndGet();navigating.set(false);yield null;}default->null;});
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->home;case "isSleeping"->false;case "getWorld"->world;case "sleep"->{sleepCount.incrementAndGet();yield true;}default->null;});
        NPC npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "isFlyable"->false;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
        TestChunks chunks=new TestChunks();
        chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,false);
        chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.NORTH,false);
        ScheduleAdapter adapter=new ScheduleAdapter(now::get);
        Location bed=new Location(world,0,64,0);
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertEquals(1,targetCount.get());
        for(int failure=0;failure<3;failure++){
            now.addAndGet(15_000);
            adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
            if(failure<2){
                now.addAndGet(2_000);
                adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
                adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
            }
        }
        assertEquals(3,targetCount.get());
        assertTrue(adapter.status().contains("bed unreachable"),adapter.status());
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertTrue(adapter.status().contains("bed unreachable"),adapter.status());
        assertTrue(adapter.status().contains("bed route retry cooldown"),adapter.status());
        assertEquals(3,targetCount.get(),"home arrival must not clear the bed retry cooldown");
        now.addAndGet(ScheduleRetryPolicy.COOLDOWN_MILLIS-1);
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertTrue(adapter.status().contains("bed unreachable"),adapter.status());
        assertTrue(adapter.status().contains("bed route retry cooldown"),adapter.status());
        assertEquals(3,targetCount.get());
        now.incrementAndGet();
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertFalse(adapter.status().contains("bed unreachable"),adapter.status());
        assertFalse(adapter.status().contains("bed route retry cooldown"),adapter.status());
        assertEquals(4,targetCount.get());
        assertEquals(0,sleepCount.get());
    }

    @Test
    void regularRouteClonesCitizensPrecisionAndRestoresTaskDefaults() {
        World world=world("regular-precision",10_000L);Location home=new Location(world,.5,64,.5);
        NavigatorParameters defaults=taskDefaults(),locals=new NavigatorParameters();AtomicReference<Boolean> navigating=new AtomicReference<>(false);
        Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
            case "getDefaultParameters"->defaults;case "getLocalParameters"->locals;case "setTarget"->{copyPrecision(locals,defaults);navigating.set(true);yield null;}
            case "isNavigating"->navigating.get();case "cancelNavigation"->{navigating.set(false);yield null;}default->null;});
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->home;case "isSleeping"->false;case "getWorld"->world;default->null;});
        NPC npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "isFlyable"->false;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
        new ScheduleAdapter(()->1_000L).tick(npc,home,null,true,false,false,32,loc->true,(from,to)->null,new TestChunks());
        assertRouteParameters(locals,false);assertTaskDefaults(defaults);
    }

    @Test
    void taskDefaultsRestoreWhenSetTargetThrows() {
        World world=world("precision-throws");Location home=new Location(world,1.79,64,.5),bed=new Location(world,0,64,0);
        NavigatorParameters defaults=taskDefaults();Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
            case "getDefaultParameters"->defaults;case "setTarget"->{throw new IllegalStateException("simulated target failure");}default->null;});
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->home;case "isSleeping"->false;case "getWorld"->world;default->null;});
        NPC npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "isFlyable"->false;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
        TestChunks chunks=new TestChunks();chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,false);chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.NORTH,false);
        assertThrows(IllegalStateException.class,()->new ScheduleAdapter(()->1_000L).tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks));
        assertTaskDefaults(defaults);
    }

    @Test
    void completedPlannedRouteGetsOneExactIterableFinalLeg() {
        RouteHarness h=new RouteHarness("exact-leg");
        ScheduleAdapter adapter=new ScheduleAdapter(()->1_000L);
        h.tick(adapter);
        Location planned=h.locationTarget.get();assertNotNull(planned);
        h.current.set(planned.clone().add(-.45,0,-.45));h.navigating.set(false);
        h.tick(adapter);
        assertNotNull(h.exactTarget.get());
        Vector vector=h.exactTarget.get().iterator().next();
        assertEquals(planned.toVector(),vector);
        assertEquals(1,h.exactCalls.get());assertRouteParameters(h.locals,false);assertTaskDefaults(h.defaults);
        h.tick(adapter);
        assertEquals(1,h.exactCalls.get(),"an issued exact leg must not be reissued before arrival");
    }

    @Test
    void exactFinalLegRejectsUnsafeSegmentAndFarOrDifferentHeightEndpoints() {
        for(String mode:new String[]{"unsafe","far","height","world"}){
            RouteHarness h=new RouteHarness("exact-"+mode);ScheduleAdapter adapter=new ScheduleAdapter(()->1_000L);h.tick(adapter);
            Location planned=h.locationTarget.get();
            h.current.set(switch(mode){case "far"->planned.clone().add(-1.1,0,0);case "height"->planned.clone().add(-.45,.3,-.45);case "world"->new Location(world("other-world"),planned.getX()-.45,planned.getY(),planned.getZ()-.45);default->planned.clone().add(-.45,0,-.45);});
            if(mode.equals("unsafe"))h.chunks.unsafe.add((int)Math.floor(planned.getX())+","+(int)Math.floor(planned.getZ()));
            h.navigating.set(false);h.tick(adapter);
            assertEquals(0,h.exactCalls.get(),mode+" segment must not receive an exact leg");
        }
    }

    private static final class RouteHarness {
        final World world;final Location home,currentValue;final AtomicReference<Location> current=new AtomicReference<>(),locationTarget=new AtomicReference<>();
        final AtomicReference<Iterable<Vector>> exactTarget=new AtomicReference<>();final AtomicReference<Boolean> navigating=new AtomicReference<>(false);
        final AtomicInteger exactCalls=new AtomicInteger();final NavigatorParameters defaults=taskDefaults(),locals=new NavigatorParameters();final TestChunks chunks=new TestChunks();final NPC npc;
        @SuppressWarnings("unchecked") RouteHarness(String name){
            world=world(name,10_000L);home=new Location(world,.5,120,.5);currentValue=home;current.set(home);
            Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
                case "getDefaultParameters"->defaults;case "getLocalParameters"->locals;
                case "setTarget"->{copyPrecision(locals,defaults);if(a[0] instanceof Location loc)locationTarget.set(loc.clone());else {exactTarget.set((Iterable<Vector>)a[0]);exactCalls.incrementAndGet();}navigating.set(true);yield null;}
                case "isNavigating"->navigating.get();case "cancelNavigation"->{navigating.set(false);yield null;}default->null;});
            Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
                case "getLocation"->current.get();case "isSleeping"->false;case "getWorld"->world;default->null;});
            npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
                case "isSpawned"->true;case "isFlyable"->false;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
        }
        void tick(ScheduleAdapter adapter){adapter.tick(npc,home,null,true,false,false,32,loc->true,(from,to)->null,chunks);}
    }

    private static NavigatorParameters taskDefaults(){return new NavigatorParameters().pathfinderType(PathfinderType.MINECRAFT).distanceMargin(.75).pathDistanceMargin(.75).destinationTeleportMargin(4).stationaryTicks(40);}
    private static void copyPrecision(NavigatorParameters target,NavigatorParameters source){target.pathfinderType(source.pathfinderType()).distanceMargin(source.distanceMargin()).pathDistanceMargin(source.pathDistanceMargin()).destinationTeleportMargin(source.destinationTeleportMargin()).stationaryTicks(source.stationaryTicks());}
    private static void assertTaskDefaults(NavigatorParameters p){assertEquals(PathfinderType.MINECRAFT,p.pathfinderType());assertEquals(.75,p.distanceMargin(),.0001);assertEquals(.75,p.pathDistanceMargin(),.0001);assertEquals(4,p.destinationTeleportMargin(),.0001);assertEquals(40,p.stationaryTicks());}
    private static void assertRouteParameters(NavigatorParameters p,boolean bed){double margin=bed?.10:.25;assertEquals(PathfinderType.CITIZENS,p.pathfinderType());assertEquals(margin,p.distanceMargin(),.0001);assertEquals(0,p.pathDistanceMargin(),.0001);assertEquals(-1,p.destinationTeleportMargin(),.0001);assertEquals(-1,p.stationaryTicks());}

    private static NPC fakeNpc(Location current){
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->current;case "isSleeping"->false;case "getWorld"->current.getWorld();case "toString"->"test villager";default->null;});
        return (NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "getEntity"->villager;case "toString"->"test NPC";default->null;});
    }

    private static NPC movingNpc(Location current,AtomicInteger targetCount,AtomicReference<Location> target){
        AtomicReference<Boolean> navigating=new AtomicReference<>(false);
        NavigatorParameters defaults=taskDefaults(),locals=new NavigatorParameters();
        Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
            case "getDefaultParameters"->defaults;case "getLocalParameters"->locals;case "setTarget"->{copyPrecision(locals,defaults);targetCount.incrementAndGet();target.set((Location)a[0]);navigating.set(true);yield null;}
            case "isNavigating"->navigating.get();case "cancelNavigation"->{navigating.set(false);yield null;}default->null;});
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->current;case "isSleeping"->false;case "getWorld"->current.getWorld();default->null;});
        return (NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "isFlyable"->false;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
    }

    private static World world(String name){return world(name,13_000L);}
    private static World world(String name,long time){return (World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()){
        case "getName"->name;case "getMinHeight"->-64;case "getMaxHeight"->320;case "getTime"->time;case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->name;default->null;});}

    private static final class TestChunks implements ScheduleAdapter.ChunkAccess {
        final Map<String,BlockData> beds=new HashMap<>();
        final java.util.Set<String> unloaded=new java.util.HashSet<>();
        final java.util.Set<String> unsafe=new java.util.HashSet<>();
        final AtomicInteger dataReads=new AtomicInteger();
        void put(int x,int y,int z,Bed.Part part,org.bukkit.block.BlockFace face,boolean occupied){beds.put(key(x,y,z),bed(part,face,occupied));}
        public boolean loaded(World world,int x,int z){return !unloaded.contains((x>>4)+","+(z>>4));}
        public Material material(World world,int x,int y,int z){return Material.RED_BED;}
        public BlockData data(World world,int x,int y,int z){dataReads.incrementAndGet();return beds.get(key(x,y,z));}
        public boolean safeStandingCell(Location location){return !unsafe.contains(location.getBlockX()+","+location.getBlockZ());}
        private static String key(int x,int y,int z){return x+","+y+","+z;}
        private static BlockData bed(Bed.Part part,org.bukkit.block.BlockFace face,boolean occupied){
            return (BlockData)Proxy.newProxyInstance(Bed.class.getClassLoader(),new Class<?>[]{Bed.class},(p,m,a)->switch(m.getName()){
                case "getPart"->part;case "getFacing"->face;case "isOccupied"->occupied;case "getAsString"->"minecraft:red_bed";case "getMaterial"->Material.RED_BED;
                case "getFaces"->java.util.Set.of(face,face.getOppositeFace());case "clone"->p;case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->null;});
        }
    }
}
