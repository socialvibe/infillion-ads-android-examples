# Infillion TrueX Android TV Examples

An Android TV reference app demonstrating three complete ways to add Infillion interactive ads to a Media3
player:

| Example | Ad source | Midroll behavior |
| --- | --- | --- |
| **Plain / Manual CSAI** | Bundled JSON fixture | The host app schedules and plays the ad pod. |
| **Google IMA CSAI** | Hosted VAST request | Google IMA requests and sequences client-side ads. |
| **Google IMA SSAI** | Google DAI VOD request | Google DAI returns one stream with stitched ad breaks. |

The examples intentionally duplicate their player, ad, and `TruexAdRenderer` code. Choose one example and
read it from top to bottom without tracing a shared framework.

## TrueX and IDVx

Both formats use Infillion's interactive ad renderer, but they differ in how the viewer enters the experience
and what happens afterward:

- **TrueX** is opt-in. The viewer may choose an interactive engagement instead of the publisher's regular ad
  break. `AD_FREE_POD` records that the viewer earned the reward; after `AD_COMPLETED`, the host app skips the
  rest of that ad pod.
- **IDVx** starts its interactive video experience directly. It does not award an ad-free pod, so the host
  app resumes the normal ad flow when the experience finishes.

If a TrueX viewer does not opt in, or an interactive experience is unavailable, the publisher's normal ad
flow continues.

See [What are Infillion Ads?](https://github.com/socialvibe/infillion-ads-integration-docs/blob/main/docs/overview/what-are-infillion-ads.md)
for broader product context.

## Integration flow

Publishers:

- Get TrueX and IDVx tags (VAST URLs) from Infillion contacts.
- Target those tags in the publisher ad server, CSAI, or SSAI stack.
- Confirm the tag reaches the app: ad-system `trueX` / `IDVx` and the renderer payload.

The exact player and ad-SDK APIs vary by insertion model, but every example follows the same high-level
flow:

1. Add the TrueX renderer and the player or ad-SDK dependencies.
2. Provide a `ViewGroup` above the video player where the renderer can display the interactive experience.
3. Detect an Infillion ad from its ad-system identifier (`trueX` or `IDVx`).
4. Read `adParameters` from the ad metadata. How that JSON arrives depends on the host app and ad framework;
   this repo does not control that path.
5. Pause or coordinate the underlying content/ad playback and, when required, move past the placeholder
   media.
6. Instantiate `TruexAdRenderer` with that JSON, the renderer container, and the correct TrueX/IDVx mode.
7. For TrueX, treat `AD_FREE_POD` as an indicator that the remaining ads in the current pod should be
   skipped after successful completion.
8. On `AD_COMPLETED`, apply any earned ad-free-pod reward; on `AD_ERROR` or `NO_ADS_AVAILABLE`, continue the
   normal fallback ad flow.
9. Release the renderer and player resources with the activity lifecycle.

For platform guidance beyond these runnable examples, see the
[official Android integration documentation](https://socialvibe.github.io/infillion-ads-integration-docs/platforms/android/).
It is still being completed.

## Run the examples

### Requirements

- Android Studio with JDK 17
- Android SDK 35
- Android TV device or emulator running API 28 or newer
- Network access to the sample media, Google IMA, and the TrueX renderer Maven repository

### Android Studio

1. Open the repository in Android Studio.
2. Create or select an Android TV virtual device.
3. Run the `kotlin-ctv-app` configuration.
4. Use the D-pad to focus an integration and press the center/select key.

### Command line

```shell
./gradlew assembleDebug
```

Install `kotlin-ctv-app/build/outputs/apk/debug/kotlin-ctv-app-debug.apk` on an Android TV device or
emulator.

Each example begins content playback and displays its current content, ad-request, linear-ad, interactive-ad,
recovery, or error state in the upper-left status panel.

## Sample configuration

Sample URLs and DAI identifiers are deliberately visible near the top of each example. Replace them with
publisher-owned configuration in a real integration.

The sample uses:

- Public reference content and linear-ad media.
- Public TrueX and IDVx QA/reference payloads.
- Google IMA CSAI sample tag via [`vast-preroll.xml`][csai_vast_preroll_link].
- Google DAI sample content ID `2496857` and video ID `truex-content22-4k` (Google Ad Manager configures 1
  preroll and 3 midrolls via [`ss_sab-vmap.xml`][ss_sab_vmap_link]
  using [`vast-preroll.xml`][vast_preroll_link] and [`vast-midroll.xml`][vast_midroll_link]).

No credentials, signing keys, or production publisher configuration are included.

## Read one integration

- [Plain / Manual CSAI](docs/manual-csai.md)
- [Google IMA CSAI](docs/ima-csai.md)
- [Google IMA SSAI](docs/ima-ssai.md)

Each guide names the package to copy, the key event transitions, and the production concerns intentionally
left outside this reference app.

## Project boundaries

This is documentation that runs, not a production player framework. It does not include analytics, consent,
production identity, retry policy, remote configuration, E2E automation, or publisher-specific VMAP/VAST
parsing. Error and fallback states are visible so developers can observe the integration contract.

See [CONTRIBUTING.md](CONTRIBUTING.md) for build, test, versioning, and pull-request instructions.

[csai_vast_preroll_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/ima-csai/fire-tv/vast-preroll.xml
[ss_sab_vmap_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/dfp-dai/firetv-vmap/ss_sab-vmap.xml
[vast_preroll_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/dfp-dai/firetv-vmap/vast-preroll.xml
[vast_midroll_link]: https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/dfp-dai/firetv-vmap/vast-midroll.xml
