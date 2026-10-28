import java.util.Properties

plugins {
    id("plakagozcusu.android.application")
    id("plakagozcusu.android.compose")
    id("plakagozcusu.hilt")
}

// On the build-logic classpath, so it is applied by id rather than through the plugins block.
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

android {
    namespace = "com.plakagozcusu.app"
    buildFeatures.buildConfig = true

    defaultConfig {
        applicationId = "com.plakagozcusu.app"
        versionCode = 8
        versionName = "0.4.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from a properties file outside the repo, referenced by the user-level
    // Gradle property `plaka.signing.properties`. Without it the release APK is built unsigned.
    val signingProps =
        providers
            .gradleProperty("plaka.signing.properties")
            .orNull
            ?.let { file(it) }
            ?.takeIf { it.isFile }
            ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
    if (signingProps != null) {
        signingConfigs.create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    // Smaller downloads: one APK per CPU family plus a universal APK (ML libraries are ~per-ABI native code).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:onboarding"))
    implementation(project(":core:camera"))
    implementation(project(":core:data"))
    implementation(project(":feature:monitor"))
    implementation(project(":vision:models"))
    implementation(project(":feature:records"))
    implementation(project(":feature:settings"))
    implementation(project(":core:datastore"))
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
}
