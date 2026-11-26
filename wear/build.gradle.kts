import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The watch app must share the phone app's package name and signing key, or the Wearable Data
// Layer won't connect them. Both read the same keystore.properties.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

val gitCommitCount = try {
    val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootProject.projectDir)
        .start()
    proc.inputStream.bufferedReader().readLine().trim().toInt()
} catch (e: Exception) { 1 }

android {
    namespace = "com.chrisalvis.rotato.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chrisalvis.rotato"
        minSdk = 30
        targetSdk = 36
        versionCode = gitCommitCount
        // Same as the phone app: matches the release tag (v1.0.<commit count>).
        versionName = "1.0.$gitCommitCount"
        resValue("string", "app_name", "Rotato")
    }

    val hasKeystore = keystoreProps.isNotEmpty() && keystoreProps["storeFile"] != null

    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        // Pairs with the phone's "Rotato Dev" debug build.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Rotato Dev")
        }
        release {
            isMinifyEnabled = true
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.complications.data.source)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
