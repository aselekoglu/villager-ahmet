package dev.ahmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleRetryPolicyTest {
    @Test
    void routeFailuresAccumulateAcrossStartsAndExhaustAtExactBoundary() {
        ScheduleRetryPolicy policy=new ScheduleRetryPolicy();
        assertEquals(ScheduleRetryPolicy.Result.RETRY,policy.failed(100));
        assertFalse(policy.ready(2_099));
        assertTrue(policy.ready(2_100));
        policy.started();
        assertEquals(1,policy.failures());
        assertEquals(ScheduleRetryPolicy.Result.RETRY,policy.failed(2_100));
        assertEquals(ScheduleRetryPolicy.Result.EXHAUSTED,policy.failed(4_100));
        assertFalse(policy.ready(19_099));
        assertTrue(policy.ready(19_100));
    }

    @Test
    void noCandidateHasCooldownAndSuccessClearsFailureHistory() {
        ScheduleRetryPolicy policy=new ScheduleRetryPolicy();
        assertEquals(ScheduleRetryPolicy.Result.RETRY,policy.failed(0));
        policy.unavailable(20);
        assertFalse(policy.ready(15_019));
        assertTrue(policy.ready(15_020));
        policy.succeeded();
        assertEquals(0,policy.failures());
        assertTrue(policy.ready(0));
    }
}
