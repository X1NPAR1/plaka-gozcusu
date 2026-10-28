plugins {
    id("plakagozcusu.android.feature")
}

android {
    namespace = "com.plakagozcusu.feature.settings"
}

dependencies {
    implementation(project(":core:datastore"))
    testImplementation(libs.datastore.preferences)
}
