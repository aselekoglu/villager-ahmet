package dev.ahmet.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ExternalProtectionAdapterTest {

  private Block dummyBlock() {
    return (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[]{Block.class},
        (proxy, method, args) -> null
    );
  }

  @Test
  void testNullBlockFailsClosed() {
    ExternalProtectionAdapter adapter = new ExternalProtectionAdapter();
    assertTrue(adapter.isProtected(null, null));
  }

  @Test
  void testGracefulFallbackWhenNoPluginsDetected() {
    ExternalProtectionAdapter adapter = new ExternalProtectionAdapter();
    Block block = dummyBlock();

    // In unit test environment without Bukkit server or plugins, should gracefully fall back to unprotected
    assertFalse(adapter.isProtected(block, null));
  }

  @Test
  void testMockProviderInjection() {
    AtomicBoolean providerCalled = new AtomicBoolean(false);
    String expectedOwner = "owner-uuid-123";

    ExternalProtectionAdapter.ExternalProvider mockProvider = (b, loc, owner) -> {
      providerCalled.set(true);
      return !expectedOwner.equals(owner);
    };

    ExternalProtectionAdapter adapter = new ExternalProtectionAdapter(mockProvider, expectedOwner);
    Block block = dummyBlock();

    assertFalse(adapter.isProtected(block, null));
    assertTrue(providerCalled.get());

    // With foreign owner, provider rejects
    adapter.setDefaultTaskOwnerUuid("foreign-uuid-999");
    assertTrue(adapter.isProtected(block, null));
  }

  @Test
  void testProviderExceptionFailsClosed() {
    ExternalProtectionAdapter.ExternalProvider throwingProvider = (b, loc, owner) -> {
      throw new RuntimeException("External claim database connection timeout");
    };

    ExternalProtectionAdapter adapter = new ExternalProtectionAdapter(throwingProvider);
    Block block = dummyBlock();

    assertTrue(adapter.isProtected(block, null), "Exception in external provider must fail closed as protected");
  }

  @Test
  void testDynamicProviderOverrideAndOwnerConfiguration() {
    ExternalProtectionAdapter adapter = new ExternalProtectionAdapter();
    Block block = dummyBlock();

    // Default without provider: false
    assertFalse(adapter.isProtected(block, null));

    // Inject provider dynamically
    adapter.setProviderOverride((b, loc, owner) -> true);
    assertTrue(adapter.isProtected(block, null));

    // Change provider to permit
    adapter.setProviderOverride((b, loc, owner) -> false);
    assertFalse(adapter.isProtected(block, null));
  }
}
