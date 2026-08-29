# Google IMA SSAI

Use this example when Google DAI returns one VOD stream containing content and stitched ad periods.

## Copy

Copy the complete `imassai` package and `activity_ima_ssai.xml`. The package owns its `VideoStreamPlayer`, Media3 wrapper, DAI request, seeking, and renderer lifecycle.

## Flow

1. The activity creates a `StreamDisplayContainer` and requests the reference VOD stream with its content and video IDs.
2. IMA gives the case-local `VideoStreamPlayer` an HLS, DASH, or progressive URL to load through Media3.
3. Cue points become native `PlayerView` ad markers. Player controls are disabled during reported ad breaks.
4. Seeking over an unplayed break snaps back to its cue point and remembers the viewer's requested destination.
5. On ad `STARTED`, the example tracks the stitched placeholder end and identifies TrueX or IDVx through `AdSystem`.
6. The stitched stream pauses and hides while `TruexAdRenderer` owns the overlay.
7. `AD_FREE_POD` records TrueX credit. On `AD_COMPLETED`, earned credit seeks to the ad-break offset plus its duration and a small safety margin. Without successful completion, playback seeks just before the interactive placeholder end so the stitched pod continues. IDVx always continues.

## Time domains

SSAI exposes stream time, ad progress, cue-point offsets, and the viewer's intended seek destination. Keep them distinct:

- **Stream position** is the Media3 position in the stitched URL.
- **Cue-point time** identifies an ad break in that stream.
- **Ad progress** reports the current stitched break duration.
- **Resume-after-snapback** is the destination the viewer originally requested.

The skip-target calculation is a pure helper with a unit test so the most consequential seek rule is easy to inspect.

## Replace in production

- Supply publisher Google DAI asset keys or content/video IDs.
- Add the publisher's authentication, stream-format, and manifest requirements.
- Reconcile IMA stream time with the production player's timeline model.
- Test all seek directions, live-window behavior if applicable, and every real pod shape.
- Add production stream-error retry, observability, and privacy configuration.
