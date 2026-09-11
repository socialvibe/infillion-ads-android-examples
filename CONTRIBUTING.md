# Contributing

## Prerequisites

- Android Studio
- JDK 17
- Android SDK 35
- An Android TV device or emulator running API 28 or newer
- Network access to the sample media, Google IMA, and the TrueX renderer Maven repository

## Build and test

Build the debug APK:

```shell
./gradlew assembleDebug
```

Run all unit tests:

```shell
./gradlew testDebugUnitTest
```

Run functional UI tests on a connected Android TV device or emulator:

```shell
./gradlew connectedDebugAndroidTest
```

Or run via standard `adb`:

```shell
adb shell am instrument -w -r -e class com.infillion.truex.reference.manualcsai.ManualCsaiTruexFlowTest com.infillion.truex.reference.test/androidx.test.runner.AndroidJUnitRunner
```

## Branch workflow

While this repository has no remote and is being bootstrapped locally, direct work on `main` is allowed.

After the initial remote push:

1. Fast-forward local `main` to `origin/main`.
2. Create `feature/<TICKET>/<description>` for a feature or `bugfix/<TICKET>/<description>` for a bug.
3. Implement the change and ensure all code-related changes pass both unit tests and functional UI tests.
4. Increment the Android app version.
5. Commit and open a pull request into `main`.
6. Wait for the approvals required by company policy.
7. Merge the approved pull request manually through the GitHub web interface.

Do not commit directly to remote `main`.

## Versioning

[gradle.properties](gradle.properties) is the version source of truth:

```properties
VERSION_CODE=1
VERSION_NAME=1.0.0
```

Every pull request must:

- Increment `VERSION_CODE` by exactly one.
- Increment the patch component of `VERSION_NAME` by exactly one.
- Leave the major and minor components unchanged.

The pull-request workflow compares both values with the target `main` revision and fails if either increment is incorrect.

## Commits

Commit messages and pull request titles use `<TICKET> - <MESSAGE>`, for example:

```text
PI-3486 - Add Android TV TrueX reference app
```

Pull request titles must be shorter than 80 characters.

## Pull requests and releases

Use [.github/pull_request_template.md](.github/pull_request_template.md) for the pull request description. Every pull request must pass all unit tests and functional UI tests, and validate the version increment.

After an approved pull request is manually merged to `main`, the release workflow creates tag `v<VERSION_NAME>` and a GitHub release with generated release notes.
