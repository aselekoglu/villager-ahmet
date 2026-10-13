package dev.ahmet;

final class DecisionRetry {
  private final long delayMillis;
  private long retryAtMillis;
  DecisionRetry(long delayMillis) {
    if(delayMillis<1)throw new IllegalArgumentException("Retry delay must be positive");
    this.delayMillis=delayMillis;
  }
  boolean shouldWait(long nowMillis){return nowMillis<retryAtMillis;}
  void schedule(long nowMillis){retryAtMillis=nowMillis+delayMillis;}
  void clear(){retryAtMillis=0;}
}
