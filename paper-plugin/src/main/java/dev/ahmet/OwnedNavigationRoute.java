package dev.ahmet;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.PathStrategy;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

/** Tracks one exact Citizens path strategy created by this plugin. */
final class OwnedNavigationRoute {
    private NPC owner;
    private PathStrategy strategy;
    private Location nativeTarget;

    void runStep(NPC npc, Runnable step, Runnable reconcile, Runnable suspendSchedule, Runnable release) {
        try {
            step.run();
            reconcile.run();
        } catch (RuntimeException | Error failure) {
            cleanup(failure, () -> cancelIfOwned(npc));
            cleanup(failure, suspendSchedule);
            cleanup(failure, release);
            throw failure;
        }
    }

    void issue(NPC npc, Runnable acquire, Runnable setTarget, Runnable finishSetup, Runnable release) {
        PathStrategy priorStrategy=currentStrategy(npc);
        boolean targetReturned=false;
        PathStrategy attemptedStrategy=null;
        try {
            acquire.run();
            setTarget.run();
            targetReturned=true;
            clear();
            capture(npc);
            attemptedStrategy=strategy;
            finishSetup.run();
        } catch (RuntimeException | Error failure) {
            if(!targetReturned){
                PathStrategy afterFailure=currentStrategy(npc);
                if(afterFailure!=null&&afterFailure!=priorStrategy){
                    clear();
                    capture(npc);
                    attemptedStrategy=strategy;
                }else if(matches(npc)){
                    throw failure;
                }
            }
            try {
                if(attemptedStrategy!=null&&isCurrentStrategy(npc,attemptedStrategy))npc.getNavigator().cancelNavigation();
            } catch(RuntimeException|Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                clear();
                try{release.run();}
                catch(RuntimeException|Error cleanupFailure){failure.addSuppressed(cleanupFailure);}
            }
            throw failure;
        }
    }

    boolean matches(NPC npc) {
        if (npc == null || npc != owner || strategy == null || !npc.isSpawned()) return false;
        Navigator navigator = npc.getNavigator();
        if (!navigator.isNavigating() || navigator.getPathStrategy() != strategy) return false;
        Location current = navigator.getTargetAsLocation();
        return sameNativeTarget(current, nativeTarget);
    }

    boolean tracksRoute() { return strategy != null; }

    void stopAndRelease(NPC npc,Runnable release) {
        try{cancelIfOwned(npc);}
        finally{release.run();}
    }

    void cancelIfOwned(NPC npc) {
        try {
            if (matches(npc)) npc.getNavigator().cancelNavigation();
        } finally {
            clear();
        }
    }

    void clear() {
        owner = null;
        strategy = null;
        nativeTarget = null;
    }

    private void capture(NPC npc) {
        if (npc == null || !npc.isSpawned()) return;
        Navigator navigator = npc.getNavigator();
        PathStrategy currentStrategy = navigator.getPathStrategy();
        Location currentTarget = navigator.getTargetAsLocation();
        if (!navigator.isNavigating() || currentStrategy == null || currentTarget == null) return;
        owner = npc;
        strategy = currentStrategy;
        nativeTarget = currentTarget.clone();
    }

    private static PathStrategy currentStrategy(NPC npc) {
        return npc==null||!npc.isSpawned()?null:npc.getNavigator().getPathStrategy();
    }

    private static boolean isCurrentStrategy(NPC npc,PathStrategy attempted) {
        return npc!=null&&npc.isSpawned()&&npc.getNavigator().isNavigating()
                &&npc.getNavigator().getPathStrategy()==attempted;
    }

    private static void cleanup(Throwable failure, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
    }

    private static boolean sameNativeTarget(Location current, Location recorded) {
        return current != null && recorded != null && current.getWorld() == recorded.getWorld()
                && Double.compare(current.getX(), recorded.getX()) == 0
                && Double.compare(current.getY(), recorded.getY()) == 0
                && Double.compare(current.getZ(), recorded.getZ()) == 0;
    }
}
