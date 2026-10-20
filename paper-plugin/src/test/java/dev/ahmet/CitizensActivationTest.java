package dev.ahmet;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CitizensActivationTest {
    private static final String PAPER = "This server is running Paper version 1.21.6-48-4d854e6 (MC: 1.21.6)\n"
            + "Implementing API version 1.21.6-R0.1-SNAPSHOT";
    private static final String PAPER_STARTUP = "This server is running Paper version 1.21.6-48-main@4d854e6 (MC: 1.21.6)";
    private static final String CITIZENS_VERSION = "2.0.41-SNAPSHOT (build 4138)";
    private static final String CITIZENS_JAR = "Citizens-2.0.41-b4138.jar";

    @Test
    void invokesOnlyTheExactEntityAndAtMostOncePerPaperTick() {
        FakeNms.calls = 0;
        Entity ahmet = entity("ahmet");
        Entity other = entity("other");
        CitizensActivation activation = bind(FakeNms.class);

        activation.activate(ahmet, 100, () -> true);
        activation.activate(ahmet, 100, () -> true);
        activation.activate(other, 100, () -> true);
        activation.activate(ahmet, 100, () -> true);
        assertEquals(2, FakeNms.calls);
        assertSame(other, FakeNms.lastEntity);

        activation.activate(ahmet, 101, () -> true);
        assertEquals(3, FakeNms.calls);
        assertSame(ahmet, FakeNms.lastEntity);
    }

    @Test
    void rejectsAnEntitySetBeyondTheBoundedPerTickCapacity() {
        FakeNms.calls = 0;
        CitizensActivation activation = bind(FakeNms.class);
        for (int index = 0; index < 32; index++) activation.activate(entity("npc-" + index), 110, () -> true);

        CitizensActivation.Failure failure = assertThrows(CitizensActivation.Failure.class,
                () -> activation.activate(entity("npc-over-cap"), 110, () -> true));
        assertEquals("citizens-activation-capacity-exceeded", failure.code());
        assertEquals(32, FakeNms.calls);
        activation.activate(entity("npc-next-tick"), 111, () -> true);
        assertEquals(33, FakeNms.calls);
    }

    @Test
    void mainThreadIsCheckedBeforeNativeInvocationAndFailureDoesNotConsumeTick() {
        FakeNms.calls = 0;
        CitizensActivation activation = bind(FakeNms.class);
        Entity ahmet = entity("ahmet");
        AtomicInteger checks = new AtomicInteger();

        CitizensActivation.Failure offThread = assertThrows(CitizensActivation.Failure.class,
                () -> activation.activate(ahmet, 20, () -> { checks.incrementAndGet(); return false; }));
        assertEquals("main-thread-required", offThread.code());
        assertEquals(1, checks.get());
        assertEquals(0, FakeNms.calls);
        activation.activate(ahmet, 20, () -> { checks.incrementAndGet(); return true; });
        assertEquals(2, checks.get());
        assertEquals(1, FakeNms.calls);
    }

    @Test
    void acceptsTheBukkitSimpleVersionAndNotTheDifferentStartupIdentity() {
        CitizensActivation accepted = CitizensActivation.bindPinned(PAPER, CITIZENS_VERSION,
                CITIZENS_JAR, FakeNms.class, Entity.class);
        assertDoesNotThrow(() -> accepted.activate(entity("ahmet"), 21, () -> true));
        assertFailure("paper-build-mismatch", CitizensActivation.bindPinned(PAPER_STARTUP, CITIZENS_VERSION,
                CITIZENS_JAR, FakeNms.class, Entity.class));
    }

    @Test
    void routeGateAllowsOnlyOwnedOrExplicitMinusOneAndKeepsOtherMetadata() {
        ActivationRangeGuard guard = new ActivationRangeGuard();
        FakeNpc owned = new FakeNpc("owned");
        guard.reconcile(owned, true);
        assertTrue(guard.shouldForceActivation(owned, true));
        assertFalse(guard.shouldForceActivation(owned, false));
        owned.range = 24;
        assertFalse(guard.shouldForceActivation(owned, true));
        owned.range = -1;

        FakeNpc foreign = new FakeNpc("foreign");
        foreign.range = -1;
        assertFalse(guard.shouldForceActivation(foreign, false));
        assertTrue(guard.shouldForceActivation(foreign, true));
        foreign.spawned = false;
        assertFalse(guard.shouldForceActivation(foreign, true));

        FakeNpc positive = new FakeNpc("positive");
        positive.range = 32;
        guard.reconcile(positive, true);
        assertEquals(32, positive.range);
        assertFalse(guard.shouldForceActivation(positive, true));
        guard.reconcile(positive, false);
        assertEquals(0, positive.removes);

        FakeNpc operatorMinusOne = new FakeNpc("operator-minus-one");
        operatorMinusOne.range = -1;
        guard.reconcile(operatorMinusOne, true);
        guard.reconcile(operatorMinusOne, false);
        assertEquals(-1, operatorMinusOne.range);
        assertEquals(0, operatorMinusOne.removes);
    }

    @Test
    void rejectsUnpinnedPaperCitizensBuildAndHelperSignature() {
        assertFailure("paper-build-mismatch", CitizensActivation.bindPinned("Paper 1.21.6-47", CITIZENS_VERSION,
                CITIZENS_JAR, FakeNms.class, Entity.class));
        assertFailure("paper-build-mismatch", CitizensActivation.bindPinned(
                "1.21.6-480-4d854e6", CITIZENS_VERSION, CITIZENS_JAR, FakeNms.class, Entity.class));
        assertFailure("paper-build-mismatch", CitizensActivation.bindPinned(
                "1.21.6-48-4d854e60", CITIZENS_VERSION, CITIZENS_JAR, FakeNms.class, Entity.class));
        assertFailure("citizens-version-mismatch", CitizensActivation.bindPinned(PAPER, "2.0.410-SNAPSHOT (build 4138)",
                CITIZENS_JAR, FakeNms.class, Entity.class));
        assertFailure("citizens-build-mismatch", CitizensActivation.bindPinned(PAPER, CITIZENS_VERSION,
                "Citizens-2.0.41-b4139.jar", FakeNms.class, Entity.class));
        assertFailure("citizens-helper-unavailable", CitizensActivation.bindPinned(PAPER, CITIZENS_VERSION,
                CITIZENS_JAR, null, Entity.class));
        assertFailure("citizens-helper-signature-mismatch", CitizensActivation.bindPinned(PAPER, CITIZENS_VERSION,
                CITIZENS_JAR, WrongNms.class, Entity.class));
    }

    @Test
    void bindingAndInvocationFailuresReleaseOnlyTheOwnedOverride() {
        ActivationRangeGuard guard = new ActivationRangeGuard();
        FakeNpc owned = new FakeNpc("owned");
        guard.reconcile(owned, true);
        CitizensActivation unavailable = CitizensActivation.bindPinned(PAPER, CITIZENS_VERSION,
                CITIZENS_JAR, null, Entity.class);
        CitizensActivation.Failure missing = assertThrows(CitizensActivation.Failure.class,
                () -> unavailable.activate(entity("ahmet"), 200, () -> true));
        assertEquals("citizens-helper-unavailable", missing.code());
        guard.reconcile(owned, false);
        assertNull(owned.range);
        assertEquals(1, owned.removes);

        CitizensActivation broken = bind(ThrowingNms.class);
        CitizensActivation.Failure invoke = assertThrows(CitizensActivation.Failure.class,
                () -> broken.activate(entity("ahmet"), 201, () -> true));
        assertEquals("citizens-helper-invocation-failed", invoke.code());
    }

    private static CitizensActivation bind(Class<?> helper) {
        return CitizensActivation.bindPinned(PAPER, CITIZENS_VERSION, CITIZENS_JAR, helper, Entity.class);
    }

    private static void assertFailure(String code, CitizensActivation activation) {
        CitizensActivation.Failure failure = assertThrows(CitizensActivation.Failure.class,
                () -> activation.activate(entity("ahmet"), 1, () -> true));
        assertEquals(code, failure.code());
    }

    private static Entity entity(String name) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    public static final class FakeNms {
        static int calls;
        static Entity lastEntity;
        public static void activate(Entity entity) { calls++; lastEntity = entity; }
    }

    public static final class WrongNms {
        public static void activate(Object entity) { }
    }

    public static final class ThrowingNms {
        public static void activate(Entity entity) { throw new IllegalStateException("private detail"); }
    }

    private static final class FakeNpc implements ActivationRangeGuard.MetadataAccess {
        private final Object id;
        private Integer range;
        private boolean spawned = true;
        private int removes;

        private FakeNpc(Object id) { this.id = id; }
        @Override public Object identity() { return id; }
        @Override public boolean spawned() { return spawned; }
        @Override public boolean hasActivationRange() { return range != null; }
        @Override public Integer activationRange() { return range; }
        @Override public void setTransientActivationRange(int value) { range = value; }
        @Override public void removeActivationRange() { removes++; range = null; }
    }
}
