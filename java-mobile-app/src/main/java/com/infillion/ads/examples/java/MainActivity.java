package com.infillion.ads.examples.java;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.infillion.ads.examples.java.manualcsai.ManualCsaiActivity;

import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final class Example {
        final String title;
        final String description;
        final String delivery;
        final String insertion;
        final Class<? extends Activity> destination;

        Example(String title, String description, String delivery, String insertion,
                Class<? extends Activity> destination) {
            this.title = title;
            this.description = description;
            this.delivery = delivery;
            this.insertion = insertion;
            this.destination = destination;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        List<Example> examples = Collections.singletonList(
                new Example(
                        getString(R.string.manual_csai_title),
                        getString(R.string.manual_csai_description),
                        getString(R.string.manual_csai_delivery),
                        getString(R.string.manual_csai_insertion),
                        ManualCsaiActivity.class));
        ViewGroup container = findViewById(R.id.examples);
        for (Example example : examples) {
            View card = getLayoutInflater().inflate(R.layout.item_example, container, false);
            ((TextView) card.findViewById(R.id.title)).setText(example.title);
            ((TextView) card.findViewById(R.id.description)).setText(example.description);
            ((TextView) card.findViewById(R.id.delivery)).setText(example.delivery);
            ((TextView) card.findViewById(R.id.insertion)).setText(example.insertion);
            // Clip the Bloom bar to the card's rounded corners.
            card.setClipToOutline(true);
            card.setOnClickListener(v -> startActivity(new Intent(this, example.destination)));
            container.addView(card);
        }

        ((TextView) findViewById(R.id.footer)).setText(getString(
                R.string.home_footer,
                BuildConfig.VERSION_NAME,
                BuildConfig.TRUEX_AD_RENDERER_VERSION));
    }
}
