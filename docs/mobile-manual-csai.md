# Plain / Manual CSAI (mobile)

Use this example when a phone or tablet app, or a publisher-owned ad layer, decides when an ad break starts
and which ads it contains. The same example ships in two languages:

| App | Package |
| --- | --- |
| `kotlin-mobile-app` | `com.infillion.ads.examples.kotlin.manualcsai` |
| `java-mobile-app` | `com.infillion.ads.examples.java.manualcsai` |

Both apps behave the same. The flow, VAST parsing, and renderer handling match the
[CTV Manual CSAI example](manual-csai.md); only the screen layout and touch handling are mobile-specific.

## Copy

Copy the complete `manualcsai` package, `activity_manual_csai.xml`, and `manual_ad_break.json` from the app
in your language. The package does not depend on the home screen or on the other app.

## Flow

1. Before content starts, the activity replaces `${user-id}` / `#{user-id}` in each interactive `vastUrl`
   with `ref-app-{uuid}` (new uuid per load), fetches the VAST (up to 3 attempts), and reads the ad parameters
   JSON payload.
2. The activity starts Media3 content with the native `PlayerView` touch controls.
3. A one-shot gate detects the 10-second reference midroll.
4. The activity stores the content position, hides the touch controls, and plays the resolved pod.
5. Linear ads play through the same case-local ExoPlayer.
6. For TrueX or IDVx, the placeholder video is not played: the player is stopped and hidden, the toolbar and
   status panel are hidden, and `TruexAdRenderer.init` receives the extracted JSON in the overlay
   `FrameLayout`.
7. `AD_FREE_POD` records TrueX credit. On `AD_COMPLETED`, earned credit skips the remaining pod; opt-out,
   errors, unavailable ads, and IDVx continue directly with the next ad in the pod.
8. Content is reloaded and restored to its saved position after the pod, with the touch controls back.

Rotation does not restart playback: the activity handles orientation and screen-size changes itself, so the
player and renderer keep running.

## Ad parameters

The parser is the same as the CTV example: it checks for a `<Companion apiFramework="truex">` JSON data URL
first, then falls back to `<Linear><AdParameters>`, and fails if neither has valid JSON. See
[VAST tag formats and ad parameters](../README.md#vast-tag-formats-and-ad-parameters) for both tag formats.
The sample fixture uses the TrueX `.../vast/companion` tag and the IDVx `.../vast/idvx/generic` tag, so both
paths run.

## Renderer contract

The activity creates `TruexAdRenderer` and `TruexAdOptions` itself:

- `supportsUserCancelStream` is enabled. Back then fires `USER_CANCEL_STREAM`, which closes the playback
  screen.
- `appId` uses the application package name.
- `enableWebViewDebugging` is debug-only (`BuildConfig.DEBUG`).
- Advertising IDs are not set here. The ad-server `advertising-id` macro should already be in `AdParameters`;
  confirm that during integration certification.
- `POPUP_WEBSITE` opens the advertiser URL with `Intent.ACTION_VIEW`. The activity's `onPause()` / `onResume()`
  pause and resume the renderer while the browser is in front.
- `pause()`, `resume()`, `stop()`, listener removal, and renderer disposal follow the activity lifecycle.

## Replace in production

- Replace the JSON fixture and sample VAST URLs with the publisher's VMAP/VAST or ad-server response.
- Read `adParameters` from the metadata the host ad framework actually delivers.
- Replace the sample content and fallback media.
- Confirm the ad-server `advertising-id` macro lands in `AdParameters` during integration certification.
- Integrate the one-shot gate with the production timeline and persisted playback state.
- Define production retry, telemetry, and renderer-error policy.

## Sample tag configuration and test IP

The sample TrueX and IDVx tags are the same as the iOS examples. Placements are per platform, so ask Infillion
for Android placements before reporting on traffic.

The tags include `&ip=158.106.195.210` (Infillion's NYC office IP). Because the TrueX ad server currently
serves ads in the US and Canada only, this keeps sample requests filled for teams and CI runners outside those
regions. Replace or remove it with publisher-managed geo/IP handling in production.
