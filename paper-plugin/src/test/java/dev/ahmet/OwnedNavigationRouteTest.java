package dev.ahmet;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.PathStrategy;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OwnedNavigationRouteTest {
    @Test
    void retainsNormalizedNativeChestRouteAndRejectsSameDestinationReplacement() {
        World world=world();
        Location logicalChest=new Location(world,12,120,-4);
        Location nativeChestTarget=new Location(world,12,121,-4);
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();

        route.issue(harness.npc,()->{},()->harness.replaceStrategy(nativeChestTarget),()->{},()->fail("successful route was released"));

        assertTrue(route.matches(harness.npc));
        assertEquals(logicalChest.getY()+1,harness.navigator.getTargetAsLocation().getY());
        assertNotEquals(logicalChest,harness.navigator.getTargetAsLocation());

        harness.replaceStrategy(nativeChestTarget);
        route.cancelIfOwned(harness.npc);
        assertTrue(harness.navigator.isNavigating(),"a foreign route to the same target must keep running");
        assertEquals(0,harness.cancels.get());
    }

    @Test
    void releasesTentativeActivationWithoutCancellingASurvivingRouteWhenSetTargetThrows() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        Location foreignTarget=new Location(world,5,70,9);
        harness.replaceStrategy(foreignTarget);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        AtomicInteger acquired=new AtomicInteger(),released=new AtomicInteger();

        assertThrows(IllegalStateException.class,()->route.issue(harness.npc,acquired::incrementAndGet,
                ()->{throw new IllegalStateException("failed before replacing navigator route");},()->{},released::incrementAndGet));

        assertEquals(1,acquired.get());
        assertEquals(1,released.get());
        assertFalse(route.matches(harness.npc));
        assertTrue(harness.navigator.isNavigating());
        assertSame(harness.foreignStrategy.get(),harness.navigator.getPathStrategy());
        assertEquals(0,harness.cancels.get());
    }

    @Test
    void detectsAndCancelsStrategyInstalledBeforeSetTargetThrows() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        Location intended=new Location(world,6,65,4);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        AtomicInteger released=new AtomicInteger();

        assertThrows(IllegalStateException.class,()->route.issue(harness.npc,()->{},
                ()->{harness.replaceStrategy(intended);throw new IllegalStateException("post-assignment setup failed");},
                ()->{},released::incrementAndGet));

        assertEquals(1,harness.cancels.get());
        assertFalse(harness.navigator.isNavigating());
        assertFalse(route.matches(harness.npc));
        assertEquals(1,released.get());
    }

    @Test
    void preservesPriorOwnedRouteWhenReplacementFailsBeforeSetTargetReturns() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        Location priorTarget=new Location(world,1,64,1);
        AtomicInteger released=new AtomicInteger();
        route.issue(harness.npc,()->{},()->harness.replaceStrategy(priorTarget),()->{},released::incrementAndGet);
        PathStrategy priorStrategy=harness.foreignStrategy.get();

        assertThrows(IllegalStateException.class,()->route.issue(harness.npc,()->{},
                ()->{throw new IllegalStateException("replacement failed before navigation changed");},()->{},released::incrementAndGet));

        assertTrue(route.matches(harness.npc));
        assertSame(priorStrategy,harness.navigator.getPathStrategy());
        assertTrue(harness.navigator.isNavigating());
        assertEquals(0,harness.cancels.get());
        assertEquals(0,released.get());
    }

    @Test
    void cancelsOnlyTheNewlyCapturedRouteAfterTargetSetupFailure() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        AtomicInteger released=new AtomicInteger();
        AtomicReference<PathStrategy> attempted=new AtomicReference<>();
        Location intended=new Location(world,3,64,7);

        assertThrows(IllegalArgumentException.class,()->route.issue(harness.npc,()->{},
                ()->{harness.replaceStrategy(intended);attempted.set(harness.foreignStrategy.get());},
                ()->{throw new IllegalArgumentException("local setup failed");},released::incrementAndGet));

        assertEquals(1,released.get());
        assertFalse(route.matches(harness.npc));
        assertFalse(harness.navigator.isNavigating());
        assertEquals(intended,attempted.get().getTargetAsLocation());
        assertEquals(1,harness.cancels.get());
    }

    @Test
    void setupFailurePreservesForeignReplacementOfTheCapturedRoute() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        AtomicInteger released=new AtomicInteger();
        Location intended=new Location(world,3,64,7);

        assertThrows(IllegalArgumentException.class,()->route.issue(harness.npc,()->{},
                ()->harness.replaceStrategy(intended),()->{
                    harness.replaceStrategy(intended);
                    throw new IllegalArgumentException("setup failed after foreign replacement");
                },released::incrementAndGet));

        assertEquals(1,released.get());
        assertFalse(route.matches(harness.npc));
        assertTrue(harness.navigator.isNavigating());
        assertEquals(intended,harness.navigator.getTargetAsLocation());
        assertEquals(0,harness.cancels.get());
    }

    @Test
    void recoveryLockStopsOwnedLiveRouteBeforeRemovingActivationOverride() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        FakeMetadata metadata=new FakeMetadata();
        ActivationRangeGuard guard=new ActivationRangeGuard();
        Location target=new Location(world,7,64,2);

        route.issue(harness.npc,()->guard.reconcile(metadata,true),
                ()->harness.replaceStrategy(target),()->{},()->guard.reconcile(metadata,false));
        assertTrue(route.matches(harness.npc));
        assertEquals(-1,metadata.range);

        route.stopAndRelease(harness.npc,guard::clear);
        assertFalse(harness.navigator.isNavigating());
        assertEquals(1,harness.cancels.get());
        assertNull(metadata.range);
        assertFalse(guard.ownsOverride());
    }

    @Test
    void failedStepStopsOwnedTaskRouteAndReleasesActivationWhilePreservingFailure() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        FakeMetadata metadata=new FakeMetadata();
        ActivationRangeGuard guard=new ActivationRangeGuard();
        Location target=new Location(world,8,64,3);
        IllegalStateException failure=new IllegalStateException("step failed");
        IllegalArgumentException scheduleCleanupFailure=new IllegalArgumentException("schedule cleanup failed");
        IllegalArgumentException guardCleanupFailure=new IllegalArgumentException("guard cleanup failed");

        route.issue(harness.npc,()->guard.reconcile(metadata,true),
                ()->harness.replaceStrategy(target),()->{},()->guard.reconcile(metadata,false));
        assertTrue(route.matches(harness.npc));
        assertEquals(-1,metadata.range);

        IllegalStateException thrown=assertThrows(IllegalStateException.class,()->route.runStep(harness.npc,
                ()->{throw failure;},()->guard.reconcile(metadata,true),()->{throw scheduleCleanupFailure;},()->{
                    guard.clear();
                    throw guardCleanupFailure;
                }));

        assertSame(failure,thrown);
        assertArrayEquals(new Throwable[]{scheduleCleanupFailure,guardCleanupFailure},thrown.getSuppressed());
        assertFalse(harness.navigator.isNavigating());
        assertEquals(1,harness.cancels.get());
        assertNull(metadata.range);
        assertFalse(guard.ownsOverride());
    }

    @Test
    void successfulStepReconcilesTheStillOwnedRoute() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        FakeMetadata metadata=new FakeMetadata();
        ActivationRangeGuard guard=new ActivationRangeGuard();
        route.issue(harness.npc,()->{},()->harness.replaceStrategy(new Location(world,4,64,6)),()->{},()->{});

        route.runStep(harness.npc,()->{},()->guard.reconcile(metadata,route.matches(harness.npc)),
                ()->fail("successful step must not suspend the schedule"),guard::clear);

        assertTrue(harness.navigator.isNavigating());
        assertEquals(-1,metadata.range);
        assertTrue(guard.ownsOverride());
    }

    @Test
    void failedStepPreservesForeignReplacementAndStillReleasesActivation() {
        World world=world();
        RouteHarness harness=new RouteHarness(world);
        OwnedNavigationRoute route=new OwnedNavigationRoute();
        FakeMetadata metadata=new FakeMetadata();
        ActivationRangeGuard guard=new ActivationRangeGuard();
        route.issue(harness.npc,()->guard.reconcile(metadata,true),
                ()->harness.replaceStrategy(new Location(world,4,64,6)),()->{},()->{});
        harness.replaceStrategy(new Location(world,9,64,2));
        PathStrategy foreign=harness.navigator.getPathStrategy();

        assertThrows(IllegalStateException.class,()->route.runStep(harness.npc,
                ()->{throw new IllegalStateException("step failed");},()->guard.reconcile(metadata,true),()->{},guard::clear));

        assertSame(foreign,harness.navigator.getPathStrategy());
        assertTrue(harness.navigator.isNavigating());
        assertEquals(0,harness.cancels.get());
        assertNull(metadata.range);
        assertFalse(guard.ownsOverride());
    }

    private static final class FakeMetadata implements ActivationRangeGuard.MetadataAccess {
        private Integer range;
        @Override public Object identity(){return this;}
        @Override public boolean spawned(){return true;}
        @Override public boolean hasActivationRange(){return range!=null;}
        @Override public Integer activationRange(){return range;}
        @Override public void setTransientActivationRange(int value){range=value;}
        @Override public void removeActivationRange(){range=null;}
    }

    private static final class RouteHarness {
        final AtomicReference<PathStrategy> foreignStrategy=new AtomicReference<>();
        final AtomicInteger cancels=new AtomicInteger();
        final Navigator navigator;
        final NPC npc;

        RouteHarness(World world) {
            navigator=(Navigator)Proxy.newProxyInstance(Navigator.class.getClassLoader(),new Class<?>[]{Navigator.class},(proxy,method,args)->switch(method.getName()){
                case "isNavigating"->foreignStrategy.get()!=null;
                case "getPathStrategy"->foreignStrategy.get();
                case "getTargetAsLocation"->foreignStrategy.get()==null?null:foreignStrategy.get().getTargetAsLocation();
                case "cancelNavigation"->{cancels.incrementAndGet();foreignStrategy.set(null);yield null;}
                case "toString"->"route test navigator";
                default->null;
            });
            npc=(NPC)Proxy.newProxyInstance(NPC.class.getClassLoader(),new Class<?>[]{NPC.class},(proxy,method,args)->switch(method.getName()){
                case "isSpawned"->true;
                case "getNavigator"->navigator;
                case "toString"->"route test NPC";
                default->null;
            });
        }

        void replaceStrategy(Location target) { foreignStrategy.set(strategy(target)); }
    }

    private static PathStrategy strategy(Location target) {
        Location copy=target.clone();
        return (PathStrategy)Proxy.newProxyInstance(PathStrategy.class.getClassLoader(),new Class<?>[]{PathStrategy.class},(proxy,method,args)->switch(method.getName()){
            case "getTargetAsLocation","getCurrentDestination"->copy.clone();
            case "getTargetType"->net.citizensnpcs.api.ai.TargetType.LOCATION;
            case "toString"->"route test strategy "+copy;
            default->null;
        });
    }

    private static World world() {
        return (World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(proxy,method,args)->switch(method.getName()){
            case "getName"->"activation-route-test";
            case "equals"->proxy==args[0];
            case "hashCode"->System.identityHashCode(proxy);
            case "toString"->"activation-route-test";
            default->null;
        });
    }
}
