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
import androidx.test.uiautomator.By
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
import java.util.regex.Pattern

/**
 * Functional UI test verifying the TrueX interactive ad renderer integration flow
 * with live ad requests, video playback, CTV remote D-pad interactions, and host app event handling.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ManualCsaiTruexFlowTest {

    private lateinit var uiDevice: UiDevice

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        uiDevice = UiDevice.getInstance(instrumentation)
    }

    @Test
    fun testTruexHappyPathIntegrationFlow() {
        Log.i(TAG, "Step 1: Open app (launch MainActivity)")
        val mainScenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            Log.i(TAG, "Step 2: Launch manual csai activity from main CTV carousel")
            uiDevice.waitForIdle(2_000L)
            // In MainActivity, the first item ('Plain / Manual CSAI') is focused initially.
            uiDevice.pressDPadCenter()

            Log.i(TAG, "Step 3: Ensure manual-csai activity is running")
            val activity = waitForActivity<ManualCsaiActivity>(timeoutMs = 15_000L)
            assertFalse("ManualCsaiActivity must be active and not finishing", activity.isFinishing)

            val caughtEvents = CopyOnWriteArrayList<TruexAdEvent>()
            activity.runOnUiThread {
                activity.addTruexEventListenerForTesting { event, _ ->
                    Log.i(TAG, "Caught TruexAdEvent in test: $event")
                    caughtEvents.add(event)
                }
            }

            Log.i(TAG, "Step 4: Ensure video playback started")
            waitForCondition(timeoutMs = 25_000L, description = "Content playback started") {
                activity.currentContentPositionMsForTesting > 0L
            }
            Log.i(TAG, "Content playback confirmed running at ${activity.currentContentPositionMsForTesting}ms")

            Log.i(TAG, "Step 5: Wait for adpod started or fail if position exceeds entry point")
            val adPodEntryPointMs = 10_000L
            val boundaryToleranceMs = 4_000L
            val maxAllowedPositionWithoutAdPodMs = adPodEntryPointMs + boundaryToleranceMs

            waitForCondition(
                timeoutMs = 35_000L,
                description = "Ad pod started before reaching boundary position",
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

            Log.i(TAG, "Step 6: Verify choice card shown")
            waitForCondition(
                timeoutMs = 30_000L,
                description = "Choice card displayed in renderer container",
            ) {
                activity.isRendererContainerVisibleForTesting &&
                    (caughtEvents.contains(TruexAdEvent.AD_STARTED) ||
                        caughtEvents.contains(TruexAdEvent.AD_DISPLAYED) ||
                        activity.statusTextForTesting.contains("AD_STARTED"))
            }
            Log.i(TAG, "Choice card is visible. Status: ${activity.statusTextForTesting}")

            Log.i(TAG, "Step 7: Select watch, then back to interactive option")
            SystemClock.sleep(1_500L)
            Log.i(TAG, "Navigating to 'Watch' option via DPAD_RIGHT")
            uiDevice.pressDPadRight()
            SystemClock.sleep(1_000L)

            Log.i(TAG, "Navigating back to interactive option via DPAD_LEFT")
            uiDevice.pressDPadLeft()
            SystemClock.sleep(1_000L)

            Log.i(TAG, "Step 8: Press OK on interactive option")
            uiDevice.pressDPadCenter()

            waitForCondition(
                timeoutMs = 15_000L,
                description = "Viewer opt-in event (OPT_IN)",
            ) {
                caughtEvents.contains(TruexAdEvent.OPT_IN) ||
                    activity.statusTextForTesting.contains("OPT_IN")
            }
            Log.i(TAG, "Viewer successfully opted in to interactive experience")

            Log.i(TAG, "Step 9: Verify an interactive portion started")
            waitForCondition(
                timeoutMs = 25_000L,
                description = "Interactive portion assets loaded and displayed",
            ) {
                activity.isRendererContainerVisibleForTesting &&
                    (caughtEvents.contains(TruexAdEvent.AD_DISPLAYED) ||
                        activity.statusTextForTesting.contains("Interactive ad"))
            }
            Log.i(TAG, "Interactive engagement portion is actively displaying")

            Log.i(TAG, "Step 10: Make 1 interactive event to achieve interaction goal")
            SystemClock.sleep(2_500L)
            Log.i(TAG, "Sending DPAD_RIGHT interaction event")
            uiDevice.pressDPadRight()
            SystemClock.sleep(600L)
            Log.i(TAG, "Sending DPAD_CENTER interaction event")
            uiDevice.pressDPadCenter()

            Log.i(TAG, "Step 11: Wait for time_spent countdown ends (~30s)")
            val countdownStart = SystemClock.elapsedRealtime()
            val maxCountdownWaitMs = 38_000L
            while (SystemClock.elapsedRealtime() - countdownStart < maxCountdownWaitMs) {
                if (activity.isTruexAdCreditReceivedForTesting ||
                    caughtEvents.contains(TruexAdEvent.AD_FREE_POD)
                ) {
                    Log.i(TAG, "AD_FREE_POD credit received during countdown!")
                    break
                }
                SystemClock.sleep(1_000L)
            }
            Log.i(TAG, "Countdown wait finished")

            Log.i(TAG, "Step 12: Check continue button shown / credit earned")
            waitForCondition(
                timeoutMs = 15_000L,
                description = "AD_FREE_POD credit earned or continue prompt visible",
            ) {
                activity.isTruexAdCreditReceivedForTesting ||
                    caughtEvents.contains(TruexAdEvent.AD_FREE_POD) ||
                    caughtEvents.contains(TruexAdEvent.AD_COMPLETED)
            }
            Log.i(TAG, "Continue condition verified. Status: ${activity.statusTextForTesting}")

            Log.i(TAG, "Step 13: Press continue button")
            SystemClock.sleep(3_000L)

            // Save screenshot for diagnostics
            runCatching {
                val file = File("/sdcard/step13_continue.png")
                uiDevice.takeScreenshot(file)
                Log.i(TAG, "Saved screenshot to ${file.absolutePath}")
            }

            // Inspect visible UI objects
            val textObjects = runCatching {
                uiDevice.findObjects(By.text(Pattern.compile(".*", Pattern.CASE_INSENSITIVE)))
            }.getOrDefault(emptyList())
            for (obj in textObjects) {
                Log.i(TAG, "Visible object: text='${obj.text}', desc='${obj.contentDescription}', bounds=${obj.visibleBounds}")
            }

            val continueButton = runCatching {
                uiDevice.findObject(By.text(Pattern.compile(".*(continue|watch|skip).*", Pattern.CASE_INSENSITIVE)))
                    ?: uiDevice.findObject(By.desc(Pattern.compile(".*(continue|watch|skip).*", Pattern.CASE_INSENSITIVE)))
            }.getOrNull()

            if (continueButton != null) {
                Log.i(TAG, "Found continue button at ${continueButton.visibleBounds}, clicking directly")
                continueButton.click()
                SystemClock.sleep(1_000L)
            }

            val continueClickStart = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - continueClickStart < 15_000L) {
                if (caughtEvents.contains(TruexAdEvent.AD_COMPLETED)) break
                Log.i(TAG, "Pressing DPAD keys / ENTER on continue prompt")
                uiDevice.pressDPadCenter()
                SystemClock.sleep(400L)
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER)
                SystemClock.sleep(400L)
                // Try navigating focus in case continue button is below or to the side
                uiDevice.pressDPadDown()
                SystemClock.sleep(400L)
                uiDevice.pressDPadRight()
                SystemClock.sleep(400L)
                uiDevice.pressDPadCenter()
                SystemClock.sleep(1_000L)
            }

            Log.i(TAG, "Step 14: Verify adFreePod and adCompleted fired and caught in app code")
            waitForCondition(
                timeoutMs = 25_000L,
                description = "AD_COMPLETED terminal event received and processed",
            ) {
                caughtEvents.contains(TruexAdEvent.AD_COMPLETED) &&
                    activity.isTruexAdCompletedRecordForTesting
            }

            assertTrue(
                "AD_FREE_POD event must be emitted and recorded in app code",
                caughtEvents.contains(TruexAdEvent.AD_FREE_POD) &&
                    activity.isTruexAdCreditEarnedRecordForTesting,
            )

            assertTrue(
                "AD_COMPLETED event must be emitted and processed in app code",
                caughtEvents.contains(TruexAdEvent.AD_COMPLETED) &&
                    activity.isTruexAdCompletedRecordForTesting,
            )

            waitForCondition(
                timeoutMs = 20_000L,
                description = "Content playback resumed with ad-free reward applied",
            ) {
                !activity.isPlayingAdPodForTesting &&
                    (activity.statusTextForTesting.contains("TrueX credit earned") ||
                        activity.statusTextForTesting.startsWith("Content •"))
            }

            Log.i(TAG, "Step 15: Complete the case")
            Log.i(TAG, "All 15 steps of TrueX happy-path flow verified successfully!")
        } finally {
            mainScenario.close()
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
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(pollIntervalMs)
        }
        fail("Timed out after ${timeoutMs}ms waiting for condition: $description")
    }

    private companion object {
        const val TAG = "ManualCsaiTruexFlowTest"
    }
}
