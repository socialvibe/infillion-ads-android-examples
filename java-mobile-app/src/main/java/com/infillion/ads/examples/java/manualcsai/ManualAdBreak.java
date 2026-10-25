package com.infillion.ads.examples.java.manualcsai;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class ManualAdBreak {
    enum AdType {
        TRUEX,
        IDVX,
        LINEAR,
    }

    static final class Ad {
        final String id;
        final AdType type;
        final String mediaUrl;
        @Nullable final String vastUrl;
        final long durationMs;
        @Nullable final JSONObject adParameters;

        Ad(String id, AdType type, String mediaUrl, @Nullable String vastUrl, long durationMs,
           @Nullable JSONObject adParameters) {
            this.id = id;
            this.type = type;
            this.mediaUrl = mediaUrl;
            this.vastUrl = vastUrl;
            this.durationMs = durationMs;
            this.adParameters = adParameters;
        }

        Ad withVast(String vastUrl, @Nullable JSONObject adParameters) {
            return new Ad(id, type, mediaUrl, vastUrl, durationMs, adParameters);
        }
    }

    static final class MidrollGate {
        private final long timeOffsetMs;
        private boolean triggered;

        MidrollGate(long timeOffsetMs) {
            this.timeOffsetMs = timeOffsetMs;
        }

        boolean shouldTrigger(long positionMs) {
            if (triggered || positionMs < timeOffsetMs) {
                return false;
            }
            triggered = true;
            return true;
        }
    }

    final String id;
    final long timeOffsetMs;
    final List<Ad> ads;

    ManualAdBreak(String id, long timeOffsetMs, List<Ad> ads) {
        this.id = id;
        this.timeOffsetMs = timeOffsetMs;
        this.ads = Collections.unmodifiableList(ads);
    }

    ManualAdBreak withAds(List<Ad> ads) {
        return new ManualAdBreak(id, timeOffsetMs, ads);
    }

    static ManualAdBreak parse(Context context, @RawRes int resourceId) throws IOException, JSONException {
        try (InputStream input = context.getResources().openRawResource(resourceId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append('\n');
            }
            return parse(json.toString());
        }
    }

    static ManualAdBreak parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray adsJson = root.getJSONArray("ads");
        List<Ad> ads = new ArrayList<>();
        for (int index = 0; index < adsJson.length(); index++) {
            JSONObject item = adsJson.getJSONObject(index);
            String system = item.getString("adSystem");
            AdType type;
            if (system.equalsIgnoreCase("trueX")) {
                type = AdType.TRUEX;
            } else if (system.equalsIgnoreCase("IDVx")) {
                type = AdType.IDVX;
            } else {
                type = AdType.LINEAR;
            }
            String vastUrl = item.optString("vastUrl");
            ads.add(new Ad(
                    item.getString("id"),
                    type,
                    item.getString("mediaUrl"),
                    vastUrl.trim().isEmpty() ? null : vastUrl,
                    item.getLong("durationSeconds") * 1_000L,
                    null));
        }
        if (ads.isEmpty()) {
            throw new IllegalArgumentException("The manual ad response contains no ads");
        }
        return new ManualAdBreak(root.getString("breakId"), root.getLong("timeOffsetMs"), ads);
    }

    static boolean shouldSkipRemainingPod(AdType type, boolean shouldSkipPod) {
        return type == AdType.TRUEX && shouldSkipPod;
    }

    static String newReferenceUserId() {
        return "ref-app-" + UUID.randomUUID();
    }

    static String applyVastUserId(String url, String userId) {
        return url.replace("${user-id}", userId).replace("#{user-id}", userId);
    }
}
