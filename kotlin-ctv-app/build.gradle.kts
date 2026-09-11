plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.infillion.truex.reference"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.infillion.truex.reference"
        minSdk = 28
        targetSdk = 35
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // TAR brings Glide's optional NotificationTarget; this app never posts notifications.
        disable += "NotificationPermission"
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.google.ima)
    implementation(libs.truex.renderer)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.uiautomator)
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

tasks.register<Exec>("runFunctionalUiTest") {
    group = "verification"
    description = "Installs APKs, runs functional UI tests via adb, and pulls screenshots & logcat to build/reports/test-artifacts"
    dependsOn("assembleDebug", "assembleDebugAndroidTest")
    commandLine(
        "bash", "-c",
        """
        set -euo pipefail
        mkdir -p build/reports/test-artifacts && \
        adb shell "mkdir -p /sdcard/Download/test-artifacts && rm -f /sdcard/Download/test-artifacts/*" && \
        adb install -r -t build/outputs/apk/debug/kotlin-ctv-app-debug.apk && \
        adb install -r -t build/outputs/apk/androidTest/debug/kotlin-ctv-app-debug-androidTest.apk && \
        adb logcat -c
        set +e
        set -o pipefail
        adb shell am instrument -w -r -e package com.infillion.truex.reference.manualcsai com.infillion.truex.reference.test/androidx.test.runner.AndroidJUnitRunner | tee build/reports/test-artifacts/instrumentation_output.txt
        test_status=${'$'}?

        echo "=== DUMPING LOGCAT TO ARTIFACTS ==="
        adb logcat -d > build/reports/test-artifacts/logcat.txt
        echo "=== PULLING SCREENSHOTS FROM DEVICE ==="
        adb pull /sdcard/Download/test-artifacts/. build/reports/test-artifacts/ || true

        echo "=== CONVERTING INSTRUMENTATION TO JUNIT XML ==="
        python3 ../scripts/parse_instrumentation_to_junit.py build/reports/test-artifacts/instrumentation_output.txt build/reports/test-artifacts/TEST-functional-ui.xml || true

        if [ ${'$'}test_status -ne 0 ] || ! grep -E -q "OK \([1-9][0-9]* tests?\)" build/reports/test-artifacts/instrumentation_output.txt; then
            echo "=== FUNCTIONAL UI TESTS FAILED ==="
            adb logcat -d -s ManualCsai ManualCsaiTruexFlowTest ManualCsaiIdvxFlowTest TruexAdRenderer TruexAdEvent
            exit 1
        fi
        echo "=== ALL FUNCTIONAL UI TESTS PASSED ==="
        """.trimIndent(),
    )
}
