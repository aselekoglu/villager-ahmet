package dev.ahmet;

/** Owns a transient Citizens activation override only while Ahmet owns a route. */
final class ActivationRangeGuard {
    interface MetadataAccess {
        Object identity();
        boolean spawned();
        boolean hasActivationRange();
        Integer activationRange();
        void setTransientActivationRange(int value);
        void removeActivationRange();
    }

    private static final int ROUTE_ACTIVATION_RANGE = -1;
    private MetadataAccess ownedMetadata;

    void reconcile(MetadataAccess currentNpc, boolean pluginOwnedRoute) {
        if (ownedMetadata != null && (!pluginOwnedRoute || currentNpc == null
                || !currentNpc.spawned() || currentNpc.identity() != ownedMetadata.identity())) {
            release();
        }
        if (!pluginOwnedRoute || currentNpc == null || !currentNpc.spawned() || ownedMetadata != null) return;
        if (currentNpc.hasActivationRange()) return;

        currentNpc.setTransientActivationRange(ROUTE_ACTIVATION_RANGE);
        ownedMetadata = currentNpc;
    }

    void clear() { release(); }

    boolean ownsOverride() { return ownedMetadata != null; }

    static boolean taskRouteEligible(boolean matchingOwnedRoute,boolean recoveryBlocked,
            boolean pendingOperation,boolean deciding,boolean hasLogicalGoal,boolean retryPending) {
        return matchingOwnedRoute&&!recoveryBlocked&&!pendingOperation&&!deciding&&hasLogicalGoal&&!retryPending;
    }

    private void release() {
        MetadataAccess previous = ownedMetadata;
        ownedMetadata = null;
        if (previous != null && previous.hasActivationRange()
                && Integer.valueOf(ROUTE_ACTIVATION_RANGE).equals(previous.activationRange())) {
            previous.removeActivationRange();
        }
    }
}
