# Google IMA CSAI

Use this example when Google IMA requests and sequences a client-side ad pod separately from the content
stream.

## Copy

Copy the complete `imacsai` package and `activity_ima_csai.xml`. Its IMA adapter, ExoPlayer, and renderer
integration are intentionally local to the package.

## Flow

1. The activity begins content playback and exposes content progress to IMA.
2. The short reference midroll issues an `AdsRequest` with the hosted sample VAST URL.
3. The case-local `VideoAdPlayer` lets IMA load and control ad media through ExoPlayer.
4. `CONTENT_PAUSE_REQUESTED` stores the content position; `CONTENT_RESUME_REQUESTED` reloads content at that
   position.
5. On `STARTED`, the controller checks `AdSystem`. Normal linear ads remain under IMA control. TrueX ads must
   be the first ad in the pod (`adPosition == 1`); later TrueX ads continue as linear playback.
6. An eligible TrueX or IDVx placeholder pauses IMA. The app parses the JSON payload from trafficking
   parameters, hides the player, and starts `TruexAdRenderer`.
7. `AD_FREE_POD` records TrueX credit. On `AD_COMPLETED`, earned credit discards the active IMA break
   (`discardAdBreak()`). Without successful completion (opt-out, cancel, or error), the player seeks near the
   end of the placeholder so IMA finishes it and continues the remaining fallback ads. IDVx always continues.

## Renderer contract

The activity creates `TruexAdRenderer` and `TruexAdOptions` itself:

- `supportsUserCancelStream` is enabled for TrueX and IDVx. Back then fires `USER_CANCEL_STREAM`. If it is
  false, TrueX choice-card Back is `OPT_OUT`; IDVx Back does nothing.
- `appId` uses the application package name.
- `enableWebViewDebugging` is debug-only (`BuildConfig.DEBUG`).
- Advertising IDs are not set here. The ad-server `advertising-id` macro should already be in `AdParameters`;
  confirm that during integration certification.
- `pause()`, `resume()`, `stop()`, listener removal, and renderer disposal follow the activity lifecycle.

`USER_CANCEL_STREAM` closes the playback screen. `POPUP_WEBSITE` is mobile-only and is not used on CTV.

## Important IMA events

| IMA event | Host responsibility |
| --- | --- |
| `LOADED` | Start the `AdsManager`. |
| `CONTENT_PAUSE_REQUESTED` | Stop content and preserve its position. |
| `STARTED` | Detect an interactive placeholder and enter the renderer when needed. |
| `CONTENT_RESUME_REQUESTED` | Restore content and controls. |
| `ALL_ADS_COMPLETED` | Destroy the completed ad manager. |
| Ad error | Show the error, release ad state, and deliberately recover content. |

Malformed interactive payloads are visible errors. The example resumes IMA's fallback path rather than
pretending the interactive ad succeeded.

## Replace in production

- Build the ad-tag URL from publisher inventory and targeting.
- Connect consent, advertising identity, companion ads, and measurement.
- Decide how playback state survives process death and device interruptions.
- Replace reference status text with publisher UI or telemetry.
- Test the publisher's real VMAP/VAST redirects, pod order, and fallback media.

## Sample tag configuration

The reference Google IMA CSAI example requests its sample ad break from
[`vast-preroll.xml`][csai_vast_preroll_link], which defines a client-side pod with TrueX, IDVx, and linear
fallback ads.

[csai_vast_preroll_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/ima-csai/fire-tv/vast-preroll.xml
