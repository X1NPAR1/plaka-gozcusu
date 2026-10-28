plugins {
    id("plakagozcusu.android.feature")
}

android {
    namespace = "com.plakagozcusu.feature.settings"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.mockk)
}
