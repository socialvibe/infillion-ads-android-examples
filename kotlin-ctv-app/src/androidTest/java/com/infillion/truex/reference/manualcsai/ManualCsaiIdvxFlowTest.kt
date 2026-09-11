package com.infillion.truex.reference.manualcsai

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.infillion.truex.reference.MainActivity
import com.truex.adrenderer.TruexAdEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Functional UI test verifying the IDVx interactive ad flow in Manual CSAI:
 * 1. Start activity from main carousel
 * 2. Start playback
 * 3. Wait for midroll ad pod (10s)
 * 4. Verify TrueX Choice Card shown
 * 5. Navigate D-pad Right to "Watch Ads" option
 * 6. Press OK (Center / Enter)
 * 7. Verify OPT_OUT event fired
 * 8. Verify TrueX placeholder finishes
 * 9. Verify IDVx starts and TruexAdRenderer launches IDVx unit
 * 10. Press OK on interactive portion, verify UI changes, and take screenshot
 * 11. Wait ~30s for IDVx duration / countdown to end
 * 12. Verify AD_COMPLETED fired, but AD_FREE_POD was NOT awarded
 * 13. Verify player advances past IDVx placeholder and begins next ad in pod (airline-linear)
 * 14. Print all captured TrueX and IDVx event transitions
 * 15. Complete test cleanly
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ManualCsaiIdvxFlowTest {

    private lateinit var uiDevice: UiDevice

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        uiDevice = UiDevice.getInstance(instrumentation)
    }

    @Test
    fun testIdvxFallbackHappyPathFlow() {
        Log.i(TAG, "Step 1: Open app (launch MainActivity)")
        val mainScenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            Log.i(TAG, "Step 1b: Launch Manual CSAI activity from main CTV carousel")
            uiDevice.waitForIdle(2_000L)
            uiDevice.pressDPadCenter()

            val activity = waitForActivity<ManualCsaiActivity>(timeoutMs = 15_000L)
            assertFalse("ManualCsaiActivity must be active and not finishing", activity.isFinishing)

            val caughtEvents = CopyOnWriteArrayList<TruexAdEvent>()
            activity.runOnUiThread {
                activity.addTruexEventListenerForTesting { event, _ ->
                    Log.i(TAG, "Caught TruexAdEvent in IDVx test: $event")
                    caughtEvents.add(event)
                }
            }

            Log.i(TAG, "Step 2: Ensure content video playback started")
            waitForCondition(
                timeoutMs = 45_000L,
                description = "Content playback started",
                details = { "status='${activity.statusTextForTesting}'" },
            ) {
                activity.currentContentPositionMsForTesting > 0L
            }
            Log.i(TAG, "Content playback running at ${activity.currentContentPositionMsForTesting}ms")

            Log.i(TAG, "Step 3: Wait for adpod started at 10s midroll")
            val adPodEntryPointMs = 10_000L
            val boundaryToleranceMs = 12_000L
            val maxAllowedPositionWithoutAdPodMs = adPodEntryPointMs + boundaryToleranceMs

            waitForCondition(
                timeoutMs = 60_000L,
                description = "Ad pod started before reaching boundary position",
                details = { "status='${activity.statusTextForTesting}', pos=${activity.currentContentPositionMsForTesting}ms" },
            ) {
                val position = activity.currentContentPositionMsForTesting
                val isAdPod = activity.isPlayingAdPodForTesting
                if (!isAdPod && position > maxAllowedPositionWithoutAdPodMs) {
                    fail(
                        "Test failed: playback position (${position}ms) passed adpod entry point " +
                            "(${adPodEntryPointMs}ms + tolerance) without starting the adpod!",
                    )
                }
                isAdPod
            }
            Log.i(TAG, "Ad pod started successfully. Status: ${activity.statusTextForTesting}")

            Log.i(TAG, "Step 4: Verify TrueX Choice Card shown")
            waitForCondition(
                timeoutMs = 75_000L,
                description = "Choice card displayed in renderer container",
                details = { "status='${activity.statusTextForTesting}', events=$caughtEvents, containerVisible=${activity.isRendererContainerVisibleForTesting}" },
            ) {
                if (caughtEvents.contains(TruexAdEvent.NO_ADS_AVAILABLE)) {
                    fail("TrueX renderer emitted NO_ADS_AVAILABLE: ad server has no fill. Status: ${activity.statusTextForTesting}")
                }
                if (caughtEvents.contains(TruexAdEvent.AD_ERROR)) {
                    fail("TrueX renderer emitted AD_ERROR. Status: ${activity.statusTextForTesting}")
                }
                activity.isRendererContainerVisibleForTesting &&
                    (caughtEvents.contains(TruexAdEvent.AD_STARTED) ||
                        caughtEvents.contains(TruexAdEvent.AD_DISPLAYED) ||
                        activity.statusTextForTesting.contains("AD_STARTED") ||
                        activity.statusTextForTesting.contains("adStarted"))
            }
            waitForCondition(
                timeoutMs = 15_000L,
                description = "Choice card assets loaded and visible (AD_DISPLAYED)",
                details = { "events=$caughtEvents" },
            ) {
                caughtEvents.contains(TruexAdEvent.AD_DISPLAYED)
            }
            Log.i(TAG, "Choice card is visible. Status: ${activity.statusTextForTesting}")
            SystemClock.sleep(1_500L)
            takeScreenshot("idvx_01_truex_choice_card.png")

            Log.i(TAG, "Step 5: Press Right to focus 'Watch Ads' option")
            SystemClock.sleep(1_000L)
            uiDevice.pressDPadRight()
            SystemClock.sleep(1_000L)
            takeScreenshot("idvx_02_opt_out.png")

            Log.i(TAG, "Step 6: Press OK on 'Watch Ads'")
            uiDevice.pressDPadCenter()
            SystemClock.sleep(400L)
            uiDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER)

            Log.i(TAG, "Step 7: Verify OPT_OUT terminal event fired")
            waitForCondition(
                timeoutMs = 25_000L,
                description = "OPT_OUT event received from TrueX choice card",
                details = { "status='${activity.statusTextForTesting}', events=$caughtEvents" },
            ) {
                caughtEvents.contains(TruexAdEvent.OPT_OUT) ||
                    activity.statusTextForTesting.contains("optOut")
            }
            Log.i(TAG, "OPT_OUT received successfully")

            fun idvxEvents(): List<TruexAdEvent> {
                val optOutIdx = caughtEvents.indexOf(TruexAdEvent.OPT_OUT)
                return if (optOutIdx >= 0 && optOutIdx + 1 <= caughtEvents.size) {
                    caughtEvents.subList(optOutIdx + 1, caughtEvents.size)
                } else emptyList()
            }

            Log.i(TAG, "Step 8: Verify TrueX placeholder finishes and transitions to IDVx")
            waitForCondition(
                timeoutMs = 25_000L,
                description = "TrueX placeholder playback completing and advancing to IDVx",
                details = { "status='${activity.statusTextForTesting}'" },
            ) {
                activity.statusTextForTesting.contains("TRUEX complete") ||
                    activity.statusTextForTesting.contains("IDVx") ||
                    idvxEvents().isNotEmpty()
            }
            Log.i(TAG, "TrueX completed and continuing fallback pod to IDVx")

            Log.i(TAG, "Step 9: Verify that IDVx started")
            waitForCondition(
                timeoutMs = 45_000L,
                description = "IDVx interactive experience started and displayed",
                details = { "status='${activity.statusTextForTesting}', idvxEvents=${idvxEvents()}, containerVisible=${activity.isRendererContainerVisibleForTesting}" },
            ) {
                activity.isRendererContainerVisibleForTesting &&
                    idvxEvents().contains(TruexAdEvent.AD_STARTED)
            }
            SystemClock.sleep(2_500L)
            Log.i(TAG, "IDVx is actively displaying. Status: ${activity.statusTextForTesting}")
            takeScreenshot("idvx_03_idvx_started.png")

            Log.i(TAG, "Step 10: Press OK and verify UI is responsive, then take screenshot")
            SystemClock.sleep(1_000L)
            assertTrue(
                "Renderer container must be visible when interacting with IDVx",
                activity.isRendererContainerVisibleForTesting,
            )
            uiDevice.pressDPadCenter()
            SystemClock.sleep(1_500L)
            assertTrue(
                "Activity and renderer container must remain active and visible after interaction",
                !activity.isFinishing && activity.isRendererContainerVisibleForTesting,
            )
            takeScreenshot("idvx_04_idvx_interactive.png")

            Log.i(TAG, "Step 11: Wait for 30s engagement timer / completion")
            val idvxWaitStart = SystemClock.elapsedRealtime()
            val maxIdvxWaitMs = 50_000L
            while (SystemClock.elapsedRealtime() - idvxWaitStart < maxIdvxWaitMs) {
                if (idvxEvents().contains(TruexAdEvent.AD_COMPLETED)) {
                    Log.i(TAG, "IDVx finished duration and emitted AD_COMPLETED")
                    break
                }
                SystemClock.sleep(1_000L)
            }

            Log.i(TAG, "Step 12: Verify adCompleted fired but not adFreePod")
            waitForCondition(
                timeoutMs = 30_000L,
                description = "IDVx adCompleted event received",
                details = { "status='${activity.statusTextForTesting}', idvxEvents=${idvxEvents()}" },
            ) {
                idvxEvents().contains(TruexAdEvent.AD_COMPLETED)
            }

            assertFalse(
                "AD_FREE_POD must NOT be awarded for IDVx",
                idvxEvents().contains(TruexAdEvent.AD_FREE_POD) ||
                    activity.isTruexAdCreditEarnedRecordForTesting,
            )
            assertTrue(
                "AD_COMPLETED must be received from IDVx renderer",
                idvxEvents().contains(TruexAdEvent.AD_COMPLETED),
            )
            Log.i(TAG, "Verified AD_COMPLETED fired without AD_FREE_POD")

            Log.i(TAG, "Step 13: Verify player seeked over IDVx placeholder and started next ad in pod (airline-linear)")
            waitForCondition(
                timeoutMs = 35_000L,
                description = "Linear fallback ad (airline-linear) actively playing (not buffering)",
                details = { "status='${activity.statusTextForTesting}', isPlaying=${activity.isPlayingForTesting}" },
            ) {
                activity.isPlayingForTesting &&
                    (activity.statusTextForTesting.contains("airline-linear") ||
                        activity.statusTextForTesting.contains("Linear fallback"))
            }
            takeScreenshot("idvx_05_linear_fallback.png")

            Log.i(TAG, "Step 14: Print all TrueX and IDVx fired events")
            Log.i(TAG, "=== ALL CAUGHT TRUEX & IDVX EVENTS ===")
            caughtEvents.forEachIndexed { index, event ->
                Log.i(TAG, "Event #$index: $event")
            }
            println("=== ALL CAUGHT TRUEX & IDVX EVENTS ===")
            caughtEvents.forEachIndexed { index, event ->
                println("Event #$index: $event")
            }

            Log.i(TAG, "Step 15: Complete the case successfully")
        } finally {
            mainScenario.close()
        }
    }

    private fun takeScreenshot(name: String) {
        val file = File("/sdcard/Download/test-artifacts", name)
        runCatching {
            file.parentFile?.mkdirs()
            file.delete()
            uiDevice.executeShellCommand("screencap -p ${file.absolutePath}")
            if (file.exists() && file.length() > 0) {
                Log.i(TAG, "Saved screenshot via screencap to ${file.absolutePath} (${file.length()} bytes)")
            } else {
                val fallbackOk = uiDevice.takeScreenshot(file)
                if (fallbackOk && file.exists() && file.length() > 0) {
                    Log.i(TAG, "Fallback uiDevice screenshot saved to ${file.absolutePath} (${file.length()} bytes)")
                } else {
                    Log.w(TAG, "Failed to capture screenshot to ${file.absolutePath}")
                }
            }
        }.onFailure {
            Log.w(TAG, "Exception capturing screenshot to ${file.absolutePath}: ${it.message}")
        }
    }

    private inline fun <reified T : Activity> waitForActivity(timeoutMs: Long): T {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var result: T? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                for (activity in resumed) {
                    if (activity is T && !activity.isFinishing) {
                        result = activity
                        break
                    }
                }
            }
            if (result != null) return result!!
            SystemClock.sleep(150L)
        }
        fail("Timed out after ${timeoutMs}ms waiting for ${T::class.java.simpleName} to be resumed")
        throw IllegalStateException("Unreachable")
    }

    private fun waitForCondition(
        timeoutMs: Long,
        pollIntervalMs: Long = 200L,
        description: String,
        details: (() -> String)? = null,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(pollIntervalMs)
        }
        val extra = details?.invoke()?.let { " [Current state: $it]" } ?: ""
        fail("Timed out after ${timeoutMs}ms waiting for condition: $description$extra")
    }

    private companion object {
        const val TAG = "ManualCsaiIdvxFlowTest"
    }
}
