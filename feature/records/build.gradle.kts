plugins {
    id("plakagozcusu.android.feature")
}

android {
    namespace = "com.plakagozcusu.feature.records"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.coil.compose)
    testImplementation(libs.mockk)
}
