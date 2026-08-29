# Plain / Manual CSAI

Use this example when the application or a publisher-owned ad layer decides when an ad break starts and which ads it contains.

## Copy

Copy the complete `manualcsai` package, `activity_manual_csai.xml`, and `manual_ad_break.json`. The package does not depend on either IMA example.

## Flow

1. The activity starts Media3 content and keeps the native `PlayerView` controls available.
2. A one-shot gate detects the short reference midroll.
3. The activity stores the content position and parses the bundled ad response.
4. Linear ads play through the same case-local ExoPlayer.
5. For TrueX or IDVx, the placeholder is positioned near its end and paused, the player is hidden, and `TruexAdRenderer` starts in the overlay `FrameLayout`.
6. `AD_FREE_POD` records TrueX credit. A terminal renderer event decides whether to skip the remaining pod or continue it.
7. Content is reloaded and restored to its saved position after the pod.

## Renderer contract

The example creates `TruexAdOptions` next to the renderer:

- `supportsUserCancelStream` is enabled for TrueX and disabled for IDVx.
- A fallback advertising ID is generated for this reference run.
- WebView debugging follows `BuildConfig.DEBUG`.
- `pause()`, `resume()`, `stop()`, listener removal, and renderer disposal follow the activity lifecycle.

`POPUP_WEBSITE` is opened only when the renderer supplies a valid HTTP(S) URL. `USER_CANCEL_STREAM` closes the playback screen.

## Replace in production

- Replace the JSON fixture with the publisher's VMAP/VAST or ad-server response.
- Replace the sample content, placeholders, and fallback media.
- Supply the publisher's advertising identity and privacy/consent values.
- Integrate the one-shot gate with the production timeline and persisted playback state.
- Define production retry, telemetry, and renderer-error policy.

