# Plain Java Android TV App Plan

## Status

Proposed. This document describes a future Java/XML Views reference app; it does not change the current Kotlin/Compose app.

## Goal

Add a second, independently installable Android TV application for developers whose host apps use Java and the Android View system. The Java app should demonstrate the same three integrations as the Kotlin app without requiring Jetpack Compose or the Leanback UI library.

## Decisions

- Rename the current `app` module to `kotlin-app`.
- Add a separate `java-app` Android application module.
- Use Java, XML layouts, standard Android Views, and `RecyclerView` in `java-app`.
- Keep `LEANBACK_LAUNCHER` and the TV manifest feature because they identify TV applications; do not add Leanback UI components.
- Give each app a distinct `applicationId` and launcher label so both APKs can be installed together.
- Share root build configuration and version values only.
- Do not share player, ad-flow, or renderer implementations between the two apps. Complete examples are more useful here than abstraction.

## Target structure

```text
truex-android-examples/
├── kotlin-app/
│   ├── build.gradle.kts
│   └── src/
├── java-app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/infillion/truex/reference/java/
│       │   │   ├── MainActivity.java
│       │   │   ├── shell/
│       │   │   │   ├── ExampleEntry.java
│       │   │   │   └── ExampleAdapter.java
│       │   │   ├── manualcsai/
│       │   │   ├── imacsai/
│       │   │   └── imassai/
│       │   └── res/
│       │       ├── drawable/
│       │       ├── font/
│       │       ├── layout/
│       │       │   ├── activity_main.xml
│       │       │   ├── item_example.xml
│       │       │   ├── activity_manual_csai.xml
│       │       │   ├── activity_ima_csai.xml
│       │       │   └── activity_ima_ssai.xml
│       │       └── values/
│       └── test/java/com/infillion/truex/reference/java/
├── docs/
│   ├── kotlin/
│   └── java/
├── gradle.properties
└── settings.gradle.kts
```

`settings.gradle.kts` will include both application modules:

```kotlin
include(":kotlin-app")
include(":java-app")
```

## Implementation phases

### 1. Rename the existing module

- Move `app/` to `kotlin-app/`.
- Change `include(":app")` to `include(":kotlin-app")`.
- Update README commands, APK paths, workflows, and documentation links.
- Preserve the current package name, `applicationId`, behavior, and tests.

Verification:

```shell
./gradlew :kotlin-app:assembleDebug :kotlin-app:testDebugUnitTest
```

### 2. Create the Java application shell

- Add `java-app` with the Android application plugin and Java 17.
- Use the shared `VERSION_CODE` and `VERSION_NAME` properties.
- Use a distinct application ID such as `com.infillion.truex.reference.java`.
- Recreate the TV launcher with XML, a `RecyclerView`, standard focus handling, and the existing Infillion visual assets.
- Preserve deterministic D-pad navigation, visible focus, and ten-foot-readable text.

Verification:

- Install both debug APKs together.
- Confirm both launcher entries are distinct.
- Navigate every Java launcher card with only the D-pad.

### 3. Port the three integrations independently

Implement separate Java packages for:

1. Plain/manual CSAI.
2. Google IMA CSAI.
3. Google IMA SSAI.

Each package should contain its own player, ad-event handling, TrueX/IDVx renderer integration, lifecycle cleanup, and status UI. Translate behavior rather than introducing a Kotlin-to-Java bridge.

Verification:

- Content starts in every example.
- The reference midroll is reached.
- TrueX can emit `AD_FREE_POD`, then skip the remaining pod only after `AD_COMPLETED`.
- IDVx completes without skipping the remaining pod.
- `AD_COMPLETED`, `AD_ERROR`, and `NO_ADS_AVAILABLE` return to a valid playback state.

### 4. Add Java-specific documentation and tests

- Split each integration guide into `docs/kotlin/` and `docs/java/`.
- Explain which Java package and XML layout to copy.
- Add Java unit tests for payload parsing and ad-break state decisions.
- Update the root README to present Kotlin/Compose and Java/Views as equal entry points.
- Keep the existing pull-request workflow running unit tests for both modules.

Verification:

```shell
./gradlew testDebugUnitTest
```

## Definition of done

- The repository produces two independently installable Android TV APKs.
- The Kotlin app behaves exactly as it did before the module rename.
- The Java app contains no Kotlin sources, Compose dependencies, or Leanback UI components.
- All three Java integrations are understandable and copyable in isolation.
- Both apps use the same public sample configuration and renderer behavior.
- Root documentation clearly directs developers to the implementation matching their language and UI toolkit.
- All unit tests pass and both debug APKs build.

## Expected tradeoff

The Java app intentionally duplicates substantial code and assets. This increases maintenance cost, but it keeps each language-specific reference complete and avoids forcing Java developers to translate Compose or Kotlin before understanding the ad integration.
