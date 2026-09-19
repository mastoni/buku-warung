import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val releaseKeystorePath: String = localProperties.getProperty("RELEASE_KEYSTORE_PATH")
    ?: System.getenv("RELEASE_KEYSTORE_PATH")
    ?: "C:\\Users\\kangtoni\\AndroidSigning\\bukuwarung-release-v2.jks"

val releaseKeystorePassword: String? = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD")
    ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")

val releaseKeyAlias: String = localProperties.getProperty("RELEASE_KEY_ALIAS")
    ?: System.getenv("RELEASE_KEY_ALIAS")
    ?: "bukuwarung"

val releaseKeyPassword: String? = localProperties.getProperty("RELEASE_KEY_PASSWORD")
    ?: System.getenv("RELEASE_KEY_PASSWORD")
    ?: releaseKeystorePassword

val releaseLicenseServerUrl: String = localProperties.getProperty("RELEASE_LICENSE_SERVER_URL")
    ?: System.getenv("RELEASE_LICENSE_SERVER_URL")
    ?: "https://license.skmnetwork.com"

if (!releaseLicenseServerUrl.startsWith("https://")) {
    throw GradleException("Release build requires a valid HTTPS RELEASE_LICENSE_SERVER_URL (was '$releaseLicenseServerUrl')")
}

android {
    namespace = "id.skmnetwork.bukuwarung"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.skmnetwork.bukuwarung"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = File(releaseKeystorePath)
            if (keystoreFile.exists() && !releaseKeystorePassword.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_OWNER_TEST", "true")
            buildConfigField("String", "LICENSE_SERVER_URL", "\"http://10.0.2.2:3000\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_OWNER_TEST", "false")
            buildConfigField("String", "LICENSE_SERVER_URL", "\"$releaseLicenseServerUrl\"")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("ownerTest") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "ENABLE_OWNER_TEST", "true")
            buildConfigField("String", "LICENSE_SERVER_URL", "\"http://10.0.2.2:3000\"")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}



dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Room Database
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // CameraX & ML Kit Barcode Scanning
    val cameraVersion = "1.4.1"
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Jetpack DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Google Identity / Credential Manager & Play Services Auth
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
