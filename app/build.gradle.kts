import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Read local.properties securely
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val tmdbApiKey: String = localProperties.getProperty("TMDB_API_KEY") ?: ""
// Wyzie subtitle API key - also kept only in local.properties (not on GitHub)
val wyzieApiKey: String = localProperties.getProperty("WYZIE_API_KEY") ?: ""
// SubDL (backup subtitle source) key - local.properties only
val subdlApiKey: String = localProperties.getProperty("SUBDL_API_KEY") ?: ""

android {
    namespace = "com.victor.stream"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.victor.stream"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Passes TMDB_API_KEY safely into your Kotlin code as BuildConfig.TMDB_API_KEY
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "WYZIE_API_KEY", "\"$wyzieApiKey\"")
        buildConfigField("String", "SUBDL_API_KEY", "\"$subdlApiKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // Simple monochrome vector icons for the side menu (Home, Movie, Tv, etc.)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // ExoPlayer (Media3) for video streaming (HLS + DASH + MP4)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Google Cast (Chromecast / Google TV) for the player's cast button
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // Coil for loading TMDB poster images
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Navigation between screens
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Retrofit & Gson for fetching TMDB & AniList API data
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}