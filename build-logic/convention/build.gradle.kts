plugins {
    `kotlin-dsl`
}

group = "com.plakagozcusu.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Putting these on the build-logic classpath also pins the versions used by every module,
    // overriding the KGP version AGP 9 would otherwise bring in.
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "plakagozcusu.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "plakagozcusu.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "plakagozcusu.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "plakagozcusu.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "plakagozcusu.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "plakagozcusu.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
