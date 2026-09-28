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

// Production release signing identity.
//
// Resolved once, from local.properties (git-ignored) or environment variables. No keystore
// path, alias, or password is ever stored in tracked source.
//
// NOTE: the RELEASE_KEYSTORE_PATH default below is a convenience for the primary developer
// workstation only. It is NOT a production identity: it points at bukuwarung-release-v2.jks,
// the key that signed v0.2.1 (SHA-256 4aeec712...). Any other machine or CI must set
// RELEASE_KEYSTORE_PATH to that same keystore explicitly, otherwise the release build fails
// rather than silently signing with a different key.
//
// Configuration (never commit the password):
//   local.properties  (git-ignored, preferred locally)
//     RELEASE_KEYSTORE_PATH=C\:\\path\\to\\bukuwarung-release-v2.jks
//     RELEASE_KEYSTORE_PASSWORD=<password>
//   or environment variables of the same names for CI.
//
// See docs/RELEASE_SIGNING.md for the full procedure.
val releaseKeystoreFile: File = File(releaseKeystorePath)

if (!releaseLicenseServerUrl.startsWith("https://")) {
    throw GradleException("Release build requires a valid HTTPS RELEASE_LICENSE_SERVER_URL (was '$releaseLicenseServerUrl')")
}

// Gate H.3 - single source of truth for the owner-test licence bypass.
//
// Any build type listed here gains the `LicenseManager.activateOwnerTest()` bypass, which grants a
// permanent, offline, server-less licence unlock. The H.3 invariant check below refuses to configure
// a build type that carries this flag unless it is unambiguously separated from production.
val ownerTestBypassBuildTypes = mutableSetOf<String>()

val RELEASE_SIGNING_CONFIG_NAME = "release"

/**
 * Gate H.3 - a snapshot of one build type's security-relevant configuration.
 *
 * Captured during configuration so both the hard fail-fast check and the
 * `verifyVariantSecurity` verification task assert against the same data, rather
 * than the task and the guard drifting apart.
 */
data class VariantSecurityRow(
    val name: String,
    val applicationId: String,
    val applicationIdSuffix: String,
    val signingConfig: String,
    val debuggable: Boolean,
    val minifyEnabled: Boolean,
    val ownerTestBypassEnabled: Boolean
)

/** Populated from the resolved `android { }` configuration; asserted by `verifyVariantSecurity`. */
var variantSecurityMatrix: List<VariantSecurityRow> = emptyList()

android {
    namespace = "id.skmnetwork.bukuwarung"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.skmnetwork.bukuwarung"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Gate H.3 - run the unit test suite against the RELEASE configuration.
    //
    // Without this, AGP defaults to `debug`, so all tests compile against a BuildConfig where
    // ENABLE_OWNER_TEST=true, DEBUG=true and LICENSE_SERVER_URL is the emulator loopback address.
    // The release configuration - the one that is actually shipped, with the owner bypass off and
    // the production HTTPS licence server - was therefore never exercised by any test.
    // See LicenseValidationUnitTest.testBuildConfig_FieldsMatchBuildType, which asserts exactly
    // those three values and only becomes meaningful once this is set to "release".
    testBuildType = "release"

    signingConfigs {
        create(RELEASE_SIGNING_CONFIG_NAME) {
            storeFile = releaseKeystoreFile
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_OWNER_TEST", "true")
            buildConfigField("String", "LICENSE_SERVER_URL", "\"http://10.0.2.2:3000\"")
            ownerTestBypassBuildTypes += "debug"
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_OWNER_TEST", "false")
            buildConfigField("String", "LICENSE_SERVER_URL", "\"$releaseLicenseServerUrl\"")
            // Applied unconditionally: a release variant must never be produced unsigned.
            signingConfig = signingConfigs.getByName(RELEASE_SIGNING_CONFIG_NAME)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // NON-DISTRIBUTABLE. Gate H.3.
        //
        // Owner Test previously used `initWith(release)` plus the production signing config and NO
        // applicationIdSuffix. That combination could emit an APK which was, to the package manager
        // and to the signing identity, indistinguishable from production while carrying the
        // owner-test licence bypass. The only thing stopping it from shipping was an incidental
        // crash: ENABLE_OWNER_TEST=true combined with the emulator's plain-HTTP licence server URL
        // tripped the `!BuildConfig.DEBUG && !https` guard in LicenseApiClient. A crash is not a
        // security boundary.
        //
        // It is now built on `debug` exactly like `smokeTest`:
        //   - applicationIdSuffix ".ownertest" so it can never replace or be installed over
        //     production, and lands in its own sandbox with its own DataStore;
        //   - the Android debug signing identity, never the production key;
        //   - debuggable, so it is obvious on inspection.
        // The production applicationId + production signer + licence bypass combination is now
        // impossible, and the invariant check below makes it impossible to reintroduce silently.
        create("ownerTest") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".ownertest"
            buildConfigField("boolean", "ENABLE_OWNER_TEST", "true")
            buildConfigField("String", "LICENSE_SERVER_URL", "\"http://10.0.2.2:3000\"")
            ownerTestBypassBuildTypes += "ownerTest"
        }
        // NON-DISTRIBUTABLE. Local funnel smoke test only. Inherits the debug signing config
        // (Android Debug keystore), never the release key, and carries an applicationIdSuffix
        // so it can never replace or be mistaken for a production install. Its manifest overlay
        // grants cleartext to 10.0.2.2 only, so no other destination is affected and no
        // release/debug configuration is modified.
        create("smokeTest") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".smoketest"
            buildConfigField("boolean", "ENABLE_OWNER_TEST", "true")
            buildConfigField("String", "LICENSE_SERVER_URL", "\"http://10.0.2.2:3000\"")
            ownerTestBypassBuildTypes += "smokeTest"
        }
    }

    // -----------------------------------------------------------------------
    // Gate H.3 - variant security invariants.
    //
    // Enforced at configuration time, so a build that would produce a
    // production-signed / production-identified artefact carrying the licence
    // bypass fails immediately rather than emitting a shippable APK.
    //
    // Two properties, because they guard against different risks:
    //
    //   1. No build type that enables ENABLE_OWNER_TEST may use the production
    //      signing key. This is unconditional: a debuggable artefact signed with
    //      the production identity is still a production-identity artefact.
    //
    //   2. A build type that enables ENABLE_OWNER_TEST and is *not* debuggable
    //      must also carry an applicationIdSuffix. A non-debuggable artefact is
    //      the kind a merchant could be handed, so it must be installable only
    //      under its own package. Debuggable variants (debug, ownerTest,
    //      smokeTest) are already trivially distinguishable by their debuggable
    //      flag and debug signing identity and are never distributed.
    // -----------------------------------------------------------------------
    buildTypes.forEach { type ->
        if (type.name !in ownerTestBypassBuildTypes) return@forEach

        val suffix = type.applicationIdSuffix.orEmpty()
        val signer = type.signingConfig?.name.orEmpty()
        val label = if (type.isDebuggable) "debuggable" else "non-debuggable"

        if (signer == RELEASE_SIGNING_CONFIG_NAME) {
            throw GradleException(
                "Build type '${type.name}' ($label) enables ENABLE_OWNER_TEST but is signed with " +
                    "the production release key. An owner-test artefact must never carry the " +
                    "production signing identity."
            )
        }
        if (!type.isDebuggable && suffix.isBlank()) {
            throw GradleException(
                "Build type '${type.name}' ($label) enables ENABLE_OWNER_TEST but has no " +
                    "applicationIdSuffix, so it would share the production applicationId " +
                    "id.skmnetwork.bukuwarung. A non-debuggable owner-test artefact must always be " +
                    "installed under a distinct package name."
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Gate H.3 - snapshot of the real variant matrix, read from the resolved
    // configuration rather than from source text. The `verifyVariantSecurity`
    // task asserts against this, so the guard and the test can never disagree.
    val productionApplicationId = defaultConfig.applicationId.orEmpty()
    variantSecurityMatrix = buildTypes.map { type ->
        VariantSecurityRow(
            name = type.name,
            applicationId = productionApplicationId + (type.applicationIdSuffix ?: ""),
            applicationIdSuffix = type.applicationIdSuffix ?: "",
            signingConfig = type.signingConfig?.name ?: "<none>",
            debuggable = type.isDebuggable,
            minifyEnabled = type.isMinifyEnabled,
            ownerTestBypassEnabled = type.name in ownerTestBypassBuildTypes
        )
    }
}

// -----------------------------------------------------------------------
// Gate H.3 - variant security verification task.
//
// Proves the release/ownerTest boundary from the resolved Gradle variant
// configuration (applicationId, applicationIdSuffix, signing config,
// debuggable, owner-bypass flag) instead of asserting on source text.
//
// Run with:  ./gradlew :app:verifyVariantSecurity
// Wired into `check`, so it runs as part of a normal verification build.
// -----------------------------------------------------------------------
val PRODUCTION_APPLICATION_ID = "id.skmnetwork.bukuwarung"

tasks.register("verifyVariantSecurity") {
    group = "verification"
    description = "Gate H.3: asserts the production / owner-test variant security boundary."

    val matrix = variantSecurityMatrix
    val productionKeystorePath = releaseKeystoreFile.absolutePath

    doLast {
        val violations = mutableListOf<String>()

        logger.lifecycle("Gate H.3 - variant security matrix (applicationId / signing / owner bypass)")
        logger.lifecycle(
            "  %-10s | %-46s | %-24s | %-10s | %-6s | %s".format(
                "buildType", "applicationId", "signingConfig", "debuggable", "minify", "ownerBypass"
            )
        )
        matrix.forEach { row ->
            logger.lifecycle(
                "  %-10s | %-46s | %-24s | %-10s | %-6s | %s".format(
                    row.name, row.applicationId, row.signingConfig, row.debuggable,
                    row.minifyEnabled, row.ownerTestBypassEnabled
                )
            )
        }
        logger.lifecycle("")

        val release = matrix.firstOrNull { it.name == "release" }
        val ownerTest = matrix.firstOrNull { it.name == "ownerTest" }

        // A. release is the only production identity, and carries no bypass.
        if (release == null) {
            violations.add("No 'release' build type is configured")
        } else {
            val r = release
            if (r.applicationId != PRODUCTION_APPLICATION_ID) {
                violations.add("release applicationId must be $PRODUCTION_APPLICATION_ID, was ${r.applicationId}")
            }
            if (r.signingConfig != RELEASE_SIGNING_CONFIG_NAME) {
                violations.add("release must use the '$RELEASE_SIGNING_CONFIG_NAME' signing config, was ${r.signingConfig}")
            }
            if (r.ownerTestBypassEnabled) {
                violations.add("release MUST NOT enable the owner-test licence bypass")
            }
            if (r.debuggable) {
                violations.add("release MUST NOT be debuggable")
            }
        }

        // C. ownerTest exists, is separated, and is not production-signed.
        if (ownerTest == null) {
            violations.add("No 'ownerTest' build type is configured")
        } else {
            val o = ownerTest
            if (!o.ownerTestBypassEnabled) {
                violations.add("ownerTest must keep ENABLE_OWNER_TEST=true to stay useful")
            }
            if (o.applicationId == PRODUCTION_APPLICATION_ID) {
                violations.add("ownerTest MUST NOT share the production applicationId ($PRODUCTION_APPLICATION_ID)")
            }
            if (o.applicationIdSuffix.isBlank()) {
                violations.add("ownerTest MUST carry an applicationIdSuffix")
            }
            if (o.signingConfig == RELEASE_SIGNING_CONFIG_NAME) {
                violations.add("ownerTest MUST NOT be signed with the production release key")
            }
        }

        // No variant at all may combine the production identity with the bypass.
        matrix.filter { it.ownerTestBypassEnabled }.forEach { row: VariantSecurityRow ->
            if (row.signingConfig == RELEASE_SIGNING_CONFIG_NAME) {
                violations.add("Variant '${row.name}' combines the owner-test bypass with the production signing key")
            }
            if (row.applicationId == PRODUCTION_APPLICATION_ID && !row.debuggable) {
                violations.add("Variant '${row.name}' combines the owner-test bypass with the production applicationId")
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Gate H.3 variant security boundary violated:\n  - " + violations.joinToString("\n  - ") +
                    "\nProduction keystore in use: $productionKeystorePath"
            )
        }

        logger.lifecycle("Gate H.3 variant security boundary: OK")
    }
}

tasks.named("check") {
    dependsOn("verifyVariantSecurity")
}



dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    // Gate H.5.1: ProcessLifecycleOwner drives foreground license validation. Attaching to the
    // PROCESS lifecycle rather than to an Activity is what keeps a rotation or a configuration
    // change from looking like a new foreground transition.
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

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

// -----------------------------------------------------------------------
// Gate H.3 - production signing is mandatory.
//
// A production artefact must never be produced unsigned or signed with an
// accidental key. If the keystore or its credentials are absent, the release
// packaging tasks fail here with an actionable message instead of failing
// deep inside the packaging pipeline (or, worse, succeeding with a default
// debug identity).
//
// Only release *artefact* tasks are checked. `assembleReleaseAndroidTest`
// builds the instrumentation APK, which is signed with the Android debug key
// and needs no production keystore, and test tasks are excluded for the same
// reason.
// -----------------------------------------------------------------------
val PRODUCTION_ARTEFACT_TASK = Regex("^(assemble|bundle|package)Release(\\w*)$", RegexOption.IGNORE_CASE)

gradle.taskGraph.whenReady {
    val buildsProductionArtefact = allTasks.any { task ->
        task.project == project &&
            PRODUCTION_ARTEFACT_TASK.matches(task.name) &&
            !task.name.contains("AndroidTest", ignoreCase = true)
    }
    if (!buildsProductionArtefact) return@whenReady

    val problems = buildList {
        if (!releaseKeystoreFile.exists()) {
            add("RELEASE_KEYSTORE_PATH does not exist: ${releaseKeystoreFile.absolutePath}")
        }
        if (releaseKeystorePassword.isNullOrEmpty()) {
            add("RELEASE_KEYSTORE_PASSWORD / RELEASE_KEYSTORE_PATH password is not set")
        }
        if (releaseKeyPassword.isNullOrEmpty()) {
            add("RELEASE_KEY_PASSWORD is not set")
        }
        if (releaseKeyAlias.isBlank()) {
            add("RELEASE_KEY_ALIAS is not set")
        }
    }
    if (problems.isNotEmpty()) {
        throw GradleException(
            "Production signing credentials are incomplete, refusing to build a production " +
                "artefact:\n  - " + problems.joinToString("\n  - ") +
                "\nSet RELEASE_KEYSTORE_PATH, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and " +
                "RELEASE_KEY_PASSWORD in local.properties (git-ignored) or as environment " +
                "variables. See docs/RELEASE_SIGNING.md."
        )
    }
}
