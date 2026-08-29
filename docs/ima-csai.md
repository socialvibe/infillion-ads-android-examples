# Google IMA CSAI

Use this example when Google IMA requests and sequences a client-side ad pod separately from the content stream.

## Copy

Copy the complete `imacsai` package and `activity_ima_csai.xml`. Its IMA adapter, ExoPlayer, and renderer integration are intentionally local to the package.

## Flow

1. The activity begins content playback and exposes content progress to IMA.
2. The short reference midroll issues an `AdsRequest` with the hosted sample VAST URL.
3. The case-local `VideoAdPlayer` lets IMA load and control ad media through ExoPlayer.
4. `CONTENT_PAUSE_REQUESTED` stores the content position; `CONTENT_RESUME_REQUESTED` reloads content at that position.
5. On `STARTED`, the controller checks `AdSystem`. Normal linear ads remain under IMA control.
6. A TrueX or IDVx placeholder pauses IMA. The app extracts trafficking parameters or a valid config URL, hides the player, and starts `TruexAdRenderer`.
7. On a terminal renderer event, TrueX credit discards the active IMA break. Without credit, the placeholder finishes and IMA continues the remaining ads. IDVx always continues.

## Important IMA events

| IMA event | Host responsibility |
| --- | --- |
| `LOADED` | Start the `AdsManager`. |
| `CONTENT_PAUSE_REQUESTED` | Stop content and preserve its position. |
| `STARTED` | Detect an interactive placeholder and enter the renderer when needed. |
| `CONTENT_RESUME_REQUESTED` | Restore content and controls. |
| `ALL_ADS_COMPLETED` | Destroy the completed ad manager. |
| Ad error | Show the error, release ad state, and deliberately recover content. |

Malformed interactive payloads are visible errors. The example resumes IMA's fallback path rather than pretending the interactive ad succeeded.

## Replace in production

- Build the ad-tag URL from publisher inventory and targeting.
- Connect consent, advertising identity, companion ads, and measurement.
- Decide how playback state survives process death and device interruptions.
- Replace reference status text with publisher UI or telemetry.
- Test the publisher's real VMAP/VAST redirects, pod order, and fallback media.

