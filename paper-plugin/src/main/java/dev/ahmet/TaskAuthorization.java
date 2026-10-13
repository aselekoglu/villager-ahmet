package dev.ahmet;

import java.util.Objects;

final class TaskAuthorization {
  private TaskAuthorization() {}
  static boolean mayControl(String actor, String owner, boolean admin, boolean taskActive) {
    return !taskActive || admin || Objects.equals(actor, owner);
  }
}
