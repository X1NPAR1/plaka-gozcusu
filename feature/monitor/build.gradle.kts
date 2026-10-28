plugins {
    id("plakagozcusu.android.feature")
}

android {
    namespace = "com.plakagozcusu.feature.monitor"
}

dependencies {
    implementation(project(":core:camera"))
    implementation(project(":core:data"))
    implementation(project(":vision:pipeline"))
    // The service wires the concrete adapters; screens only see :vision:api types.
    implementation(project(":vision:models"))
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.camerax.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(testFixtures(project(":core:camera")))
}
