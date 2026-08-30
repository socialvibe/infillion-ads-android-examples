# Plain / Manual CSAI

Use this example when the application or a publisher-owned ad layer decides when an ad break starts and which ads it contains.

## Copy

Copy the complete `manualcsai` package, `activity_manual_csai.xml`, and `manual_ad_break.json`. The package does not depend on either IMA example.

## Flow

1. Before content starts, the activity fetches each interactive `vastUrl` and reads `<AdParameters>` JSON, or companion `StaticResource` JSON when `AdParameters` is absent.
2. The activity starts Media3 content and keeps the native `PlayerView` controls available.
3. A one-shot gate detects the short reference midroll.
4. The activity stores the content position and plays the resolved pod.
5. Linear ads play through the same case-local ExoPlayer.
6. For TrueX or IDVx, the placeholder is positioned near its end and paused, the player is hidden, and `TruexAdRenderer.init` receives the extracted JSON in the overlay `FrameLayout`.
7. `AD_FREE_POD` records TrueX credit. On `AD_COMPLETED`, earned credit skips the remaining pod; errors and unavailable ads continue the fallback pod.
8. Content is reloaded and restored to its saved position after the pod.

## Renderer contract

The example creates `TruexAdOptions` next to the renderer:

- `supportsUserCancelStream` is enabled for TrueX and disabled for IDVx.
- A fallback advertising ID is generated for this reference run.
- WebView debugging follows `BuildConfig.DEBUG`.
- `pause()`, `resume()`, `stop()`, listener removal, and renderer disposal follow the activity lifecycle.

`POPUP_WEBSITE` is opened only when the renderer supplies a valid HTTP(S) URL. `USER_CANCEL_STREAM` closes the playback screen.

## Replace in production

- Replace the JSON fixture and sample VAST URLs with the publisher's VMAP/VAST or ad-server response.
- Read `adParameters` from the metadata the host ad framework actually delivers.
- Replace the sample content, placeholders, and fallback media.
- Supply the publisher's advertising identity and privacy/consent values.
- Integrate the one-shot gate with the production timeline and persisted playback state.
- Define production retry, telemetry, and renderer-error policy.
