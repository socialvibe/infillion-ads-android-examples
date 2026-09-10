# Google IMA SSAI

Use this example when Google DAI returns one VOD stream containing content and stitched ad periods.

## Copy

Copy the complete `imassai` package and `activity_ima_ssai.xml`. The package owns its `VideoStreamPlayer`,
Media3 wrapper, DAI request, seeking, and renderer lifecycle.

## Flow

1. The activity creates a `StreamDisplayContainer` and requests the reference VOD stream with its content and
   video IDs.
2. IMA gives the case-local `VideoStreamPlayer` an HLS, DASH, or progressive URL to load through Media3.
3. Cue points become native `PlayerView` ad markers. Player controls are disabled during reported ad breaks.
4. Seeking over an unplayed break snaps back to its cue point and remembers the viewer's requested
   destination.
5. On ad `STARTED`, the example tracks the stitched placeholder end and identifies TrueX or IDVx through
   `AdSystem`. TrueX ads must be the first ad in the pod (`adPosition == 1`); later TrueX ads continue as
   stitched linear ads.
6. The stitched stream pauses and hides while `TruexAdRenderer` owns the overlay, initialized with the JSON
   payload extracted from the TrueX companion ad or trafficking parameters.
7. `AD_FREE_POD` records TrueX credit. On `AD_COMPLETED`, earned credit seeks to the ad-break offset plus its
   duration and a small safety margin (Google DAI `StreamManager` does not provide an ad break discard
   method). Without successful completion (opt-out, cancel, error, or IDVx), playback seeks just before the
   interactive placeholder end so the stitched pod continues.

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

## Time domains

SSAI exposes stream time, ad progress, cue-point offsets, and the viewer's intended seek destination. Keep
them distinct:

- **Stream position** is the Media3 position in the stitched URL.
- **Cue-point time** identifies an ad break in that stream.
- **Ad progress** reports the current stitched break duration.
- **Resume-after-snapback** is the destination the viewer originally requested.

The skip-target calculation is a pure helper with a unit test so the most consequential seek rule is easy to
inspect.

## Sample stream configuration

The reference Google DAI stream uses content source ID `2496857` and video ID `truex-content22-4k`. Google Ad
Manager (GAM) uses [`ss_sab-vmap.xml`][ss_sab_vmap_link] to configure the ad breaks, which defines **1
preroll and 3 midrolls**:

- **Preroll** (at `start`): [`vast-preroll.xml`][vast_preroll_link] (TrueX, IDVx, Airline, Petcare)
- **Midroll 1** (at `06:17`): [`vast-midroll.xml`][vast_midroll_link] (TrueX, IDVx, Coffee, Petcare)
- **Midroll 2** (at `11:17`): [`vast-midroll.xml`][vast_midroll_link] (TrueX, IDVx, Coffee, Petcare)
- **Midroll 3** (at `18:34`): [`vast-midroll.xml`][vast_midroll_link] (TrueX, IDVx, Coffee, Petcare)

## Replace in production

- Supply publisher Google DAI asset keys or content/video IDs.
- Add the publisher's authentication, stream-format, and manifest requirements.
- Reconcile IMA stream time with the production player's timeline model.
- Test all seek directions, live-window behavior if applicable, and every real pod shape.
- Add production stream-error retry, observability, and privacy configuration.

[ss_sab_vmap_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/dfp-dai/firetv-vmap/ss_sab-vmap.xml
[vast_preroll_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/dfp-dai/firetv-vmap/vast-preroll.xml
[vast_midroll_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/dfp-dai/firetv-vmap/vast-midroll.xml
