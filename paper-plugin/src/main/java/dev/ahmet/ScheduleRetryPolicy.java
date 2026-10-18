package dev.ahmet;

/** Finite schedule-only route retry budget with a bounded no-candidate cooldown. */
final class ScheduleRetryPolicy {
    enum Result { RETRY, EXHAUSTED }
    static final int FAILURE_BUDGET = 3;
    static final long RETRY_MILLIS = 2_000;
    static final long COOLDOWN_MILLIS = 15_000;
    private int failures;
    private long retryAt = -1;

    Result failed(long now) {
        failures++;
        if (failures >= FAILURE_BUDGET) {
            failures = 0;
            retryAt = now + COOLDOWN_MILLIS;
            return Result.EXHAUSTED;
        }
        retryAt = now + RETRY_MILLIS;
        return Result.RETRY;
    }
    void unavailable(long now) { retryAt = now + COOLDOWN_MILLIS; }
    void started() { retryAt = -1; }
    boolean ready(long now) { return retryAt < 0 || now >= retryAt; }
    void succeeded() { failures = 0; retryAt = -1; }
    void clear() { failures = 0; retryAt = -1; }
    int failures() { return failures; }
    long retryAt() { return retryAt; }
}
