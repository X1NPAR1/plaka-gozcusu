plugins {
    id("plakagozcusu.android.library")
    id("plakagozcusu.android.compose")
}

android {
    namespace = "com.plakagozcusu.core.designsystem"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.animation)
}
