package dev.ahmet.agent;

public record ActionBudget(
    int maxRouteClears,
    int maxJobClears,
    int maxRouteRetries,
    int maxExplorationAttempts,
    int maxConsecutiveEmptyScans,
    int maxWaitDecisions,
    long helpCooldownMs
) {
  public static final ActionBudget DEFAULT = new ActionBudget(3, 12, 3, 4, 2, 3, 30_000L);

  public ActionBudget {
    if (maxRouteClears < 1) throw new IllegalArgumentException("maxRouteClears must be >= 1");
    if (maxJobClears < 1) throw new IllegalArgumentException("maxJobClears must be >= 1");
    if (maxRouteRetries < 1) throw new IllegalArgumentException("maxRouteRetries must be >= 1");
    if (maxExplorationAttempts < 1) throw new IllegalArgumentException("maxExplorationAttempts must be >= 1");
    if (maxConsecutiveEmptyScans < 1) throw new IllegalArgumentException("maxConsecutiveEmptyScans must be >= 1");
    if (maxWaitDecisions < 1) throw new IllegalArgumentException("maxWaitDecisions must be >= 1");
    if (helpCooldownMs < 0) throw new IllegalArgumentException("helpCooldownMs must be >= 0");
  }

  public boolean canClearObstruction(int routeClears, int jobClears) {
    return routeClears < maxRouteClears && jobClears < maxJobClears;
  }

  public boolean canRetryRoute(int retries) {
    return retries < maxRouteRetries;
  }

  public boolean canExplore(int attempts) {
    return attempts < maxExplorationAttempts;
  }

  public boolean isScanExhausted(int consecutiveEmptyScans) {
    return consecutiveEmptyScans >= maxConsecutiveEmptyScans;
  }

  public boolean isWaitExhausted(int waitDecisions) {
    return waitDecisions >= maxWaitDecisions;
  }

  public boolean canRequestHelp(long lastHelpMs, long nowMs) {
    return (nowMs - lastHelpMs) >= helpCooldownMs;
  }
}
