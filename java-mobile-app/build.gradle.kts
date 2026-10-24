plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.infillion.ads.examples.java"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.infillion.ads.examples.java"
        minSdk = 28
        targetSdk = 35
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()

        buildConfigField("String", "TRUEX_AD_RENDERER_VERSION", "\"${libs.truex.renderer.get().version}\"")
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

    buildFeatures {
        buildConfig = true
    }

    lint {
        // TAR brings Glide's optional NotificationTarget; this app never posts notifications.
        disable += "NotificationPermission"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.truex.renderer)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
