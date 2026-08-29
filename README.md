# Infillion TrueX Android TV Examples

A single Android TV reference app demonstrating three complete ways to add Infillion interactive ads to a Media3 player:

- **Plain / Manual CSAI** - the host app schedules and plays the ad pod.
- **Google IMA CSAI** - Google IMA requests and sequences client-side ads.
- **Google IMA SSAI** - Google DAI returns one stream with stitched ad breaks.

The examples intentionally duplicate their player, ad, and `TruexAdRenderer` code. Choose one example and read it from top to bottom without tracing a shared framework.

## Requirements

- Android Studio with JDK 17
- Android SDK 35
- Android TV device or emulator running API 28 or newer
- Network access to the sample media, Google IMA, and the TrueX renderer Maven repository

## Run

1. Open the repository in Android Studio.
2. Create or select an Android TV virtual device.
3. Run the `app` configuration.
4. Use the D-pad to focus an integration and press the center/select key.

From a terminal:

```shell
./gradlew assembleDebug
```

Install the APK from `app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests:

```shell
./gradlew testDebugUnitTest
```

## What happens

Each screen begins content playback and exposes its ad state in the upper-left status panel.

| Example | Ad source | Midroll behavior |
| --- | --- | --- |
| Manual CSAI | Bundled JSON fixture | The app triggers a short reference midroll and owns pod playback. |
| IMA CSAI | Hosted VAST request | The app asks IMA for a client-side pod at the reference midroll. |
| IMA SSAI | Google DAI VOD request | IMA returns a stitched stream and reports its ad periods. |

Interactive ads are identified by their `AdSystem`:

- **TrueX** presents a choice card. `AD_FREE_POD` records earned credit; the app waits for a renderer terminal event before skipping the rest of the break.
- **IDVx** begins its interactive experience directly. It never earns pod credit and playback continues with the next ad.

`AD_COMPLETED`, `AD_ERROR`, and `NO_ADS_AVAILABLE` are terminal renderer events. Each example then either skips the break when TrueX credit was earned or follows its own fallback path.

## Sample configuration

Sample URLs and DAI identifiers are deliberately visible near the top of each example. Replace them with publisher-owned configuration in a real integration.

The sample uses:

- Public reference content and linear-ad media.
- Public TrueX and IDVx QA/reference payloads.
- Google DAI sample content ID `2496857` and video ID `truex-content22-4k`.

No credentials, signing keys, or production publisher configuration are included.

## Read one integration

- [Plain / Manual CSAI](docs/manual-csai.md)
- [Google IMA CSAI](docs/ima-csai.md)
- [Google IMA SSAI](docs/ima-ssai.md)

Each guide names the package to copy, the key event transitions, and the production concerns intentionally left outside this reference app.

## Project boundaries

This is documentation that runs, not a production player framework. It does not include analytics, consent, production identity, retry policy, remote configuration, E2E automation, or publisher-specific VMAP/VAST parsing. Error and fallback states are visible so developers can observe the integration contract.

## Development workflow

After the initial local bootstrap is pushed, all changes start from current `main` on a `feature/<TICKET>/<description>` or `bugfix/<TICKET>/<description>` branch. Each pull request must:

1. Include the required implementation and unit tests.
2. Increment `VERSION_CODE` by one in `gradle.properties`.
3. Increment the patch component of `VERSION_NAME` by one.
4. Pass the pull-request workflow.
5. Receive the approvals required by company policy before a manual merge to `main`.

## Versioning and releases

`gradle.properties` is the version source of truth:

```properties
VERSION_CODE=1
VERSION_NAME=1.0.0
```

`VERSION_CODE` is Android's monotonically increasing internal version. `VERSION_NAME` is the public semantic version.

The pull-request workflow runs `testDebugUnitTest` and verifies both version increments. When a pull request is closed as merged into `main`, the release workflow creates tag `v<VERSION_NAME>` and a GitHub release with generated release notes.

## Commits

Commit messages and pull request titles use `<TICKET> - <MESSAGE>`, for example:

```text
PI-3486 - Add Android TV TrueX reference app
```
