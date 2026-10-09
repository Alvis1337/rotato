import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.gms.oss.licenses)
    alias(libs.plugins.ksp)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// Auto-increment versionCode from git commit count so every CI build is unique.
val gitCommitCount = try {
    val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootProject.projectDir)
        .start()
    proc.inputStream.bufferedReader().readLine().trim().toInt()
} catch (e: Exception) { 1 }

android {
    namespace = "com.chrisalvis.rotato"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chrisalvis.rotato"
        minSdk = 26
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = "1.0"
        resValue("string", "app_name", "Rotato")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MAL credentials: env vars take priority (CI), fall back to local.properties (dev).
        val malClientId     = System.getenv("MAL_CLIENT_ID")     ?: localProps["mal.clientId"]     ?: ""
        val malClientSecret = System.getenv("MAL_CLIENT_SECRET") ?: localProps["mal.clientSecret"] ?: ""
        buildConfigField("String", "MAL_CLIENT_ID",     "\"$malClientId\"")
        buildConfigField("String", "MAL_CLIENT_SECRET", "\"$malClientSecret\"")
        // Reddit "installed app" client ID (reddit.com/prefs/apps). Optional: without it Reddit
        // is fetched anonymously, which Reddit now refuses from many networks.
        val redditClientId = System.getenv("REDDIT_CLIENT_ID") ?: localProps["reddit.clientId"] ?: ""
        buildConfigField("String", "REDDIT_CLIENT_ID", "\"$redditClientId\"")
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
        // Installs beside the release app (its own data), labelled "Rotato Dev".
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
        buildConfig = true
        resValues = true
    }
}



dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.androidx.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.androidx.window)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.drag.select.compose)
    implementation(libs.play.services.oss.licenses)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
