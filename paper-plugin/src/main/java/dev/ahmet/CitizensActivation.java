package dev.ahmet;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Narrow adapter for the pinned Citizens activation helper. */
final class CitizensActivation {
    private static final String EXPECTED_CITIZENS_JAR = "Citizens-2.0.41-b4138.jar";
    private static final String EXPECTED_CITIZENS_VERSION = "2.0.41-SNAPSHOT (build 4138)";
    private static final String EXPECTED_PAPER_IDENTITY = "1.21.6-48-4d854e6";
    private static final String NMS_CLASS = "net.citizensnpcs.util.NMS";
    private static final int MAX_ENTITIES_PER_TICK = 32;

    static final class Failure extends RuntimeException {
        private final String code;

        Failure(String code) {
            super(code);
            this.code = code;
        }

        String code() { return code; }
    }

    private final Method activate;
    private final String unavailableCode;
    private final Set<Entity> activatedThisTick = Collections.newSetFromMap(new IdentityHashMap<>());
    private long activationTick = Long.MIN_VALUE;

    private CitizensActivation(Method activate, String unavailableCode) {
        this.activate = activate;
        this.unavailableCode = unavailableCode;
    }

    static CitizensActivation bind(Plugin citizens, String paperVersion, Class<?> entityType) {
        if (citizens == null || !citizens.getName().equalsIgnoreCase("Citizens"))
            return unavailable("citizens-plugin-missing");
        String pluginVersion = citizens.getPluginMeta().getVersion();
        if (!EXPECTED_CITIZENS_VERSION.equals(pluginVersion))
            return unavailable("citizens-version-mismatch");
        if (!matchesPaperIdentity(paperVersion))
            return unavailable("paper-build-mismatch");
        try {
            ClassLoader citizensLoader = citizens.getClass().getClassLoader();
            Class<?> helper = Class.forName(NMS_CLASS, false, citizensLoader);
            if (helper.getClassLoader() != citizensLoader) return unavailable("citizens-classloader-mismatch");
            URL source = helper.getProtectionDomain().getCodeSource() == null
                    ? null : helper.getProtectionDomain().getCodeSource().getLocation();
            if (source == null || !Path.of(source.toURI()).getFileName().toString().equals(EXPECTED_CITIZENS_JAR))
                return unavailable("citizens-build-mismatch");
            return bindHelper(helper, entityType);
        } catch (ReflectiveOperationException | java.net.URISyntaxException | RuntimeException | LinkageError failure) {
            return unavailable("citizens-helper-unavailable");
        }
    }

    /** Package-visible seam keeps signature and compatibility checks directly testable. */
    static CitizensActivation bindPinned(String paperVersion, String citizensVersion, String citizensJarName,
                                         Class<?> helper, Class<?> entityType) {
        if (!matchesPaperIdentity(paperVersion))
            return unavailable("paper-build-mismatch");
        if (!EXPECTED_CITIZENS_VERSION.equals(citizensVersion))
            return unavailable("citizens-version-mismatch");
        if (!EXPECTED_CITIZENS_JAR.equals(citizensJarName)) return unavailable("citizens-build-mismatch");
        if (helper == null) return unavailable("citizens-helper-unavailable");
        return bindHelper(helper, entityType);
    }

    private static boolean matchesPaperIdentity(String paperVersion) {
        if (paperVersion == null) return false;
        int start = paperVersion.indexOf(EXPECTED_PAPER_IDENTITY);
        if (start < 0) return false;
        int end = start + EXPECTED_PAPER_IDENTITY.length();
        return !isVersionCharacter(start == 0 ? '\0' : paperVersion.charAt(start - 1))
                && !isVersionCharacter(end == paperVersion.length() ? '\0' : paperVersion.charAt(end));
    }

    private static boolean isVersionCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '_' || value == '@' || value == '-';
    }

    private static CitizensActivation bindHelper(Class<?> helper, Class<?> entityType) {
        try {
            Method method = helper.getMethod("activate", entityType);
            if (method.getDeclaringClass() != helper || !Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class || method.getParameterCount() != 1)
                return unavailable("citizens-helper-signature-mismatch");
            return new CitizensActivation(method, null);
        } catch (NoSuchMethodException | SecurityException failure) {
            return unavailable("citizens-helper-signature-mismatch");
        }
    }

    private static CitizensActivation unavailable(String code) {
        return new CitizensActivation(null, code);
    }

    void activate(Entity entity, long tick, BooleanSupplier primaryThread) {
        if (!primaryThread.getAsBoolean()) throw new Failure("main-thread-required");
        if (entity == null) throw new Failure("citizens-entity-missing");
        if (unavailableCode != null) throw new Failure(unavailableCode);
        if (activate == null) throw new Failure("citizens-helper-unavailable");
        if (activationTick != tick) {
            activatedThisTick.clear();
            activationTick = tick;
        }
        if (activatedThisTick.contains(entity)) return;
        if (activatedThisTick.size() >= MAX_ENTITIES_PER_TICK)
            throw new Failure("citizens-activation-capacity-exceeded");
        try {
            activate.invoke(null, entity);
            activatedThisTick.add(entity);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException failure) {
            throw new Failure("citizens-helper-invocation-failed");
        }
    }
}
