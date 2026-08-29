package com.infillion.truex.reference

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.infillion.truex.reference.imacsai.ImaCsaiActivity
import com.infillion.truex.reference.imassai.ImaSsaiActivity
import com.infillion.truex.reference.manualcsai.ManualCsaiActivity
import com.infillion.truex.reference.shell.ExampleEntry
import com.infillion.truex.reference.shell.ImmersiveListScreen
import com.infillion.truex.reference.shell.theme.TrueXReferenceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val examples = listOf(
            ExampleEntry(
                title = getString(R.string.manual_csai_title),
                description = "Own the ad break yourself: pause content, run the interactive renderer, then skip or continue the pod.",
                delivery = "Simulated request",
                insertion = "Client-side",
                artwork = R.drawable.hero_manual_csai,
                destination = ManualCsaiActivity::class.java,
            ),
            ExampleEntry(
                title = getString(R.string.ima_csai_title),
                description = "Let Google IMA request and sequence client-side ads while the app handles TrueX and IDVx placeholders.",
                delivery = "Google IMA",
                insertion = "Client-side",
                artwork = R.drawable.hero_ima_csai,
                destination = ImaCsaiActivity::class.java,
            ),
            ExampleEntry(
                title = getString(R.string.ima_ssai_title),
                description = "Play a Google DAI stream, coordinate stitched ad timing, and seek the stream when TrueX credit is earned.",
                delivery = "Google DAI",
                insertion = "Server-side",
                artwork = R.drawable.hero_ima_ssai,
                destination = ImaSsaiActivity::class.java,
            ),
        )

        setContent {
            TrueXReferenceTheme {
                ImmersiveListScreen(
                    examples = examples,
                    onOpenExample = { example ->
                        startActivity(Intent(this, example.destination))
                    },
                )
            }
        }
    }
}

