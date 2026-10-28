plugins {
    id("plakagozcusu.android.feature")
}

android {
    namespace = "com.plakagozcusu.feature.onboarding"
}

dependencies {
    implementation(project(":core:camera"))
    implementation(project(":vision:pipeline"))
    implementation(libs.camerax.compose)
    testImplementation(libs.mockk)
    testImplementation(testFixtures(project(":core:camera")))
}

dependencies {
    implementation(project(":core:datastore"))
    testImplementation(libs.datastore.preferences)
}
