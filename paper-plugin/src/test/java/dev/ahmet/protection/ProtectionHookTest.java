package dev.ahmet.protection;

import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProtectionHookTest {

  private Block dummyBlock() {
    return (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[]{Block.class},
        (proxy, method, args) -> null
    );
  }

  @Test
  void testNullBlockFailsClosed() {
    CompositeProtectionHook hook = new CompositeProtectionHook();
    assertTrue(hook.isProtected(null, null), "Null block must fail closed as protected");
  }

  @Test
  void testUnprotectedWhenNoHooksTrigger() {
    Block block = dummyBlock();
    ProtectionHook hook1 = (b, loc) -> false;
    ProtectionHook hook2 = (b, loc) -> false;
    CompositeProtectionHook composite = new CompositeProtectionHook(List.of(hook1, hook2));

    assertFalse(composite.isProtected(block, null));
  }

  @Test
  void testProtectedWhenAnyHookTriggers() {
    Block block = dummyBlock();
    ProtectionHook allowed = (b, loc) -> false;
    ProtectionHook blocked = (b, loc) -> true;
    CompositeProtectionHook composite = new CompositeProtectionHook(List.of(allowed, blocked));

    assertTrue(composite.isProtected(block, null));
  }

  @Test
  void testHookExceptionFailsClosed() {
    Block block = dummyBlock();
    ProtectionHook throwingHook = (b, loc) -> {
      throw new RuntimeException("External claim plugin connection failed");
    };
    CompositeProtectionHook composite = new CompositeProtectionHook(List.of(throwingHook));

    assertTrue(composite.isProtected(block, null), "Exception in delegate must fail closed");
  }

  @Test
  void testDynamicHookRegistration() {
    Block block = dummyBlock();
    CompositeProtectionHook composite = new CompositeProtectionHook();
    assertFalse(composite.isProtected(block, null));

    composite.addHook((b, loc) -> true);
    assertTrue(composite.isProtected(block, null));
  }
}
