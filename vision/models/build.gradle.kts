plugins {
    id("plakagozcusu.android.library")
    id("plakagozcusu.hilt")
}

android {
    namespace = "com.plakagozcusu.vision.models"
    // Models must stay uncompressed so LiteRT can memory-map them from the APK.
    androidResources.noCompress += "tflite"
}

dependencies {
    api(project(":vision:api"))
    implementation(libs.litert)
    implementation(libs.androidx.core.ktx)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.serialization.json)
}
