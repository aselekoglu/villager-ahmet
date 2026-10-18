package dev.ahmet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import org.bukkit.entity.Villager;
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
        NavigatorParameters defaults=new NavigatorParameters(),locals=new NavigatorParameters();
        Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
            case "getDefaultParameters"->defaults;case "getLocalParameters"->locals;case "setTarget"->{navigatorTarget.set((Location)a[0]);navigating.set(true);yield null;}
            case "isNavigating"->navigating.get();case "cancelNavigation"->{navigatorCancels.incrementAndGet();navigating.set(false);yield null;}default->null;});
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->current.get();case "isSleeping"->false;case "getWorld"->world;case "sleep"->{sleepCalls.incrementAndGet();yield true;}case "toString"->"sleep seam villager";default->null;});
        NPC npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
        TestChunks chunks=new TestChunks();
        chunks.put(0,64,0,Bed.Part.FOOT,org.bukkit.block.BlockFace.NORTH,false);
        chunks.put(0,64,-1,Bed.Part.HEAD,org.bukkit.block.BlockFace.NORTH,false);
        Location foot=new Location(world,0,64,0),home=new Location(world,1.79,64,.5);
        ScheduleAdapter adapter=new ScheduleAdapter(()->1_000L);
        adapter.tick(npc,home,foot,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertEquals(.10,defaults.distanceMargin(),.0001);
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
    void bedRetryExhaustionKeepsCooldownEvenWhenFallbackHomeIsAlreadyReached() {
        World world=world("bed-cooldown");
        Location home=new Location(world,1.79,64,.5);
        AtomicLong now=new AtomicLong(0);
        AtomicInteger targetCount=new AtomicInteger(),cancelCount=new AtomicInteger(),sleepCount=new AtomicInteger();
        NavigatorParameters defaults=new NavigatorParameters(),locals=new NavigatorParameters();
        AtomicReference<Boolean> navigating=new AtomicReference<>(false);
        Navigator navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(p,m,a)->switch(m.getName()){
            case "getDefaultParameters"->defaults;case "getLocalParameters"->locals;case "setTarget"->{targetCount.incrementAndGet();navigating.set(true);yield null;}
            case "isNavigating"->navigating.get();case "cancelNavigation"->{cancelCount.incrementAndGet();navigating.set(false);yield null;}default->null;});
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->home;case "isSleeping"->false;case "getWorld"->world;case "sleep"->{sleepCount.incrementAndGet();yield true;}default->null;});
        NPC npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "getEntity"->villager;case "getNavigator"->navigator;default->null;});
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
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertEquals(3,targetCount.get(),"home arrival must not clear the bed retry cooldown");
        now.addAndGet(ScheduleRetryPolicy.COOLDOWN_MILLIS-1);
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertEquals(3,targetCount.get());
        now.incrementAndGet();
        adapter.tick(npc,home,bed,true,false,false,32,loc->true,(from,to)->null,chunks);
        assertEquals(4,targetCount.get());
        assertEquals(0,sleepCount.get());
    }

    private static NPC fakeNpc(Location current){
        Villager villager=(Villager)Proxy.newProxyInstance(Villager.class.getClassLoader(),new Class<?>[]{Villager.class},(p,m,a)->switch(m.getName()){
            case "getLocation"->current;case "isSleeping"->false;case "getWorld"->current.getWorld();case "toString"->"test villager";default->null;});
        return (NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(p,m,a)->switch(m.getName()){
            case "isSpawned"->true;case "getEntity"->villager;case "toString"->"test NPC";default->null;});
    }

    private static World world(String name){return (World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()){
        case "getName"->name;case "getMinHeight"->-64;case "getMaxHeight"->320;case "getTime"->13_000L;case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->name;default->null;});}

    private static final class TestChunks implements ScheduleAdapter.ChunkAccess {
        final Map<String,BlockData> beds=new HashMap<>();
        final java.util.Set<String> unloaded=new java.util.HashSet<>();
        final AtomicInteger dataReads=new AtomicInteger();
        void put(int x,int y,int z,Bed.Part part,org.bukkit.block.BlockFace face,boolean occupied){beds.put(key(x,y,z),bed(part,face,occupied));}
        public boolean loaded(World world,int x,int z){return !unloaded.contains((x>>4)+","+(z>>4));}
        public Material material(World world,int x,int y,int z){return Material.RED_BED;}
        public BlockData data(World world,int x,int y,int z){dataReads.incrementAndGet();return beds.get(key(x,y,z));}
        public boolean safeStandingCell(Location location){return true;}
        private static String key(int x,int y,int z){return x+","+y+","+z;}
        private static BlockData bed(Bed.Part part,org.bukkit.block.BlockFace face,boolean occupied){
            return (BlockData)Proxy.newProxyInstance(Bed.class.getClassLoader(),new Class<?>[]{Bed.class},(p,m,a)->switch(m.getName()){
                case "getPart"->part;case "getFacing"->face;case "isOccupied"->occupied;case "getAsString"->"minecraft:red_bed";case "getMaterial"->Material.RED_BED;
                case "getFaces"->java.util.Set.of(face,face.getOppositeFace());case "clone"->p;case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->null;});
        }
    }
}
