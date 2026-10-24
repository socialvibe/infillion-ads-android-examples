package com.infillion.ads.examples.java.manualcsai;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.infillion.ads.examples.java.BuildConfig;
import com.infillion.ads.examples.java.R;
import com.truex.adrenderer.IEventEmitter;
import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ManualCsaiActivity extends AppCompatActivity {
    private static final String TAG = "InfillionAdsExamples";
    private static final String CONTENT_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4";

    private View playerContainer;
    private Toolbar toolbar;
    private PlayerView playerView;
    private ViewGroup rendererContainer;
    private TextView statusText;
    private ExoPlayer player;
    private ManualAdBreak adBreak;
    private ManualAdBreak.MidrollGate midrollGate;
    @Nullable private TruexAdRenderer truexAdRenderer;
    private boolean truexAdCreditReceived;
    private boolean truexAdTerminalEvent;
    private long contentPositionMs;
    private int currentAdIndex = -1;
    private boolean playingAdPod;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            Log.i(TAG, "onPlaybackStateChanged: playbackState=" + playbackState + ", playingAdPod=" + playingAdPod);
            if (playbackState == Player.STATE_ENDED && playingAdPod) {
                Log.i(TAG, "Ad ended; advancing ad pod");
                advanceAdPod();
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.e(TAG, "ExoPlayer playback error: " + error.getErrorCodeName());
            showStatus("Playback error: " + error.getErrorCodeName() + ". Continuing safely.");
            if (playingAdPod) {
                advanceAdPod();
            } else {
                finish();
            }
        }
    };

    private final Runnable midrollCheck = new Runnable() {
        @Override
        public void run() {
            if (!playingAdPod && midrollGate.shouldTrigger(player.getCurrentPosition())) {
                Log.i(TAG, "Midroll trigger reached at " + player.getCurrentPosition() + "ms");
                startAdBreak();
            }
            if (!isFinishing()) {
                playerContainer.postDelayed(this, 250L);
            }
        }
    };

    // Handles lifecycle and user-interaction events emitted by TruexAdRenderer.
    private final IEventEmitter.IEventHandler truexAdEventHandler = (event, data) -> {
        Log.i(TAG, "TruexAdEvent " + event + " data=" + data);
        switch (event) {
            // Main flow events
            case AD_FETCH_COMPLETED:
                Log.i(TAG, "TrueX renderer finished fetching ad payload and is ready to present");
                break;
            case AD_STARTED:
                // Interactive unit has started displaying to the viewer
                Log.i(TAG, "TrueX interactive unit started");
                showStatus("Interactive ad • " + event);
                break;
            case AD_DISPLAYED:
                Log.i(TAG, "TrueX interactive assets loaded and visible");
                break;
            case AD_FREE_POD:
                // The viewer completed the requirements to earn the ad-free pod reward.
                // Do not skip immediately; wait for a terminal event (AD_COMPLETED) before skipping the pod.
                Log.i(TAG, "TrueX credit earned (AD_FREE_POD); waiting for terminal event to skip remaining pod");
                truexAdCreditReceived = true;
                break;
            case USER_CANCEL_STREAM:
                // The viewer pressed Back on the choice card or exit prompt to leave playback entirely.
                Log.i(TAG, "Viewer cancelled stream via USER_CANCEL_STREAM");
                showStatus("Viewer cancelled the stream");
                finish();
                break;
            // Terminal events
            case AD_COMPLETED:
            case AD_ERROR:
            case NO_ADS_AVAILABLE:
                Log.i(TAG, "Terminal TrueX event received: " + event);
                finishTruexAd(event);
                break;
            // Informative events
            case OPT_IN:
                // Viewer selected the interactive engagement over regular linear ads
                Log.i(TAG, "Viewer opted in to interactive experience");
                showStatus("Interactive ad • " + event);
                break;
            case OPT_OUT:
                // Viewer chose linear fallback or the choice-card timer expired
                Log.i(TAG, "Viewer opted out of interactive experience");
                showStatus("Interactive ad • " + event);
                break;
            case USER_CANCEL:
                // Viewer backed out of the interactive engagement after opting in
                Log.i(TAG, "Viewer backed out of engagement (USER_CANCEL)");
                break;
            case POPUP_WEBSITE:
                // The ad asks to open an advertiser page; onPause/onResume pause and resume the renderer.
                Object url = data != null ? data.get("url") : null;
                Log.i(TAG, "Opening advertiser page: " + url);
                if (url instanceof String) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse((String) url)));
                    } catch (ActivityNotFoundException error) {
                        Log.w(TAG, "No app can open " + url + ": " + error.getMessage());
                    }
                }
                break;
            case VIDEO_EVENT:
                // Video progress inside the interactive unit; not required for host timeline management
                break;
            default:
                break;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: initializing ManualCsaiActivity");
        setContentView(R.layout.activity_manual_csai);
        playerContainer = findViewById(R.id.playerContainer);
        toolbar = findViewById(R.id.toolbar);
        playerView = findViewById(R.id.playerView);
        rendererContainer = findViewById(R.id.rendererContainer);
        statusText = findViewById(R.id.statusText);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.manual_csai_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        try {
            adBreak = ManualAdBreak.parse(this, R.raw.manual_ad_break);
        } catch (Exception error) {
            throw new IllegalStateException("Can't load manual_ad_break.json", error);
        }
        midrollGate = new ManualAdBreak.MidrollGate(adBreak.timeOffsetMs);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(playerListener);
        showStatus("Loading VAST ad parameters");
        Log.i(TAG, "Resolving VAST ad parameters asynchronously");
        new Thread(this::resolveVastAndStart, "manual-vast-load").start();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void resolveVastAndStart() {
        String userId = ManualAdBreak.newReferenceUserId();
        Log.i(TAG, "VAST user-id generated: " + userId);
        List<ManualAdBreak.Ad> resolvedAds = new ArrayList<>();
        for (ManualAdBreak.Ad ad : adBreak.ads) {
            if (ad.vastUrl == null) {
                resolvedAds.add(ad);
                continue;
            }
            String url = ManualAdBreak.applyVastUserId(ad.vastUrl, userId);
            JSONObject payload = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    payload = ManualVastPayloadParser.load(url);
                } catch (Exception error) {
                    Log.w(TAG, "Attempt " + attempt + ": Failed to load VAST from " + url + ": " + error.getMessage());
                }
                if (payload != null) {
                    break;
                }
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            resolvedAds.add(ad.withVast(url, payload));
        }
        ManualAdBreak resolved = adBreak.withAds(resolvedAds);
        runOnUiThread(() -> {
            if (isFinishing()) {
                return;
            }
            adBreak = resolved;
            Log.i(TAG, "VAST resolution complete; starting content playback from 0ms");
            playContent(0L);
            playerContainer.post(midrollCheck);
        });
    }

    // [1] The host app owns the content timeline and decides when the ad break starts.
    private void startAdBreak() {
        contentPositionMs = player.getCurrentPosition();
        Log.i(TAG, "startAdBreak: pausing content at " + contentPositionMs + "ms and starting ad pod " + adBreak.id);
        player.pause();
        // Viewers can't seek or skip ads with the touch controls.
        playerView.setUseController(false);
        playingAdPod = true;
        currentAdIndex = 0;
        showStatus("Simulated ad response loaded • " + adBreak.id);
        playCurrentAd();
    }

    private void playCurrentAd() {
        ManualAdBreak.Ad ad = currentAdOrNull();
        if (ad == null) {
            Log.i(TAG, "playCurrentAd: no more ads in pod; completing ad break");
            finishAdBreak("Ad pod complete");
            return;
        }
        Log.i(TAG, "playCurrentAd: index=" + currentAdIndex + ", adId=" + ad.id + ", type=" + ad.type + ", mediaUrl=" + ad.mediaUrl);
        // The host owns playback, so interactive ads skip their placeholder video and start the renderer directly.
        if (ad.type != ManualAdBreak.AdType.LINEAR) {
            if (ad.type == ManualAdBreak.AdType.TRUEX) {
                showStatus("TrueX interactive ad • starting renderer");
            } else {
                showStatus("IDVx interactive ad • starting renderer");
            }
            // Stop loading the paused media so it can't report errors while the renderer runs.
            player.stop();
            showInteractiveAd(ad);
            return;
        }
        showStatus("Linear fallback • " + ad.id);
        playerView.setVisibility(View.VISIBLE);
        player.setMediaItem(MediaItem.fromUri(ad.mediaUrl));
        player.prepare();
        player.play();
    }

    // [2] TAR owns the overlay while the paused player stays hidden.
    private void showInteractiveAd(ManualAdBreak.Ad ad) {
        JSONObject adParameters = ad.adParameters;
        if (adParameters == null) {
            Log.e(TAG, "showInteractiveAd: ad " + ad.id + " has no adParameters; continuing fallback pod");
            showStatus("Renderer setup failed: Interactive ad " + ad.id + " has no ad parameters. Continuing fallback pod.");
            completeInteractiveAd(false);
            return;
        }
        Log.i(TAG, "showInteractiveAd: hiding player and presenting TrueX renderer container for " + ad.type);
        playerView.setVisibility(View.INVISIBLE);
        rendererContainer.setVisibility(View.VISIBLE);
        // The renderer takes the whole screen on a phone.
        toolbar.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        truexAdCreditReceived = false;
        truexAdTerminalEvent = false;

        // Configure TrueX ad options:
        // - supportsUserCancelStream: enables Back button to trigger USER_CANCEL_STREAM
        // - appId: package name for telemetry attribution
        // - enableWebViewDebugging: allows inspect via chrome://inspect in debug builds
        TruexAdOptions options = new TruexAdOptions();
        options.supportsUserCancelStream = true;
        options.appId = getPackageName();
        options.enableWebViewDebugging = BuildConfig.DEBUG;

        TruexAdRenderer newRenderer = new TruexAdRenderer(this);
        newRenderer.addEventListener(null, truexAdEventHandler);
        newRenderer.init(adParameters, options);
        truexAdRenderer = newRenderer;
        Log.i(TAG, "Starting TruexAdRenderer inside rendererContainer");
        try {
            newRenderer.start(rendererContainer);
        } catch (RuntimeException error) {
            Log.e(TAG, "Failed to start TruexAdRenderer: " + error.getMessage());
            showStatus("Renderer setup failed: " + error.getMessage() + ". Continuing fallback pod.");
            completeInteractiveAd(false);
        }
    }

    // [3] Terminal event processing: AD_FREE_POD credit skips remaining pod only on AD_COMPLETED.
    private void finishTruexAd(TruexAdEvent event) {
        if (truexAdTerminalEvent) {
            return;
        }
        truexAdTerminalEvent = true;
        Log.i(TAG, "finishTruexAd: event=" + event + ", creditReceived=" + truexAdCreditReceived);
        showStatus("Renderer finished: " + event);
        completeInteractiveAd(truexAdCreditReceived && event == TruexAdEvent.AD_COMPLETED);
    }

    private void completeInteractiveAd(boolean shouldSkipPod) {
        ManualAdBreak.Ad ad = currentAdOrNull();
        if (ad == null) {
            return;
        }
        Log.i(TAG, "completeInteractiveAd: adType=" + ad.type + ", shouldSkipPod=" + shouldSkipPod);
        disposeRenderer();
        if (ManualAdBreak.shouldSkipRemainingPod(ad.type, shouldSkipPod)) {
            Log.i(TAG, "completeInteractiveAd: TrueX credit earned; skipping remaining ads in pod");
            finishAdBreak("TrueX credit earned • remaining ads skipped");
            return;
        }
        // Opt-out, error, cancel, or IDVx: continue with the next ad in the pod.
        Log.i(TAG, "completeInteractiveAd: " + ad.type + " complete without pod skip; continuing fallback pod");
        showStatus(ad.type + " complete • continuing fallback pod");
        advanceAdPod();
    }

    private void advanceAdPod() {
        currentAdIndex += 1;
        Log.i(TAG, "advanceAdPod: advancing to ad index " + currentAdIndex);
        playCurrentAd();
    }

    private void finishAdBreak(String message) {
        Log.i(TAG, "finishAdBreak: " + message + "; resuming content at " + contentPositionMs + "ms");
        playingAdPod = false;
        currentAdIndex = -1;
        disposeRenderer();
        playerView.setUseController(true);
        showStatus(message);
        playContent(contentPositionMs);
    }

    private void playContent(long positionMs) {
        Log.i(TAG, "playContent: setting content media item at " + positionMs + "ms");
        playerView.setVisibility(View.VISIBLE);
        player.setMediaItem(MediaItem.fromUri(CONTENT_URL), positionMs);
        player.prepare();
        player.play();
        showStatus("Content • midroll at " + (adBreak.timeOffsetMs / 1_000) + "s");
    }

    @Nullable
    private ManualAdBreak.Ad currentAdOrNull() {
        if (currentAdIndex < 0 || currentAdIndex >= adBreak.ads.size()) {
            return null;
        }
        return adBreak.ads.get(currentAdIndex);
    }

    private void showStatus(String message) {
        Log.i(TAG, "Status: " + message);
        statusText.setText(message);
    }

    private void disposeRenderer() {
        Log.i(TAG, "disposeRenderer: releasing TruexAdRenderer and resetting container");
        if (truexAdRenderer != null) {
            truexAdRenderer.removeEventListener(null, truexAdEventHandler);
            truexAdRenderer.stop();
        }
        truexAdRenderer = null;
        truexAdCreditReceived = false;
        truexAdTerminalEvent = false;
        rendererContainer.removeAllViews();
        rendererContainer.setVisibility(View.GONE);
        toolbar.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume: truexAdRendererActive=" + (truexAdRenderer != null));
        if (truexAdRenderer != null) {
            truexAdRenderer.resume();
        } else {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause: truexAdRendererActive=" + (truexAdRenderer != null));
        if (truexAdRenderer != null) {
            truexAdRenderer.pause();
        }
        player.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy: releasing resources and player");
        playerContainer.removeCallbacks(midrollCheck);
        disposeRenderer();
        player.removeListener(playerListener);
        playerView.setPlayer(null);
        player.release();
        super.onDestroy();
    }
}
