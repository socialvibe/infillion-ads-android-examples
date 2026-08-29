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
    fun onlyTruexCreditSkipsPod() {
        assertTrue(shouldSkipRemainingPod(ManualAdType.TRUEX, receivedCredit = true))
        assertFalse(shouldSkipRemainingPod(ManualAdType.TRUEX, receivedCredit = false))
        assertFalse(shouldSkipRemainingPod(ManualAdType.IDVX, receivedCredit = true))
    }
}
