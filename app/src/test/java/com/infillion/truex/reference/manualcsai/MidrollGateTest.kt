package com.infillion.truex.reference.manualcsai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidrollGateTest {
    @Test
    fun triggersOnlyOnceAtOrAfterOffset() {
        val gate = MidrollGate(10_000L)

        assertFalse(gate.shouldTrigger(9_999L))
        assertTrue(gate.shouldTrigger(10_000L))
        assertFalse(gate.shouldTrigger(20_000L))
    }

    @Test
    fun onlyCompletedTruexRewardSkipsPod() {
        assertTrue(shouldSkipRemainingPod(ManualAdType.TRUEX, shouldSkipPod = true))
        assertFalse(shouldSkipRemainingPod(ManualAdType.TRUEX, shouldSkipPod = false))
        assertFalse(shouldSkipRemainingPod(ManualAdType.IDVX, shouldSkipPod = true))
    }
}
