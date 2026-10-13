package dev.ahmet;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class TaskAuthorizationTest {
  @Test void onlyOwnerOrAdminCanControlAnActiveTask() {
    assertTrue(TaskAuthorization.mayControl("owner", "owner", false, true));
    assertTrue(TaskAuthorization.mayControl("stranger", "owner", true, true));
    assertFalse(TaskAuthorization.mayControl("stranger", "owner", false, true));
  }
  @Test void idleNavigationDoesNotRequireTaskOwnership() {
    assertTrue(TaskAuthorization.mayControl("stranger", "owner", false, false));
  }
}
