package com.infillion.ads.examples.kotlin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.infillion.ads.examples.kotlin.databinding.ActivityMainBinding
import com.infillion.ads.examples.kotlin.databinding.ItemExampleBinding
import com.infillion.ads.examples.kotlin.manualcsai.ManualCsaiActivity

class MainActivity : AppCompatActivity() {
    private data class Example(
        val title: String,
        val description: String,
        val delivery: String,
        val insertion: String,
        val destination: Class<out Activity>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val examples = listOf(
            Example(
                title = getString(R.string.manual_csai_title),
                description = getString(R.string.manual_csai_description),
                delivery = getString(R.string.manual_csai_delivery),
                insertion = getString(R.string.manual_csai_insertion),
                destination = ManualCsaiActivity::class.java,
            ),
        )
        examples.forEach { example ->
            val card = ItemExampleBinding.inflate(layoutInflater, binding.examples, true)
            card.title.text = example.title
            card.description.text = example.description
            card.delivery.text = example.delivery
            card.insertion.text = example.insertion
            // Clip the Bloom bar to the card's rounded corners.
            card.root.clipToOutline = true
            card.root.setOnClickListener {
                startActivity(Intent(this, example.destination))
            }
        }

        binding.footer.text = getString(
            R.string.home_footer,
            BuildConfig.VERSION_NAME,
            BuildConfig.TRUEX_AD_RENDERER_VERSION,
        )
    }
}
