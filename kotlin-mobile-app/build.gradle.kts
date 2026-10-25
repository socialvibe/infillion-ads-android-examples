plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.infillion.ads.examples.kotlin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.infillion.ads.examples.kotlin"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        // TAR brings Glide's optional NotificationTarget; this app never posts notifications.
        disable += "NotificationPermission"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.truex.renderer)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
