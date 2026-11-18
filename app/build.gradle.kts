import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val releaseSigningFile = rootProject.file(".release-private/signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use { load(it) }
}
fun releaseSigningValue(name: String, envName: String): String? =
    providers.environmentVariable(envName).orNull ?: releaseSigning.getProperty(name)
val releaseStorePath = releaseSigningValue("storeFile", "DAGOAL_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "DAGOAL_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "DAGOAL_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "DAGOAL_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.stipasay.dagoal"
    buildFeatures {
        resValues = true
    }
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.stipasay.dagoal"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("bool", "firebase_account_cleanup_enabled",
            (providers.gradleProperty("dagoalFirebaseAccountCleanup").orNull == "true").toString())
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
// Local development can still compile release code without credentials; distribution must use this task.
tasks.register("distributionRelease") {
    group = "build"
    description = "Build signed release artifacts for distribution."
    doFirst {
        check(hasReleaseSigning) { "Release signing is missing. See docs/release/RELEASE.md; never distribute an unsigned artifact." }
    }
    dependsOn("assembleRelease", "bundleRelease", "lintRelease")
}
