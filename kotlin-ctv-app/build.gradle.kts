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

val prepareDevice = tasks.register<Exec>("prepareDeviceForTests") {
    group = "verification"
    description = "Prepares device storage and clears logcat before instrumented tests"
    commandLine(
        "bash", "-c",
        """
        adb shell "mkdir -p /sdcard/Download/test-artifacts && rm -f /sdcard/Download/test-artifacts/*" || true
        adb logcat -c || true
        """.trimIndent(),
    )
}

val pullTestArtifacts = tasks.register<Exec>("pullTestArtifacts") {
    group = "verification"
    description = "Pulls test screenshots and dumps logcat to build/reports/test-artifacts"
    commandLine(
        "bash", "-c",
        """
        mkdir -p build/reports/test-artifacts
        adb logcat -d > build/reports/test-artifacts/logcat.txt || true
        adb pull /sdcard/Download/test-artifacts/. build/reports/test-artifacts/ || true
        """.trimIndent(),
    )
}

tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    dependsOn(prepareDevice)
    finalizedBy(pullTestArtifacts)
}

tasks.register("runFunctionalUiTest") {
    group = "verification"
    description = "Runs connectedDebugAndroidTest and collects test artifacts"
    dependsOn(tasks.matching { it.name == "connectedDebugAndroidTest" })
}
