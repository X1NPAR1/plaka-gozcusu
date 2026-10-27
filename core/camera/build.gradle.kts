plugins {
    id("plakagozcusu.android.library")
    id("plakagozcusu.hilt")
}

android {
    namespace = "com.plakagozcusu.core.camera"
    testFixtures.enable = true
}

dependencies {
    api(project(":vision:api"))
    api(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.mockk)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
