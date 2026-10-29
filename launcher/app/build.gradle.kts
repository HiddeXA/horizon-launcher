import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// API-sleutels staan in local.properties (komt nooit in git):
//   tmdb.token=eyJhbGciOi...   (TMDB "API Read Access Token")
//   omdb.key=abcd1234          (OMDb API key)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String {
    // Aanhalingstekens en backslashes in wachtwoorden veilig in BuildConfig zetten.
    val v = localProps.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$v\""
}

android {
    namespace = "nl.thuis.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.thuis.launcher"
        minSdk = 28          // Chromecast met Google TV draait Android 12+
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"

        buildConfigField("String", "TMDB_TOKEN", secret("tmdb.token"))
        buildConfigField("String", "OMDB_KEY", secret("omdb.key"))
        // IPTV (Xtream): server-adres, gebruikersnaam en wachtwoord van je aanbieder
        buildConfigField("String", "XTREAM_SERVER", secret("xtream.server"))
        buildConfigField("String", "XTREAM_USER", secret("xtream.user"))
        buildConfigField("String", "XTREAM_PASS", secret("xtream.pass"))
    }

    buildTypes {
        release {
            // R8: verkleint en optimaliseert de code. Samen met een niet-debugbare build
            // is dit de grootste snelheidswinst voor Compose.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug") // later eigen sleutel
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    // Installeert de meegeleverde Baseline Profiles van Compose bij sideloaden (snellere start en scroll)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Bios: speler en pictogrammen
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-extractor:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    implementation("androidx.compose.material:material-icons-core")

    // Netwerk en afbeeldingen
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    testImplementation("junit:junit:4.13.2")
}
