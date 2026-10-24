package com.infillion.ads.examples.java.manualcsai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MidrollGateTest {
    @Test
    public void triggersOnlyOnceAtOrAfterOffset() {
        ManualAdBreak.MidrollGate gate = new ManualAdBreak.MidrollGate(10_000L);

        assertFalse(gate.shouldTrigger(9_999L));
        assertTrue(gate.shouldTrigger(10_000L));
        assertFalse(gate.shouldTrigger(20_000L));
    }

    @Test
    public void onlyCompletedTruexRewardSkipsPod() {
        assertTrue(ManualAdBreak.shouldSkipRemainingPod(ManualAdBreak.AdType.TRUEX, true));
        assertFalse(ManualAdBreak.shouldSkipRemainingPod(ManualAdBreak.AdType.TRUEX, false));
        assertFalse(ManualAdBreak.shouldSkipRemainingPod(ManualAdBreak.AdType.IDVX, true));
    }
}
