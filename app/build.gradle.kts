plugins {
    id("plakagozcusu.android.application")
    id("plakagozcusu.android.compose")
    id("plakagozcusu.hilt")
}

// On the build-logic classpath, so it is applied by id rather than through the plugins block.
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

android {
    namespace = "com.plakagozcusu.app"

    defaultConfig {
        applicationId = "com.plakagozcusu.app"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
}
